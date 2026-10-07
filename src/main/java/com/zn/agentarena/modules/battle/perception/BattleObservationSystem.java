package com.zn.agentarena.modules.battle.perception;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleRules;
import com.zn.agentarena.modules.battle.domain.BattleRegionRules;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 把服务端真实状态投影为指定智能体理论上能够知道的信息。 */
@Component
public class BattleObservationSystem {

    private static final List<String> RULES = List.of(
            "主要胜利条件是成为最后未淘汰者；达到最大回合或全员倒地时按公开生存积分决胜",
            "生存积分=生命×2+护甲+食物×2+饮水×2+医疗包×12+武器等级×18+弹药+击杀×120+计分有效伤害×2；每个目标本局累计最多贡献100点计分伤害，过量伤害不计分，治疗或救援不重置额度；同分依次比较击杀、生命、较小 playerId",
            "每个 ALIVE 玩家的战术行动都会消耗 FOOD x1 和 WATER x1；任一归零后该行动结束时受到5伤害",
            "缩圈随局长调整，以公开 nextShrinkRound 为准；12轮局在第3/5/7/9轮缩圈；第2至5阶段圈外伤害依次为8/14/22/35，公开 ringOrder 的末项最先出圈、首项为最终安全区",
            "MOVE 每次只能前往相邻区域",
            "ATTACK 目标必须在同一区域；徒手基础伤害18，武器基础伤害28+武器等级×12，暴击率12%，护甲最多吸收一半原始伤害",
            "同区 ACTIVE 盟友会为遭受第三方攻击者提供6点掩护减伤；攻击或处决盟友仍属于背叛",
            "空投仅在无人占领时可 CAPTURE；已有占领者时必须先让其离开或倒地，不能靠重复 CAPTURE 重置进度；隐藏也会中断占领；守住后获得高级武器、6弹药、20护甲和1医疗包；空投落点在下次缩圈后仍安全",
            "HEAL 消耗一个医疗包并恢复35生命；REST 在食水均大于0时恢复6生命，E营地额外恢复3；GUARD不增加永久护甲，仅使下次受击减伤8，此防守在自己下次行动时失效",
            "B林地掩护减伤4，D废墟掩护减伤6；掩护和临时防守在暴击计算后、护甲吸收前生效",
            "攻击、占领、处决和非林地搜索会暴露自己，承受额外8点原始伤害；下一次采取安静行动后解除；隐藏状态只保留到下一次战术行动；连续第3次及以后HIDE会被侦测、公布区域并暴露，不能隐藏，改变战术动作可重置连续次数",
            "倒地玩家只能 SKIP 并通信；同区域可用 REVIVE 救援或 EXECUTE 处决");

    public BattleAgentObservation observe(BattleRoyaleGameState state, Long actorPlayerId) {
        BattlePlayerState actor = state == null ? null : state.findPlayer(actorPlayerId);
        if (actor == null) {
            throw new IllegalArgumentException("无法为不存在的玩家构建大逃杀观察");
        }
        BattleSelfObservation self = new BattleSelfObservation(actor.getPlayerId(), actor.getDisplayName(),
                actor.getRegion(), actor.getHealth(), actor.getFood(), actor.getWater(), actor.isArmed(),
                actor.getWeaponName(), actor.getWeaponLevel(), actor.getAmmo(), actor.getArmor(), actor.getMedkits(),
                actor.isHidden(), actor.isExposed(), actor.isGuarded(), actor.getKills(), actor.getLastAction(), actor.getRepeatedActions(),
                actor.effectiveLifeStatus(), actor.getBleedOutRound());
        List<ObservedBattlePlayer> visible = state.contendingPlayers().stream()
                .filter(player -> !Objects.equals(player.getPlayerId(), actorPlayerId))
                .filter(player -> Objects.equals(player.getRegion(), actor.getRegion()))
                .filter(player -> isVisibleTo(actor, player))
                .map(this::toObservedPlayer)
                .toList();
        List<String> ringOrder = state.getRingOrder() == null || state.getRingOrder().size() != state.getRegions().size()
                ? BattleRoyaleRules.ringOrder(state.getRegions(), state.getRandomSeed()) : List.copyOf(state.getRingOrder());
        int nextShrink = BattleRoyaleRules.nextShrinkRound(state.getRingStage(), state.getMaxRounds());
        BattlePublicWorldObservation publicWorld = new BattlePublicWorldObservation(state.getRoundNo(),
                state.getMaxRounds(), state.getSafeRegions() == null ? List.of() : List.copyOf(state.getSafeRegions()),
                state.getRingStage(), ringOrder, nextShrink < 0 ? null : nextShrink,
                BattleRoyaleRules.zoneDamage(state.getRingStage()),
                state.getRegions() == null ? List.of() : List.copyOf(state.getRegions()),
                BattleRegionRules.TOPOLOGY, state.getSupplyDropRegion(), state.getSupplyDropClaimPlayerId(),
                state.getSupplyDropClaimActionsRemaining(),
                state.getRegionSearchCounts() == null ? 0 : state.getRegionSearchCounts().getOrDefault(actor.getRegion(), 0),
                state.getRoundTurnOrder() == null ? List.of() : List.copyOf(state.getRoundTurnOrder()),
                state.getActionsInRound(),
                state.contendingPlayers().size(), RULES);
        return new BattleAgentObservation(self, publicWorld, visible);
    }

    private boolean isVisibleTo(BattlePlayerState actor, BattlePlayerState other) {
        if (!other.isHidden()) return true;
        return other.isExposed() || Objects.equals(actor.getLastAttackerPlayerId(), other.getPlayerId());
    }

    private ObservedBattlePlayer toObservedPlayer(BattlePlayerState player) {
        return new ObservedBattlePlayer(player.getPlayerId(), player.getDisplayName(), player.getRegion(),
                healthStatus(player.getHealth()), player.isArmed(), player.isExposed(),
                player.effectiveLifeStatus(), player.getBleedOutRound());
    }

    private BattleHealthStatus healthStatus(int health) {
        if (health > 65) return BattleHealthStatus.HEALTHY;
        if (health > 30) return BattleHealthStatus.INJURED;
        return BattleHealthStatus.CRITICAL;
    }

}
