package com.zn.agentarena.modules.base.controller;

import com.zn.agentarena.common.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 基础健康检查控制器。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@RestController
public class BaseHealthController {

    /**
     * 获取服务运行状态。
     *
     * @return 服务状态
     */
    @GetMapping("/api/health")
    public Response<Map<String, String>> health() {
        return Response.success(Map.of("status", "UP", "service", "agent-arena"));
    }
}
