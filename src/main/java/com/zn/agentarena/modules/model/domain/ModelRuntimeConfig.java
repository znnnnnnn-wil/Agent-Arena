package com.zn.agentarena.modules.model.domain;

import java.time.Duration;

/**
 * 单次模型运行配置。
 *
 * @param provider 模型供应商
 * @param modelName 模型名称
 * @param baseUrl 服务地址
 * @param apiKey API Key明文，仅允许存在于调用内存中
 * @param temperature 温度参数
 * @param maxTokens 最大输出Token
 * @param timeout 请求超时
 * @param maxRetries 最大重试次数
 * @author zhangxin
 * @date 2026/07/12
 */
public record ModelRuntimeConfig(
        ModelProvider provider,
        String modelName,
        String baseUrl,
        String apiKey,
        Double temperature,
        Integer maxTokens,
        Duration timeout,
        Integer maxRetries
) {
}
