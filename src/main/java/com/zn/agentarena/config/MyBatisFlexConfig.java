package com.zn.agentarena.config;

import com.mybatisflex.core.handler.JacksonTypeHandler;
import com.mybatisflex.spring.boot.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** 为 JSON Map 字段显式注册跨平台一致的 MyBatis 类型处理器。 */
@Configuration(proxyBeanMethods = false)
public class MyBatisFlexConfig {

    @Bean
    ConfigurationCustomizer jsonMapTypeHandlerCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(Map.class, JacksonTypeHandler.class);
    }
}
