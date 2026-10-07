package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.Map;

/** 实测专用模式参数；复用生产DNS校验与传输，不改变生产默认行为。 */
public final class RealModelTestFactory implements ModelFactory {
    private final ModelEndpointPolicy policy;
    public RealModelTestFactory(ModelEndpointPolicy policy) { this.policy = policy; }
    @Override public ChatModel create(ModelRuntimeConfig config) {
        String endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        policy.validate(config.provider(), endpoint);
        return OpenAiChatModel.builder()
                .httpClientBuilder(new ValidatedModelHttpClientBuilder(policy, endpoint))
                .baseUrl(endpoint).modelName(config.modelName()).apiKey(config.apiKey())
                .temperature(config.temperature()).maxTokens(config.maxTokens())
                .timeout(config.timeout()).maxRetries(0)
                .customParameters(Map.of("enable_thinking", false))
                .logRequests(false).logResponses(false).build();
    }
}
