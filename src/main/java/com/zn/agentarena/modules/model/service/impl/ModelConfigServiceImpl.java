package com.zn.agentarena.modules.model.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.common.utils.ApiKeyCipher;
import com.zn.agentarena.dao.entity.ModelConfig;
import com.zn.agentarena.dao.entity.User;
import com.zn.agentarena.dao.mapper.ModelConfigMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.dto.request.CreateModelConfigRequest;
import com.zn.agentarena.modules.model.dto.request.UpdateModelConfigRequest;
import com.zn.agentarena.modules.model.dto.response.ModelConfigResponse;
import com.zn.agentarena.modules.model.gateway.ModelEndpointPolicy;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.model.service.IModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * 模型配置服务实现类。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements IModelConfigService {

    private final ModelConfigMapper modelConfigMapper;
    private final UserMapper userMapper;
    private final ApiKeyCipher apiKeyCipher;
    private final ModelEndpointPolicy endpointPolicy;
    private final AgentArenaProperties properties;

    /** 创建并加密保存模型配置。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigResponse createModelConfig(Long userId, CreateModelConfigRequest request) {
        ensureUserExists(userId);
        validateApiKey(request.getProvider(), request.getApiKey(), false);
        endpointPolicy.validate(request.getProvider(), request.getBaseUrl());
        validateModelOptions(request.getModelOptions());
        ModelConfig config = ModelConfig.builder()
                .userId(userId)
                .name(normalizeRequiredText(request.getName(), "配置名称不能为空"))
                .provider(request.getProvider())
                .modelName(normalizeRequiredText(request.getModelName(), "模型名称不能为空"))
                .baseUrl(normalizeNullableText(request.getBaseUrl()))
                .encryptedApiKey(apiKeyCipher.encrypt(request.getApiKey()))
                .modelOptions(request.getModelOptions())
                .enabled(true)
                .deleted(false)
                .build();
        modelConfigMapper.insertSelective(config);
        return toResponse(config);
    }

    /** 查询当前用户的模型配置列表。 */
    @Override
    public List<ModelConfigResponse> getModelConfigs(Long userId) {
        ensureUserExists(userId);
        return modelConfigMapper.selectListByQuery(QueryWrapper.create()
                        .eq(ModelConfig::getUserId, userId)
                        .eq(ModelConfig::getDeleted, false)
                        .orderBy(ModelConfig::getCreateTime, false))
                .stream().map(this::toResponse).toList();
    }

    /** 更新用户拥有的模型配置。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigResponse updateModelConfig(Long userId, Long configId, UpdateModelConfigRequest request) {
        ModelConfig config = getOwnedConfig(userId, configId);
        if (request.getName() != null) {
            config.setName(normalizeRequiredText(request.getName(), "配置名称不能为空"));
        }
        if (request.getModelName() != null) {
            config.setModelName(normalizeRequiredText(request.getModelName(), "模型名称不能为空"));
        }
        if (request.getBaseUrl() != null) {
            endpointPolicy.validate(config.getProvider(), request.getBaseUrl());
            config.setBaseUrl(normalizeNullableText(request.getBaseUrl()));
        }
        if (StringUtils.hasText(request.getApiKey())) {
            config.setEncryptedApiKey(apiKeyCipher.encrypt(request.getApiKey().trim()));
        }
        if (request.getModelOptions() != null) {
            validateModelOptions(request.getModelOptions());
            config.setModelOptions(request.getModelOptions());
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        updateConfigOrThrow(config);
        return toResponse(config);
    }

    /** 删除用户拥有的模型配置。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModelConfig(Long userId, Long configId) {
        ModelConfig config = getOwnedConfig(userId, configId);
        config.setEnabled(false);
        config.setDeleted(true);
        updateConfigOrThrow(config);
    }

    /** 读取并解密用于模型调用的运行时配置。 */
    @Override
    public ModelRuntimeConfig getRuntimeConfig(Long userId, Long configId) {
        ModelConfig config = getOwnedConfig(userId, configId);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw CustomException.create(ResultCodeEnum.MODEL_CONFIG_NOT_FOUND, "模型配置已停用");
        }
        endpointPolicy.validate(config.getProvider(), config.getBaseUrl());
        return new ModelRuntimeConfig(config.getProvider(), config.getModelName(), config.getBaseUrl(),
                apiKeyCipher.decrypt(config.getEncryptedApiKey()),
                getDoubleOption(config, "temperature", 0.3),
                getIntegerOption(config, "maxTokens", 1024),
                Duration.ofSeconds(getIntegerOption(config, "timeoutSeconds",
                        Math.toIntExact(properties.model().readTimeout().toSeconds()))),
                getIntegerOption(config, "maxRetries", properties.model().maxRetries()));
    }

    private void ensureUserExists(Long userId) {
        if (userId == null || userMapper.selectOneById(userId) == null) {
            throw CustomException.create(ResultCodeEnum.USER_NOT_FOUND);
        }
    }

    private ModelConfig getOwnedConfig(Long userId, Long configId) {
        if (userId == null || configId == null) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "用户ID和配置ID不能为空");
        }
        ModelConfig config = modelConfigMapper.selectOneById(configId);
        if (config == null || Boolean.TRUE.equals(config.getDeleted()) || !userId.equals(config.getUserId())) {
            throw CustomException.create(ResultCodeEnum.MODEL_CONFIG_NOT_FOUND);
        }
        return config;
    }

    private void updateConfigOrThrow(ModelConfig config) {
        if (modelConfigMapper.update(config) != 1) {
            throw CustomException.create(ResultCodeEnum.FAIL, "模型配置更新失败，请重试");
        }
    }

    private void validateApiKey(ModelProvider provider, String apiKey, boolean allowEmpty) {
        if (!allowEmpty && provider != ModelProvider.OLLAMA && !StringUtils.hasText(apiKey)) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "模型API Key不能为空");
        }
    }

    private void validateModelOptions(java.util.Map<String, Object> options) {
        if (options == null) {
            return;
        }
        if (options.size() > 20) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "模型参数数量不能超过20个");
        }
        validateNumberRange(options, "temperature", 0, 2);
        validateNumberRange(options, "maxTokens", 1, 32_768);
        validateNumberRange(options, "timeoutSeconds", 1, 300);
        validateNumberRange(options, "maxRetries", 0, 5);
    }

    private void validateNumberRange(java.util.Map<String, Object> options, String key, double minimum, double maximum) {
        Object value = options.get(key);
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < minimum || number.doubleValue() > maximum) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR,
                    key + "必须在" + minimum + "到" + maximum + "之间");
        }
    }

    private String normalizeRequiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, message);
        }
        return value.trim();
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer getIntegerOption(ModelConfig config, String key, Integer defaultValue) {
        Object value = config.getModelOptions() == null ? null : config.getModelOptions().get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private Double getDoubleOption(ModelConfig config, String key, Double defaultValue) {
        Object value = config.getModelOptions() == null ? null : config.getModelOptions().get(key);
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    private ModelConfigResponse toResponse(ModelConfig config) {
        return ModelConfigResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .provider(config.getProvider())
                .modelName(config.getModelName())
                .baseUrl(config.getBaseUrl())
                .apiKeyConfigured(StringUtils.hasText(config.getEncryptedApiKey()))
                .modelOptions(config.getModelOptions())
                .enabled(config.getEnabled())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
