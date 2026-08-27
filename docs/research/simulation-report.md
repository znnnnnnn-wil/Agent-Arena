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
- games: 1000
- players: 8
- maxRounds: 20
- seed range: 100000 ~ 100999
- invariant checks: true

## 稳定性

| 指标 | 数值 |
|---|---:|
| 成功局 | 1000 |
| 失败/异常局 | 0 |
| 平均轮数 | 16.01 |
| 中位数轮数 | 16 |
| 最小 / 最大轮数 | 12 / 20 |
| 最大轮结束占比 | 2.10% |

### 平均存活人数曲线

| 轮次 | 平均存活/倒地参赛者 |
|---:|---:|
| 1 | 8.00 |
| 2 | 8.00 |
| 3 | 8.00 |
| 4 | 8.00 |
| 5 | 7.98 |
| 6 | 7.90 |
| 7 | 7.75 |
| 8 | 7.52 |
| 9 | 7.10 |
| 10 | 6.45 |
| 11 | 5.76 |
| 12 | 4.59 |
| 13 | 3.63 |
| 14 | 2.90 |
| 15 | 2.05 |
| 16 | 1.66 |
| 17 | 1.45 |
| 18 | 1.35 |
| 19 | 1.30 |
| 20 | 1.28 |

## 核心游戏数据

| 指标 | 数值 |
|---|---:|
| 攻击次数 | 22416 |
| 击倒次数 | 7771 |
| 淘汰次数 | 6719 |
| 处决次数 | 1769 |
| 暴击次数 | 2634 |

## Alliance

| 指标 | 数值 |
|---|---:|
| 提案数 | 4537 |
| 每局平均形成数 | 3.90 |
| 形成率（形成/提案） | 85.89% |
| 平均持续轮数 | 5.54 |
| 正常退出率 | 10.26% |
| 背叛率 | 56.43% |
| 终局仍存活比例 | 33.31% |

## Promise

| 指标 | 数值 |
|---|---:|
| 提议数 | 4747 |
| 接受率 | 75.61% |
| 履约率（履约/接受） | 23.07% |
| 违约率 | 61.94% |
| 未接受过期率 | 13.78% |
| 参与者淘汰取消数 | 1011 |

## Trade

| 指标 | 数值 |
|---|---:|
| 提议数 | 2670 |
| 完成数 | 1416 |
| 成功率 | 65.62% |
| 条件变化失败率 | 34.38% |

## Rescue

| 指标 | 数值 |
|---|---:|
| DOWNED | 7771 |
| REVIVE | 536 |
| 救援率 | 6.90% |
| EXECUTE | 1769 |
| BLEED OUT | 4950 |

## Communication

| 指标 | 数值 |
|---|---:|
| PUBLIC | 5888 |
| LOCAL | 15805 |
| PRIVATE | 5667 |
| 平均每局消息 | 27.36 |
| PUBLIC 占比 | 21.52% |
| LOCAL 占比 | 57.77% |
| PRIVATE 占比 | 20.71% |

## Rumor

| 指标 | 数值 |
|---|---:|
| 产生/传播记录 | 6194 |
| 平均传播代数 | 0.28 |
| 最大传播代数 | 4 |
| 平均 confidence | 50.87 |
| 根传闻无人继续传播比例 | 76.22% |

## Information Trading

| 指标 | 数值 |
|---|---:|
| 提议数 | 1730 |
| 成功数 | 337 |
| 成功率 | 28.44% |
| 平均价格 | 1.00 |

## Hidden Goals

| 指标 | 数值 |
|---|---:|
| 目标总数 | 8000 |
| 完成率 | 37.06% |
| 失败/未完成率 | 62.94% |
| FIELD_MEDIC 完成率 | 4.65% |
| ALLIANCE_KEEPER 完成率 | 39.65% |
| INFORMATION_BROKER 完成率 | 3.95% |
| RESOURCE_HOARDER 完成率 | 100.00% |

## Cooperative Objectives

| 指标 | 数值 |
|---|---:|
| 生成数 | 1000 |
| 完成率 | 0.00% |
| 失败率 | 100.00% |
| 平均参与人数 | 1.79 |

## Persona

| riskPreference | 玩家样本 | 胜率 | 每人攻击 | 每人结盟提案 | 每人背叛 | 每人交易提案 | 每人救援 |
|---|---:|---:|---:|---:|---:|---:|---:|
| BOLD | 3000 | 17.17% | 3.81 | 0.58 | 0.38 | 0.33 | 0.07 |
| BALANCED | 3000 | 10.37% | 2.54 | 0.54 | 0.23 | 0.31 | 0.08 |
| CAUTIOUS | 2000 | 8.70% | 1.68 | 0.59 | 0.18 | 0.37 | 0.05 |

## Context Token 审计（修改后有界注入）

| 轮次 | 平均字符 | 估算 token | Memory | Rumor | Message | Relationship |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 3716.00 | 1062.25 | 3.00 | 0.00 | 0.00 | 7.00 |
| 10 | 7244.78 | 2070.37 | 52.08 | 1.48 | 6.27 | 7.00 |
| 20 | 7512.84 | 2147.02 | 75.74 | 2.67 | 8.79 | 7.00 |

审计前协议、传闻与情报交易为无界全量注入；审计后上限为 Memory 30、Message 20、Rumor 20、Alliance/Promise/Trade/Information Trade 各 12，Relationship 仅当前对局其他玩家。

## Strategy Collapse Warnings

- **OBJECTIVE_COMPLETION_COLLAPSE**: 协作目标完成率低于 5%（observed=0.00）
- **PROMISE_BREAKAGE_HIGH**: 承诺违约率超过 60%（observed=0.62）

## 代表性对局

### seed=100553，winner=5，rounds=20

- Round 1: P4 → BATTLE_RUMOR_SHARED
- Round 1: P6 → BATTLE_ALLIANCE_FORMED
- Round 2: P2 → BATTLE_ALLIANCE_FORMED
- Round 3: P8 → BATTLE_ALLIANCE_FORMED
- Round 3: P1 → BATTLE_ALLIANCE_BETRAYED P6
- Round 3: P2 → BATTLE_ALLIANCE_BETRAYED P7
- Round 3: P2 → BATTLE_PROMISE_ACCEPTED
- Round 3: P6 → BATTLE_TRADE_COMPLETED P6
- Round 3: P7 → BATTLE_RUMOR_SHARED
- Round 4: P5 → BATTLE_RUMOR_SHARED
- Round 4: P6 → BATTLE_ALLIANCE_FORMED
- Round 5: P7 → BATTLE_PROMISE_BROKEN
- Round 5: P7 → BATTLE_PROMISE_ACCEPTED
- Round 6: P2 → BATTLE_PROMISE_FULFILLED

### seed=100962，winner=7，rounds=19

- Round 2: P8 → BATTLE_ALLIANCE_FORMED
- Round 2: P2 → BATTLE_RUMOR_SHARED
- Round 3: P7 → BATTLE_PROMISE_ACCEPTED
- Round 4: P8 → BATTLE_ALLIANCE_BETRAYED P3
- Round 4: P8 → BATTLE_RUMOR_SHARED
- Round 4: P7 → BATTLE_RUMOR_SHARED
- Round 5: P1 → BATTLE_RUMOR_SHARED
- Round 5: P4 → BATTLE_ALLIANCE_FORMED
- Round 6: P8 → BATTLE_ALLIANCE_FORMED
- Round 6: P2 → BATTLE_PROMISE_BROKEN
- Round 6: P3 → BATTLE_RUMOR_SHARED
- Round 6: P4 → BATTLE_RUMOR_SHARED
- Round 7: P8 → BATTLE_ALLIANCE_BETRAYED P3
- Round 7: P1 → BATTLE_ALLIANCE_FORMED

### seed=100244，winner=5，rounds=15

- Round 1: P6 → BATTLE_RUMOR_SHARED
- Round 1: P7 → BATTLE_ALLIANCE_FORMED
- Round 2: P6 → BATTLE_ALLIANCE_FORMED
- Round 2: P7 → BATTLE_ALLIANCE_BETRAYED P2
- Round 2: P7 → BATTLE_TRADE_COMPLETED P7
- Round 3: P2 → BATTLE_RUMOR_SHARED
- Round 3: P3 → BATTLE_RUMOR_SHARED
- Round 3: P4 → BATTLE_ALLIANCE_FORMED
- Round 3: P6 → BATTLE_RUMOR_SHARED
- Round 4: P8 → BATTLE_PROMISE_ACCEPTED
- Round 4: P2 → BATTLE_PROMISE_ACCEPTED
- Round 4: P3 → BATTLE_TRADE_COMPLETED P3
- Round 4: P6 → BATTLE_INFORMATION_TRADE_COMPLETED
- Round 5: P8 → BATTLE_PROMISE_ACCEPTED

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
