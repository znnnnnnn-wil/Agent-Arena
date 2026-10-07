package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.engine.BattleRoyaleGameEngine;
import com.zn.agentarena.modules.battle.simulation.BattleStateInvariantChecker;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleLocalGameSmokeTest {
    @Test
    void productionLocalGamesFinishWithValidStateAndSocialInteractions() {
        var events = new TreeMap<String, Integer>();
        var actions = new TreeMap<String, Integer>();
        int decisions = 0;
        for (long seed = 910_000; seed < 910_030; seed++) {
            int players = List.of(6, 8, 10).get((int) (seed % 3));
            var regions = List.of("A", "B", "C", "D", "E");
            var spawns = BattleRoyaleRules.spawnAssignments(regions, players, seed);
            var roster = new ArrayList<BattlePlayerState>();
            for (int i = 0; i < players; i++) roster.add(BattlePlayerState.builder()
                    .playerId((long) i + 1).displayName("P" + (i + 1)).region(spawns.get(i))
                    .health(100).food(5).water(5).alive(true).build());
            var state = BattleRoyaleGameState.builder().gameId(seed).randomSeed(seed).roundNo(1)
                    .maxRounds(20).players(roster)
                    .ringOrder(new ArrayList<>(BattleRoyaleRules.ringOrder(regions, seed))).build();
            var engine = new BattleRoyaleGameEngine();
            var runner = new BattleAiGameRunner(null, new BattleAgentContextBuilder());
            int turns = 0;
            while (!engine.isFinished(state)) {
                assertTrue(turns++ < players * 20, "seed=" + seed + " 对局未按时结束");
                var result = runner.runNextTurn(state, engine.currentTurnPlayer(state), null);
                for (var event : result.events()) {
                    events.merge(event.type(), 1, Integer::sum);
                    if (event.type().equals("BATTLE_ACTION_EXECUTED"))
                        actions.merge(String.valueOf(event.payload().get("action")), 1, Integer::sum);
                }
                new BattleStateInvariantChecker().verify(state, seed);
                if (engine.isFinished(state)) assertEquals("BATTLE_FINISHED", result.events().get(result.events().size() - 1).type());
            }
            decisions += turns;
        }
        assertEquals(30, events.get("BATTLE_FINISHED"));
        assertTrue(events.getOrDefault("BATTLE_ALLIANCE_FORMED", 0) > 0);
        assertTrue(events.getOrDefault("BATTLE_OBJECTIVE_COMPLETED", 0) > 0);
        assertTrue(events.getOrDefault("BATTLE_PRIVATE_MESSAGE", 0) > 0);
        System.out.println("LOCAL_SMOKE games=30 decisions=" + decisions + " actions=" + actions + " events=" + events);
    }
}
