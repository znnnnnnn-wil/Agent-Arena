package com.zn.agentarena.modules.battle.simulation;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class BattleSimulationMain {
    private BattleSimulationMain() {}
    public static void main(String[] args) throws Exception {
        int games=args.length>0?Integer.parseInt(args[0]):1000;
        long seed=args.length>1?Long.parseLong(args[1]):100_000L;
        Path output=Path.of(args.length>2?args[2]:"simulation-report.md").toAbsolutePath();
        BattleSimulationConfig config=new BattleSimulationConfig(BattleSimulationMode.FAST_SIMULATION,games,8,20,seed,true);
        var results=new BattleSimulationRunner().run(config);
        BattleSimulationMetrics metrics=new BattleSimulationMetrics(config,results);
        Files.writeString(output,metrics.markdown(),StandardCharsets.UTF_8);
        System.out.println("FAST_SIMULATION games="+games+", failures="+metrics.failures()+", report="+output);
    }
}
