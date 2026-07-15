package com.zn.agentarena.modules.battle.agent;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("大逃杀AI自动推进器测试")
class BattleAiGameRunnerTest {

    @Test
    @DisplayName("runNextTurn: 模型异常时应降级并继续执行合法行动")
    void testModelFailureFallsBack() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(BattleAiDecision.class)))
                .thenThrow(new IllegalStateException("timeout"));
        BattlePlayerState actor = player(1L);
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(1L).maxRounds(20)
                .roundNo(1).randomSeed(42L).regions(List.of("A", "B", "C", "D", "E"))
                .safeRegion("A").safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(List.of(actor, player(2L))).build();
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.OPENAI, "test", null,
                "test-key", 0.2, 256, Duration.ofSeconds(5), 0);

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, actor, config);

        assertEquals("MODEL_FALLBACK", result.events().get(0).payload().get("source"));
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_ACTION_EXECUTED".equals(event.type())));
    }

    private BattlePlayerState player(Long id) {
        return BattlePlayerState.builder().playerId(id).displayName(id + "号幸存者")
                .region("A").health(100).food(5).water(5).alive(true).build();
    }
}
