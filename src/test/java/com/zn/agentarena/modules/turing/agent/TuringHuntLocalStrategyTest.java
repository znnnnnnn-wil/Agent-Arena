package com.zn.agentarena.modules.turing.agent;

import com.zn.agentarena.modules.turing.domain.TuringHuntAction;
import com.zn.agentarena.modules.turing.domain.TuringHuntActionType;
import com.zn.agentarena.modules.turing.domain.TuringHuntDefensePurpose;
import com.zn.agentarena.modules.turing.domain.TuringHuntGameState;
import com.zn.agentarena.modules.turing.domain.TuringHuntPhase;
import com.zn.agentarena.modules.turing.domain.TuringHuntPlayerState;
import com.zn.agentarena.modules.turing.engine.TuringHuntGameEngine;
import com.zn.agentarena.modules.turing.prompt.TuringQuestionBank;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TuringHuntLocalStrategyTest {

    private final TuringHuntLocalStrategy strategy = new TuringHuntLocalStrategy();
    private final TuringHuntGameEngine engine = new TuringHuntGameEngine();

    @Test
    void everyFirstRoundQuestionProducesALegalBoundedAnswer() {
        for (String question : TuringQuestionBank.FIRST_ROUND_QUESTIONS) {
            TuringHuntGameState state = state(TuringHuntPhase.ANSWER_COLLECTION);
            state.setCurrentQuestion(question);
            TuringHuntPlayerState actor = state.findPlayer(101L);

            TuringHuntAction action = strategy.create(state, actor, TuringHuntActionType.ANSWER);

            assertThat(engine.validate(state, action).valid()).as(question).isTrue();
            assertThat(action.answer().codePointCount(0, action.answer().length())).isLessThanOrEqualTo(90);
        }
    }

    @Test
    void everyInteractivePhaseHasALegalLocalFallback() {
        assertLegal(state(TuringHuntPhase.ANSWER_COLLECTION), 101L, TuringHuntActionType.ANSWER);
        assertLegal(state(TuringHuntPhase.SUSPICION_COLLECTION), 101L, TuringHuntActionType.SUSPECT);

        TuringHuntGameState question = state(TuringHuntPhase.QUESTION);
        question.setCurrentQuestionerPlayerId(101L);
        question.setCurrentQuestionTargetPlayerId(102L);
        assertLegal(question, 101L, TuringHuntActionType.QUESTION);

        TuringHuntGameState response = state(TuringHuntPhase.RESPONSE);
        response.setCurrentQuestionerPlayerId(101L);
        response.setCurrentQuestionTargetPlayerId(102L);
        assertLegal(response, 102L, TuringHuntActionType.RESPOND);

        TuringHuntGameState defense = state(TuringHuntPhase.DEFENSE);
        defense.setDefenseQueue(new ArrayList<>(List.of(101L)));
        defense.setDefensePurpose(TuringHuntDefensePurpose.CONTROVERSY);
        assertLegal(defense, 101L, TuringHuntActionType.DEFEND);

        assertLegal(state(TuringHuntPhase.VOTE_COLLECTION), 101L, TuringHuntActionType.VOTE);

        TuringHuntGameState revote = state(TuringHuntPhase.VOTE_COLLECTION);
        revote.setRevote(true);
        revote.setRevoteCandidates(new ArrayList<>(List.of(102L, 103L)));
        assertLegal(revote, 101L, TuringHuntActionType.VOTE);
    }

    private void assertLegal(TuringHuntGameState state, Long actorId, TuringHuntActionType type) {
        TuringHuntAction action = strategy.create(state, state.findPlayer(actorId), type);
        assertThat(engine.validate(state, action).valid()).as(type.name()).isTrue();
    }

    private TuringHuntGameState state(TuringHuntPhase phase) {
        List<TuringHuntPlayerState> players = new ArrayList<>();
        for (int seatNo = 1; seatNo <= 6; seatNo++) {
            players.add(TuringHuntPlayerState.builder().playerId(100L + seatNo).seatNo(seatNo)
                    .displayName(seatNo + "号").human(seatNo == 3).alive(true).build());
        }
        return TuringHuntGameState.builder().gameId(1L).roundNo(1).maxRounds(3).phase(phase)
                .humanPlayerId(103L).currentQuestion("测试问题").randomSeed(17L).players(players).build();
    }
}
