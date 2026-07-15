package com.zn.agentarena.dao.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 模型配置表(ModelConfig)，存储用户的大模型连接配置。
 *
 * @author zhangxin
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("aa_model_config")
public class ModelConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模型配置ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 配置名称 */
    private String name;

    /** 模型供应商 */
    private ModelProvider provider;

    /** 模型名称 */
    private String modelName;

    /** 自定义服务地址 */
    private String baseUrl;

    /** 加密后的API Key */
    @Column(isLarge = true)
    private String encryptedApiKey;

    /** 模型调用参数 */
    @Column(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> modelOptions;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否已被用户删除；保留记录以维持历史对局外键。 */
    private Boolean deleted;

    /** 创建时间 */
    @Column(onInsertValue = "now(3)")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(onInsertValue = "now(3)", onUpdateValue = "now(3)")
    private LocalDateTime updateTime;
}
