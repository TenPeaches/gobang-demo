package com.gobang.mapper;

import com.gobang.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper//告诉 Spring Boot 这是 MyBatis 的 Mapper 接口，启动时自动生成实现类，不需要手动编写实现类了
public interface UserMapper {
    // 注册：插入用户
    int insertUser(User user);

    // 登录：根据用户名查询用户信息
    User selectUserByUsername(String username);

    // 更新积分/场次/胜场
    int updateUserStats(@Param("id") Integer id,
                        @Param("score") Integer score,
                        @Param("totalGames") Integer totalGames,
                        @Param("winGames") Integer winGames);
}