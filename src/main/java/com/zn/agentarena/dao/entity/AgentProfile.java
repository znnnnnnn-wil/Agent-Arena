package com.zn.agentarena.dao.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** 用户可复用的轻量 Agent 人格。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("aa_agent_profile")
public class AgentProfile {

    /** 人格档案ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 人格名称 */
    private String name;

    /** 人格描述 */
    private String description;

    /** 人格配置，包括特质、表达风格和行为指引等信息 */
    @Column(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> personality;

    /** 逻辑删除标记 */
    private Boolean deleted;

    /** 创建时间 */
    @Column(onInsertValue = "now(3)")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(onInsertValue = "now(3)", onUpdateValue = "now(3)")
    private LocalDateTime updateTime;
}
