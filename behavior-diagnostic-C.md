# Agent Arena 行为诊断报告

## 实验配置

- FAST_SIMULATION：500 局；8 players；20 max rounds；每回合 invariant。
- Seed 区间：100000~100999、300000~300499。
- LLM_SAMPLE：未执行；未获得可计费外部模型调用的人工授权/明确费用配置。
- 本轮未修改 Prompt、玩法或平衡参数。

## LLM_SAMPLE 行为观察

没有获得人工授权的可计费模型配置，因此没有调用外部模型，也不伪造 LLM 结论。Trace 与运行入口已就绪。 

## Promise Funnel

| 阶段 | 数量 | 占 Accepted |
|---|---:|---:|
| Accepted | 1791 | 100.00% |
| 承诺者提前淘汰 | 287 | 16.02% |
| 期限内从未持有所需资源 | 564 | 31.49% |
| 持有资源但未与对方相遇 | 166 | 9.27% |
| 至少一次合法履约机会 | 774 | 43.22% |
| 选择 FULFILL_PROMISE | 1097 | 61.25% |
| 最终 FULFILLED | 418 | 23.34% |
| 有机会但未履约 | 356 | 19.88% |
| BROKEN | 1076 | 60.08% |

主因分类：MECHANIC_REACHABILITY。漏斗把客观不可履约与有机会未履约分开。

## Cooperative Objective Funnel

| 阶段 | 数量 |
|---|---:|
| 目标 | 500 |
| Context 中看到目标的独立 Agent | 4000 |
| 表达参与/移动/贡献意图的独立 Agent | 2048 |
| 向目标区移动 | 1543 |
| 到达目标区 | 2219 |
| 到达且有可贡献资源 | 1506 |
| 实际贡献者 | 854 |
| 完成 | 0 |

平均完成度：16.70%；首次贡献时平均剩余 4.57 轮；平均最大同时到场人数 3.63。

完成度分布：{51-75%=0, 0-25%=433, 100%=0, 76-99%=0, 26-50%=67}

失败原因：{DEADLINE_TOO_SHORT=25, PARTIAL_CONTRIBUTION=274, INSUFFICIENT_PARTICIPANTS=157, REACHED_BUT_DID_NOT_CONTRIBUTE=33, PARTICIPANTS_ELIMINATED=11}

## FIELD_MEDIC Funnel

| 阶段 | 数量 |
|---|---:|
| Goal Assigned | 1000 |
| 看到可感知 DOWNED | 695 |
| 同时见到 DOWNED 且有 MEDKIT | 214 |
| 合法决策机会（turn） | 144 |
| 选择对应动作 | 53 |
| 成功 | 53 |
| Goal Completed | 53 |

失败原因：{OPPORTUNITY_NOT_CHOSEN=60, NO_LEGAL_RESCUE_OPPORTUNITY=582, NO_VISIBLE_DOWNED=305}

## INFORMATION_BROKER Funnel

| 阶段 | 数量 |
|---|---:|
| Goal Assigned | 1000 |
| 曾拥有本人已知情报 | 815 |
| 曾遇到潜在买家 | 802 |
| 合法决策机会（turn） | 4918 |
| 选择对应动作 | 195 |
| 成功 | 41 |
| Goal Completed | 41 |

失败原因：{BUYER_CANNOT_PAY=51, REGION_CONDITION_FAILED=14, OTHER=14, PROPOSAL_REJECTED=52, NO_BUYER_ENCOUNTER=13, NO_INFORMATION=185, NO_PROPOSAL=630}

## RESOURCE_HOARDER Diagnosis

- Assigned：1000；Completed：1000。
- 平均完成轮：1.13；最早：1；1~3 轮完成：100.00%。
- 初始 FOOD+WATER=14，目标阈值=8，因此无需主动囤积即可完成。结论：`GOAL_TRIVIALITY_CONFIRMED`（BALANCE，可达但目标没有区分度）。

## Alliance Betrayal Diagnosis

背叛 1084 次；背叛前联盟平均持续 1.77 轮。结构化场景：{LOW_TRUST=323, UNEXPLAINED=287, RESOURCE_CONFLICT=158, PROMISE_CONFLICT=118, SURVIVAL_PRESSURE=119, ENDGAME=79}。分类只使用背叛者当时可知关系/传闻和独立 serverTruth 条件，不调用第二次 LLM。

## Rescue Funnel

| DOWNED episodes | 3922 |
| 至少一名现场观察者 | 3296 |
| 至少一名观察者有 MEDKIT | 730 |
| 合法救援决策机会（turn） | 743 |
| 选择 REVIVE | 271 |
| 最终 REVIVED | 267 |
| EXECUTED | 916 |
| BLEED_OUT | 2739 |

低救援率应优先按“无现场观察者 / 现场无医疗包 / 有机会但未选择”三段解释，而不是只看 DOWNED 分母。

## Persona Cross-Seed Analysis

| Persona | 样本席位 | 胜场 | 胜率 | 95% CI | attacks/player | alliances/player | betrayals/alliance | trades/player | revives/opportunity | fulfilled/accepted |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| BOLD | 1500 | 236 | 15.73% | 13.98%–17.66% | 3.76 | 0.62 | 36.57% | 0.59 | 40.17% | 24.39% |
| BALANCED | 1500 | 176 | 11.73% | 10.20%–13.46% | 2.56 | 0.58 | 27.32% | 0.55 | 43.04% | 21.85% |
| CAUTIOUS | 1000 | 88 | 8.80% | 7.20%–10.72% | 1.74 | 0.65 | 18.25% | 0.63 | 42.42% | 23.66% |

## Seed Stability

| Baseline | Seeds | Alliance formed | Betrayal | Promise accept | Promise fulfill | Rescue | Objective | Hidden Goal | Trade | Info Trade | BOLD/BALANCED/CAUTIOUS win |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| A | 100000~100999 | 85.89% | 56.43% | 75.61% | 23.07% | 6.90% | 0.00% | 37.06% | 65.62% | 28.44% | BALANCED 10.37% / BOLD 17.17% / CAUTIOUS 8.70% |
| B | 300000~300499 | 86.02% | 56.99% | 75.38% | 23.34% | 6.81% | 0.00% | 37.43% | 65.38% | 28.54% | BALANCED 11.73% / BOLD 15.73% / CAUTIOUS 8.80% |

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
