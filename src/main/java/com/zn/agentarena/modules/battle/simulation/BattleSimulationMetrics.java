package com.zn.agentarena.modules.battle.simulation;

import com.zn.agentarena.modules.battle.alliance.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.promise.*;
import com.zn.agentarena.modules.battle.trade.*;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 聚合仿真结果并生成稳定、紧凑的 Markdown 报告。 */
public class BattleSimulationMetrics {
    private final BattleSimulationConfig config;
    private final List<BattleSimulationResult> results;
    private final Map<String,Long> events;
    private final List<SimulationWarning> warnings;

    public BattleSimulationMetrics(BattleSimulationConfig config, List<BattleSimulationResult> results) {
        this.config=config; this.results=List.copyOf(results); this.events=countEvents(results); this.warnings=detectWarnings();
    }

    public List<SimulationWarning> warnings(){ return warnings; }
    public long eventCount(String type){ return events.getOrDefault(type,0L); }
    public long failures(){ return results.stream().filter(BattleSimulationResult::abnormal).count(); }

    public String markdown() {
        StringBuilder b=new StringBuilder("# Agent Arena 大逃杀社会系统仿真报告\n\n");
        b.append("## 代码审计结论\n\n")
                .append("### Critical\n\n- 未发现。\n\n")
                .append("### High\n\n")
                .append("- `BattleSocialActionSystem.execute`：无关 PUBLIC/LOCAL 消息听众会被合并进私密 Alliance/Promise/Trade 的 knownBy；造成 AI 认知越权。**已修复**。\n")
                .append("- `BattleRumorSystem.share` + `BattleMemorySystem.rememberFact`：Rumor confidence 被写成事实 certainty=100，且缺少 sourceRumorId/speaker 与去重上限；造成 Claim/Fact 污染和长局膨胀。**已修复**。\n")
                .append("- `BattleSocialActionSystem.execute`：DOWNED 玩家仍可执行社会动作，与规则和 Prompt 冲突。**已修复**。\n\n")
                .append("### Medium\n\n")
                .append("- `BattlePromiseSystem.evaluateDeadlines`：参与者淘汰后仍可能被判违约。**已修复为 CANCELLED，且幂等**。\n")
                .append("- `BattleBetrayalDetector.detect`：直接调用时零伤害 ATTACK 也可触发背叛。**已修复**。\n")
                .append("- `BattleHiddenGoalSystem.ensureAssigned`：部分旧快照只要已有一个目标就不再补齐其他玩家。**已修复**。\n")
                .append("- `BattleAgentContextBuilder`：协议、传闻与情报交易历史无注入上限。**已修复为有界 tail**。\n")
                .append("- `BattleSocialStateInitializer.ensure`：显式 null 的 knownBy/集合字段恢复后可能 NPE。**已修复并按最小知情原则补参与者**。\n\n")
                .append("### Low\n\n- `BattleMemorySystem.rememberAction`：DOWNED 玩家不会记录全局淘汰与空投公开事件。**已修复为 contendingPlayers**。\n\n")
                .append("## 信息边界审计\n\n")
                .append("| 子系统 | 越权读取结论 |\n|---|---|\n")
                .append("| Observation | 仅同区、未隐藏或已暴露目标；不返回精确敌方 HP/弹药/护甲 |\n")
                .append("| Memory | 仅亲历、可见、公开或实际听众事件；Rumor 使用非 100 certainty |\n")
                .append("| Alliance / Promise / Trade | AI Context 仅按 knownBy；无关通信不再扩散协议 |\n")
                .append("| Rumor | 只改变 Rumor/Claim Memory，不修改世界事实或事实记忆 |\n")
                .append("| Hidden Goal | 仅 owner Context；公开完成事件不含类型 |\n")
                .append("| Information Trade | 仅买卖双方可见条款；publicPayload 不含 rumorId/price |\n")
                .append("| Replay / Public Spectator | 使用 publicPayload；内部 payload 仅房主认证回放 |\n\n")
                .append("## 规则执行顺序\n\n`Decision → Tactical validation/execution → Tactical Memory → Betrayal/Relationship → Communication delivery/Memory → Social Action → Hidden Goal evaluation → Deadline/expiry evaluation → Snapshot`\n\n")
                .append("该顺序现已由跨系统测试固定，不再依赖偶然调用结果。\n\n")
                .append("## Invariant\n\n- 资源与 HP 非负；alive 与 lifeStatus 一致。\n- ELIMINATED 不进入 turnPlayers；DOWNED 仅 SKIP，且必须有 bleedOutRound。\n- ACTIVE Alliance 成员存在且同一对玩家至多一个。\n- 已解析 Promise 必须有 resolvedRound；Trade 条目合法且库存不为负。\n- PRIVATE Message 双方存在；socialStates 覆盖全部玩家。\n- Rumor 不超过 200；Hidden Goal owner 必须存在。\n\n")
                .append("## Simulation Framework\n\n- `BattleSimulationRunner`：复用生产执行链的确定性无头策略。\n- `BattleSimulationConfig/Result/Metrics`：配置、单局记录和聚合。\n- `BattleStateInvariantChecker`：每 Turn 立即检查并报告 seed/round。\n- `BattleSimulationMain`：CLI 入口；同配置与 Seed 范围可复现。\n\n")
                .append("LLM_SAMPLE 仅能通过显式注入 `ModelGateway + ModelRuntimeConfig` 启用；默认 CLI 永远使用 FAST_SIMULATION，避免意外 API 费用。\n\n");
        b.append("## 配置\n\n- mode: ").append(config.mode()).append("\n- games: ").append(config.games())
                .append("\n- players: ").append(config.players()).append("\n- maxRounds: ").append(config.maxRounds())
                .append("\n- seed range: ").append(config.firstSeed()).append(" ~ ").append(config.firstSeed()+config.games()-1)
                .append("\n- invariant checks: ").append(config.invariantChecks()).append("\n\n");
        int[] rounds=results.stream().mapToInt(BattleSimulationResult::rounds).sorted().toArray();
        b.append("## 稳定性\n\n| 指标 | 数值 |\n|---|---:|\n")
                .append("| 成功局 | ").append(config.games()-failures()).append(" |\n| 失败/异常局 | ").append(failures()).append(" |\n")
                .append("| 平均轮数 | ").append(f(avg(rounds))).append(" |\n| 中位数轮数 | ").append(rounds.length==0?0:rounds[rounds.length/2]).append(" |\n")
                .append("| 最小 / 最大轮数 | ").append(rounds.length==0?0:rounds[0]).append(" / ").append(rounds.length==0?0:rounds[rounds.length-1]).append(" |\n")
                .append("| 最大轮结束占比 | ").append(pct(results.stream().filter(r->"MAX_ROUNDS".equals(r.terminationReason())).count(),config.games())).append(" |\n\n");
        b.append("### 平均存活人数曲线\n\n| 轮次 | 平均存活/倒地参赛者 |\n|---:|---:|\n");
        for(int round=1;round<=config.maxRounds();round++){ final int rr=round; double v=results.stream().mapToInt(r->aliveAt(r,rr)).average().orElse(0); if(v>0)b.append("| ").append(round).append(" | ").append(f(v)).append(" |\n"); }
        section(b,"核心游戏数据",List.of(
                row("攻击次数",action("ATTACK")),
                row("击倒次数",eventCount("BATTLE_PLAYER_DOWNED")), row("淘汰次数",eventCount("BATTLE_PLAYER_ELIMINATED")),
                row("处决次数",eventCount("BATTLE_PLAYER_EXECUTED")), row("暴击次数",criticalHits())));
        alliance(b); promise(b); trade(b); rescue(b); communication(b); rumor(b); infoTrade(b); hiddenGoals(b); objectives(b); persona(b); prompt(b); warnings(b); representative(b); anomalies(b);
        b.append("\n## 可疑规则与解释\n\n- 同一 Decision 的语义固定为：战术行动先执行，随后判定背叛，再通信，最后社会动作。")
                .append("因此 `LEAVE_ALLIANCE + ATTACK` 仍属于先攻击盟友并背叛；`ATTACK + ACCEPT_ALLIANCE` 不构成背叛；`MOVE + ACCEPT_TRADE` 使用移动后的区域校验；`ATTACK + PROPOSE_PROMISE` 在双方仍可用时仍会发出承诺。\n")
                .append("- Promise 只由显式 FULFILL_PROMISE 履约；普通 Trade 即使转移同类资源也不自动改变 Promise，避免无法证明转移意图。\n")
                .append("- DOWNED 只允许通信，不允许联盟、承诺、交易、传闻或协作贡献；deadline 不暂停。\n")
                .append("- FAST_SIMULATION 是确定性结构化策略，不代表真实 LLM 的语言质量；它用于状态机、可达性和策略塌缩基线。\n");
        b.append("\n## 推荐下一步\n\n1. 对警告中使用率最低的既有机制做 Prompt/策略可达性实验，不新增玩法。\n2. 用 10~20 局显式授权的 LLM_SAMPLE 对比 FAST_SIMULATION 行为分布与事件链。\n3. 将本报告阈值纳入 CI 的非阻断趋势检查，先积累多次基线再决定调参。\n");
        b.append("\n## 不要继续加什么\n\n- 暂不增加 Reputation、Evidence、Lie Detection、新目标类型、新社会 Action 或新资源。\n")
                .append("- 暂不扩展 3D；当前限制需要先从既有机制使用率、Prompt 可达性和数值数据中定位。\n")
                .append("- 在 LLM_SAMPLE 与多次基线尚未完成前，不依据单次结果直接调整伤害、医疗包、trust 或奖励。\n");
        return b.toString();
    }

    private void alliance(StringBuilder b){ long proposed=eventCount("BATTLE_ALLIANCE_PROPOSED"),formed=eventCount("BATTLE_ALLIANCE_FORMED"),left=eventCount("BATTLE_ALLIANCE_LEFT"),betrayed=eventCount("BATTLE_ALLIANCE_BETRAYED"); List<BattleAlliance> all=results.stream().flatMap(r->r.finalState().getAlliances().stream()).toList(); double duration=all.stream().filter(a->a.getActivatedRound()!=null).mapToInt(a->Math.max(0,(a.getEndedRound()==null?config.maxRounds():a.getEndedRound())-a.getActivatedRound())).average().orElse(0); long activeEnd=all.stream().filter(a->a.getStatus()==BattleAllianceStatus.ACTIVE).count(); section(b,"Alliance",List.of(row("提案数",proposed),row("每局平均形成数",f(formed/(double)config.games())),row("形成率（形成/提案）",pct(formed,proposed)),row("平均持续轮数",f(duration)),row("正常退出率",pct(left,formed)),row("背叛率",pct(betrayed,formed)),row("终局仍存活比例",pct(activeEnd,formed)))); }
    private void promise(StringBuilder b){long p=eventCount("BATTLE_PROMISE_PROPOSED"),a=eventCount("BATTLE_PROMISE_ACCEPTED"),f=eventCount("BATTLE_PROMISE_FULFILLED"),br=eventCount("BATTLE_PROMISE_BROKEN"),ex=eventCount("BATTLE_PROMISE_EXPIRED"),ca=eventCount("BATTLE_PROMISE_CANCELLED");section(b,"Promise",List.of(row("提议数",p),row("接受率",pct(a,p)),row("履约率（履约/接受）",pct(f,a)),row("违约率",pct(br,a)),row("未接受过期率",pct(ex,p)),row("参与者淘汰取消数",ca)));}
    private void trade(StringBuilder b){long p=eventCount("BATTLE_TRADE_PROPOSED"),ok=eventCount("BATTLE_TRADE_COMPLETED"),fail=eventCount("BATTLE_TRADE_FAILED");section(b,"Trade",List.of(row("提议数",p),row("完成数",ok),row("成功率",pct(ok,ok+fail)),row("条件变化失败率",pct(fail,ok+fail))));}
    private void rescue(StringBuilder b){long d=eventCount("BATTLE_PLAYER_DOWNED"),r=eventCount("BATTLE_PLAYER_REVIVED"),e=eventCount("BATTLE_PLAYER_EXECUTED"),bleed=results.stream().flatMap(x->x.events().stream()).filter(x->"BATTLE_PLAYER_ELIMINATED".equals(x.type())&&"BLEED_OUT".equals(x.payload().get("reason"))).count();section(b,"Rescue",List.of(row("DOWNED",d),row("REVIVE",r),row("救援率",pct(r,d)),row("EXECUTE",e),row("BLEED OUT",bleed)));}
    private void communication(StringBuilder b){long pub=eventCount("BATTLE_PUBLIC_MESSAGE"),local=eventCount("BATTLE_LOCAL_MESSAGE"),pri=eventCount("BATTLE_PRIVATE_MESSAGE"),total=pub+local+pri;section(b,"Communication",List.of(row("PUBLIC",pub),row("LOCAL",local),row("PRIVATE",pri),row("平均每局消息",f(total/(double)config.games())),row("PUBLIC 占比",pct(pub,total)),row("LOCAL 占比",pct(local,total)),row("PRIVATE 占比",pct(pri,total))));}
    private void rumor(StringBuilder b){List<BattleRumor> all=results.stream().flatMap(r->r.finalState().getRumors().stream()).toList();double generation=all.stream().mapToInt(BattleRumor::getGeneration).average().orElse(0),confidence=all.stream().mapToInt(BattleRumor::getConfidence).average().orElse(0);int max=all.stream().mapToInt(BattleRumor::getGeneration).max().orElse(0);long roots=all.stream().filter(r->r.getGeneration()==0).count(),unspread=all.stream().filter(r->r.getGeneration()==0).filter(root->all.stream().noneMatch(x->Objects.equals(x.getSourceRumorId(),root.getRumorId()))).count();section(b,"Rumor",List.of(row("产生/传播记录",all.size()),row("平均传播代数",f(generation)),row("最大传播代数",max),row("平均 confidence",f(confidence)),row("根传闻无人继续传播比例",pct(unspread,roots))));}
    private void infoTrade(StringBuilder b){long p=eventCount("BATTLE_INFORMATION_TRADE_PROPOSED"),ok=eventCount("BATTLE_INFORMATION_TRADE_COMPLETED"),fail=eventCount("BATTLE_INFORMATION_TRADE_FAILED");double price=results.stream().flatMap(r->r.finalState().getInformationTradeProposals().stream()).filter(x->x.getStatus()==BattleInformationTradeStatus.COMPLETED).mapToInt(x->x.getPrice().amount()).average().orElse(0);section(b,"Information Trading",List.of(row("提议数",p),row("成功数",ok),row("成功率",pct(ok,ok+fail)),row("平均价格",f(price))));}
    private void hiddenGoals(StringBuilder b) {
        var all = results.stream().flatMap(r -> r.finalState().getHiddenGoals().stream()).toList();
        long done = all.stream().filter(g -> "COMPLETED".equals(String.valueOf(g.getStatus()))).count();
        List<String[]> rows = new ArrayList<>(List.of(
                row("目标总数", all.size()),
                row("完成率", pct(done, all.size())),
                row("失败/未完成率", pct(all.size() - done, all.size()))));
        all.stream().collect(Collectors.groupingBy(g -> g.getType().name())).forEach((type, goals) -> {
            long typeDone = goals.stream().filter(g -> "COMPLETED".equals(String.valueOf(g.getStatus()))).count();
            rows.add(row(type + " 完成率", pct(typeDone, goals.size())));
        });
        section(b, "Hidden Goals", rows);
    }
    private void objectives(StringBuilder b){var all=results.stream().flatMap(r->r.finalState().getCooperativeObjectives().stream()).toList();long done=all.stream().filter(o->"COMPLETED".equals(String.valueOf(o.getStatus()))).count(),expired=all.stream().filter(o->"EXPIRED".equals(String.valueOf(o.getStatus()))).count();double people=all.stream().mapToInt(o->o.getContributionByPlayer().size()).average().orElse(0);section(b,"Cooperative Objectives",List.of(row("生成数",all.size()),row("完成率",pct(done,all.size())),row("失败率",pct(expired,all.size())),row("平均参与人数",f(people))));}
    private void persona(StringBuilder b){b.append("\n## Persona\n\n| riskPreference | 玩家样本 | 胜率 | 每人攻击 | 每人结盟提案 | 每人背叛 | 每人交易提案 | 每人救援 |\n|---|---:|---:|---:|---:|---:|---:|---:|\n");for(String risk:List.of("BOLD","BALANCED","CAUTIOUS")){long samples=results.stream().flatMap(r->r.finalState().getPlayers().stream()).filter(p->risk.equalsIgnoreCase(p.getPersona().getRiskPreference())).count();long wins=results.stream().filter(r->r.winnerPlayerId()!=null).filter(r->risk.equalsIgnoreCase(r.finalState().findPlayer(r.winnerPlayerId()).getPersona().getRiskPreference())).count();b.append("| ").append(risk).append(" | ").append(samples).append(" | ").append(pct(wins,samples)).append(" | ").append(f(personaEvents(risk,e->"BATTLE_ACTION_EXECUTED".equals(e.type())&&"ATTACK".equals(e.payload().get("action")))/Math.max(1d,samples))).append(" | ").append(f(personaEvents(risk,e->"BATTLE_ALLIANCE_PROPOSED".equals(e.type()))/Math.max(1d,samples))).append(" | ").append(f(personaEvents(risk,e->"BATTLE_ALLIANCE_BETRAYED".equals(e.type()))/Math.max(1d,samples))).append(" | ").append(f(personaEvents(risk,e->"BATTLE_TRADE_PROPOSED".equals(e.type()))/Math.max(1d,samples))).append(" | ").append(f(personaEvents(risk,e->"BATTLE_PLAYER_REVIVED".equals(e.type()))/Math.max(1d,samples))).append(" |\n");}}
    private void prompt(StringBuilder b){b.append("\n## Context Token 审计（修改后有界注入）\n\n| 轮次 | 平均字符 | 估算 token | Memory | Rumor | Message | Relationship |\n|---:|---:|---:|---:|---:|---:|---:|\n");for(int round:List.of(1,10,20)){List<BattlePromptSample>s=results.stream().map(r->r.promptSamples().get(round)).filter(Objects::nonNull).toList();if(!s.isEmpty())b.append("| ").append(round).append(" | ").append(f(s.stream().mapToInt(BattlePromptSample::characters).average().orElse(0))).append(" | ").append(f(s.stream().mapToInt(BattlePromptSample::estimatedTokens).average().orElse(0))).append(" | ").append(f(s.stream().mapToInt(BattlePromptSample::memories).average().orElse(0))).append(" | ").append(f(s.stream().mapToInt(BattlePromptSample::rumors).average().orElse(0))).append(" | ").append(f(s.stream().mapToInt(BattlePromptSample::messages).average().orElse(0))).append(" | ").append(f(s.stream().mapToInt(BattlePromptSample::relationships).average().orElse(0))).append(" |\n");}b.append("\n审计前协议、传闻与情报交易为无界全量注入；审计后上限为 Memory 30、Message 20、Rumor 20、Alliance/Promise/Trade/Information Trade 各 12，Relationship 仅当前对局其他玩家。\n");}
    private void warnings(StringBuilder b){b.append("\n## Strategy Collapse Warnings\n\n");if(warnings.isEmpty())b.append("- 未触发阈值警告。\n");else warnings.forEach(w->b.append("- **").append(w.code()).append("**: ").append(w.message()).append("（observed=").append(f(w.observedValue())).append("）\n"));}
    private void representative(StringBuilder b){b.append("\n## 代表性对局\n");results.stream().filter(r->!r.abnormal()).sorted(Comparator.comparingLong(this::socialScore).reversed()).limit(3).forEach(r->{b.append("\n### seed=").append(r.seed()).append("，winner=").append(r.winnerPlayerId()).append("，rounds=").append(r.rounds()).append("\n\n");r.events().stream().filter(this::representativeEvent).limit(14).forEach(e->b.append("- Round ").append(roundOf(e)).append(": P").append(e.actorPlayerId()==null?"系统":e.actorPlayerId()).append(" → ").append(e.type()).append(targetText(e)).append("\n"));});}
    private void anomalies(StringBuilder b){b.append("\n## 异常局\n\n");if(failures()==0)b.append("- 无。\n");else results.stream().filter(BattleSimulationResult::abnormal).limit(20).forEach(r->b.append("- seed=").append(r.seed()).append(", round=").append(r.rounds()).append(", ").append(r.failure()).append("\n"));}

    private List<SimulationWarning> detectWarnings(){List<SimulationWarning>w=new ArrayList<>();long actions=eventCount("BATTLE_ACTION_EXECUTED"),search=action("SEARCH"),messages=eventCount("BATTLE_PUBLIC_MESSAGE")+eventCount("BATTLE_LOCAL_MESSAGE")+eventCount("BATTLE_PRIVATE_MESSAGE");warn(w,search/(double)Math.max(1,actions)>.70,"POSSIBLE_STRATEGY_COLLAPSE_SEARCH","SEARCH 占全部行动超过 70%",search/(double)Math.max(1,actions));warn(w,eventCount("BATTLE_TRADE_PROPOSED")/(double)config.games()<.1,"MECHANISM_UNDERUSED_TRADE","资源交易提议每局少于 0.1",eventCount("BATTLE_TRADE_PROPOSED")/(double)config.games());warn(w,eventCount("BATTLE_ALLIANCE_FORMED")/(double)config.games()<.1,"MECHANISM_UNDERUSED_ALLIANCE","联盟形成每局少于 0.1",eventCount("BATTLE_ALLIANCE_FORMED")/(double)config.games());warn(w,eventCount("BATTLE_PLAYER_REVIVED")/(double)Math.max(1,eventCount("BATTLE_PLAYER_DOWNED"))<.02,"MECHANISM_UNDERUSED_REVIVE","救援率低于 2%",eventCount("BATTLE_PLAYER_REVIVED")/(double)Math.max(1,eventCount("BATTLE_PLAYER_DOWNED")));warn(w,eventCount("BATTLE_PRIVATE_MESSAGE")/(double)Math.max(1,messages)<.01,"CHANNEL_UNDERUSED_PRIVATE","私聊占比低于 1%",eventCount("BATTLE_PRIVATE_MESSAGE")/(double)Math.max(1,messages));warn(w,eventCount("BATTLE_RUMOR_SHARED")/(double)config.games()<.1,"MECHANISM_UNDERUSED_RUMOR","传闻传播每局少于 0.1",eventCount("BATTLE_RUMOR_SHARED")/(double)config.games());long objectives=results.stream().flatMap(r->r.finalState().getCooperativeObjectives().stream()).count(),completedObjectives=results.stream().flatMap(r->r.finalState().getCooperativeObjectives().stream()).filter(o->"COMPLETED".equals(String.valueOf(o.getStatus()))).count();warn(w,completedObjectives/(double)Math.max(1,objectives)<.05,"OBJECTIVE_COMPLETION_COLLAPSE","协作目标完成率低于 5%",completedObjectives/(double)Math.max(1,objectives));long accepted=eventCount("BATTLE_PROMISE_ACCEPTED"),broken=eventCount("BATTLE_PROMISE_BROKEN");warn(w,broken/(double)Math.max(1,accepted)>.60,"PROMISE_BREAKAGE_HIGH","承诺违约率超过 60%",broken/(double)Math.max(1,accepted));return List.copyOf(w);}
    private void warn(List<SimulationWarning>w,boolean condition,String code,String message,double value){if(condition)w.add(new SimulationWarning(code,message,value));}
    private Map<String,Long> countEvents(List<BattleSimulationResult> rs){return rs.stream().flatMap(r->r.events().stream()).collect(Collectors.groupingBy(EngineEvent::type,LinkedHashMap::new,Collectors.counting()));}
    private long action(String action){return results.stream().flatMap(r->r.events().stream()).filter(e->"BATTLE_ACTION_EXECUTED".equals(e.type())&&action.equals(e.payload().get("action"))).count();}
    private long criticalHits(){return results.stream().flatMap(r->r.events().stream()).filter(e->"BATTLE_ACTION_EXECUTED".equals(e.type())&&Boolean.TRUE.equals(e.payload().get("critical"))).count();}
    private long personaEvents(String risk,Predicate<EngineEvent> test){return results.stream().flatMap(r->r.events().stream().map(e->Map.entry(r,e))).filter(x->x.getValue().actorPlayerId()!=null).filter(x->{var p=x.getKey().finalState().findPlayer(x.getValue().actorPlayerId());return p!=null&&risk.equalsIgnoreCase(p.getPersona().getRiskPreference());}).map(Map.Entry::getValue).filter(test).count();}
    private long socialScore(BattleSimulationResult r){return r.events().stream().filter(this::representativeEvent).count();}
    private int aliveAt(BattleSimulationResult result,int round){return result.aliveByRound().entrySet().stream().filter(e->e.getKey()<=round).max(Map.Entry.comparingByKey()).map(Map.Entry::getValue).orElse(config.players());}
    private boolean representativeEvent(EngineEvent e){return e.type().matches("BATTLE_(ALLIANCE_FORMED|ALLIANCE_BETRAYED|PROMISE_(ACCEPTED|FULFILLED|BROKEN)|TRADE_COMPLETED|PLAYER_(REVIVED|EXECUTED)|RUMOR_SHARED|INFORMATION_TRADE_COMPLETED|OBJECTIVE_COMPLETED)");}
    private int roundOf(EngineEvent e){Object r=e.payload().get("simulationRound");if(r==null)r=e.payload().get("round");return r instanceof Number n?n.intValue():0;}
    private String targetText(EngineEvent e){Object t=e.payload().get("targetPlayerId");if(t==null)t=e.payload().get("victimPlayerId");if(t==null)t=e.payload().get("playerId");return t==null?"":" P"+t;}
    private void section(StringBuilder b,String title,List<String[]> rows){b.append("\n## ").append(title).append("\n\n| 指标 | 数值 |\n|---|---:|\n");rows.forEach(r->b.append("| ").append(r[0]).append(" | ").append(r[1]).append(" |\n"));}
    private String[] row(String name,Object value){return new String[]{name,String.valueOf(value)};}
    private double avg(int[] values){return Arrays.stream(values).average().orElse(0);}
    private String pct(long n,long d){return f(100d*n/Math.max(1,d))+"%";}
    private String f(double value){return String.format(Locale.ROOT,"%.2f",value);}
}
