package com.gobang.entity;

import lombok.Data;

@Data // Lombok自动生成get/set
public class User {
    private Integer id;
    private String username;
    private String password;
    private Integer score;
    private Integer totalGames; // 总对局数，对应数据库total_games
    private Integer winGames;   // 胜场数，对应数据库win_games
}