# Candidate-based Tactical Decision Architecture 实施与验收报告

## 最终架构

```text
BattleRoyaleGameState
→ BattleObservationSystem
→ BattleAgentContext（有限观察、记忆、关系、可见社会状态）
→ BattleDecisionCandidateGenerator
→ BattleDecisionCandidateSet（当前玩家、当前回合、当前 sequence）
→ BattleAgentContextBuilder.build(context, candidateSet)
→ ModelGateway<BattleCandidateDecision>
→ tacticalCandidateId
→ BattleDecisionCandidateResolver
→ BattleRoyaleGameEngine.validate（服务器 Truth 最终复验）
→ BattleRoyaleGameEngine.execute
→ Betrayal / Memory / Relationship
→ Communication
→ Social Action
→ Deadline / Goal
→ EngineEvent / Snapshot
```

生产模型不再组合 `action + targetPlayerId + targetRegion`。程序生成已经绑定动作语义、目标和区域的具体合法候选；模型只能选择一个临时候选 ID；Resolver 不读取 `reasonSummary`，也不允许模型覆盖候选参数。

历史 Prompt 实验和旧 `BattleAiDecision` 仍保留用于既有报告复现，但生产 `runNextTurn` 和无 Prompt override 的生产诊断入口均使用新的 Candidate 协议。

## 新增核心类

| 文件 | 职责 |
|---|---|
| `decision/BattleDecisionCandidate.java` | 一个完整、不可改写的具体战术动作实例 |
| `decision/BattleDecisionCandidateSet.java` | 绑定 game/round/sequence/actor 的临时候选集合 |
| `decision/BattleDecisionCandidateGenerator.java` | 仅依据 Observation、已知社会状态和公开规则生成候选，不评分、不推荐 |
| `decision/BattleCandidateDecision.java` | 正式生产模型输出 DTO：`tacticalCandidateId` + 原有社会/通信字段 |
| `decision/BattleDecisionCandidateResolver.java` | 查找 ID、恢复 Engine Action、最终合法性复验 |
| `decision/BattleCandidateResolution*.java` | `SELECTED`、`INVALID_CANDIDATE_SELECTION`、`CANDIDATE_STALE` 结果 |
| `decision/BattleHeuristicCandidateSelector.java` | LOCAL fallback 的独立候选策略；真实模型路径不调用 |
| `model/gateway/StructuredModelDiagnostics.java` | 安全提供 raw structured response/repair 标记，不包含凭证 |
| `simulation/BattleCandidateAcceptanceMain.java` | 严格真实模型验收入口；节流且任何模型失败立即终止，不允许 fallback 冒充 |

## Candidate 格式与示例

每个 ID 只在当前 Agent 当前 Turn 有效，按稳定的 `ActionType → targetId → region` 顺序生成，不包含 utility、score、priority、recommended 或 bestAction。

```text
T01
action: MOVE_B
destination: B
effect: 从 A 移动到相邻区域 B
cost: 消耗本次战术行动

T03
action: SEARCH_CURRENT_REGION
effect: 搜索当前区域 A，可能发现资源（不保证产出）
cost: 消耗本次战术行动

T06
action: HEAL_SELF
target: SELF
effect: 治疗自己，当前生命 20/100，恢复最多 35 生命
cost: MEDKIT x1

T05
action: ATTACK_P2
target: P2
effect: 尝试伤害可见玩家 P2

T08
action: REVIVE_P2
target: P2
effect: 救起同区倒地玩家 P2，使其恢复为 ALIVE
cost: MEDKIT x1
```

`ATTACK/EXECUTE` 已知 ACTIVE 盟友时只增加客观的 betrayal 后果说明，不禁止攻击。旧 `TRADE` 战术 enum 仅作协议兼容且引擎不转移资源，因此未生成 Tactical Candidate；资源交易继续由现有 Social Action 原子执行。

## LLM 输出契约

正式生产 JSON：

```json
{
  "tacticalCandidateId": "T06",
  "socialAction": null,
  "communication": null,
  "reasonSummary": "当前状态需要恢复。",
  "confidence": 86
}
```

生产 Prompt 明确禁止返回 `action`、`targetPlayerId`、`targetRegion`，并要求从 `CURRENT TACTICAL OPTIONS` 恰好选择一个 ID。Social Action 和 Communication 的现有结构未重构。

## 信息边界

- hidden 且未暴露的玩家不会进入 `visiblePlayers`，因此不会生成对应 ATTACK/REVIVE/EXECUTE。
- 不同区或只存在于过时 Memory 的未知位置玩家不会生成目标候选。
- CandidateGenerator 不读取服务器玩家全集来发现目标；MOVE 使用公开 topology，空投使用公开 observation，自身资源使用 self observation。
- 私密 Alliance 只通过 `allianceSystem.visibleTo` 后的 `BattleSocialContext` 参与已知背叛说明；未知联盟不会出现在候选中。
- Hidden Goal 仍只把当前 Agent 自己可见的目标保留在策略上下文中，不通过 Candidate 泄漏其他玩家目标。
- 服务器 Truth 仅在 Resolver 的最终 `BattleRoyaleGameEngine.validate` 中复验；失效原因只写内部 trace，不反馈秘密给模型。
- Candidate 不写入 GameState、数据库或 Snapshot；Replay/SSE/公共 spectator 仍只记录真实执行动作。`selectedCandidateId` 和 `candidateResolution` 只进入内部 `AI_DECISION.payload`/debug trace，公共 payload 明确省略。

## Sanitizer 与执行忠实性

真实模型路径现为：

```text
LLM candidate selection
→ Candidate Resolver
→ legality revalidation
→ Engine
```

旧 `sanitizeAction` 仅保留给显式传入历史 Prompt override 的实验复现入口。生产 `runNextTurn` 不再调用它。因此：

- 合法 `SEARCH_CURRENT_REGION` 必须解析并执行为 SEARCH，不会被程序替换为 ATTACK。
- 合法 `MOVE_B` 必须移动到 B，不会被程序重新选区。
- `HEAL_SELF` 始终只恢复自己，不能用 reasonSummary 表达“治疗 P2”。
- 不存在 ID → `INVALID_CANDIDATE_SELECTION` → SKIP。
- 跨玩家、跨回合、跨 sequence 或执行前失效 → `CANDIDATE_STALE` → SKIP。
- invalid/stale 均不进行第二次模型调用，也不猜测模型意图。

## 自动测试

- Maven：285 tests，0 failures，0 errors，0 skipped。
- 新测试覆盖 Candidate 生成、HEAL_SELF、SEARCH、每个邻区 MOVE、每个可见目标 ATTACK、REVIVE、EXECUTE、HIDE、GUARD、REST、SKIP、DOWNED-only-SKIP、hidden/unknown 防泄漏、无 MEDKIT、确定性/唯一 ID。
- Resolver 测试覆盖 HEAL/ATTACK/MOVE 恢复、目标不可改写、非法 ID、stale、跨 Turn、跨 Player、reasonSummary 不参与解析。
- 生产回归测试固定合法 SEARCH 保持 SEARCH、MOVE 目标保持 B、候选内部字段不进入 public payload。

## FAST 100 局 Smoke

- mode: FAST_SIMULATION
- games: 100
- players: 8
- maxRounds: 20
- seed: 2,300,000–2,300,099
- invariant checks: true（每 Turn）
- abnormal games: 0
- invariant failures: 0

FAST 继续使用自身确定性策略并复用同一生产执行链，不代表模型自主性结果。

## qwen-max Controlled Regression

配置：QWEN / `qwen-max`，temperature 0.4，maxTokens 900，retries 0；4 场景 × 3 = 12 次真实请求。全部 `source=MODEL`，fallback=0，parser repair=0，invalid=0，stale=0。

| Scenario | Run | Selected Candidate | 绑定语义 | Resolved | Executed |
|---|---:|---|---|---|---|
| HEAL | 1 | T03 | SEARCH_CURRENT_REGION | SEARCH | SEARCH |
| HEAL | 2 | T02 | MOVE_C | MOVE(C) | MOVE(C) |
| HEAL | 3 | T02 | MOVE_C | MOVE(C) | MOVE(C) |
| SEARCH | 1 | T02 | MOVE_C | MOVE(C) | MOVE(C) |
| SEARCH | 2 | T02 | MOVE_C | MOVE(C) | MOVE(C) |
| SEARCH | 3 | T02 | MOVE_C | MOVE(C) | MOVE(C) |
| ATTACK | 1 | T03 | SEARCH_CURRENT_REGION | SEARCH | SEARCH |
| ATTACK | 2 | T02 | MOVE_C | MOVE(C) | MOVE(C) |
| ATTACK | 3 | T03 | SEARCH_CURRENT_REGION | SEARCH | SEARCH |
| REVIVE | 1 | T08 | REVIVE_P2 | REVIVE(P2) | REVIVE(P2) |
| REVIVE | 2 | T08 | REVIVE_P2 | REVIVE(P2) | REVIVE(P2) |
| REVIVE | 3 | T08 | REVIVE_P2 | REVIVE(P2) | REVIVE(P2) |

### HEAL 回归

- 三次候选表均明确存在 `HEAL_SELF target=SELF cost=MEDKIT x1`。
- 模型选择：SEARCH、MOVE_C、MOVE_C；没有选择 HEAL_SELF。
- 即使未选择 HEAL，模型已不可能通过 HEAL 指定或暗示治疗 P2；Resolver 不读取 reasonSummary 补 target。

### SEARCH 回归

- 三次均存在 `SEARCH_CURRENT_REGION`，模型选择 MOVE_C ×3。
- 这反映模型策略偏好，不是架构替换；另外 HEAL/ATTACK 场景中模型实际选择的 SEARCH 均 `Selected → Resolved → Executed` 保持 SEARCH。

### ATTACK 回归

- 三次均存在具体 `ATTACK_P2`，模型选择 SEARCH、MOVE_C、SEARCH。
- 未出现“想救 P2 → HEAL_SELF”或任何自由 target 语义错位；模型没有选择攻击属于策略结果，不是 Resolver/Sanitizer 改写。

### REVIVE 回归

- `REVIVE_P2` 3/3 被选择并 3/3 忠实执行，target 始终为 P2。

验收标准不是 12/12 选择人为认定的最优动作，而是所有选择来自具体 Candidate、语义不可改写、解析后忠实执行且无策略 override；该标准全部满足。

## 5 局真实 qwen-max 完整比赛

第一次无节流尝试在受控调用后触发供应商限流并产生生产 fallback，因此整批明确排除，未计入下表。冷却后使用严格验收 Gateway（请求间隔 3.3 秒、任一真实调用失败立即终止）重新运行；最终批次为 568 次真实 qwen-max 决策，fallback=0。

| Seed | Rounds | Winner | Termination | Abnormal |
|---:|---:|---:|---|---:|
| 2,200,000 | 20 | P7 | MAX_ROUNDS | 0 |
| 2,200,001 | 14 | P2 | NO_ALIVE_PLAYER | 0 |
| 2,200,002 | 17 | P7 | LAST_CONTENDER | 0 |
| 2,200,003 | 15 | P1 | NO_ALIVE_PLAYER | 0 |
| 2,200,004 | 14 | P7 | NO_ALIVE_PLAYER | 0 |

Tactical Action distribution：

| Action | Count |
|---|---:|
| SEARCH | 255 |
| CAPTURE | 127 |
| MOVE | 121 |
| SKIP | 42 |
| HIDE | 13 |
| ATTACK | 8 |
| HEAL_SELF（Engine HEAL） | 1 |
| EXECUTE | 1 |
| REVIVE | 0 |
| GUARD | 0 |
| REST | 0 |

- social actions: NONE 568
- communication: NONE 568
- invalid Candidate: 0
- stale Candidate: 0
- model fallback: 0
- abnormal games: 0
- invariant failures: 0

完整比赛显示 SEARCH/CAPTURE/MOVE 仍占主要选择，社会动作与通信为零。这是剩余模型策略行为，不再是动作语义、目标拼装或 sanitizer override 的架构问题。

## 生产 Prompt

`BattleAgentContextBuilder.build(context, candidateSet)` 已正式接入生产 `runNextTurn`，新增 `CURRENT TACTICAL OPTIONS`，保留 Persona、Memory、Relationship、Alliance、Promise、Trade、Hidden Goal、Cooperative Objective、Rumor、Information Trade 和 Communication 上下文。

这不是实验 Variant。生产协议现在只允许选择 `tacticalCandidateId`；历史实验构建器和报告未删除。

## Snapshot / Replay / SSE

- `BattleRoyaleGameState` 未增加 Candidate 字段，旧 Snapshot 的 Alliance、Promise、Trade、Rumor、Hidden Goal、Cooperative Objective 恢复结构不变。
- CandidateSet 是方法内临时值，每 Turn 重建。
- Engine 和 Replay 继续记录最终真实 `BattleRoyaleAction`。
- 公共 spectator payload 不含 Candidate 列表、selected ID、解析结果或内部失效原因。

## 剩余问题

1. qwen-max 在受控 HEAL/SEARCH/ATTACK 场景中仍经常选择 MOVE/SEARCH，完整比赛也偏向 SEARCH/CAPTURE/MOVE；这是非阻塞策略表现，不是 Candidate 架构 Bug。
2. 5 局完整比赛没有产生 Social Action 或 Communication；本轮按范围未重构社会决策，现有社会结构仍兼容但模型未使用。
3. 百炼 qwen-max 连续无节流调用会触发账号限流；严格验收入口已加入 3.3 秒节流和 fail-fast，生产侧仍按现有 fallback 可用性策略运行。

未发现阻塞性的 Context、Candidate Schema、Resolver、Legality Revalidation、Execution、Snapshot、Replay 或 Public Payload Bug。

## 是否可以结束 AI Decision 调试阶段

**YES**

Candidate 架构已经把“定义真实合法具体选项”交给程序，把“选择哪个选项”留给 LLM，并保证选择被忠实执行。既定 Maven、FAST 100 局、12 次 qwen-max 受控回归和 5 局严格真实比赛均已完成。AI Decision Debugging Phase 可以结束；不再继续 Prompt 实验，后续回到正常游戏开发。
