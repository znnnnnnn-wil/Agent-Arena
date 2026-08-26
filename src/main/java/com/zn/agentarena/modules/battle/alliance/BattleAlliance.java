package com.zn.agentarena.modules.battle.alliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 可退出、可背叛的双人临时联盟及其提案。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleAlliance {
    private String allianceId;
    private Long proposerPlayerId;
    private Long memberAPlayerId;
    private Long memberBPlayerId;
    private int createdRound;
    private int expiresRound;
    private Integer activatedRound;
    private BattleAllianceStatus status;
    private Integer endedRound;
    private String endReason;
    @Builder.Default
    private List<Long> knownByPlayerIds = new ArrayList<>();

    public boolean includes(Long playerId) {
        return playerId != null && (playerId.equals(memberAPlayerId) || playerId.equals(memberBPlayerId));
    }

    public Long otherMember(Long playerId) {
        if (playerId == null) return null;
        if (playerId.equals(memberAPlayerId)) return memberBPlayerId;
        if (playerId.equals(memberBPlayerId)) return memberAPlayerId;
        return null;
    }
}
