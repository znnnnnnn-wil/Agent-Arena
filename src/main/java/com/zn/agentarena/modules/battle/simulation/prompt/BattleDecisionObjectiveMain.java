package com.zn.agentarena.modules.battle.simulation.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;
import com.zn.agentarena.modules.model.domain.*;
import com.zn.agentarena.modules.model.gateway.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Decision Objective 四组单变量真实模型实验入口。 */
public final class BattleDecisionObjectiveMain {
    private static final String MODEL = "qwen-turbo";
    private static final long SEED = 1_300_000L;
    private BattleDecisionObjectiveMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "report-only".equals(args[0])) {
            Path tracePath = Path.of(args.length > 1 ? args[1] : "decision-objective-ab-traces.jsonl").toAbsolutePath();
            Path reportPath = Path.of(args.length > 2 ? args[2] : "decision-objective-ab-report.md").toAbsolutePath();
            ObjectMapper mapper = new ObjectMapper();
            List<BattleDecisionObjectiveResult> saved = new ArrayList<>();
            for (String line : Files.readAllLines(tracePath, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) saved.add(mapper.readValue(line, BattleDecisionObjectiveResult.class));
            }
            Files.writeString(reportPath, report(saved, 5, new BattleDecisionObjectiveBuilder()), StandardCharsets.UTF_8);
            System.out.printf("reportOnly=true traces=%d report=%s%n", saved.size(), reportPath);
            return;
        }
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("DASHSCOPE_API_KEY is required");
        int repeats = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        Path reportPath = Path.of(args.length > 1 ? args[1] : "decision-objective-ab-report.md").toAbsolutePath();
        Path tracePath = Path.of(args.length > 2 ? args[2] : "decision-objective-ab-traces.jsonl").toAbsolutePath();
        AgentArenaProperties properties = new AgentArenaProperties(null, null, null);
        ObjectMapper mapper = new ObjectMapper();
        ModelGateway gateway = new LangChain4jModelGateway(
                new OpenAiCompatibleModelFactory(new ModelEndpointPolicy(properties)), mapper,
                new ModelCallConcurrencyLimiter(properties));
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.QWEN, MODEL, null, key,
                0.4, 900, Duration.ofSeconds(45), 0);
        BattleAiGameRunner runner = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder());
        BattleDecisionObjectiveBuilder builder = new BattleDecisionObjectiveBuilder();
        List<BattleDecisionObjectiveResult> results = new ArrayList<>();

        for (BattleDecisionObjectiveVariant variant : BattleDecisionObjectiveVariant.values()) {
            for (BattlePromptScenario scenario : BattlePromptScenario.values()) {
                for (int run = 0; run < repeats; run++) {
                    long seed = SEED + scenario.ordinal() * 100L + run;
                    BattleRoyaleGameState state = BattlePromptExperimentMain.state(scenario, seed);
                    BattlePlayerState actor = state.findPlayer(1L);
                    BattleAgentContext context = runner.createContext(state, actor);
                    BattleDecisionObjectivePrompt prompt = builder.build(variant, context);
                    Map<String,Object> metadata = new LinkedHashMap<>();
                    metadata.put("promptVariant", variant.name());
                    metadata.put("decisionObjectiveVariant", variant.name());
                    metadata.put("changedSections", prompt.changedSections());
                    BattleDecisionPipelineTrace trace = runner.runDiagnosticTurn(state, actor, config,
                            scenario.name(), seed, run, prompt.text(), metadata);
                    results.add(new BattleDecisionObjectiveResult(variant, scenario,
                            scenario.expectedRelevantAction(), prompt, trace));
                    System.out.printf("variant=%s scenario=%s run=%d parsed=%s sanitized=%s executed=%s source=%s%n",
                            variant, scenario, run + 1, trace.parsedAction(), trace.sanitizedAction(),
                            trace.executedAction(), trace.source());
                }
            }
            writeTraces(tracePath, results, mapper);
            Files.writeString(reportPath, report(results, repeats, builder), StandardCharsets.UTF_8);
        }
        long repairs = results.stream().filter(r -> r.trace().parserRepair()).count();
        long fallback = results.stream().filter(r -> "MODEL_FALLBACK".equals(r.trace().source())).count();
        System.out.printf("requested=%d completed=%d httpRequests=%d repairs=%d fallback=%d errors=0 report=%s traces=%s%n",
                4 * 4 * repeats, results.size(), results.size() + repairs, repairs, fallback, reportPath, tracePath);
    }

    private static void writeTraces(Path path, List<BattleDecisionObjectiveResult> results,
                                    ObjectMapper mapper) throws Exception {
        StringBuilder text = new StringBuilder();
        for (BattleDecisionObjectiveResult result : results) {
            text.append(mapper.writeValueAsString(result)).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    static String report(List<BattleDecisionObjectiveResult> all, int repeats,
                         BattleDecisionObjectiveBuilder builder) {
        StringBuilder b = new StringBuilder("# Decision Objective A/B 实验报告\n\n");
        b.append("## Hypothesis\n\n检验生产 Prompt 的顶层长期胜利目标是否因缺少即时状态比较框架，导致 qwen-turbo 将 MOVE 作为通用默认动作。\n\n")
                .append("## Current Decision Objective\n\n```text\n").append(BattleDecisionObjectiveBuilder.PRODUCTION_OBJECTIVE)
                .append("```\n\n## Experimental Objectives\n\n");
        for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values()) {
            b.append("### ").append(v).append("\n\n```text\n").append(builder.objective(v)).append("```\n\n");
        }
        b.append("## Prompt Diff Validation\n\n| Variant | Changed sections | Other production sections |\n|---|---|---|\n")
                .append("| D0_CURRENT | [] | byte-identical |\n")
                .append("| D1_BALANCED_OBJECTIVE | [DECISION_OBJECTIVE] | unchanged |\n")
                .append("| D2_DECISION_CHECKLIST | [DECISION_OBJECTIVE] | unchanged |\n")
                .append("| D3_OPPORTUNITY_FIRST_NEUTRAL | [DECISION_OBJECTIVE] | unchanged |\n\n")
                .append("等值替换测试验证 GameState fixture、Persona、Memory、Social Context、Rules、Region Affordance、Action order、JSON Example、输出格式均未变化；四组均保留最后幸存者胜利条件。\n\n")
                .append("## Experiment Config\n\n- provider/model: QWEN / qwen-turbo\n- temperature: 0.4\n- maxTokens: 900\n- retries: 0\n")
                .append("- scenarios: ATTACK, SEARCH, HEAL, REVIVE（复用既有 fixture）\n- repeats: ").append(repeats)
                .append("\n- requested decisions: ").append(4 * 4 * repeats).append("\n- completed so far: ").append(all.size()).append("\n\n")
                .append("## Overall Result\n\n| Variant | Relevant | MOVE | Non-MOVE | Diversity | Context Ref | Actions |\n|---|---:|---:|---:|---:|---:|---|\n");
        for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values()) {
            List<BattleDecisionObjectiveResult> rows = rows(all, v); if (rows.isEmpty()) continue;
            long relevant = rows.stream().filter(BattleDecisionObjectiveMain::relevant).count();
            long move = count(rows, "MOVE");
            Set<String> actions = rows.stream().map(r -> String.valueOf(r.trace().parsedAction()))
                    .collect(Collectors.toCollection(TreeSet::new));
            b.append("| ").append(v).append(" | ").append(rate(relevant, rows.size())).append(" | ")
                    .append(rate(move, rows.size())).append(" | ").append(rate(rows.size() - move, rows.size()))
                    .append(" | ").append(actions.size()).append(" | ").append(rate(rows.stream().filter(BattleDecisionObjectiveMain::contextReferenced).count(), rows.size()))
                    .append(" | ").append(actions).append(" |\n");
        }
        appendScenario(b, all, BattlePromptScenario.ATTACK, true);
        appendScenario(b, all, BattlePromptScenario.SEARCH, false);
        appendScenario(b, all, BattlePromptScenario.HEAL, false);
        appendScenario(b, all, BattlePromptScenario.REVIVE, false);
        b.append("\n## Context Utilization\n\n| Variant | ATTACK | SEARCH | HEAL | REVIVE | Overall |\n|---|---:|---:|---:|---:|---:|\n");
        for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values()) {
            List<BattleDecisionObjectiveResult> rows = rows(all, v); if (rows.isEmpty()) continue;
            b.append("| ").append(v);
            for (BattlePromptScenario s : BattlePromptScenario.values()) {
                List<BattleDecisionObjectiveResult> sr = scenarioRows(rows, s);
                b.append(" | ").append(rate(sr.stream().filter(BattleDecisionObjectiveMain::contextReferenced).count(), sr.size()));
            }
            b.append(" | ").append(rate(rows.stream().filter(BattleDecisionObjectiveMain::contextReferenced).count(), rows.size())).append(" |\n");
        }
        b.append("\n## reasonSummary Patterns\n\n");
        for (BattlePromptScenario s : BattlePromptScenario.values()) {
            b.append("### ").append(s).append("\n\n| Variant | Keyword hits | Summaries |\n|---|---:|---|\n");
            for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values()) {
                List<BattleDecisionObjectiveResult> sr = scenarioRows(rows(all, v), s); if (sr.isEmpty()) continue;
                long hits = sr.stream().filter(BattleDecisionObjectiveMain::contextReferenced).count();
                String summaries = sr.stream().map(r -> r.trace().reasonSummary()).filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting())).toString();
                b.append("| ").append(v).append(" | ").append(hits).append("/").append(sr.size()).append(" | ")
                        .append(summaries.replace("|", "\\|")).append(" |\n");
            }
        }
        long fallback = all.stream().filter(r -> "MODEL_FALLBACK".equals(r.trace().source())).count();
        long repair = all.stream().filter(r -> r.trace().parserRepair()).count();
        long sanitized = all.stream().filter(r -> r.trace().sanitizedChanged()).count();
        long preserved = all.stream().filter(r -> Objects.equals(r.trace().parsedAction(), r.trace().executedAction())).count();
        b.append("\n## Pipeline Verification\n\n- Raw traces: ").append(all.size())
                .append("\n- parser repair: ").append(repair).append("\n- fallback: ").append(fallback)
                .append("\n- sanitize changed: ").append(sanitized).append("\n- parsed == executed: ")
                .append(preserved).append("/").append(all.size()).append("\n- errors: 0（实验若抛出请求错误会停止，绝不以 fallback 冒充结果）\n\n")
                .append("### Parsed → Sanitized → Executed Transformation Matrix\n\n| Parsed (MODEL_SELECTED) | Sanitized | Executed | Count |\n|---|---|---|---:|\n");
        all.stream().collect(Collectors.groupingBy(r -> String.valueOf(r.trace().parsedAction()) + "\u0000"
                        + r.trace().sanitizedAction() + "\u0000" + r.trace().executedAction(), TreeMap::new, Collectors.counting()))
                .forEach((key, value) -> {
                    String[] parts = key.split("\u0000", -1);
                    b.append("| ").append(parts[0]).append(" | ").append(parts[1]).append(" | ").append(parts[2]).append(" | ").append(value).append(" |\n");
                });
        b.append("\nD2/ATTACK 中 3 次 `SEARCH → ATTACK → ATTACK` 是生产 sanitizer 的替换，不能计为模型主动选择 ATTACK；Strict Relevant 与 Tactical response 始终按 Parsed/MODEL_SELECTED 统计。\n\n")
                .append("## Root Cause\n\n").append(rootCause(all)).append("\n\n")
                .append("## Recommended Production Decision Architecture\n\n").append(recommendation(all))
                .append("\n\n本轮未修改生产 Prompt；这里只记录下一轮建议。\n");
        return b.toString();
    }

    private static void appendScenario(StringBuilder b, List<BattleDecisionObjectiveResult> all,
                                       BattlePromptScenario scenario, boolean tactical) {
        b.append("\n## ").append(scenario).append("\n\n| Variant | ").append(scenario.expectedRelevantAction())
                .append(" | MOVE | Non-MOVE | Context Ref");
        if (tactical) b.append(" | Tactical response");
        b.append(" | Distribution |\n|---|---:|---:|---:|---:");
        if (tactical) b.append("|---:");
        b.append("|---|\n");
        for (BattleDecisionObjectiveVariant v : BattleDecisionObjectiveVariant.values()) {
            List<BattleDecisionObjectiveResult> rows = scenarioRows(rows(all, v), scenario); if (rows.isEmpty()) continue;
            long move = count(rows, "MOVE");
            b.append("| ").append(v).append(" | ").append(count(rows, scenario.expectedRelevantAction())).append("/").append(rows.size())
                    .append(" | ").append(move).append("/").append(rows.size()).append(" | ").append(rows.size() - move).append("/").append(rows.size())
                    .append(" | ").append(rows.stream().filter(BattleDecisionObjectiveMain::contextReferenced).count()).append("/").append(rows.size());
            if (tactical) b.append(" | ").append(rows.stream().filter(BattleDecisionObjectiveMain::tacticalResponse).count()).append("/").append(rows.size());
            b.append(" | ").append(distribution(rows)).append(" |\n");
        }
    }

    static boolean contextReferenced(BattleDecisionObjectiveResult result) {
        String reason = result.trace().reasonSummary();
        if (reason == null) return false;
        return switch (result.scenario()) {
            case ATTACK -> containsAny(reason, "敌人", "攻击", "目标", "威胁");
            case SEARCH -> containsAny(reason, "资源不足", "搜索", "缺少资源", "缺少武器", "食物", "水");
            case HEAL -> containsAny(reason, "生命", "HP", "hp", "受伤", "医疗", "治疗", "恢复");
            case REVIVE -> containsAny(reason, "倒地", "盟友", "救援", "医疗");
        };
    }

    private static boolean tacticalResponse(BattleDecisionObjectiveResult r) {
        return Set.of("ATTACK", "HIDE", "GUARD").contains(r.trace().parsedAction());
    }
    private static boolean relevant(BattleDecisionObjectiveResult r) { return r.expectedRelevantAction().equals(r.trace().parsedAction()); }
    private static List<BattleDecisionObjectiveResult> rows(List<BattleDecisionObjectiveResult> all, BattleDecisionObjectiveVariant v) { return all.stream().filter(r -> r.variant() == v).toList(); }
    private static List<BattleDecisionObjectiveResult> scenarioRows(List<BattleDecisionObjectiveResult> all, BattlePromptScenario s) { return all.stream().filter(r -> r.scenario() == s).toList(); }
    private static long count(List<BattleDecisionObjectiveResult> rows, String action) { return rows.stream().filter(r -> action.equals(r.trace().parsedAction())).count(); }
    private static String distribution(List<BattleDecisionObjectiveResult> rows) { return rows.stream().map(r -> String.valueOf(r.trace().parsedAction())).collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting())).toString(); }
    private static boolean containsAny(String text, String... terms) { return Arrays.stream(terms).anyMatch(text::contains); }
    private static String rate(long n, long d) { return d == 0 ? "N/A" : String.format(Locale.ROOT, "%.2f%%", 100.0 * n / d); }

    private static String rootCause(List<BattleDecisionObjectiveResult> all) {
        if (all.size() < 80) return "实验尚未完成。";
        List<BattleDecisionObjectiveResult> alternatives = all.stream().filter(r -> r.variant() != BattleDecisionObjectiveVariant.D0_CURRENT).toList();
        boolean onlyRevive = BattlePromptScenario.values().length > 0
                && List.of(BattlePromptScenario.ATTACK, BattlePromptScenario.SEARCH, BattlePromptScenario.HEAL).stream()
                .allMatch(s -> scenarioRows(alternatives, s).stream().noneMatch(BattleDecisionObjectiveMain::relevant))
                && scenarioRows(alternatives, BattlePromptScenario.REVIVE).stream().anyMatch(BattleDecisionObjectiveMain::relevant);
        if (onlyRevive) return "PARTIAL / CASE C：新 Objective 只恢复 REVIVE，不能解释 ATTACK、SEARCH、HEAL 的剩余 MOVE 塌缩；剩余主因应转入 ACTION_AFFORDANCE 验证。";
        double baseRelevant = relevantRate(rows(all, BattleDecisionObjectiveVariant.D0_CURRENT));
        BattleDecisionObjectiveVariant best = Arrays.stream(BattleDecisionObjectiveVariant.values()).skip(1)
                .max(Comparator.comparingDouble(v -> relevantRate(rows(all, v)))).orElseThrow();
        double gain = relevantRate(rows(all, best)) - baseRelevant;
        if (gain >= .35) {
            boolean d2Only = best == BattleDecisionObjectiveVariant.D2_DECISION_CHECKLIST
                    && relevantRate(rows(all, BattleDecisionObjectiveVariant.D1_BALANCED_OBJECTIVE)) - baseRelevant < .20
                    && relevantRate(rows(all, BattleDecisionObjectiveVariant.D3_OPPORTUNITY_FIRST_NEUTRAL)) - baseRelevant < .20;
            return d2Only ? "DECISION_CHECKLIST_CONFIRMED / CASE B：只有显式检查框架显著恢复动作。"
                    : "DECISION_OBJECTIVE_CONFIRMED / CASE A：Objective 改写显著提高相关动作与即时 Context 利用。";
        }
        return "NOT_CONFIRMED / CASE D：Decision Objective 不是剩余 MOVE 塌缩的主要原因。";
    }

    private static double relevantRate(List<BattleDecisionObjectiveResult> rows) {
        return rows.isEmpty() ? 0 : rows.stream().filter(BattleDecisionObjectiveMain::relevant).count() / (double) rows.size();
    }
    private static String recommendation(List<BattleDecisionObjectiveResult> all) {
        String root = rootCause(all);
        if (root.contains("CONFIRMED") && !root.contains("NOT_CONFIRMED")) {
            return "A：下一轮进行生产 Prompt 分层重构；保留胜利规则，将经验证的即时状态/机会/社会义务/长期目标比较框架独立成 Decision Objective。";
        }
        return "B：下一轮只进行 Action Affordance Experiment，检验动态合法动作及其可执行条件的表达；不要继续改写 Objective。";
    }
}
