package com.zn.agentarena.modules.werewolf.agent;

import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import com.zn.agentarena.modules.werewolf.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 狼人杀AI自动推进器测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("狼人杀AI自动推进器测试")
class WerewolfAiGameRunnerTest {

    @Test
    @DisplayName("runNextTurn: 应根据结构化模型决策推进狼人行动")
    void testRunNextTurnSuccess() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenReturn(new WerewolfAiDecision("WOLF_KILL", 5L, null, "资源不足", 80));
        WerewolfAgentContextBuilder contextBuilder = new WerewolfAgentContextBuilder();
        WerewolfAiGameRunner runner = new WerewolfAiGameRunner(gateway, contextBuilder);
        WerewolfGameState state = createState();

        runner.runNextTurn(state, runtimeConfigs());

        assertEquals(WerewolfPhase.NIGHT_SEER, state.getPhase());
        assertEquals(5L, state.getNightKillTargetId());
        verify(gateway).generateStructured(any(), contains("你的身份: WEREWOLF"), eq(WerewolfAiDecision.class));
    }

    @Test
    @DisplayName("context: 村民上下文不应泄露其他玩家身份")
    void testContextDoesNotLeakPrivateRoles() {
        WerewolfGameState state = createState();
        WerewolfPlayerState villager = state.findPlayer(5L);

        String context = new WerewolfAgentContextBuilder().build(state, villager);

        assertTrue(context.contains("你的身份: VILLAGER"));
        assertFalse(context.contains("ID=1, 座位=1, 名称=1号玩家, 状态=存活, 身份=WEREWOLF"));
        assertFalse(context.contains("身份=SEER"));
    }

    @Test
    @DisplayName("runNextTurn: 模型异常时应使用本地策略继续对局")
    void testModelFailureFallsBack() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.generateStructured(any(), any(), eq(WerewolfAiDecision.class)))
                .thenThrow(new IllegalStateException("timeout"));
        WerewolfGameState state = createState();

        var result = new WerewolfAiGameRunner(gateway, new WerewolfAgentContextBuilder())
                .runNextTurn(state, runtimeConfigs());

        assertNotNull(state.getNightKillTargetId());
        assertFalse(state.findPlayer(state.getNightKillTargetId()).getRole().isWolfCamp());
        assertEquals("MODEL_FALLBACK", result.events().get(0).payload().get("source"));
    }

    private Map<Long, ModelRuntimeConfig> runtimeConfigs() {
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.OPENAI, "test", null,
                "test-key", 0.2, 512, Duration.ofSeconds(10), 1);
        Map<Long, ModelRuntimeConfig> configs = new LinkedHashMap<>();
        for (long id = 1; id <= 8; id++) {
            configs.put(id, config);
        }
        return configs;
    }

    private WerewolfGameState createState() {
        List<WerewolfPlayerState> players = new ArrayList<>();
        players.add(player(1L, WerewolfRole.WEREWOLF));
        players.add(player(2L, WerewolfRole.WEREWOLF));
        players.add(player(3L, WerewolfRole.SEER));
        WerewolfPlayerState witch = player(4L, WerewolfRole.WITCH);
        witch.setAntidoteAvailable(true);
        witch.setPoisonAvailable(true);
        players.add(witch);
        players.add(player(5L, WerewolfRole.VILLAGER));
        players.add(player(6L, WerewolfRole.VILLAGER));
        players.add(player(7L, WerewolfRole.VILLAGER));
        players.add(player(8L, WerewolfRole.VILLAGER));
        return WerewolfGameState.builder().gameId(1L).roundNo(1)
                .phase(WerewolfPhase.NIGHT_WOLF).players(players)
                .votes(new LinkedHashMap<>()).build();
    }

    private WerewolfPlayerState player(Long id, WerewolfRole role) {
        return WerewolfPlayerState.builder().playerId(id).seatNo(id.intValue())
                .displayName(id + "号玩家").role(role).alive(true).build();
    }
}
