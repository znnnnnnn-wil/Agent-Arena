package com.zn.agentarena.modules.battle.social;

import com.zn.agentarena.modules.battle.agent.BattleAgentContextBuilder;
import com.zn.agentarena.modules.battle.agent.BattleAiDecision;
import com.zn.agentarena.modules.battle.agent.BattleAiGameRunner;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationSystem;
import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.decision.BattleCandidateDecision;
import com.zn.agentarena.modules.battle.memory.BattleMemorySystem;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("大逃杀通信、记忆和主观关系测试")
class BattleSocialSystemsTest {

    @Test
    @DisplayName("PRIVATE 只对发送者和接收者可见并形成对应记忆")
    void privateMessageIsDeliveredOnlyToParticipants() {
        Fixture fixture = fixture();
        var delivered = fixture.communication.deliver(fixture.state, 1L,
                new BattleCommunicationDecision("PRIVATE", 2L, "C准备攻击你。"));
        fixture.memory.rememberMessage(fixture.state, delivered.message());

        assertTrue(delivered.delivered());
        assertEquals(1, fixture.communication.messagesVisibleTo(fixture.state, 1L).size());
        assertEquals(1, fixture.communication.messagesVisibleTo(fixture.state, 2L).size());
        assertTrue(fixture.communication.messagesVisibleTo(fixture.state, 3L).isEmpty());
        assertTrue(fixture.memory.memoriesFor(fixture.state, 2L).stream()
                .anyMatch(memory -> memory.getSummary().contains("C准备攻击你")));
        assertTrue(fixture.memory.memoriesFor(fixture.state, 3L).isEmpty());
        BattleAiGameRunner runner = new BattleAiGameRunner(mock(ModelGateway.class), new BattleAgentContextBuilder());
        assertEquals("C准备攻击你。", runner.createContext(fixture.state, fixture.state.findPlayer(2L))
                .receivedMessages().get(0).getContent());
        assertTrue(runner.createContext(fixture.state, fixture.state.findPlayer(3L)).receivedMessages().isEmpty());
        assertFalse(delivered.event().publicPayload().containsKey("content"));
        assertTrue(delivered.event().payload().containsKey("content"));
    }

    @Test
    @DisplayName("PUBLIC 对所有存活 AI 可见但不改变真实世界资源")
    void publicMessageIsHeardButDoesNotBecomeWorldFact() {
        Fixture fixture = fixture();
        fixture.state.setRegionSearchCounts(new LinkedHashMap<>(java.util.Map.of("E", 4)));
        var before = new LinkedHashMap<>(fixture.state.getRegionSearchCounts());

        var delivered = fixture.communication.deliver(fixture.state, 1L,
                new BattleCommunicationDecision("PUBLIC", null, "E区有医疗资源。"));
        fixture.memory.rememberMessage(fixture.state, delivered.message());

        for (long playerId = 1; playerId <= 3; playerId++) {
            assertEquals(1, fixture.communication.messagesVisibleTo(fixture.state, playerId).size());
        }
        BattleAiGameRunner runner = new BattleAiGameRunner(mock(ModelGateway.class), new BattleAgentContextBuilder());
        assertEquals("E区有医疗资源。", runner.createContext(fixture.state, fixture.state.findPlayer(2L))
                .receivedMessages().get(0).getContent());
        assertEquals(before, fixture.state.getRegionSearchCounts());
        assertEquals("E区有医疗资源。", delivered.event().publicPayload().get("content"));
    }

    @Test
    @DisplayName("被攻击者关系改变，未观察到的第三方关系保持默认")
    void attackChangesOnlyVictimsSubjectiveRelationship() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(BattleCandidateDecision.class)))
                .thenReturn(new BattleCandidateDecision("T04", null, null, "主动攻击", 80));
        BattlePlayerState victim = player(1L, "A");
        BattlePlayerState attacker = player(2L, "A");
        attacker.setArmed(true);
        attacker.setWeaponName("简易手枪");
        attacker.setWeaponLevel(1);
        attacker.setAmmo(5);
        BattlePlayerState witnessElsewhere = player(3L, "E");
        BattleRoyaleGameState state = state(victim, attacker, witnessElsewhere);

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, attacker, runtimeConfig());

        BattleRelationshipState victimView = state.getSocialStates().get(1L).getRelationships().get(2L);
        BattleRelationshipState witnessView = state.getSocialStates().get(3L).getRelationships().get(2L);
        assertTrue(victimView.getTrust() < 50);
        assertTrue(victimView.getResentment() > 0);
        assertEquals(50, witnessView.getTrust());
        assertEquals(0, witnessView.getResentment());
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_RELATIONSHIP_CHANGED".equals(event.type())));
    }

    @Test
    @DisplayName("非法通信应被丢弃但合法战术行动仍执行")
    void invalidCommunicationDoesNotCancelAction() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(BattleCandidateDecision.class)))
                .thenReturn(new BattleCandidateDecision("T03", null,
                        new BattleCommunicationDecision("PRIVATE", 999L, "无效目标"), "搜索", 70));
        BattlePlayerState actor = player(1L, "A");
        BattleRoyaleGameState state = state(actor, player(2L, "E"));

        var result = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder())
                .runNextTurn(state, actor, runtimeConfig());

        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_ACTION_EXECUTED".equals(event.type())));
        assertTrue(result.events().stream().noneMatch(event -> "BATTLE_PRIVATE_MESSAGE".equals(event.type())));
    }

    private Fixture fixture() {
        BattleSocialStateInitializer initializer = new BattleSocialStateInitializer();
        BattleRoyaleGameState state = state(player(1L, "A"), player(2L, "B"), player(3L, "C"));
        initializer.ensure(state);
        return new Fixture(state, new BattleCommunicationSystem(initializer), new BattleMemorySystem(initializer));
    }

    private BattleRoyaleGameState state(BattlePlayerState... players) {
        return BattleRoyaleGameState.builder().gameId(9L).roundNo(1).maxRounds(20).randomSeed(42L)
                .safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(new ArrayList<>(List.of(players))).build();
    }

    private BattlePlayerState player(Long id, String region) {
        return BattlePlayerState.builder().playerId(id).displayName(id + "号")
                .region(region).health(100).food(5).water(5).alive(true).build();
    }

    private ModelRuntimeConfig runtimeConfig() {
        return new ModelRuntimeConfig(ModelProvider.OPENAI, "test", null,
                "test-key", 0.2, 256, Duration.ofSeconds(5), 0);
    }

    private record Fixture(BattleRoyaleGameState state,
                           BattleCommunicationSystem communication,
                           BattleMemorySystem memory) {
    }
}
