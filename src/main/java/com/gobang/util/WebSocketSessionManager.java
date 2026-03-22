package com.gobang.util;

import javax.websocket.Session;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket会话管理器：保存用户名→Session的映射，方便服务端主动推送消息
 * 核心：用ConcurrentHashMap保证多线程下的线程安全（多房间/多用户并发）
 */
public class WebSocketSessionManager {
    // 线程安全的Map：key=用户名，value=对应的WebSocket Session
    private static final ConcurrentHashMap<String, Session> SESSION_MAP = new ConcurrentHashMap<>();
    // 轻量鉴权 token：key=用户名，value=token（用于跨页面/重连绑定 WS 时校验）
    private static final ConcurrentHashMap<String, String> TOKEN_MAP = new ConcurrentHashMap<>();

    // 添加会话（用户登录/绑定WS时调用）
    public static void addSession(String username, Session session) {
        SESSION_MAP.put(username, session);
    }

    // 保存/更新 token（登录成功时调用）
    public static void setToken(String username, String token) {
        TOKEN_MAP.put(username, token);
    }

    // 获取 token
    public static String getToken(String username) {
        return TOKEN_MAP.get(username);
    }

    // 获取会话（服务端推送消息时调用）
    public static Session getSession(String username) {
        return SESSION_MAP.get(username);
    }

    // 移除会话（连接断开时调用，但保留 token 用于重连）
    public static void removeSession(String username) {
        SESSION_MAP.remove(username);
        // 注意：不删除 token，因为页面跳转/重连时需要 token 重新绑定
    }

    // 移除 token（仅在用户主动退出登录时调用）
    public static void removeToken(String username) {
        TOKEN_MAP.remove(username);
    }
}