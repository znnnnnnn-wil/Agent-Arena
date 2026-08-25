# Agent Arena 大逃杀行为诊断报告

## 实验配置

- 生产代码基线：当前最新工作区代码；复用真实 Observation、Memory、Relationship、Communication、Alliance、Promise、Trade、Rumor、Hidden Goal、Cooperative Objective、Downed/Rescue 与 `BattleAiGameRunner` 执行链。
- FAST_SIMULATION 新实验：1500 局，8 players，20 max rounds，每回合 invariant。
- 新 Seed 区间：200000–200499、300000–300499、400000–400499，各 500 局。
- 稳定性参照：上一轮已验证的 100000–100999 共 1000 局。
- 原计划优先 3×1000；逐决策 Trace 的串行采集耗时过长，按任务允许的降级方案改为 3×500。最终 1500 个新样本全部成功，0 异常，0 invariant failure。
- LLM_SAMPLE：**已执行 10 局**，使用百炼 QWEN `qwen-turbo`；1009 次 `MODEL` 决策、0 次回退、0 异常局。
- 本轮没有修改 Prompt、伤害、资源、奖励、门槛、信任值、联盟参数或 3D。

分区原始报告：[B](behavior-diagnostic-B.md)、[C](behavior-diagnostic-C.md)、[D](behavior-diagnostic-D.md)。

## LLM_SAMPLE 行为观察

本轮已获得真实 LLM 样本，但只有 10 局，以下结论仅适用于百炼 `qwen-turbo` 在当前 Prompt、当前初始化和这组 Seed 下的行为，不外推到其他模型。

- 1009/1009 决策来自 `MODEL`，`MODEL_FALLBACK=0`。
- 战术动作：`MOVE=905`、`SKIP=104`。
- 社会动作：仅出现 69 次 `PROPOSE_ALLIANCE`。
- 69 次联盟提案均未形成接受/正式联盟；Promise、救援、Rumor、Information Trade、Cooperative Objective 完成事件均为 0。
- 通信：69 次 PRIVATE，未观察到多回合社会协议链。

结论：模型能输出结构化社会动作，但在这 10 局中没有推进到“提案—接受—履约/背叛”的持续社会互动，主要选择移动和跳过。

已完成可安全运行的 LLM_SAMPLE 入口与 Decision Trace；无 `ModelGateway + ModelRuntimeConfig` 时会硬失败，不会回退成伪 LLM 样本。Trace 只保存简短 `reasonSummary` 和 `confidence`，不保存 Chain of Thought。

## Decision Trace 与信息边界

每次决策记录 Seed、轮次、Actor、Persona、生命状态、区域、可见玩家、可见 DOWNED、自身资源、实际注入的记忆、关系、本人可见协议、Rumor Claim、隐藏目标、公共目标以及最终动作/社会动作/通信/摘要/置信度。

`agentKnownState` 与 `serverTruth` 是两个独立类型：前者只从过滤后的 `BattleAgentContext` 构建；后者只供服务器内部计算可达性。Rumor 在 Trace 中明确标记为 `CLAIM`，不会混成 Fact。诊断字段只进入内部 `AI_DECISION.payload`，不会进入公共 payload。

## Promise Funnel

三组共 5355 个 Accepted Promise。分类按承诺最终结局和全部逐回合机会做排他统计；同一决策中 `MOVE/SEARCH` 先执行、随后履约所创造的机会也计入。

| 阶段 | 数量 | 占 Accepted |
|---|---:|---:|
| Accepted | 5355 | 100.00% |
| 参与者淘汰后取消 | 809 | 15.11% |
| 期限内从未持有所需资源 | 1730 | 32.31% |
| 持有资源但从未形成合法相遇机会 | 504 | 9.41% |
| 至少一次合法履约机会 | 2312 | 43.17% |
| 最终 FULFILLED | 1224 | 22.86% |
| 有合法机会但未履约 | 1088 | 20.32% |
| 最终 BROKEN | 3301 | 61.64% |
| 曾选择 FULFILL_PROMISE（含条件失败尝试） | 3249 | 60.67% |

守恒关系：`809 + 1730 + 504 + 2312 = 5355`；`1224 + 1088 = 2312`。

根因判断：以 **MECHANIC_REACHABILITY 为主，STRATEGY 为重要次因**。56.83% 的 Accepted Promise 没有形成可完成的非取消机会；但在真正有机会的 2312 个承诺中，47.06% 仍未履约，说明策略选择也显著。FAST 策略本身会优先查看 Promise，因此该数据不能证明真实 LLM 的 Prompt 显著性问题。

## Cooperative Objective Funnel

| 阶段 | 数量 |
|---|---:|
| 目标 | 1500 |
| 在 Context 中看到目标的 Agent | 12000 |
| 表达参与/移动/贡献意图的 Agent | 6185 |
| 向目标区移动 | 4608 |
| 到达目标区 | 6666 |
| 到达且携带可贡献资源 | 4557 |
| 实际贡献者 | 2609 |
| 完成 | 0 |

- 平均完成度：16.71%。
- 首次贡献时平均剩余：4.50 轮。
- 平均最大同时到场人数：3.64。
- 完成度：0–25% 为 1287（85.80%）；26–50% 为 209（13.93%）；51–75% 为 4（0.27%）；76–99% 和 100% 均为 0。

失败原因：

| 原因 | 数量 | 占失败目标 |
|---|---:|---:|
| PARTIAL_CONTRIBUTION | 853 | 56.87% |
| INSUFFICIENT_PARTICIPANTS | 414 | 27.60% |
| REACHED_BUT_DID_NOT_CONTRIBUTE | 117 | 7.80% |
| DEADLINE_TOO_SHORT | 72 | 4.80% |
| PARTICIPANTS_ELIMINATED（仅计 deadline 前淘汰） | 44 | 2.93% |

根因判断：**MECHANIC_REACHABILITY + STRATEGY**。Agent 普遍知道目标，也有大量到达与资源机会；0% 不是“不知道目标”或单纯“到不了”，而是贡献深度不足和多人贡献无法聚合。85.80% 停在 0–25%，不是大量卡在 76–99%，所以不能把问题解释成阈值只略高。

## FIELD_MEDIC Funnel

| 阶段 | 数量 |
|---|---:|
| Goal Assigned | 3000 |
| 曾看到可感知 DOWNED | 2060 |
| 曾同时见到 DOWNED 且有 MEDKIT | 627 |
| 合法救援决策机会（turn） | 401 |
| 选择 REVIVE | 154 |
| 成功 REVIVE | 154 |
| Goal Completed | 151 |

未完成原因：NO_VISIBLE_DOWNED 940；NO_LEGAL_RESCUE_OPPORTUNITY 1750；OPPORTUNITY_NOT_CHOSEN 156；REVIVE_NOT_COMPLETED 3。

根因判断：**MECHANIC_REACHABILITY 为主，STRATEGY 为次**。约 89.6% 的未完成者从未得到合法救援机会；但在 401 个机会中只有 38.4% 选择 REVIVE。

## INFORMATION_BROKER Funnel

| 阶段 | 数量 |
|---|---:|
| Goal Assigned | 3000 |
| 曾拥有本人真实已知情报 | 2438 |
| 曾遇到潜在买家 | 2407 |
| 合法销售机会（turn） | 14719 |
| 提出 Information Trade | 593 |
| 成功成交 | 117 |
| Goal Completed | 117 |

未完成原因：NO_PROPOSAL 1872；NO_INFORMATION 562；PROPOSAL_REJECTED 175；BUYER_CANNOT_PAY 145；REGION_CONDITION_FAILED 50；NO_BUYER_ENCOUNTER 31；OTHER 48。

根因判断：**STRATEGY 为主，REACHABILITY 为次**。有情报和买家的条件经常出现，但提出交易的频率极低。

本轮确认并修复了一个真实 CODE_BUG：此前买家接受情报交易时会触发买家的 INFORMATION_BROKER 目标，卖家反而得不到完成判定。修复后只评估真实卖家；三组中“成功成交 = Goal Completed = 117”。

## RESOURCE_HOARDER Diagnosis

- Assigned：3000；Completed：3000（100%）。
- 平均完成轮：1.13；最早第 1 轮；1–3 轮完成率 100%。
- 初始 FOOD+WATER=14，目标阈值=8。

结论：`GOAL_TRIVIALITY_CONFIRMED`。玩家无需执行囤积行为，初始资源已经满足条件。这是明确的 **BALANCE** 问题，本轮未调阈值。

## Alliance Betrayal Diagnosis

- 背叛：3234 次；背叛前联盟平均持续约 1.84 轮。
- Seed 区间背叛率：55.83%、56.99%、54.87%，与基线 56.43% 一致。

| 场景 | 数量 | 占背叛 |
|---|---:|---:|
| LOW_TRUST | 944 | 29.19% |
| UNEXPLAINED | 865 | 26.75% |
| RESOURCE_CONFLICT | 477 | 14.75% |
| SURVIVAL_PRESSURE | 382 | 11.81% |
| PROMISE_CONFLICT | 328 | 10.14% |
| ENDGAME | 238 | 7.36% |
| RUMOR_INFLUENCE | 0 | 0.00% |

真实 LLM 的 `reasonSummary` 未采集，因此 26.75% 仍只能标为 UNEXPLAINED。FAST 中没有针对具体盟友的 Rumor Claim 命中背叛前上下文，不能据此推断真实 LLM 不受谣言影响。当前高背叛更像 FAST 攻击策略、低信任与资源/生存压力共同作用，而不是 Seed 偶然。

## Rescue Funnel

| 阶段/结局 | 数量 | 占 DOWNED |
|---|---:|---:|
| DOWNED episodes | 11718 | 100.00% |
| 至少一名现场观察者 | 9899 | 84.48% |
| 至少一名观察者有 MEDKIT | 2216 | 18.91% |
| 合法救援决策机会（turn） | 2272 | — |
| 选择 REVIVE | 820 | 占机会 36.09% |
| 最终 REVIVED | 801 | 6.84% |
| EXECUTED | 2728 | 23.28% |
| BLEED_OUT | 8189 | 69.89% |

守恒关系：`801 + 2728 + 8189 = 11718`。

根因判断：**MECHANIC_REACHABILITY 为主，STRATEGY 为次**。现场通常有人，但只有 18.91% 的倒地事件曾有带医疗包的观察者；即使形成合法决策机会，也只有 36.09% 选择救援。

## Persona Cross-Seed Analysis

| Persona | 样本席位 | 胜场 | 胜率 | 95% CI | attacks/player | alliance proposals/player | betrayals/alliance membership | trades/player | revives/opportunity | fulfilled/accepted |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| BOLD | 4500 | 758 | 16.84% | 15.78%–17.97% | 3.72–3.81 | 0.62–0.66 | 36.10%–37.28% | 0.55–0.59 | 40.17%–43.13% | 23.54%–24.47% |
| BALANCED | 4500 | 482 | 10.71% | 9.84%–11.65% | 2.52–2.61 | 0.58–0.60 | 25.13%–27.32% | 0.55–0.56 | 40.45%–45.62% | 21.85%–25.17% |
| CAUTIOUS | 3000 | 260 | 8.67% | 7.71%–9.73% | 1.67–1.74 | 0.62–0.67 | 15.26%–18.25% | 0.56–0.63 | 34.09%–42.42% | 18.62%–23.66% |

`betrayals/alliance membership` 使用人格实际参与过的 ACTIVE 联盟作为机会分母，不再错误地只用该人格作为 proposer 的联盟数。

BOLD 在 A/B/C/D 四个 Seed 区间的胜率分别为 17.17%、17.27%、15.73%、17.53%，持续高于 BALANCED 与 CAUTIOUS；可判断这不是单 Seed 偶然，但 FAST 策略本身显式赋予不同攻击倾向，不能直接外推真实 LLM。

## Seed Stability

| Baseline | Seeds | Alliance formed | Betrayal | Promise accept | Promise fulfill | Rescue | Objective | Hidden Goal | Trade | Info Trade | BOLD / BALANCED / CAUTIOUS win |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| A | 100000–100999 (1000) | 85.89% | 56.43% | 75.61% | 23.07% | 6.90% | 0.00% | 37.06% | 65.62% | 28.44% | 17.17% / 10.37% / 8.70% |
| B | 200000–200499 (500) | 85.70% | 55.83% | 76.50% | 22.41% | 6.93% | 0.00% | 37.38% | 65.69% | 30.59% | 17.27% / 10.47% / 8.40% |
| C | 300000–300499 (500) | 86.02% | 56.99% | 75.38% | 23.34% | 6.81% | 0.00% | 37.43% | 65.38% | 28.54% | 15.73% / 11.73% / 8.80% |
| D | 400000–400499 (500) | 85.32% | 54.87% | 74.21% | 22.82% | 6.77% | 0.00% | 36.58% | 63.38% | 31.54% | 17.53% / 9.93% / 8.80% |

结论：核心异常在独立 Seed 区间中稳定复现。Alliance、Promise、Rescue、Objective、Hidden Goal、Trade 与 Persona 排序均未表现为单 Seed 偶然；Information Trade 波动稍大（28.44%–31.54%），但仍处于同一量级。

## Prompt Salience Findings

当前 Prompt 排列已审计，Trace 记录了实际注入的 Accepted Promise、记忆数量和关键记忆摘要。本轮真实 LLM 样本中没有 Accepted Promise，因此没有机会判断 `PROMISE_SALIENCE_PROBLEM` 或 `SOCIAL_COMMITMENT_IGNORED`；这两个问题仍需后续构造已有承诺的 LLM 实验。

## Reachability vs Strategy

| 机制 | 问题 | CODE | REACHABILITY | STRATEGY | BALANCE | 置信度 |
|---|---|---:|---:|---:|---:|---|
| Promise | 高违约 |  | 主 | 次 |  | 高（排他漏斗） |
| Cooperative Objective | 0% 完成 |  | ✓ | ✓ | 待单变量实验 | 高 |
| FIELD_MEDIC | 低完成 |  | 主 | 次 |  | 高 |
| INFORMATION_BROKER | 低完成 | 已修复归属 Bug | 次 | 主 |  | 高 |
| RESOURCE_HOARDER | 初始即满足 |  |  |  | 主 | 高 |
| Alliance betrayal | 约 56% |  |  | 主（FAST） | 可能 | 中 |
| Rescue | 约 7% |  | 主 | 次 |  | 高 |
| BOLD 优势 | 跨 Seed 稳定 |  |  | FAST Persona 策略 | 可能 | 中高 |

## 代表性 LLM 对局

已运行 10 局真实 LLM_SAMPLE。代表性 Seed：500000、500002、500004、500007、500008；这些对局均有真实模型决策，但没有形成正式联盟、Promise、救援或传闻链，因此不伪造不存在的事件故事。完整聚合见 [llm-sample-report.md](llm-sample-report.md)。

## 发现的代码 Bug

1. **INFORMATION_BROKER 完成归属错误（已修复）**：买家接受交易时错误触发买家 Hidden Goal；现改为成功交易后评估真实卖家。

没有发现其他新的生产状态机 Bug。诊断过程中发现并修正的 Promise 后置机会、deadline 前淘汰、Persona 联盟分母、Rumor subject 均属于实验统计口径，不是玩法 Bug。

## 本轮代码修改

- 新增仿真专用 Decision Trace，严格分离 `agentKnownState` 与 `serverTruth`。
- 新增 Promise、Cooperative Objective、FIELD_MEDIC、INFORMATION_BROKER、RESOURCE_HOARDER、Alliance betrayal、Rescue 与 Persona 机会率漏斗。
- 新增 Wilson 95% CI、多 Seed 稳定性分析、受限并发 FAST 执行和分区报告。
- 修复 INFORMATION_BROKER 完成归属 Bug。
- 内部 AI_DECISION 增加诊断字段；公共 payload 不包含社会动作/通信诊断字段。
- 未增加玩法，未调参，未修改 Prompt 或 3D。

## 测试结果

- Maven：216 tests，0 failures，0 errors，0 skipped。
- 新增行为诊断专项测试：12 tests，通过。
- FAST_SIMULATION：本轮 1500 局；参照基线 1000 局；新实验 0 abnormal、0 invariant failure。
- LLM_SAMPLE：10 局；1009 个真实模型决策；0 回退；0 异常。

## 下一轮实验建议

仅建议三项，不在本轮实现：

1. 构造已有 Accepted Promise 的 LLM_SAMPLE 场景，单独验证 Promise 是否进入决策重点以及模型是否主动履约。
2. 对 Cooperative Objective 做单变量 Prompt 位置实验，保持门槛、deadline 与奖励完全不变。
3. 对 RESOURCE_HOARDER 做单变量初始化差距实验，验证“初始即满足”因果链后再讨论平衡调整。
