package com.zn.agentarena.modules.battle.engine;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
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
        if (actor == null || !actor.isAlive()) {
            return ActionValidation.fail(ResultCodeEnum.PLAYER_NOT_FOUND, "玩家不存在或已经出局");
        }
        if (action.type() == BattleActionType.MOVE) {
            return state.getRegions().contains(action.targetRegion())
                    ? ActionValidation.success()
                    : ActionValidation.fail(ResultCodeEnum.ACTION_TARGET_INVALID, "目标区域不存在");
        }
        if (action.type() == BattleActionType.ATTACK || action.type() == BattleActionType.TRADE) {
            BattlePlayerState target = state.findPlayer(action.targetPlayerId());
            if (target == null || !target.isAlive() || !actor.getRegion().equals(target.getRegion())) {
                return ActionValidation.fail(ResultCodeEnum.ACTION_TARGET_INVALID, "目标玩家不在同一区域或不可用");
            }
        }
        if (action.type() == BattleActionType.HEAL && actor.getMedkits() <= 0) {
            return ActionValidation.fail(ResultCodeEnum.ACTION_TARGET_INVALID, "没有可用医疗包");
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
        BattlePlayerState actionTarget = action.targetPlayerId() == null ? null : state.findPlayer(action.targetPlayerId());
        boolean targetWasAlive = actionTarget != null && actionTarget.isAlive();
        List<EngineEvent> events = new ArrayList<>();
        String lootType = null;
        String lootName = null;
        int lootAmount = 0;
        int damageDealt = 0;
        boolean criticalHit = false;
        switch (action.type()) {
            case MOVE -> actor.setRegion(action.targetRegion());
            case SEARCH -> {
                SearchOutcome outcome = searchRegion(state, actor);
                lootType = outcome.type();
                lootName = outcome.name();
                lootAmount = outcome.amount();
            }
            case REST -> actor.setHealth(Math.min(100, actor.getHealth() + 15));
            case HIDE -> actor.setHidden(true);
            case HEAL -> {
                actor.setMedkits(actor.getMedkits() - 1);
                actor.setHealth(Math.min(100, actor.getHealth() + 35));
            }
            case GUARD -> actor.setArmor(Math.min(60, actor.getArmor() + 12));
            case TRADE -> {
                BattlePlayerState target = state.findPlayer(action.targetPlayerId());
                if (actor.getFood() > 0) { actor.setFood(actor.getFood() - 1); target.setFood(target.getFood() + 1); }
            }
            case ATTACK -> {
                BattlePlayerState target = state.findPlayer(action.targetPlayerId());
                boolean weaponAttack = actor.isArmed() && actor.getAmmo() > 0;
                int rawDamage = weaponAttack ? 28 + actor.getWeaponLevel() * 12 : 18;
                if (weaponAttack) actor.setAmmo(actor.getAmmo() - 1);
                if (target.isHidden()) rawDamage = Math.max(10, rawDamage - 8);
                Random combatRandom = new Random(state.getRandomSeed() + state.getSequenceNo() * 31 + target.getPlayerId());
                criticalHit = combatRandom.nextInt(100) < 12;
                if (criticalHit) rawDamage += rawDamage / 2;
                int absorbed = Math.min(target.getArmor(), rawDamage / 2);
                target.setArmor(target.getArmor() - absorbed);
                damageDealt = rawDamage - absorbed;
                target.setHealth(target.getHealth() - damageDealt);
                actor.setTotalDamage(actor.getTotalDamage() + damageDealt);
                target.setHidden(false);
                if (target.getHealth() <= 0) {
                    target.setAlive(false);
                    actor.setKills(actor.getKills() + 1);
                }
            }
            case SKIP -> { }
        }
        actor.setHidden(action.type() == BattleActionType.HIDE);
        actor.setFood(Math.max(0, actor.getFood() - 1));
        actor.setWater(Math.max(0, actor.getWater() - 1));
        if (actor.getFood() == 0 || actor.getWater() == 0) actor.setHealth(actor.getHealth() - 5);
        if (actor.getHealth() <= 0) actor.setAlive(false);
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
        if (lootType != null) {
            actionPayload.put("lootType", lootType);
            actionPayload.put("lootName", lootName);
            actionPayload.put("lootAmount", lootAmount);
            actionPayload.put("supplyDrop", Objects.equals(state.getSupplyDropRegion(), actor.getRegion()));
        }
        if (damageDealt > 0) actionPayload.put("damage", damageDealt);
        if (criticalHit) actionPayload.put("critical", true);
        if (actionTarget != null) actionPayload.put("targetHealth", actionTarget.getHealth());
        Map<String, Object> publicActionPayload = new LinkedHashMap<>(actionPayload);
        if (action.type() == BattleActionType.HIDE) {
            publicActionPayload.remove("region");
        }
        addEvent(state, events, "BATTLE_ACTION_EXECUTED", action.actorPlayerId(),
                actionPayload, publicActionPayload);
        if (targetWasAlive && !actionTarget.isAlive()) {
            addEvent(state, events, "BATTLE_PLAYER_ELIMINATED", action.actorPlayerId(),
                    Map.of("playerId", actionTarget.getPlayerId(), "reason", "ATTACK"));
        }
        if (!actor.isAlive()) {
            addEvent(state, events, "BATTLE_PLAYER_ELIMINATED", action.actorPlayerId(),
                    Map.of("playerId", actor.getPlayerId(), "reason", "SURVIVAL"));
        }
        state.setActionsInRound(state.getActionsInRound() + 1);
        if (state.getActionsInRound() >= Math.max(1, state.alivePlayers().size())) {
            state.setActionsInRound(0);
            state.setRoundNo(state.getRoundNo() + 1);
            updateRingAndApplyZoneDamage(state, events);
        }
        if (state.alivePlayers().size() <= 1 || state.getRoundNo() >= state.getMaxRounds()) {
            BattlePlayerState winner = state.alivePlayers().stream().max(Comparator.comparingInt(this::survivalScore)).orElse(null);
            state.setWinnerPlayerId(winner == null ? null : winner.getPlayerId());
            Map<String, Object> finishPayload = new LinkedHashMap<>();
            finishPayload.put("winnerPlayerId", winner == null ? -1L : winner.getPlayerId());
            finishPayload.put("winnerName", winner == null ? "无人幸存" : winner.getDisplayName());
            finishPayload.put("score", winner == null ? 0 : survivalScore(winner));
            finishPayload.put("kills", winner == null ? 0 : winner.getKills());
            addEvent(state, events, "BATTLE_FINISHED", null, finishPayload);
        }
        return new EngineResult<>(state, events);
    }

    @Override
    /** 判断大逃杀游戏是否已结束。 */
    public boolean isFinished(BattleRoyaleGameState state) {
        return state == null || state.getWinnerPlayerId() != null || state.alivePlayers().size() <= 1
                || state.getRoundNo() >= state.getMaxRounds();
    }

    private SearchOutcome searchRegion(BattleRoyaleGameState state, BattlePlayerState actor) {
        Random random = new Random(state.getRandomSeed() + state.getSequenceNo() * 17 + actor.getPlayerId());
        boolean supplyDrop = Objects.equals(state.getSupplyDropRegion(), actor.getRegion());
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
        player.setArmor(Math.min(60, player.getArmor() + amount));
        return new SearchOutcome("ARMOR", "护甲", player.getArmor() - before);
    }

    private SearchOutcome addAmmoOrArmor(BattlePlayerState player, boolean supplyDrop) {
        if (!player.isArmed()) return addArmor(player, supplyDrop ? 25 : 15);
        int amount = supplyDrop ? 6 : 4;
        player.setAmmo(player.getAmmo() + amount);
        return new SearchOutcome("AMMO", "弹药", amount);
    }

    private int survivalScore(BattlePlayerState player) {
        return Math.max(0, player.getHealth()) * 2 + player.getArmor() + player.getFood() * 4
                + player.getWater() * 4 + player.getMedkits() * 18 + player.getWeaponLevel() * 25
                + player.getAmmo() * 2 + player.getKills() * 60;
    }

    private record SearchOutcome(String type, String name, int amount) { }

    private void updateRingAndApplyZoneDamage(BattleRoyaleGameState state, List<EngineEvent> events) {
        int stage = Math.min(5, 1 + Math.max(0, state.getRoundNo() - 1) / 3);
        List<String> ringOrder = List.of("A", "B", "C", "E", "D");
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
            state.setLastSupplyDropRound(state.getRoundNo());
            addEvent(state, events, "BATTLE_SUPPLY_DROP", null,
                    Map.of("region", dropRegion, "round", state.getRoundNo(), "bonus", "高级武器、弹药与补给"));
        }
        if (stage <= 1) return;
        int zoneDamage = switch (stage) { case 2 -> 8; case 3 -> 14; case 4 -> 22; default -> 35; };
        for (BattlePlayerState player : state.alivePlayers()) {
            if (safeRegions.contains(player.getRegion())) continue;
            player.setHealth(player.getHealth() - zoneDamage);
            if (player.getHealth() <= 0) player.setAlive(false);
            Map<String, Object> damagePayload = new LinkedHashMap<>();
            damagePayload.put("playerId", player.getPlayerId());
            damagePayload.put("region", player.getRegion());
            damagePayload.put("damage", zoneDamage);
            damagePayload.put("health", player.getHealth());
            damagePayload.put("ringStage", stage);
            addEvent(state, events, "BATTLE_ZONE_DAMAGE", player.getPlayerId(), damagePayload);
            if (!player.isAlive()) {
                addEvent(state, events, "BATTLE_PLAYER_ELIMINATED", player.getPlayerId(),
                        Map.of("playerId", player.getPlayerId(), "reason", "ZONE"));
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
}
