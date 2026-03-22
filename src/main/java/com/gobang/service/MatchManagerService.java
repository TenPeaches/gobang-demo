package com.gobang.service;

import com.gobang.entity.User;

public interface MatchManagerService {
    // 玩家加入匹配队列
    void joinMatch(User user);
    
    // 玩家取消匹配
    void cancelMatch(User user);
}