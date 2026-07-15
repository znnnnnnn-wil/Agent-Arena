package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;

/**
 * 游戏模型调用网关。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public interface ModelGateway {

    /** 调用模型生成文本。 */
    String generate(ModelRuntimeConfig config, String prompt);

    /** 调用模型并将JSON结果转换为指定类型。 */
    <T> T generateStructured(ModelRuntimeConfig config, String prompt, Class<T> responseType);
}
