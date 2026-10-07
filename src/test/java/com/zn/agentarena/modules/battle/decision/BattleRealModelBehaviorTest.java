package com.zn.agentarena.modules.battle.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.goal.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.promise.*;
import com.zn.agentarena.modules.battle.simulation.*;
import com.zn.agentarena.modules.battle.simulation.prompt.*;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.model.domain.*;
import com.zn.agentarena.modules.model.gateway.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 显式启用才调用付费模型；仅保存合成场景的聚合结果，不保存密钥或原始响应。 */
@EnabledIfEnvironmentVariable(named = "ARENA_REAL_MODEL_TEST", matches = "true")
class BattleRealModelBehaviorTest {
    private static final String RULES_VERSION = "2026-10-07-effective-damage-incentive-v3";
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private Path outputPath = Path.of("target/real-model-behavior-results.json");

    @Test
    void controlledOpportunitiesAndCompleteGame() throws Exception {
        String key = System.getenv("QWEN_API_KEY");
        if (key == null || key.isBlank()) key = System.getenv("DASHSCOPE_API_KEY");
        assertNotNull(key, "需要配置千问密钥环境变量");
        assertFalse(key.isBlank());
        boolean quick = "true".equals(System.getenv("ARENA_REAL_MODEL_QUICK"));
        var properties = new AgentArenaProperties(null, null, null);
        var delegate = new LangChain4jModelGateway(quick ? new RealModelTestFactory(new ModelEndpointPolicy(properties))
                : new OpenAiCompatibleModelFactory(new ModelEndpointPolicy(properties)),
                mapper, new ModelCallConcurrencyLimiter(properties));
        var gateway = new RecordingGateway(delegate);
        String model = Optional.ofNullable(System.getenv("ARENA_REAL_MODEL_NAME")).filter(value -> !value.isBlank()).orElse("qwen-plus");
        int repeats = quick ? 1 : 3;
        int stopRound = 12;
        int maxTokens = quick ? 4096 : 900;
        int timeoutSeconds = quick ? 90 : 45;
        if (quick) outputPath = Path.of("target/real-model-" + model.replaceAll("[^a-zA-Z0-9._-]", "_") + "-results.json");
        double temperature = model.equals("kimi-k3") ? 1.0 : 0.4;
        var config = new ModelRuntimeConfig(ModelProvider.QWEN, model, null, key,
                temperature, maxTokens, Duration.ofSeconds(timeoutSeconds), 0);
        var runner = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder());
        report.put("model", model);
        report.put("rulesVersion", RULES_VERSION);
        report.put("temperature", temperature);
        report.put("maxTokens", maxTokens);
        report.put("timeoutSeconds", timeoutSeconds);
        report.put("repeats", repeats);
        report.put("thinkingMode", quick ? "disabled" : "provider-default");
        report.put("retries", 0);
        report.put("status", "RUNNING");
        report.put("controlled", rows);
        boolean resume = "true".equals(System.getenv("ARENA_REAL_MODEL_RESUME"));
        if (resume) {
            var saved = mapper.readTree(Files.readString(outputPath));
            assertEquals(model, saved.path("model").asText(), "续测必须使用与已保存样本相同的模型");
            assertEquals(RULES_VERSION, saved.path("rulesVersion").asText(), "规则改变后不能复用旧控制样本");
            if (quick && saved.path("controlled").isEmpty()) {
                // 鉴权或参数检查失败没有产生样本，修复配置后从专项场景开始。
                resume = false;
            } else {
                assertEquals(scenariosCount(repeats), saved.path("controlled").size(), "续测要求全部受控样本已保存");
                assertEquals(temperature, saved.path("temperature").asDouble(), "续测的采样参数必须相同");
                assertEquals(maxTokens, saved.path("maxTokens").asInt(), "续测的输出上限必须相同");
                for (var row : saved.path("controlled")) rows.add(mapper.convertValue(row,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }));
                report.put("resumedControlledSamples", rows.size());
                for (var row : rows) if (row.get("latencyMs") instanceof Number latency) gateway.latencies.add(latency.longValue());
            }
        }
        String[] scenarios = {"REVIVE_ALLY", "REVIVE_NO_MEDKIT", "SELL_INFORMATION", "BUY_INFORMATION",
                "BUY_ALREADY_KNOWN", "BUY_NO_PAYMENT", "FULFILL_MEDKIT", "FULFILL_NO_MEDKIT"};
        for (int i = 0; i < scenarios.length; i++) {
            if (resume) break;
            String scenario = scenarios[i];
            for (int repeat = 0; repeat < repeats; repeat++) {
                long seed = 3_100_000L + i * 100 + repeat;
                var state = scenario(scenario, seed);
                var actor = state.findPlayer(1L);
                var context = runner.createContext(state, actor);
                var tactical = new BattleDecisionCandidateGenerator().generate(state, actor, context);
                var social = new BattleSocialDecisionCandidateGenerator().generate(state, actor, context);
                com.zn.agentarena.modules.game.engine.EngineResult<BattleRoyaleGameState> result;
                try {
                    result = runner.runNextTurn(state, actor, config);
                } catch (AssertionError unavailable) {
                    report.put("status", "API_CALL_FAILED");
                    report.put("modelCalls", gateway.calls);
                    report.put("failedScenario", scenario);
                    save();
                    throw unavailable;
                }
                var selected = gateway.last;
                var row = new LinkedHashMap<String, Object>();
                row.put("latencyMs", gateway.latencies.get(gateway.latencies.size() - 1));
                row.put("scenario", scenario);
                row.put("seed", seed);
                row.put("tacticalCandidates", tactical.candidates().stream().map(c -> c.actionType().name()).toList());
                row.put("socialCandidates", social.candidates().stream().map(c -> c.decision() == null ? "NONE" : c.decision().type()).toList());
                row.put("selectedTactical", selected.tacticalCandidateId());
                row.put("selectedSocial", selected.socialCandidateId());
                row.put("selectedSocialType", social.candidates().stream().filter(c -> Objects.equals(c.candidateId(), selected.socialCandidateId()))
                        .map(c -> c.decision() == null ? "NONE" : c.decision().type()).findFirst().orElse("INVALID"));
                row.put("decision", result.events().get(0).payload());
                row.put("events", result.events().stream().map(EngineEvent::type).toList());
                rows.add(row);
                save();
                new BattleStateInvariantChecker().verify(state, seed);
                System.out.printf("REAL_CONTROL scenario=%s repeat=%d action=%s social=%s resolution=%s%n", scenario, repeat + 1,
                        rowDecision(row).get("action"), row.get("selectedSocialType"), rowDecision(row).get("socialCandidateResolution"));
                assertEquals("MODEL", rowDecision(row).get("source"), "不得把本地回退作为真实模型样本");
            }
        }
        BattleSimulationResult game;
        try {
            game = new BattleSimulationRunner(gateway, config).run(new BattleSimulationConfig(
                    BattleSimulationMode.LLM_SAMPLE, 1, 6, stopRound, 3_200_000L, true)).get(0);
        } catch (AssertionError failed) {
            report.put("status", "API_CALL_FAILED");
            report.put("failedScenario", "COMPLETE_GAME");
            report.put("modelCalls", gateway.calls);
            save();
            throw failed;
        }
        var decisions = game.events().stream().filter(e -> e.type().equals("AI_DECISION")).toList();
        var gameRow = new LinkedHashMap<String, Object>();
        gameRow.put("seed", game.seed());
        gameRow.put("players", 6);
        gameRow.put("stopRound", stopRound);
        gameRow.put("rounds", game.rounds());
        gameRow.put("termination", game.terminationReason());
        gameRow.put("abnormal", game.abnormal());
        gameRow.put("failure", game.failure());
        gameRow.put("decisions", decisions.size());
        gameRow.put("actions", distribution(decisions, "action"));
        gameRow.put("socialActions", distribution(decisions, "socialAction"));
        gameRow.put("sources", distribution(decisions, "source"));
        gameRow.put("tacticalResolutions", distribution(decisions, "candidateResolution"));
        gameRow.put("socialResolutions", distribution(decisions, "socialCandidateResolution"));
        Map<String, Integer> events = new TreeMap<>();
        game.events().forEach(e -> events.merge(e.type(), 1, Integer::sum));
        gameRow.put("events", events);
        var opportunities = game.decisionTraces().stream().filter(t ->
                t.lifeStatus() == BattlePlayerLifeStatus.ALIVE
                && t.agentKnownState().selfResources().getOrDefault("ammo", 0) > 0
                && t.agentKnownState().selfResources().getOrDefault("armed", 0) == 1
                && t.agentKnownState().selfResources().getOrDefault("visibleActiveNonAllies", 0) > 0).toList();
        gameRow.put("armedVisibleNonAllyOpportunities", opportunities.size());
        gameRow.put("attacksAtThoseOpportunities", opportunities.stream()
                .filter(t -> "ATTACK".equals(t.decision().action())).count());
        gameRow.put("firstAttackRound", game.decisionTraces().stream()
                .filter(t -> "ATTACK".equals(t.decision().action())).mapToInt(t -> t.round()).min().orElse(-1));
        gameRow.put("finalRingStage", game.finalState().getRingStage());
        gameRow.put("remainingContenders", game.finalState().contendingPlayers().size());
        gameRow.put("winnerPlayerId", game.winnerPlayerId());
        game.events().stream().filter(e -> "BATTLE_FINISHED".equals(e.type())).findFirst().ifPresent(e -> {
            gameRow.put("winnerReason", e.payload().get("reason"));
            gameRow.put("winnerScoreBreakdown", e.payload().get("scoreBreakdown"));
        });
        report.put("game", gameRow);
        report.put("modelCallsThisRun", gateway.calls);
        report.put("latenciesMs", gateway.latencies);
        report.put("modelCalls", (resume ? rows.size() : 0) + gateway.calls);
        report.put("status", game.abnormal() ? "GAME_ABNORMAL" : "COMPLETE");
        save();
        System.out.printf("REAL_GAME decisions=%d abnormal=%s calls=%d%n", decisions.size(), game.abnormal(), gateway.calls);
        assertFalse(game.abnormal());
        assertTrue(decisions.stream().allMatch(e -> "MODEL".equals(e.payload().get("source"))));
    }

    private int scenariosCount(int repeats) { return 8 * repeats; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowDecision(Map<String, Object> row) { return (Map<String, Object>) row.get("decision"); }

    private Map<String, Integer> distribution(List<EngineEvent> events, String field) {
        Map<String, Integer> counts = new TreeMap<>();
        events.forEach(e -> counts.merge(String.valueOf(e.payload().getOrDefault(field, "NONE")), 1, Integer::sum));
        return counts;
    }

    private void save() throws Exception {
        Files.writeString(outputPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    private BattleRoyaleGameState scenario(String name, long seed) {
        var state = BattlePromptExperimentMain.state(name.startsWith("REVIVE") ? BattlePromptScenario.REVIVE : BattlePromptScenario.ATTACK, seed);
        var actor = state.findPlayer(1L);
        var subject = state.findPlayer(2L);
        subject.setHealth(name.startsWith("REVIVE") ? 0 : 100);
        actor.setArmed(false);
        actor.setWeaponLevel(0);
        actor.setWeaponName(null);
        actor.setAmmo(0);
        if (name.equals("REVIVE_NO_MEDKIT") || name.equals("FULFILL_NO_MEDKIT")) actor.setMedkits(0);
        if (name.contains("INFORMATION") || name.startsWith("BUY_")) {
            boolean sell = name.equals("SELL_INFORMATION");
            var rumor = BattleRumor.builder().rumorId("test-rumor").originatorPlayerId(sell ? 1L : 2L)
                    .speakerPlayerId(sell ? 1L : 2L).claimType(BattleRumorClaimType.RESOURCE_CACHE)
                    .claim("北部林地有补给，尚未核实").confidence(75).createdRound(1)
                    .knownByPlayerIds(new ArrayList<>(List.of(sell ? 1L : 2L))).build();
            if (name.equals("BUY_ALREADY_KNOWN")) rumor.getKnownByPlayerIds().add(1L);
            state.getRumors().add(rumor);
            if (sell) state.getHiddenGoals().add(BattleHiddenGoal.builder().goalId("test-broker").ownerPlayerId(1L)
                    .type(BattleHiddenGoalType.INFORMATION_BROKER).targetValue(1).status(BattleHiddenGoalStatus.ACTIVE).build());
            else state.getInformationTradeProposals().add(BattleInformationTradeProposal.builder().tradeId("test-sale")
                    .sellerPlayerId(2L).buyerPlayerId(1L).rumorId("test-rumor")
                    .price(new BattleResourceAmount(BattleResourceType.AMMO, 1)).createdRound(1).expiresRound(4)
                    .status(BattleInformationTradeStatus.PROPOSED).knownByPlayerIds(new ArrayList<>(List.of(1L, 2L))).build());
            if (!name.equals("BUY_NO_PAYMENT")) actor.setAmmo(3);
        }
        if (name.startsWith("FULFILL")) state.getPromises().add(BattlePromise.builder().promiseId("test-promise")
                .promiserPlayerId(1L).promiseePlayerId(2L).promiseType(BattlePromiseType.GIVE_RESOURCE)
                .resourceType(BattleResourceType.MEDKIT).amount(1).deadlineRound(2)
                .status(BattlePromiseStatus.ACCEPTED).acceptedRound(1).build());
        return state;
    }

    private static class RecordingGateway implements ModelGateway {
        private final ModelGateway delegate;
        private BattleCandidateDecision last;
        private int calls;
        private final List<Long> latencies = new ArrayList<>();
        private long lastStarted;
        RecordingGateway(ModelGateway delegate) { this.delegate = delegate; }
        private void throttle() {
            long wait = 3_300 - (System.currentTimeMillis() - lastStarted);
            if (wait > 0) try { Thread.sleep(wait); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new AssertionError("真实模型测试被中断");
            }
            lastStarted = System.currentTimeMillis();
        }
        public String generate(ModelRuntimeConfig config, String prompt) { throw new UnsupportedOperationException(); }
        public <T> T generateStructured(ModelRuntimeConfig config, String prompt, Class<T> type) {
            throttle();
            long started = System.nanoTime();
            try {
                T result = delegate.generateStructured(config, prompt, type);
                calls++;
                latencies.add((System.nanoTime() - started) / 1_000_000);
                last = (BattleCandidateDecision) result;
                System.out.printf("REAL_MODEL_CALL completed=%d%n", calls);
                return result;
            } catch (RuntimeException e) {
                // 不保留供应商原始错误内容，避免凭证、端点或响应被写入测试报告。
                throw new AssertionError("REAL_MODEL_CALL_FAILED: " + e.getClass().getSimpleName());
            }
        }
    }
}
