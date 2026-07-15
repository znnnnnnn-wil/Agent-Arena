package com.zn.agentarena.dao.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.common.enums.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 游戏表(Game)，存储游戏基础信息和运行状态。
 *
 * @author zhangxin
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("aa_game")
public class Game implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 游戏ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 创建用户ID */
    private Long ownerUserId;

    /** 游戏名称 */
    private String name;

    /** 游戏模式 */
    private GameMode mode;

    /** 游戏状态 */
    private GameStatus status;

    /** 当前游戏阶段 */
    private String currentPhase;

    /** 当前轮次 */
    private Integer currentRound;

    /** 最大玩家数 */
    private Integer maxPlayers;

    /** 是否公开 */
    private Boolean publicGame;

    /** 游戏规则配置 */
    @Column(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> ruleConfig;

    /** 随机种子 */
    private Long randomSeed;

    /** 乐观锁版本 */
    @Column(version = true)
    private Integer version;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime finishTime;

    /** 创建时间 */
    @Column(onInsertValue = "now(3)")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(onInsertValue = "now(3)", onUpdateValue = "now(3)")
    private LocalDateTime updateTime;
}
