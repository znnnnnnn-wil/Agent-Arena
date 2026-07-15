package com.zn.agentarena.modules.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 更新模型配置请求。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
public class UpdateModelConfigRequest {

    /** 配置名称 */
    @Size(max = 64, message = "配置名称不能超过64个字符")
    private String name;

    /** 模型名称 */
    @Size(max = 128, message = "模型名称不能超过128个字符")
    private String modelName;

    /** 自定义服务地址 */
    @Size(max = 512, message = "服务地址不能超过512个字符")
    private String baseUrl;

    /** 新的API Key，为空时保留原值 */
    @Size(max = 4096, message = "API Key长度不合法")
    private String apiKey;

    /** 模型调用参数 */
    private Map<String, Object> modelOptions;

    /** 是否启用 */
    private Boolean enabled;
}
