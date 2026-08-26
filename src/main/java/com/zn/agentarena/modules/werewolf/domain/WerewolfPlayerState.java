package com.zn.agentarena.modules.werewolf.domain;

import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 狼人杀玩家状态。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WerewolfPlayerState {

    /** 玩家ID */
    private Long playerId;

    /** 座位号 */
    private Integer seatNo;

    /** 玩家名称 */
    private String displayName;

    /** 对局开始时固化的 Agent 人格。 */
    private AgentPersonaSnapshot persona;

    /** 游戏角色 */
    private WerewolfRole role;

    /** 是否存活 */
    private boolean alive;

    /** 是否拥有解药 */
    private boolean antidoteAvailable;

    /** 是否拥有毒药 */
    private boolean poisonAvailable;

    /** 预言家已经查验过的玩家及结果。 */
    @Builder.Default
    private Map<Long, Boolean> seerChecks = new LinkedHashMap<>();
}
