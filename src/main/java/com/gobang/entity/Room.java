package com.gobang.entity;

import lombok.Data;

@Data
public class Room {
    private String roomId;
    private User user1;
    private User user2;
    private Integer currentPlayerId;// 当前回合玩家ID
    private boolean isEnd;//棋局是否结束
    private int[][] chessboard;// 15x15棋盘：0=空位置，1=玩家1的棋，2=玩家2的棋
    private long createTime; // 房间创建时间（毫秒数）：用于后续清理超时未开始的房间
    private boolean started;// 标记对局是否已经开始（有任意一方落过子）

    // 构造方法
    public Room(String roomId, User user1, User user2) {
        this.roomId = roomId;
        this.user1 = user1;
        this.user2 = user2;
        this.currentPlayerId = user1.getId(); // 默认先手为user1
        this.isEnd = false;
        this.chessboard = new int[15][15]; // 15x15棋盘
        this.createTime = System.currentTimeMillis(); // 初始化创建时间
        this.started = false; // 初始还没开始下棋
    }
}