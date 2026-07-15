package com.zn.agentarena.modules.model.controller;

import com.zn.agentarena.common.Response;
import com.zn.agentarena.modules.model.dto.request.CreateModelConfigRequest;
import com.zn.agentarena.modules.model.dto.request.UpdateModelConfigRequest;
import com.zn.agentarena.modules.model.dto.response.ModelConfigResponse;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import com.zn.agentarena.modules.model.service.IModelConfigService;
import com.zn.agentarena.common.utils.FixedWindowRateLimiter;
import com.zn.agentarena.config.AgentArenaProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.Duration;

/**
 * 模型配置控制器。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/model-configs")
public class ModelConfigController {

    private final IModelConfigService modelConfigService;
    private final ModelGateway modelGateway;
    private final FixedWindowRateLimiter rateLimiter;
    private final AgentArenaProperties properties;

    /** 创建模型配置。 */
    @PostMapping
    public Response<ModelConfigResponse> create(@AuthenticationPrincipal Long userId,
                                                @Valid @RequestBody CreateModelConfigRequest request) {
        rateLimiter.check(userId, "model-create", 10);
        return Response.success(modelConfigService.createModelConfig(userId, request));
    }

    /** 获取用户的模型配置。 */
    @GetMapping
    public Response<List<ModelConfigResponse>> list(@AuthenticationPrincipal Long userId) {
        return Response.success(modelConfigService.getModelConfigs(userId));
    }

    /** 更新模型配置。 */
    @PutMapping("/{configId}")
    public Response<ModelConfigResponse> update(@AuthenticationPrincipal Long userId,
                                                @PathVariable @Positive Long configId,
                                                @Valid @RequestBody UpdateModelConfigRequest request) {
        return Response.success(modelConfigService.updateModelConfig(userId, configId, request));
    }

    /** 删除模型配置。 */
    @DeleteMapping("/{configId}")
    public Response<Void> delete(@AuthenticationPrincipal Long userId,
                                 @PathVariable @Positive Long configId) {
        modelConfigService.deleteModelConfig(userId, configId);
        return Response.success();
    }

    /** 测试已保存模型配置是否可以正常调用。 */
    @PostMapping("/{configId}/test")
    public Response<Map<String, Object>> testConnection(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long configId) {
        ModelRuntimeConfig source = modelConfigService.getRuntimeConfig(userId, configId);
        rateLimiter.check(userId, "model-test", Math.max(1, properties.model().callsPerMinute() / 2));
        ModelRuntimeConfig testConfig = new ModelRuntimeConfig(source.provider(), source.modelName(), source.baseUrl(),
                source.apiKey(), 0.0, 32, Duration.ofSeconds(15), 0);
        long start = System.currentTimeMillis();
        String content = modelGateway.generate(testConfig, "Reply with exactly: OK");
        return Response.success(Map.of("connected", true, "latencyMs", System.currentTimeMillis() - start,
                "response", content == null ? "" : content.substring(0, Math.min(100, content.length()))));
    }
}
