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

/** “长期生存目标 × 区域资源特色”2×2 真实模型实验入口。 */
public final class BattlePromptFactorialMain {
    private static final String MODEL = "qwen-turbo";
    private static final long SEED = 1_200_000L;
    private static final List<String> KEYWORDS = List.of(
            "食物", "B区", "B区域", "资源", "长期生存", "最后幸存", "协作目标", "安全", "医疗", "受伤", "敌人", "救援");
    private BattlePromptFactorialMain() { }

    public static void main(String[] args) throws Exception {
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("DASHSCOPE_API_KEY is required");
        int repeats = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        Path report = Path.of(args.length > 1 ? args[1] : "prompt-factorial-report.md").toAbsolutePath();
        Path traces = Path.of(args.length > 2 ? args[2] : "prompt-factorial-traces.jsonl").toAbsolutePath();
        AgentArenaProperties properties = new AgentArenaProperties(null, null, null);
        ObjectMapper mapper = new ObjectMapper();
        ModelGateway gateway = new LangChain4jModelGateway(
                new OpenAiCompatibleModelFactory(new ModelEndpointPolicy(properties)), mapper,
                new ModelCallConcurrencyLimiter(properties));
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.QWEN, MODEL, null, key,
                0.4, 900, Duration.ofSeconds(45), 0);
        BattleAiGameRunner runner = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder());
        BattlePromptFactorialBuilder builder = new BattlePromptFactorialBuilder();
        List<BattlePromptFactorialResult> results = new ArrayList<>();
        for (BattlePromptFactorialVariant variant : BattlePromptFactorialVariant.values()) {
            for (BattlePromptScenario scenario : BattlePromptScenario.values()) {
                for (int run = 0; run < repeats; run++) {
                    long seed = SEED + scenario.ordinal() * 100L + run;
                    BattleRoyaleGameState state = BattlePromptExperimentMain.state(scenario, seed);
                    BattlePlayerState actor = state.findPlayer(1L);
                    BattleAgentContext context = runner.createContext(state, actor);
                    BattlePromptFactorialPrompt prompt = builder.build(variant, context);
                    Map<String,Object> metadata = new LinkedHashMap<>();
                    metadata.put("promptVariant", variant.name());
                    metadata.put("longTermGoalPresent", prompt.longTermGoalPresent());
                    metadata.put("regionAffordancePresent", prompt.regionAffordancePresent());
                    metadata.put("changedSections", prompt.changedSections());
                    BattleDecisionPipelineTrace trace = runner.runDiagnosticTurn(state, actor, config,
                            scenario.name(), seed, run, prompt.text(), metadata);
                    results.add(new BattlePromptFactorialResult(variant, scenario,
                            scenario.expectedRelevantAction(), prompt, trace));
                    System.out.printf("variant=%s scenario=%s run=%d parsed=%s target=%s sanitized=%s executed=%s source=%s%n",
                            variant, scenario, run + 1, trace.parsedAction(), trace.parsedTargetRegion(),
                            trace.sanitizedAction(), trace.executedAction(), trace.source());
                }
            }
            writeTraces(traces, results, mapper);
            Files.writeString(report, report(results, repeats), StandardCharsets.UTF_8);
        }
        long repairs = results.stream().filter(r -> r.trace().parserRepair()).count();
        long fallback = results.stream().filter(r -> "MODEL_FALLBACK".equals(r.trace().source())).count();
        System.out.printf("requested=%d successful=%d httpRequests=%d repairs=%d fallback=%d report=%s traces=%s%n",
                results.size(), results.size()-fallback, results.size()+repairs, repairs, fallback, report, traces);
    }

    private static void writeTraces(Path path, List<BattlePromptFactorialResult> results, ObjectMapper mapper) throws Exception {
        StringBuilder b = new StringBuilder();
        for (BattlePromptFactorialResult result : results) b.append(mapper.writeValueAsString(result)).append('\n');
        Files.writeString(path, b.toString(), StandardCharsets.UTF_8);
    }

    private static String report(List<BattlePromptFactorialResult> all, int repeats) {
        StringBuilder b = new StringBuilder("# 长期生存目标 × 区域资源特色 2×2 因果消融报告\n\n");
        b.append("## Hypothesis\n\n检验 qwen-turbo 的 MOVE 塌缩来自 LONG_TERM_GOAL、REGION_AFFORDANCE、二者交互，或两者均非原因。\n\n")
                .append("## Factor Definitions\n\n- A+：逐字保留生产目标“你正在参加一局多智能体大逃杀，目标是成为最后幸存者。”\n")
                .append("- A-：仅替换为中性任务“你需要根据当前观察和可执行行动，选择此刻最合理的行动，并遵守游戏规则。”\n")
                .append("- B+：逐字保留生产区域特色映射。\n- B-：仅删除区域→资源映射，保留当前位置、拓扑、安全区和 targetRegion 约束。\n\n")
                .append("## Prompt Diff Validation\n\n| Variant | LT Goal | Region Affordance | Changed Sections |\n|---|---|---|---|\n")
                .append("| V00 | ON | ON | []（与生产 Prompt 逐字一致） |\n")
                .append("| V01 | ON | OFF | [REGION_AFFORDANCE] |\n")
                .append("| V10 | OFF | ON | [LONG_TERM_GOAL] |\n")
                .append("| V11 | OFF | OFF | [LONG_TERM_GOAL, REGION_AFFORDANCE] |\n\n")
                .append("B- 全文校验不含区域特色、A武器、B食物、C饮水与弹药、D护甲、E医疗；A- 全文校验不含战略性“最后幸存者/长期生存”指令。JSON Example、Action order、Persona、Social Context 和输出格式保持不变。\n\n")
                .append("## Experiment Config\n\n- provider/model: QWEN / qwen-turbo\n- temperature: 0.4\n- maxTokens: 900\n- retries: 0\n")
                .append("- scenarios: ATTACK, SEARCH, HEAL, REVIVE\n- repeats: ").append(repeats)
                .append("\n- requested decisions: 80\n- completed so far: ").append(all.size()).append("\n\n")
                .append("## Overall 2×2 Results\n\n| Variant | LT | Region | Strict Relevant | MOVE | Non-MOVE | Diversity | Actions |\n|---|---|---|---:|---:|---:|---:|---|\n");
        for (BattlePromptFactorialVariant v : BattlePromptFactorialVariant.values()) {
            List<BattlePromptFactorialResult> rows = rows(all,v); if (rows.isEmpty()) continue;
            long rel=rows.stream().filter(BattlePromptFactorialMain::relevant).count(), move=count(rows,"MOVE");
            Set<String> actions=rows.stream().map(r->String.valueOf(r.trace().parsedAction())).collect(Collectors.toCollection(TreeSet::new));
            b.append("| ").append(v).append(" | ").append(v.longTermGoalPresent()?"ON":"OFF").append(" | ")
                    .append(v.regionAffordancePresent()?"ON":"OFF").append(" | ").append(rate(rel,rows.size())).append(" | ")
                    .append(rate(move,rows.size())).append(" | ").append(rate(rows.size()-move,rows.size())).append(" | ")
                    .append(actions.size()).append(" | ").append(actions).append(" |\n");
        }
        for (BattlePromptScenario s : BattlePromptScenario.values()) appendScenario(b,all,s);
        b.append("\n## MOVE Target Region\n\n| Variant | MOVE count | A | B | C | D | E | B share |\n|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (BattlePromptFactorialVariant v : BattlePromptFactorialVariant.values()) {
            List<BattlePromptFactorialResult> moves=rows(all,v).stream().filter(r->"MOVE".equals(r.trace().parsedAction())).toList(); if(moves.isEmpty()&&rows(all,v).isEmpty())continue;
            b.append("| ").append(v).append(" | ").append(moves.size());
            for(String region:List.of("A","B","C","D","E")) b.append(" | ").append(moves.stream().filter(r->region.equals(r.trace().parsedTargetRegion())).count());
            long toB=moves.stream().filter(r->"B".equals(r.trace().parsedTargetRegion())).count();
            b.append(" | ").append(rate(toB,moves.size())).append(" |\n");
        }
        b.append("\n## reasonSummary Analysis\n\n| Variant | 食物 | B区/B区域 | 资源 | 长期生存 | 最后幸存 | 协作目标 | 安全 | 医疗 | 受伤 | 敌人 | 救援 |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for(BattlePromptFactorialVariant v:BattlePromptFactorialVariant.values()){
            List<BattlePromptFactorialResult> rows=rows(all,v);if(rows.isEmpty())continue;b.append("| ").append(v);
            for(String term:List.of("食物","B区|B区域","资源","长期生存","最后幸存","协作目标","安全","医疗","受伤","敌人","救援")){
                long c=rows.stream().filter(r->containsAny(r.trace().reasonSummary(),term.split("\\|"))).count();b.append(" | ").append(c);
            } b.append(" |\n");
        }
        long stale=all.stream().filter(r->!r.variant().regionAffordancePresent())
                .filter(r->containsAny(r.trace().reasonSummary(),"食物")&&containsAny(r.trace().reasonSummary(),"B区","B区域")).count();
        b.append("\nB- Prompt 已确认无区域→资源映射；B- 中仍出现“B区+食物”的次数：").append(stale)
                .append("。大于 0 时标记 `STALE_PATTERN_CONTINUATION`。\n\n")
                .append("## Long-Term Goal Effect\n\nMOVE 主效应（A+ 平均 MOVE率 − A- 平均 MOVE率）：")
                .append(pp(longEffect(all))).append("。\n\n## Region Affordance Effect\n\nMOVE 主效应（B+ 平均 MOVE率 − B- 平均 MOVE率）：")
                .append(pp(regionEffect(all))).append("。\n\n## Interaction Effect\n\nDifference-in-differences：")
                .append(pp(interaction(all))).append("。\n\n## Context Sensitivity\n\n");
        for(BattlePromptFactorialVariant v:BattlePromptFactorialVariant.values()){
            List<BattlePromptFactorialResult> rows=rows(all,v);if(rows.isEmpty())continue;
            Set<String> modes=new LinkedHashSet<>();for(BattlePromptScenario s:BattlePromptScenario.values()){List<BattlePromptFactorialResult> sr=rows.stream().filter(r->r.scenario()==s).toList();if(!sr.isEmpty())modes.add(mode(sr));}
            b.append("- ").append(v).append(": scenario modal action diversity=").append(modes.size()).append(" ").append(modes).append("\n");
        }
        long fallback=all.stream().filter(r->"MODEL_FALLBACK".equals(r.trace().source())).count();
        long repair=all.stream().filter(r->r.trace().parserRepair()).count();
        long changed=all.stream().filter(r->r.trace().sanitizedChanged()).count();
        b.append("\n## Pipeline\n\n- successful MODEL decisions: ").append(all.size()-fallback)
                .append("\n- fallback: ").append(fallback).append("\n- parser repair: ").append(repair)
                .append("\n- sanitize changed: ").append(changed).append("\n- parsed == executed: ")
                .append(all.stream().filter(r->Objects.equals(r.trace().parsedAction(),r.trace().executedAction())).count()).append("/").append(all.size()).append("\n\n")
                .append("## Root Cause\n\n").append(rootCause(all)).append("\n\n")
                .append("## Recommended Production Prompt Architecture\n\n").append(recommendation(all))
                .append("\n\n生产 Prompt 未修改；以上仅为下一阶段设计建议。\n");
        return b.toString();
    }

    private static void appendScenario(StringBuilder b,List<BattlePromptFactorialResult> all,BattlePromptScenario s){
        b.append("\n## ").append(s).append("\n\n| Variant | ").append(s.expectedRelevantAction()).append(" | MOVE | HIDE | Other | Distribution |\n|---|---:|---:|---:|---:|---|\n");
        for(BattlePromptFactorialVariant v:BattlePromptFactorialVariant.values()){
            List<BattlePromptFactorialResult> rows=rows(all,v).stream().filter(r->r.scenario()==s).toList();if(rows.isEmpty())continue;
            long expected=count(rows,s.expectedRelevantAction()),move=count(rows,"MOVE"),hide=count(rows,"HIDE");
            b.append("| ").append(v).append(" | ").append(expected).append(" | ").append(move).append(" | ").append(hide).append(" | ")
                    .append(rows.size()-expected-move-hide).append(" | ").append(distribution(rows)).append(" |\n");
        }
    }
    private static List<BattlePromptFactorialResult> rows(List<BattlePromptFactorialResult> all,BattlePromptFactorialVariant v){return all.stream().filter(r->r.variant()==v).toList();}
    private static boolean relevant(BattlePromptFactorialResult r){return r.expectedRelevantAction().equals(r.trace().parsedAction());}
    private static long count(List<BattlePromptFactorialResult> rows,String action){return rows.stream().filter(r->action.equals(r.trace().parsedAction())).count();}
    private static String distribution(List<BattlePromptFactorialResult> rows){return rows.stream().map(r->String.valueOf(r.trace().parsedAction())).collect(Collectors.groupingBy(Function.identity(),TreeMap::new,Collectors.counting())).toString();}
    private static String mode(List<BattlePromptFactorialResult> rows){return rows.stream().map(r->String.valueOf(r.trace().parsedAction())).collect(Collectors.groupingBy(Function.identity(),Collectors.counting())).entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("NONE");}
    private static boolean containsAny(String value,String...terms){if(value==null)return false;return Arrays.stream(terms).anyMatch(value::contains);}
    private static double moveRate(List<BattlePromptFactorialResult> all,BattlePromptFactorialVariant v){List<BattlePromptFactorialResult> r=rows(all,v);return r.isEmpty()?Double.NaN:count(r,"MOVE")/(double)r.size();}
    private static double longEffect(List<BattlePromptFactorialResult> a){return ((moveRate(a,BattlePromptFactorialVariant.V00)+moveRate(a,BattlePromptFactorialVariant.V01))-(moveRate(a,BattlePromptFactorialVariant.V10)+moveRate(a,BattlePromptFactorialVariant.V11)))/2;}
    private static double regionEffect(List<BattlePromptFactorialResult> a){return ((moveRate(a,BattlePromptFactorialVariant.V00)+moveRate(a,BattlePromptFactorialVariant.V10))-(moveRate(a,BattlePromptFactorialVariant.V01)+moveRate(a,BattlePromptFactorialVariant.V11)))/2;}
    private static double interaction(List<BattlePromptFactorialResult> a){return (moveRate(a,BattlePromptFactorialVariant.V00)-moveRate(a,BattlePromptFactorialVariant.V01))-(moveRate(a,BattlePromptFactorialVariant.V10)-moveRate(a,BattlePromptFactorialVariant.V11));}
    private static String rootCause(List<BattlePromptFactorialResult> a){
        if(a.size()<80)return "实验尚未完成。";double r00=moveRate(a,BattlePromptFactorialVariant.V00),r01=moveRate(a,BattlePromptFactorialVariant.V01),r10=moveRate(a,BattlePromptFactorialVariant.V10),r11=moveRate(a,BattlePromptFactorialVariant.V11);
        if(Math.max(Math.max(r00-r01,r00-r10),r00-r11)<.20)return "REGION / LONG_TERM HYPOTHESIS NOT SUPPORTED。";
        if(r11<Math.min(r01,r10)-.15)return "INTERACTION_EFFECT：共同移除的改善显著大于任一单因素。";
        return regionEffect(a)>longEffect(a)?"PRIMARY: REGION_AFFORDANCE_ANCHORING。":"PRIMARY: LONG_TERM_GOAL_ANCHORING。";
    }
    private static String recommendation(List<BattlePromptFactorialResult> a){String root=rootCause(a);if(root.contains("REGION_AFFORDANCE"))return "推荐 Prompt Re-architecture：将区域资源知识降为低显著性、按需提供的战略背景。";if(root.contains("LONG_TERM"))return "推荐重写 Decision Objective，使即时状态与长期胜利条件并列而非单向长期生存锚定。";if(root.contains("INTERACTION"))return "推荐分层 Prompt：即时状态 → 当前义务/机会 → 战略背景。";return "推荐下一轮 Action Affordance + 更强模型对照。";}
    private static String rate(long n,long d){return d==0?"N/A":String.format(Locale.ROOT,"%.2f%%",100.0*n/d);}
    private static String pp(double value){return Double.isNaN(value)?"N/A":String.format(Locale.ROOT,"%+.2f percentage points",value*100);}
}
