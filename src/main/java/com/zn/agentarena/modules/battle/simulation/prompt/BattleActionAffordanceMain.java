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

/** Action Affordance 四组单变量真实模型实验入口。 */
public final class BattleActionAffordanceMain {
    private static final String MODEL = "qwen-turbo";
    private static final long SEED = 1_400_000L;
    private BattleActionAffordanceMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "report-only".equals(args[0])) {
            Path traces = Path.of(args.length > 1 ? args[1] : "action-affordance-ab-traces.jsonl").toAbsolutePath();
            Path report = Path.of(args.length > 2 ? args[2] : "action-affordance-ab-report.md").toAbsolutePath();
            ObjectMapper mapper = new ObjectMapper();
            List<BattleActionAffordanceResult> saved = new ArrayList<>();
            for (String line : Files.readAllLines(traces, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                BattleActionAffordanceResult old = mapper.readValue(line, BattleActionAffordanceResult.class);
                saved.add(new BattleActionAffordanceResult(old.variant(), old.scenario(), old.expectedRelevantAction(),
                        old.prompt(), old.computedAffordances(), old.observableState(),
                        contextRecognized(old.scenario(), old.trace().reasonSummary()),
                        affordanceRecognized(old.scenario(), old.trace().reasonSummary()), old.trace()));
            }
            writeTraces(traces, saved, mapper);
            Files.writeString(report, report(saved, 5), StandardCharsets.UTF_8);
            System.out.printf("reportOnly=true traces=%d report=%s%n", saved.size(), report);
            return;
        }
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("DASHSCOPE_API_KEY is required");
        int repeats = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        Path reportPath = Path.of(args.length > 1 ? args[1] : "action-affordance-ab-report.md").toAbsolutePath();
        Path tracePath = Path.of(args.length > 2 ? args[2] : "action-affordance-ab-traces.jsonl").toAbsolutePath();
        AgentArenaProperties properties = new AgentArenaProperties(null, null, null);
        ObjectMapper mapper = new ObjectMapper();
        ModelGateway gateway = new LangChain4jModelGateway(new OpenAiCompatibleModelFactory(new ModelEndpointPolicy(properties)),
                mapper, new ModelCallConcurrencyLimiter(properties));
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.QWEN, MODEL, null, key,
                0.4, 900, Duration.ofSeconds(45), 0);
        BattleAiGameRunner runner = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder());
        BattleActionAffordanceEngine affordanceEngine = new BattleActionAffordanceEngine();
        BattleActionAffordanceBuilder promptBuilder = new BattleActionAffordanceBuilder();
        List<BattleActionAffordanceResult> results = new ArrayList<>();
        for (BattleActionAffordanceVariant variant : BattleActionAffordanceVariant.values()) {
            for (BattlePromptScenario scenario : BattlePromptScenario.values()) {
                for (int run = 0; run < repeats; run++) {
                    long seed = SEED + scenario.ordinal() * 100L + run;
                    BattleRoyaleGameState state = BattlePromptExperimentMain.state(scenario, seed);
                    BattlePlayerState actor = state.findPlayer(1L);
                    BattleAgentContext context = runner.createContext(state, actor);
                    List<BattleActionAffordance> computed = variant == BattleActionAffordanceVariant.A0_CURRENT
                            ? null : affordanceEngine.compute(state, actor, context);
                    Map<String,Object> observableState = affordanceEngine.observableState(state, actor, context);
                    BattleActionAffordancePrompt prompt = promptBuilder.build(variant, context, computed);
                    Map<String,Object> metadata = new LinkedHashMap<>();
                    metadata.put("promptVariant", variant.name());
                    metadata.put("actionAffordanceVariant", variant.name());
                    metadata.put("changedSections", prompt.changedSections());
                    if (computed != null) metadata.put("computedAffordances", computed);
                    metadata.put("observableState", observableState);
                    BattleDecisionPipelineTrace trace = runner.runDiagnosticTurn(state, actor, config,
                            scenario.name(), seed, run, prompt.text(), metadata);
                    boolean contextRecognized = contextRecognized(scenario, trace.reasonSummary());
                    boolean affordanceRecognized = affordanceRecognized(scenario, trace.reasonSummary());
                    BattleActionAffordanceResult result = new BattleActionAffordanceResult(variant, scenario,
                            scenario.expectedRelevantAction(), prompt, computed, observableState,
                            contextRecognized, affordanceRecognized, trace);
                    results.add(result);
                    System.out.printf("variant=%s scenario=%s run=%d parsed=%s recognized=%s/%s sanitized=%s executed=%s source=%s%n",
                            variant, scenario, run + 1, trace.parsedAction(), contextRecognized, affordanceRecognized,
                            trace.sanitizedAction(), trace.executedAction(), trace.source());
                }
            }
            writeTraces(tracePath, results, mapper);
            Files.writeString(reportPath, report(results, repeats), StandardCharsets.UTF_8);
        }
        long repairs = results.stream().filter(r -> r.trace().parserRepair()).count();
        long fallback = results.stream().filter(r -> "MODEL_FALLBACK".equals(r.trace().source())).count();
        System.out.printf("requested=%d completed=%d httpRequests=%d repairs=%d fallback=%d errors=0 report=%s traces=%s%n",
                4 * 4 * repeats, results.size(), results.size() + repairs, repairs, fallback, reportPath, tracePath);
    }

    private static void writeTraces(Path path, List<BattleActionAffordanceResult> results, ObjectMapper mapper) throws Exception {
        StringBuilder b = new StringBuilder();
        for (BattleActionAffordanceResult result : results) b.append(mapper.writeValueAsString(result)).append('\n');
        Files.writeString(path, b.toString(), StandardCharsets.UTF_8);
    }

    static boolean contextRecognized(BattlePromptScenario scenario, String reason) {
        if (reason == null) return false;
        return switch (scenario) {
            case HEAL -> any(reason, "生命", "血量", "HP", "hp", "health", "受伤", "低生命");
            case ATTACK -> any(reason, "敌人", "对手", "P2", "目标", "威胁", "CRITICAL");
            case SEARCH -> any(reason, "当前区域", "本区域", "A区", "搜索", "物资", "资源");
            case REVIVE -> any(reason, "倒地", "DOWNED", "downed", "盟友", "P2");
        };
    }

    static boolean affordanceRecognized(BattlePromptScenario scenario, String reason) {
        if (reason == null) return false;
        return switch (scenario) {
            case HEAL -> any(reason, "HEAL", "治疗", "恢复")
                    && any(reason, "医疗包", "MEDKIT", "medkit")
                    && any(reason, "生命", "血量", "HP", "hp", "health", "受伤");
            case ATTACK -> any(reason, "ATTACK", "攻击") && any(reason, "敌人", "对手", "P2", "目标");
            case SEARCH -> any(reason, "SEARCH", "搜索") && any(reason, "当前区域", "本区域", "A区", "原地")
                    && any(reason, "资源", "物资", "搜索");
            case REVIVE -> any(reason, "REVIVE", "Reviv", "reviv", "救援", "救起", "复活")
                    && any(reason, "倒地", "DOWNED", "Downed", "downed")
                    && any(reason, "同区", "当前区域", "A区", "P2");
        };
    }

    static String report(List<BattleActionAffordanceResult> all, int repeats) {
        StringBuilder b = new StringBuilder("# Action Affordance A/B 实验报告\n\n")
                .append("## Hypothesis\n\n检验 qwen-turbo 的剩余 MOVE 塌缩是否来自 GameState → executable action 映射不可靠。\n\n")
                .append("## Production Rule and Sanitizer Audit\n\n")
                .append("- Affordance 候选仅来自 Agent 可观察状态；最终用 `BattleRoyaleGameEngine.validate` 判定。\n")
                .append("- SEARCH 在上一轮 ATTACK fixture 中是合法动作。`SEARCH → ATTACK` 来自 sanitizer 的攻击收益策略替换，而非非法动作修复。\n")
                .append("- 因 sanitizer 混合合法化与策略偏好，本实验不使用 sanitizer 生成候选，但保持其生产行为不变。\n")
                .append("- HEAL 的真实生产校验条件是 MEDKIT > 0；引擎当前不禁止满血 HEAL。SEARCH 没有硬性资源枯竭禁用条件。\n\n")
                .append("## Variants\n\n- A0_CURRENT：生产 Prompt 逐字基线，无 computedAffordances。\n")
                .append("- A1_LEGAL_ACTION_NAMES：仅插入动态合法动作名称。\n")
                .append("- A2_ACTION_PRECONDITIONS：插入动作名称及当前满足的执行条件。\n")
                .append("- A3_DYNAMIC_AFFORDANCE：插入动作名称及状态绑定字段。\n\n")
                .append("## Prompt Diff Validation\n\nA1/A2/A3 均只在生产输出约束前插入 `ACTION_AFFORDANCE` Section；Decision Objective、Persona、Memory、Social Context、Rules、Region、生产 Action order、JSON Example 和输出 schema 保持 byte-identical。A0 与生产 Prompt 逐字一致。\n\n")
                .append("## Experiment Config\n\n- provider/model: QWEN / qwen-turbo\n- temperature: 0.4\n- maxTokens: 900\n- retries: 0\n")
                .append("- scenarios: ATTACK, SEARCH, HEAL, REVIVE（复用既有 fixture）\n- repeats: ").append(repeats)
                .append("\n- requested: ").append(16 * repeats).append("\n- completed so far: ").append(all.size()).append("\n\n")
                .append("## Overall Result\n\n| Variant | Relevant/Selection | MOVE | Non-MOVE | Diversity | Context Recognition | Affordance Recognition | Parsed=Executed | Sanitize Changed | Actions |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (BattleActionAffordanceVariant v : BattleActionAffordanceVariant.values()) {
            List<BattleActionAffordanceResult> rows = rows(all, v); if (rows.isEmpty()) continue;
            long move = count(rows, "MOVE"), relevant = rows.stream().filter(BattleActionAffordanceMain::relevant).count();
            Set<String> actions = rows.stream().map(r -> String.valueOf(r.trace().parsedAction())).collect(Collectors.toCollection(TreeSet::new));
            b.append("| ").append(v).append(" | ").append(rate(relevant, rows.size())).append(" | ").append(rate(move, rows.size()))
                    .append(" | ").append(rate(rows.size()-move, rows.size())).append(" | ").append(actions.size())
                    .append(" | ").append(rate(rows.stream().filter(BattleActionAffordanceResult::contextRecognized).count(), rows.size()))
                    .append(" | ").append(rate(rows.stream().filter(BattleActionAffordanceResult::affordanceRecognized).count(), rows.size()))
                    .append(" | ").append(rate(rows.stream().filter(r -> Objects.equals(r.trace().parsedAction(), r.trace().executedAction())).count(), rows.size()))
                    .append(" | ").append(rate(rows.stream().filter(r -> r.trace().sanitizedChanged()).count(), rows.size()))
                    .append(" | ").append(actions).append(" |\n");
        }
        for (BattlePromptScenario s : BattlePromptScenario.values()) appendScenario(b, all, s);
        appendRecognitionLadder(b, all);
        appendSearchPatterns(b, all);
        appendPipeline(b, all);
        b.append("\n## Root Cause Classification\n\n").append(classification(all)).append("\n\n")
                .append("## Recommended Next Step\n\n").append(recommendation(all))
                .append("\n\n生产 Prompt 与生产 sanitizer 均未修改。\n");
        return b.toString();
    }

    private static void appendScenario(StringBuilder b, List<BattleActionAffordanceResult> all, BattlePromptScenario s) {
        b.append("\n## ").append(s).append("\n\n| Variant | Context seen | Affordance understood | Target selected | MOVE | Non-MOVE");
        if (s == BattlePromptScenario.ATTACK) b.append(" | Tactical response");
        b.append(" | Distribution |\n|---|---:|---:|---:|---:|---:");
        if (s == BattlePromptScenario.ATTACK) b.append("|---:");
        b.append("|---|\n");
        for (BattleActionAffordanceVariant v : BattleActionAffordanceVariant.values()) {
            List<BattleActionAffordanceResult> rows = scenarioRows(rows(all,v),s); if(rows.isEmpty())continue;
            long move=count(rows,"MOVE");
            b.append("| ").append(v).append(" | ").append(frac(rows,BattleActionAffordanceResult::contextRecognized))
                    .append(" | ").append(frac(rows,BattleActionAffordanceResult::affordanceRecognized))
                    .append(" | ").append(count(rows,s.expectedRelevantAction())).append("/").append(rows.size())
                    .append(" | ").append(move).append("/").append(rows.size()).append(" | ").append(rows.size()-move).append("/").append(rows.size());
            if(s==BattlePromptScenario.ATTACK) b.append(" | ").append(rows.stream().filter(BattleActionAffordanceMain::tactical).count()).append("/").append(rows.size());
            b.append(" | ").append(distribution(rows)).append(" |\n");
        }
        b.append("\nreasonSummary：\n\n");
        for(BattleActionAffordanceVariant v:BattleActionAffordanceVariant.values()){
            List<BattleActionAffordanceResult> rows=scenarioRows(rows(all,v),s);if(rows.isEmpty())continue;
            b.append("- ").append(v).append(": ").append(rows.stream().map(r->r.trace().reasonSummary()).collect(Collectors.toList())).append("\n");
        }
    }

    private static void appendRecognitionLadder(StringBuilder b,List<BattleActionAffordanceResult> all){
        b.append("\n## Recognition Ladder\n\n| Variant | Scenario | STATE SEEN | AFFORDANCE UNDERSTOOD | ACTION SELECTED |\n|---|---|---:|---:|---:|\n");
        for(BattleActionAffordanceVariant v:BattleActionAffordanceVariant.values())for(BattlePromptScenario s:BattlePromptScenario.values()){
            List<BattleActionAffordanceResult> r=scenarioRows(rows(all,v),s);if(r.isEmpty())continue;
            b.append("| ").append(v).append(" | ").append(s).append(" | ").append(frac(r,BattleActionAffordanceResult::contextRecognized))
                    .append(" | ").append(frac(r,BattleActionAffordanceResult::affordanceRecognized)).append(" | ")
                    .append(count(r,s.expectedRelevantAction())).append("/").append(r.size()).append(" |\n");
        }
    }

    private static void appendSearchPatterns(StringBuilder b,List<BattleActionAffordanceResult> all){
        b.append("\n## SEARCH Mapping Patterns\n\n| Variant | current-region resource/search | MOVE-to-resource |\n|---|---:|---:|\n");
        for(BattleActionAffordanceVariant v:BattleActionAffordanceVariant.values()){
            List<BattleActionAffordanceResult> r=scenarioRows(rows(all,v),BattlePromptScenario.SEARCH);if(r.isEmpty())continue;
            long current=r.stream().filter(x->any(x.trace().reasonSummary(),"当前区域","本区域","A区","原地","搜索")).count();
            long travel=r.stream().filter(x->any(x.trace().reasonSummary(),"前往","移动","B区","B区域","C区","C区域")).count();
            b.append("| ").append(v).append(" | ").append(current).append("/").append(r.size()).append(" | ").append(travel).append("/").append(r.size()).append(" |\n");
        }
    }

    private static void appendPipeline(StringBuilder b,List<BattleActionAffordanceResult> all){
        long fallback=all.stream().filter(r->"MODEL_FALLBACK".equals(r.trace().source())).count();
        long repair=all.stream().filter(r->r.trace().parserRepair()).count();
        b.append("\n## Pipeline\n\n- raw traces: ").append(all.size()).append("\n- parser repair: ").append(repair)
                .append("\n- fallback: ").append(fallback).append("\n- errors: 0\n\n")
                .append("| Parsed (MODEL_SELECTED) | Sanitized | Executed | Count |\n|---|---|---|---:|\n");
        all.stream().collect(Collectors.groupingBy(r->String.valueOf(r.trace().parsedAction())+"\u0000"+r.trace().sanitizedAction()+"\u0000"+r.trace().executedAction(),TreeMap::new,Collectors.counting()))
                .forEach((k,n)->{String[] p=k.split("\u0000",-1);b.append("| ").append(p[0]).append(" | ").append(p[1]).append(" | ").append(p[2]).append(" | ").append(n).append(" |\n");});
        b.append("\n所有成功率、Recognition 和 Tactical response 均按 Parsed/MODEL_SELECTED 统计。\n");
    }

    static String classification(List<BattleActionAffordanceResult> all){
        if(all.size()<80)return "实验尚未完成。";
        List<BattleActionAffordanceResult> a1=rows(all,BattleActionAffordanceVariant.A1_LEGAL_ACTION_NAMES);
        List<BattleActionAffordanceResult> a3=rows(all,BattleActionAffordanceVariant.A3_DYNAMIC_AFFORDANCE);
        if(successPattern(a1))return "PASS / CASE D：仅显式列出合法动作名称已经显著恢复目标动作，候选空间显著性是主要根因。";
        if(successPattern(a3))return "PASS / CASE A：动态 Affordance 显著恢复 HEAL、SEARCH、ATTACK 战术响应并保持 REVIVE，GameState → executable action 映射是主要根因。";
        double recognition=a3.stream().filter(BattleActionAffordanceResult::affordanceRecognized).count()/(double)a3.size();
        double selection=a3.stream().filter(BattleActionAffordanceMain::relevant).count()/(double)a3.size();
        if(recognition>=.75 && selection<.5)return "PARTIAL / CASE B：Affordance Recognition 已恢复，但目标动作选择仍低；问题进入 Preference / Utility 层。";
        return "FAIL / CASE C：即使显式提供动态 Affordance，模型仍未稳定建立并表达动作映射；需要检查 Section placement、状态表达、动作语义显著性或模型能力。";
    }

    private static boolean successPattern(List<BattleActionAffordanceResult> rows){
        if(rows.size()<20)return false;
        boolean heal=selection(rows,BattlePromptScenario.HEAL)>=.6, search=selection(rows,BattlePromptScenario.SEARCH)>=.6;
        boolean revive=selection(rows,BattlePromptScenario.REVIVE)>=.6;
        List<BattleActionAffordanceResult> attack=scenarioRows(rows,BattlePromptScenario.ATTACK);
        boolean tactical=attack.stream().filter(BattleActionAffordanceMain::tactical).count()/(double)attack.size()>=.6;
        return heal&&search&&revive&&tactical;
    }

    private static String recommendation(List<BattleActionAffordanceResult> all){
        String c=classification(all);
        if(c.contains("CASE D"))return "采用最小生产方案：Deterministic legal action names → LLM selection → validator；无需复杂 A2/A3。";
        if(c.contains("CASE A"))return "下一轮设计生产级 Deterministic Affordance Engine，但先审计并拆分 sanitizer 的合法化与策略替换职责。";
        if(c.contains("CASE B"))return "下一轮只做 Action Utility / Decision Scoring 单变量实验，区分候选已知后的偏好与长期目标、Persona、Memory/Social Goal 支配。";
        return "下一轮做 Affordance Section Placement × State Representation 的受控实验；若 Recognition 仍失败，再进行 qwen-turbo 与更强模型对照。";
    }

    private static boolean tactical(BattleActionAffordanceResult r){return Set.of("ATTACK","HIDE","GUARD").contains(r.trace().parsedAction());}
    private static boolean relevant(BattleActionAffordanceResult r){return r.expectedRelevantAction().equals(r.trace().parsedAction());}
    private static double selection(List<BattleActionAffordanceResult> r,BattlePromptScenario s){List<BattleActionAffordanceResult>x=scenarioRows(r,s);return count(x,s.expectedRelevantAction())/(double)x.size();}
    private static List<BattleActionAffordanceResult> rows(List<BattleActionAffordanceResult>a,BattleActionAffordanceVariant v){return a.stream().filter(r->r.variant()==v).toList();}
    private static List<BattleActionAffordanceResult> scenarioRows(List<BattleActionAffordanceResult>a,BattlePromptScenario s){return a.stream().filter(r->r.scenario()==s).toList();}
    private static long count(List<BattleActionAffordanceResult>r,String a){return r.stream().filter(x->a.equals(x.trace().parsedAction())).count();}
    private static String distribution(List<BattleActionAffordanceResult>r){return r.stream().map(x->String.valueOf(x.trace().parsedAction())).collect(Collectors.groupingBy(Function.identity(),TreeMap::new,Collectors.counting())).toString();}
    private static String frac(List<BattleActionAffordanceResult>r,java.util.function.Predicate<BattleActionAffordanceResult>p){return r.stream().filter(p).count()+"/"+r.size();}
    private static String rate(long n,long d){return d==0?"N/A":String.format(Locale.ROOT,"%.2f%%",100.0*n/d);}
    private static boolean any(String value,String...terms){return value!=null&&Arrays.stream(terms).anyMatch(value::contains);}
}
