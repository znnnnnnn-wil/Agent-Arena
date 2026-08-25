package com.zn.agentarena.modules.battle.simulation;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Runs fixed-cost FAST diagnostics only. LLM_SAMPLE remains explicit and cannot be selected by CLI. */
public final class BattleBehaviorDiagnosticMain {
    private BattleBehaviorDiagnosticMain() {}
    public static void main(String[] args) throws Exception {
        int games=args.length>0?Integer.parseInt(args[0]):1000;
        Path output=Path.of(args.length>1?args[1]:"behavior-diagnostic-report.md").toAbsolutePath();
        long requestedStart=args.length>2?Long.parseLong(args[2]):-1L;
        long[] starts=requestedStart>=0?new long[]{requestedStart}:new long[]{200_000L,300_000L,400_000L};
        BattleSimulationRunner runner=new BattleSimulationRunner();
        // Baseline A is the already validated 1000-game result from simulation-report.md.
        BattleBehaviorDiagnosticMetrics.SeedRow baselineRow=new BattleBehaviorDiagnosticMetrics.SeedRow(100_000L,1000,
                .8589,.5643,.7561,.2307,.0690,0,.3706,.6562,.2844,
                Map.of("BOLD",.1717,"BALANCED",.1037,"CAUTIOUS",.0870));
        List<BattleSimulationResult> all=new ArrayList<>();
        for(long start:starts){BattleSimulationConfig c=new BattleSimulationConfig(BattleSimulationMode.FAST_SIMULATION,games,8,20,start,true);List<BattleSimulationResult> rs=runner.run(c);all.addAll(rs);long failures=rs.stream().filter(BattleSimulationResult::abnormal).count();System.out.println("FAST seeds="+start+".."+(start+games-1)+", games="+games+", failures="+failures);}
        BattleBehaviorDiagnosticMetrics metrics=new BattleBehaviorDiagnosticMetrics(all);List<BattleBehaviorDiagnosticMetrics.SeedRow> rows=new ArrayList<>();rows.add(baselineRow);for(long start:starts)rows.add(metrics.seedRow(start,games));
        Files.writeString(output,metrics.markdown(rows,false,"未获得可计费外部模型调用的人工授权/明确费用配置。"),StandardCharsets.UTF_8);
        System.out.println("report="+output+", diagnosticGames="+all.size()+", baselineGames=1000, invariantFailures="+all.stream().filter(BattleSimulationResult::abnormal).count());
    }
}
