package com.gobang.service.impl;

import com.gobang.entity.Room;
import com.gobang.service.RoomManagerService;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 房间管理服务实现
 */
@Service
public class RoomManagerImpl implements RoomManagerService {
    // 房间ID -> 房间对象
    private final ConcurrentHashMap<String, Room> roomMap = new ConcurrentHashMap<>();
    // 用户ID -> 房间ID
    private final ConcurrentHashMap<Integer, String> userRoomMap = new ConcurrentHashMap<>();
    // 定时清理超时房间的线程池，和 MatchManagerImpl 中的线程池不同，ScheduledExecutorService 适合固定时间间隔执行任务
    // 而 MatchManagerImpl 中的线程池更适合持续运行的任务，如匹配用户
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // 房间超时时间（毫秒）
    private static final long ROOM_TIMEOUT_MS = 30 * 60 * 1000; // 30分钟

    /**
     * 构造方法，初始化定时清理任务
     */
    public RoomManagerImpl() {
        // 每15分钟清理一次超时房间，缩短清理间隔以更及时释放内存
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupTimeoutRooms();
            } catch (Exception e) {
                System.err.println("清理超时房间异常: " + e.getMessage());
                e.printStackTrace();
            }
            // 第一个15表示15分钟后执行第一次清理，第二个15表示后续每15分钟执行一次清理
        }, 15, 15, TimeUnit.MINUTES);
    }

    /**
     * 清理超时房间
     */
    private void cleanupTimeoutRooms() {
        long currentTime = System.currentTimeMillis();
        
        // 批量清理超时房间，返回true时，removeIf() 会自动从 roomMap 中移除该房间
        roomMap.entrySet().removeIf(entry -> {
            Room room = entry.getValue();
            long createTime = room.getCreateTime();
            
            // 检查房间是否超时
            if (currentTime - createTime > ROOM_TIMEOUT_MS) {
                // 清理用户-房间绑定
                userRoomMap.remove(room.getUser1().getId());
                userRoomMap.remove(room.getUser2().getId());
                System.out.println("清理超时房间: " + entry.getKey() + "，创建时间: " + createTime);
                return true;
            }
            return false;
        });
    }

    /**
     * 添加房间
     * @param room 房间对象
     */
    @Override
    public void addRoom(Room room) {
        try {
            room.setCreateTime(System.currentTimeMillis()); // 设置房间创建时间
            roomMap.put(room.getRoomId(), room);
            System.out.println("添加房间: " + room.getRoomId());
        } catch (Exception e) {
            System.err.println("添加房间失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 根据用户ID获取房间
     * @param userId 用户ID
     * @return 房间对象
     */
    @Override
    public Room getRoomByUserId(Integer userId) {
        try {
            String roomId = userRoomMap.get(userId);
            return roomId != null ? roomMap.get(roomId) : null;
        } catch (Exception e) {
            System.err.println("根据用户ID获取房间失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 根据房间ID获取房间
     * @param roomId 房间ID
     * @return 房间对象
     */
    @Override
    public Room getRoomByRoomId(String roomId) {
        try {
            return roomMap.get(roomId);
        } catch (Exception e) {
            System.err.println("根据房间ID获取房间失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 添加用户-房间绑定
     * @param userId 用户ID
     * @param roomId 房间ID
     */
    @Override
    public void addUserRoom(Integer userId, String roomId) {
        try {
            userRoomMap.put(userId, roomId);
        } catch (Exception e) {
            System.err.println("添加用户-房间绑定失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 移除房间
     * @param roomId 房间ID
     */
    @Override
    public void removeRoom(String roomId) {
        try {
            Room room = roomMap.remove(roomId);
            if (room != null) {
                userRoomMap.remove(room.getUser1().getId());
                userRoomMap.remove(room.getUser2().getId());
                System.out.println("移除房间: " + roomId);
            }
        } catch (Exception e) {
            System.err.println("移除房间失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取当前房间数量
     * @return 房间数量
     */
    public int getRoomCount() {
        return roomMap.size();
    }
}