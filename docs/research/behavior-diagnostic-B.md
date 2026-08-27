# Agent Arena 行为诊断报告

## 实验配置

- FAST_SIMULATION：500 局；8 players；20 max rounds；每回合 invariant。
- Seed 区间：100000~100999、200000~200499。
- LLM_SAMPLE：未执行；未获得可计费外部模型调用的人工授权/明确费用配置。
- 本轮未修改 Prompt、玩法或平衡参数。

## LLM_SAMPLE 行为观察

没有获得人工授权的可计费模型配置，因此没有调用外部模型，也不伪造 LLM 结论。Trace 与运行入口已就绪。 

## Promise Funnel

| 阶段 | 数量 | 占 Accepted |
|---|---:|---:|
| Accepted | 1794 | 100.00% |
| 承诺者提前淘汰 | 257 | 14.33% |
| 期限内从未持有所需资源 | 609 | 33.95% |
| 持有资源但未与对方相遇 | 171 | 9.53% |
| 至少一次合法履约机会 | 757 | 42.20% |
| 选择 FULFILL_PROMISE | 1087 | 60.59% |
| 最终 FULFILLED | 402 | 22.41% |
| 有机会但未履约 | 355 | 19.79% |
| BROKEN | 1128 | 62.88% |

主因分类：MECHANIC_REACHABILITY。漏斗把客观不可履约与有机会未履约分开。

## Cooperative Objective Funnel

| 阶段 | 数量 |
|---|---:|
| 目标 | 500 |
| Context 中看到目标的独立 Agent | 4000 |
| 表达参与/移动/贡献意图的独立 Agent | 2066 |
| 向目标区移动 | 1539 |
| 到达目标区 | 2217 |
| 到达且有可贡献资源 | 1529 |
| 实际贡献者 | 884 |
| 完成 | 0 |

平均完成度：16.84%；首次贡献时平均剩余 4.43 轮；平均最大同时到场人数 3.60。

完成度分布：{0-25%=421, 100%=0, 76-99%=0, 26-50%=78, 51-75%=1}

失败原因：{PARTIAL_CONTRIBUTION=291, REACHED_BUT_DID_NOT_CONTRIBUTE=45, INSUFFICIENT_PARTICIPANTS=122, PARTICIPANTS_ELIMINATED=17, DEADLINE_TOO_SHORT=25}

## FIELD_MEDIC Funnel

| 阶段 | 数量 |
|---|---:|
| Goal Assigned | 1000 |
| 看到可感知 DOWNED | 669 |
| 同时见到 DOWNED 且有 MEDKIT | 221 |
| 合法决策机会（turn） | 142 |
| 选择对应动作 | 60 |
| 成功 | 60 |
| Goal Completed | 58 |

失败原因：{OPPORTUNITY_NOT_CHOSEN=48, NO_LEGAL_RESCUE_OPPORTUNITY=561, NO_VISIBLE_DOWNED=331, REVIVE_NOT_COMPLETED=2}

## INFORMATION_BROKER Funnel

| 阶段 | 数量 |
|---|---:|
| Goal Assigned | 1000 |
| 曾拥有本人已知情报 | 819 |
| 曾遇到潜在买家 | 809 |
| 合法决策机会（turn） | 5066 |
| 选择对应动作 | 205 |
| 成功 | 41 |
| Goal Completed | 41 |

失败原因：{PROPOSAL_REJECTED=54, NO_BUYER_ENCOUNTER=10, NO_INFORMATION=181, NO_PROPOSAL=620, BUYER_CANNOT_PAY=51, REGION_CONDITION_FAILED=23, OTHER=20}

## RESOURCE_HOARDER Diagnosis

- Assigned：1000；Completed：1000。
- 平均完成轮：1.13；最早：1；1~3 轮完成：100.00%。
- 初始 FOOD+WATER=14，目标阈值=8，因此无需主动囤积即可完成。结论：`GOAL_TRIVIALITY_CONFIRMED`（BALANCE，可达但目标没有区分度）。

## Alliance Betrayal Diagnosis

背叛 1091 次；背叛前联盟平均持续 1.90 轮。结构化场景：{LOW_TRUST=302, RESOURCE_CONFLICT=163, PROMISE_CONFLICT=107, UNEXPLAINED=303, SURVIVAL_PRESSURE=132, ENDGAME=84}。分类只使用背叛者当时可知关系/传闻和独立 serverTruth 条件，不调用第二次 LLM。

## Rescue Funnel

| DOWNED episodes | 3910 |
| 至少一名现场观察者 | 3322 |
| 至少一名观察者有 MEDKIT | 736 |
| 合法救援决策机会（turn） | 758 |
| 选择 REVIVE | 282 |
| 最终 REVIVED | 271 |
| EXECUTED | 894 |
| BLEED_OUT | 2745 |

低救援率应优先按“无现场观察者 / 现场无医疗包 / 有机会但未选择”三段解释，而不是只看 DOWNED 分母。

## Persona Cross-Seed Analysis

| Persona | 样本席位 | 胜场 | 胜率 | 95% CI | attacks/player | alliances/player | betrayals/alliance | trades/player | revives/opportunity | fulfilled/accepted |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| BOLD | 1500 | 259 | 17.27% | 15.44%–19.26% | 3.81 | 0.65 | 36.10% | 0.59 | 40.41% | 24.47% |
| BALANCED | 1500 | 157 | 10.47% | 9.02%–12.12% | 2.52 | 0.60 | 26.62% | 0.56 | 45.62% | 23.39% |
| CAUTIOUS | 1000 | 84 | 8.40% | 6.84%–10.28% | 1.67 | 0.67 | 18.13% | 0.60 | 35.34% | 18.62% |

## Seed Stability

| Baseline | Seeds | Alliance formed | Betrayal | Promise accept | Promise fulfill | Rescue | Objective | Hidden Goal | Trade | Info Trade | BOLD/BALANCED/CAUTIOUS win |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| A | 100000~100999 | 85.89% | 56.43% | 75.61% | 23.07% | 6.90% | 0.00% | 37.06% | 65.62% | 28.44% | CAUTIOUS 8.70% / BALANCED 10.37% / BOLD 17.17% |
| B | 200000~200499 | 85.70% | 55.83% | 76.50% | 22.41% | 6.93% | 0.00% | 37.38% | 65.69% | 30.59% | CAUTIOUS 8.40% / BALANCED 10.47% / BOLD 17.27% |

## Prompt Salience Findings

Trace 记录了实际注入的 Accepted Promise、关键记忆数量、简短 reasonSummary 与选择。FAST 策略会优先检查承诺，因此 FAST 数据不能证明真实 LLM 的显著性问题；在未运行 LLM_SAMPLE 时，不标记 `PROMISE_SALIENCE_PROBLEM` 或 `SOCIAL_COMMITMENT_IGNORED`。

## Reachability vs Strategy

| 机制 | 问题 | CODE | REACHABILITY | STRATEGY | BALANCE | 置信度 |
|---|---|---:|---:|---:|---:|---|
| Promise | 高违约 |  | ✓ |  |  | 中高 |
| Cooperative Objective | 低完成 |  | ✓ | ✓ |  | 高（逐回合漏斗） |
| FIELD_MEDIC | 低完成 |  |  | ✓ |  | 高 |
| INFORMATION_BROKER | 低完成 |  | ✓ | ✓ |  | 高 |
| RESOURCE_HOARDER | 初始即满足 |  |  |  | ✓ | 高 |
| Rescue | 低救援 |  | ✓ | ✓ |  | 高 |
| Alliance betrayal | 高背叛 |  |  | ✓ | 可能 | 中（FAST policy） |

## 代表性 LLM 对局

未运行 LLM_SAMPLE，因此没有伪造代表性故事。FAST 对局不是 LLM 社会行为证据。

## 发现的代码 Bug

本轮诊断实现过程中未确认新的生产状态机 Bug；只扩展了内部 AI_DECISION payload 的诊断字段，且公共 payload 明确排除这些字段。

## 本轮代码修改

- 仿真专用 Decision Trace，严格分离 `agentKnownState` / `serverTruth`。
- Promise、多人目标、FIELD_MEDIC、INFORMATION_BROKER、RESOURCE_HOARDER、背叛、救援和 Persona 机会率漏斗。
- 多 Seed 区间聚合、Wilson 95% 置信区间、诊断测试和报告入口。
- 无新玩法、无调参、无 Prompt/3D 修改。

## 下一轮实验建议

1. 人工授权 10 局 LLM_SAMPLE，仅观察 Promise 显著性与社会承诺忽略率。
2. 对 Cooperative Objective 做单变量 Prompt 位置实验，保持门槛与奖励不变。
3. 对 RESOURCE_HOARDER 做单变量目标初始化差距实验，先验证“初始即满足”因果链，不直接上线调参。
