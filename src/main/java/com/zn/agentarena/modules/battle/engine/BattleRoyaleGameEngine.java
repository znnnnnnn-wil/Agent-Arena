package com.zn.agentarena.modules.battle.engine;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceStatus;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.game.engine.*;

import java.util.*;

/**
 * 简化大逃杀确定性规则引擎。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public class BattleRoyaleGameEngine implements GameEngine<BattleRoyaleGameState, BattleRoyaleAction> {
    private static final int BLEED_OUT_AFTER_ROUNDS = 2;
    private static final Map<String, Set<String>> REGION_CONNECTIONS = Map.of(
            "A", Set.of("B", "C"),
            "B", Set.of("A", "D"),
            "C", Set.of("A", "D", "E"),
            "D", Set.of("B", "C", "E"),
            "E", Set.of("C", "D"));

    @Override
    /** 校验大逃杀行动是否符合当前游戏状态。 */
    public ActionValidation validate(BattleRoyaleGameState state, BattleRoyaleAction action) {
        if (state == null || action == null || action.type() == null) {
            return ActionValidation.fail(ResultCodeEnum.PARAMS_ERROR, "游戏状态和行动不能为空");
        }
        if (isFinished(state)) {
            return ActionValidation.fail(ResultCodeEnum.GAME_STATUS_ERROR, "游戏已经结束");
        }
        BattlePlayerState actor = state.findPlayer(action.actorPlayerId());
        if (actor == null || actor.isEliminated()) {
            return ActionValidation.fail(ResultCodeEnum.PLAYER_NOT_FOUND, "玩家不存在或已经出局");
        }
        if (actor.isDowned() && action.type() != BattleActionType.SKIP) {
            return ActionValidation.fail(ResultCodeEnum.ACTION_NOT_ALLOWED, "倒地玩家只能跳过战术行动并发送消息");
        }
        if (action.type() == BattleActionType.MOVE) {
            return state.getRegions().contains(action.targetRegion())
                    && adjacentRegions(actor.getRegion()).contains(action.targetRegion())
                    ? ActionValidation.success()
                    : ActionValidation.fail(ResultCodeEnum.ACTION_TARGET_INVALID, "只能移动到相邻区域");
        }
        if (action.type() == BattleActionType.ATTACK || action.type() == BattleActionType.TRADE) {
            BattlePlayerState target = state.findPlayer(action.targetPlayerId());
            if (target == null || !target.isActive() || target.getPlayerId().equals(actor.getPlayerId())
                    || !actor.getRegion().equals(target.getRegion())) {
                return ActionValidation.fail(ResultCodeEnum.ACTION_TARGET_INVALID, "目标玩家不在同一区域或不可用");
            }
        }
        if (action.type() == BattleActionType.REVIVE || action.type() == BattleActionType.EXECUTE) {
            BattlePlayerState target = state.findPlayer(action.targetPlayerId());
            if (target == null || !target.isDowned() || target.getPlayerId().equals(actor.getPlayerId())
                    || !Objects.equals(actor.getRegion(), target.getRegion())) {
                return ActionValidation.fail(ResultCodeEnum.ACTION_TARGET_INVALID, "倒地目标不在同一区域或不可用");
            }
            if (action.type() == BattleActionType.REVIVE && actor.getMedkits() <= 0) {
                return ActionValidation.fail(ResultCodeEnum.ACTION_TARGET_INVALID, "救援需要一个医疗包");
            }
        }
        if (action.type() == BattleActionType.HEAL && actor.getMedkits() <= 0) {
            return ActionValidation.fail(ResultCodeEnum.ACTION_TARGET_INVALID, "没有可用医疗包");
        }
        if (action.type() == BattleActionType.CAPTURE
                && !Objects.equals(state.getSupplyDropRegion(), actor.getRegion())) {
            return ActionValidation.fail(ResultCodeEnum.ACTION_TARGET_INVALID, "当前区域没有可占领的空投");
        }
        if (action.type() == BattleActionType.CAPTURE
                && Objects.equals(state.getSupplyDropClaimPlayerId(), actor.getPlayerId())
                && state.getSupplyDropClaimActionsRemaining() > 0) {
            return ActionValidation.fail(ResultCodeEnum.ACTION_NOT_ALLOWED, "你已经在占领该空投，重复 CAPTURE 会重置进度");
        }
        return ActionValidation.success();
    }

    @Override
    /** 执行一项大逃杀行动并生成状态变更事件。 */
    public EngineResult<BattleRoyaleGameState> execute(BattleRoyaleGameState state, BattleRoyaleAction action) {
        ActionValidation validation = validate(state, action);
        if (!validation.valid()) {
            throw CustomException.create(validation.resultCode(), validation.message());
        }
        BattlePlayerState actor = state.findPlayer(action.actorPlayerId());
        ensureRoundTurnOrder(state);
        long actionRandomNo = state.getRandomActionNo();
        state.setRandomActionNo(actionRandomNo + 1);
        BattlePlayerState actionTarget = action.targetPlayerId() == null ? null : state.findPlayer(action.targetPlayerId());
        boolean targetWasActive = actionTarget != null && actionTarget.isActive();
        boolean actorWasActive = actor.isActive();
        boolean claimWasActiveAtTurnStart = state.getSupplyDropClaimPlayerId() != null
                && state.getSupplyDropClaimActionsRemaining() > 0;
        List<EngineEvent> events = new ArrayList<>();
        String lootType = null;
        String lootName = null;
        int lootAmount = 0;
        int damageDealt = 0;
        boolean criticalHit = false;
        switch (action.type()) {
            case MOVE -> actor.setRegion(action.targetRegion());
            case SEARCH -> {
                SearchOutcome outcome = searchRegion(state, actor, actionRandomNo);
                lootType = outcome.type();
                lootName = outcome.name();
                lootAmount = outcome.amount();
            }
            case CAPTURE -> {
                state.setSupplyDropClaimPlayerId(actor.getPlayerId());
                state.setSupplyDropClaimActionsRemaining(Math.max(2, state.alivePlayers().size()));
                addEvent(state, events, "BATTLE_SUPPLY_CONTESTED", actor.getPlayerId(),
                        Map.of("playerId", actor.getPlayerId(), "region", actor.getRegion(),
                                "actionsRemaining", state.getSupplyDropClaimActionsRemaining()));
            }
            case REST -> {
                if (actor.getFood() > 0 && actor.getWater() > 0) {
                    actor.setHealth(Math.min(BattleRoyaleRules.MAX_HEALTH,
                            actor.getHealth() + BattleRoyaleRules.REST_HEAL));
                }
            }
            case HIDE -> actor.setHidden(true);
            case HEAL -> {
                actor.setMedkits(actor.getMedkits() - 1);
                actor.setHealth(Math.min(BattleRoyaleRules.MAX_HEALTH,
                        actor.getHealth() + BattleRoyaleRules.HEAL_AMOUNT));
            }
            case GUARD -> actor.setArmor(Math.min(BattleRoyaleRules.GUARD_ARMOR_CAP,
                    actor.getArmor() + BattleRoyaleRules.GUARD_ARMOR_GAIN));
            case TRADE -> { /* 旧动作仅保留协议兼容；真实交换由 BattleTradeSystem 原子执行。 */ }
            case ATTACK -> {
                BattlePlayerState target = state.findPlayer(action.targetPlayerId());
                boolean weaponAttack = actor.isArmed() && actor.getAmmo() > 0;
                int rawDamage = weaponAttack ? 28 + actor.getWeaponLevel() * 12 : 18;
                if (weaponAttack) actor.setAmmo(actor.getAmmo() - 1);
                if (target.isHidden()) rawDamage = Math.max(10, rawDamage - 8);
                if (target.isExposed()) rawDamage += 8;
                if (state.getRingStage() >= 4) rawDamage = rawDamage * 5 / 4;
                if (hasActiveAllyCover(state, actor.getPlayerId(), target)) {
                    rawDamage = Math.max(10, rawDamage - BattleRoyaleRules.ALLY_COVER_REDUCTION);
                }
                Random combatRandom = new Random(state.getRandomSeed() + actionRandomNo * 31 + target.getPlayerId());
                criticalHit = combatRandom.nextInt(100) < BattleRoyaleRules.CRITICAL_CHANCE;
                if (criticalHit) rawDamage += rawDamage / 2;
                int absorbed = Math.min(target.getArmor(), rawDamage / 2);
                target.setArmor(target.getArmor() - absorbed);
                damageDealt = rawDamage - absorbed;
                target.setHealth(target.getHealth() - damageDealt);
                actor.setTotalDamage(actor.getTotalDamage() + damageDealt);
                target.setHidden(false);
                target.setLastAttackerPlayerId(actor.getPlayerId());
                actor.setExposed(false);
                state.setCombatOccurredThisRound(true);
                state.setRoundsWithoutCombat(0);
                if (target.getHealth() <= 0) {
                    target.markDowned(state.getRoundNo(), BLEED_OUT_AFTER_ROUNDS, actor.getPlayerId());
                }
            }
            case REVIVE -> {
                BattlePlayerState target = state.findPlayer(action.targetPlayerId());
                actor.setMedkits(actor.getMedkits() - 1);
                target.markAlive(BattleRoyaleRules.REVIVE_HEALTH);
            }
            case EXECUTE -> {
                BattlePlayerState target = state.findPlayer(action.targetPlayerId());
                target.markEliminated();
                actor.setKills(actor.getKills() + 1);
            }
            case SKIP -> { }
        }
        if (actorWasActive) {
            actor.setHidden(action.type() == BattleActionType.HIDE);
            actor.setFood(Math.max(0, actor.getFood() - BattleRoyaleRules.METABOLISM_COST));
            actor.setWater(Math.max(0, actor.getWater() - BattleRoyaleRules.METABOLISM_COST));
            if (actor.getFood() == 0 || actor.getWater() == 0) {
                actor.setHealth(actor.getHealth() - BattleRoyaleRules.STARVATION_DAMAGE);
            }
            if (actor.getHealth() <= 0 && actor.isActive()) {
                actor.markDowned(state.getRoundNo(), BLEED_OUT_AFTER_ROUNDS, null);
            }
        }
        if (action.type().name().equals(actor.getLastAction())) actor.setRepeatedActions(actor.getRepeatedActions() + 1);
        else actor.setRepeatedActions(1);
        actor.setLastAction(action.type().name());
        Map<String, Object> actionPayload = new LinkedHashMap<>();
        actionPayload.put("action", action.type().name());
        actionPayload.put("playerId", action.actorPlayerId());
        actionPayload.put("targetPlayerId", action.targetPlayerId());
        actionPayload.put("region", actor.getRegion());
        actionPayload.put("health", actor.getHealth());
        actionPayload.put("food", actor.getFood());
        actionPayload.put("water", actor.getWater());
        actionPayload.put("armed", actor.isArmed());
        actionPayload.put("weaponName", actor.getWeaponName());
        actionPayload.put("weaponLevel", actor.getWeaponLevel());
        actionPayload.put("ammo", actor.getAmmo());
        actionPayload.put("armor", actor.getArmor());
        actionPayload.put("medkits", actor.getMedkits());
        actionPayload.put("kills", actor.getKills());
        actionPayload.put("lifeStatus", actor.effectiveLifeStatus().name());
        if (lootType != null) {
            actionPayload.put("lootType", lootType);
            actionPayload.put("lootName", lootName);
            actionPayload.put("lootAmount", lootAmount);
            actionPayload.put("supplyDrop", false);
        }
        if (damageDealt > 0) actionPayload.put("damage", damageDealt);
        if (criticalHit) actionPayload.put("critical", true);
        if (actionTarget != null) {
            actionPayload.put("targetHealth", actionTarget.getHealth());
            actionPayload.put("targetLifeStatus", actionTarget.effectiveLifeStatus().name());
        }
        Map<String, Object> publicActionPayload = new LinkedHashMap<>(actionPayload);
        if (action.type() == BattleActionType.HIDE) {
            publicActionPayload.remove("region");
        }
        addEvent(state, events, "BATTLE_ACTION_EXECUTED", action.actorPlayerId(),
                actionPayload, publicActionPayload);
        if (targetWasActive && actionTarget != null && actionTarget.isDowned()) {
            addEvent(state, events, "BATTLE_PLAYER_DOWNED", action.actorPlayerId(),
                    Map.of("playerId", actionTarget.getPlayerId(), "downedByPlayerId", action.actorPlayerId(),
                            "region", actionTarget.getRegion(), "bleedOutRound", actionTarget.getBleedOutRound()));
        }
        if (action.type() == BattleActionType.REVIVE && actionTarget != null && actionTarget.isActive()) {
            addEvent(state, events, "BATTLE_PLAYER_REVIVED", action.actorPlayerId(),
                    Map.of("rescuerPlayerId", action.actorPlayerId(), "playerId", actionTarget.getPlayerId(),
                            "region", actionTarget.getRegion(), "health", actionTarget.getHealth(), "medkitCost", 1));
        }
        if (action.type() == BattleActionType.EXECUTE && actionTarget != null && actionTarget.isEliminated()) {
            addEvent(state, events, "BATTLE_PLAYER_EXECUTED", action.actorPlayerId(),
                    Map.of("executorPlayerId", action.actorPlayerId(), "playerId", actionTarget.getPlayerId(),
                            "region", actionTarget.getRegion()));
            addEvent(state, events, "BATTLE_PLAYER_ELIMINATED", action.actorPlayerId(),
                    Map.of("playerId", actionTarget.getPlayerId(), "reason", "EXECUTE"));
        }
        if (actorWasActive && actor.isDowned()) {
            addEvent(state, events, "BATTLE_PLAYER_DOWNED", action.actorPlayerId(),
                    Map.of("playerId", actor.getPlayerId(), "downedByPlayerId", -1L,
                            "region", actor.getRegion(), "bleedOutRound", actor.getBleedOutRound()));
        }
        cancelInvalidSupplyClaim(state, events);
        if (claimWasActiveAtTurnStart && action.type() != BattleActionType.CAPTURE
                && state.getSupplyDropClaimPlayerId() != null) {
            state.setSupplyDropClaimActionsRemaining(state.getSupplyDropClaimActionsRemaining() - 1);
            if (state.getSupplyDropClaimActionsRemaining() <= 0) {
                awardSupplyDrop(state, events);
            }
        }
        state.setActionsInRound(state.getActionsInRound() + 1);
        skipEliminatedTurnSlots(state);
        if (state.getActionsInRound() >= Math.max(1, state.getRoundTurnOrder().size())) {
            state.setActionsInRound(0);
            state.setRoundTurnOrder(new ArrayList<>());
            state.setRoundNo(state.getRoundNo() + 1);
            processBleedOut(state, events);
            updateCombatPressure(state, events);
            updateRingAndApplyZoneDamage(state, events);
        }
        if (state.contendingPlayers().size() <= 1 || state.alivePlayers().isEmpty()
                || state.getRoundNo() >= state.getMaxRounds()) {
            BattlePlayerState winner = state.contendingPlayers().stream()
                    .max(Comparator.comparingInt(BattleRoyaleRules::survivalScore)
                            .thenComparingInt(BattlePlayerState::getKills)
                            .thenComparingInt(BattlePlayerState::getHealth)
                            .thenComparingLong(player -> -player.getPlayerId())).orElse(null);
            state.setWinnerPlayerId(winner == null ? null : winner.getPlayerId());
            Map<String, Object> finishPayload = new LinkedHashMap<>();
            finishPayload.put("winnerPlayerId", winner == null ? -1L : winner.getPlayerId());
            finishPayload.put("winnerName", winner == null ? "无人幸存" : winner.getDisplayName());
            finishPayload.put("score", winner == null ? 0 : BattleRoyaleRules.survivalScore(winner));
            finishPayload.put("kills", winner == null ? 0 : winner.getKills());
            addEvent(state, events, "BATTLE_FINISHED", null, finishPayload);
        }
        return new EngineResult<>(state, events);
    }

    @Override
    /** 判断大逃杀游戏是否已结束。 */
    public boolean isFinished(BattleRoyaleGameState state) {
        return state == null || state.getWinnerPlayerId() != null || state.contendingPlayers().size() <= 1
                || state.alivePlayers().isEmpty()
                || state.getRoundNo() >= state.getMaxRounds();
    }

    private SearchOutcome searchRegion(BattleRoyaleGameState state, BattlePlayerState actor, long actionRandomNo) {
        Random random = new Random(state.getRandomSeed() + actionRandomNo * 17 + actor.getPlayerId());
        if (state.getRegionSearchCounts() == null) state.setRegionSearchCounts(new LinkedHashMap<>());
        int searched = state.getRegionSearchCounts().getOrDefault(actor.getRegion(), 0);
        state.getRegionSearchCounts().put(actor.getRegion(), searched + 1);
        boolean contested = state.alivePlayers().stream()
                .anyMatch(player -> !player.getPlayerId().equals(actor.getPlayerId())
                        && Objects.equals(player.getRegion(), actor.getRegion()));
        int depletionRoll = random.nextInt(100);
        if (searched >= 6 || (searched >= 3 && depletionRoll < 25 + (searched - 3) * 20)
                || (contested && depletionRoll < 35)) {
            if (!actor.isArmed()) actor.setSearchesWithoutWeapon(actor.getSearchesWithoutWeapon() + 1);
            return new SearchOutcome("EMPTY", "枯竭的物资点", 0);
        }
        boolean supplyDrop = false;
        int weaponChance = "A".equals(actor.getRegion()) ? 45 : 35;
        if (supplyDrop) weaponChance += 25;
        int roll = random.nextInt(100);
        if (!actor.isArmed() && (actor.getSearchesWithoutWeapon() >= 2 || roll < weaponChance)) {
            equipWeapon(actor, 1);
            actor.setSearchesWithoutWeapon(0);
            return new SearchOutcome("WEAPON", actor.getWeaponName(), 1);
        }
        if (actor.isArmed() && roll < weaponChance) {
            if (actor.getWeaponLevel() < 3 && (supplyDrop || random.nextInt(100) < 40)) {
                equipWeapon(actor, actor.getWeaponLevel() + 1);
                return new SearchOutcome("WEAPON_UPGRADE", actor.getWeaponName(), actor.getWeaponLevel());
            }
            int ammo = supplyDrop ? 6 : 4;
            actor.setAmmo(actor.getAmmo() + ammo);
            return new SearchOutcome("AMMO", "弹药", ammo);
        }
        if (!actor.isArmed()) actor.setSearchesWithoutWeapon(actor.getSearchesWithoutWeapon() + 1);
        int category = random.nextInt(100);
        return switch (actor.getRegion()) {
            case "B" -> category < 45 ? addFood(actor, supplyDrop ? 5 : 3)
                    : category < 65 ? addMedkit(actor, supplyDrop ? 2 : 1)
                    : category < 85 ? addWater(actor, 3) : addArmor(actor, supplyDrop ? 25 : 15);
            case "C" -> category < 45 ? addWater(actor, supplyDrop ? 5 : 3)
                    : category < 65 ? addAmmoOrArmor(actor, supplyDrop)
                    : category < 85 ? addFood(actor, 3) : addArmor(actor, supplyDrop ? 25 : 15);
            case "D" -> category < 45 ? addArmor(actor, supplyDrop ? 30 : 18)
                    : category < 70 ? addAmmoOrArmor(actor, supplyDrop)
                    : category < 85 ? addFood(actor, 3) : addWater(actor, 3);
            case "E" -> category < 40 ? addMedkit(actor, supplyDrop ? 2 : 1)
                    : category < 60 ? addFood(actor, 3)
                    : category < 80 ? addWater(actor, 3) : addArmor(actor, supplyDrop ? 25 : 15);
            default -> category < 30 ? addArmor(actor, supplyDrop ? 25 : 15)
                    : category < 55 ? addAmmoOrArmor(actor, supplyDrop)
                    : category < 75 ? addMedkit(actor, 1)
                    : category < 88 ? addFood(actor, 3) : addWater(actor, 3);
        };
    }

    private void equipWeapon(BattlePlayerState player, int level) {
        int actualLevel = Math.max(1, Math.min(3, level));
        player.setArmed(true);
        player.setWeaponLevel(actualLevel);
        player.setWeaponName(switch (actualLevel) { case 1 -> "简易手枪"; case 2 -> "战术步枪"; default -> "精确步枪"; });
        player.setAmmo(player.getAmmo() + (actualLevel == 1 ? 5 : 4));
    }

    private SearchOutcome addFood(BattlePlayerState player, int amount) {
        player.setFood(player.getFood() + amount);
        return new SearchOutcome("FOOD", "食物", amount);
    }

    private SearchOutcome addWater(BattlePlayerState player, int amount) {
        player.setWater(player.getWater() + amount);
        return new SearchOutcome("WATER", "饮用水", amount);
    }

    private SearchOutcome addMedkit(BattlePlayerState player, int amount) {
        player.setMedkits(player.getMedkits() + amount);
        return new SearchOutcome("MEDKIT", "医疗包", amount);
    }

    private SearchOutcome addArmor(BattlePlayerState player, int amount) {
        int before = player.getArmor();
        player.setArmor(Math.min(BattleRoyaleRules.LOOT_ARMOR_CAP, player.getArmor() + amount));
        return new SearchOutcome("ARMOR", "护甲", player.getArmor() - before);
    }

    private SearchOutcome addAmmoOrArmor(BattlePlayerState player, boolean supplyDrop) {
        if (!player.isArmed()) return addArmor(player, supplyDrop ? 25 : 15);
        int amount = supplyDrop ? 6 : 4;
        player.setAmmo(player.getAmmo() + amount);
        return new SearchOutcome("AMMO", "弹药", amount);
    }

    private record SearchOutcome(String type, String name, int amount) { }

    /** 返回某区域直接相连、可在一次行动内到达的区域。 */
    public Set<String> adjacentRegions(String region) {
        return REGION_CONNECTIONS.getOrDefault(region, Set.of());
    }

    private void cancelInvalidSupplyClaim(BattleRoyaleGameState state, List<EngineEvent> events) {
        if (state.getSupplyDropClaimPlayerId() == null) return;
        BattlePlayerState claimant = state.findPlayer(state.getSupplyDropClaimPlayerId());
        if (claimant != null && claimant.isActive()
                && Objects.equals(claimant.getRegion(), state.getSupplyDropRegion())) return;
        Long claimantId = state.getSupplyDropClaimPlayerId();
        state.setSupplyDropClaimPlayerId(null);
        state.setSupplyDropClaimActionsRemaining(0);
        addEvent(state, events, "BATTLE_SUPPLY_CLAIM_BROKEN", claimantId,
                Map.of("playerId", claimantId == null ? -1L : claimantId));
    }

    private void awardSupplyDrop(BattleRoyaleGameState state, List<EngineEvent> events) {
        BattlePlayerState claimant = state.findPlayer(state.getSupplyDropClaimPlayerId());
        if (claimant == null || !claimant.isActive()) {
            cancelInvalidSupplyClaim(state, events);
            return;
        }
        equipWeapon(claimant, Math.min(3, Math.max(2, claimant.getWeaponLevel() + 1)));
        claimant.setAmmo(claimant.getAmmo() + 6);
        claimant.setArmor(Math.min(60, claimant.getArmor() + 20));
        claimant.setMedkits(claimant.getMedkits() + 1);
        addEvent(state, events, "BATTLE_SUPPLY_CAPTURED", claimant.getPlayerId(),
                Map.of("playerId", claimant.getPlayerId(), "region", claimant.getRegion(),
                        "weaponName", claimant.getWeaponName(), "weaponLevel", claimant.getWeaponLevel(),
                        "ammo", claimant.getAmmo(), "armor", claimant.getArmor(),
                        "medkits", claimant.getMedkits()));
        state.setSupplyDropRegion(null);
        state.setSupplyDropClaimPlayerId(null);
        state.setSupplyDropClaimActionsRemaining(0);
    }

    private void updateCombatPressure(BattleRoyaleGameState state, List<EngineEvent> events) {
        if (state.isCombatOccurredThisRound()) {
            state.setCombatOccurredThisRound(false);
            state.setRoundsWithoutCombat(0);
            return;
        }
        state.setRoundsWithoutCombat(state.getRoundsWithoutCombat() + 1);
        if (state.getRoundsWithoutCombat() < 2) return;
        BattlePlayerState exposed = state.alivePlayers().stream()
                .filter(player -> !player.isExposed())
                .max(Comparator.comparingInt(BattleRoyaleRules::survivalScore))
                .orElse(null);
        if (exposed == null) return;
        exposed.setExposed(true);
        addEvent(state, events, "BATTLE_PLAYER_EXPOSED", exposed.getPlayerId(),
                Map.of("playerId", exposed.getPlayerId(), "region", exposed.getRegion(),
                        "reason", "NO_COMBAT", "bonusDamage", 8));
    }

    private void processBleedOut(BattleRoyaleGameState state, List<EngineEvent> events) {
        for (BattlePlayerState player : state.getPlayers() == null ? List.<BattlePlayerState>of() : state.getPlayers()) {
            if (!player.isDowned() || player.getBleedOutRound() == null
                    || state.getRoundNo() < player.getBleedOutRound()) continue;
            Long downedBy = player.getDownedByPlayerId();
            player.markEliminated();
            BattlePlayerState attacker = state.findPlayer(downedBy);
            if (attacker != null) attacker.setKills(attacker.getKills() + 1);
            addEvent(state, events, "BATTLE_PLAYER_ELIMINATED", downedBy,
                    Map.of("playerId", player.getPlayerId(), "reason", "BLEED_OUT"));
        }
    }

    private void updateRingAndApplyZoneDamage(BattleRoyaleGameState state, List<EngineEvent> events) {
        int stage = BattleRoyaleRules.ringStageForRound(state.getRoundNo());
        ensureRingOrder(state);
        List<String> ringOrder = state.getRingOrder();
        int safeCount = 6 - stage;
        List<String> safeRegions = new ArrayList<>(ringOrder.subList(0, safeCount));
        if (state.getRingStage() != stage || state.getSafeRegions() == null || !state.getSafeRegions().equals(safeRegions)) {
            state.setRingStage(stage);
            state.setSafeRegions(safeRegions);
            state.setSafeRegion(safeRegions.get(0));
            Map<String, Object> zonePayload = new LinkedHashMap<>();
            zonePayload.put("ringStage", stage);
            zonePayload.put("safeRegions", safeRegions);
            zonePayload.put("nextShrinkRound", stage < 5 ? stage * 3 + 1 : null);
            addEvent(state, events, "BATTLE_ZONE_UPDATED", null, zonePayload);
        }
        if (state.getRoundNo() % 4 == 0 && state.getLastSupplyDropRound() != state.getRoundNo()) {
            int dropIndex = Math.floorMod((int) (state.getRandomSeed() + state.getRoundNo()), safeRegions.size());
            String dropRegion = safeRegions.get(dropIndex);
            state.setSupplyDropRegion(dropRegion);
            state.setSupplyDropClaimPlayerId(null);
            state.setSupplyDropClaimActionsRemaining(0);
            state.setLastSupplyDropRound(state.getRoundNo());
            addEvent(state, events, "BATTLE_SUPPLY_DROP", null,
                    Map.of("region", dropRegion, "round", state.getRoundNo(), "bonus", "高级武器、弹药与补给"));
        }
        if (stage <= 1) return;
        int zoneDamage = BattleRoyaleRules.zoneDamage(stage);
        for (BattlePlayerState player : state.alivePlayers()) {
            if (safeRegions.contains(player.getRegion())) continue;
            player.setHealth(player.getHealth() - zoneDamage);
            if (player.getHealth() <= 0) player.markDowned(state.getRoundNo(), BLEED_OUT_AFTER_ROUNDS, null);
            Map<String, Object> damagePayload = new LinkedHashMap<>();
            damagePayload.put("playerId", player.getPlayerId());
            damagePayload.put("region", player.getRegion());
            damagePayload.put("damage", zoneDamage);
            damagePayload.put("health", player.getHealth());
            damagePayload.put("ringStage", stage);
            addEvent(state, events, "BATTLE_ZONE_DAMAGE", player.getPlayerId(), damagePayload);
            if (player.isDowned()) {
                addEvent(state, events, "BATTLE_PLAYER_DOWNED", player.getPlayerId(),
                        Map.of("playerId", player.getPlayerId(), "downedByPlayerId", -1L,
                                "region", player.getRegion(), "bleedOutRound", player.getBleedOutRound()));
            }
        }
    }

    private void addEvent(BattleRoyaleGameState state, List<EngineEvent> events, String type,
                          Long actorPlayerId, Map<String, Object> payload) {
        addEvent(state, events, type, actorPlayerId, payload, payload);
    }

    private void addEvent(BattleRoyaleGameState state, List<EngineEvent> events, String type,
                          Long actorPlayerId, Map<String, Object> payload,
                          Map<String, Object> publicPayload) {
        state.setSequenceNo(state.getSequenceNo() + 1);
        events.add(new EngineEvent(type, actorPlayerId, payload, publicPayload));
    }

    /** 初始化并返回当前回合冻结的行动者；每轮轮换先手，淘汰不会使后续玩家左移。 */
    public BattlePlayerState currentTurnPlayer(BattleRoyaleGameState state) {
        ensureRoundTurnOrder(state);
        skipEliminatedTurnSlots(state);
        if (state.getActionsInRound() >= state.getRoundTurnOrder().size()) return null;
        return state.findPlayer(state.getRoundTurnOrder().get(state.getActionsInRound()));
    }

    private void ensureRoundTurnOrder(BattleRoyaleGameState state) {
        if (state.getRoundTurnOrder() != null && !state.getRoundTurnOrder().isEmpty()) return;
        List<Long> order = new ArrayList<>(state.contendingPlayers().stream()
                .map(BattlePlayerState::getPlayerId).toList());
        if (!order.isEmpty()) Collections.rotate(order, -Math.floorMod(state.getRoundNo() - 1, order.size()));
        state.setRoundTurnOrder(order);
        state.setActionsInRound(Math.min(state.getActionsInRound(), order.size()));
    }

    private void skipEliminatedTurnSlots(BattleRoyaleGameState state) {
        List<Long> order = state.getRoundTurnOrder() == null ? List.of() : state.getRoundTurnOrder();
        while (state.getActionsInRound() < order.size()) {
            BattlePlayerState next = state.findPlayer(order.get(state.getActionsInRound()));
            if (next != null && !next.isEliminated()) break;
            state.setActionsInRound(state.getActionsInRound() + 1);
        }
    }

    private void ensureRingOrder(BattleRoyaleGameState state) {
        if (state.getRingOrder() == null || state.getRingOrder().size() != state.getRegions().size()
                || !new HashSet<>(state.getRingOrder()).equals(new HashSet<>(state.getRegions()))) {
            state.setRingOrder(new ArrayList<>(BattleRoyaleRules.ringOrder(state.getRegions(), state.getRandomSeed())));
        }
    }

    private boolean hasActiveAllyCover(BattleRoyaleGameState state, Long attackerId, BattlePlayerState target) {
        return state.getAlliances() != null && state.getAlliances().stream()
                .filter(alliance -> alliance.getStatus() == BattleAllianceStatus.ACTIVE)
                .filter(alliance -> alliance.includes(target.getPlayerId()))
                .map(alliance -> state.findPlayer(alliance.otherMember(target.getPlayerId())))
                .anyMatch(ally -> ally != null && ally.isActive()
                        && !Objects.equals(ally.getPlayerId(), attackerId)
                        && Objects.equals(ally.getRegion(), target.getRegion()));
    }
}
