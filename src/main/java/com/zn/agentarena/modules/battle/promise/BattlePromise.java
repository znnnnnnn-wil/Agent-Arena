package com.zn.agentarena.modules.battle.promise;

import com.zn.agentarena.modules.battle.domain.BattleResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 需要对方接受、且必须由承诺者主动履行的资源承诺。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattlePromise {
    private String promiseId;
    private Long promiserPlayerId;
    private Long promiseePlayerId;
    private BattlePromiseType promiseType;
    private BattleResourceType resourceType;
    private int amount;
    private int createdRound;
    private int deadlineRound;
    private BattlePromiseStatus status;
    private Integer acceptedRound;
    private Integer resolvedRound;
    @Builder.Default
    private List<Long> knownByPlayerIds = new ArrayList<>();
}
