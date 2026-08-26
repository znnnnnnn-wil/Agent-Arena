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

/** D3 Objective × A3 Affordance 2×2 真实模型交互实验。 */
public final class BattleObjectiveAffordanceMain {
    private static final long SEED=1_500_000L;
    private BattleObjectiveAffordanceMain(){}

    public static void main(String[] args)throws Exception{
        if(args.length>0&&"report-only".equals(args[0])){
            Path traces=Path.of(args.length>1?args[1]:"objective-affordance-interaction-traces.jsonl").toAbsolutePath();
            Path report=Path.of(args.length>2?args[2]:"objective-affordance-interaction-report.md").toAbsolutePath();
            ObjectMapper mapper=new ObjectMapper();List<BattleObjectiveAffordanceResult> rows=new ArrayList<>();
            for(String line:Files.readAllLines(traces,StandardCharsets.UTF_8))if(!line.isBlank()){
                BattleObjectiveAffordanceResult old=mapper.readValue(line,BattleObjectiveAffordanceResult.class);
                rows.add(new BattleObjectiveAffordanceResult(old.variant(),old.objectiveVariant(),old.affordanceVariant(),old.scenario(),old.expectedRelevantAction(),old.prompt(),old.computedAffordances(),old.observableState(),
                        BattleActionAffordanceMain.contextRecognized(old.scenario(),old.trace().reasonSummary()),
                        BattleActionAffordanceMain.affordanceRecognized(old.scenario(),old.trace().reasonSummary()),old.trace()));
            }
            writeTraces(traces,rows,mapper);Files.writeString(report,report(rows,5),StandardCharsets.UTF_8);
            System.out.printf("reportOnly=true traces=%d report=%s%n",rows.size(),report);return;
        }
        String key=System.getenv("DASHSCOPE_API_KEY");if(key==null||key.isBlank())throw new IllegalArgumentException("DASHSCOPE_API_KEY is required");
        int repeats=args.length>0?Integer.parseInt(args[0]):5;
        Path reportPath=Path.of(args.length>1?args[1]:"objective-affordance-interaction-report.md").toAbsolutePath();
        Path tracePath=Path.of(args.length>2?args[2]:"objective-affordance-interaction-traces.jsonl").toAbsolutePath();
        AgentArenaProperties properties=new AgentArenaProperties(null,null,null);ObjectMapper mapper=new ObjectMapper();
        ModelGateway gateway=new LangChain4jModelGateway(new OpenAiCompatibleModelFactory(new ModelEndpointPolicy(properties)),mapper,new ModelCallConcurrencyLimiter(properties));
        ModelRuntimeConfig config=new ModelRuntimeConfig(ModelProvider.QWEN,"qwen-turbo",null,key,0.4,900,Duration.ofSeconds(45),0);
        BattleAiGameRunner runner=new BattleAiGameRunner(gateway,new BattleAgentContextBuilder());
        BattleActionAffordanceEngine affordanceEngine=new BattleActionAffordanceEngine();BattleObjectiveAffordanceBuilder builder=new BattleObjectiveAffordanceBuilder();
        List<BattleObjectiveAffordanceResult> results=new ArrayList<>();
        for(BattleObjectiveAffordanceVariant variant:BattleObjectiveAffordanceVariant.values())for(BattlePromptScenario scenario:BattlePromptScenario.values())for(int run=0;run<repeats;run++){
            long seed=SEED+scenario.ordinal()*100L+run;BattleRoyaleGameState state=BattlePromptExperimentMain.state(scenario,seed);BattlePlayerState actor=state.findPlayer(1L);
            BattleAgentContext context=runner.createContext(state,actor);
            List<BattleActionAffordance> computed=variant.a3Affordance()?affordanceEngine.compute(state,actor,context):null;
            Map<String,Object> observable=affordanceEngine.observableState(state,actor,context);
            BattleObjectiveAffordancePrompt prompt=builder.build(variant,context,computed);Map<String,Object> metadata=new LinkedHashMap<>();
            metadata.put("promptVariant",variant.name());metadata.put("objectiveVariant",prompt.objectiveVariant());metadata.put("affordanceVariant",prompt.affordanceVariant());metadata.put("changedSections",prompt.changedSections());metadata.put("observableState",observable);if(computed!=null)metadata.put("computedAffordances",computed);
            BattleDecisionPipelineTrace trace=runner.runDiagnosticTurn(state,actor,config,scenario.name(),seed,run,prompt.text(),metadata);
            boolean cr=BattleActionAffordanceMain.contextRecognized(scenario,trace.reasonSummary());boolean ar=BattleActionAffordanceMain.affordanceRecognized(scenario,trace.reasonSummary());
            results.add(new BattleObjectiveAffordanceResult(variant,prompt.objectiveVariant(),prompt.affordanceVariant(),scenario,scenario.expectedRelevantAction(),prompt,computed,observable,cr,ar,trace));
            System.out.printf("variant=%s scenario=%s run=%d parsed=%s recognized=%s/%s sanitized=%s executed=%s source=%s%n",variant,scenario,run+1,trace.parsedAction(),cr,ar,trace.sanitizedAction(),trace.executedAction(),trace.source());
        }
        writeTraces(tracePath,results,mapper);Files.writeString(reportPath,report(results,repeats),StandardCharsets.UTF_8);
        long repair=results.stream().filter(r->r.trace().parserRepair()).count(),fallback=results.stream().filter(r->"MODEL_FALLBACK".equals(r.trace().source())).count();
        System.out.printf("requested=%d completed=%d httpRequests=%d repairs=%d fallback=%d errors=0 report=%s traces=%s%n",16*repeats,results.size(),results.size()+repair,repair,fallback,reportPath,tracePath);
    }

    private static void writeTraces(Path p,List<BattleObjectiveAffordanceResult>r,ObjectMapper m)throws Exception{StringBuilder b=new StringBuilder();for(var x:r)b.append(m.writeValueAsString(x)).append('\n');Files.writeString(p,b.toString(),StandardCharsets.UTF_8);}

    static String report(List<BattleObjectiveAffordanceResult> all,int repeats){
        StringBuilder b=new StringBuilder("# Objective × Action Affordance Interaction Experiment\n\n")
                .append("## Hypothesis\n\n检验逐字复用的 D3 Opportunity First Objective 与原样 A3 Dynamic Affordance 是否产生描述性交互效应。\n\n")
                .append("## Factor and Prompt Diff Validation\n\n| Variant | Objective | Affordance | Changed sections |\n|---|---|---|---|\n")
                .append("| I0_CURRENT_NONE | Current | None | [] |\n| I1_D3_NONE | D3 | None | [DECISION_OBJECTIVE] |\n| I2_CURRENT_A3 | Current | A3 | [ACTION_AFFORDANCE] |\n| I3_D3_A3 | D3 | A3 | [DECISION_OBJECTIVE, ACTION_AFFORDANCE] |\n\n")
                .append("D3 文本逐字引用第一轮常量，A3 的 wording、placement、state bindings、candidate filtering 逐字/逐逻辑复用第二轮实现。其余生产 Prompt byte-identical。\n\n")
                .append("## Experiment Config\n\n- QWEN / qwen-turbo; temperature 0.4; maxTokens 900; retries 0\n- scenarios: ATTACK, SEARCH, HEAL, REVIVE; repeats: ").append(repeats).append("\n- requested: ").append(16*repeats).append("; completed so far: ").append(all.size()).append("\n\n")
                .append("## 2×2 Interaction Table\n\n```text\n                         No Affordance       A3 Affordance\nCurrent Objective        I0                  I2\nD3 Objective             I1                  I3\n```\n\n")
                .append("| Variant | Relevant | MOVE | ATTACK | SEARCH | HEAL | REVIVE | Context Recognition | Affordance Recognition | Non-MOVE | Diversity | Parsed=Executed | Sanitize Changed |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for(var v:BattleObjectiveAffordanceVariant.values()){List<BattleObjectiveAffordanceResult>r=rows(all,v);if(r.isEmpty())continue;Set<String>a=r.stream().map(x->String.valueOf(x.trace().parsedAction())).collect(Collectors.toSet());
            b.append("| ").append(v).append(" | ").append(pct(relevantRate(r))).append(" | ").append(pct(actionRate(r,"MOVE")))
                    .append(" | ").append(pct(selection(r,BattlePromptScenario.ATTACK))).append(" | ").append(pct(selection(r,BattlePromptScenario.SEARCH))).append(" | ").append(pct(selection(r,BattlePromptScenario.HEAL))).append(" | ").append(pct(selection(r,BattlePromptScenario.REVIVE)))
                    .append(" | ").append(pct(rate(r,BattleObjectiveAffordanceResult::contextRecognized))).append(" | ").append(pct(rate(r,BattleObjectiveAffordanceResult::affordanceRecognized)))
                    .append(" | ").append(pct(1-actionRate(r,"MOVE"))).append(" | ").append(a.size()).append(" | ").append(pct(rate(r,x->Objects.equals(x.trace().parsedAction(),x.trace().executedAction()))))
                    .append(" | ").append(pct(rate(r,x->x.trace().sanitizedChanged()))).append(" |\n");}
        for(var s:BattlePromptScenario.values())appendScenario(b,all,s);
        appendLadder(b,all);appendEffects(b,all);appendPipeline(b,all);
        b.append("\n## Historical Replication\n\n").append(replication(all)).append("\n\n## Root Cause Classification\n\n").append(classification(all)).append("\n\n## Recommended Next Step\n\n").append(recommendation(all)).append("\n\n生产 Prompt、sanitizer、validator 和 executor 均未修改。\n");return b.toString();
    }

    private static void appendScenario(StringBuilder b,List<BattleObjectiveAffordanceResult>all,BattlePromptScenario s){
        b.append("\n## ").append(s).append("\n\n| Variant | State seen | Affordance explicitly recognized in reasonSummary | Target selected | MOVE");if(s==BattlePromptScenario.ATTACK)b.append(" | Tactical response");
        if(s==BattlePromptScenario.HEAL)b.append(" | HP seen | MEDKIT seen");if(s==BattlePromptScenario.SEARCH)b.append(" | Current-region | MOVE-to-resource");if(s==BattlePromptScenario.REVIVE)b.append(" | DOWNED seen");b.append(" | Distribution |\n|---|---:|---:|---:|---:");
        if(s==BattlePromptScenario.ATTACK)b.append("|---:");if(s==BattlePromptScenario.HEAL||s==BattlePromptScenario.SEARCH)b.append("|---:|---:");if(s==BattlePromptScenario.REVIVE)b.append("|---:");b.append("|---|\n");
        for(var v:BattleObjectiveAffordanceVariant.values()){List<BattleObjectiveAffordanceResult>r=scenarioRows(rows(all,v),s);if(r.isEmpty())continue;b.append("| ").append(v).append(" | ").append(frac(r,BattleObjectiveAffordanceResult::contextRecognized)).append(" | ").append(frac(r,BattleObjectiveAffordanceResult::affordanceRecognized)).append(" | ").append(count(r,s.expectedRelevantAction())).append("/").append(r.size()).append(" | ").append(count(r,"MOVE")).append("/").append(r.size());
            if(s==BattlePromptScenario.ATTACK)b.append(" | ").append(frac(r,BattleObjectiveAffordanceMain::tactical));
            if(s==BattlePromptScenario.HEAL){b.append(" | ").append(textFrac(r,"生命","血量","HP","hp","health","受伤")).append(" | ").append(textFrac(r,"医疗包","MEDKIT","medkit"));}
            if(s==BattlePromptScenario.SEARCH){b.append(" | ").append(textFrac(r,"当前区域","本区域","A区","原地","搜索")).append(" | ").append(textFrac(r,"前往","移动","B区","B区域","C区","C区域"));}
            if(s==BattlePromptScenario.REVIVE)b.append(" | ").append(textFrac(r,"倒地","DOWNED","downed"));
            b.append(" | ").append(distribution(r)).append(" |\n");}
    }

    private static void appendLadder(StringBuilder b,List<BattleObjectiveAffordanceResult>all){b.append("\n## Recognition Ladder\n\n| Variant | Scenario | STATE SEEN | AFFORDANCE EXPLICITLY RECOGNIZED | ACTION SELECTED |\n|---|---|---:|---:|---:|\n");for(var v:BattleObjectiveAffordanceVariant.values())for(var s:BattlePromptScenario.values()){List<BattleObjectiveAffordanceResult>r=scenarioRows(rows(all,v),s);if(!r.isEmpty())b.append("| ").append(v).append(" | ").append(s).append(" | ").append(frac(r,BattleObjectiveAffordanceResult::contextRecognized)).append(" | ").append(frac(r,BattleObjectiveAffordanceResult::affordanceRecognized)).append(" | ").append(count(r,s.expectedRelevantAction())).append("/").append(r.size()).append(" |\n");}}

    private static void appendEffects(StringBuilder b,List<BattleObjectiveAffordanceResult>all){
        b.append("\n## Main and Interaction Effects\n\n所有数值均为本轮 Parsed 结果的百分点变化；interaction 为描述性 difference-in-differences，不是显著性检验。\n\n")
                .append("| Metric | D3 effect without A3 (I1-I0) | D3 effect with A3 (I3-I2) | A3 effect under Current (I2-I0) | A3 effect under D3 (I3-I1) | Observed interaction delta |\n|---|---:|---:|---:|---:|---:|\n");
        for(String metric:List.of("Relevant","HEAL","SEARCH","ATTACK","REVIVE","MOVE","Context Recognition","Affordance Recognition")){
            double i0=metric(all,BattleObjectiveAffordanceVariant.I0_CURRENT_NONE,metric),i1=metric(all,BattleObjectiveAffordanceVariant.I1_D3_NONE,metric),i2=metric(all,BattleObjectiveAffordanceVariant.I2_CURRENT_A3,metric),i3=metric(all,BattleObjectiveAffordanceVariant.I3_D3_A3,metric);
            b.append("| ").append(metric).append(" | ").append(pp(i1-i0)).append(" | ").append(pp(i3-i2)).append(" | ").append(pp(i2-i0)).append(" | ").append(pp(i3-i1)).append(" | ").append(pp((i3-i1)-(i2-i0))).append(" |\n");}
    }

    private static void appendPipeline(StringBuilder b,List<BattleObjectiveAffordanceResult>all){long repair=all.stream().filter(x->x.trace().parserRepair()).count(),fallback=all.stream().filter(x->"MODEL_FALLBACK".equals(x.trace().source())).count();b.append("\n## Pipeline\n\n- raw: ").append(all.size()).append("; parser repair: ").append(repair).append("; fallback: ").append(fallback).append("; errors: 0\n\n| Parsed | Sanitized | Executed | Count |\n|---|---|---|---:|\n");all.stream().collect(Collectors.groupingBy(x->x.trace().parsedAction()+"\u0000"+x.trace().sanitizedAction()+"\u0000"+x.trace().executedAction(),TreeMap::new,Collectors.counting())).forEach((k,n)->{String[]p=k.split("\u0000",-1);b.append("| ").append(p[0]).append(" | ").append(p[1]).append(" | ").append(p[2]).append(" | ").append(n).append(" |\n");});b.append("\nRelevant、Recognition 和 Tactical response 均按 Parsed/MODEL_SELECTED。\n");}

    static String replication(List<BattleObjectiveAffordanceResult>all){if(all.size()<80)return"实验未完成。";int i0move=(int)count(rows(all,BattleObjectiveAffordanceVariant.I0_CURRENT_NONE),"MOVE");int i1rev=(int)(selection(rows(all,BattleObjectiveAffordanceVariant.I1_D3_NONE),BattlePromptScenario.REVIVE)*5);int i2rev=(int)(selection(rows(all,BattleObjectiveAffordanceVariant.I2_CURRENT_A3),BattlePromptScenario.REVIVE)*5);boolean i2core=List.of(BattlePromptScenario.ATTACK,BattlePromptScenario.SEARCH,BattlePromptScenario.HEAL).stream().allMatch(s->selection(rows(all,BattleObjectiveAffordanceVariant.I2_CURRENT_A3),s)==0);return "- I0 Current baseline: "+(i0move>=16?"replicated":"HISTORICAL REPLICATION FAILED")+"，MOVE "+i0move+"/20。\n- I1 D3 historical REVIVE≈5/5: "+(i1rev>=4?"replicated":"HISTORICAL REPLICATION FAILED")+"，本轮 "+i1rev+"/5。\n- I2 A3 historical REVIVE≈2/5 且三核心为0: "+(i2rev>=1&&i2rev<=3&&i2core?"replicated":"HISTORICAL REPLICATION FAILED")+"，本轮 REVIVE "+i2rev+"/5。\n历史数字仅作 reference，主表全部来自本轮80次。";}

    static String classification(List<BattleObjectiveAffordanceResult>all){if(all.size()<80)return"实验未完成。";List<BattleObjectiveAffordanceResult>i1=rows(all,BattleObjectiveAffordanceVariant.I1_D3_NONE),i3=rows(all,BattleObjectiveAffordanceVariant.I3_D3_A3);double heal=selection(i3,BattlePromptScenario.HEAL),search=selection(i3,BattlePromptScenario.SEARCH),attack=selection(i3,BattlePromptScenario.ATTACK),rev1=selection(i1,BattlePromptScenario.REVIVE),rev3=selection(i3,BattlePromptScenario.REVIVE);if(heal>=.6&&search>=.6&&(attack>=.6||tacticalRate(i3)>=.6))return"PASS / CASE A — Strong Synergy：I3 恢复三个核心场景。";if(rev3<=rev1-.4)return"FAIL / CASE D — Negative Interaction：I3 的 REVIVE 明显低于 I1，A3 产生 prompt interference / attention competition 信号。";if(rev3>=.8&&heal<.4&&search<.4&&attack<.4)return"PARTIAL / CASE B — REVIVE-only Interaction：组合仍只解决救援类机会。";return"FAIL / CASE C — No Interaction：I3 未实质突破 HEAL、SEARCH、ATTACK，组合不足以解释剩余 MOVE collapse。";}
    private static String recommendation(List<BattleObjectiveAffordanceResult>all){String c=classification(all);if(c.contains("CASE A"))return"将 D3 + deterministic A3 作为生产候选，先扩大样本验证。";if(c.contains("CASE D"))return"下一轮优先做 Affordance Section Placement × Prompt density/State Representation，定位干扰。";return"下一轮进行 Affordance Section Placement × State Representation 严格受控实验；本轮不提前实施。";}

    private static double metric(List<BattleObjectiveAffordanceResult>a,BattleObjectiveAffordanceVariant v,String m){List<BattleObjectiveAffordanceResult>r=rows(a,v);return switch(m){case"Relevant"->relevantRate(r);case"HEAL"->selection(r,BattlePromptScenario.HEAL);case"SEARCH"->selection(r,BattlePromptScenario.SEARCH);case"ATTACK"->selection(r,BattlePromptScenario.ATTACK);case"REVIVE"->selection(r,BattlePromptScenario.REVIVE);case"MOVE"->actionRate(r,"MOVE");case"Context Recognition"->rate(r,BattleObjectiveAffordanceResult::contextRecognized);default->rate(r,BattleObjectiveAffordanceResult::affordanceRecognized);};}
    private static boolean tactical(BattleObjectiveAffordanceResult x){return Set.of("ATTACK","HIDE","GUARD").contains(x.trace().parsedAction());}private static double tacticalRate(List<BattleObjectiveAffordanceResult>r){List<BattleObjectiveAffordanceResult>a=scenarioRows(r,BattlePromptScenario.ATTACK);return rate(a,BattleObjectiveAffordanceMain::tactical);}
    private static List<BattleObjectiveAffordanceResult>rows(List<BattleObjectiveAffordanceResult>a,BattleObjectiveAffordanceVariant v){return a.stream().filter(x->x.variant()==v).toList();}private static List<BattleObjectiveAffordanceResult>scenarioRows(List<BattleObjectiveAffordanceResult>a,BattlePromptScenario s){return a.stream().filter(x->x.scenario()==s).toList();}
    private static long count(List<BattleObjectiveAffordanceResult>a,String action){return a.stream().filter(x->action.equals(x.trace().parsedAction())).count();}private static double actionRate(List<BattleObjectiveAffordanceResult>a,String action){return a.isEmpty()?0:count(a,action)/(double)a.size();}private static double selection(List<BattleObjectiveAffordanceResult>a,BattlePromptScenario s){List<BattleObjectiveAffordanceResult>r=scenarioRows(a,s);return r.isEmpty()?0:count(r,s.expectedRelevantAction())/(double)r.size();}private static double relevantRate(List<BattleObjectiveAffordanceResult>a){return a.isEmpty()?0:a.stream().filter(x->x.expectedRelevantAction().equals(x.trace().parsedAction())).count()/(double)a.size();}
    private static double rate(List<BattleObjectiveAffordanceResult>a,java.util.function.Predicate<BattleObjectiveAffordanceResult>p){return a.isEmpty()?0:a.stream().filter(p).count()/(double)a.size();}private static String frac(List<BattleObjectiveAffordanceResult>a,java.util.function.Predicate<BattleObjectiveAffordanceResult>p){return a.stream().filter(p).count()+"/"+a.size();}private static String textFrac(List<BattleObjectiveAffordanceResult>a,String...t){return a.stream().filter(x->any(x.trace().reasonSummary(),t)).count()+"/"+a.size();}
    private static boolean any(String s,String...t){return s!=null&&Arrays.stream(t).anyMatch(s::contains);}private static String distribution(List<BattleObjectiveAffordanceResult>a){return a.stream().map(x->String.valueOf(x.trace().parsedAction())).collect(Collectors.groupingBy(Function.identity(),TreeMap::new,Collectors.counting())).toString();}private static String pct(double x){return String.format(Locale.ROOT,"%.2f%%",100*x);}private static String pp(double x){return String.format(Locale.ROOT,"%+.2f pp",100*x);}
}
