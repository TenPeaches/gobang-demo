package com.gobang.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.dto.ResultDTO;
import com.gobang.entity.Room;
import com.gobang.entity.User;
import com.gobang.service.MatchManagerService;
import com.gobang.service.RoomManagerService;
import com.gobang.util.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.websocket.Session;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 匹配服务实现类（真实段位匹配+双方推送匹配信息）
 */
@Service
public class MatchManagerImpl implements MatchManagerService {
    // 匹配超时时间（毫秒），这里的_是Java的数字分隔符，仅用于提升可读性
    private static final long MATCH_TIMEOUT_MS = 60_000L;
    // 段位队列：key=段位名称，value=该段位的匹配队列，ConcurrentLinkedQueue保证线程安全
    private final Map<String, ConcurrentLinkedQueue<User>> rankQueues = new HashMap<>();
    // 记录用户进入匹配队列的时间：key=userId
    private final ConcurrentHashMap<Integer, Long> enqueueTimeMap = new ConcurrentHashMap<>();
    // 匹配线程池（2个线程的线程池），这2个线程会轮流、反复检查所有段位队列，检查到匹配就创建房间，检查到超时就清理用户
    private final ExecutorService matchPool = Executors.newFixedThreadPool(2);
    // JSON工具
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RoomManagerService roomManagerService;

    // 初始化段位队列
    public MatchManagerImpl() {
        rankQueues.put("入门级", new ConcurrentLinkedQueue<>());
        rankQueues.put("进阶级", new ConcurrentLinkedQueue<>());
        rankQueues.put("高手级", new ConcurrentLinkedQueue<>());
        // 启动匹配检测线程
        startMatchThread();
    }

    /**
     * 判断用户段位
     *
     * @param user 用户对象
     * @return 段位名称
     */
    private String getUserRank(User user) {
        int score = user.getScore();
        if (score <= 1000) return "入门级";
        else if (score <= 2000) return "进阶级";
        else return "高手级";
    }

    /**
     * 启动匹配检测线程（每秒检测）
     */
    private void startMatchThread() {
        matchPool.submit(() -> { // 在线程池中提交一个任务，不断执行匹配检测
            while (true) { // 让“检测队列”的逻辑能一直执行，直到项目停止
                try {
                    long currentTime = System.currentTimeMillis();

                    // 遍历所有段位队列
                    for (String rank : rankQueues.keySet()) {
                        ConcurrentLinkedQueue<User> queue = rankQueues.get(rank);

                        // 清理超时用户（避免一直挂在队列里）
                        cleanupTimeoutUsers(queue, currentTime);

                        // 匹配用户
                        matchUsers(queue, rank);
                    }

                    Thread.sleep(1000); // 防止 CPU 被这个线程“吃满”，给服务器留喘气的机会，每秒钟检测一次所有段位队列
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 线程被中断时，重新设为“中断状态”
                    break; // 退出循环
                } catch (Exception e) {
                    // 捕获其他异常，避免匹配线程崩溃
                    System.err.println("匹配线程异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 清理超时用户
     * 
     * @param queue       匹配队列
     * @param currentTime 当前时间
     */
    private void cleanupTimeoutUsers(ConcurrentLinkedQueue<User> queue, long currentTime) {
        // 把队列中的用户转换为数组，避免在遍历过程中修改队列（ConcurrentLinkedQueue不支持直接遍历并移除）
        User[] usersToRemove = queue.toArray(new User[0]);
        for (User user : usersToRemove) {
            if (user == null) continue; // 如果用户为空，跳过

            Long enqueueTime = enqueueTimeMap.get(user.getId());
            if (enqueueTime != null && currentTime - enqueueTime > MATCH_TIMEOUT_MS) {
                // 从队列移除并通知
                if (queue.remove(user)) {
                    enqueueTimeMap.remove(user.getId());
                    Session session = WebSocketSessionManager.getSession(user.getUsername());
                    if (session != null && session.isOpen()) {
                        try {
                            // 通过 getBasicRemote() 获取远程端点后才能调用 sendText() 发送，writeValueAsString()：将 Java 对象转换为 JSON 字符串
                            session.getBasicRemote().sendText(objectMapper.writeValueAsString(
                                    new ResultDTO("matchTimeout", "匹配超时，请重新匹配")
                            ));
                        } catch (Exception e) {
                            // 超时通知失败不影响主流程（可能是连接刚断开）
                            System.err.println("通知用户匹配超时失败: " + e.getMessage());
                        }
                    }
                    System.out.println("用户" + user.getUsername() + "匹配超时，已从队列移除");
                }
            }
        }
    }

    /**
     * 匹配用户
     *
     * @param queue 匹配队列
     * @param rank  段位
     */
    private void matchUsers(ConcurrentLinkedQueue<User> queue, String rank) {
        if (queue.size() >= 2) {
            // 取出2个用户创建房间
            User user1 = queue.poll();
            User user2 = queue.poll();

            if (user1 != null && user2 != null) {
                enqueueTimeMap.remove(user1.getId());
                enqueueTimeMap.remove(user2.getId());

                // 创建房间，生成唯一的房间ID：使用UUID确保唯一性，截取前8位使ID更简洁
                String roomId = "room_" + UUID.randomUUID().toString().substring(0, 8);
                Room room = new Room(roomId, user1, user2);

                // 保存房间
                roomManagerService.addRoom(room);
                roomManagerService.addUserRoom(user1.getId(), roomId);
                roomManagerService.addUserRoom(user2.getId(), roomId);

                // 给双方推送匹配成功信息
                pushMatchSuccess(user1, user2, roomId);
                System.out.println("匹配成功：" + user1.getUsername() + " vs " + user2.getUsername() + "（段位：" + rank + "）");
            }
        }
    }

    /**
     * 给双方推送匹配成功信息
     *
     * @param user1  用户1
     * @param user2  用户2
     * @param roomId 房间ID
     */
    private void pushMatchSuccess(User user1, User user2, String roomId) {
        try {
            // 给user1推送
            Session session1 = WebSocketSessionManager.getSession(user1.getUsername());
            if (session1 != null && session1.isOpen()) {
                Map<String, Object> result1 = new HashMap<>();
                result1.put("self", user1);
                result1.put("opponent", user2);
                result1.put("roomId", roomId);
                result1.put("roomUser1Id", user1.getId()); // 真实先手的用户ID

                session1.getBasicRemote().sendText(objectMapper.writeValueAsString(
                        new ResultDTO("matchSuccess", result1)
                ));
            } else {
                System.err.println("用户" + user1.getUsername() + "离线，无法推送匹配成功信息");
            }

            // 给user2推送
            Session session2 = WebSocketSessionManager.getSession(user2.getUsername());
            if (session2 != null && session2.isOpen()) {
                Map<String, Object> result2 = new HashMap<>();
                result2.put("self", user2);
                result2.put("opponent", user1);
                result2.put("roomId", roomId);
                result2.put("roomUser1Id", user1.getId()); // 同样下发先手ID

                session2.getBasicRemote().sendText(objectMapper.writeValueAsString(
                        new ResultDTO("matchSuccess", result2)
                ));
            } else {
                System.err.println("用户" + user2.getUsername() + "离线，无法推送匹配成功信息");
            }
        } catch (Exception e) {
            System.err.println("推送匹配成功信息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 用户加入匹配队列
     *
     * @param user 用户对象
     */
    @Override
    public void joinMatch(User user) {
        try {
            String rank = getUserRank(user);
            ConcurrentLinkedQueue<User> queue = rankQueues.get(rank);
            queue.offer(user);
            enqueueTimeMap.put(user.getId(), System.currentTimeMillis());
            System.out.println(user.getUsername() + "加入" + rank + "匹配队列，当前队列人数：" + queue.size());
        } catch (Exception e) {
            System.err.println("用户加入匹配队列失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 用户取消匹配
     *
     * @param user 用户对象
     */
    @Override
    public void cancelMatch(User user) {
        try {
            String rank = getUserRank(user);
            ConcurrentLinkedQueue<User> queue = rankQueues.get(rank);
            // 从队列中移除该用户
            queue.remove(user);
            enqueueTimeMap.remove(user.getId());
            System.out.println(user.getUsername() + "取消" + rank + "匹配，当前队列人数：" + queue.size());
        } catch (Exception e) {
            System.err.println("用户取消匹配失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}