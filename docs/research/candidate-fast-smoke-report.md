# Agent Arena 大逃杀社会系统仿真报告

## 代码审计结论

### Critical

- 未发现。

### High

- `BattleSocialActionSystem.execute`：无关 PUBLIC/LOCAL 消息听众会被合并进私密 Alliance/Promise/Trade 的 knownBy；造成 AI 认知越权。**已修复**。
- `BattleRumorSystem.share` + `BattleMemorySystem.rememberFact`：Rumor confidence 被写成事实 certainty=100，且缺少 sourceRumorId/speaker 与去重上限；造成 Claim/Fact 污染和长局膨胀。**已修复**。
- `BattleSocialActionSystem.execute`：DOWNED 玩家仍可执行社会动作，与规则和 Prompt 冲突。**已修复**。

### Medium

- `BattlePromiseSystem.evaluateDeadlines`：参与者淘汰后仍可能被判违约。**已修复为 CANCELLED，且幂等**。
- `BattleBetrayalDetector.detect`：直接调用时零伤害 ATTACK 也可触发背叛。**已修复**。
- `BattleHiddenGoalSystem.ensureAssigned`：部分旧快照只要已有一个目标就不再补齐其他玩家。**已修复**。
- `BattleAgentContextBuilder`：协议、传闻与情报交易历史无注入上限。**已修复为有界 tail**。
- `BattleSocialStateInitializer.ensure`：显式 null 的 knownBy/集合字段恢复后可能 NPE。**已修复并按最小知情原则补参与者**。

### Low

- `BattleMemorySystem.rememberAction`：DOWNED 玩家不会记录全局淘汰与空投公开事件。**已修复为 contendingPlayers**。

## 信息边界审计

| 子系统 | 越权读取结论 |
|---|---|
| Observation | 仅同区、未隐藏或已暴露目标；不返回精确敌方 HP/弹药/护甲 |
| Memory | 仅亲历、可见、公开或实际听众事件；Rumor 使用非 100 certainty |
| Alliance / Promise / Trade | AI Context 仅按 knownBy；无关通信不再扩散协议 |
| Rumor | 只改变 Rumor/Claim Memory，不修改世界事实或事实记忆 |
| Hidden Goal | 仅 owner Context；公开完成事件不含类型 |
| Information Trade | 仅买卖双方可见条款；publicPayload 不含 rumorId/price |
| Replay / Public Spectator | 使用 publicPayload；内部 payload 仅房主认证回放 |

## 规则执行顺序

`Decision → Tactical validation/execution → Tactical Memory → Betrayal/Relationship → Communication delivery/Memory → Social Action → Hidden Goal evaluation → Deadline/expiry evaluation → Snapshot`

该顺序现已由跨系统测试固定，不再依赖偶然调用结果。

## Invariant

- 资源与 HP 非负；alive 与 lifeStatus 一致。
- ELIMINATED 不进入 turnPlayers；DOWNED 仅 SKIP，且必须有 bleedOutRound。
- ACTIVE Alliance 成员存在且同一对玩家至多一个。
- 已解析 Promise 必须有 resolvedRound；Trade 条目合法且库存不为负。
- PRIVATE Message 双方存在；socialStates 覆盖全部玩家。
- Rumor 不超过 200；Hidden Goal owner 必须存在。

## Simulation Framework

- `BattleSimulationRunner`：复用生产执行链的确定性无头策略。
- `BattleSimulationConfig/Result/Metrics`：配置、单局记录和聚合。
- `BattleStateInvariantChecker`：每 Turn 立即检查并报告 seed/round。
- `BattleSimulationMain`：CLI 入口；同配置与 Seed 范围可复现。

LLM_SAMPLE 仅能通过显式注入 `ModelGateway + ModelRuntimeConfig` 启用；默认 CLI 永远使用 FAST_SIMULATION，避免意外 API 费用。

## 配置

- mode: FAST_SIMULATION
- games: 100
- players: 8
- maxRounds: 20
- seed range: 2300000 ~ 2300099
- invariant checks: true

## 稳定性

| 指标 | 数值 |
|---|---:|
| 成功局 | 100 |
| 失败/异常局 | 0 |
| 平均轮数 | 15.97 |
| 中位数轮数 | 16 |
| 最小 / 最大轮数 | 10 / 20 |
| 最大轮结束占比 | 3.00% |

### 平均存活人数曲线

| 轮次 | 平均存活/倒地参赛者 |
|---:|---:|
| 1 | 8.00 |
| 2 | 8.00 |
| 3 | 8.00 |
| 4 | 7.99 |
| 5 | 7.98 |
| 6 | 7.88 |
| 7 | 7.69 |
| 8 | 7.37 |
| 9 | 7.03 |
| 10 | 6.34 |
| 11 | 5.69 |
| 12 | 4.61 |
| 13 | 3.57 |
| 14 | 2.88 |
| 15 | 2.19 |
| 16 | 1.73 |
| 17 | 1.49 |
| 18 | 1.36 |
| 19 | 1.32 |
| 20 | 1.30 |

## 核心游戏数据

| 指标 | 数值 |
|---|---:|
| 攻击次数 | 2247 |
| 击倒次数 | 776 |
| 淘汰次数 | 670 |
| 处决次数 | 196 |
| 暴击次数 | 269 |

## Alliance

| 指标 | 数值 |
|---|---:|
| 提案数 | 446 |
| 每局平均形成数 | 3.73 |
| 形成率（形成/提案） | 83.63% |
| 平均持续轮数 | 5.21 |
| 正常退出率 | 12.87% |
| 背叛率 | 55.23% |
| 终局仍存活比例 | 31.90% |

## Promise

| 指标 | 数值 |
|---|---:|
| 提议数 | 479 |
| 接受率 | 74.32% |
| 履约率（履约/接受） | 23.31% |
| 违约率 | 57.58% |
| 未接受过期率 | 15.03% |
| 参与者淘汰取消数 | 117 |

## Trade

| 指标 | 数值 |
|---|---:|
| 提议数 | 273 |
| 完成数 | 138 |
| 成功率 | 62.73% |
| 条件变化失败率 | 37.27% |

## Rescue

| 指标 | 数值 |
|---|---:|
| DOWNED | 776 |
| REVIVE | 57 |
| 救援率 | 7.35% |
| EXECUTE | 196 |
| BLEED OUT | 474 |

## Communication

| 指标 | 数值 |
|---|---:|
| PUBLIC | 575 |
| LOCAL | 1537 |
| PRIVATE | 559 |
| 平均每局消息 | 26.71 |
| PUBLIC 占比 | 21.53% |
| LOCAL 占比 | 57.54% |
| PRIVATE 占比 | 20.93% |

## Rumor

| 指标 | 数值 |
|---|---:|
| 产生/传播记录 | 643 |
| 平均传播代数 | 0.25 |
| 最大传播代数 | 3 |
| 平均 confidence | 51.24 |
| 根传闻无人继续传播比例 | 76.86% |

## Information Trading

| 指标 | 数值 |
|---|---:|
| 提议数 | 163 |
| 成功数 | 33 |
| 成功率 | 30.00% |
| 平均价格 | 1.00 |

## Hidden Goals

| 指标 | 数值 |
|---|---:|
| 目标总数 | 800 |
| 完成率 | 37.00% |
| 失败/未完成率 | 63.00% |
| FIELD_MEDIC 完成率 | 3.50% |
| ALLIANCE_KEEPER 完成率 | 39.00% |
| INFORMATION_BROKER 完成率 | 5.50% |
| RESOURCE_HOARDER 完成率 | 100.00% |

## Cooperative Objectives

| 指标 | 数值 |
|---|---:|
| 生成数 | 100 |
| 完成率 | 0.00% |
| 失败率 | 100.00% |
| 平均参与人数 | 1.77 |

## Persona

| riskPreference | 玩家样本 | 胜率 | 每人攻击 | 每人结盟提案 | 每人背叛 | 每人交易提案 | 每人救援 |
|---|---:|---:|---:|---:|---:|---:|---:|
| BOLD | 300 | 19.67% | 3.81 | 0.53 | 0.31 | 0.35 | 0.07 |
| BALANCED | 300 | 7.33% | 2.52 | 0.56 | 0.25 | 0.35 | 0.08 |
| CAUTIOUS | 200 | 9.50% | 1.74 | 0.60 | 0.19 | 0.32 | 0.07 |

## Context Token 审计（修改后有界注入）

| 轮次 | 平均字符 | 估算 token | Memory | Rumor | Message | Relationship |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 4112.00 | 1175.25 | 3.00 | 0.00 | 0.00 | 7.00 |
| 10 | 7620.97 | 2177.81 | 49.99 | 1.54 | 5.75 | 7.00 |
| 20 | 7716.75 | 2205.25 | 80.00 | 2.25 | 8.50 | 7.00 |

审计前协议、传闻与情报交易为无界全量注入；审计后上限为 Memory 30、Message 20、Rumor 20、Alliance/Promise/Trade/Information Trade 各 12，Relationship 仅当前对局其他玩家。

## Strategy Collapse Warnings

- **OBJECTIVE_COMPLETION_COLLAPSE**: 协作目标完成率低于 5%（observed=0.00）

## 代表性对局

### seed=2300009，winner=1，rounds=17

- Round 2: P2 → BATTLE_ALLIANCE_FORMED
- Round 2: P3 → BATTLE_ALLIANCE_FORMED
- Round 2: P5 → BATTLE_RUMOR_SHARED
- Round 3: P8 → BATTLE_ALLIANCE_BETRAYED P3
- Round 3: P6 → BATTLE_TRADE_COMPLETED P6
- Round 3: P7 → BATTLE_PROMISE_ACCEPTED
- Round 4: P4 → BATTLE_ALLIANCE_FORMED
- Round 4: P6 → BATTLE_PROMISE_ACCEPTED
- Round 4: P7 → BATTLE_TRADE_COMPLETED P7
- Round 5: P4 → BATTLE_RUMOR_SHARED
- Round 6: P1 → BATTLE_PROMISE_BROKEN
- Round 6: P2 → BATTLE_PROMISE_BROKEN
- Round 6: P1 → BATTLE_RUMOR_SHARED
- Round 6: P2 → BATTLE_ALLIANCE_BETRAYED P7

### seed=2300050，winner=6，rounds=20

- Round 2: P1 → BATTLE_RUMOR_SHARED
- Round 2: P3 → BATTLE_TRADE_COMPLETED P3
- Round 2: P7 → BATTLE_RUMOR_SHARED
- Round 3: P2 → BATTLE_TRADE_COMPLETED P2
- Round 3: P6 → BATTLE_ALLIANCE_FORMED
- Round 4: P6 → BATTLE_PROMISE_ACCEPTED
- Round 5: P8 → BATTLE_TRADE_COMPLETED P8
- Round 5: P1 → BATTLE_TRADE_COMPLETED P1
- Round 5: P2 → BATTLE_PROMISE_FULFILLED
- Round 6: P1 → BATTLE_RUMOR_SHARED
- Round 6: P4 → BATTLE_TRADE_COMPLETED P4
- Round 6: P6 → BATTLE_ALLIANCE_FORMED
- Round 7: P8 → BATTLE_ALLIANCE_FORMED
- Round 7: P4 → BATTLE_PROMISE_ACCEPTED

### seed=2300035，winner=8，rounds=15

- Round 1: P1 → BATTLE_RUMOR_SHARED
- Round 1: P5 → BATTLE_RUMOR_SHARED
- Round 1: P6 → BATTLE_RUMOR_SHARED
- Round 1: P7 → BATTLE_ALLIANCE_FORMED
- Round 2: P8 → BATTLE_RUMOR_SHARED
- Round 2: P7 → BATTLE_ALLIANCE_BETRAYED P2
- Round 3: P3 → BATTLE_ALLIANCE_FORMED
- Round 3: P5 → BATTLE_RUMOR_SHARED
- Round 4: P1 → BATTLE_ALLIANCE_FORMED
- Round 4: P2 → BATTLE_ALLIANCE_FORMED
- Round 4: P5 → BATTLE_PROMISE_ACCEPTED
- Round 4: P7 → BATTLE_ALLIANCE_BETRAYED P2
- Round 5: P5 → BATTLE_RUMOR_SHARED
- Round 5: P7 → BATTLE_TRADE_COMPLETED P7

## 异常局

- 无。

## 可疑规则与解释

- 同一 Decision 的语义固定为：战术行动先执行，随后判定背叛，再通信，最后社会动作。因此 `LEAVE_ALLIANCE + ATTACK` 仍属于先攻击盟友并背叛；`ATTACK + ACCEPT_ALLIANCE` 不构成背叛；`MOVE + ACCEPT_TRADE` 使用移动后的区域校验；`ATTACK + PROPOSE_PROMISE` 在双方仍可用时仍会发出承诺。
- Promise 只由显式 FULFILL_PROMISE 履约；普通 Trade 即使转移同类资源也不自动改变 Promise，避免无法证明转移意图。
- DOWNED 只允许通信，不允许联盟、承诺、交易、传闻或协作贡献；deadline 不暂停。
- FAST_SIMULATION 是确定性结构化策略，不代表真实 LLM 的语言质量；它用于状态机、可达性和策略塌缩基线。

## 推荐下一步

1. 对警告中使用率最低的既有机制做 Prompt/策略可达性实验，不新增玩法。
2. 用 10~20 局显式授权的 LLM_SAMPLE 对比 FAST_SIMULATION 行为分布与事件链。
3. 将本报告阈值纳入 CI 的非阻断趋势检查，先积累多次基线再决定调参。

## 不要继续加什么

- 暂不增加 Reputation、Evidence、Lie Detection、新目标类型、新社会 Action 或新资源。
- 暂不扩展 3D；当前限制需要先从既有机制使用率、Prompt 可达性和数值数据中定位。
- 在 LLM_SAMPLE 与多次基线尚未完成前，不依据单次结果直接调整伤害、医疗包、trust 或奖励。
