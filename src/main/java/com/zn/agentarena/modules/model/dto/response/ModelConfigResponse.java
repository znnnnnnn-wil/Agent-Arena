package com.zn.agentarena.modules.model.dto.response;

import com.zn.agentarena.modules.model.domain.ModelProvider;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 模型配置响应，不包含API Key明文和密文。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
@Builder
public class ModelConfigResponse {
    /** 模型配置 ID。 */
    private Long id;
    /** 配置名称。 */
    private String name;
    /** 模型服务提供商。 */
    private ModelProvider provider;
    /** 调用的模型名称。 */
    private String modelName;
    /** 自定义模型服务地址。 */
    private String baseUrl;
    /** 是否已配置 API Key，不返回密钥内容。 */
    private Boolean apiKeyConfigured;
    /** 模型调用参数。 */
    private Map<String, Object> modelOptions;
    /** 配置是否启用。 */
    private Boolean enabled;
    /** 配置创建时间。 */
    private LocalDateTime createTime;
    /** 配置最后更新时间。 */
    private LocalDateTime updateTime;
}
