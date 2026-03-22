package com.gobang.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor // Lombok注解：生成无参构造
@AllArgsConstructor // Lombok注解：生成全参构造（替代手动写的带参构造）
public class BindUserDTO {
    private String username;
    // 轻量鉴权 token：登录成功后由后端下发，前端重连/换页绑定时携带，用于防止仅凭用户名冒充
    private String token;
}
