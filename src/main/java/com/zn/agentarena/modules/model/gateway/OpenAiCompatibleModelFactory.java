package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.Map;

/**
 * OpenAI兼容协议模型工厂。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleModelFactory implements ModelFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Map<com.zn.agentarena.modules.model.domain.ModelProvider, String> DEFAULT_BASE_URLS = Map.ofEntries(
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.OPENAI, "https://api.openai.com/v1"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.DEEPSEEK, "https://api.deepseek.com"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.QWEN, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.DOUBAO, "https://ark.cn-beijing.volces.com/api/v3"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.QIANFAN, "https://qianfan.baidubce.com/v2"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.ZHIPU, "https://open.bigmodel.cn/api/paas/v4"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.MOONSHOT, "https://api.moonshot.cn/v1"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.YI, "https://api.lingyiwanwu.com/v1"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.MINIMAX, "https://api.minimaxi.com/v1"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.HUNYUAN, "https://api.hunyuan.cloud.tencent.com/v1"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.BAICHUAN, "https://api.baichuan-ai.com/v1"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.SILICONFLOW, "https://api.siliconflow.cn/v1"),
            Map.entry(com.zn.agentarena.modules.model.domain.ModelProvider.OLLAMA, "http://localhost:11434/v1")
    );
    private final ModelEndpointPolicy endpointPolicy;

    @Override
    /** 根据运行时配置创建 OpenAI 兼容的聊天模型客户端。 */
    public ChatModel create(ModelRuntimeConfig config) {
        validateConfig(config);
        String baseUrl = StringUtils.hasText(config.baseUrl())
                ? config.baseUrl() : DEFAULT_BASE_URLS.get(config.provider());
        boolean local = config.provider() == com.zn.agentarena.modules.model.domain.ModelProvider.OLLAMA;
        if (!local) endpointPolicy.validate(config.provider(), baseUrl);
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .httpClientBuilder(local ? JdkHttpClient.builder().httpClientBuilder(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER))
                        : new ValidatedModelHttpClientBuilder(endpointPolicy, baseUrl))
                .modelName(config.modelName())
                .apiKey(config.apiKey())
                .timeout(config.timeout() == null ? DEFAULT_TIMEOUT : config.timeout())
                .maxRetries(config.maxRetries() == null ? 2 : config.maxRetries())
                .logRequests(false)
                .logResponses(false);

        if (StringUtils.hasText(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        if (config.temperature() != null) {
            builder.temperature(config.temperature());
        }
        if (config.maxTokens() != null) {
            builder.maxTokens(config.maxTokens());
        }
        return builder.build();
    }

    private void validateConfig(ModelRuntimeConfig config) {
        if (config == null || config.provider() == null) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "模型供应商不能为空");
        }
        if (!StringUtils.hasText(config.modelName())) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "模型名称不能为空");
        }
        if (!StringUtils.hasText(config.apiKey()) && config.provider() != com.zn.agentarena.modules.model.domain.ModelProvider.OLLAMA) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "模型API Key不能为空");
        }
        if (config.provider() == com.zn.agentarena.modules.model.domain.ModelProvider.SPARK
                && !StringUtils.hasText(config.baseUrl())) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "讯飞星火需要配置兼容接口Base URL");
        }
    }
}
