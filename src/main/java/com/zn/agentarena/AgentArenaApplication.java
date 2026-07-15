package com.zn.agentarena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent Arena 服务端启动类。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@SpringBootApplication
@MapperScan("com.zn.agentarena.dao.mapper")
@ConfigurationPropertiesScan
@EnableScheduling
public class AgentArenaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentArenaApplication.class, args);
    }
}
