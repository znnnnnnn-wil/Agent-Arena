package com.zn.agentarena.modules.turing.engine;

import com.zn.agentarena.modules.game.engine.EngineResult;
import com.zn.agentarena.modules.turing.domain.*;
import com.zn.agentarena.modules.turing.prompt.TuringQuestionGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TuringHuntGameEngineTest {

    private final TuringHuntGameEngine engine = new TuringHuntGameEngine();

    @Test
    void questionGeneratorIsDeterministic() {
        TuringQuestionGenerator generator = new TuringQuestionGenerator();
        assertThat(generator.generate(7788L, 1)).isEqualTo(generator.generate(7788L, 1));
        assertThat(generator.generate(7788L, 2)).contains("上一轮");
        assertThat(generator.generate(7788L, 3)).contains("变化");
    }

    @Test
    void startPublishesFirstPromptAndEntersAnswerCollection() {
        TuringHuntGameState state = newState();
        EngineResult<TuringHuntGameState> result = engine.execute(state, TuringHuntAction.advance());

        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.ANSWER_COLLECTION);
        assertThat(state.getCurrentQuestion()).isNotBlank();
        assertThat(state.getCurrentActorPlayerId()).isEqualTo(101L);
        assertThat(result.events()).extracting(event -> event.type()).contains(
                "TURING_GAME_STARTED", "TURING_ROUND_STARTED", "TURING_PROMPT_PUBLISHED");
    }

    @Test
    void answerSubmissionKeepsAnswerOutOfPublicPayload() {
        TuringHuntGameState state = startedState();
        var result = engine.execute(state, answer(101L, "我会删除镜子，因为它的功能最容易替代。"));

        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).publicPayload()).containsExactlyEntriesOf(Map.of("submitted", true));
        assertThat(result.events().get(0).payload()).containsKey("answer");
        assertThat(state.getAnswersByPlayerId()).containsKey(101L);
    }

    @Test
    void answersCannotBeRevealedBeforeEveryAlivePlayerSubmits() {
        TuringHuntGameState state = startedState();
        engine.execute(state, answer(101L, "选择镜子，因为存在替代方式。"));

        assertThat(engine.validate(state, TuringHuntAction.advance()).valid()).isFalse();
        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.ANSWER_COLLECTION);
    }

    @Test
    void answersAreRevealedTogetherAfterLastSubmission() {
        TuringHuntGameState state = startedState();
        submitAllAnswers(state);

        var result = engine.execute(state, TuringHuntAction.advance());
        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.ANSWER_REVEAL);
        assertThat(result.events()).anyMatch(event -> "TURING_ANSWERS_REVEALED".equals(event.type())
                && ((List<?>) event.publicPayload().get("answers")).size() == 6);
        Map<?, ?> firstAnswer = (Map<?, ?>) ((List<?>) result.events().stream()
                .filter(event -> "TURING_ANSWERS_REVEALED".equals(event.type())).findFirst().orElseThrow()
                .publicPayload().get("answers")).get(0);
        assertThat(firstAnswer.containsKey("seatNo")).isTrue();
        assertThat(firstAnswer.containsKey("displayName")).isTrue();
        assertThat(firstAnswer.containsKey("answer")).isTrue();
        assertThat(firstAnswer.containsKey("reasonSummary")).isFalse();
        assertThat(firstAnswer.containsKey("actorPlayerId")).isFalse();
        assertThat(firstAnswer.containsKey("modelName")).isFalse();
        assertThat(firstAnswer.containsKey("elapsedMs")).isFalse();
    }

    @Test
    void suspicionSubmissionIsSecret() {
        TuringHuntGameState state = suspicionCollectionState();
        var result = engine.execute(state, suspect(101L, 104L, List.of(104L, 105L)));

        assertThat(result.events().get(0).publicPayload()).containsExactlyEntriesOf(Map.of("submitted", true));
        assertThat(result.events().get(0).payload()).containsKey("primarySuspectPlayerId");
    }

    @Test
    void controversyUsesPrimaryOrdinaryAndSeatTieBreaks() {
        TuringHuntGameState state = suspicionCollectionState();
        state.setSuspicionsByPlayerId(new LinkedHashMap<>(Map.of(
                101L, suspicion(104L, 104L, 105L),
                102L, suspicion(104L, 104L),
                103L, suspicion(105L, 105L),
                104L, suspicion(105L, 105L),
                105L, suspicion(104L, 104L),
                106L, suspicion(104L, 104L))));
        state.getSuspicionsByPlayerId().forEach((actor, value) ->
                state.getPrimarySuspectByPlayerId().put(actor, value.primarySuspectPlayerId()));

        engine.execute(state, TuringHuntAction.advance());

        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.CONTROVERSY_REVEAL);
        assertThat(seats(state, state.getControversyPlayerIds())).containsExactly(4, 5);
        assertThat(state.getPublicSuspicionCounts().get(104L)).isEqualTo(4);
    }

    @Test
    void questionerPrefersPrimarySuspector() {
        TuringHuntGameState state = suspicionCollectionState();
        state.setSuspicionsByPlayerId(new LinkedHashMap<>(Map.of(
                101L, suspicion(104L, 104L),
                102L, suspicion(105L, 104L),
                103L, suspicion(104L, 104L),
                104L, suspicion(105L, 105L),
                105L, suspicion(104L, 104L),
                106L, suspicion(104L, 104L))));
        engine.execute(state, TuringHuntAction.advance());

        TuringHuntQuestionPair pairForFour = state.getQuestionQueue().stream()
                .filter(pair -> pair.targetPlayerId().equals(104L)).findFirst().orElseThrow();
        assertThat(pairForFour.questionerPlayerId()).isEqualTo(101L);
    }

    @Test
    void questioningMovesToResponseOnlyOnASeparateAdvance() {
        TuringHuntGameState state = questionState();
        Long questioner = state.getCurrentQuestionerPlayerId();
        Long target = state.getCurrentQuestionTargetPlayerId();

        engine.execute(state, question(questioner, target));
        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.QUESTION);
        assertThat(engine.requiredActionType(state)).isEqualTo(TuringHuntActionType.ADVANCE);

        engine.execute(state, TuringHuntAction.advance());
        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.RESPONSE);
        assertThat(state.getCurrentActorPlayerId()).isEqualTo(target);
    }

    @Test
    void responseMovesToNextQuestionOrDefenseOnSeparateAdvance() {
        TuringHuntGameState state = questionState();
        Long questioner = state.getCurrentQuestionerPlayerId();
        Long target = state.getCurrentQuestionTargetPlayerId();
        engine.execute(state, question(questioner, target));
        engine.execute(state, TuringHuntAction.advance());
        engine.execute(state, response(target, questioner));

        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.RESPONSE);
        engine.execute(state, TuringHuntAction.advance());
        assertThat(state.getPhase()).isIn(TuringHuntPhase.QUESTION, TuringHuntPhase.DEFENSE);
    }

    @Test
    void voteSubmissionDoesNotExposeTarget() {
        TuringHuntGameState state = voteCollectionState();
        var result = engine.execute(state, vote(101L, 102L));

        assertThat(result.events().get(0).publicPayload()).containsExactlyEntriesOf(Map.of("submitted", true));
        assertThat(result.events().get(0).payload()).containsEntry("targetPlayerId", 102L);
    }

    @Test
    void allVotesAreRevealedTogetherWithCounts() {
        TuringHuntGameState state = voteCollectionState();
        submitVotes(state, Map.of(101L, 102L, 102L, 101L, 103L, 102L,
                104L, 102L, 105L, 102L, 106L, 102L));

        var result = engine.execute(state, TuringHuntAction.advance());
        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.VOTE_REVEAL);
        assertThat(result.events()).anyMatch(event -> "TURING_VOTES_REVEALED".equals(event.type())
                && ((Map<?, ?>) event.publicPayload().get("voteCounts")).get(2).equals(5));
    }

    @Test
    void firstTieStartsCandidateDefenseAndRevote() {
        TuringHuntGameState state = voteRevealState(false, List.of(101L, 102L));
        engine.execute(state, TuringHuntAction.advance());

        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.DEFENSE);
        assertThat(state.isRevote()).isTrue();
        assertThat(state.getRevoteCandidates()).containsExactly(101L, 102L);
        assertThat(state.getDefensePurpose()).isEqualTo(TuringHuntDefensePurpose.TIE_BREAK);
    }

    @Test
    void secondTieEliminatesNobodyAndFinishesRound() {
        TuringHuntGameState state = voteRevealState(true, List.of(101L, 102L));
        engine.execute(state, TuringHuntAction.advance());

        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.ROUND_RESULT);
        assertThat(state.getEliminatedPlayerIds()).isEmpty();
        assertThat(state.getCompletedRounds()).hasSize(1);
    }

    @Test
    void eliminatingHumanImmediatelyGivesAiVictory() {
        TuringHuntGameState state = voteRevealState(false, List.of(103L));
        engine.execute(state, TuringHuntAction.advance());

        assertThat(state.getPhase()).isEqualTo(TuringHuntPhase.FINISHED);
        assertThat(state.getWinner()).isEqualTo(TuringHuntWinner.AI);
        assertThat(state.getFinishedReason()).isEqualTo(TuringHuntFinishedReason.HUMAN_ELIMINATED);
    }

    @Test
    void secondWrongAiEliminationGivesHumanVictory() {
        TuringHuntGameState state = voteRevealState(false, List.of(101L));
        state.setWrongAiEliminationCount(1);
        engine.execute(state, TuringHuntAction.advance());

        assertThat(state.getWinner()).isEqualTo(TuringHuntWinner.HUMAN);
        assertThat(state.getFinishedReason()).isEqualTo(TuringHuntFinishedReason.TWO_AIS_WRONGLY_ELIMINATED);
    }

    @Test
    void survivingThirdRoundGivesHumanVictory() {
        TuringHuntGameState state = voteRevealState(false, List.of(101L));
        state.setRoundNo(3);
        engine.execute(state, TuringHuntAction.advance());

        assertThat(state.getWinner()).isEqualTo(TuringHuntWinner.HUMAN);
        assertThat(state.getFinishedReason()).isEqualTo(TuringHuntFinishedReason.HUMAN_SURVIVED_THREE_ROUNDS);
    }

    @Test
    void reachingHumanAndOneAiGivesHumanVictory() {
        TuringHuntGameState state = voteRevealState(false, List.of(101L));
        state.getPlayers().forEach(player -> player.setAlive(
                player.getPlayerId().equals(101L) || player.getPlayerId().equals(102L)
                        || player.getPlayerId().equals(103L)));
        engine.execute(state, TuringHuntAction.advance());

        assertThat(state.getWinner()).isEqualTo(TuringHuntWinner.HUMAN);
        assertThat(state.getFinishedReason()).isEqualTo(TuringHuntFinishedReason.FINAL_TWO_SURVIVORS);
    }

    @Test
    void finishedGameCannotAdvance() {
        TuringHuntGameState state = voteRevealState(false, List.of(103L));
        engine.execute(state, TuringHuntAction.advance());

        assertThatThrownBy(() -> engine.execute(state, TuringHuntAction.advance()))
                .hasMessageContaining("结束");
    }

    @Test
    void revoteRejectsTargetsOutsideTieCandidates() {
        TuringHuntGameState state = voteCollectionState();
        state.setRevote(true);
        state.setRevoteCandidates(new ArrayList<>(List.of(101L, 102L)));

        assertThat(engine.validate(state, vote(101L, 104L)).valid()).isFalse();
    }

    @Test
    void selfSuspicionAndSelfVoteAreRejected() {
        TuringHuntGameState suspicion = suspicionCollectionState();
        assertThat(engine.validate(suspicion, suspect(101L, 101L, List.of(101L))).valid()).isFalse();
        TuringHuntGameState vote = voteCollectionState();
        assertThat(engine.validate(vote, vote(101L, 101L)).valid()).isFalse();
    }

    @Test
    void overlongAnswerIsRejectedWithoutMutatingState() {
        TuringHuntGameState state = startedState();

        assertThat(engine.validate(state, answer(101L, "答".repeat(91))).valid()).isFalse();
        assertThatThrownBy(() -> engine.execute(state, answer(101L, "答".repeat(91))))
                .hasMessageContaining("90");
        assertThat(state.getAnswersByPlayerId()).isEmpty();
    }

    private TuringHuntGameState newState() {
        List<TuringHuntPlayerState> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(TuringHuntPlayerState.builder().playerId(100L + seat).seatNo(seat)
                    .displayName(seat + "号").human(seat == 3).alive(true).build());
        }
        return TuringHuntGameState.builder().gameId(1L).roundNo(1).maxRounds(3)
                .phase(TuringHuntPhase.ROUND_PROMPT).humanPlayerId(103L)
                .randomSeed(7788L).players(players).build();
    }

    private TuringHuntGameState startedState() {
        TuringHuntGameState state = newState();
        engine.execute(state, TuringHuntAction.advance());
        return state;
    }

    private TuringHuntGameState suspicionCollectionState() {
        TuringHuntGameState state = startedState();
        submitAllAnswers(state);
        engine.execute(state, TuringHuntAction.advance());
        engine.execute(state, TuringHuntAction.advance());
        return state;
    }

    private TuringHuntGameState questionState() {
        TuringHuntGameState state = suspicionCollectionState();
        List<Long> ids = state.alivePlayers().stream().map(TuringHuntPlayerState::getPlayerId).toList();
        for (int index = 0; index < ids.size(); index++) {
            Long actor = ids.get(index);
            Long target = ids.get((index + 1) % ids.size());
            engine.execute(state, suspect(actor, target, List.of(target)));
        }
        engine.execute(state, TuringHuntAction.advance());
        engine.execute(state, TuringHuntAction.advance());
        return state;
    }

    private TuringHuntGameState voteCollectionState() {
        TuringHuntGameState state = newState();
        state.setPhase(TuringHuntPhase.VOTE_COLLECTION);
        state.setCurrentActorPlayerId(101L);
        return state;
    }

    private TuringHuntGameState voteRevealState(boolean revote, List<Long> top) {
        TuringHuntGameState state = newState();
        state.setPhase(TuringHuntPhase.VOTE_REVEAL);
        state.setRevote(revote);
        state.setRevoteCandidates(revote ? new ArrayList<>(top) : new ArrayList<>());
        state.setPendingTopCandidates(new ArrayList<>(top));
        state.setPendingVoteCounts(new LinkedHashMap<>());
        state.setCurrentQuestion("测试问题");
        return state;
    }

    private void submitAllAnswers(TuringHuntGameState state) {
        for (TuringHuntPlayerState player : state.alivePlayers()) {
            engine.execute(state, answer(player.getPlayerId(), "我明确选择保留当前方案，因为公开条件支持这个取舍，也能避免不可逆的损失。"));
        }
    }

    private void submitVotes(TuringHuntGameState state, Map<Long, Long> votes) {
        for (TuringHuntPlayerState player : state.alivePlayers()) {
            engine.execute(state, vote(player.getPlayerId(), votes.get(player.getPlayerId())));
        }
    }

    private TuringHuntAction answer(Long actor, String value) {
        if (value.codePointCount(0, value.length()) < 30) {
            value = value + "我这样选择是因为公开条件更支持这个取舍，也更容易复核。";
        }
        return new TuringHuntAction(actor, TuringHuntActionType.ANSWER, value, null, List.of(),
                List.of(), "简短理由", null, null, null, null, null);
    }

    private TuringHuntAction suspect(Long actor, Long primary, List<Long> suspects) {
        return new TuringHuntAction(actor, TuringHuntActionType.SUSPECT, null, primary, suspects,
                List.of("POSITION_SHIFT"), "公开回答里的立场变化缺少具体解释", null, null, null, null, null);
    }

    private TuringHuntAction question(Long actor, Long target) {
        return new TuringHuntAction(actor, TuringHuntActionType.QUESTION, null, null, List.of(),
                List.of(), "", target, "请解释你前后判断变化的依据？", null, null, null);
    }

    private TuringHuntAction response(Long actor, Long questioner) {
        return new TuringHuntAction(actor, TuringHuntActionType.RESPOND, null, null, List.of(),
                List.of(), "回应具体问题", questioner, null,
                "我的变化来自刚才公开的新信息，这条依据改变了原先判断。", null, null);
    }

    private TuringHuntAction vote(Long actor, Long target) {
        return new TuringHuntAction(actor, TuringHuntActionType.VOTE, null, null, List.of(),
                List.of(), "公开回答里的立场前后明显不一致", target, null, null, null, null);
    }

    private TuringHuntSuspicion suspicion(Long primary, Long... suspects) {
        return new TuringHuntSuspicion(primary, List.of(suspects), List.of("TEST"), "测试依据");
    }

    private List<Integer> seats(TuringHuntGameState state, List<Long> ids) {
        return ids.stream().map(id -> state.findPlayer(id).getSeatNo()).toList();
    }
}
