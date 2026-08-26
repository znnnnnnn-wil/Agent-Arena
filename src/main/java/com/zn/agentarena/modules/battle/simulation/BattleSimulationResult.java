package com.zn.agentarena.modules.battle.simulation;

import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.simulation.trace.BattleAgentDecisionTrace;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import java.util.*;

public record BattleSimulationResult(long seed, Long winnerPlayerId, int rounds, String terminationReason,
                                     boolean abnormal, String failure, BattleRoyaleGameState finalState,
                                     List<EngineEvent> events, Map<Integer,Integer> aliveByRound,
                                     Map<Integer,BattlePromptSample> promptSamples,
                                     List<BattleAgentDecisionTrace> decisionTraces) {
    public BattleSimulationResult(long seed, Long winnerPlayerId, int rounds, String terminationReason,
                                  boolean abnormal, String failure, BattleRoyaleGameState finalState,
                                  List<EngineEvent> events, Map<Integer,Integer> aliveByRound,
                                  Map<Integer,BattlePromptSample> promptSamples) {
        this(seed, winnerPlayerId, rounds, terminationReason, abnormal, failure, finalState, events,
                aliveByRound, promptSamples, List.of());
    }
}
