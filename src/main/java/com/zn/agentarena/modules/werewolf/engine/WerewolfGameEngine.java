package com.zn.agentarena.modules.werewolf.engine;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.game.engine.ActionValidation;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.game.engine.EngineResult;
import com.zn.agentarena.modules.game.engine.GameEngine;
import com.zn.agentarena.modules.werewolf.domain.*;

import java.util.*;

/**
 * 狼人杀确定性规则引擎。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public class WerewolfGameEngine implements GameEngine<WerewolfGameState, WerewolfAction> {

    /**
     * 校验狼人杀行动。
     *
     * @param state 游戏状态
     * @param action 玩家行动
     * @return 校验结果
     */
    @Override
    public ActionValidation validate(WerewolfGameState state, WerewolfAction action) {
        if (state == null || action == null || action.type() == null) {
            return invalid(ResultCodeEnum.PARAMS_ERROR, "游戏状态和行动不能为空");
        }
        if (isFinished(state)) {
            return invalid(ResultCodeEnum.GAME_STATUS_ERROR, "游戏已经结束");
        }

        WerewolfPlayerState actor = state.findPlayer(action.actorPlayerId());
        if (actor == null) {
            return invalid(ResultCodeEnum.PLAYER_NOT_FOUND, "行动玩家不存在");
        }
        if (!actor.isAlive()) {
            return invalid(ResultCodeEnum.PLAYER_ALREADY_ELIMINATED, "出局玩家不能行动");
        }

        ActionValidation phaseValidation = validatePhaseAndRole(state, action, actor);
        if (!phaseValidation.valid()) {
            return phaseValidation;
        }
        return validateTarget(state, action, actor);
    }

    /**
     * 执行狼人杀行动并推进状态。
     *
     * @param state 游戏状态
     * @param action 玩家行动
     * @return 引擎执行结果
     */
    @Override
    public EngineResult<WerewolfGameState> execute(WerewolfGameState state, WerewolfAction action) {
        ActionValidation validation = validate(state, action);
        if (!validation.valid()) {
            throw CustomException.create(validation.resultCode(), validation.message());
        }

        List<EngineEvent> events = new ArrayList<>();
        switch (action.type()) {
            case WOLF_KILL -> executeWolfKill(state, action, events);
            case GUARD_PROTECT -> executeGuardProtect(state, action, events);
            case SEER_CHECK -> executeSeerCheck(state, action, events);
            case WITCH_SAVE, WITCH_POISON, SKIP -> executeWitchAction(state, action, events);
            case SPEAK -> executeSpeech(state, action, events);
            case VOTE -> executeVote(state, action, events);
            default -> throw CustomException.create(ResultCodeEnum.ACTION_NOT_ALLOWED);
        }
        return new EngineResult<>(state, List.copyOf(events));
    }

    @Override
    /** 判断狼人杀游戏是否已产生胜方。 */
    public boolean isFinished(WerewolfGameState state) {
        return state != null && state.getPhase() == WerewolfPhase.FINISHED;
    }

    private ActionValidation validatePhaseAndRole(WerewolfGameState state, WerewolfAction action,
                                                   WerewolfPlayerState actor) {
        return switch (state.getPhase()) {
            case NIGHT_WOLF -> action.type() == WerewolfActionType.WOLF_KILL
                    && actor.getRole() == WerewolfRole.WEREWOLF
                    ? ActionValidation.success()
                    : invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前仅允许狼人选择袭击目标");
            case NIGHT_GUARD -> action.type() == WerewolfActionType.GUARD_PROTECT
                    && actor.getRole() == WerewolfRole.GUARD
                    ? ActionValidation.success()
                    : invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前仅允许守卫选择保护目标");
            case NIGHT_SEER -> action.type() == WerewolfActionType.SEER_CHECK
                    && actor.getRole() == WerewolfRole.SEER
                    ? ActionValidation.success()
                    : invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前仅允许预言家查验玩家");
            case NIGHT_WITCH -> isWitchAction(action.type()) && actor.getRole() == WerewolfRole.WITCH
                    ? ActionValidation.success()
                    : invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前仅允许女巫使用技能或跳过");
            case DAY_DISCUSSION -> validateDiscussionActor(state, action, actor);
            case DAY_VOTE -> action.type() == WerewolfActionType.VOTE && !state.getVotes().containsKey(actor.getPlayerId())
                    ? ActionValidation.success()
                    : invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前只能投票且每名玩家只能投一票");
            case FINISHED -> invalid(ResultCodeEnum.GAME_STATUS_ERROR, "游戏已经结束");
        };
    }

    private ActionValidation validateDiscussionActor(WerewolfGameState state, WerewolfAction action,
                                                       WerewolfPlayerState actor) {
        List<WerewolfPlayerState> alivePlayers = state.alivePlayers();
        if (action.type() != WerewolfActionType.SPEAK || state.getDiscussionIndex() >= alivePlayers.size()) {
            return invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前仅允许指定玩家发言");
        }
        WerewolfPlayerState expectedActor = alivePlayers.get(state.getDiscussionIndex());
        return expectedActor.getPlayerId().equals(actor.getPlayerId())
                ? ActionValidation.success()
                : invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "尚未轮到该玩家发言");
    }

    private ActionValidation validateTarget(WerewolfGameState state, WerewolfAction action,
                                              WerewolfPlayerState actor) {
        if (action.type() == WerewolfActionType.SPEAK || action.type() == WerewolfActionType.SKIP
                || action.type() == WerewolfActionType.WITCH_SAVE) {
            if (action.type() == WerewolfActionType.WITCH_SAVE && !actor.isAntidoteAvailable()) {
                return invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "女巫解药已经使用");
            }
            if (action.type() == WerewolfActionType.WITCH_SAVE && state.getNightKillTargetId() == null) {
                return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "当前夜晚没有可救治的玩家");
            }
            return ActionValidation.success();
        }

        WerewolfPlayerState target = state.findPlayer(action.targetPlayerId());
        if (target == null || !target.isAlive()) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "行动目标不存在或已经出局");
        }
        if (target.getPlayerId().equals(actor.getPlayerId()) && action.type() != WerewolfActionType.GUARD_PROTECT) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "不能选择自己作为目标");
        }
        if (action.type() == WerewolfActionType.GUARD_PROTECT
                && target.getPlayerId().equals(state.getLastGuardTargetId())) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "守卫不能连续两晚保护同一名玩家");
        }
        if (action.type() == WerewolfActionType.WOLF_KILL && target.getRole() == WerewolfRole.WEREWOLF) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "狼人不能袭击队友");
        }
        if (action.type() == WerewolfActionType.WITCH_POISON && !actor.isPoisonAvailable()) {
            return invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "女巫毒药已经使用");
        }
        return ActionValidation.success();
    }

    private void executeWolfKill(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        state.setNightKillTargetId(action.targetPlayerId());
        addEvent(state, events, "WOLF_TARGET_SELECTED", action.actorPlayerId(),
                Map.of("targetPlayerId", action.targetPlayerId()), Map.of("completed", true));
        if (hasAliveRole(state, WerewolfRole.GUARD)) changePhase(state, WerewolfPhase.NIGHT_GUARD, events);
        else advanceAfterGuard(state, events);
    }

    private void executeGuardProtect(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        state.setGuardedTargetId(action.targetPlayerId());
        addEvent(state, events, "GUARD_PROTECTED", action.actorPlayerId(),
                Map.of("targetPlayerId", action.targetPlayerId()), Map.of("completed", true));
        advanceAfterGuard(state, events);
    }

    private void advanceAfterGuard(WerewolfGameState state, List<EngineEvent> events) {
        if (hasAliveRole(state, WerewolfRole.SEER)) changePhase(state, WerewolfPhase.NIGHT_SEER, events);
        else advanceAfterSeer(state, events);
    }

    private void executeSeerCheck(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        WerewolfPlayerState target = state.findPlayer(action.targetPlayerId());
        boolean wolf = target.getRole().isWolfCamp();
        WerewolfPlayerState seer = state.findPlayer(action.actorPlayerId());
        seer.getSeerChecks().put(target.getPlayerId(), wolf);
        addEvent(state, events, "SEER_CHECKED", action.actorPlayerId(),
                Map.of("targetPlayerId", target.getPlayerId(), "wolf", wolf), Map.of("completed", true));
        advanceAfterSeer(state, events);
    }

    private void advanceAfterSeer(WerewolfGameState state, List<EngineEvent> events) {
        if (hasAliveRole(state, WerewolfRole.WITCH)) changePhase(state, WerewolfPhase.NIGHT_WITCH, events);
        else settleNight(state, events);
    }

    private void executeWitchAction(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        WerewolfPlayerState witch = state.findPlayer(action.actorPlayerId());
        if (action.type() == WerewolfActionType.WITCH_SAVE) {
            witch.setAntidoteAvailable(false);
            state.setNightSaved(true);
        } else if (action.type() == WerewolfActionType.WITCH_POISON) {
            witch.setPoisonAvailable(false);
            state.setPoisonTargetId(action.targetPlayerId());
        }
        addEvent(state, events, "WITCH_ACTION_COMPLETED", action.actorPlayerId(),
                Map.of("action", action.type().name(), "targetPlayerId",
                        action.targetPlayerId() == null ? -1L : action.targetPlayerId()),
                Map.of("completed", true));
        settleNight(state, events);
    }

    private void settleNight(WerewolfGameState state, List<EngineEvent> events) {
        Set<Long> eliminated = new LinkedHashSet<>();
        boolean guarded = state.getNightKillTargetId() != null
                && state.getNightKillTargetId().equals(state.getGuardedTargetId());
        if (!state.isNightSaved() && !guarded && state.getNightKillTargetId() != null) {
            eliminated.add(state.getNightKillTargetId());
        }
        if (state.getPoisonTargetId() != null) {
            eliminated.add(state.getPoisonTargetId());
        }
        eliminated.forEach(playerId -> eliminatePlayer(state, playerId, "NIGHT", events));
        Map<String, Object> dawnPayload = new LinkedHashMap<>();
        dawnPayload.put("eliminatedIds", new ArrayList<>(eliminated));
        dawnPayload.put("peaceful", eliminated.isEmpty());
        dawnPayload.put("saved", state.isNightSaved());
        dawnPayload.put("guarded", guarded);
        addEvent(state, events, "DAWN_RESULT", null, dawnPayload,
                Map.of("eliminatedIds", new ArrayList<>(eliminated), "peaceful", eliminated.isEmpty()));
        clearNightState(state);

        if (!finishIfWinnerExists(state, events)) {
            state.setDiscussionIndex(0);
            changePhase(state, WerewolfPhase.DAY_DISCUSSION, events);
        }
    }

    private void executeSpeech(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        String message = action.message() == null ? "" : action.message().trim();
        addEvent(state, events, "PLAYER_SPOKE", action.actorPlayerId(),
                Map.of("message", message), Map.of("playerId", action.actorPlayerId(), "message", message));
        appendHistory(state, state.findPlayer(action.actorPlayerId()).getSeatNo() + "号发言：" + message);
        state.setDiscussionIndex(state.getDiscussionIndex() + 1);
        if (state.getDiscussionIndex() >= state.alivePlayers().size()) {
            state.getVotes().clear();
            changePhase(state, WerewolfPhase.DAY_VOTE, events);
        }
    }

    private void executeVote(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        state.getVotes().put(action.actorPlayerId(), action.targetPlayerId());
        addEvent(state, events, "PLAYER_VOTED", action.actorPlayerId(),
                Map.of("targetPlayerId", action.targetPlayerId()),
                Map.of("playerId", action.actorPlayerId(), "targetPlayerId", action.targetPlayerId()));
        appendHistory(state, state.findPlayer(action.actorPlayerId()).getSeatNo() + "号投票给"
                + state.findPlayer(action.targetPlayerId()).getSeatNo() + "号");
        if (state.getVotes().size() >= state.alivePlayers().size()) {
            settleVotes(state, events);
        }
    }

    private void settleVotes(WerewolfGameState state, List<EngineEvent> events) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        state.getVotes().values().forEach(target -> counts.merge(target, 1L, Long::sum));
        long highest = counts.values().stream().max(Long::compareTo).orElse(0L);
        List<Long> candidates = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == highest)
                .map(Map.Entry::getKey)
                .toList();

        addEvent(state, events, "VOTE_RESULT", null,
                Map.of("counts", counts, "candidateIds", candidates),
                Map.of("counts", counts, "candidateIds", candidates));

        if (candidates.size() == 1) {
            eliminatePlayer(state, candidates.get(0), "VOTE", events);
        } else {
            addEvent(state, events, "VOTE_TIED", null,
                    Map.of("candidateIds", candidates), Map.of("candidateIds", candidates));
        }
        state.getVotes().clear();

        if (!finishIfWinnerExists(state, events)) {
            state.setRoundNo(state.getRoundNo() + 1);
            changePhase(state, WerewolfPhase.NIGHT_WOLF, events);
        }
    }

    private void eliminatePlayer(WerewolfGameState state, Long playerId, String reason,
                                 List<EngineEvent> events) {
        WerewolfPlayerState player = state.findPlayer(playerId);
        if (player != null && player.isAlive()) {
            player.setAlive(false);
            appendHistory(state, player.getSeatNo() + "号因" + reason + "出局，身份为" + player.getRole().name());
            addEvent(state, events, "PLAYER_ELIMINATED", playerId,
                    Map.of("playerId", playerId, "role", player.getRole().name(), "reason", reason),
                    Map.of("playerId", playerId, "reason", reason));
        }
    }

    private boolean finishIfWinnerExists(WerewolfGameState state, List<EngineEvent> events) {
        long wolves = state.alivePlayers().stream().filter(player -> player.getRole().isWolfCamp()).count();
        long goodPlayers = state.alivePlayers().size() - wolves;
        WerewolfWinner winner = wolves == 0 ? WerewolfWinner.GOOD
                : wolves >= goodPlayers ? WerewolfWinner.WOLF : null;
        if (winner == null) {
            return false;
        }
        state.setWinner(winner);
        state.setPhase(WerewolfPhase.FINISHED);
        addEvent(state, events, "GAME_FINISHED", null,
                Map.of("winner", winner.name()), Map.of("winner", winner.name()));
        return true;
    }

    private void changePhase(WerewolfGameState state, WerewolfPhase phase, List<EngineEvent> events) {
        state.setPhase(phase);
        addEvent(state, events, "PHASE_CHANGED", null,
                Map.of("phase", phase.name()), Map.of("phase", phase.name()));
    }

    private void addEvent(WerewolfGameState state, List<EngineEvent> events, String type,
                          Long actorPlayerId, Map<String, Object> payload, Map<String, Object> publicPayload) {
        state.setSequenceNo(state.getSequenceNo() + 1);
        events.add(new EngineEvent(type, actorPlayerId, payload, publicPayload));
    }

    private void clearNightState(WerewolfGameState state) {
        if (state.getGuardedTargetId() != null) state.setLastGuardTargetId(state.getGuardedTargetId());
        state.setNightKillTargetId(null);
        state.setPoisonTargetId(null);
        state.setGuardedTargetId(null);
        state.setNightSaved(false);
    }

    private boolean hasAliveRole(WerewolfGameState state, WerewolfRole role) {
        return state.alivePlayers().stream().anyMatch(player -> player.getRole() == role);
    }

    private void appendHistory(WerewolfGameState state, String item) {
        state.getPublicHistory().add(item);
        if (state.getPublicHistory().size() > 30) state.getPublicHistory().remove(0);
    }

    private boolean isWitchAction(WerewolfActionType type) {
        return type == WerewolfActionType.WITCH_SAVE
                || type == WerewolfActionType.WITCH_POISON
                || type == WerewolfActionType.SKIP;
    }

    private ActionValidation invalid(ResultCodeEnum resultCode, String message) {
        return ActionValidation.fail(resultCode, message);
    }
}
