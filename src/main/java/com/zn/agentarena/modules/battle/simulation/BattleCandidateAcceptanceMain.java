package com.zn.agentarena.modules.battle.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.battle.agent.BattleAgentContextBuilder;
import com.zn.agentarena.modules.battle.agent.BattleAiGameRunner;
import com.zn.agentarena.modules.battle.simulation.prompt.BattlePromptExperimentMain;
import com.zn.agentarena.modules.battle.simulation.prompt.BattlePromptScenario;
import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.LangChain4jModelGateway;
import com.zn.agentarena.modules.model.gateway.ModelCallConcurrencyLimiter;
import com.zn.agentarena.modules.model.gateway.ModelEndpointPolicy;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import com.zn.agentarena.modules.model.gateway.OpenAiCompatibleModelFactory;
import com.zn.agentarena.modules.model.gateway.StructuredModelDiagnostics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Candidate 生产架构的一次性验收：12 个受控决策和 5 局 qwen-max 完整比赛。 */
public final class BattleCandidateAcceptanceMain {
    private static final String MODEL = "qwen-max";
    private static final long CONTROL_SEED = 2_100_000L;
    private static final long GAME_SEED = 2_200_000L;

    private BattleCandidateAcceptanceMain() { }

    public static void main(String[] args) throws Exception {
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("DASHSCOPE_API_KEY is required");
        int repeats = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        Path reportPath = Path.of(args.length > 2 ? args[2] : "candidate-decision-architecture-report.md")
                .toAbsolutePath();
        long minimumCallIntervalMillis = args.length > 3 ? Long.parseLong(args[3]) : 3_300L;

        AgentArenaProperties properties = new AgentArenaProperties(null, null, null);
        ModelGateway rawGateway = new LangChain4jModelGateway(
                new OpenAiCompatibleModelFactory(new ModelEndpointPolicy(properties)),
                new ObjectMapper(), new ModelCallConcurrencyLimiter(properties));
        ModelGateway gateway = new StrictThrottledGateway(rawGateway, minimumCallIntervalMillis);
        ModelRuntimeConfig modelConfig = new ModelRuntimeConfig(ModelProvider.QWEN, MODEL, null, key,
                0.4, 900, Duration.ofSeconds(45), 0);
        BattleAiGameRunner runner = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder());

        List<BattleDecisionPipelineTrace> controlled = new ArrayList<>();
        List<BattlePromptScenario> scenarios = List.of(BattlePromptScenario.HEAL, BattlePromptScenario.SEARCH,
                BattlePromptScenario.ATTACK, BattlePromptScenario.REVIVE);
        for (BattlePromptScenario scenario : scenarios) {
            for (int run = 0; run < repeats; run++) {
                long seed = CONTROL_SEED + scenario.ordinal() * 100L + run;
                var state = BattlePromptExperimentMain.state(scenario, seed);
                var trace = runner.runDiagnosticTurn(state, state.findPlayer(1L), modelConfig,
                        scenario.name(), seed, run);
                requireReal(trace);
                controlled.add(trace);
                System.out.printf("controlled scenario=%s run=%d selected=%s resolved=%s executed=%s source=%s%n",
                        scenario, run + 1, trace.context().get("selectedCandidateId"),
                        trace.context().get("resolvedAction"), trace.executedAction(), trace.source());
            }
        }

        BattleSimulationConfig simulationConfig = new BattleSimulationConfig(BattleSimulationMode.LLM_SAMPLE,
                games, 8, 20, GAME_SEED, true);
        List<BattleSimulationResult> completeGames = new BattleSimulationRunner(gateway, modelConfig)
                .run(simulationConfig);
        Files.writeString(reportPath, markdown(controlled, completeGames, repeats), StandardCharsets.UTF_8);
        long fallbacks = completeGames.stream().flatMap(result -> result.events().stream())
                .filter(event -> "AI_DECISION".equals(event.type()))
                .filter(event -> "MODEL_FALLBACK".equals(event.payload().get("source"))).count();
        System.out.printf("acceptance controlled=%d games=%d fallback=%d abnormal=%d report=%s%n",
                controlled.size(), completeGames.size(), fallbacks,
                completeGames.stream().filter(BattleSimulationResult::abnormal).count(), reportPath);
    }

    private static void requireReal(BattleDecisionPipelineTrace trace) {
        if (!"MODEL".equals(trace.source()) || trace.rawOutput() == null || trace.rawOutput().isBlank()) {
            throw new IllegalStateException("受控验收不得使用 fallback 或伪造模型结果: " + trace.scenarioId());
        }
    }

    private static String markdown(List<BattleDecisionPipelineTrace> controlled,
                                   List<BattleSimulationResult> games, int repeats) {
        StringBuilder report = new StringBuilder("# Candidate-based Tactical Decision Architecture 验收报告\n\n");
        report.append("## 架构\n\n`GameState → ObservationSystem → BattleAgentContext → BattleDecisionCandidateGenerator → BattleDecisionCandidateSet → production Prompt → ModelGateway<BattleCandidateDecision> → BattleDecisionCandidateResolver → legality revalidation → BattleRoyaleGameEngine → EngineEvent`\n\n")
                .append("候选是当前玩家当前回合的临时对象，不写入 GameState、Snapshot、Replay 或公开 spectator payload。\n\n")
                .append("## 模型配置\n\n- provider: QWEN\n- model: qwen-max\n- temperature: 0.4\n- maxTokens: 900\n- retries: 0\n- API Key: 仅从进程环境变量读取，未写入代码、trace 或报告\n\n")
                .append("## Controlled Regression\n\n| Scenario | Run | Selected Candidate | Resolved | Executed | Resolution | Reason |\n|---|---:|---|---|---|---|---|\n");
        for (BattleDecisionPipelineTrace trace : controlled) {
            report.append("| ").append(trace.scenarioId()).append(" | ").append(trace.runIndex() + 1)
                    .append(" | ").append(trace.context().get("selectedCandidateId"))
                    .append(" | ").append(trace.context().get("resolvedAction"))
                    .append(" | ").append(trace.executedAction())
                    .append(" | ").append(trace.context().get("candidateResolution"))
                    .append(" | ").append(escape(trace.reasonSummary())).append(" |\n");
        }
        report.append("\n每个场景重复 ").append(repeats).append(" 次；模型只能返回候选 ID，目标和区域均由候选绑定。\n\n")
                .append("## Complete Games\n\n| Seed | Rounds | Winner | Termination | Abnormal |\n|---:|---:|---:|---|---:|\n");
        for (BattleSimulationResult game : games) {
            report.append("| ").append(game.seed()).append(" | ").append(game.rounds()).append(" | ")
                    .append(game.winnerPlayerId()).append(" | ").append(game.terminationReason()).append(" | ")
                    .append(game.abnormal()).append(" |\n");
        }
        List<EngineEvent> decisions = games.stream().flatMap(game -> game.events().stream())
                .filter(event -> "AI_DECISION".equals(event.type())).toList();
        report.append("\n### Tactical Action Distribution\n\n").append(table(distribution(decisions, "action")))
                .append("\n### Social Action Distribution\n\n").append(table(distribution(decisions, "socialAction")))
                .append("\n### Communication Distribution\n\n").append(table(distribution(decisions, "communicationChannel")));
        long invalid = decisions.stream().filter(event -> "INVALID_CANDIDATE_SELECTION".equals(
                event.payload().get("candidateResolution"))).count();
        long stale = decisions.stream().filter(event -> "CANDIDATE_STALE".equals(
                event.payload().get("candidateResolution"))).count();
        long fallback = decisions.stream().filter(event -> "MODEL_FALLBACK".equals(event.payload().get("source"))).count();
        long abnormal = games.stream().filter(BattleSimulationResult::abnormal).count();
        report.append("\n## Stability\n\n- decisions: ").append(decisions.size())
                .append("\n- invalid Candidate: ").append(invalid)
                .append("\n- stale Candidate: ").append(stale)
                .append("\n- model fallback: ").append(fallback)
                .append("\n- abnormal games: ").append(abnormal).append("\n");
        return report.toString();
    }

    private static Map<String, Long> distribution(List<EngineEvent> decisions, String field) {
        return decisions.stream().map(event -> event.payload().get(field))
                .map(value -> value == null ? "NONE" : String.valueOf(value))
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
    }

    private static String table(Map<String, Long> values) {
        StringBuilder table = new StringBuilder("| Value | Count |\n|---|---:|\n");
        values.forEach((value, count) -> table.append("| ").append(value).append(" | ").append(count).append(" |\n"));
        return table.toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|").replaceAll("\\s+", " ");
    }

    /** 验收专用：按账号限流节流，并禁止生产 fallback 被统计成真实模型样本。 */
    private static final class StrictThrottledGateway implements ModelGateway, StructuredModelDiagnostics {
        private final ModelGateway delegate;
        private final long minimumIntervalMillis;
        private long lastCallStartedAt;

        private StrictThrottledGateway(ModelGateway delegate, long minimumIntervalMillis) {
            this.delegate = delegate;
            this.minimumIntervalMillis = Math.max(0L, minimumIntervalMillis);
        }

        @Override
        public String generate(ModelRuntimeConfig config, String prompt) {
            awaitTurn();
            try {
                return delegate.generate(config, prompt);
            } catch (RuntimeException failure) {
                throw new AssertionError("REAL_MODEL_ACCEPTANCE_UNAVAILABLE", failure);
            }
        }

        @Override
        public <T> T generateStructured(ModelRuntimeConfig config, String prompt, Class<T> responseType) {
            awaitTurn();
            try {
                return delegate.generateStructured(config, prompt, responseType);
            } catch (RuntimeException failure) {
                throw new AssertionError("REAL_MODEL_ACCEPTANCE_UNAVAILABLE", failure);
            }
        }

        private synchronized void awaitTurn() {
            long waitMillis = minimumIntervalMillis - (System.currentTimeMillis() - lastCallStartedAt);
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("REAL_MODEL_ACCEPTANCE_INTERRUPTED", interrupted);
                }
            }
            lastCallStartedAt = System.currentTimeMillis();
        }

        @Override
        public String lastRawResponse() {
            return delegate instanceof StructuredModelDiagnostics diagnostics
                    ? diagnostics.lastRawResponse() : null;
        }

        @Override
        public boolean lastParserRepair() {
            return delegate instanceof StructuredModelDiagnostics diagnostics
                    && diagnostics.lastParserRepair();
        }
    }
}
