package com.zn.agentarena.modules.model.service;

import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.dto.request.CreateModelConfigRequest;
import com.zn.agentarena.modules.model.dto.request.UpdateModelConfigRequest;
import com.zn.agentarena.modules.model.dto.response.ModelConfigResponse;

import java.util.List;

/**
 * 模型配置服务接口。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public interface IModelConfigService {

    /** 创建模型配置。 */
    ModelConfigResponse createModelConfig(Long userId, CreateModelConfigRequest request);

    /** 查询用户的全部模型配置。 */
    List<ModelConfigResponse> getModelConfigs(Long userId);

    /** 更新指定模型配置。 */
    ModelConfigResponse updateModelConfig(Long userId, Long configId, UpdateModelConfigRequest request);

    /** 删除指定模型配置。 */
    void deleteModelConfig(Long userId, Long configId);

    /** 获取可供模型调用的运行时配置。 */
    ModelRuntimeConfig getRuntimeConfig(Long userId, Long configId);
}
