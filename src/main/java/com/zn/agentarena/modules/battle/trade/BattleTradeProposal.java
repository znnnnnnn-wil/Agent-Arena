package com.zn.agentarena.modules.battle.trade;

import com.zn.agentarena.modules.battle.domain.BattleResourceAmount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 需要目标玩家明确接受、并在接受时原子执行的交易提案。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleTradeProposal {
    private String tradeId;
    private Long proposerPlayerId;
    private Long targetPlayerId;
    @Builder.Default
    private List<BattleResourceAmount> offer = new ArrayList<>();
    @Builder.Default
    private List<BattleResourceAmount> request = new ArrayList<>();
    private int createdRound;
    private int expiresRound;
    private BattleTradeStatus status;
    private Integer resolvedRound;
    private String failureReason;
    @Builder.Default
    private List<Long> knownByPlayerIds = new ArrayList<>();
}
