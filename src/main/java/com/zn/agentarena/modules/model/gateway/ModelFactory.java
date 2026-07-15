package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import dev.langchain4j.model.chat.ChatModel;

/**
 * LangChain4j模型工厂接口。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public interface ModelFactory {

    /**
     * 根据运行配置创建模型客户端。
     *
     * @param config 运行配置
     * @return 模型客户端
     */
    ChatModel create(ModelRuntimeConfig config);
}
