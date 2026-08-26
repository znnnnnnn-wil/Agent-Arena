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
            case SPEAK -> executeInitialSpeech(state, action, events);
            case QUESTION -> executeQuestion(state, action, events);
            case RESPOND -> executeResponse(state, action, events);
            case FINAL_STATEMENT -> executeFinalStatement(state, action, events);
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
            case DAY_QUESTION -> validateQuestionActor(state, action, actor, true);
            case DAY_RESPONSE -> validateQuestionActor(state, action, actor, false);
            case DAY_FINAL_STATEMENT -> validateFinalStatementActor(state, action, actor);
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

    private ActionValidation validateQuestionActor(WerewolfGameState state, WerewolfAction action,
                                                     WerewolfPlayerState actor, boolean question) {
        WerewolfActionType expectedType = question ? WerewolfActionType.QUESTION : WerewolfActionType.RESPOND;
        List<WerewolfQuestionPair> queue = state.getQuestionQueue() == null ? List.of() : state.getQuestionQueue();
        if (action.type() != expectedType || state.getQuestionIndex() < 0
                || state.getQuestionIndex() >= queue.size()) {
            return invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, question ? "当前没有可执行的质询" : "当前没有待回应的质询");
        }
        WerewolfQuestionPair pair = queue.get(state.getQuestionIndex());
        Long expectedActor = question ? pair.getQuestionerPlayerId() : pair.getRespondentPlayerId();
        Long expectedTarget = question ? pair.getRespondentPlayerId() : pair.getQuestionerPlayerId();
        if (!actor.getPlayerId().equals(expectedActor)) {
            return invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, question ? "尚未轮到该玩家质询" : "仅被质询玩家可以回应");
        }
        return Objects.equals(expectedTarget, action.targetPlayerId())
                ? ActionValidation.success()
                : invalid(ResultCodeEnum.ACTION_TARGET_INVALID, question ? "质询目标与争议玩家不一致" : "回应目标必须是质询者");
    }

    private ActionValidation validateFinalStatementActor(WerewolfGameState state, WerewolfAction action,
                                                           WerewolfPlayerState actor) {
        List<WerewolfPlayerState> alive = state.alivePlayers();
        if (action.type() != WerewolfActionType.FINAL_STATEMENT
                || state.getFinalStatementIndex() < 0 || state.getFinalStatementIndex() >= alive.size()) {
            return invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前没有可执行的最终陈述");
        }
        return alive.get(state.getFinalStatementIndex()).getPlayerId().equals(actor.getPlayerId())
                ? ActionValidation.success()
                : invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "尚未轮到该玩家最终陈述");
    }

    private ActionValidation validateTarget(WerewolfGameState state, WerewolfAction action,
                                              WerewolfPlayerState actor) {
        if (action.type() == WerewolfActionType.SPEAK || action.type() == WerewolfActionType.FINAL_STATEMENT
                || action.type() == WerewolfActionType.SKIP
                || action.type() == WerewolfActionType.WITCH_SAVE) {
            if (action.targetPlayerId() != null) {
                return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "当前行动不应指定目标");
            }
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
            ensureDiscussionCollections(state);
            state.setDiscussionIndex(0);
            state.setQuestionIndex(0);
            state.setFinalStatementIndex(0);
            state.getQuestionQueue().clear();
            state.getClaimChangedPlayerIds().clear();
            state.getVoteIntentChangedPlayerIds().clear();
            changePhase(state, WerewolfPhase.DAY_DISCUSSION, events);
        }
    }

    private void executeInitialSpeech(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        recordPublicStance(state, action, true);
        addPublicSpeechEvent(state, action, "PLAYER_SPOKE", events);
        state.setDiscussionIndex(state.getDiscussionIndex() + 1);
        if (state.getDiscussionIndex() >= state.alivePlayers().size()) {
            if (state.getDiscussionVersion() >= 2 && prepareQuestionQueue(state, events)) {
                changePhase(state, WerewolfPhase.DAY_QUESTION, events);
            } else {
                beginVoting(state, events);
            }
        }
    }

    private void executeQuestion(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        recordPublicStance(state, action, false);
        addPublicSpeechEvent(state, action, "PLAYER_QUESTIONED", events);
        changePhase(state, WerewolfPhase.DAY_RESPONSE, events);
    }

    private void executeResponse(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        recordPublicStance(state, action, true);
        addPublicSpeechEvent(state, action, "PLAYER_RESPONDED", events);
        state.setQuestionIndex(state.getQuestionIndex() + 1);
        if (state.getQuestionQueue() != null && state.getQuestionIndex() < state.getQuestionQueue().size()) {
            changePhase(state, WerewolfPhase.DAY_QUESTION, events);
        } else {
            state.setFinalStatementIndex(0);
            changePhase(state, WerewolfPhase.DAY_FINAL_STATEMENT, events);
        }
    }

    private void executeFinalStatement(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        recordPublicStance(state, action, true);
        addPublicSpeechEvent(state, action, "PLAYER_FINAL_STATEMENT", events);
        state.setFinalStatementIndex(state.getFinalStatementIndex() + 1);
        if (state.getFinalStatementIndex() >= state.alivePlayers().size()) {
            beginVoting(state, events);
        }
    }

    private void beginVoting(WerewolfGameState state, List<EngineEvent> events) {
        state.getVotes().clear();
        changePhase(state, WerewolfPhase.DAY_VOTE, events);
    }

    private void addPublicSpeechEvent(WerewolfGameState state, WerewolfAction action, String eventType,
                                      List<EngineEvent> events) {
        String message = action.message() == null ? "" : action.message().trim();
        WerewolfPlayerState actor = state.findPlayer(action.actorPlayerId());
        WerewolfPlayerState target = state.findPlayer(action.targetPlayerId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roundNo", state.getRoundNo());
        payload.put("actorPlayerId", actor.getPlayerId());
        payload.put("actorSeatNo", actor.getSeatNo());
        payload.put("actorDisplayName", actor.getDisplayName());
        payload.put("targetPlayerId", target == null ? null : target.getPlayerId());
        payload.put("targetSeatNo", target == null ? null : target.getSeatNo());
        payload.put("targetDisplayName", target == null ? null : target.getDisplayName());
        payload.put("message", message);
        payload.put("publicSpeech", message);
        payload.put("claims", action.claims());
        payload.put("suspects", action.suspects());
        payload.put("supports", action.supports());
        payload.put("voteIntentSeatNo", action.voteIntentSeatNo());
        payload.put("reasonTags", action.reasonTags());
        payload.put("reasonSummary", action.reasonSummary());
        payload.put("confidence", action.confidence());
        addEvent(state, events, eventType, action.actorPlayerId(), payload, new LinkedHashMap<>(payload));
        String verb = switch (eventType) {
            case "PLAYER_QUESTIONED" -> "质询" + target.getSeatNo() + "号：";
            case "PLAYER_RESPONDED" -> "回应" + target.getSeatNo() + "号：";
            case "PLAYER_FINAL_STATEMENT" -> "最终陈述：";
            default -> "发言：";
        };
        appendHistory(state, actor.getSeatNo() + "号" + verb + message);
    }

    private void recordPublicStance(WerewolfGameState state, WerewolfAction action, boolean replaceRelations) {
        ensureDiscussionCollections(state);
        WerewolfPlayerState actor = state.findPlayer(action.actorPlayerId());
        WerewolfPublicStance previous = state.getPublicStances().get(actor.getPlayerId());
        if (replaceRelations && previous != null) {
            if (!previous.getClaims().isEmpty() && !action.claims().isEmpty()
                    && !new LinkedHashSet<>(previous.getClaims()).equals(new LinkedHashSet<>(action.claims()))) {
                state.getClaimChangedPlayerIds().add(actor.getPlayerId());
            }
            if (previous.getVoteIntentSeatNo() != null && action.voteIntentSeatNo() != null
                    && !previous.getVoteIntentSeatNo().equals(action.voteIntentSeatNo())) {
                state.getVoteIntentChangedPlayerIds().add(actor.getPlayerId());
            }
        }
        WerewolfPublicStance stance = WerewolfPublicStance.builder()
                .playerId(actor.getPlayerId()).seatNo(actor.getSeatNo())
                .claims(replaceRelations || previous == null ? new ArrayList<>(action.claims())
                        : new ArrayList<>(previous.getClaims()))
                .suspects(replaceRelations || previous == null ? new ArrayList<>(action.suspects())
                        : new ArrayList<>(previous.getSuspects()))
                .supports(replaceRelations || previous == null ? new ArrayList<>(action.supports())
                        : new ArrayList<>(previous.getSupports()))
                .voteIntentSeatNo(replaceRelations || previous == null ? action.voteIntentSeatNo()
                        : previous.getVoteIntentSeatNo())
                .reasonTags(new ArrayList<>(action.reasonTags()))
                .reasonSummary(action.reasonSummary()).lastAction(action.type().name())
                .updatedRound(state.getRoundNo()).confidence(action.confidence()).build();
        state.getPublicStances().put(actor.getPlayerId(), stance);
    }

    private boolean prepareQuestionQueue(WerewolfGameState state, List<EngineEvent> events) {
        ensureDiscussionCollections(state);
        Map<Integer, WerewolfPlayerState> bySeat = state.alivePlayers().stream()
                .collect(java.util.stream.Collectors.toMap(WerewolfPlayerState::getSeatNo, player -> player));
        List<WerewolfPublicStance> todaysStances = state.getPublicStances().values().stream()
                .filter(stance -> stance.getUpdatedRound() == state.getRoundNo())
                .filter(stance -> state.findPlayer(stance.getPlayerId()) != null
                        && state.findPlayer(stance.getPlayerId()).isAlive()).toList();
        Map<Long, Integer> scores = new LinkedHashMap<>();
        for (WerewolfPublicStance stance : todaysStances) {
            for (Integer seatNo : stance.getSuspects()) {
                WerewolfPlayerState target = bySeat.get(seatNo);
                if (target != null) scores.merge(target.getPlayerId(), 2, Integer::sum);
            }
            WerewolfPlayerState voteTarget = bySeat.get(stance.getVoteIntentSeatNo());
            if (voteTarget != null) scores.merge(voteTarget.getPlayerId(), 1, Integer::sum);
            if (state.getClaimChangedPlayerIds().contains(stance.getPlayerId())) {
                scores.merge(stance.getPlayerId(), 2, Integer::sum);
            }
            if (state.getVoteIntentChangedPlayerIds().contains(stance.getPlayerId())) {
                scores.merge(stance.getPlayerId(), 1, Integer::sum);
            }
            Long previousVoteId = state.getPreviousVotes().get(stance.getPlayerId());
            WerewolfPlayerState previousVoteTarget = state.findPlayer(previousVoteId);
            if (previousVoteTarget != null && stance.getVoteIntentSeatNo() != null
                    && !previousVoteTarget.getSeatNo().equals(stance.getVoteIntentSeatNo())) {
                scores.merge(stance.getPlayerId(), 1, Integer::sum);
            }
        }
        Map<String, List<WerewolfPublicStance>> roleClaimants = todaysStances.stream()
                .flatMap(stance -> stance.getClaims().stream()
                        .filter(claim -> !"VILLAGER".equals(claim))
                        .map(claim -> Map.entry(claim, stance)))
                .collect(java.util.stream.Collectors.groupingBy(Map.Entry::getKey,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(Map.Entry::getValue,
                                java.util.stream.Collectors.toList())));
        roleClaimants.values().stream().filter(claimants -> claimants.size() > 1)
                .flatMap(Collection::stream)
                .forEach(stance -> scores.merge(stance.getPlayerId(), 2, Integer::sum));
        for (WerewolfPublicStance stance : todaysStances) {
            for (Integer suspectSeat : stance.getSuspects()) {
                WerewolfPlayerState suspect = bySeat.get(suspectSeat);
                WerewolfPublicStance reverse = suspect == null ? null : state.getPublicStances().get(suspect.getPlayerId());
                if (reverse != null && reverse.getSuspects().contains(stance.getSeatNo())) {
                    scores.merge(stance.getPlayerId(), 1, Integer::sum);
                }
            }
        }

        List<WerewolfPlayerState> controversial = scores.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .map(entry -> state.findPlayer(entry.getKey()))
                .filter(Objects::nonNull).filter(WerewolfPlayerState::isAlive)
                .sorted(Comparator.<WerewolfPlayerState>comparingInt(player -> scores.get(player.getPlayerId()))
                        .reversed().thenComparingInt(WerewolfPlayerState::getSeatNo))
                .limit(2).toList();
        if (controversial.isEmpty()) {
            state.getQuestionQueue().clear();
            return false;
        }

        List<WerewolfQuestionPair> queue = new ArrayList<>();
        Set<Long> usedQuestioners = new LinkedHashSet<>();
        for (WerewolfPlayerState respondent : controversial) {
            WerewolfPublicStance questionerStance = todaysStances.stream()
                    .filter(stance -> !stance.getPlayerId().equals(respondent.getPlayerId()))
                    .filter(stance -> !usedQuestioners.contains(stance.getPlayerId()))
                    .filter(stance -> stance.getSuspects().contains(respondent.getSeatNo()))
                    .max(Comparator.<WerewolfPublicStance>comparingInt(stance ->
                                    (Objects.equals(stance.getVoteIntentSeatNo(), respondent.getSeatNo()) ? 2 : 0)
                                            + (int) Math.round(stance.getConfidence() * 10))
                            .thenComparingInt(stance -> -stance.getSeatNo()))
                    .orElse(null);
            if (questionerStance == null) continue;
            queue.add(WerewolfQuestionPair.builder().questionerPlayerId(questionerStance.getPlayerId())
                    .respondentPlayerId(respondent.getPlayerId()).build());
            usedQuestioners.add(questionerStance.getPlayerId());
        }
        state.setQuestionQueue(new ArrayList<>(queue));
        state.setQuestionIndex(0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roundNo", state.getRoundNo());
        payload.put("controversialPlayers", controversial.stream()
                .map(player -> publicPlayerRef(player, scores.getOrDefault(player.getPlayerId(), 0))).toList());
        payload.put("questionPairs", queue.stream().map(pair -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questioner", publicPlayerRef(state.findPlayer(pair.getQuestionerPlayerId()), null));
            item.put("respondent", publicPlayerRef(state.findPlayer(pair.getRespondentPlayerId()), null));
            return item;
        }).toList());
        addEvent(state, events, "CONTROVERSY_IDENTIFIED", null, payload, new LinkedHashMap<>(payload));
        return !queue.isEmpty();
    }

    private Map<String, Object> publicPlayerRef(WerewolfPlayerState player, Integer score) {
        Map<String, Object> ref = new LinkedHashMap<>();
        if (player != null) {
            ref.put("playerId", player.getPlayerId());
            ref.put("seatNo", player.getSeatNo());
            ref.put("displayName", player.getDisplayName());
        }
        if (score != null) ref.put("controversyScore", score);
        return ref;
    }

    private void ensureDiscussionCollections(WerewolfGameState state) {
        if (state.getPublicStances() == null) state.setPublicStances(new LinkedHashMap<>());
        state.getPublicStances().values().stream().filter(Objects::nonNull).forEach(stance -> {
            if (stance.getClaims() == null) stance.setClaims(new ArrayList<>());
            if (stance.getSuspects() == null) stance.setSuspects(new ArrayList<>());
            if (stance.getSupports() == null) stance.setSupports(new ArrayList<>());
            if (stance.getReasonTags() == null) stance.setReasonTags(new ArrayList<>());
        });
        if (state.getQuestionQueue() == null) state.setQuestionQueue(new ArrayList<>());
        if (state.getClaimChangedPlayerIds() == null) state.setClaimChangedPlayerIds(new LinkedHashSet<>());
        if (state.getVoteIntentChangedPlayerIds() == null) state.setVoteIntentChangedPlayerIds(new LinkedHashSet<>());
        if (state.getPreviousVotes() == null) state.setPreviousVotes(new LinkedHashMap<>());
    }

    private void executeVote(WerewolfGameState state, WerewolfAction action, List<EngineEvent> events) {
        state.getVotes().put(action.actorPlayerId(), action.targetPlayerId());
        WerewolfPlayerState actor = state.findPlayer(action.actorPlayerId());
        WerewolfPlayerState target = state.findPlayer(action.targetPlayerId());
        addEvent(state, events, "PLAYER_VOTED", action.actorPlayerId(),
                Map.of("playerId", actor.getPlayerId(), "actorSeatNo", actor.getSeatNo(),
                        "targetPlayerId", target.getPlayerId(), "targetSeatNo", target.getSeatNo()),
                Map.of("playerId", actor.getPlayerId(), "actorSeatNo", actor.getSeatNo(),
                        "targetPlayerId", target.getPlayerId(), "targetSeatNo", target.getSeatNo()));
        appendHistory(state, actor.getSeatNo() + "号投票给" + target.getSeatNo() + "号");
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
        state.setPreviousVotes(new LinkedHashMap<>(state.getVotes()));
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
