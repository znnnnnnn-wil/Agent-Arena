package com.zn.agentarena.dao.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import com.zn.agentarena.common.enums.PlayerType;
import com.zn.agentarena.modules.game.domain.PlayerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 游戏玩家表(GamePlayer)，存储对局席位和玩家状态。
 *
 * @author zhangxin
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("aa_game_player")
public class GamePlayer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 游戏玩家ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 游戏ID */
    private Long gameId;

    /** 座位号 */
    private Integer seatNo;

    /** 玩家类型 */
    private PlayerType playerType;

    /** 真人用户ID */
    private Long userId;

    /** 模型配置ID */
    private Long modelConfigId;

    /** 智能体人格ID */
    private Long agentProfileId;

    /** 显示名称 */
    private String displayName;

    /** 玩家状态 */
    private PlayerStatus status;

    /** 游戏角色代码 */
    private String roleCode;

    /** 公开状态 */
    @Column(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> publicState;

    /** 私有状态 */
    @Column(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> privateState;

    /** 乐观锁版本 */
    @Column(version = true)
    private Integer version;

    /** 创建时间 */
    @Column(onInsertValue = "now(3)")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(onInsertValue = "now(3)", onUpdateValue = "now(3)")
    private LocalDateTime updateTime;
}
