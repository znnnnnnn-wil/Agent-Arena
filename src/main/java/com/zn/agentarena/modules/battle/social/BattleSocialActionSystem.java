package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.alliance.BattleAllianceSystem;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.promise.BattlePromiseSystem;
import com.zn.agentarena.modules.battle.trade.BattleTradeSystem;
import com.zn.agentarena.modules.battle.objective.BattleCooperativeObjectiveSystem;
import com.zn.agentarena.modules.battle.information.BattleInformationTradeSystem;
import com.zn.agentarena.modules.battle.information.BattleRumorSystem;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** 将统一社会动作路由到联盟、承诺和交易子系统；非法动作静默丢弃。 */
@Component
public class BattleSocialActionSystem {
    private final BattleAllianceSystem allianceSystem;
    private final BattlePromiseSystem promiseSystem;
    private final BattleTradeSystem tradeSystem;
    private final BattleCooperativeObjectiveSystem objectiveSystem;
    private final BattleInformationTradeSystem informationTradeSystem;
    private final BattleRumorSystem rumorSystem;

    @Autowired
    public BattleSocialActionSystem(BattleAllianceSystem allianceSystem, BattlePromiseSystem promiseSystem,
                                    BattleTradeSystem tradeSystem, BattleCooperativeObjectiveSystem objectiveSystem,
                                    BattleInformationTradeSystem informationTradeSystem, BattleRumorSystem rumorSystem) {
        this.allianceSystem = allianceSystem;
        this.promiseSystem = promiseSystem;
        this.tradeSystem = tradeSystem;
        this.objectiveSystem = objectiveSystem;
        this.informationTradeSystem = informationTradeSystem;
        this.rumorSystem = rumorSystem;
    }

    /** 兼容第二阶段的轻量测试构造。 */
    public BattleSocialActionSystem(BattleAllianceSystem allianceSystem, BattlePromiseSystem promiseSystem,
                                    BattleTradeSystem tradeSystem) {
        this.allianceSystem = allianceSystem;
        this.promiseSystem = promiseSystem;
        this.tradeSystem = tradeSystem;
        BattleSocialStateInitializer initializer = new BattleSocialStateInitializer();
        com.zn.agentarena.modules.battle.memory.BattleMemorySystem memory =
                new com.zn.agentarena.modules.battle.memory.BattleMemorySystem(initializer);
        this.rumorSystem = new BattleRumorSystem(memory);
        this.informationTradeSystem = new BattleInformationTradeSystem(rumorSystem, memory);
        this.objectiveSystem = new BattleCooperativeObjectiveSystem(memory);
    }

    public BattleSocialActionResult execute(BattleRoyaleGameState state, Long actorId,
                                            BattleSocialDecision decision, Collection<Long> informedPlayers) {
        if (decision == null || decision.type() == null || state.findPlayer(actorId) == null
                || !state.findPlayer(actorId).isActive()) return BattleSocialActionResult.empty();
        BattleSocialActionType type;
        try {
            type = BattleSocialActionType.valueOf(decision.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BattleSocialActionResult.empty();
        }
        List<BattleRelationshipChange> changes = new ArrayList<>();
        List<EngineEvent> events = switch (type) {
            case PROPOSE_ALLIANCE -> allianceSystem.propose(state, actorId, decision.targetPlayerId(), List.of());
            case ACCEPT_ALLIANCE -> allianceSystem.accept(state, actorId, decision.targetPlayerId(),
                    decision.referenceId(), List.of(), changes);
            case REJECT_ALLIANCE -> allianceSystem.reject(state, actorId, decision.targetPlayerId(), decision.referenceId());
            case LEAVE_ALLIANCE -> allianceSystem.leave(state, actorId, decision.targetPlayerId(), decision.referenceId());
            case PROPOSE_PROMISE -> promiseSystem.propose(state, actorId, decision.targetPlayerId(),
                    decision.promise(), List.of());
            case ACCEPT_PROMISE -> promiseSystem.accept(state, actorId, decision.targetPlayerId(),
                    decision.referenceId(), List.of());
            case REJECT_PROMISE -> promiseSystem.reject(state, actorId, decision.targetPlayerId(), decision.referenceId());
            case FULFILL_PROMISE -> promiseSystem.fulfill(state, actorId, decision.targetPlayerId(),
                    decision.referenceId(), changes);
            case PROPOSE_TRADE -> tradeSystem.propose(state, actorId, decision.targetPlayerId(),
                    decision.trade(), List.of());
            case ACCEPT_TRADE -> tradeSystem.accept(state, actorId, decision.targetPlayerId(),
                    decision.referenceId(), changes);
            case REJECT_TRADE -> tradeSystem.reject(state, actorId, decision.targetPlayerId(), decision.referenceId());
            case CONTRIBUTE_OBJECTIVE -> objectiveSystem.contribute(state, actorId, decision.objectiveContribution());
            case SHARE_RUMOR -> rumorSystem.share(state, actorId, decision.rumor(), informedPlayers);
            case PROPOSE_INFORMATION_TRADE -> informationTradeSystem.propose(state, actorId, decision.targetPlayerId(), decision.informationTrade());
            case ACCEPT_INFORMATION_TRADE -> informationTradeSystem.accept(state, actorId, decision.targetPlayerId(), decision.referenceId());
            case REJECT_INFORMATION_TRADE -> informationTradeSystem.reject(state, actorId, decision.targetPlayerId(), decision.referenceId());
        };
        return new BattleSocialActionResult(events, changes);
    }

    public BattleSocialActionResult evaluateRoundState(BattleRoyaleGameState state) {
        List<EngineEvent> events = new ArrayList<>();
        List<BattleRelationshipChange> changes = new ArrayList<>();
        events.addAll(allianceSystem.expireProposals(state));
        events.addAll(tradeSystem.expireProposals(state));
        events.addAll(promiseSystem.evaluateDeadlines(state, changes));
        events.addAll(objectiveSystem.evaluate(state));
        events.addAll(informationTradeSystem.expire(state));
        return new BattleSocialActionResult(events, changes);
    }
}
