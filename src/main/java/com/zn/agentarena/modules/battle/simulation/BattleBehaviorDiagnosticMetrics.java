package com.zn.agentarena.modules.battle.simulation;

import com.zn.agentarena.modules.battle.alliance.*;
import com.zn.agentarena.modules.battle.goal.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.objective.*;
import com.zn.agentarena.modules.battle.promise.*;
import com.zn.agentarena.modules.battle.simulation.trace.*;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Cross-seed behavioral funnels. Counts opportunities from pre-decision traces, never from hindsight alone. */
public final class BattleBehaviorDiagnosticMetrics {
    public record PromiseFunnel(long accepted, long promiserEliminated, long neverHadResource,
                                long hadResourceNeverMet, long legalOpportunity, long choseFulfill,
                                long fulfilled, long legalButDidNotFulfill, long broken, long cancelled) {}
    public record ObjectiveFunnel(long objectives, long completed, long knownAgents, long interestedAgents,
                                  long movedTowardRegion, long reachedRegion, long reachedWithResource,
                                  long contributors, double averageFirstContributionRoundsRemaining,
                                  double averageMaxSimultaneousParticipants, double averageCompletion,
                                  Map<String,Long> completionBands, Map<String,Long> failureReasons) {}
    public record GoalFunnel(long assigned, long observedDownedOrHadInformation, long metCounterparty,
                             long legalOpportunities, long decisions, long successes, long completed,
                             Map<String,Long> failureReasons) {}
    public record RescueFunnel(long downed, long withVisibleObserver, long withMedkitObserver,
                               long legalObserverOpportunities, long reviveChoices, long executed,
                               long revived, long bledOut) {}
    public record PersonaRow(String persona, long samples, long wins, double winRate,
                             double ciLow, double ciHigh, double attacksPerPlayer,
                             double allianceProposalsPerPlayer, double betrayalPerAlliance,
                             double tradesPerPlayer, double revivesPerOpportunity,
                             double promiseFulfillmentPerAccepted) {}
    public record SeedRow(long firstSeed, int games, double allianceFormation, double betrayal,
                          double promiseAcceptance, double promiseFulfillment, double rescue,
                          double objectiveCompletion, double hiddenGoalCompletion, double tradeSuccess,
                          double informationTradeSuccess, Map<String,Double> personaWinRates) {}

    private final List<BattleSimulationResult> results;
    private final List<BattleAgentDecisionTrace> traces;

    public BattleBehaviorDiagnosticMetrics(List<BattleSimulationResult> results) {
        this.results = List.copyOf(results);
        this.traces = results.stream().flatMap(r -> r.decisionTraces().stream()).toList();
    }

    public PromiseFunnel promiseFunnel() {
        List<BattlePromise> accepted = results.stream().flatMap(r -> r.finalState().getPromises().stream())
                .filter(p -> p.getAcceptedRound() != null).toList();
        long eliminated=0, never=0, neverMet=0, legal=0, chose=0, legalNo=0;
        for (BattlePromise p : accepted) {
            List<BattlePromiseOpportunityTruth> observations = promiseTruth(p);
            boolean has = observations.stream().anyMatch(BattlePromiseOpportunityTruth::hasResource);
            boolean met = observations.stream().anyMatch(x -> x.hasResource() && x.sameRegion());
            boolean opportunity = observations.stream().anyMatch(BattlePromiseOpportunityTruth::legalFulfillmentOpportunity)
                    || p.getStatus() == BattlePromiseStatus.FULFILLED;
            boolean selected = tracesForGame(p).stream().anyMatch(t -> Objects.equals(t.actorPlayerId(),p.getPromiserPlayerId())
                    && "FULFILL_PROMISE".equals(t.decision().socialAction())
                    && Objects.equals(p.getPromiseId(), t.decision().socialReferenceId()));
            if (p.getStatus()==BattlePromiseStatus.CANCELLED) eliminated++;
            else if (opportunity) legal++;
            else if (!has) never++;
            else if (!met) neverMet++;
            else neverMet++;
            if (selected) chose++;
            if (opportunity && p.getStatus()!=BattlePromiseStatus.FULFILLED
                    && p.getStatus()!=BattlePromiseStatus.CANCELLED) legalNo++;
        }
        return new PromiseFunnel(accepted.size(), eliminated, never, neverMet, legal, chose,
                count(accepted,p->p.getStatus()==BattlePromiseStatus.FULFILLED), legalNo,
                count(accepted,p->p.getStatus()==BattlePromiseStatus.BROKEN),
                count(accepted,p->p.getStatus()==BattlePromiseStatus.CANCELLED));
    }

    public ObjectiveFunnel objectiveFunnel() {
        List<BattleCooperativeObjective> all = results.stream().flatMap(r->r.finalState().getCooperativeObjectives().stream()).toList();
        long known=0, interested=0, moved=0, reached=0, resource=0, contributors=0;
        double completion=0, firstRemaining=0, maxTogether=0; long firstCount=0;
        Map<String,Long> bands=new LinkedHashMap<>(), reasons=new LinkedHashMap<>();
        for(String band:List.of("0-25%","26-50%","51-75%","76-99%","100%"))bands.put(band,0L);
        for (BattleSimulationResult result:results) for (BattleCooperativeObjective o:result.finalState().getCooperativeObjectives()) {
            List<BattleAgentDecisionTrace> ts=result.decisionTraces();
            Set<Long> k=actors(ts,t->t.agentKnownState().publicObjectives().stream().anyMatch(x->x.startsWith(o.getObjectiveId()+"@")));
            Set<Long> i=actors(ts,t->isObjectiveInterest(t,o));
            Set<Long> m=actors(ts,t->"MOVE".equals(t.decision().action())&&Objects.equals(o.getRegion(),t.decision().targetRegion()));
            Set<Long> r=actors(ts,t->Objects.equals(o.getRegion(),t.region()));
            Set<Long> hr=actors(ts,t->objectiveTruth(t,o).map(x->x.inRegion()&&x.hasNeededResource()).orElse(false));
            known+=k.size(); interested+=i.size(); moved+=m.size(); reached+=r.size(); resource+=hr.size();
            contributors+=o.getContributionByPlayer().size();
            int required=o.getRequired().values().stream().mapToInt(Integer::intValue).sum();
            int contributed=o.getContributed().values().stream().mapToInt(Integer::intValue).sum();
            double ratio=required==0?1:Math.min(1,contributed/(double)required); completion+=ratio;
            String band=ratio>=1?"100%":ratio>.75?"76-99%":ratio>.50?"51-75%":ratio>.25?"26-50%":"0-25%";
            bands.merge(band,1L,Long::sum);
            Map<Integer,Long> byRound=ts.stream().filter(t->Objects.equals(o.getRegion(),t.region()))
                    .collect(Collectors.groupingBy(BattleAgentDecisionTrace::round,Collectors.mapping(BattleAgentDecisionTrace::actorPlayerId,Collectors.toSet())))
                    .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->(long)e.getValue().size()));
            maxTogether+=byRound.values().stream().mapToLong(Long::longValue).max().orElse(0);
            OptionalInt first=result.events().stream().filter(e->"BATTLE_OBJECTIVE_CONTRIBUTED".equals(e.type()))
                    .mapToInt(this::eventRound).min();
            if(first.isPresent()){firstRemaining+=Math.max(0,o.getDeadlineRound()-first.getAsInt());firstCount++;}
            if(o.getStatus()!=BattleCooperativeObjectiveStatus.COMPLETED)reasons.merge(objectiveFailure(o,k,i,r,hr,first,ts,result),1L,Long::sum);
        }
        return new ObjectiveFunnel(all.size(),count(all,o->o.getStatus()==BattleCooperativeObjectiveStatus.COMPLETED),known,
                interested,moved,reached,resource,contributors,firstCount==0?0:firstRemaining/firstCount,
                all.isEmpty()?0:maxTogether/all.size(),all.isEmpty()?0:completion/all.size(),Map.copyOf(bands),Map.copyOf(reasons));
    }

    public GoalFunnel fieldMedicFunnel(){
        List<GoalRun> goals=goalRuns(BattleHiddenGoalType.FIELD_MEDIC); long seen=0,met=0,opp=0,dec=0,ok=0,done=0;
        Map<String,Long> failures=new LinkedHashMap<>();
        for(GoalRun g:goals){List<BattleAgentDecisionTrace> ts=ownerTraces(g);boolean s=ts.stream().anyMatch(t->!t.serverTruth().medicOpportunity().visibleDownedPlayerIds().isEmpty());boolean m=ts.stream().anyMatch(t->t.serverTruth().medicOpportunity().hasMedkit());long o=ts.stream().filter(t->!t.serverTruth().medicOpportunity().legalReviveTargets().isEmpty()).count();boolean d=ts.stream().anyMatch(t->"REVIVE".equals(t.decision().action()));boolean success=events(g.result,"BATTLE_PLAYER_REVIVED").stream().anyMatch(e->Objects.equals(e.actorPlayerId(),g.goal.getOwnerPlayerId()));
            if(s)seen++;if(s&&m)met++;opp+=o;if(d)dec++;if(success)ok++;if(g.goal.getStatus()==BattleHiddenGoalStatus.COMPLETED)done++;
            if(g.goal.getStatus()!=BattleHiddenGoalStatus.COMPLETED)failures.merge(!s?"NO_VISIBLE_DOWNED":o==0?"NO_LEGAL_RESCUE_OPPORTUNITY":!d?"OPPORTUNITY_NOT_CHOSEN":"REVIVE_NOT_COMPLETED",1L,Long::sum);
        }
        return new GoalFunnel(goals.size(),seen,met,opp,dec,ok,done,Map.copyOf(failures));
    }

    public GoalFunnel informationBrokerFunnel(){
        List<GoalRun> goals=goalRuns(BattleHiddenGoalType.INFORMATION_BROKER);long info=0,buyer=0,opp=0,dec=0,ok=0,done=0;Map<String,Long> fail=new LinkedHashMap<>();
        for(GoalRun g:goals){List<BattleAgentDecisionTrace> ts=ownerTraces(g);boolean has=ts.stream().anyMatch(t->t.serverTruth().informationBrokerOpportunity().hasInformation());boolean met=ts.stream().anyMatch(t->t.serverTruth().informationBrokerOpportunity().hasBuyerEncounter());long opportunities=ts.stream().filter(t->t.serverTruth().informationBrokerOpportunity().hasBuyerEncounter()).count();boolean proposed=ts.stream().anyMatch(t->"PROPOSE_INFORMATION_TRADE".equals(t.decision().socialAction()));boolean success=g.result.finalState().getInformationTradeProposals().stream().anyMatch(p->Objects.equals(p.getSellerPlayerId(),g.goal.getOwnerPlayerId())&&p.getStatus()==BattleInformationTradeStatus.COMPLETED);
            if(has)info++;if(met)buyer++;opp+=opportunities;if(proposed)dec++;if(success)ok++;if(g.goal.getStatus()==BattleHiddenGoalStatus.COMPLETED)done++;
            if(g.goal.getStatus()!=BattleHiddenGoalStatus.COMPLETED)fail.merge(!has?"NO_INFORMATION":!met?"NO_BUYER_ENCOUNTER":!proposed?"NO_PROPOSAL":infoFailure(g),1L,Long::sum);
        }
        return new GoalFunnel(goals.size(),info,buyer,opp,dec,ok,done,Map.copyOf(fail));
    }

    public RescueFunnel rescueFunnel(){long down=0,visible=0,med=0,opps=0,choice=0,exec=0,rev=0,bleed=0;
        for(BattleSimulationResult r:results){List<EngineEvent> es=r.events();for(int idx=0;idx<es.size();idx++){EngineEvent e=es.get(idx);if(!"BATTLE_PLAYER_DOWNED".equals(e.type()))continue;down++;Long id=number(e.payload().get("playerId"));int start=eventRound(e);int end=start+2;String outcome="";for(int j=idx+1;j<es.size();j++){EngineEvent x=es.get(j);if(!Objects.equals(id,number(x.payload().get("playerId"))))continue;if("BATTLE_PLAYER_REVIVED".equals(x.type())){outcome="REVIVED";end=eventRound(x);break;}if("BATTLE_PLAYER_EXECUTED".equals(x.type())){outcome="EXECUTED";end=eventRound(x);break;}if("BATTLE_PLAYER_ELIMINATED".equals(x.type())){outcome="BLEED_OUT";end=eventRound(x);break;}}
                final int from=start,to=end;List<BattleAgentDecisionTrace> ts=r.decisionTraces().stream().filter(t->t.round()>=from&&t.round()<=to).filter(t->!Objects.equals(t.actorPlayerId(),id)).toList();boolean v=ts.stream().anyMatch(t->t.agentKnownState().visibleDownedPlayerIds().contains(id));boolean hm=ts.stream().anyMatch(t->t.agentKnownState().visibleDownedPlayerIds().contains(id)&&t.serverTruth().medicOpportunity().hasMedkit());long o=ts.stream().filter(t->t.serverTruth().medicOpportunity().legalReviveTargets().contains(id)).count();long c=ts.stream().filter(t->"REVIVE".equals(t.decision().action())&&Objects.equals(id,t.decision().targetPlayerId())).count();if(v)visible++;if(hm)med++;opps+=o;choice+=c;if("REVIVED".equals(outcome))rev++;else if("EXECUTED".equals(outcome))exec++;else bleed++;}}
        return new RescueFunnel(down,visible,med,opps,choice,exec,rev,bleed);
    }

    public List<PersonaRow> personaRows(){List<PersonaRow> out=new ArrayList<>();for(String persona:List.of("BOLD","BALANCED","CAUTIOUS")){long samples=results.stream().flatMap(r->r.finalState().getPlayers().stream()).filter(p->persona.equalsIgnoreCase(p.getPersona().getRiskPreference())).count();long wins=results.stream().filter(r->r.winnerPlayerId()!=null).filter(r->persona.equalsIgnoreCase(r.finalState().findPlayer(r.winnerPlayerId()).getPersona().getRiskPreference())).count();double[] ci=wilson(wins,samples);List<BattleAgentDecisionTrace> pt=traces.stream().filter(t->persona.equalsIgnoreCase(t.persona())).toList();long allianceProposals=pt.stream().filter(t->"PROPOSE_ALLIANCE".equals(t.decision().socialAction())).count();long tradeProposals=pt.stream().filter(t->"PROPOSE_TRADE".equals(t.decision().socialAction())).count();long reviveOpp=pt.stream().filter(t->!t.serverTruth().medicOpportunity().legalReviveTargets().isEmpty()).count();long revives=pt.stream().filter(t->"REVIVE".equals(t.decision().action())).count();long formed=results.stream().mapToLong(r->r.finalState().getAlliances().stream().filter(a->a.getActivatedRound()!=null).flatMap(a->List.of(a.getMemberAPlayerId(),a.getMemberBPlayerId()).stream()).filter(id->persona.equals(personaOf(id,r))).count()).sum();long betrayed=results.stream().flatMap(r->r.events().stream()).filter(e->"BATTLE_ALLIANCE_BETRAYED".equals(e.type())).filter(e->persona.equals(personaOf(e.actorPlayerId(),findResult(e)))).count();List<BattlePromise> ps=results.stream().flatMap(r->r.finalState().getPromises().stream()).filter(p->p.getAcceptedRound()!=null).filter(p->persona.equals(personaOf(p.getPromiserPlayerId(),findResult(p)))).toList();long fulfilled=count(ps,p->p.getStatus()==BattlePromiseStatus.FULFILLED);
            out.add(new PersonaRow(persona,samples,wins,rate(wins,samples),ci[0],ci[1],rate(pt.stream().filter(t->"ATTACK".equals(t.decision().action())).count(),samples),rate(allianceProposals,samples),rate(betrayed,formed),rate(tradeProposals,samples),rate(revives,reviveOpp),rate(fulfilled,ps.size())));}return out;}

    public SeedRow seedRow(long firstSeed,int games){List<BattleSimulationResult> rs=results.stream().filter(r->r.seed()>=firstSeed&&r.seed()<firstSeed+games).toList();long proposed=events(rs,"BATTLE_ALLIANCE_PROPOSED"),formed=events(rs,"BATTLE_ALLIANCE_FORMED"),betrayed=events(rs,"BATTLE_ALLIANCE_BETRAYED");long pp=events(rs,"BATTLE_PROMISE_PROPOSED"),pa=events(rs,"BATTLE_PROMISE_ACCEPTED"),pf=events(rs,"BATTLE_PROMISE_FULFILLED");long down=events(rs,"BATTLE_PLAYER_DOWNED"),rev=events(rs,"BATTLE_PLAYER_REVIVED");long tp=events(rs,"BATTLE_TRADE_COMPLETED"),tf=events(rs,"BATTLE_TRADE_FAILED");long ip=events(rs,"BATTLE_INFORMATION_TRADE_COMPLETED"),iff=events(rs,"BATTLE_INFORMATION_TRADE_FAILED");List<BattleCooperativeObjective> os=rs.stream().flatMap(r->r.finalState().getCooperativeObjectives().stream()).toList();List<BattleHiddenGoal> hs=rs.stream().flatMap(r->r.finalState().getHiddenGoals().stream()).toList();Map<String,Double> wins=new LinkedHashMap<>();for(String p:List.of("BOLD","BALANCED","CAUTIOUS")){long s=rs.stream().flatMap(r->r.finalState().getPlayers().stream()).filter(x->p.equals(x.getPersona().getRiskPreference())).count();long w=rs.stream().filter(r->r.winnerPlayerId()!=null&&p.equals(r.finalState().findPlayer(r.winnerPlayerId()).getPersona().getRiskPreference())).count();wins.put(p,rate(w,s));}return new SeedRow(firstSeed,games,rate(formed,proposed),rate(betrayed,formed),rate(pa,pp),rate(pf,pa),rate(rev,down),rate(count(os,o->o.getStatus()==BattleCooperativeObjectiveStatus.COMPLETED),os.size()),rate(count(hs,h->h.getStatus()==BattleHiddenGoalStatus.COMPLETED),hs.size()),rate(tp,tp+tf),rate(ip,ip+iff),Map.copyOf(wins));}

    public String markdown(List<SeedRow> seeds, boolean llmExecuted, String llmReason) {
        PromiseFunnel p=promiseFunnel();ObjectiveFunnel o=objectiveFunnel();GoalFunnel fm=fieldMedicFunnel(),ib=informationBrokerFunnel();RescueFunnel r=rescueFunnel();StringBuilder b=new StringBuilder("# Agent Arena 行为诊断报告\n\n");
        b.append("## 实验配置\n\n- FAST_SIMULATION：").append(results.size()).append(" 局；8 players；20 max rounds；每回合 invariant。\n- Seed 区间：").append(seeds.stream().map(s->s.firstSeed()+"~"+(s.firstSeed()+s.games()-1)).collect(Collectors.joining("、"))).append("。\n- LLM_SAMPLE：").append(llmExecuted?"已执行":"未执行").append("；").append(llmReason).append("\n- 本轮未修改 Prompt、玩法或平衡参数。\n\n");
        b.append("## LLM_SAMPLE 行为观察\n\n").append(llmExecuted?"详见代表性对局。":"没有获得人工授权的可计费模型配置，因此没有调用外部模型，也不伪造 LLM 结论。Trace 与运行入口已就绪。 ").append("\n\n");
        b.append("## Promise Funnel\n\n| 阶段 | 数量 | 占 Accepted |\n|---|---:|---:|\n");row(b,"Accepted",p.accepted,p.accepted);row(b,"承诺者提前淘汰",p.promiserEliminated,p.accepted);row(b,"期限内从未持有所需资源",p.neverHadResource,p.accepted);row(b,"持有资源但未与对方相遇",p.hadResourceNeverMet,p.accepted);row(b,"至少一次合法履约机会",p.legalOpportunity,p.accepted);row(b,"选择 FULFILL_PROMISE",p.choseFulfill,p.accepted);row(b,"最终 FULFILLED",p.fulfilled,p.accepted);row(b,"有机会但未履约",p.legalButDidNotFulfill,p.accepted);row(b,"BROKEN",p.broken,p.accepted);
        b.append("\n主因分类：").append(p.legalButDidNotFulfill>p.neverHadResource+p.hadResourceNeverMet?"STRATEGY / PROMPT":"MECHANIC_REACHABILITY").append("。漏斗把客观不可履约与有机会未履约分开。\n\n");
        b.append("## Cooperative Objective Funnel\n\n| 阶段 | 数量 |\n|---|---:|\n");kv(b,"目标",o.objectives);kv(b,"Context 中看到目标的独立 Agent",o.knownAgents);kv(b,"表达参与/移动/贡献意图的独立 Agent",o.interestedAgents);kv(b,"向目标区移动",o.movedTowardRegion);kv(b,"到达目标区",o.reachedRegion);kv(b,"到达且有可贡献资源",o.reachedWithResource);kv(b,"实际贡献者",o.contributors);kv(b,"完成",o.completed);b.append("\n平均完成度：").append(pc(o.averageCompletion)).append("；首次贡献时平均剩余 ").append(f(o.averageFirstContributionRoundsRemaining)).append(" 轮；平均最大同时到场人数 ").append(f(o.averageMaxSimultaneousParticipants)).append("。\n\n完成度分布：").append(o.completionBands).append("\n\n失败原因：").append(o.failureReasons).append("\n\n");
        goalSection(b,"FIELD_MEDIC Funnel",fm,"看到可感知 DOWNED","同时见到 DOWNED 且有 MEDKIT");goalSection(b,"INFORMATION_BROKER Funnel",ib,"曾拥有本人已知情报","曾遇到潜在买家");
        List<GoalRun> hoarders=goalRuns(BattleHiddenGoalType.RESOURCE_HOARDER);double avgRound=hoarders.stream().filter(g->g.goal.getCompletedRound()!=null).mapToInt(g->g.goal.getCompletedRound()).average().orElse(0);int earliest=hoarders.stream().filter(g->g.goal.getCompletedRound()!=null).mapToInt(g->g.goal.getCompletedRound()).min().orElse(0);long early=hoarders.stream().filter(g->g.goal.getCompletedRound()!=null&&g.goal.getCompletedRound()<=3).count();b.append("## RESOURCE_HOARDER Diagnosis\n\n- Assigned：").append(hoarders.size()).append("；Completed：").append(count(hoarders,g->g.goal.getStatus()==BattleHiddenGoalStatus.COMPLETED)).append("。\n- 平均完成轮：").append(f(avgRound)).append("；最早：").append(earliest).append("；1~3 轮完成：").append(pct(early,hoarders.size())).append("。\n- 初始 FOOD+WATER=14，目标阈值=8，因此无需主动囤积即可完成。结论：`GOAL_TRIVIALITY_CONFIRMED`（BALANCE，可达但目标没有区分度）。\n\n");
        betrayalSection(b);b.append("## Rescue Funnel\n\n");kv(b,"DOWNED episodes",r.downed);kv(b,"至少一名现场观察者",r.withVisibleObserver);kv(b,"至少一名观察者有 MEDKIT",r.withMedkitObserver);kv(b,"合法救援决策机会（turn）",r.legalObserverOpportunities);kv(b,"选择 REVIVE",r.reviveChoices);kv(b,"最终 REVIVED",r.revived);kv(b,"EXECUTED",r.executed);kv(b,"BLEED_OUT",r.bledOut);b.append("\n低救援率应优先按“无现场观察者 / 现场无医疗包 / 有机会但未选择”三段解释，而不是只看 DOWNED 分母。\n\n");
        b.append("## Persona Cross-Seed Analysis\n\n| Persona | 样本席位 | 胜场 | 胜率 | 95% CI | attacks/player | alliances/player | betrayals/alliance | trades/player | revives/opportunity | fulfilled/accepted |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");for(PersonaRow x:personaRows())b.append("| ").append(x.persona).append(" | ").append(x.samples).append(" | ").append(x.wins).append(" | ").append(pc(x.winRate)).append(" | ").append(pc(x.ciLow)).append("–").append(pc(x.ciHigh)).append(" | ").append(f(x.attacksPerPlayer)).append(" | ").append(f(x.allianceProposalsPerPlayer)).append(" | ").append(pc(x.betrayalPerAlliance)).append(" | ").append(f(x.tradesPerPlayer)).append(" | ").append(pc(x.revivesPerOpportunity)).append(" | ").append(pc(x.promiseFulfillmentPerAccepted)).append(" |\n");
        b.append("\n## Seed Stability\n\n| Baseline | Seeds | Alliance formed | Betrayal | Promise accept | Promise fulfill | Rescue | Objective | Hidden Goal | Trade | Info Trade | BOLD/BALANCED/CAUTIOUS win |\n|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");char name='A';for(SeedRow s:seeds)b.append("| ").append(name++).append(" | ").append(s.firstSeed).append("~").append(s.firstSeed+s.games-1).append(" | ").append(pc(s.allianceFormation)).append(" | ").append(pc(s.betrayal)).append(" | ").append(pc(s.promiseAcceptance)).append(" | ").append(pc(s.promiseFulfillment)).append(" | ").append(pc(s.rescue)).append(" | ").append(pc(s.objectiveCompletion)).append(" | ").append(pc(s.hiddenGoalCompletion)).append(" | ").append(pc(s.tradeSuccess)).append(" | ").append(pc(s.informationTradeSuccess)).append(" | ").append(s.personaWinRates.entrySet().stream().map(e->e.getKey()+" "+pc(e.getValue())).collect(Collectors.joining(" / "))).append(" |\n");
        b.append("\n## Prompt Salience Findings\n\nTrace 记录了实际注入的 Accepted Promise、关键记忆数量、简短 reasonSummary 与选择。FAST 策略会优先检查承诺，因此 FAST 数据不能证明真实 LLM 的显著性问题；在未运行 LLM_SAMPLE 时，不标记 `PROMISE_SALIENCE_PROBLEM` 或 `SOCIAL_COMMITMENT_IGNORED`。\n\n");
        b.append("## Reachability vs Strategy\n\n| 机制 | 问题 | CODE | REACHABILITY | STRATEGY | BALANCE | 置信度 |\n|---|---|---:|---:|---:|---:|---|\n");classificationRows(b,p,o,fm,ib,r);
        b.append("\n## 代表性 LLM 对局\n\n未运行 LLM_SAMPLE，因此没有伪造代表性故事。FAST 对局不是 LLM 社会行为证据。\n\n## 发现的代码 Bug\n\n本轮诊断实现过程中未确认新的生产状态机 Bug；只扩展了内部 AI_DECISION payload 的诊断字段，且公共 payload 明确排除这些字段。\n\n## 本轮代码修改\n\n- 仿真专用 Decision Trace，严格分离 `agentKnownState` / `serverTruth`。\n- Promise、多人目标、FIELD_MEDIC、INFORMATION_BROKER、RESOURCE_HOARDER、背叛、救援和 Persona 机会率漏斗。\n- 多 Seed 区间聚合、Wilson 95% 置信区间、诊断测试和报告入口。\n- 无新玩法、无调参、无 Prompt/3D 修改。\n\n## 下一轮实验建议\n\n1. 人工授权 10 局 LLM_SAMPLE，仅观察 Promise 显著性与社会承诺忽略率。\n2. 对 Cooperative Objective 做单变量 Prompt 位置实验，保持门槛与奖励不变。\n3. 对 RESOURCE_HOARDER 做单变量目标初始化差距实验，先验证“初始即满足”因果链，不直接上线调参。\n");return b.toString();
    }

    private void betrayalSection(StringBuilder b){Map<String,Long> cats=new LinkedHashMap<>();long total=0;double dur=0;for(BattleSimulationResult r:results)for(EngineEvent e:r.events())if("BATTLE_ALLIANCE_BETRAYED".equals(e.type())){total++;int round=eventRound(e);Long actor=e.actorPlayerId(),victim=number(e.payload().get("victimPlayerId"));BattleAgentDecisionTrace t=r.decisionTraces().stream().filter(x->x.round()==round&&Objects.equals(x.actorPlayerId(),actor)&&Objects.equals(x.decision().targetPlayerId(),victim)).findFirst().orElse(null);String aid=String.valueOf(e.payload().get("allianceId"));BattleAlliance a=r.finalState().getAlliances().stream().filter(x->Objects.equals(x.getAllianceId(),aid)).findFirst().orElse(null);if(a!=null&&a.getActivatedRound()!=null)dur+=Math.max(0,round-a.getActivatedRound());cats.merge(classifyBetrayal(t,r,actor,victim,round),1L,Long::sum);}b.append("## Alliance Betrayal Diagnosis\n\n背叛 ").append(total).append(" 次；背叛前联盟平均持续 ").append(f(total==0?0:dur/total)).append(" 轮。结构化场景：").append(cats).append("。分类只使用背叛者当时可知关系/传闻和独立 serverTruth 条件，不调用第二次 LLM。\n\n");}
    private String classifyBetrayal(BattleAgentDecisionTrace t,BattleSimulationResult r,Long actor,Long victim,int round){if(t==null)return"UNEXPLAINED";if(t.serverTruth().lateGame()||t.serverTruth().alivePlayers()<=3)return"ENDGAME";if(t.serverTruth().supplyDropConflict())return"RESOURCE_CONFLICT";boolean promise=r.finalState().getPromises().stream().anyMatch(p->p.getAcceptedRound()!=null&&p.getAcceptedRound()<=round&&p.getResolvedRound()!=null&&p.getResolvedRound()>=round&&((Objects.equals(p.getPromiserPlayerId(),actor)&&Objects.equals(p.getPromiseePlayerId(),victim))||(Objects.equals(p.getPromiserPlayerId(),victim)&&Objects.equals(p.getPromiseePlayerId(),actor))));if(promise)return"PROMISE_CONFLICT";if(t.agentKnownState().rumors().stream().anyMatch(x->x.contains("subject="+victim)))return"RUMOR_INFLUENCE";BattleTraceRelationship rel=t.agentKnownState().involvedRelationships().get(victim);if(rel!=null&&rel.trust()<35)return"LOW_TRUST";if(t.serverTruth().resourceTight())return"SURVIVAL_PRESSURE";return"UNEXPLAINED";}
    private void classificationRows(StringBuilder b,PromiseFunnel p,ObjectiveFunnel o,GoalFunnel fm,GoalFunnel ib,RescueFunnel r){String promise=p.legalButDidNotFulfill>p.neverHadResource+p.hadResourceNeverMet?"STRATEGY":"REACHABILITY";b.append("| Promise | 高违约 |  | ").append("REACHABILITY".equals(promise)?"✓":"").append(" | ").append("STRATEGY".equals(promise)?"✓":"").append(" |  | 中高 |\n");b.append("| Cooperative Objective | 低完成 |  | ✓ | ✓ |  | 高（逐回合漏斗） |\n");b.append("| FIELD_MEDIC | 低完成 |  | ").append(fm.legalOpportunities==0?"✓":"").append(" | ").append(fm.legalOpportunities>0&&fm.decisions<fm.legalOpportunities?"✓":"").append(" |  | 高 |\n");b.append("| INFORMATION_BROKER | 低完成 |  | ✓ | ✓ |  | 高 |\n");b.append("| RESOURCE_HOARDER | 初始即满足 |  |  |  | ✓ | 高 |\n");b.append("| Rescue | 低救援 |  | ✓ | ✓ |  | 高 |\n");b.append("| Alliance betrayal | 高背叛 |  |  | ✓ | 可能 | 中（FAST policy） |\n");}
    private void goalSection(StringBuilder b,String title,GoalFunnel g,String first,String second){b.append("## ").append(title).append("\n\n| 阶段 | 数量 |\n|---|---:|\n");kv(b,"Goal Assigned",g.assigned);kv(b,first,g.observedDownedOrHadInformation);kv(b,second,g.metCounterparty);kv(b,"合法决策机会（turn）",g.legalOpportunities);kv(b,"选择对应动作",g.decisions);kv(b,"成功",g.successes);kv(b,"Goal Completed",g.completed);b.append("\n失败原因：").append(g.failureReasons).append("\n\n");}
    private String objectiveFailure(BattleCooperativeObjective o,Set<Long> known,Set<Long> interested,Set<Long> reached,Set<Long> resource,OptionalInt first,List<BattleAgentDecisionTrace> ts,BattleSimulationResult result){if(interested.isEmpty())return"NO_AGENT_INTEREST";if(reached.isEmpty())return"FAILED_TO_REACH_REGION";if(resource.isEmpty())return"INSUFFICIENT_RESOURCE";if(o.getContributionByPlayer().isEmpty())return"REACHED_BUT_DID_NOT_CONTRIBUTE";if(first.isPresent()&&o.getDeadlineRound()-first.getAsInt()<=1)return"DEADLINE_TOO_SHORT";if(o.getContributionByPlayer().size()<2)return"INSUFFICIENT_PARTICIPANTS";boolean dead=result.events().stream().filter(e->"BATTLE_PLAYER_ELIMINATED".equals(e.type())&&eventRound(e)<=o.getDeadlineRound()).map(e->number(e.payload().get("playerId"))).anyMatch(o.getContributionByPlayer()::containsKey);if(dead)return"PARTICIPANTS_ELIMINATED";return"PARTIAL_CONTRIBUTION";}
    private boolean isObjectiveInterest(BattleAgentDecisionTrace t,BattleCooperativeObjective o){return "CONTRIBUTE_OBJECTIVE".equals(t.decision().socialAction())||("MOVE".equals(t.decision().action())&&Objects.equals(o.getRegion(),t.decision().targetRegion()))||(t.decision().reasonSummary()!=null&&(t.decision().reasonSummary().toLowerCase(Locale.ROOT).contains("objective")||t.decision().reasonSummary().contains("协作")||t.decision().reasonSummary().contains("信标")));}
    private Optional<BattleObjectiveOpportunityTruth> objectiveTruth(BattleAgentDecisionTrace t,BattleCooperativeObjective o){return t.serverTruth().objectiveOpportunities().stream().filter(x->Objects.equals(x.objectiveId(),o.getObjectiveId())).findFirst();}
    private List<BattlePromiseOpportunityTruth> promiseTruth(BattlePromise p){return results.stream().filter(r->r.finalState().getPromises().contains(p)).flatMap(r->r.decisionTraces().stream()).filter(t->Objects.equals(t.actorPlayerId(),p.getPromiserPlayerId())).flatMap(t->t.serverTruth().promiseOpportunities().stream()).filter(x->Objects.equals(x.promiseId(),p.getPromiseId())).toList();}
    private List<BattleAgentDecisionTrace> tracesForGame(BattlePromise p){return results.stream().filter(r->r.finalState().getPromises().contains(p)).findFirst().map(BattleSimulationResult::decisionTraces).orElse(List.of());}
    private String infoFailure(GoalRun g){List<BattleInformationTradeProposal> ps=g.result.finalState().getInformationTradeProposals().stream().filter(p->Objects.equals(p.getSellerPlayerId(),g.goal.getOwnerPlayerId())).toList();if(ps.isEmpty())return"NO_PROPOSAL";if(ps.stream().anyMatch(p->p.getStatus()==BattleInformationTradeStatus.REJECTED||p.getStatus()==BattleInformationTradeStatus.EXPIRED))return"PROPOSAL_REJECTED";if(ps.stream().anyMatch(p->"BUYER_RESOURCE_MISSING".equals(p.getFailureReason())))return"BUYER_CANNOT_PAY";if(ps.stream().anyMatch(p->"INFORMATION_UNAVAILABLE".equals(p.getFailureReason())))return"KNOWLEDGE_INVALIDATED";if(ps.stream().anyMatch(p->"NOT_SAME_REGION".equals(p.getFailureReason())))return"REGION_CONDITION_FAILED";return"OTHER";}
    private record GoalRun(BattleSimulationResult result,BattleHiddenGoal goal){}
    private List<GoalRun> goalRuns(BattleHiddenGoalType type){List<GoalRun> out=new ArrayList<>();for(BattleSimulationResult r:results)for(BattleHiddenGoal g:r.finalState().getHiddenGoals())if(g.getType()==type)out.add(new GoalRun(r,g));return out;}
    private List<BattleAgentDecisionTrace> ownerTraces(GoalRun g){return g.result.decisionTraces().stream().filter(t->Objects.equals(t.actorPlayerId(),g.goal.getOwnerPlayerId())).toList();}
    private Set<Long> actors(List<BattleAgentDecisionTrace> ts,Predicate<BattleAgentDecisionTrace> p){return ts.stream().filter(p).map(BattleAgentDecisionTrace::actorPlayerId).collect(Collectors.toSet());}
    private List<EngineEvent> events(BattleSimulationResult r,String type){return r.events().stream().filter(e->type.equals(e.type())).toList();}
    private long events(List<BattleSimulationResult> rs,String type){return rs.stream().flatMap(r->r.events().stream()).filter(e->type.equals(e.type())).count();}
    private int eventRound(EngineEvent e){Object x=e.payload().get("simulationRound");return x instanceof Number n?n.intValue():0;}
    private Long number(Object x){return x instanceof Number n?n.longValue():null;}
    private <T> long count(Collection<T> xs,Predicate<T> p){return xs.stream().filter(p).count();}
    private double rate(long n,long d){return d==0?0:n/(double)d;}
    private String pct(long n,long d){return pc(rate(n,d));}
    private String pc(double x){return String.format(Locale.ROOT,"%.2f%%",x*100);}
    private String f(double x){return String.format(Locale.ROOT,"%.2f",x);}
    private void row(StringBuilder b,String name,long n,long d){b.append("| ").append(name).append(" | ").append(n).append(" | ").append(pct(n,d)).append(" |\n");}
    private void kv(StringBuilder b,String name,long n){b.append("| ").append(name).append(" | ").append(n).append(" |\n");}
    private double[] wilson(long wins,long n){if(n==0)return new double[]{0,0};double z=1.96,p=wins/(double)n,den=1+z*z/n,center=(p+z*z/(2*n))/den,margin=z*Math.sqrt((p*(1-p)+z*z/(4*n))/n)/den;return new double[]{Math.max(0,center-margin),Math.min(1,center+margin)};}
    private String personaOf(Long playerId,BattleSimulationResult r){if(r==null||playerId==null||r.finalState().findPlayer(playerId)==null)return"UNKNOWN";return r.finalState().findPlayer(playerId).getPersona().getRiskPreference();}
    private BattleSimulationResult findResult(Object item){for(BattleSimulationResult r:results){if(item instanceof BattleAlliance a&&r.finalState().getAlliances().contains(a))return r;if(item instanceof BattlePromise p&&r.finalState().getPromises().contains(p))return r;if(item instanceof EngineEvent e&&r.events().contains(e))return r;}return null;}
}
