package com.gobang.service;

import com.gobang.entity.Room;

public interface RoomManagerService {
    // 添加房间
    void addRoom(Room room);
    // 通过用户ID查房间
    Room getRoomByUserId(Integer userId);
    // 通过房间ID查房间
    Room getRoomByRoomId(String roomId);
    // 绑定用户与房间
    void addUserRoom(Integer userId, String roomId);
    // 移除房间
    void removeRoom(String roomId);
}