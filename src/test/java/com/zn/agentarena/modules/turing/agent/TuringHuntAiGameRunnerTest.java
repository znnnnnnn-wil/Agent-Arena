package com.zn.agentarena.modules.turing.agent;

import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import com.zn.agentarena.modules.turing.domain.TuringHuntGameState;
import com.zn.agentarena.modules.turing.domain.TuringHuntPhase;
import com.zn.agentarena.modules.turing.domain.TuringHuntPlayerState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TuringHuntAiGameRunnerTest {

    @Test
    void invalidShortTextGetsExactlyOneCorrectionAndUsesActionTokenBudget() {
        ModelGateway gateway = mock(ModelGateway.class);
        TuringHuntAiDecision tooShort = decision("我选地图。");
        TuringHuntAiDecision corrected = decision(
                "我选择保留地图，因为它给所有人同一份方向参照，也能让后续判断更容易公开复核。");
        when(gateway.generateStructured(any(), anyString(), eq(TuringHuntAiDecision.class)))
                .thenReturn(tooShort, corrected);
        TuringHuntAiGameRunner runner = new TuringHuntAiGameRunner(gateway,
                new TuringHuntAgentContextBuilder(), new TuringHuntLocalStrategy(),
                new TuringHuntFairnessMetrics());
        TuringHuntGameState state = state();

        runner.runNextTurn(state, runtimeConfig());

        verify(gateway, times(2)).generateStructured(any(), anyString(), eq(TuringHuntAiDecision.class));
        ArgumentCaptor<ModelRuntimeConfig> configs = ArgumentCaptor.forClass(ModelRuntimeConfig.class);
        verify(gateway, times(2)).generateStructured(configs.capture(), anyString(), eq(TuringHuntAiDecision.class));
        assertThat(configs.getAllValues()).extracting(ModelRuntimeConfig::maxTokens).containsOnly(256);
        assertThat(state.getAnswersByPlayerId().get(101L).answer()).isEqualTo(corrected.answer());
    }

    @Test
    void secondInvalidTextFallsBackWithoutAThirdModelCall() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), anyString(), eq(TuringHuntAiDecision.class)))
                .thenReturn(decision("太短。"), decision("仍然太短。"));
        TuringHuntAiGameRunner runner = new TuringHuntAiGameRunner(gateway,
                new TuringHuntAgentContextBuilder(), new TuringHuntLocalStrategy(),
                new TuringHuntFairnessMetrics());
        TuringHuntGameState state = state();

        runner.runNextTurn(state, runtimeConfig());

        verify(gateway, times(2)).generateStructured(any(), anyString(), eq(TuringHuntAiDecision.class));
        assertThat(state.getAnswersByPlayerId().get(101L).answer()).hasSizeGreaterThan(20);
    }

    private TuringHuntAiDecision decision(String answer) {
        return new TuringHuntAiDecision("ANSWER", answer, null, List.of(), List.of(),
                "内部短依据", null, null, null, null, null);
    }

    private ModelRuntimeConfig runtimeConfig() {
        return new ModelRuntimeConfig(ModelProvider.OPENAI, "test", "https://example.invalid", "secret",
                0.4, 2_000, Duration.ofSeconds(2), 0);
    }

    private TuringHuntGameState state() {
        List<TuringHuntPlayerState> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(TuringHuntPlayerState.builder().playerId(100L + seat).seatNo(seat)
                    .displayName(seat + "号").human(seat == 3).alive(true).build());
        }
        return TuringHuntGameState.builder().gameId(1L).roundNo(1).maxRounds(3)
                .phase(TuringHuntPhase.ANSWER_COLLECTION).currentQuestion("你会保留地图还是时钟？")
                .humanPlayerId(103L).randomSeed(7L).players(players).build();
    }
}
