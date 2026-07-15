package com.zn.agentarena.modules.model.service.impl;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.common.utils.ApiKeyCipher;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.dao.entity.ModelConfig;
import com.zn.agentarena.dao.entity.User;
import com.zn.agentarena.dao.mapper.ModelConfigMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelEndpointPolicy;
import com.zn.agentarena.modules.model.dto.request.CreateModelConfigRequest;
import com.zn.agentarena.modules.model.dto.response.ModelConfigResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 模型配置服务单元测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("模型配置服务单元测试")
class ModelConfigServiceImplTest {

    @Mock
    private ModelConfigMapper modelConfigMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ModelEndpointPolicy endpointPolicy;

    private ApiKeyCipher apiKeyCipher;
    private ModelConfigServiceImpl modelConfigService;

    @BeforeEach
    void setUp() {
        apiKeyCipher = new ApiKeyCipher("agent-arena-unit-test-secret-32chars");
        modelConfigService = new ModelConfigServiceImpl(modelConfigMapper, userMapper, apiKeyCipher,
                endpointPolicy, new AgentArenaProperties(null, null, null));
    }

    @Test
    @DisplayName("createModelConfig: 应加密API Key并隐藏响应中的敏感信息")
    void testCreateModelConfigSuccess() {
        CreateModelConfigRequest request = createRequest();
        when(userMapper.selectOneById(1L)).thenReturn(User.builder().id(1L).build());
        ArgumentCaptor<ModelConfig> captor = ArgumentCaptor.forClass(ModelConfig.class);

        ModelConfigResponse response = modelConfigService.createModelConfig(1L, request);

        verify(modelConfigMapper).insertSelective(captor.capture());
        ModelConfig inserted = captor.getValue();
        assertNotEquals("sk-test", inserted.getEncryptedApiKey());
        assertEquals("sk-test", apiKeyCipher.decrypt(inserted.getEncryptedApiKey()));
        assertTrue(response.getApiKeyConfigured());
        assertEquals("测试配置", response.getName());
    }

    @Test
    @DisplayName("createModelConfig: 用户不存在时不应写入数据库")
    void testCreateModelConfigUserNotFound() {
        when(userMapper.selectOneById(1L)).thenReturn(null);

        CustomException exception = assertThrows(CustomException.class,
                () -> modelConfigService.createModelConfig(1L, createRequest()));

        assertEquals(ResultCodeEnum.USER_NOT_FOUND, exception.getResultCodeEnum());
        verify(modelConfigMapper, never()).insertSelective(any());
    }

    @Test
    @DisplayName("getRuntimeConfig: 应解密密钥并转换模型参数")
    void testGetRuntimeConfigSuccess() {
        ModelConfig config = ModelConfig.builder()
                .id(10L).userId(1L).provider(ModelProvider.DEEPSEEK)
                .modelName("deepseek-chat").baseUrl("https://api.deepseek.com/v1")
                .encryptedApiKey(apiKeyCipher.encrypt("secret-key"))
                .modelOptions(Map.of("temperature", 0.6, "maxTokens", 2048, "timeoutSeconds", 30))
                .enabled(true).build();
        when(modelConfigMapper.selectOneById(10L)).thenReturn(config);

        ModelRuntimeConfig runtimeConfig = modelConfigService.getRuntimeConfig(1L, 10L);

        assertEquals("secret-key", runtimeConfig.apiKey());
        assertEquals(0.6, runtimeConfig.temperature());
        assertEquals(2048, runtimeConfig.maxTokens());
        assertEquals(30, runtimeConfig.timeout().toSeconds());
    }

    @Test
    @DisplayName("deleteModelConfig: 应软删除以保留历史对局外键")
    void testDeleteModelConfigUsesSoftDelete() {
        ModelConfig config = ModelConfig.builder().id(10L).userId(1L)
                .provider(ModelProvider.OPENAI).modelName("gpt-test")
                .enabled(true).deleted(false).build();
        when(modelConfigMapper.selectOneById(10L)).thenReturn(config);
        when(modelConfigMapper.update(config)).thenReturn(1);

        modelConfigService.deleteModelConfig(1L, 10L);

        assertFalse(config.getEnabled());
        assertTrue(config.getDeleted());
        verify(modelConfigMapper).update(config);
    }

    private CreateModelConfigRequest createRequest() {
        CreateModelConfigRequest request = new CreateModelConfigRequest();
        request.setName("  测试配置  ");
        request.setProvider(ModelProvider.OPENAI);
        request.setModelName("gpt-test");
        request.setApiKey("sk-test");
        request.setModelOptions(Map.of("temperature", 0.2));
        return request;
    }
}
