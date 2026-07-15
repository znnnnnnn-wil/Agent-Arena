package com.zn.agentarena.modules.model.dto.request;

import com.zn.agentarena.modules.model.domain.ModelProvider;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Map;

/**
 * 创建模型配置请求。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
public class CreateModelConfigRequest {

    /** 配置名称 */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 64, message = "配置名称不能超过64个字符")
    private String name;

    /** 模型供应商 */
    @NotNull(message = "模型供应商不能为空")
    private ModelProvider provider;

    /** 模型名称 */
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 128, message = "模型名称不能超过128个字符")
    private String modelName;

    /** 自定义服务地址 */
    @Size(max = 512, message = "服务地址不能超过512个字符")
    private String baseUrl;

    /** 模型API Key */
    @Size(max = 4096, message = "API Key长度不合法")
    private String apiKey;

    /** 模型调用参数 */
    private Map<String, Object> modelOptions;
}
