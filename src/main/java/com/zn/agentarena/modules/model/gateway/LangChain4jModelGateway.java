package com.zn.agentarena.modules.model.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于LangChain4j的模型调用网关。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LangChain4jModelGateway implements ModelGateway {

    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private final ModelCallConcurrencyLimiter concurrencyLimiter;

    @Override
    /** 调用模型生成纯文本响应。 */
    public String generate(ModelRuntimeConfig config, String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "模型提示词不能为空");
        }
        return concurrencyLimiter.execute(() -> {
            try {
                ChatModel model = modelFactory.create(config);
                return model.chat(prompt);
            } catch (CustomException exception) {
                throw exception;
            } catch (Exception exception) {
                log.warn("模型调用失败: provider={}, model={}, reason={}", config.provider(), config.modelName(),
                        exception.getClass().getSimpleName());
                throw CustomException.create(ResultCodeEnum.MODEL_CALL_FAILED, "模型调用失败");
            }
        });
    }

    @Override
    /** 调用模型并将 JSON 响应转换为指定类型。 */
    public <T> T generateStructured(ModelRuntimeConfig config, String prompt, Class<T> responseType) {
        if (responseType == null) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "结构化响应类型不能为空");
        }
        String json = generate(config, prompt);
        try {
            return objectMapper.readValue(stripMarkdownCodeFence(json), responseType);
        } catch (JsonProcessingException exception) {
            throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型未返回合法的结构化数据");
        }
    }

    private String stripMarkdownCodeFence(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        int firstBrace = normalized.indexOf('{');
        int lastBrace = normalized.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            normalized = normalized.substring(firstBrace, lastBrace + 1);
        }
        return normalized;
    }
}
