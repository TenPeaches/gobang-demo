package com.gobang.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor // Lombok注解：生成无参构造
@AllArgsConstructor // Lombok注解：生成全参构造（替代手动写的带参构造）
public class PlaceDTO {
    private int x;
    private int y;
    private String roomId;
}
