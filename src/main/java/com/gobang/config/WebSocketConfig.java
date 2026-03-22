package com.gobang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration // 配置类，Spring Boot 启动时，会自动扫描所有带有 @Configuration 注解的类
public class WebSocketConfig {
    // 启动项目时，这个bean遍历项目中所有类，找到带@ServerEndpoint注解的类，将这些类注册为 WebSocket 端点，使其可以被客户端通过 WebSocket 连接访问
    @Bean // Spring 容器在处理 WebSocketConfig 类时，会自动执行所有带有 @Bean 注解的方法，@Bean注解默认会把方法名作为Bean的名称
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}