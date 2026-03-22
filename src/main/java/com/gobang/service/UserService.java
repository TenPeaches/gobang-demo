package com.gobang.service;

import com.gobang.entity.User;

public interface UserService {
    // 注册（先查用户名是否存在）
    boolean register(User user);

    // 登录（验证账号密码）
    User login(String username, String password);

    // 更新用户积分/场次/胜场
    boolean updateUserStats(User user);
}