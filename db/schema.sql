-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS gobang_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 使用数据库
USE gobang_db;

-- 创建用户表
create table user
(
    id          int auto_increment comment '用户ID'
        primary key,
    username    varchar(20)     not null comment '用户名',
    password    varchar(20)     not null comment '密码',
    score       int default 800 null comment '初始积分800',
    total_games int default 0   null comment '总场次',
    win_games   int default 0   null comment '胜场数',
    constraint username
        unique (username)
);