package com.zn.agentarena.modules.battle.information;

import com.zn.agentarena.modules.battle.domain.BattleResourceAmount;
import lombok.*;
import java.util.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BattleInformationTradeProposal {
    private String tradeId;
    private Long sellerPlayerId;
    private Long buyerPlayerId;
    private String rumorId;
    private BattleResourceAmount price;
    private int createdRound;
    private int expiresRound;
    private BattleInformationTradeStatus status;
    private Integer resolvedRound;
    private String failureReason;
    @Builder.Default private List<Long> knownByPlayerIds = new ArrayList<>();
}
