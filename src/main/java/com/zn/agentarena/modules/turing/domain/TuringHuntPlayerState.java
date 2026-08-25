package com.zn.agentarena.modules.turing.domain;

import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图灵潜伏局席位状态。真人标记只存在于内部快照。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TuringHuntPlayerState {
    /** 玩家ID */
    private Long playerId;
    /** 座位号 */
    private Integer seatNo;
    /** 显示名称 */
    private String displayName;
    /** 玩家人格快照 */
    private AgentPersonaSnapshot persona;
    /** 是否为真人玩家 */
    private boolean human;
    /** 是否存活 */
    @Builder.Default
    private boolean alive = true;
}
