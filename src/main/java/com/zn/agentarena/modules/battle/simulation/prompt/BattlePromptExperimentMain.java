package com.zn.agentarena.modules.battle.simulation.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import com.zn.agentarena.modules.battle.agent.*;
import com.zn.agentarena.modules.battle.alliance.*;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.social.*;
import com.zn.agentarena.modules.battle.simulation.trace.BattleDecisionPipelineTrace;
import com.zn.agentarena.modules.model.domain.*;
import com.zn.agentarena.modules.model.gateway.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** qwen-turbo Prompt A/B 消融实验；不修改生产 Prompt。 */
public final class BattlePromptExperimentMain {
    private static final String MODEL = "qwen-turbo";
    private static final long EXPERIMENT_SEED = 1_100_000L;
    private BattlePromptExperimentMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "lengths".equalsIgnoreCase(args[0])) {
            printPromptLengths();
            return;
        }
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("DASHSCOPE_API_KEY is required");
        int repeats = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        Path reportPath = Path.of(args.length > 1 ? args[1] : "prompt-ablation-report.md").toAbsolutePath();
        Path tracePath = Path.of(args.length > 2 ? args[2] : "prompt-ablation-traces.jsonl").toAbsolutePath();
        int startVariant = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int endVariant = args.length > 4 ? Integer.parseInt(args[4]) : BattlePromptVariant.values().length - 1;
        AgentArenaProperties properties = new AgentArenaProperties(null, null, null);
        ObjectMapper mapper = new ObjectMapper();
        ModelGateway gateway = new LangChain4jModelGateway(
                new OpenAiCompatibleModelFactory(new ModelEndpointPolicy(properties)), mapper,
                new ModelCallConcurrencyLimiter(properties));
        ModelRuntimeConfig config = new ModelRuntimeConfig(ModelProvider.QWEN, MODEL, null, key,
                0.4, 900, Duration.ofSeconds(45), 0);
        BattleAiGameRunner runner = new BattleAiGameRunner(gateway, new BattleAgentContextBuilder());
        BattlePromptExperimentBuilder promptBuilder = new BattlePromptExperimentBuilder();
        List<BattlePromptExperimentResult> results = new ArrayList<>();

        if (startVariant == 0) {
            runVariant(BattlePromptVariant.P0_CURRENT, repeats, config, runner, promptBuilder, results);
            boolean reproduced = results.stream().allMatch(r -> "MOVE".equals(r.trace().parsedAction()));
            if (!reproduced) {
                Files.writeString(reportPath, report(results, repeats, false), StandardCharsets.UTF_8);
                writeTraces(tracePath, results, mapper);
                throw new IllegalStateException("P0 did not reproduce all-MOVE; stopped before ablations");
            }
            startVariant = 1;
        }
        for (int index = startVariant; index <= endVariant; index++) {
            runVariant(BattlePromptVariant.values()[index], repeats, config, runner, promptBuilder, results);
            Files.writeString(reportPath, report(results, repeats, true), StandardCharsets.UTF_8);
            writeTraces(tracePath, results, mapper);
        }
        Files.writeString(reportPath, report(results, repeats, true), StandardCharsets.UTF_8);
        writeTraces(tracePath, results, mapper);
        long fallback = results.stream().filter(r -> "MODEL_FALLBACK".equals(r.trace().source())).count();
        long repair = results.stream().filter(r -> r.trace().parserRepair()).count();
        System.out.printf("calls=%d fallbacks=%d repairs=%d report=%s traces=%s%n",
                results.size(), fallback, repair, reportPath, tracePath);
    }

    private static void printPromptLengths() {
        BattleAiGameRunner runner = new BattleAiGameRunner(null, new BattleAgentContextBuilder());
        BattlePromptExperimentBuilder builder = new BattlePromptExperimentBuilder();
        for (BattlePromptVariant variant : BattlePromptVariant.values()) {
            List<Integer> chars = new ArrayList<>(), tokens = new ArrayList<>(), sections = new ArrayList<>();
            for (BattlePromptScenario scenario : BattlePromptScenario.values()) {
                BattleRoyaleGameState state = state(scenario, EXPERIMENT_SEED + scenario.ordinal() * 100L);
                BattleAgentContext context = runner.createContext(state, state.findPlayer(1L));
                BattlePromptExperimentPrompt prompt = builder.build(variant, scenario, context, EXPERIMENT_SEED, 0);
                chars.add(prompt.characters()); tokens.add(prompt.estimatedTokens()); sections.add(prompt.sectionCount());
            }
            System.out.printf("%s chars=%s tokens=%s sections=%s%n", variant, chars, tokens, sections);
        }
    }

    private static void runVariant(BattlePromptVariant variant, int repeats, ModelRuntimeConfig config,
                                   BattleAiGameRunner runner, BattlePromptExperimentBuilder promptBuilder,
                                   List<BattlePromptExperimentResult> results) {
        for (BattlePromptScenario scenario : BattlePromptScenario.values()) {
            for (int run = 0; run < repeats; run++) {
                long seed = EXPERIMENT_SEED + scenario.ordinal() * 100L + run;
                BattleRoyaleGameState state = state(scenario, seed);
                BattlePlayerState actor = state.findPlayer(1L);
                BattleAgentContext context = runner.createContext(state, actor);
                BattlePromptExperimentPrompt prompt = promptBuilder.build(variant, scenario, context, EXPERIMENT_SEED, run);
                Map<String,Object> metadata = new LinkedHashMap<>();
                metadata.put("promptVariant", variant.name());
                metadata.put("promptActionOrder", prompt.actionOrder());
                metadata.put("schemaActionOrder", List.of());
                metadata.put("schemaEnumExperiment", "NOT_APPLICABLE_TEXT_GENERATION");
                BattleDecisionPipelineTrace trace = runner.runDiagnosticTurn(state, actor, config,
                        scenario.name(), seed, run, prompt.text(), metadata);
                int position = trace.parsedAction() == null ? 0 : prompt.actionOrder().indexOf(trace.parsedAction()) + 1;
                results.add(new BattlePromptExperimentResult(variant, scenario, scenario.expectedRelevantAction(),
                        position, prompt, trace));
                System.out.printf("variant=%s scenario=%s run=%d parsed=%s sanitized=%s executed=%s position=%d%n",
                        variant, scenario, run + 1, trace.parsedAction(), trace.sanitizedAction(), trace.executedAction(), position);
            }
        }
    }

    public static BattleRoyaleGameState state(BattlePromptScenario scenario, long seed) {
        BattlePlayerState actor = player(1L, "A", 100, 7, 7, true, 2, 10, 2);
        BattlePlayerState subject = player(2L, "A", 20, 7, 7, false, 0, 0, 0);
        List<BattlePlayerState> players = new ArrayList<>(List.of(actor, subject));
        for (long id = 3; id <= 6; id++) {
            BattlePlayerState extra = player(id, "E", 100, 7, 7, false, 0, 0, 0);
            extra.setHidden(true);
            players.add(extra);
        }
        BattleRoyaleGameState state = BattleRoyaleGameState.builder().gameId(seed).randomSeed(seed)
                .roundNo(1).maxRounds(20).regions(new ArrayList<>(List.of("A","B","C","D","E")))
                .safeRegion("A").safeRegions(new ArrayList<>(List.of("A","B","C","D","E")))
                .players(players).build();
        switch (scenario) {
            case ATTACK -> { }
            case SEARCH -> { actor.setArmed(false); actor.setWeaponName(null); actor.setWeaponLevel(0); actor.setAmmo(0);
                actor.setFood(1); actor.setWater(1); actor.setMedkits(0); subject.markEliminated(); }
            case HEAL -> { actor.setHealth(20); actor.setMedkits(2); subject.markEliminated(); }
            case REVIVE -> { actor.setMedkits(2); subject.markDowned(1, 3, null);
                state.getAlliances().add(BattleAlliance.builder().allianceId("revive-alliance").proposerPlayerId(1L)
                        .memberAPlayerId(1L).memberBPlayerId(2L).createdRound(1).activatedRound(1)
                        .status(BattleAllianceStatus.ACTIVE).build()); }
        }
        BattleSocialStateInitializer initializer = new BattleSocialStateInitializer();
        initializer.ensure(state);
        if (scenario == BattlePromptScenario.REVIVE) {
            BattleRelationshipState relationship = initializer.socialState(state, 1L).getRelationships().get(2L);
            relationship.setTrust(90); relationship.setGratitude(80); relationship.setResentment(0); relationship.setSuspicion(5);
        }
        return state;
    }

    private static BattlePlayerState player(long id, String region, int hp, int food, int water,
                                            boolean armed, int level, int ammo, int medkits) {
        AgentPersonaSnapshot persona = AgentPersonaSnapshot.builder().profileId(id).code("PROMPT_ABLATION")
                .name("Balanced Survivor").description("Balances risk and long-term survival")
                .riskPreference("BALANCED").traits(List.of("BALANCED")).speakingStyle("concise")
                .behaviorGuidance("Use observable evidence and choose legal actions").builtIn(true).build();
        return BattlePlayerState.builder().playerId(id).displayName("P" + id).persona(persona).region(region)
                .health(hp).food(food).water(water).armed(armed).weaponName(armed ? "Level 2 weapon" : null)
                .weaponLevel(level).ammo(ammo).medkits(medkits).alive(true)
                .lifeStatus(BattlePlayerLifeStatus.ALIVE).build();
    }

    private static void writeTraces(Path path, List<BattlePromptExperimentResult> results, ObjectMapper mapper) throws Exception {
        StringBuilder b = new StringBuilder();
        for (BattlePromptExperimentResult result : results) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("variant", result.variant()); row.put("scenario", result.scenario());
            row.put("expectedRelevantAction", result.expectedRelevantAction());
            row.put("selectedPosition", result.selectedPosition()); row.put("prompt", result.prompt());
            row.put("pipeline", result.trace());
            b.append(mapper.writeValueAsString(row)).append('\n');
        }
        Files.writeString(path, b.toString(), StandardCharsets.UTF_8);
    }

    private static String report(List<BattlePromptExperimentResult> results, int repeats, boolean complete) {
        StringBuilder b = new StringBuilder("# Prompt A/B 消融实验与 MOVE 策略塌缩根因定位\n\n");
        b.append("## Current Prompt Anatomy\n\nCURRENT PROMPT SECTION ORDER:\n\n")
                .append("1. System instruction / knowledge boundary\n2. Persona\n3. Current observation / public world / visible players\n")
                .append("4. Memory\n5. Communication history\n6. Relationships\n7. Alliance\n8. Promise\n9. Trade\n")
                .append("10. Hidden Goal\n11. Cooperative Objective\n12. Rumor\n13. Information Trade\n14. Visible Downed\n")
                .append("15. Game Rules\n16. JSON example and output requirements\n17. Social action values\n18. Tactical action values and target constraints\n\n");
        b.append("## MOVE Bias Audit\n\n- Java enum: MOVE is first.\n- Prompt tactical list: MOVE is first.\n")
                .append("- Structured schema enum: none; gateway uses ordinary text generation plus local JSON parsing.\n")
                .append("- JSON example: tactical action is SEARCH, not MOVE; social example couples PROPOSE_ALLIANCE + PRIVATE.\n")
                .append("- Default: no null-to-MOVE default in DTO/parser/sanitize.\n- Initial risk rating: HIGH (two independent first-position exposures, pending ablation outcome).\n\n");
        b.append("## Experiment Configuration\n\n- provider/model: QWEN / qwen-turbo\n- temperature: 0.4\n")
                .append("- repeats: ").append(repeats).append(" per scenario/variant\n- scenarios: ATTACK, SEARCH, HEAL, REVIVE\n")
                .append("- real calls completed: ").append(results.size()).append("\n- P0 reproduced: ").append(complete).append("\n\n");
        b.append("## Overall Result\n\n| Variant | Relevant | MOVE | SKIP | Other | Avg chars | Est. tokens |\n|---|---:|---:|---:|---:|---:|---:|\n");
        for (BattlePromptVariant variant : BattlePromptVariant.values()) {
            List<BattlePromptExperimentResult> rows = rows(results, variant);
            if (rows.isEmpty()) continue;
            long relevant = rows.stream().filter(BattlePromptExperimentMain::relevant).count();
            long move = count(rows, "MOVE"), skip = count(rows, "SKIP");
            long other = rows.size() - relevant - move - skip;
            b.append("| ").append(variant).append(" | ").append(rate(relevant, rows.size())).append(" | ")
                    .append(rate(move, rows.size())).append(" | ").append(rate(skip, rows.size())).append(" | ")
                    .append(rate(other, rows.size())).append(" | ").append(Math.round(rows.stream().mapToInt(r -> r.prompt().characters()).average().orElse(0)))
                    .append(" | ").append(Math.round(rows.stream().mapToInt(r -> r.prompt().estimatedTokens()).average().orElse(0))).append(" |\n");
        }
        b.append("\n## Per Scenario\n");
        for (BattlePromptScenario scenario : BattlePromptScenario.values()) {
            b.append("\n### ").append(scenario).append("\n\n| Variant | Selected actions | Relevant |\n|---|---|---:|\n");
            for (BattlePromptVariant variant : BattlePromptVariant.values()) {
                List<BattlePromptExperimentResult> rows = results.stream().filter(r -> r.variant()==variant && r.scenario()==scenario).toList();
                if (rows.isEmpty()) continue;
                b.append("| ").append(variant).append(" | ").append(distribution(rows)).append(" | ")
                        .append(rows.stream().filter(BattlePromptExperimentMain::relevant).count()).append("/").append(rows.size()).append(" |\n");
            }
        }
        List<BattlePromptExperimentResult> random = rows(results, BattlePromptVariant.P5_RANDOM_ORDER);
        b.append("\n## Position Bias Analysis\n\nSelected Position Distribution (P5):\n\n");
        random.stream().filter(r -> r.selectedPosition() > 0).collect(Collectors.groupingBy(BattlePromptExperimentResult::selectedPosition,
                TreeMap::new, Collectors.counting())).forEach((p,c) -> b.append("- position ").append(p).append(": ").append(c).append("/").append(random.size()).append(" (").append(rate(c,random.size())).append(")\n"));
        b.append("\n## Example Bias Analysis\n\nP0 vs P2: ").append(compare(results, BattlePromptVariant.P0_CURRENT, BattlePromptVariant.P2_NO_EXAMPLE)).append("\n");
        b.append("\n## Context Overload Analysis\n\nP0 vs P1: ").append(compare(results, BattlePromptVariant.P0_CURRENT, BattlePromptVariant.P1_MINIMAL))
                .append("\nP0 vs P6: ").append(compare(results, BattlePromptVariant.P0_CURRENT, BattlePromptVariant.P6_NO_SOCIAL_CONTEXT))
                .append("\nP0 vs P7: ").append(compare(results, BattlePromptVariant.P0_CURRENT, BattlePromptVariant.P7_NO_PERSONA)).append("\n");
        b.append("\n## Structured Output Enum Experiment\n\nNOT_APPLICABLE: production does not send a schema enum or enable JSON mode. All variants use BattleAiDecision and the same parser.\n");
        long fallback = results.stream().filter(r -> "MODEL_FALLBACK".equals(r.trace().source())).count();
        long repair = results.stream().filter(r -> r.trace().parserRepair()).count();
        long changed = results.stream().filter(r -> r.trace().sanitizedChanged()).count();
        b.append("\n## Pipeline\n\n- fallbacks: ").append(fallback).append("\n- parser repairs: ").append(repair)
                .append("\n- sanitize changes: ").append(changed).append("\n- parsed == executed: ")
                .append(results.stream().filter(r -> Objects.equals(r.trace().parsedAction(), r.trace().executedAction())).count())
                .append("/").append(results.size()).append("\n");
        b.append("\n## reasonSummary Analysis\n\nTop summaries:\n\n");
        results.stream().map(r -> r.trace().reasonSummary()).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(12)
                .forEach(e -> b.append("- ").append(e.getValue()).append(" × ").append(e.getKey()).append("\n"));
        b.append("\n## Root Cause\n\n").append(rootCause(results)).append("\n")
                .append("\n## Model Capability Analysis\n\nModel comparison was not run: only the user-authorized qwen-turbo configuration was used. Capability is assessed only after prompt ablations.\n")
                .append("\n## Recommended Prompt Architecture\n\n1. Separate a compact tactical decision block from social context while preserving both through staged prompting.\n")
                .append("2. Present dynamically legal tactical affordances with randomized or counterbalanced order.\n")
                .append("3. Replace the single mixed tactical/social example with schema-only output constraints or balanced examples. This is a recommendation only; production Prompt was not changed.\n");
        return b.toString();
    }

    private static List<BattlePromptExperimentResult> rows(List<BattlePromptExperimentResult> all, BattlePromptVariant v) { return all.stream().filter(r -> r.variant()==v).toList(); }
    private static boolean relevant(BattlePromptExperimentResult r) { return r.expectedRelevantAction().equals(r.trace().parsedAction()); }
    private static long count(List<BattlePromptExperimentResult> rows, String action) { return rows.stream().filter(r -> action.equals(r.trace().parsedAction())).count(); }
    private static String distribution(List<BattlePromptExperimentResult> rows) { return rows.stream().map(r -> String.valueOf(r.trace().parsedAction())).collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting())).toString(); }
    private static String compare(List<BattlePromptExperimentResult> all, BattlePromptVariant a, BattlePromptVariant b) { List<BattlePromptExperimentResult> x=rows(all,a), y=rows(all,b); return "relevant " + rate(x.stream().filter(BattlePromptExperimentMain::relevant).count(),x.size()) + " -> " + rate(y.stream().filter(BattlePromptExperimentMain::relevant).count(),y.size()) + ", MOVE " + rate(count(x,"MOVE"),x.size()) + " -> " + rate(count(y,"MOVE"),y.size()); }
    private static String rootCause(List<BattlePromptExperimentResult> all) {
        double p0 = fraction(rows(all,BattlePromptVariant.P0_CURRENT));
        double p1 = fraction(rows(all,BattlePromptVariant.P1_MINIMAL));
        double p2 = fraction(rows(all,BattlePromptVariant.P2_NO_EXAMPLE));
        double p4 = fraction(rows(all,BattlePromptVariant.P4_TARGET_FIRST));
        if (p1 >= .5 && p0 < .2) return "PRIMARY: PROMPT_COMPLEXITY / CONTEXT_OVERLOAD. " + (p4 > p0+.25 ? "SECONDARY: ACTION_ORDER_BIAS." : "No strong secondary order effect.");
        if (p4 >= .5 && p0 < .2) return "PRIMARY: ACTION_ORDER_BIAS.";
        if (p2 >= .5 && p0 < .2) return "PRIMARY: FEW_SHOT / EXAMPLE_BIAS.";
        return "Ablations did not restore reliable relevant actions; qwen-turbo prompt-following/model capability remains a leading cause, subject to an authorized stronger-model comparison.";
    }
    private static double fraction(List<BattlePromptExperimentResult> rows) { return rows.isEmpty()?0:rows.stream().filter(BattlePromptExperimentMain::relevant).count()/(double)rows.size(); }
    private static String rate(long n,long d) { return d==0?"N/A":String.format(Locale.ROOT,"%.2f%%",100.0*n/d); }
}
