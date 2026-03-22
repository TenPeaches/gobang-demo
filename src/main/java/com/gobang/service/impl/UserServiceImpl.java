package com.gobang.service.impl;

import com.gobang.entity.User;
import com.gobang.mapper.UserMapper;
import com.gobang.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service//标记这是Spring的服务类，启动时自动创建对象，这样其他类就可以通过依赖注入来使用这个服务
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean register(User user) {
        if (userMapper.selectUserByUsername(user.getUsername()) != null) {
            return false; // 用户名已存在
        }
        // 初始数据：积分800、场次0、胜场0
        user.setScore(800);
        user.setTotalGames(0);
        user.setWinGames(0);
        return userMapper.insertUser(user) > 0;
    }

    @Override
    public User login(String username, String password) {
        // 1. 根据用户名查用户（不管密码，先查出来）
        User user = userMapper.selectUserByUsername(username);
        if (user == null) {
            return null; // 用户名不存在
        }
        // 2. 对应 “绑定 WS 用户”—— 用户已经在前端登录过（密码已经校验过），现在只是要把用户名和 WS 会话绑定，不需要再输密码，所以前端调用这个方法时，password传null；
        if (password == null) {
            return user;
        }
        // 3. 密码不为null（登录场景），校验密码
        if (user.getPassword().equals(password)) {
            return user;
        }
        return null; // 密码错误
    }

    @Override
    public boolean updateUserStats(User user) {
        return userMapper.updateUserStats(
                user.getId(), user.getScore(), user.getTotalGames(), user.getWinGames()
        ) > 0;
    }
}