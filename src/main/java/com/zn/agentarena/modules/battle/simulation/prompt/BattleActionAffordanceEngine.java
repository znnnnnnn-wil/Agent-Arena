package com.zn.agentarena.modules.battle.simulation.prompt;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine;
import com.zn.agentarena.modules.battle.perception.ObservedBattlePlayer;
import java.util.*;

/**
 * 最小实验 Affordance 引擎。候选仅来自 Agent 可观察状态，最终可执行性由生产规则引擎 validate 判定。
 * 不调用生产 sanitizer，因为 sanitizer 同时包含策略替换，不能作为纯合法动作来源。
 */
public final class BattleActionAffordanceEngine {
    public static final List<BattleActionType> PROMPT_ACTION_ORDER = List.of(
            BattleActionType.MOVE, BattleActionType.SEARCH, BattleActionType.CAPTURE,
            BattleActionType.REST, BattleActionType.ATTACK, BattleActionType.HIDE,
            BattleActionType.HEAL, BattleActionType.GUARD, BattleActionType.REVIVE,
            BattleActionType.EXECUTE, BattleActionType.SKIP);
    private final BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();

    public List<BattleActionAffordance> compute(BattleRoyaleGameState state, BattlePlayerState actor,
                                                 BattleAgentContext context) {
        if (state == null || actor == null || context == null) return List.of();
        List<BattleActionAffordance> result = new ArrayList<>();
        for (BattleActionType type : PROMPT_ACTION_ORDER) {
            List<BattleRoyaleAction> valid = candidates(type, state, actor, context).stream()
                    .filter(action -> engine.validate(state, action).valid()).toList();
            if (!valid.isEmpty()) result.add(toAffordance(type, valid, state, actor, context));
        }
        return List.copyOf(result);
    }

    private List<BattleRoyaleAction> candidates(BattleActionType type, BattleRoyaleGameState state,
                                                 BattlePlayerState actor, BattleAgentContext context) {
        Long actorId = actor.getPlayerId();
        if (type == BattleActionType.MOVE) {
            return context.observation().publicWorld().topology().getOrDefault(actor.getRegion(), List.of()).stream()
                    .map(region -> new BattleRoyaleAction(actorId, type, null, region)).toList();
        }
        if (type == BattleActionType.ATTACK || type == BattleActionType.REVIVE || type == BattleActionType.EXECUTE) {
            return context.observation().visiblePlayers().stream()
                    .filter(player -> type == BattleActionType.ATTACK
                            ? player.lifeStatus() == BattlePlayerLifeStatus.ALIVE
                            : player.lifeStatus() == BattlePlayerLifeStatus.DOWNED)
                    .map(player -> new BattleRoyaleAction(actorId, type, player.playerId(), null)).toList();
        }
        return List.of(new BattleRoyaleAction(actorId, type, null, null));
    }

    private BattleActionAffordance toAffordance(BattleActionType type, List<BattleRoyaleAction> valid,
                                                BattleRoyaleGameState state, BattlePlayerState actor,
                                                BattleAgentContext context) {
        List<Long> targets = valid.stream().map(BattleRoyaleAction::targetPlayerId).filter(Objects::nonNull).distinct().toList();
        List<String> regions = valid.stream().map(BattleRoyaleAction::targetRegion).filter(Objects::nonNull).distinct().toList();
        Map<String,Object> relevant = new LinkedHashMap<>();
        String condition;
        switch (type) {
            case MOVE -> { condition = "至少存在一个相邻且可到达的区域"; relevant.put("currentRegion", actor.getRegion()); relevant.put("reachableRegions", regions); }
            case SEARCH -> { condition = "当前区域在生产规则下接受 SEARCH"; relevant.put("currentRegion", actor.getRegion()); relevant.put("currentRegionSearchCount", state.getRegionSearchCounts().getOrDefault(actor.getRegion(), 0)); }
            case CAPTURE -> { condition = "当前区域存在可占领的空投"; relevant.put("currentRegion", actor.getRegion()); relevant.put("supplyDropRegion", state.getSupplyDropRegion()); }
            case REST -> { condition = "当前存活状态允许原地休整"; relevant.put("health", actor.getHealth()); relevant.put("food", actor.getFood()); relevant.put("water", actor.getWater()); }
            case ATTACK -> { condition = "同区域存在可观察且有效的 ALIVE 攻击目标"; relevant.put("currentRegion", actor.getRegion()); relevant.put("targetPlayerIds", targets); relevant.put("sameRegion", true); }
            case HIDE -> { condition = "存活玩家可选择隐藏；连续第3次及以后会被侦测并暴露"; relevant.put("currentlyHidden", actor.isHidden()); relevant.put("lastAction", actor.getLastAction() == null ? "NONE" : actor.getLastAction()); relevant.put("repeatedActions", actor.getRepeatedActions()); }
            case HEAL -> { condition = "医疗包数量大于零，满足生产 HEAL 校验条件"; relevant.put("health", actor.getHealth()); relevant.put("medkits", actor.getMedkits()); }
            case GUARD -> { condition = "存活玩家可临时防守，下次受击减伤8，不增加永久护甲"; relevant.put("armor", actor.getArmor()); }
            case REVIVE -> { condition = "同区域存在可观察的 DOWNED 玩家且医疗包数量大于零"; relevant.put("currentRegion", actor.getRegion()); relevant.put("targetPlayerIds", targets); relevant.put("targetState", "DOWNED"); relevant.put("sameRegion", true); relevant.put("medkits", actor.getMedkits()); }
            case EXECUTE -> { condition = "同区域存在可观察的 DOWNED 玩家"; relevant.put("currentRegion", actor.getRegion()); relevant.put("targetPlayerIds", targets); relevant.put("targetState", "DOWNED"); relevant.put("sameRegion", true); }
            case SKIP -> { condition = "当前规则允许跳过战术行动"; relevant.put("lifeStatus", actor.effectiveLifeStatus().name()); }
            default -> throw new IllegalStateException("unexpected experimental action: " + type);
        }
        return new BattleActionAffordance(type.name(), targets, regions, condition, Map.copyOf(relevant));
    }

    public Map<String,Object> observableState(BattleRoyaleGameState state, BattlePlayerState actor,
                                              BattleAgentContext context) {
        Map<String,Object> snapshot = new LinkedHashMap<>();
        snapshot.put("self", Map.of("playerId", actor.getPlayerId(), "region", actor.getRegion(),
                "health", actor.getHealth(), "food", actor.getFood(), "water", actor.getWater(),
                "medkits", actor.getMedkits(), "armor", actor.getArmor(), "ammo", actor.getAmmo(),
                "armed", actor.isArmed(), "lifeStatus", actor.effectiveLifeStatus().name()));
        snapshot.put("visiblePlayers", context.observation().visiblePlayers().stream().map(this::visibleSnapshot).toList());
        snapshot.put("safeRegions", context.observation().publicWorld().safeRegions());
        snapshot.put("reachableRegions", context.observation().publicWorld().topology().getOrDefault(actor.getRegion(), List.of()));
        snapshot.put("currentRegionSearchCount", state.getRegionSearchCounts().getOrDefault(actor.getRegion(), 0));
        snapshot.put("supplyDropRegion", state.getSupplyDropRegion());
        return Collections.unmodifiableMap(snapshot);
    }

    private Map<String,Object> visibleSnapshot(ObservedBattlePlayer player) {
        Map<String,Object> value = new LinkedHashMap<>();
        value.put("playerId", player.playerId()); value.put("region", player.currentObservedRegion());
        value.put("healthStatus", player.healthStatus().name()); value.put("lifeStatus", player.lifeStatus().name());
        value.put("obviousWeapon", player.obviousWeapon()); value.put("exposed", player.exposed());
        return Map.copyOf(value);
    }
}
