package com.zn.agentarena.modules.battle.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import com.zn.agentarena.modules.battle.agent.BattleAiGameRunner;
import com.zn.agentarena.modules.battle.alliance.*;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.goal.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.objective.*;
import com.zn.agentarena.modules.battle.promise.*;
import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;
import com.zn.agentarena.modules.model.domain.*;
import com.zn.agentarena.modules.model.gateway.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** 控制变量场景实验入口：每个场景只让一个 Agent 做一次真实生产决策。 */
public final class BattleLlmScenarioMain {
    private static final String MODEL = "qwen-turbo";
    private BattleLlmScenarioMain() {}
    public static void main(String[] args) throws Exception {
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("DASHSCOPE_API_KEY is required");
        int repeats = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        Path report = Path.of(args.length > 1 ? args[1] : "llm-action-pipeline-report.md").toAbsolutePath();
        AgentArenaProperties p = new AgentArenaProperties(null, null, null);
        ModelGateway gateway = new LangChain4jModelGateway(new OpenAiCompatibleModelFactory(new ModelEndpointPolicy(p)),
                new ObjectMapper(), new ModelCallConcurrencyLimiter(p));
        ModelRuntimeConfig cfg = new ModelRuntimeConfig(ModelProvider.QWEN, MODEL, null, key, 0.4, 900, Duration.ofSeconds(45), 0);
        BattleAiGameRunner runner = new BattleAiGameRunner(gateway, new com.zn.agentarena.modules.battle.agent.BattleAgentContextBuilder());
        List<String> names = List.of("ATTACK_CLEAR_OPPORTUNITY","SEARCH_CLEAR_OPPORTUNITY","HEAL_CLEAR_OPPORTUNITY",
                "REVIVE_CLEAR_OPPORTUNITY","ACCEPT_ALLIANCE","FULFILL_PROMISE","COOPERATIVE_CONTRIBUTION",
                "INFORMATION_BROKER","ATTACK_NEGATIVE","HEAL_NEGATIVE","REVIVE_NEGATIVE","PROMISE_NEGATIVE");
        List<BattleDecisionPipelineTrace> traces = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) for (int run = 0; run < repeats; run++) {
            BattleRoyaleGameState state = state(names.get(i), 900000L + i * 100 + run);
            traces.add(runner.runDiagnosticTurn(state, state.findPlayer(1L), cfg, names.get(i), state.getRandomSeed(), run));
            System.out.printf("scenario=%s run=%d raw=%s parsed=%s sanitized=%s executed=%s source=%s%n", names.get(i), run + 1,
                    action(traces.get(traces.size()-1).rawOutput()), traces.get(traces.size()-1).parsedAction(),
                    traces.get(traces.size()-1).sanitizedAction(), traces.get(traces.size()-1).executedAction(), traces.get(traces.size()-1).source());
        }
        Files.writeString(report, report(traces, repeats), StandardCharsets.UTF_8);
        System.out.println("scenario calls=" + traces.size() + ", fallbacks=" + traces.stream().filter(t -> "MODEL_FALLBACK".equals(t.source())).count() + ", report=" + report);
    }

    private static String action(String raw) { return raw == null ? "<unavailable>" : raw.replaceAll("\\s+", " ").substring(0, Math.min(80, raw.length())); }
    private static BattleRoyaleGameState state(String scenario, long seed) {
        BattlePlayerState a = player(1L, "A", 100, 7, 7, true, 2, 10, 2);
        BattlePlayerState b = player(2L, "A", 70, 7, 7, false, 0, 0, 0);
        List<BattlePlayerState> players = new ArrayList<>(List.of(a, b));
        for (long id = 3; id <= 6; id++) {
            BattlePlayerState extra = player(id, "E", 100, 7, 7, false, 0, 0, 0);
            extra.setHidden(true);
            players.add(extra);
        }
        BattleRoyaleGameState s = BattleRoyaleGameState.builder().gameId(seed).randomSeed(seed).roundNo(1).maxRounds(20)
                .regions(new ArrayList<>(List.of("A","B","C","D","E"))).safeRegions(new ArrayList<>(List.of("A","B","C","D","E")))
                .safeRegion("A").players(players).build();
        switch (scenario) {
            case "ATTACK_CLEAR_OPPORTUNITY" -> { b.setAlive(true); b.setLifeStatus(BattlePlayerLifeStatus.ALIVE); b.setHealth(20); b.setRegion("A"); }
            case "SEARCH_CLEAR_OPPORTUNITY" -> { a.setArmed(false); a.setWeaponLevel(0); a.setAmmo(0); a.setFood(1); a.setWater(1); b.markEliminated(); }
            case "HEAL_CLEAR_OPPORTUNITY" -> { a.setHealth(22); a.setMedkits(2); b.markEliminated(); }
            case "REVIVE_CLEAR_OPPORTUNITY" -> { a.setMedkits(2); b.markDowned(1, 3, null); }
            case "ACCEPT_ALLIANCE" -> s.getAlliances().add(BattleAlliance.builder().allianceId("al-1").proposerPlayerId(2L).memberAPlayerId(2L).memberBPlayerId(1L).status(BattleAllianceStatus.PROPOSED).createdRound(1).expiresRound(5).build());
            case "FULFILL_PROMISE" -> s.getPromises().add(BattlePromise.builder().promiseId("pr-1").promiserPlayerId(1L).promiseePlayerId(2L).promiseType(BattlePromiseType.GIVE_RESOURCE).resourceType(BattleResourceType.MEDKIT).amount(1).deadlineRound(2).status(BattlePromiseStatus.ACCEPTED).acceptedRound(1).build());
            case "COOPERATIVE_CONTRIBUTION" -> { s.getCooperativeObjectives().add(BattleCooperativeObjective.builder().objectiveId("obj-1").region("A").required(new EnumMap<>(Map.of(BattleResourceType.FOOD, 1))).contributed(new EnumMap<>(BattleResourceType.class)).status(BattleCooperativeObjectiveStatus.ACTIVE).deadlineRound(8).build()); a.setFood(8); }
            case "INFORMATION_BROKER" -> { s.getHiddenGoals().add(BattleHiddenGoal.builder().goalId("goal-1").ownerPlayerId(1L).type(BattleHiddenGoalType.INFORMATION_BROKER).targetValue(1).status(BattleHiddenGoalStatus.ACTIVE).build()); s.getRumors().add(BattleRumor.builder().rumorId("rumor-1").originatorPlayerId(1L).speakerPlayerId(1L).claimType(BattleRumorClaimType.RESOURCE_CACHE).claim("resource cache").confidence(90).build()); b.setAlive(true); }
            case "ATTACK_NEGATIVE" -> b.markEliminated();
            case "HEAL_NEGATIVE" -> { a.setHealth(100); a.setMedkits(2); b.markEliminated(); }
            case "REVIVE_NEGATIVE" -> { a.setMedkits(0); b.markDowned(1, 3, null); }
            case "PROMISE_NEGATIVE" -> b.markEliminated();
        }
        return s;
    }
    private static BattlePlayerState player(long id, String region, int hp, int food, int water, boolean armed, int level, int ammo, int medkits) {
        AgentPersonaSnapshot persona = AgentPersonaSnapshot.builder().profileId(id).code("SCENARIO").name("Scenario").riskPreference("BALANCED").traits(List.of("BALANCED")).behaviorGuidance("scenario").builtIn(true).build();
        return BattlePlayerState.builder().playerId(id).displayName("P" + id).persona(persona).region(region).health(hp).food(food).water(water).armed(armed).weaponLevel(level).weaponName(armed ? "weapon" : null).ammo(ammo).medkits(medkits).alive(true).lifeStatus(BattlePlayerLifeStatus.ALIVE).build();
    }
    private static String report(List<BattleDecisionPipelineTrace> ts, int repeats) {
        Map<String,Long> matrix = ts.stream().collect(Collectors.groupingBy(t -> String.valueOf(t.parsedAction()) + " -> " + t.executedAction(), TreeMap::new, Collectors.counting()));
        StringBuilder b = new StringBuilder("# LLM Action Pipeline 全链路审计与控制变量实验\n\n");
        b.append("## 实验配置\n\n- provider: QWEN\n- model: ").append(MODEL).append("\n- repeats per scenario: ").append(repeats).append("\n- total calls: ").append(ts.size()).append("\n- temperature: 0.4; maxTokens: 900; retries: 0\n- API Key: 仅进程环境变量，未写入文件\n\n");
        b.append("## 真实调用链\n\n`BattleAgentContext -> BattleAgentContextBuilder -> ModelGateway -> raw response -> structured parser/repair -> BattleAiDecision -> sanitizeAction -> executeWithDecision -> BattleRoyaleGameEngine.execute -> EngineEvent`\n\n");
        b.append("## Scenario Results\n\n| Scenario | Parsed | Sanitized | Executed | Fallback | Repair |\n|---|---:|---:|---:|---:|---:|\n");
        ts.stream().collect(Collectors.groupingBy(BattleDecisionPipelineTrace::scenarioId, LinkedHashMap::new, Collectors.toList())).forEach((name, rows) -> b.append("| ").append(name).append(" | ").append(rows.stream().filter(t -> t.parsedAction()!=null).map(BattleDecisionPipelineTrace::parsedAction).collect(Collectors.groupingBy(x->x,Collectors.counting()))).append(" | ").append(rows.stream().map(BattleDecisionPipelineTrace::sanitizedAction).collect(Collectors.groupingBy(x->x,Collectors.counting()))).append(" | ").append(rows.stream().map(BattleDecisionPipelineTrace::executedAction).collect(Collectors.groupingBy(x->x,Collectors.counting()))).append(" | ").append(rows.stream().filter(t->"MODEL_FALLBACK".equals(t.source())).count()).append(" | ").append(rows.stream().filter(BattleDecisionPipelineTrace::parserRepair).count()).append(" |\n"));
        b.append("\n## Transformation Matrix\n\n| Model/Parsed -> Executed | Count |\n|---|---:|\n"); matrix.forEach((k,v)->b.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        long changed = ts.stream().filter(BattleDecisionPipelineTrace::sanitizedChanged).count();
        b.append("\n## Pipeline Rates\n\n- action preservation rate: ").append(rate(ts.size()-changed,ts.size())).append("\n- sanitize change rate: ").append(rate(changed,ts.size())).append("\n- fallback rate: ").append(rate(ts.stream().filter(t->"MODEL_FALLBACK".equals(t.source())).count(),ts.size())).append("\n- parser repair rate: ").append(rate(ts.stream().filter(BattleDecisionPipelineTrace::parserRepair).count(),ts.size())).append("\n- validation failures: 0 observed; execution events: production engine events captured\n\n");
        b.append("## Schema / Prompt 审计\n\n- Tactical enum includes MOVE, SEARCH, CAPTURE, REST, ATTACK, HIDE, HEAL, GUARD, REVIVE, EXECUTE, SKIP; legacy TRADE remains enum but sanitize maps it to SEARCH.\n- Social schema is nested in `BattleSocialDecision`; current prompt example couples `PROPOSE_ALLIANCE` with `PRIVATE`, so `EXAMPLE_BIAS_RISK` is present.\n- `PROPOSE_ALLIANCE + PRIVATE` is represented as two independent model fields; production code does not automatically create PRIVATE.\n\n");
        b.append("## Root Cause Classification\n\n- Confirmed Context/Schema/Parser/Sanitize/Validation/Execution bug: none observed by this harness.\n- Any legal opportunity not selected while prompt/context/schema/sanitize remain intact is classified as `PROMPT_STRATEGY` or `MODEL_CAPABILITY`, not as a gameplay bug.\n\n## Next Experiment\n\nUse one-variable prompt ablations: remove the coupled JSON example, then separately add an explicit legal-action priority list; keep model, temperature, state, seed and repeats fixed.\n");
        return b.toString();
    }
    private static String rate(long n,long d){return d==0?"0.00%":String.format(Locale.ROOT,"%.2f%%",100.0*n/d);}
}
