package com.zn.agentarena.modules.battle.simulation;

import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;
import com.zn.agentarena.modules.model.domain.*;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleActionPipelineTraceTest {
    @Test
    void traceKeepsParsedSanitizedAndExecutedActionsDistinct() {
        ModelGateway gateway = new ModelGateway() {
            public String generate(ModelRuntimeConfig c, String p) { return "{}"; }
            public <T> T generateStructured(ModelRuntimeConfig c, String p, Class<T> type) {
                return type.cast(new BattleAiDecision("ATTACK", 2L, null, null, null, "attack", 80));
            }
        };
        BattleAiGameRunner runner = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder());
        BattlePlayerState actor = player(1, "A", 100, true, 2, 10);
        BattlePlayerState target = player(2, "A", 20, false, 0, 0);
        List<BattlePlayerState> players = new ArrayList<>(List.of(actor, target));
        for (long id = 3; id <= 6; id++) { BattlePlayerState p = player(id, "E", 100, false, 0, 0); p.setHidden(true); players.add(p); }
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(1L).randomSeed(1L).roundNo(1).maxRounds(20)
                .safeRegion("A").safeRegions(new ArrayList<>(List.of("A","B","C","D","E"))).players(players).build();
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.OPENAI, "test", null, "x", 0.0, 100, Duration.ofSeconds(1), 0);
        BattleDecisionPipelineTrace trace = runner.runDiagnosticTurn(state, actor, config, "TEST", 1L, 0);
        assertEquals("ATTACK", trace.parsedAction());
        assertEquals("ATTACK", trace.sanitizedAction());
        assertEquals("ATTACK", trace.executedAction());
        assertFalse(trace.sanitizedChanged());
        assertTrue(trace.valid());
        assertTrue(trace.engineEvents().contains("BATTLE_ACTION_EXECUTED"));
    }

    @Test
    void hiddenPlayersAreNotExposedAsVisibleAttackCandidates() {
        BattlePlayerState actor = player(1, "A", 100, true, 2, 10);
        BattlePlayerState hidden = player(2, "A", 20, false, 0, 0);
        hidden.setHidden(true);
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(2L).randomSeed(2L).roundNo(1).maxRounds(20)
                .safeRegion("A").safeRegions(new ArrayList<>(List.of("A","B","C","D","E")))
                .players(new ArrayList<>(List.of(actor, hidden))).build();
        BattleAiGameRunner runner = new BattleAiGameRunner(null, new BattleAgentContextBuilder());
        assertTrue(runner.createContext(state, actor).observation().visiblePlayers().isEmpty());
    }

    private BattlePlayerState player(long id, String region, int hp, boolean armed, int level, int ammo) {
        return BattlePlayerState.builder().playerId(id).displayName("P" + id).region(region).health(hp).food(7).water(7)
                .armed(armed).weaponLevel(level).ammo(ammo).alive(true).lifeStatus(BattlePlayerLifeStatus.ALIVE).build();
    }
}
