package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * 中国模型OpenAI兼容工厂测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("中国模型兼容工厂测试")
class OpenAiCompatibleModelFactoryTest {

    @Test
    @DisplayName("create: 豆包等供应商应使用默认兼容地址创建客户端")
    void testChineseProviders() {
        OpenAiCompatibleModelFactory factory = new OpenAiCompatibleModelFactory(mock(ModelEndpointPolicy.class));
        for (ModelProvider provider : new ModelProvider[]{
                ModelProvider.DOUBAO, ModelProvider.QIANFAN, ModelProvider.ZHIPU,
                ModelProvider.MOONSHOT, ModelProvider.YI, ModelProvider.MINIMAX,
                ModelProvider.HUNYUAN, ModelProvider.BAICHUAN, ModelProvider.SILICONFLOW}) {
            assertNotNull(factory.create(new ModelRuntimeConfig(provider, "test-model", null,
                    "test-key", 0.2, 256, Duration.ofSeconds(5), 1)));
        }
    }
}
