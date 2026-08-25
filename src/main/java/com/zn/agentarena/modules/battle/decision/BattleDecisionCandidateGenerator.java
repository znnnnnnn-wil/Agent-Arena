package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceStatus;
import com.zn.agentarena.modules.battle.domain.BattleActionType;
import com.zn.agentarena.modules.battle.domain.BattlePlayerLifeStatus;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleAction;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleRules;
import com.zn.agentarena.modules.battle.perception.ObservedBattlePlayer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 从已经过滤过的信息构造具体战术选择；不计算优先级或推荐动作。 */
@Component
public class BattleDecisionCandidateGenerator {
    private static final List<BattleActionType> STABLE_ACTION_ORDER = List.of(
            BattleActionType.MOVE, BattleActionType.SEARCH, BattleActionType.CAPTURE,
            BattleActionType.REST, BattleActionType.ATTACK, BattleActionType.HIDE,
            BattleActionType.HEAL, BattleActionType.GUARD, BattleActionType.REVIVE,
            BattleActionType.EXECUTE, BattleActionType.SKIP);
    public BattleDecisionCandidateSet generate(BattleRoyaleGameState state,
                                                BattlePlayerState actor,
                                                BattleAgentContext context) {
        if (state == null || actor == null || context == null || context.observation() == null) {
            throw new IllegalArgumentException("生成战术候选需要游戏状态、玩家和有限观察上下文");
        }
        List<Draft> drafts = new ArrayList<>();
        if (actor.isDowned()) {
            add(drafts, actor, BattleActionType.SKIP, null, null, "SKIP",
                    "跳过本次战术行动", "无", null);
            return bind(state, actor, drafts);
        }

        var observation = context.observation();
        var self = observation.self();
        observation.publicWorld().topology().getOrDefault(self.region(), List.of()).stream()
                .sorted().forEach(region -> add(drafts, actor, BattleActionType.MOVE, null, region,
                        "MOVE_" + region, "从 " + self.region() + " 移动到相邻区域 " + region,
                        "消耗本次战术行动", null));
        int searchCount = observation.publicWorld().currentRegionSearchCount();
        if (searchCount < 6) {
            String depletion = searchCount >= 3 ? "；该区域已搜索" + searchCount + "次，产出明显衰减" : "";
            add(drafts, actor, BattleActionType.SEARCH, null, null, "SEARCH_CURRENT_REGION",
                    "搜索当前区域 " + self.region() + "，可能发现资源（不保证产出）" + depletion,
                    "消耗本次战术行动", null);
        }
        if (Objects.equals(observation.publicWorld().supplyDropRegion(), self.region())
                && !Objects.equals(observation.publicWorld().supplyDropClaimPlayerId(), actor.getPlayerId())) {
            add(drafts, actor, BattleActionType.CAPTURE, null, null, "CAPTURE_SUPPLY_DROP",
                    "争夺公开空投并把守点窗口设为当前存活人数；当前占领者=P"
                            + observation.publicWorld().supplyDropClaimPlayerId() + "，剩余行动="
                            + observation.publicWorld().supplyDropClaimActionsRemaining(),
                    "消耗本次战术行动", null);
        }
        if (self.health() < BattleRoyaleRules.MAX_HEALTH && self.food() > 1 && self.water() > 1) {
            add(drafts, actor, BattleActionType.REST, null, null, "REST_SELF",
                    "原地休整并恢复6生命", "消耗本次战术行动", null);
        }

        observation.visiblePlayers().stream()
                .filter(player -> player.lifeStatus() == BattlePlayerLifeStatus.ALIVE)
                .sorted(Comparator.comparing(ObservedBattlePlayer::playerId))
                .forEach(target -> add(drafts, actor, BattleActionType.ATTACK, target.playerId(), null,
                        "ATTACK_P" + target.playerId(), "尝试伤害可见玩家 P" + target.playerId()
                                + "；基础原始伤害=" + (self.armed() && self.ammo() > 0
                                ? 28 + self.weaponLevel() * 12 : 18) + "，暴击率12%，再结算目标护甲/隐藏/盟友掩护",
                        self.armed() && self.ammo() > 0 ? "消耗弹药 x1" : "无弹药时使用基础攻击",
                        allianceConsequence(context, actor.getPlayerId(), target.playerId(), "攻击")));

        add(drafts, actor, BattleActionType.HIDE, null, null, "HIDE_SELF",
                "隐藏自己，直到被行动或规则暴露", "消耗本次战术行动", null);
        if (self.medkits() > 0 && self.health() < BattleRoyaleRules.MAX_HEALTH) {
            add(drafts, actor, BattleActionType.HEAL, null, null, "HEAL_SELF",
                    "治疗自己（SELF），当前生命 " + self.health() + "/100，恢复最多 35 生命",
                    "MEDKIT x1", null);
        }
        if (self.armor() < BattleRoyaleRules.GUARD_ARMOR_CAP) {
            add(drafts, actor, BattleActionType.GUARD, null, null, "GUARD_SELF",
                    "护甲+8，最高到36；当前护甲=" + self.armor(), "消耗本次战术行动", null);
        }

        observation.visiblePlayers().stream()
                .filter(player -> player.lifeStatus() == BattlePlayerLifeStatus.DOWNED)
                .sorted(Comparator.comparing(ObservedBattlePlayer::playerId))
                .forEach(target -> {
                    if (self.medkits() > 0) {
                        add(drafts, actor, BattleActionType.REVIVE, target.playerId(), null,
                                "REVIVE_P" + target.playerId(), "救起同区倒地玩家 P" + target.playerId()
                                        + "，使其恢复为 ALIVE", "MEDKIT x1", null);
                    }
                    add(drafts, actor, BattleActionType.EXECUTE, target.playerId(), null,
                            "EXECUTE_P" + target.playerId(), "处决同区倒地玩家 P" + target.playerId(),
                            "消耗本次战术行动",
                            allianceConsequence(context, actor.getPlayerId(), target.playerId(), "处决"));
                });
        add(drafts, actor, BattleActionType.SKIP, null, null, "SKIP",
                "跳过本次战术行动", "无", null);
        return bind(state, actor, drafts);
    }

    private BattleDecisionCandidateSet bind(BattleRoyaleGameState state, BattlePlayerState actor,
                                             List<Draft> drafts) {
        drafts.sort(Comparator.comparingInt((Draft draft) -> STABLE_ACTION_ORDER.indexOf(draft.action().type()))
                .thenComparing(draft -> draft.action().targetPlayerId(), Comparator.nullsFirst(Long::compareTo))
                .thenComparing(draft -> draft.action().targetRegion(), Comparator.nullsFirst(String::compareTo)));
        List<BattleDecisionCandidate> candidates = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            Draft draft = drafts.get(index);
            candidates.add(new BattleDecisionCandidate("T%02d".formatted(index + 1), draft.action().type(),
                    actor.getPlayerId(), draft.action().targetPlayerId(), draft.action().targetRegion(),
                    draft.semanticName(), draft.effectSummary(), draft.costSummary(), draft.knownConsequence()));
        }
        return new BattleDecisionCandidateSet(state.getGameId(), state.getRoundNo(), state.getSequenceNo(),
                actor.getPlayerId(), candidates);
    }

    private void add(List<Draft> drafts, BattlePlayerState actor, BattleActionType type,
                     Long targetPlayerId, String targetRegion, String semanticName,
                     String effectSummary, String costSummary, String knownConsequence) {
        String completeCost = actor.isActive()
                ? costSummary + "；行动结束固定消耗 FOOD x1 + WATER x1，任一归零再受5伤害"
                : costSummary;
        drafts.add(new Draft(new BattleRoyaleAction(actor.getPlayerId(), type, targetPlayerId, targetRegion),
                semanticName, effectSummary, completeCost, knownConsequence));
    }

    private String allianceConsequence(BattleAgentContext context, Long actorId, Long targetId, String verb) {
        boolean knownActiveAlliance = context.social().alliances().stream()
                .anyMatch(alliance -> alliance.getStatus() == BattleAllianceStatus.ACTIVE
                        && alliance.includes(actorId) && alliance.includes(targetId));
        return knownActiveAlliance ? verb + "该玩家会被系统视为背叛你已知的 ACTIVE 联盟" : null;
    }

    private record Draft(BattleRoyaleAction action, String semanticName, String effectSummary,
                         String costSummary, String knownConsequence) { }
}
