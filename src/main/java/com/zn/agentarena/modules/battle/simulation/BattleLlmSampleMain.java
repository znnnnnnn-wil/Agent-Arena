package com.zn.agentarena.modules.battle.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.battle.agent.BattleAiDecision;
import com.zn.agentarena.modules.battle.simulation.trace.BattleAgentDecisionTrace;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import com.zn.agentarena.modules.model.gateway.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** Explicit, credential-safe entry point for paid LLM_SAMPLE experiments. */
public final class BattleLlmSampleMain {
    private static final String MODEL_NAME = "qwen-turbo";

    private BattleLlmSampleMain() {}

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DASHSCOPE_API_KEY is required");
        }
        AgentArenaProperties properties = new AgentArenaProperties(null, null, null);
        ModelGateway gateway = new LangChain4jModelGateway(
                new OpenAiCompatibleModelFactory(new ModelEndpointPolicy(properties)),
                new ObjectMapper(), new ModelCallConcurrencyLimiter(properties));
        ModelRuntimeConfig runtime = new ModelRuntimeConfig(ModelProvider.QWEN, MODEL_NAME, null, apiKey,
                0.4, 900, Duration.ofSeconds(45), 0);

        if (args.length > 0 && "probe".equalsIgnoreCase(args[0])) {
            BattleAiDecision decision = gateway.generateStructured(runtime, """
                    只返回一个合法 JSON 对象，不要 Markdown。字段为 action,targetPlayerId,targetRegion,
                    socialAction,communication,reasonSummary,confidence。action 必须为 SKIP，其他复杂字段为 null，
                    reasonSummary 为不超过20字的中文摘要，confidence 为 0 到 100 的整数。
                    """, BattleAiDecision.class);
            if (decision == null || decision.action() == null) throw new IllegalStateException("probe returned no action");
            System.out.println("LLM probe ok: model=" + MODEL_NAME + ", action=" + decision.action()
                    + ", confidence=" + decision.confidence());
            return;
        }

        int games = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        if (games < 1 || games > 20) throw new IllegalArgumentException("LLM_SAMPLE games must be 1..20");
        Path output = Path.of(args.length > 1 ? args[1] : "llm-sample-report.md").toAbsolutePath();
        long firstSeed = args.length > 2 ? Long.parseLong(args[2]) : 500_000L;
        BattleSimulationConfig config = new BattleSimulationConfig(BattleSimulationMode.LLM_SAMPLE,
                games, 8, 20, firstSeed, true);
        List<BattleSimulationResult> results = new BattleSimulationRunner(gateway, runtime).run(config);
        Files.writeString(output, report(config, results), StandardCharsets.UTF_8);
        long model = traces(results).stream().filter(t -> "MODEL".equals(t.decision().source())).count();
        long fallback = traces(results).stream().filter(t -> "MODEL_FALLBACK".equals(t.decision().source())).count();
        System.out.println("LLM_SAMPLE games=" + games + ", modelDecisions=" + model + ", fallbacks=" + fallback
                + ", abnormal=" + results.stream().filter(BattleSimulationResult::abnormal).count()
                + ", report=" + output);
    }

    private static String report(BattleSimulationConfig config, List<BattleSimulationResult> results) {
        List<BattleAgentDecisionTrace> traces = traces(results);
        long model = traces.stream().filter(t -> "MODEL".equals(t.decision().source())).count();
        long fallback = traces.stream().filter(t -> "MODEL_FALLBACK".equals(t.decision().source())).count();
        long abnormal = results.stream().filter(BattleSimulationResult::abnormal).count();
        Map<String,Long> actions = traces.stream().collect(Collectors.groupingBy(
                t -> nullSafe(t.decision().action()), TreeMap::new, Collectors.counting()));
        Map<String,Long> social = traces.stream().filter(t -> t.decision().socialAction() != null)
                .collect(Collectors.groupingBy(t -> t.decision().socialAction(), TreeMap::new, Collectors.counting()));
        Map<String,Long> communication = traces.stream().filter(t -> t.decision().communicationChannel() != null)
                .collect(Collectors.groupingBy(t -> t.decision().communicationChannel(), TreeMap::new, Collectors.counting()));
        Map<String,Long> events = results.stream().flatMap(r -> r.events().stream())
                .collect(Collectors.groupingBy(EngineEvent::type, TreeMap::new, Collectors.counting()));
        long acceptedVisible = traces.stream().filter(t -> !t.agentKnownState().acceptedPromises().isEmpty()).count();
        long promiseMentioned = traces.stream().filter(t -> !t.agentKnownState().acceptedPromises().isEmpty())
                .filter(t -> mentions(t.decision().reasonSummary(), "承诺", "promise", "履约")).count();
        long commitmentIgnored = traces.stream().filter(t -> !t.agentKnownState().acceptedPromises().isEmpty())
                .filter(t -> t.serverTruth().promiseOpportunities().stream().anyMatch(x -> x.legalFulfillmentOpportunity()))
                .filter(t -> !"FULFILL_PROMISE".equals(t.decision().socialAction())).count();
        BattleBehaviorDiagnosticMetrics metrics = new BattleBehaviorDiagnosticMetrics(results);
        var promise = metrics.promiseFunnel();
        var objective = metrics.objectiveFunnel();
        var medic = metrics.fieldMedicFunnel();
        var broker = metrics.informationBrokerFunnel();
        var rescue = metrics.rescueFunnel();

        StringBuilder b = new StringBuilder("# Agent Arena 真实 LLM_SAMPLE 报告\n\n");
        b.append("## 配置\n\n- provider: QWEN / 百炼 OpenAI-compatible\n- model: ").append(MODEL_NAME)
                .append("\n- games: ").append(config.games()).append("\n- seeds: ").append(config.firstSeed())
                .append("–").append(config.firstSeed()+config.games()-1).append("\n- max rounds: ")
                .append(config.maxRounds()).append("\n- API Key: 仅进程环境变量，未写入文件或日志\n\n");
        b.append("## 调用真实性\n\n- MODEL decisions: ").append(model).append("\n- MODEL_FALLBACK: ")
                .append(fallback).append("\n- abnormal games: ").append(abnormal)
                .append("\n- real-model ratio: ").append(percent(model, model+fallback)).append("\n\n");
        b.append("## 决策分布\n\n- tactical: ").append(actions).append("\n- social: ").append(social)
                .append("\n- communication: ").append(communication).append("\n\n");
        b.append("## 关键社会事件\n\n");
        for (String type : List.of("BATTLE_ALLIANCE_FORMED","BATTLE_ALLIANCE_BETRAYED","BATTLE_PROMISE_ACCEPTED",
                "BATTLE_PROMISE_FULFILLED","BATTLE_PROMISE_BROKEN","BATTLE_PLAYER_REVIVED","BATTLE_RUMOR_SHARED",
                "BATTLE_INFORMATION_TRADE_COMPLETED","BATTLE_OBJECTIVE_COMPLETED")) {
            b.append("- ").append(type).append(": ").append(events.getOrDefault(type,0L)).append("\n");
        }
        b.append("\n## Promise 与显著性\n\n- Accepted: ").append(promise.accepted()).append("\n- Fulfilled: ")
                .append(promise.fulfilled()).append("\n- Broken: ").append(promise.broken())
                .append("\n- legal opportunities: ").append(promise.legalOpportunity())
                .append("\n- legal but not fulfilled: ").append(promise.legalButDidNotFulfill())
                .append("\n- decisions whose Context contained Accepted Promise: ").append(acceptedVisible)
                .append("\n- reasonSummary mentioned promise/commitment: ").append(promiseMentioned)
                .append(" (").append(percent(promiseMentioned,acceptedVisible)).append(")")
                .append("\n- legal opportunity ignored in decision: ").append(commitmentIgnored).append("\n\n");
        b.append("## 其他漏斗\n\n- Objective completed: ").append(objective.completed()).append("/")
                .append(objective.objectives()).append("; average completion ").append(percent(objective.averageCompletion(),1))
                .append("\n- FIELD_MEDIC completed: ").append(medic.completed()).append("/").append(medic.assigned())
                .append("; legal opportunities ").append(medic.legalOpportunities()).append("; decisions ").append(medic.decisions())
                .append("\n- INFORMATION_BROKER completed: ").append(broker.completed()).append("/").append(broker.assigned())
                .append("; opportunities ").append(broker.legalOpportunities()).append("; proposals ").append(broker.decisions())
                .append("\n- Rescue: ").append(rescue.revived()).append("/").append(rescue.downed())
                .append("; choices/opportunities ").append(rescue.reviveChoices()).append("/").append(rescue.legalObserverOpportunities())
                .append("\n\n");
        b.append("## 代表性对局索引\n\n");
        results.stream().sorted(Comparator.comparingLong(r -> -socialEventCount(r))).limit(Math.min(5,results.size()))
                .forEach(r -> b.append("- seed ").append(r.seed()).append(": rounds=").append(r.rounds())
                        .append(", winner=").append(r.winnerPlayerId()).append(", socialEvents=")
                        .append(socialEventCount(r)).append(", modelDecisions=")
                        .append(r.decisionTraces().stream().filter(t -> "MODEL".equals(t.decision().source())).count())
                        .append("\n"));
        b.append("\n完整逐决策 reasonSummary、Agent 知情状态与 server truth 保存在本次运行的内存结果中；报告只输出聚合，避免泄露凭证或大对象。\n");
        return b.toString();
    }

    private static List<BattleAgentDecisionTrace> traces(List<BattleSimulationResult> results) {
        return results.stream().flatMap(r -> r.decisionTraces().stream()).toList();
    }
    private static boolean mentions(String value, String... terms) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(terms).anyMatch(lower::contains);
    }
    private static long socialEventCount(BattleSimulationResult result) {
        return result.events().stream().filter(e -> e.type().contains("ALLIANCE") || e.type().contains("PROMISE")
                || e.type().contains("TRADE") || e.type().contains("RUMOR") || e.type().contains("OBJECTIVE")
                || e.type().contains("REVIVED")).count();
    }
    private static String nullSafe(String value) { return value == null ? "NONE" : value; }
    private static String percent(double numerator, double denominator) {
        return denominator == 0 ? "0.00%" : String.format(Locale.ROOT,"%.2f%%",numerator*100/denominator);
    }
}
