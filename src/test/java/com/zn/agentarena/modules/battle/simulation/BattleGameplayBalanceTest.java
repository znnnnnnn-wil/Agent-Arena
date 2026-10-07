package com.zn.agentarena.modules.battle.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BattleGameplayBalanceTest {
    @Test
    void shortGamesKeepStateValidAcrossThirtySeeds() throws Exception {
        var results = new BattleSimulationRunner().run(new BattleSimulationConfig(
                BattleSimulationMode.FAST_SIMULATION, 30, 6, 12, 3_200_000L, true));
        for (var result : results) {
            assertFalse(result.abnormal(), "seed=" + result.seed() + ": " + result.failure());
            assertNotNull(result.winnerPlayerId(), "seed=" + result.seed());
            if (result.rounds() >= 9) assertEquals(5, result.finalState().getRingStage());
        }
        var report = Map.of("games", results.size(), "abnormal", 0,
                "decisions", results.stream().mapToInt(r -> r.decisionTraces().size()).sum(),
                "attacks", results.stream().flatMap(r -> r.decisionTraces().stream())
                    .filter(t -> "ATTACK".equals(t.decision().action())).count(),
                "timeLimitGames", results.stream().filter(r -> "MAX_ROUNDS".equals(r.terminationReason())).count());
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/gameplay-local-validation.json"),
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
