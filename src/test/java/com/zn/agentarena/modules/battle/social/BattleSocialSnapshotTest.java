package com.zn.agentarena.modules.battle.social;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.dao.entity.GameSnapshot;
import com.zn.agentarena.dao.mapper.GameSnapshotMapper;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationSystem;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceStatus;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceSystem;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.memory.BattleMemorySystem;
import com.zn.agentarena.modules.battle.promise.BattlePromiseDecision;
import com.zn.agentarena.modules.battle.promise.BattlePromiseStatus;
import com.zn.agentarena.modules.battle.promise.BattlePromiseSystem;
import com.zn.agentarena.modules.game.service.GameSnapshotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("大逃杀社会状态快照兼容测试")
class BattleSocialSnapshotTest {

    @Test
    @DisplayName("新快照往返后联盟、承诺、倒地、记忆、关系和通信保持一致")
    void socialStateRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BattleSocialStateInitializer initializer = new BattleSocialStateInitializer();
        BattleRoyaleGameState state = state();
        initializer.ensure(state);
        BattleRelationshipSystem relationships = new BattleRelationshipSystem(initializer);
        relationships.attacked(state, 1L, 2L, 40);
        BattleMemorySystem memory = new BattleMemorySystem(initializer);
        BattleCommunicationSystem communication = new BattleCommunicationSystem(initializer);
        var delivered = communication.deliver(state, 2L,
                new BattleCommunicationDecision("PRIVATE", 1L, "暂时停火。"));
        memory.rememberMessage(state, delivered.message());
        BattleAllianceSystem alliances = new BattleAllianceSystem(initializer, memory, relationships);
        alliances.propose(state, 1L, 2L, List.of(1L, 2L));
        alliances.accept(state, 2L, 1L, state.getAlliances().get(0).getAllianceId(),
                List.of(1L, 2L), new ArrayList<>());
        BattlePromiseSystem promises = new BattlePromiseSystem(initializer, memory, relationships);
        state.findPlayer(1L).setMedkits(1);
        promises.propose(state, 1L, 2L,
                new BattlePromiseDecision("GIVE_RESOURCE", "MEDKIT", 1, 5), List.of(1L, 2L));
        promises.accept(state, 2L, 1L, state.getPromises().get(0).getPromiseId(), List.of(1L, 2L));
        state.findPlayer(2L).markDowned(1, 2, 1L);

        BattleRoyaleGameState restored = mapper.readValue(mapper.writeValueAsString(state), BattleRoyaleGameState.class);
        initializer.ensure(restored);

        assertEquals(state.getSocialStates(), restored.getSocialStates());
        assertEquals(state.getCommunicationHistory(), restored.getCommunicationHistory());
        assertEquals(BattleAllianceStatus.ACTIVE, restored.getAlliances().get(0).getStatus());
        assertEquals(BattlePromiseStatus.ACCEPTED, restored.getPromises().get(0).getStatus());
        assertTrue(restored.findPlayer(2L).isDowned());
        assertEquals(3, restored.findPlayer(2L).getBleedOutRound());
    }

    @Test
    @DisplayName("缺少 socialStates 的旧快照可读取并补齐默认关系")
    void oldSnapshotWithoutSocialStateIsCompatible() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BattleRoyaleGameState original = state();
        Map<String, Object> oldState = mapper.convertValue(original, new TypeReference<>() { });
        oldState.remove("socialStates");
        oldState.remove("communicationHistory");
        oldState.remove("alliances");
        oldState.remove("promises");
        oldState.remove("tradeProposals");
        removeDownedFields(oldState);
        GameSnapshotMapper snapshotMapper = mock(GameSnapshotMapper.class);
        when(snapshotMapper.selectOneByQuery(any())).thenReturn(GameSnapshot.builder()
                .gameId(1L).sequenceNo(0L).gameState(oldState).build());
        BattleSocialStateInitializer initializer = new BattleSocialStateInitializer();
        GameSnapshotService service = new GameSnapshotService(snapshotMapper, mapper,
                new AgentArenaProperties(null, null, null), initializer);

        BattleRoyaleGameState restored = service.loadLatestBattleSnapshot(1L);

        assertNotNull(restored.getSocialStates());
        assertNotNull(restored.getCommunicationHistory());
        assertNotNull(restored.getAlliances());
        assertNotNull(restored.getPromises());
        assertNotNull(restored.getTradeProposals());
        assertTrue(restored.getAlliances().isEmpty());
        assertTrue(restored.findPlayer(1L).isActive());
        assertEquals(2, restored.getSocialStates().size());
        assertEquals(50, restored.getSocialStates().get(1L).getRelationships().get(2L).getTrust());
    }

    private BattleRoyaleGameState state() {
        return BattleRoyaleGameState.builder().gameId(1L).roundNo(1).maxRounds(20)
                .players(new ArrayList<>(List.of(player(1L), player(2L)))).build();
    }

    private BattlePlayerState player(Long id) {
        return BattlePlayerState.builder().playerId(id).displayName(id + "号").region("A")
                .health(100).food(5).water(5).alive(true).build();
    }

    @SuppressWarnings("unchecked")
    private void removeDownedFields(Map<String, Object> state) {
        for (Map<String, Object> player : (List<Map<String, Object>>) state.get("players")) {
            player.remove("lifeStatus");
            player.remove("downedAtRound");
            player.remove("bleedOutRound");
            player.remove("downedByPlayerId");
        }
    }
}
