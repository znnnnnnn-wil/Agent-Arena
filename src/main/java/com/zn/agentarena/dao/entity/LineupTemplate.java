package com.zn.agentarena.dao.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import com.zn.agentarena.common.enums.GameMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** 可复用的模型、人格与席位阵容快照。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("aa_lineup_template")
public class LineupTemplate {

    /** 阵容模板ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 阵容模板名称 */
    private String name;

    /** 适用的游戏模式 */
    private GameMode mode;

    /** 阵容配置，包括模型、人格、席位和游戏规则等快照信息 */
    @Column(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> templateConfig;

    /** 创建时间 */
    @Column(onInsertValue = "now(3)")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(onInsertValue = "now(3)", onUpdateValue = "now(3)")
    private LocalDateTime updateTime;
}
