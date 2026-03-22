package com.gobang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.dto.*;
import com.gobang.entity.Room;
import com.gobang.entity.User;
import com.gobang.service.MatchManagerService;
import com.gobang.service.RoomManagerService;
import com.gobang.service.UserService;
import com.gobang.util.ChessUtil;
import com.gobang.util.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 五子棋WebSocket核心控制器：处理所有实时通信（登录、绑定、匹配、落子、判赢）
 */
@ServerEndpoint("/ws/gobang")//前端通过 ws://xxx/ws/gobang 发起连接请求时，服务器会找到标注了这个注解的 GobangWebSocket 类，创建该类的实例，并触发 @OnOpen 方法，每个玩家各有一个实例
@Component//确保 Spring 能够管理 GobangWebSocket 类，其核心作用是实现依赖注入，使得可以在 GobangWebSocket 类中使用 @Autowired 注解注入其他 Spring 管理的 Bean
public class GobangWebSocket {
    // JSON序列化/反序列化工具（Jackson的ObjectMapper）
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // 当前连接的用户
    private User currentUser;

    /*
    @ServerEndpoint 注解的 WebSocket 类，实例是由 Tomcat/Jetty（WebSocket 容器）创建的，不是 Spring 容器创建的，因此无法使用 @Autowired 注解注入依赖对象。
     */
    //static变量属于GobangWebSocket类，不是属于某个实例 —— 不管 Tomcat 创建多少个 WebSocket 实例（每个玩家一个），都能共用这一个userService
    private static UserService userService;
    private static MatchManagerService matchManagerService;
    private static RoomManagerService roomManagerService;

    /*
    WebSocket 实例由 Tomcat 创建（非 Spring），直接 @Autowired 成员变量会 null
    所以用 “静态变量 + Spring 注入 setter” 的方式
    让所有 WebSocket 实例共用 Spring 管理的 Service，保证调用不报错
     */
    @Autowired
    public void setUserService(UserService userService) {
        GobangWebSocket.userService = userService;
    }

    @Autowired
    public void setMatchManagerService(MatchManagerService matchManagerService) {
        GobangWebSocket.matchManagerService = matchManagerService;
    }

    @Autowired
    public void setRoomManagerService(RoomManagerService roomManagerService) {
        GobangWebSocket.roomManagerService = roomManagerService;
    }

    // 连接建立
    @OnOpen
    public void onOpen(Session session) {
        System.out.println("WS连接建立：sessionId = " + session.getId());
    }

    // 接收消息
    @OnMessage//标记「前端发文本消息时自动执行的方法」—— 只要前端通过当前 WS 连接发消息（不管发什么），这个方法必触发，每次发消息都触发一次
    public void onMessage(String message, Session session) {
        try {
            //把前端的JSON字符串转成自定义的MessageDTO对象
            MessageDTO msgDTO = OBJECT_MAPPER.readValue(message, MessageDTO.class);
            switch (msgDTO.getType()) {
                //处理用户注册
                case "register": handleRegister(msgDTO.getData(), session); break;
                //处理用户登录（绑定 WS 用户）
                case "login": handleLogin(msgDTO.getData(), session); break;
                //绑定用户
                case "bindUser": handleBindUser(msgDTO.getData(), session); break;
                //处理 “开始匹配” 请求
                case "startMatch": handleStartMatch(session); break;
                //处理 “取消匹配” 请求
                case "cancelMatch": handleCancelMatch(session); break;
                //处理落子请求
                case "placeChess": handlePlaceChess(msgDTO.getData(), session); break;
                //处理退出登录
                case "logout": handleLogout(session); break;
                //查用户信息
                case "getUserInfo": handleGetUserInfo(msgDTO.getData(), session); break;
                //查房间信息
                case "getRoom": handleGetRoom(msgDTO.getData(), session); break;
                default: sendMsg(session, new ResultDTO("error", "未知消息类型"));
            }
        } catch (Exception e) {
            sendMsg(session, new ResultDTO("error", "消息处理失败：" + e.getMessage()));
            e.printStackTrace();
        }
    }

    // 连接关闭
    @OnClose
    public void onClose(Session session) {
        if (currentUser == null) return;

        Room room = roomManagerService.getRoomByUserId(currentUser.getId());
        // 无论是否开局，断开都先从匹配队列移除，避免“幽灵匹配”
        matchManagerService.cancelMatch(currentUser);

        // 找到对手
        User otherUser = null;
        Session otherSession = null;
        if (room != null) {
            otherUser = room.getUser1().getId().equals(currentUser.getId()) ? room.getUser2() : room.getUser1();
            if (otherUser != null) {
                otherSession = WebSocketSessionManager.getSession(otherUser.getUsername());
            }
        }

        // 对于未开始的房间，延迟检查是否需要解散
        if (room != null && !room.isEnd() && !room.isStarted()) {
            // 声明为final，以便在lambda表达式中使用
            final Room finalRoom = room;
            final Session finalOtherSession = otherSession;
            final User finalCurrentUser = currentUser;
            
            // 启动一个线程，使其在后台执行任务不影响主线程正常运行，延迟3秒后检查用户是否重新连接
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    // 检查用户是否重新连接
                    Session newSession = WebSocketSessionManager.getSession(finalCurrentUser.getUsername());
                    if (newSession == null || !newSession.isOpen()) {
                        // 用户没有重新连接，解散房间
                        if (finalOtherSession != null && finalOtherSession.isOpen()) {
                            sendMsg(finalOtherSession, new ResultDTO("oppLeave", "对手离开，房间已解散"));
                        }
                        roomManagerService.removeRoom(finalRoom.getRoomId());
                        System.out.println("用户" + finalCurrentUser.getUsername() + "3秒内未重新连接，房间已解散");
                    } else {
                        // 用户重新连接，保留房间
                        System.out.println("用户" + finalCurrentUser.getUsername() + "已重新连接，房间保留");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } 
        // 对于已开始的房间，进行胜负结算
        else if (room != null && !room.isEnd() && room.isStarted()) {
            // 积分计算（增加边界保护：防止负分，设置上限）
            int currentUserNewScore = Math.max(currentUser.getScore() - 50, 0); // 下限0
            int otherUserNewScore = Math.min(otherUser.getScore() + 50, 99999); // 上限99999
            currentUser.setScore(currentUserNewScore);
            userService.updateUserStats(currentUser);

            otherUser.setScore(otherUserNewScore);
            otherUser.setTotalGames(otherUser.getTotalGames() + 1);
            otherUser.setWinGames(otherUser.getWinGames() + 1);
            userService.updateUserStats(otherUser);

            if (otherSession != null && otherSession.isOpen()) {
                sendMsg(otherSession, new ResultDTO("oppLeave", "对手主动退出，你获胜！积分+50"));
            }
            roomManagerService.removeRoom(room.getRoomId());
        }
        // 统一使用 WebSocketSessionManager 管理 Session
        WebSocketSessionManager.removeSession(currentUser.getUsername());
        System.out.println("WS连接关闭：sessionId = " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        error.printStackTrace();
    }

    // 处理注册
    private void handleRegister(Object data, Session session) {
        try {
            RegisterDTO regDto = OBJECT_MAPPER.convertValue(data, RegisterDTO.class);
            User user = new User();
            user.setUsername(regDto.getUsername());
            user.setPassword(regDto.getPassword());
            boolean success = userService.register(user);
            if (success) {
                sendMsg(session, new ResultDTO("regOk", "注册成功！请登录"));
            } else {
                sendMsg(session, new ResultDTO("regFail", "用户名已存在"));
            }
        } catch (Exception e) {
            sendMsg(session, new ResultDTO("regFail", "注册失败：" + e.getMessage()));
        }
    }

    // 处理登录
    private void handleLogin(Object data, Session session) {
        try {
            LoginDTO loginDto = OBJECT_MAPPER.convertValue(data, LoginDTO.class);
            User user = userService.login(loginDto.getUsername(), loginDto.getPassword());
            if (user == null) {
                sendMsg(session, new ResultDTO("loginFail", "账号或密码错误"));
                return;
            }
            //判断 “同一个账号是否在其他设备登录”（通过检查Session是否存在）
            if (WebSocketSessionManager.getSession(user.getUsername()) != null) {
                sendMsg(session, new ResultDTO("loginFail", "账号已在其他设备登录"));
                return;
            }

            // 统一使用 WebSocketSessionManager 管理 Session
            WebSocketSessionManager.addSession(user.getUsername(), session);
            // 生成轻量 token（仅用于 WS 绑定校验，不做长期持久化）
            String token = UUID.randomUUID().toString();
            WebSocketSessionManager.setToken(user.getUsername(), token);

            this.currentUser = user;
            Map<String, Object> payload = new HashMap<>();
            payload.put("user", user);
            payload.put("token", token);
            sendMsg(session, new ResultDTO("loginOk", payload));
        } catch (Exception e) {
            sendMsg(session, new ResultDTO("loginFail", "登录失败：" + e.getMessage()));
        }
    }

    // 处理绑定用户
    private void handleBindUser(Object data, Session session) {
        try {
            BindUserDTO bindDto = OBJECT_MAPPER.convertValue(data, BindUserDTO.class);
            String username = bindDto.getUsername();
            String token = bindDto.getToken();
            // 必须携带 token 且与服务端记录一致，防止仅凭用户名冒充
            String serverToken = WebSocketSessionManager.getToken(username);
            if (token == null || serverToken == null || !serverToken.equals(token)) {
                sendMsg(session, new ResultDTO("bindFail", "用户绑定失败：token无效，请重新登录"));
                return;
            }

            User user = userService.login(username, null);
            if (user == null) {
                sendMsg(session, new ResultDTO("bindFail", "用户不存在"));
                return;
            }

            this.currentUser = user;
            // 统一使用 WebSocketSessionManager 管理 Session
            WebSocketSessionManager.addSession(username, session);

            sendMsg(session, new ResultDTO("bindOk", "用户绑定成功"));
        } catch (Exception e) {
            sendMsg(session, new ResultDTO("bindFail", "绑定失败：" + e.getMessage()));
        }
    }

    // 处理开始匹配
    private void handleStartMatch(Session session) {
        if (currentUser == null) {//玩家登录 / 绑定后，这个变量会被赋值；没登录则为 null
            sendMsg(session, new ResultDTO("matchFail", "请先登录！"));
            return;
        }

        matchManagerService.joinMatch(currentUser);
        sendMsg(session, new ResultDTO("matchWaiting", "已加入" + getRank(currentUser) + "匹配队列，等待对手..."));
    }

    // 处理取消匹配
    private void handleCancelMatch(Session session) {
        if (currentUser == null) {
            sendMsg(session, new ResultDTO("cancelMatchFail", "请先登录！"));
            return;
        }

        matchManagerService.cancelMatch(currentUser);
        sendMsg(session, new ResultDTO("cancelMatchOk", "已取消匹配"));
    }

    // 处理落子
    private void handlePlaceChess(Object data, Session session) {
        try {
            PlaceDTO placeDto = OBJECT_MAPPER.convertValue(data, PlaceDTO.class);
            int x = placeDto.getX();
            int y = placeDto.getY();

            if (currentUser == null) {
                sendMsg(session, new ResultDTO("placeFail", "请先登录！"));
                return;
            }

            String roomId = null;
            Room roomByUserId = roomManagerService.getRoomByUserId(currentUser.getId());
            if (roomByUserId != null) {
                roomId = roomByUserId.getRoomId();
            }
            if (roomId == null && placeDto.getRoomId() != null) {
                roomId = placeDto.getRoomId();
            }

            Room room = roomManagerService.getRoomByRoomId(roomId);
            if (room == null) {
                sendMsg(session, new ResultDTO("placeFail", "未匹配到对手！"));
                return;
            }
            // 更新“房间活动时间”，避免长对局被定时清理误删
            room.setCreateTime(System.currentTimeMillis());
            // 标记对局已开始（只要任意一方首次成功进入落子流程，就认为对局已经开始）
            if (!room.isStarted()) {
                room.setStarted(true);
            }
            if (room.isEnd()) {
                sendMsg(session, new ResultDTO("placeFail", "棋局已结束！"));
                return;
            }
            //玩家回合校验
            if (!room.getCurrentPlayerId().equals(currentUser.getId())) {
                sendMsg(session, new ResultDTO("placeFail", "还没到你的回合！"));
                return;
            }
            // 落子位置有效性校验
            if (x < 0 || x >= 15 || y < 0 || y >= 15 || room.getChessboard()[x][y] != 0) {
                sendMsg(session, new ResultDTO("placeFail", "落子位置无效！"));
                return;
            }

            //给当前落子玩家分配一个 “棋子数字标识”
            int playerFlag = room.getUser1().getId().equals(currentUser.getId()) ? 1 : 2;
            room.getChessboard()[x][y] = playerFlag;

            // 1. 找到对手
            User opponent = room.getUser1().getId().equals(currentUser.getId()) ? room.getUser2() : room.getUser1();
            // 2. 获取对手的WS连接
            Session opponentSession = WebSocketSessionManager.getSession(opponent.getUsername());
            // 3. 封装落子数据
            ChessDTO chessDTO = new ChessDTO(x, y, playerFlag);
            // 4. 给当前玩家发落子成功的确认
            sendMsg(session, new ResultDTO("chessOk", chessDTO));
            // 5. 给对手发落子消息，同步棋盘
            if (opponentSession != null && opponentSession.isOpen()) {
                sendMsg(opponentSession, new ResultDTO("chessOk", chessDTO));
            }
            //判赢逻辑
            if (ChessUtil.isWin(room.getChessboard(), x, y, playerFlag)) {
                //标记房间 “棋局结束”，防止后续再落子
                room.setEnd(true);
                User winner = currentUser;
                User loser = opponent;

                // 积分计算（增加边界保护：防止负分，设置上限）
                int winnerNewScore = Math.min(winner.getScore() + 50, 99999); // 上限99999
                int loserNewScore = Math.max(loser.getScore() - 30, 0); // 下限0
                winner.setScore(winnerNewScore);
                loser.setScore(loserNewScore);
                winner.setTotalGames(winner.getTotalGames() + 1);
                loser.setTotalGames(loser.getTotalGames() + 1);
                winner.setWinGames(winner.getWinGames() + 1);

                userService.updateUserStats(winner);
                userService.updateUserStats(loser);

                sendMsg(session, new ResultDTO("win", "恭喜你获胜！积分+50，当前积分：" + winner.getScore()));
                if (opponentSession != null && opponentSession.isOpen()) {
                    sendMsg(opponentSession, new ResultDTO("lose", "你输了！积分-30，当前积分：" + loser.getScore()));
                }

                roomManagerService.removeRoom(room.getRoomId());
            } else {
                //如果刚落子的就是“该下棋的人”→ 切换“该下棋的人”为对手的ID
                room.setCurrentPlayerId(room.getCurrentPlayerId().equals(currentUser.getId()) ? opponent.getId() : currentUser.getId());
            }
        } catch (Exception e) {
            sendMsg(session, new ResultDTO("placeFail", "落子失败：" + e.getMessage()));
        }
    }

    // 处理退出登录
    private void handleLogout(Session session) {
        if (currentUser != null) {
            // 统一使用 WebSocketSessionManager 管理 Session
            WebSocketSessionManager.removeSession(currentUser.getUsername());
            // 主动退出时删除 token，防止 token 泄露
            WebSocketSessionManager.removeToken(currentUser.getUsername());
            this.currentUser = null;
        }
        sendMsg(session, new ResultDTO("logoutOk", "退出成功！"));
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 处理获取用户信息
    private void handleGetUserInfo(Object data, Session session) {
        try {
            if (currentUser == null) {
                sendMsg(session, new ResultDTO("userInfoFail", "请先登录"));
                return;
            }
            // 不信任前端传的 username，直接按当前会话绑定的用户查询
            User user = userService.login(currentUser.getUsername(), null);
            sendMsg(session, new ResultDTO("userInfoOk", user));
        } catch (Exception e) {
            sendMsg(session, new ResultDTO("userInfoFail", "获取用户信息失败"));
        }
    }

    // 处理获取房间信息
    private void handleGetRoom(Object data, Session session) {
        try {
            //把前端传的参数转成GetRoomDTO
            GetRoomDTO getRoomDTO = OBJECT_MAPPER.convertValue(data, GetRoomDTO.class);
            //优先按roomId查询（前端主动传了房间ID）
            if (getRoomDTO.getRoomId() != null) {
                Room room = roomManagerService.getRoomByRoomId(getRoomDTO.getRoomId());
                if (room != null) {
                    sendMsg(session, new ResultDTO("roomOk", "房间存在"));
                } else {
                    sendMsg(session, new ResultDTO("roomFail", "未匹配到房间"));
                }
                return;
            }
            //没传roomId → 先校验用户是否登录
            if (currentUser == null) {
                sendMsg(session, new ResultDTO("roomFail", "请先登录"));
                return;
            }
            //已登录 → 按当前用户ID查“自己所在的房间”
            Room room = roomManagerService.getRoomByUserId(currentUser.getId());
            if (room != null) {
                sendMsg(session, new ResultDTO("roomOk", "房间存在"));
            } else {
                sendMsg(session, new ResultDTO("roomFail", "未匹配到房间"));
            }
        } catch (Exception e) {
            sendMsg(session, new ResultDTO("roomFail", "获取房间失败"));
        }
    }

    // 获取用户段位
    private String getRank(User user) {
        int score = user.getScore();
        if (score <= 1000) return "入门级";
        else if (score <= 2000) return "进阶级";
        else return "高手级";
    }

    // 发送消息工具
    private void sendMsg(Session session, Object msg) {
        try {
            if (session != null && session.isOpen()) {
                //session.getBasicRemote()获取 WS 连接的基础通信通道，和pushMatchSuccess里的getBasicRemote()是同一个东西
                //sendText通过通信通道，把 JSON 字符串发送给前端
                session.getBasicRemote().sendText(OBJECT_MAPPER.writeValueAsString(msg));//OBJECT_MAPPER.writeValueAsString(msg)把对象转换成JSON 字符串
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}