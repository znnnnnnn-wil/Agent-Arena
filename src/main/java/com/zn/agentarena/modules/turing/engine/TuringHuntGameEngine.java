package com.zn.agentarena.modules.turing.engine;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.game.engine.ActionValidation;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.game.engine.EngineResult;
import com.zn.agentarena.modules.game.engine.GameEngine;
import com.zn.agentarena.modules.turing.domain.*;
import com.zn.agentarena.modules.turing.prompt.TuringQuestionGenerator;
import com.zn.agentarena.modules.turing.text.TuringTextPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图灵潜伏局确定性规则引擎。
 *
 * <p>一次 {@link #execute(TuringHuntGameState, TuringHuntAction)} 只接受一个玩家行动，
 * 或执行一次 {@code ADVANCE} 阶段转换。模型调用顺序和响应时间不参与任何规则。</p>
 */
public class TuringHuntGameEngine implements GameEngine<TuringHuntGameState, TuringHuntAction> {

    public static final int MAX_SUSPECTS = 2;
    public static final int MAX_REASON_TAGS = 3;

    private final TuringQuestionGenerator questionGenerator;
    private final TuringTextPolicy textPolicy;

    public TuringHuntGameEngine() {
        this(new TuringQuestionGenerator(), TuringTextPolicy.INSTANCE);
    }

    public TuringHuntGameEngine(TuringQuestionGenerator questionGenerator) {
        this(questionGenerator, TuringTextPolicy.INSTANCE);
    }

    public TuringHuntGameEngine(TuringQuestionGenerator questionGenerator, TuringTextPolicy textPolicy) {
        this.questionGenerator = Objects.requireNonNull(questionGenerator);
        this.textPolicy = Objects.requireNonNull(textPolicy);
    }

    @Override
    public ActionValidation validate(TuringHuntGameState state, TuringHuntAction action) {
        if (state == null || action == null || action.type() == null) {
            return invalid(ResultCodeEnum.PARAMS_ERROR, "游戏状态和行动不能为空");
        }
        ensureCollections(state);
        if (state.getPhase() == TuringHuntPhase.FINISHED || state.getWinner() != null) {
            return invalid(ResultCodeEnum.GAME_STATUS_ERROR, "图灵潜伏局已经结束");
        }
        TuringHuntActionType required = requiredActionType(state);
        if (required != action.type()) {
            return invalid(ResultCodeEnum.GAME_PHASE_MISMATCH,
                    "当前阶段需要 " + required + "，不能执行 " + action.type());
        }
        if (required == TuringHuntActionType.ADVANCE) {
            if (action.actorPlayerId() != null) {
                return invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "阶段推进不能指定玩家");
            }
            return ActionValidation.success();
        }

        TuringHuntPlayerState actor = state.findPlayer(action.actorPlayerId());
        if (actor == null) return invalid(ResultCodeEnum.PLAYER_NOT_FOUND, "行动玩家不存在");
        if (!actor.isAlive()) return invalid(ResultCodeEnum.PLAYER_ALREADY_ELIMINATED, "已淘汰玩家不能行动");
        Long expectedActorId = requiredActorPlayerId(state);
        if (!Objects.equals(expectedActorId, actor.getPlayerId())) {
            return invalid(ResultCodeEnum.ACTION_NOT_ALLOWED, "当前不是该玩家的行动时机");
        }

        return switch (action.type()) {
            case ANSWER -> validatePublicText(TuringHuntActionType.ANSWER, action.answer());
            case SUSPECT -> validateSuspicion(state, actor, action);
            case QUESTION -> validateQuestion(state, actor, action);
            case RESPOND -> validateResponse(state, actor, action);
            case DEFEND -> validateDefense(state, actor, action);
            case VOTE -> validateVote(state, actor, action);
            case ADVANCE -> ActionValidation.success();
        };
    }

    @Override
    public EngineResult<TuringHuntGameState> execute(TuringHuntGameState state, TuringHuntAction action) {
        TuringHuntAction normalizedAction = action == null ? null : textPolicy.normalizeAction(action, false);
        ActionValidation validation = validate(state, normalizedAction);
        if (!validation.valid()) {
            throw CustomException.create(validation.resultCode(), validation.message());
        }
        List<EngineEvent> events = new ArrayList<>();
        if (normalizedAction.type() == TuringHuntActionType.ADVANCE) {
            advance(state, events);
        } else {
            executePlayerAction(state, normalizedAction, events);
        }
        refreshCurrentActor(state);
        return new EngineResult<>(state, List.copyOf(events));
    }

    @Override
    public boolean isFinished(TuringHuntGameState state) {
        return state != null && (state.getPhase() == TuringHuntPhase.FINISHED || state.getWinner() != null);
    }

    /** 返回下一次调用应提交的动作类型。 */
    public TuringHuntActionType requiredActionType(TuringHuntGameState state) {
        ensureCollections(state);
        return switch (state.getPhase()) {
            case ANSWER_COLLECTION -> nextMissing(state, state.getAnswersByPlayerId()) == null
                    ? TuringHuntActionType.ADVANCE : TuringHuntActionType.ANSWER;
            case SUSPICION_COLLECTION -> nextMissing(state, state.getSuspicionsByPlayerId()) == null
                    ? TuringHuntActionType.ADVANCE : TuringHuntActionType.SUSPECT;
            case QUESTION -> state.isCurrentQuestionSubmitted()
                    ? TuringHuntActionType.ADVANCE : TuringHuntActionType.QUESTION;
            case RESPONSE -> state.isCurrentResponseSubmitted()
                    ? TuringHuntActionType.ADVANCE : TuringHuntActionType.RESPOND;
            case DEFENSE -> state.getDefenseIndex() >= state.getDefenseQueue().size()
                    ? TuringHuntActionType.ADVANCE : TuringHuntActionType.DEFEND;
            case VOTE_COLLECTION -> nextMissing(state, state.getVotesByPlayerId()) == null
                    ? TuringHuntActionType.ADVANCE : TuringHuntActionType.VOTE;
            case FINISHED -> throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR,
                    "图灵潜伏局已经结束");
            default -> TuringHuntActionType.ADVANCE;
        };
    }

    /** 返回下一名玩家；确定性转换时返回 {@code null}。 */
    public Long requiredActorPlayerId(TuringHuntGameState state) {
        TuringHuntActionType required = requiredActionType(state);
        return switch (required) {
            case ANSWER -> playerId(nextMissing(state, state.getAnswersByPlayerId()));
            case SUSPECT -> playerId(nextMissing(state, state.getSuspicionsByPlayerId()));
            case QUESTION -> state.getCurrentQuestionerPlayerId();
            case RESPOND -> state.getCurrentQuestionTargetPlayerId();
            case DEFEND -> state.getDefenseIndex() < state.getDefenseQueue().size()
                    ? state.getDefenseQueue().get(state.getDefenseIndex()) : null;
            case VOTE -> playerId(nextMissing(state, state.getVotesByPlayerId()));
            case ADVANCE -> null;
        };
    }

    private void executePlayerAction(TuringHuntGameState state, TuringHuntAction action,
                                     List<EngineEvent> events) {
        TuringHuntPlayerState actor = state.findPlayer(action.actorPlayerId());
        switch (action.type()) {
            case ANSWER -> {
                TuringHuntAnswer answer = new TuringHuntAnswer(clean(action.answer()), clean(action.reasonSummary()));
                state.getAnswersByPlayerId().put(actor.getPlayerId(), answer);
                state.getAnswerSubmissionOrder().add(actor.getPlayerId());
                addEvent(state, events, TuringHuntEventType.TURING_ANSWER_SUBMITTED, actor.getPlayerId(),
                        mapOf("actorPlayerId", actor.getPlayerId(), "actorSeatNo", actor.getSeatNo(),
                                "answer", answer.answer(), "reasonSummary", answer.reasonSummary()),
                        Map.of("submitted", true));
            }
            case SUSPECT -> {
                TuringHuntSuspicion suspicion = new TuringHuntSuspicion(action.primarySuspectPlayerId(),
                        action.suspectPlayerIds(), normalizeTags(action.reasonTags()), clean(action.reasonSummary()));
                state.getSuspicionsByPlayerId().put(actor.getPlayerId(), suspicion);
                state.getPrimarySuspectByPlayerId().put(actor.getPlayerId(), action.primarySuspectPlayerId());
                addEvent(state, events, TuringHuntEventType.TURING_SUSPICION_SUBMITTED, actor.getPlayerId(),
                        mapOf("actorPlayerId", actor.getPlayerId(), "actorSeatNo", actor.getSeatNo(),
                                "primarySuspectPlayerId", action.primarySuspectPlayerId(),
                                "suspectPlayerIds", action.suspectPlayerIds(),
                                "reasonTags", suspicion.reasonTags(), "reasonSummary", suspicion.reasonSummary()),
                        Map.of("submitted", true));
            }
            case QUESTION -> {
                TuringHuntQuestion question = new TuringHuntQuestion(action.targetPlayerId(), clean(action.question()));
                state.getQuestions().put(actor.getPlayerId(), question);
                state.setCurrentQuestionSubmitted(true);
                replaceCurrentExchangeQuestion(state, actor.getPlayerId(), action.targetPlayerId(), question.question());
                addEvent(state, events, TuringHuntEventType.TURING_QUESTION_ASKED, actor.getPlayerId(),
                        mapOf("questionerPlayerId", actor.getPlayerId(), "targetPlayerId", action.targetPlayerId(),
                                "question", question.question()),
                        mapOf("questionerSeatNo", actor.getSeatNo(),
                                "targetSeatNo", seatNo(state, action.targetPlayerId()), "question", question.question()));
            }
            case RESPOND -> {
                TuringHuntResponse response = new TuringHuntResponse(action.targetPlayerId(),
                        clean(action.response()), clean(action.reasonSummary()));
                state.getResponses().put(actor.getPlayerId(), response);
                state.setCurrentResponseSubmitted(true);
                replaceCurrentExchangeResponse(state, response.response());
                addEvent(state, events, TuringHuntEventType.TURING_RESPONSE_GIVEN, actor.getPlayerId(),
                        mapOf("responderPlayerId", actor.getPlayerId(), "targetPlayerId", action.targetPlayerId(),
                                "response", response.response(), "reasonSummary", response.reasonSummary()),
                        mapOf("responderSeatNo", actor.getSeatNo(),
                                "targetSeatNo", seatNo(state, action.targetPlayerId()), "response", response.response()));
            }
            case DEFEND -> {
                TuringHuntDefense defense = new TuringHuntDefense(clean(action.defense()),
                        action.counterSuspectPlayerId(), clean(action.reasonSummary()));
                state.getDefenses().put(actor.getPlayerId(), defense);
                state.getCurrentDefenseHistory().add(new TuringHuntDefenseRecord(actor.getPlayerId(),
                        state.getDefensePurpose(), defense));
                state.setDefenseIndex(state.getDefenseIndex() + 1);
                addEvent(state, events, TuringHuntEventType.TURING_DEFENSE_GIVEN, actor.getPlayerId(),
                        mapOf("playerId", actor.getPlayerId(), "defense", defense.defense(),
                                "counterSuspectPlayerId", defense.counterSuspectPlayerId(),
                                "reasonSummary", defense.reasonSummary(), "purpose", state.getDefensePurpose().name()),
                        mapOf("seatNo", actor.getSeatNo(), "defense", defense.defense(),
                                "counterSuspectSeatNo", seatNo(state, defense.counterSuspectPlayerId()),
                                "purpose", state.getDefensePurpose().name()));
            }
            case VOTE -> {
                TuringHuntVote vote = new TuringHuntVote(action.targetPlayerId(), clean(action.reasonSummary()));
                state.getVotesByPlayerId().put(actor.getPlayerId(), vote);
                addEvent(state, events, TuringHuntEventType.TURING_VOTE_SUBMITTED, actor.getPlayerId(),
                        mapOf("actorPlayerId", actor.getPlayerId(), "actorSeatNo", actor.getSeatNo(),
                                "targetPlayerId", vote.targetPlayerId(), "reasonSummary", vote.reasonSummary(),
                                "revote", state.isRevote()),
                        Map.of("submitted", true));
            }
            case ADVANCE -> throw new IllegalStateException("ADVANCE must not be handled as a player action");
        }
    }

    private void advance(TuringHuntGameState state, List<EngineEvent> events) {
        switch (state.getPhase()) {
            case WAITING -> transition(state, TuringHuntPhase.ROUND_PROMPT, events);
            case ROUND_PROMPT -> publishRoundPrompt(state, events);
            case ANSWER_COLLECTION -> revealAnswers(state, events);
            case ANSWER_REVEAL -> transition(state, TuringHuntPhase.SUSPICION_COLLECTION, events);
            case SUSPICION_COLLECTION -> revealControversy(state, events);
            case CONTROVERSY_REVEAL -> beginQuestioningOrDefense(state, events);
            case QUESTION -> transition(state, TuringHuntPhase.RESPONSE, events);
            case RESPONSE -> advanceAfterResponse(state, events);
            case DEFENSE -> beginVoting(state, events);
            case VOTE_COLLECTION -> revealVotes(state, events);
            case VOTE_REVEAL -> settleVoteReveal(state, events);
            case ROUND_RESULT -> beginNextRound(state, events);
            case FINISHED -> throw CustomException.create(ResultCodeEnum.GAME_STATUS_ERROR,
                    "图灵潜伏局已经结束");
        }
    }

    private void publishRoundPrompt(TuringHuntGameState state, List<EngineEvent> events) {
        resetCurrentRound(state);
        state.setCurrentQuestion(questionGenerator.generate(state.getRandomSeed(), state.getRoundNo()));
        if (state.getSequenceNo() == 0) {
            addEvent(state, events, TuringHuntEventType.TURING_GAME_STARTED, null,
                    mapOf("maxRounds", state.getMaxRounds(), "playerCount", state.getPlayers().size()),
                    mapOf("maxRounds", state.getMaxRounds(), "playerCount", state.getPlayers().size()));
        }
        addEvent(state, events, TuringHuntEventType.TURING_ROUND_STARTED, null,
                Map.of("roundNo", state.getRoundNo()), Map.of("roundNo", state.getRoundNo()));
        addEvent(state, events, TuringHuntEventType.TURING_PROMPT_PUBLISHED, null,
                mapOf("roundNo", state.getRoundNo(), "question", state.getCurrentQuestion()),
                mapOf("roundNo", state.getRoundNo(), "question", state.getCurrentQuestion()));
        transition(state, TuringHuntPhase.ANSWER_COLLECTION, events);
    }

    private void revealAnswers(TuringHuntGameState state, List<EngineEvent> events) {
        List<Map<String, Object>> publicAnswers = state.alivePlayers().stream().map(player -> {
            TuringHuntAnswer answer = state.getAnswersByPlayerId().get(player.getPlayerId());
            return mapOf("seatNo", player.getSeatNo(), "displayName", player.getDisplayName(),
                    "answer", answer == null ? "" : answer.answer());
        }).toList();
        addEvent(state, events, TuringHuntEventType.TURING_ANSWERS_REVEALED, null,
                mapOf("answersByPlayerId", new LinkedHashMap<>(state.getAnswersByPlayerId()),
                        "answers", publicAnswers),
                Map.of("answers", publicAnswers));
        transition(state, TuringHuntPhase.ANSWER_REVEAL, events);
    }

    private void revealControversy(TuringHuntGameState state, List<EngineEvent> events) {
        ControversyResult result = computeControversy(state);
        state.setControversyPlayerIds(new ArrayList<>(result.controversyPlayerIds()));
        state.setPublicSuspicionCounts(new LinkedHashMap<>(result.publicSuspicionCounts()));
        state.setQuestionQueue(new ArrayList<>(buildQuestionQueue(state, result.controversyPlayerIds())));
        List<Integer> controversySeats = seatNos(state, result.controversyPlayerIds());
        Map<Integer, Integer> publicCounts = toSeatCountMap(state, result.publicSuspicionCounts());
        addEvent(state, events, TuringHuntEventType.TURING_CONTROVERSY_REVEALED, null,
                mapOf("controversyPlayerIds", result.controversyPlayerIds(),
                        "suspicionCountsByPlayerId", result.publicSuspicionCounts(),
                        "questionQueue", state.getQuestionQueue()),
                mapOf("controversySeatNos", controversySeats, "suspicionCounts", publicCounts));
        transition(state, TuringHuntPhase.CONTROVERSY_REVEAL, events);
    }

    private void beginQuestioningOrDefense(TuringHuntGameState state, List<EngineEvent> events) {
        if (!state.getQuestionQueue().isEmpty()) {
            state.setQuestionIndex(0);
            loadQuestionPair(state);
            transition(state, TuringHuntPhase.QUESTION, events);
            return;
        }
        startDefense(state, state.getControversyPlayerIds(), TuringHuntDefensePurpose.CONTROVERSY);
        transition(state, state.getDefenseQueue().isEmpty()
                ? TuringHuntPhase.VOTE_COLLECTION : TuringHuntPhase.DEFENSE, events);
    }

    private void advanceAfterResponse(TuringHuntGameState state, List<EngineEvent> events) {
        if (state.getQuestionIndex() + 1 < state.getQuestionQueue().size()) {
            state.setQuestionIndex(state.getQuestionIndex() + 1);
            loadQuestionPair(state);
            transition(state, TuringHuntPhase.QUESTION, events);
            return;
        }
        startDefense(state, state.getControversyPlayerIds(), TuringHuntDefensePurpose.CONTROVERSY);
        transition(state, state.getDefenseQueue().isEmpty()
                ? TuringHuntPhase.VOTE_COLLECTION : TuringHuntPhase.DEFENSE, events);
    }

    private void beginVoting(TuringHuntGameState state, List<EngineEvent> events) {
        state.setVotesByPlayerId(new LinkedHashMap<>());
        state.setPendingVoteCounts(new LinkedHashMap<>());
        state.setPendingTopCandidates(new ArrayList<>());
        state.setPublicVoteCounts(new LinkedHashMap<>());
        transition(state, TuringHuntPhase.VOTE_COLLECTION, events);
    }

    private void revealVotes(TuringHuntGameState state, List<EngineEvent> events) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (TuringHuntVote vote : state.getVotesByPlayerId().values()) {
            counts.merge(vote.targetPlayerId(), 1, Integer::sum);
        }
        int maximum = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Long> top = counts.entrySet().stream().filter(entry -> entry.getValue() == maximum)
                .map(Map.Entry::getKey).sorted(Comparator.comparing(id -> seatNo(state, id))).toList();
        state.setPendingVoteCounts(new LinkedHashMap<>(counts));
        state.setPendingTopCandidates(new ArrayList<>(top));
        state.setPublicVoteCounts(new LinkedHashMap<>(counts));
        state.getCurrentVoteHistory().add(new LinkedHashMap<>(state.getVotesByPlayerId()));
        state.getCurrentVoteCountHistory().add(new LinkedHashMap<>(counts));

        List<Map<String, Object>> publicVotes = state.alivePlayers().stream().map(player -> {
            TuringHuntVote vote = state.getVotesByPlayerId().get(player.getPlayerId());
            return mapOf("voterSeatNo", player.getSeatNo(),
                    "targetSeatNo", vote == null ? null : seatNo(state, vote.targetPlayerId()),
                    "reasonSummary", vote == null ? "" : vote.reasonSummary());
        }).toList();
        addEvent(state, events, TuringHuntEventType.TURING_VOTES_REVEALED, null,
                mapOf("votesByPlayerId", new LinkedHashMap<>(state.getVotesByPlayerId()),
                        "voteCountsByPlayerId", counts, "revote", state.isRevote()),
                mapOf("votes", publicVotes, "voteCounts", toSeatCountMap(state, counts),
                        "revote", state.isRevote()));
        transition(state, TuringHuntPhase.VOTE_REVEAL, events);
    }

    private void settleVoteReveal(TuringHuntGameState state, List<EngineEvent> events) {
        List<Long> top = state.getPendingTopCandidates();
        if (top.size() > 1) {
            List<Integer> tieSeats = seatNos(state, top);
            addEvent(state, events, TuringHuntEventType.TURING_VOTE_TIED, null,
                    mapOf("candidatePlayerIds", top, "revote", state.isRevote()),
                    mapOf("candidateSeatNos", tieSeats, "revote", state.isRevote()));
            if (!state.isRevote()) {
                state.setRevoteCandidates(new ArrayList<>(top));
                state.setRevote(true);
                state.setVotesByPlayerId(new LinkedHashMap<>());
                startDefense(state, top, TuringHuntDefensePurpose.TIE_BREAK);
                transition(state, TuringHuntPhase.DEFENSE, events);
                return;
            }
            completeRound(state, null, false, events);
            if (state.getRoundNo() >= state.getMaxRounds()) {
                finish(state, TuringHuntWinner.HUMAN, TuringHuntFinishedReason.FINAL_ROUND_TIE, events);
            } else {
                transition(state, TuringHuntPhase.ROUND_RESULT, events);
            }
            return;
        }

        Long eliminatedId = top.isEmpty() ? null : top.get(0);
        boolean eliminatedHuman = false;
        if (eliminatedId != null) {
            TuringHuntPlayerState eliminated = state.findPlayer(eliminatedId);
            if (eliminated != null && eliminated.isAlive()) {
                eliminated.setAlive(false);
                eliminatedHuman = eliminated.isHuman();
                state.setPendingEliminationPlayerId(eliminatedId);
                state.getEliminatedPlayerIds().add(eliminatedId);
                if (!eliminatedHuman) state.setWrongAiEliminationCount(state.getWrongAiEliminationCount() + 1);
                Map<String, Object> publicIdentity = mapOf("seatNo", eliminated.getSeatNo(),
                        "displayName", eliminated.getDisplayName(),
                        "identity", eliminatedHuman ? "HUMAN" : "AI");
                addEvent(state, events, TuringHuntEventType.TURING_PLAYER_ELIMINATED, null,
                        mapOf("playerId", eliminatedId, "identity", publicIdentity.get("identity")), publicIdentity);
                addEvent(state, events, TuringHuntEventType.TURING_IDENTITY_REVEALED, null,
                        mapOf("playerId", eliminatedId, "identity", publicIdentity.get("identity")), publicIdentity);
            }
        }
        completeRound(state, eliminatedId, eliminatedHuman, events);

        TuringHuntFinishedReason reason = winnerReason(state, eliminatedHuman);
        if (reason != null) {
            finish(state, eliminatedHuman ? TuringHuntWinner.AI : TuringHuntWinner.HUMAN, reason, events);
        } else {
            transition(state, TuringHuntPhase.ROUND_RESULT, events);
        }
    }

    private TuringHuntFinishedReason winnerReason(TuringHuntGameState state, boolean humanJustEliminated) {
        if (humanJustEliminated || state.findPlayer(state.getHumanPlayerId()) == null
                || !state.findPlayer(state.getHumanPlayerId()).isAlive()) {
            return TuringHuntFinishedReason.HUMAN_ELIMINATED;
        }
        if (state.getWrongAiEliminationCount() >= 2) {
            return TuringHuntFinishedReason.TWO_AIS_WRONGLY_ELIMINATED;
        }
        if (state.alivePlayers().size() <= 2) {
            return TuringHuntFinishedReason.FINAL_TWO_SURVIVORS;
        }
        if (state.getRoundNo() >= state.getMaxRounds()) {
            return TuringHuntFinishedReason.HUMAN_SURVIVED_THREE_ROUNDS;
        }
        return null;
    }

    private void beginNextRound(TuringHuntGameState state, List<EngineEvent> events) {
        state.setRoundNo(state.getRoundNo() + 1);
        state.setCurrentQuestion(null);
        transition(state, TuringHuntPhase.ROUND_PROMPT, events);
    }

    private void completeRound(TuringHuntGameState state, Long eliminatedId, boolean eliminatedHuman,
                               List<EngineEvent> events) {
        List<Map<Long, TuringHuntVote>> voteRounds = state.getCurrentVoteHistory().stream()
                .map(LinkedHashMap::new).map(map -> (Map<Long, TuringHuntVote>) map).toList();
        List<Map<Long, Integer>> voteCounts = state.getCurrentVoteCountHistory().stream()
                .map(LinkedHashMap::new).map(map -> (Map<Long, Integer>) map).toList();
        TuringHuntRoundState round = TuringHuntRoundState.builder()
                .roundNo(state.getRoundNo()).question(state.getCurrentQuestion())
                .answersByPlayerId(new LinkedHashMap<>(state.getAnswersByPlayerId()))
                .suspicionsByPlayerId(new LinkedHashMap<>(state.getSuspicionsByPlayerId()))
                .controversyPlayerIds(new ArrayList<>(state.getControversyPlayerIds()))
                .exchanges(new ArrayList<>(state.getCurrentExchanges()))
                .defenses(new ArrayList<>(state.getCurrentDefenseHistory()))
                .voteRounds(new ArrayList<>(voteRounds)).voteCountRounds(new ArrayList<>(voteCounts))
                .tieCandidatePlayerIds(new ArrayList<>(state.getRevoteCandidates()))
                .eliminatedPlayerId(eliminatedId).eliminatedHuman(eliminatedHuman).build();
        state.getCompletedRounds().add(round);
        Map<Long, Long> lastVotes = new LinkedHashMap<>();
        if (!state.getCurrentVoteHistory().isEmpty()) {
            state.getCurrentVoteHistory().get(state.getCurrentVoteHistory().size() - 1)
                    .forEach((voter, vote) -> lastVotes.put(voter, vote.targetPlayerId()));
        }
        state.setPreviousVotes(lastVotes);
        state.setPreviousTieCandidates(new ArrayList<>(state.getRevoteCandidates()));
        addEvent(state, events, TuringHuntEventType.TURING_ROUND_FINISHED, null,
                mapOf("roundNo", state.getRoundNo(), "eliminatedPlayerId", eliminatedId,
                        "wrongAiEliminationCount", state.getWrongAiEliminationCount()),
                mapOf("roundNo", state.getRoundNo(), "eliminatedSeatNo", seatNo(state, eliminatedId),
                        "wrongAiEliminationCount", state.getWrongAiEliminationCount()));
    }

    private void finish(TuringHuntGameState state, TuringHuntWinner winner,
                        TuringHuntFinishedReason reason, List<EngineEvent> events) {
        state.setWinner(winner);
        state.setFinishedReason(reason);
        transition(state, TuringHuntPhase.FINISHED, events);
        Map<String, Object> recap = buildPublicRecap(state);
        addEvent(state, events, TuringHuntEventType.TURING_GAME_FINISHED, null,
                mapOf("winner", winner.name(), "finishedReason", reason.name(),
                        "humanPlayerId", state.getHumanPlayerId(), "recap", recap), recap);
    }

    private void resetCurrentRound(TuringHuntGameState state) {
        state.setAnswerSubmissionOrder(new ArrayList<>());
        state.setAnswersByPlayerId(new LinkedHashMap<>());
        state.setSuspicionsByPlayerId(new LinkedHashMap<>());
        state.setPrimarySuspectByPlayerId(new LinkedHashMap<>());
        state.setQuestionQueue(new ArrayList<>());
        state.setQuestionIndex(0);
        state.setCurrentQuestionerPlayerId(null);
        state.setCurrentQuestionTargetPlayerId(null);
        state.setQuestions(new LinkedHashMap<>());
        state.setResponses(new LinkedHashMap<>());
        state.setDefenses(new LinkedHashMap<>());
        state.setVotesByPlayerId(new LinkedHashMap<>());
        state.setRevoteCandidates(new ArrayList<>());
        state.setControversyPlayerIds(new ArrayList<>());
        state.setPublicSuspicionCounts(new LinkedHashMap<>());
        state.setPublicVoteCounts(new LinkedHashMap<>());
        state.setRevote(false);
        state.setDefenseQueue(new ArrayList<>());
        state.setDefenseIndex(0);
        state.setDefensePurpose(TuringHuntDefensePurpose.CONTROVERSY);
        state.setCurrentExchanges(new ArrayList<>());
        state.setCurrentDefenseHistory(new ArrayList<>());
        state.setCurrentVoteHistory(new ArrayList<>());
        state.setCurrentVoteCountHistory(new ArrayList<>());
        state.setPendingVoteCounts(new LinkedHashMap<>());
        state.setPendingTopCandidates(new ArrayList<>());
        state.setPendingEliminationPlayerId(null);
        state.setCurrentQuestionSubmitted(false);
        state.setCurrentResponseSubmitted(false);
    }

    private void loadQuestionPair(TuringHuntGameState state) {
        TuringHuntQuestionPair pair = state.getQuestionQueue().get(state.getQuestionIndex());
        state.setCurrentQuestionerPlayerId(pair.questionerPlayerId());
        state.setCurrentQuestionTargetPlayerId(pair.targetPlayerId());
        state.setCurrentQuestionSubmitted(false);
        state.setCurrentResponseSubmitted(false);
        state.getCurrentExchanges().add(new TuringHuntQuestionExchange(pair.questionerPlayerId(),
                pair.targetPlayerId(), null, null));
    }

    private void replaceCurrentExchangeQuestion(TuringHuntGameState state, Long questionerId,
                                                Long targetId, String question) {
        int index = state.getCurrentExchanges().size() - 1;
        state.getCurrentExchanges().set(index,
                new TuringHuntQuestionExchange(questionerId, targetId, question, null));
    }

    private void replaceCurrentExchangeResponse(TuringHuntGameState state, String response) {
        int index = state.getCurrentExchanges().size() - 1;
        TuringHuntQuestionExchange current = state.getCurrentExchanges().get(index);
        state.getCurrentExchanges().set(index, new TuringHuntQuestionExchange(current.questionerPlayerId(),
                current.targetPlayerId(), current.question(), response));
    }

    private void startDefense(TuringHuntGameState state, List<Long> players,
                              TuringHuntDefensePurpose purpose) {
        List<Long> alive = players.stream().distinct()
                .filter(id -> state.findPlayer(id) != null && state.findPlayer(id).isAlive())
                .sorted(Comparator.comparing(id -> seatNo(state, id))).toList();
        state.setDefenseQueue(new ArrayList<>(alive));
        state.setDefenseIndex(0);
        state.setDefensePurpose(purpose);
    }

    private ControversyResult computeControversy(TuringHuntGameState state) {
        Map<Long, Integer> score = new LinkedHashMap<>();
        Map<Long, Integer> primaryCount = new LinkedHashMap<>();
        Map<Long, Integer> ordinaryCount = new LinkedHashMap<>();
        Map<Long, Set<Long>> publicSuspectors = new LinkedHashMap<>();
        state.alivePlayers().forEach(player -> {
            score.put(player.getPlayerId(), 0);
            primaryCount.put(player.getPlayerId(), 0);
            ordinaryCount.put(player.getPlayerId(), 0);
            publicSuspectors.put(player.getPlayerId(), new LinkedHashSet<>());
        });
        state.getSuspicionsByPlayerId().forEach((actorId, suspicion) -> {
            Long primary = suspicion.primarySuspectPlayerId();
            if (score.containsKey(primary)) {
                score.merge(primary, 3, Integer::sum);
                primaryCount.merge(primary, 1, Integer::sum);
                publicSuspectors.get(primary).add(actorId);
            }
            for (Long suspect : suspicion.suspectPlayerIds()) {
                if (!score.containsKey(suspect)) continue;
                score.merge(suspect, 1, Integer::sum);
                ordinaryCount.merge(suspect, 1, Integer::sum);
                publicSuspectors.get(suspect).add(actorId);
            }
        });
        state.getPreviousVotes().values().forEach(target -> {
            if (score.containsKey(target)) score.merge(target, 1, Integer::sum);
        });
        state.getPreviousTieCandidates().forEach(target -> {
            if (score.containsKey(target)) score.merge(target, 1, Integer::sum);
        });
        Comparator<Long> order = Comparator.<Long, Integer>comparing(score::get).reversed()
                .thenComparing(Comparator.<Long, Integer>comparing(primaryCount::get).reversed())
                .thenComparing(Comparator.<Long, Integer>comparing(ordinaryCount::get).reversed())
                .thenComparing(id -> seatNo(state, id));
        List<Long> controversy = score.keySet().stream().filter(id -> score.get(id) > 0)
                .sorted(order).limit(2).toList();
        Map<Long, Integer> publicCounts = new LinkedHashMap<>();
        publicSuspectors.forEach((id, actors) -> publicCounts.put(id, actors.size()));
        return new ControversyResult(controversy, publicCounts);
    }

    private List<TuringHuntQuestionPair> buildQuestionQueue(TuringHuntGameState state,
                                                            List<Long> controversyIds) {
        List<TuringHuntQuestionPair> result = new ArrayList<>();
        Long lastQuestioner = state.getLastQuestionerPlayerId();
        for (Long targetId : controversyIds) {
            List<Long> primary = candidateQuestioners(state, targetId, true);
            List<Long> ordinary = candidateQuestioners(state, targetId, false);
            List<Long> candidates = new ArrayList<>(primary);
            ordinary.stream().filter(id -> !candidates.contains(id)).forEach(candidates::add);
            Long prior = lastQuestioner;
            candidates.sort(Comparator.comparing((Long id) -> Objects.equals(id, prior))
                    .thenComparing(id -> seatNo(state, id)));
            if (candidates.isEmpty()) continue;
            Long questioner = candidates.get(0);
            result.add(new TuringHuntQuestionPair(questioner, targetId));
            lastQuestioner = questioner;
        }
        state.setLastQuestionerPlayerId(lastQuestioner);
        return result;
    }

    private List<Long> candidateQuestioners(TuringHuntGameState state, Long targetId, boolean primary) {
        return state.getSuspicionsByPlayerId().entrySet().stream()
                .filter(entry -> {
                    TuringHuntPlayerState player = state.findPlayer(entry.getKey());
                    if (player == null || !player.isAlive() || Objects.equals(entry.getKey(), targetId)) return false;
                    return primary ? Objects.equals(entry.getValue().primarySuspectPlayerId(), targetId)
                            : entry.getValue().suspectPlayerIds().contains(targetId);
                })
                .map(Map.Entry::getKey).sorted(Comparator.comparing(id -> seatNo(state, id))).toList();
    }

    private ActionValidation validateSuspicion(TuringHuntGameState state, TuringHuntPlayerState actor,
                                               TuringHuntAction action) {
        if (!legalOtherAlive(state, actor, action.primarySuspectPlayerId())) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "首要怀疑对象必须是其他存活玩家");
        }
        if (action.suspectPlayerIds().size() > MAX_SUSPECTS
                || new LinkedHashSet<>(action.suspectPlayerIds()).size() != action.suspectPlayerIds().size()) {
            return invalid(ResultCodeEnum.PARAMS_ERROR, "怀疑对象最多两名且不能重复");
        }
        if (action.suspectPlayerIds().stream().anyMatch(id -> !legalOtherAlive(state, actor, id))) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "怀疑对象必须是其他存活玩家");
        }
        if (action.reasonTags().size() > MAX_REASON_TAGS) {
            return invalid(ResultCodeEnum.PARAMS_ERROR, "怀疑理由标签最多三个");
        }
        return validatePublicText(TuringHuntActionType.SUSPECT, action.reasonSummary());
    }

    private ActionValidation validateQuestion(TuringHuntGameState state, TuringHuntPlayerState actor,
                                              TuringHuntAction action) {
        if (!Objects.equals(action.targetPlayerId(), state.getCurrentQuestionTargetPlayerId())) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "只能质询规则安排的争议玩家");
        }
        return validatePublicText(TuringHuntActionType.QUESTION, action.question());
    }

    private ActionValidation validateResponse(TuringHuntGameState state, TuringHuntPlayerState actor,
                                              TuringHuntAction action) {
        if (!Objects.equals(action.targetPlayerId(), state.getCurrentQuestionerPlayerId())) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "回应对象必须是当前质询者");
        }
        return validatePublicText(TuringHuntActionType.RESPOND, action.response());
    }

    private ActionValidation validateDefense(TuringHuntGameState state, TuringHuntPlayerState actor,
                                             TuringHuntAction action) {
        if (action.counterSuspectPlayerId() != null
                && !legalOtherAlive(state, actor, action.counterSuspectPlayerId())) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "反向怀疑对象必须是其他存活玩家");
        }
        return validatePublicText(TuringHuntActionType.DEFEND, action.defense());
    }

    private ActionValidation validateVote(TuringHuntGameState state, TuringHuntPlayerState actor,
                                          TuringHuntAction action) {
        if (!legalOtherAlive(state, actor, action.targetPlayerId())) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "投票对象必须是其他存活玩家");
        }
        if (state.isRevote() && !state.getRevoteCandidates().contains(action.targetPlayerId())) {
            return invalid(ResultCodeEnum.ACTION_TARGET_INVALID, "重投只能选择平票候选人");
        }
        return validatePublicText(TuringHuntActionType.VOTE, action.reasonSummary());
    }

    private ActionValidation validatePublicText(TuringHuntActionType type, String value) {
        TuringTextPolicy.TextValidation validation = textPolicy.validate(type, value);
        return validation.valid() ? ActionValidation.success()
                : invalid(ResultCodeEnum.PARAMS_ERROR, validation.message());
    }

    private boolean legalOtherAlive(TuringHuntGameState state, TuringHuntPlayerState actor, Long targetId) {
        TuringHuntPlayerState target = state.findPlayer(targetId);
        return target != null && target.isAlive() && !Objects.equals(actor.getPlayerId(), targetId);
    }

    private <T> TuringHuntPlayerState nextMissing(TuringHuntGameState state, Map<Long, T> submissions) {
        return state.alivePlayers().stream()
                .filter(player -> !submissions.containsKey(player.getPlayerId())).findFirst().orElse(null);
    }

    private Long playerId(TuringHuntPlayerState player) {
        return player == null ? null : player.getPlayerId();
    }

    private void refreshCurrentActor(TuringHuntGameState state) {
        if (state.getPhase() == TuringHuntPhase.FINISHED) {
            state.setCurrentActorPlayerId(null);
            return;
        }
        state.setCurrentActorPlayerId(requiredActorPlayerId(state));
    }

    private void transition(TuringHuntGameState state, TuringHuntPhase next, List<EngineEvent> events) {
        TuringHuntPhase previous = state.getPhase();
        state.setPhase(next);
        addEvent(state, events, TuringHuntEventType.TURING_PHASE_CHANGED, null,
                mapOf("from", previous.name(), "phase", next.name()),
                mapOf("from", previous.name(), "phase", next.name()));
    }

    private void addEvent(TuringHuntGameState state, List<EngineEvent> events, TuringHuntEventType type,
                          Long actorPlayerId, Map<String, Object> payload, Map<String, Object> publicPayload) {
        state.setSequenceNo(state.getSequenceNo() + 1);
        events.add(new EngineEvent(type.name(), actorPlayerId,
                payload == null ? Map.of() : payload, publicPayload == null ? Map.of() : publicPayload));
    }

    private Map<String, Object> buildPublicRecap(TuringHuntGameState state) {
        List<Map<String, Object>> rounds = state.getCompletedRounds().stream()
                .map(round -> publicRound(state, round)).toList();
        Map<String, Object> recap = new LinkedHashMap<>();
        recap.put("winner", state.getWinner().name());
        recap.put("finishedReason", state.getFinishedReason().name());
        recap.put("humanSeatNo", seatNo(state, state.getHumanPlayerId()));
        recap.put("humanSurvivedRounds", (int) state.getCompletedRounds().stream()
                .filter(round -> !round.isEliminatedHuman()).count());
        recap.put("wrongAiEliminationCount", state.getWrongAiEliminationCount());
        recap.put("eliminationOrder", seatNos(state, state.getEliminatedPlayerIds()));
        recap.put("roundSummaries", rounds);
        recap.put("firstCorrectSuspectSeatNo", firstCorrectSuspectSeat(state));
        recap.put("mostAccurateAiSeatNo", scoredAiSeat(state, true));
        recap.put("mostMisledAiSeatNo", scoredAiSeat(state, false));
        return recap;
    }

    private Map<String, Object> publicRound(TuringHuntGameState state, TuringHuntRoundState round) {
        List<Map<String, Object>> answers = round.getAnswersByPlayerId().entrySet().stream()
                .sorted(Comparator.comparing(entry -> seatNo(state, entry.getKey())))
                .map(entry -> mapOf("seatNo", seatNo(state, entry.getKey()),
                        "answer", entry.getValue().answer()))
                .toList();
        List<Map<String, Object>> suspicions = round.getSuspicionsByPlayerId().entrySet().stream()
                .sorted(Comparator.comparing(entry -> seatNo(state, entry.getKey())))
                .map(entry -> mapOf("actorSeatNo", seatNo(state, entry.getKey()),
                        "primarySuspectSeatNo", seatNo(state, entry.getValue().primarySuspectPlayerId()),
                        "suspectSeatNos", seatNos(state, entry.getValue().suspectPlayerIds()),
                        "reasonTags", entry.getValue().reasonTags(),
                        "reasonSummary", entry.getValue().reasonSummary())).toList();
        List<Map<String, Object>> exchanges = round.getExchanges().stream().map(exchange ->
                mapOf("questionerSeatNo", seatNo(state, exchange.questionerPlayerId()),
                        "targetSeatNo", seatNo(state, exchange.targetPlayerId()),
                        "question", exchange.question(), "response", exchange.response())).toList();
        List<Map<String, Object>> defenses = round.getDefenses().stream().map(record ->
                mapOf("seatNo", seatNo(state, record.playerId()), "purpose", record.purpose().name(),
                        "defense", record.defense().defense(),
                        "counterSuspectSeatNo", seatNo(state, record.defense().counterSuspectPlayerId()),
                        "reasonSummary", record.defense().reasonSummary())).toList();
        List<List<Map<String, Object>>> voteRounds = round.getVoteRounds().stream().map(votes ->
                votes.entrySet().stream().sorted(Comparator.comparing(entry -> seatNo(state, entry.getKey())))
                        .map(entry -> mapOf("voterSeatNo", seatNo(state, entry.getKey()),
                                "targetSeatNo", seatNo(state, entry.getValue().targetPlayerId()),
                                "reasonSummary", entry.getValue().reasonSummary())).toList()).toList();
        List<Map<Integer, Integer>> countRounds = round.getVoteCountRounds().stream()
                .map(counts -> toSeatCountMap(state, counts)).toList();
        return mapOf("roundNo", round.getRoundNo(), "question", round.getQuestion(),
                "answers", answers, "suspicions", suspicions,
                "controversySeatNos", seatNos(state, round.getControversyPlayerIds()),
                "exchanges", exchanges, "defenses", defenses, "voteRounds", voteRounds,
                "voteCountRounds", countRounds, "tieCandidateSeatNos",
                seatNos(state, round.getTieCandidatePlayerIds()),
                "eliminatedSeatNo", seatNo(state, round.getEliminatedPlayerId()),
                "eliminatedIdentity", round.getEliminatedPlayerId() == null ? null
                        : round.isEliminatedHuman() ? "HUMAN" : "AI");
    }

    private Integer firstCorrectSuspectSeat(TuringHuntGameState state) {
        for (TuringHuntRoundState round : state.getCompletedRounds()) {
            Integer seat = round.getSuspicionsByPlayerId().entrySet().stream()
                    .filter(entry -> !Objects.equals(entry.getKey(), state.getHumanPlayerId()))
                    .filter(entry -> Objects.equals(entry.getValue().primarySuspectPlayerId(), state.getHumanPlayerId()))
                    .map(entry -> seatNo(state, entry.getKey())).min(Integer::compareTo).orElse(null);
            if (seat != null) return seat;
        }
        return null;
    }

    private Integer scoredAiSeat(TuringHuntGameState state, boolean accurate) {
        Map<Long, Integer> scores = state.getPlayers().stream().filter(player -> !player.isHuman())
                .collect(Collectors.toMap(TuringHuntPlayerState::getPlayerId, ignored -> 0,
                        (left, right) -> left, LinkedHashMap::new));
        for (TuringHuntRoundState round : state.getCompletedRounds()) {
            round.getSuspicionsByPlayerId().forEach((actor, suspicion) -> {
                if (!scores.containsKey(actor)) return;
                boolean primaryHit = Objects.equals(suspicion.primarySuspectPlayerId(), state.getHumanPlayerId());
                boolean included = suspicion.suspectPlayerIds().contains(state.getHumanPlayerId());
                if (accurate) {
                    if (primaryHit) scores.merge(actor, 3, Integer::sum);
                    if (included) scores.merge(actor, 1, Integer::sum);
                } else {
                    if (!primaryHit) scores.merge(actor, 3, Integer::sum);
                    long wrong = suspicion.suspectPlayerIds().stream()
                            .filter(id -> !Objects.equals(id, state.getHumanPlayerId())).count();
                    scores.merge(actor, (int) wrong, Integer::sum);
                }
            });
            round.getVoteRounds().forEach(votes -> votes.forEach((actor, vote) -> {
                if (!scores.containsKey(actor)) return;
                boolean hit = Objects.equals(vote.targetPlayerId(), state.getHumanPlayerId());
                if (accurate) scores.merge(actor, hit ? 3 : -2, Integer::sum);
                else if (!hit) scores.merge(actor, 3, Integer::sum);
            }));
        }
        return scores.entrySet().stream().sorted(Map.Entry.<Long, Integer>comparingByValue().reversed()
                        .thenComparing(entry -> seatNo(state, entry.getKey())))
                .map(entry -> seatNo(state, entry.getKey())).findFirst().orElse(null);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) return List.of();
        return tags.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).map(value -> value.toUpperCase()
                        .replace('-', '_').replace(' ', '_').replaceAll("[^A-Z0-9_]", ""))
                .filter(value -> !value.isBlank()).distinct().limit(MAX_REASON_TAGS).toList();
    }

    private String clean(String value) {
        return textPolicy.normalize(value);
    }

    private Integer seatNo(TuringHuntGameState state, Long playerId) {
        TuringHuntPlayerState player = state.findPlayer(playerId);
        return player == null ? null : player.getSeatNo();
    }

    private List<Integer> seatNos(TuringHuntGameState state, List<Long> playerIds) {
        if (playerIds == null) return List.of();
        return playerIds.stream().map(id -> seatNo(state, id)).filter(Objects::nonNull).toList();
    }

    private Map<Integer, Integer> toSeatCountMap(TuringHuntGameState state, Map<Long, Integer> counts) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        counts.entrySet().stream().sorted(Comparator.comparing(entry -> seatNo(state, entry.getKey())))
                .forEach(entry -> {
                    Integer seat = seatNo(state, entry.getKey());
                    if (seat != null && entry.getValue() != null && entry.getValue() > 0) {
                        result.put(seat, entry.getValue());
                    }
                });
        return result;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    private void ensureCollections(TuringHuntGameState state) {
        if (state.getPlayers() == null) state.setPlayers(new ArrayList<>());
        if (state.getAnswerSubmissionOrder() == null) state.setAnswerSubmissionOrder(new ArrayList<>());
        if (state.getAnswersByPlayerId() == null) state.setAnswersByPlayerId(new LinkedHashMap<>());
        if (state.getSuspicionsByPlayerId() == null) state.setSuspicionsByPlayerId(new LinkedHashMap<>());
        if (state.getPrimarySuspectByPlayerId() == null) state.setPrimarySuspectByPlayerId(new LinkedHashMap<>());
        if (state.getQuestionQueue() == null) state.setQuestionQueue(new ArrayList<>());
        if (state.getQuestions() == null) state.setQuestions(new LinkedHashMap<>());
        if (state.getResponses() == null) state.setResponses(new LinkedHashMap<>());
        if (state.getDefenses() == null) state.setDefenses(new LinkedHashMap<>());
        if (state.getVotesByPlayerId() == null) state.setVotesByPlayerId(new LinkedHashMap<>());
        if (state.getRevoteCandidates() == null) state.setRevoteCandidates(new ArrayList<>());
        if (state.getEliminatedPlayerIds() == null) state.setEliminatedPlayerIds(new ArrayList<>());
        if (state.getControversyPlayerIds() == null) state.setControversyPlayerIds(new ArrayList<>());
        if (state.getPublicSuspicionCounts() == null) state.setPublicSuspicionCounts(new LinkedHashMap<>());
        if (state.getPublicVoteCounts() == null) state.setPublicVoteCounts(new LinkedHashMap<>());
        if (state.getDefenseQueue() == null) state.setDefenseQueue(new ArrayList<>());
        if (state.getDefensePurpose() == null) state.setDefensePurpose(TuringHuntDefensePurpose.CONTROVERSY);
        if (state.getPreviousVotes() == null) state.setPreviousVotes(new LinkedHashMap<>());
        if (state.getPreviousTieCandidates() == null) state.setPreviousTieCandidates(new ArrayList<>());
        if (state.getCompletedRounds() == null) state.setCompletedRounds(new ArrayList<>());
        if (state.getCurrentExchanges() == null) state.setCurrentExchanges(new ArrayList<>());
        if (state.getCurrentDefenseHistory() == null) state.setCurrentDefenseHistory(new ArrayList<>());
        if (state.getCurrentVoteHistory() == null) state.setCurrentVoteHistory(new ArrayList<>());
        if (state.getCurrentVoteCountHistory() == null) state.setCurrentVoteCountHistory(new ArrayList<>());
        if (state.getPendingVoteCounts() == null) state.setPendingVoteCounts(new LinkedHashMap<>());
        if (state.getPendingTopCandidates() == null) state.setPendingTopCandidates(new ArrayList<>());
    }

    private ActionValidation invalid(ResultCodeEnum code, String message) {
        return ActionValidation.fail(code, message);
    }

    private record ControversyResult(List<Long> controversyPlayerIds,
                                     Map<Long, Integer> publicSuspicionCounts) {
    }
}
