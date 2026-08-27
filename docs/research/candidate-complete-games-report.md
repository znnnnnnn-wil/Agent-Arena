# Candidate-based Tactical Decision Architecture 验收报告

## 架构

`GameState → ObservationSystem → BattleAgentContext → BattleDecisionCandidateGenerator → BattleDecisionCandidateSet → production Prompt → ModelGateway<BattleCandidateDecision> → BattleDecisionCandidateResolver → legality revalidation → BattleRoyaleGameEngine → EngineEvent`

候选是当前玩家当前回合的临时对象，不写入 GameState、Snapshot、Replay 或公开 spectator payload。

## 模型配置

- provider: QWEN
- model: qwen-max
- temperature: 0.4
- maxTokens: 900
- retries: 0
- API Key: 仅从进程环境变量读取，未写入代码、trace 或报告

## Controlled Regression

| Scenario | Run | Selected Candidate | Resolved | Executed | Resolution | Reason |
|---|---:|---|---|---|---|---|

每个场景重复 0 次；模型只能返回候选 ID，目标和区域均由候选绑定。

## Complete Games

| Seed | Rounds | Winner | Termination | Abnormal |
|---:|---:|---:|---|---:|
| 2200000 | 20 | 7 | MAX_ROUNDS | false |
| 2200001 | 14 | 2 | NO_ALIVE_PLAYER | false |
| 2200002 | 17 | 7 | LAST_CONTENDER | false |
| 2200003 | 15 | 1 | NO_ALIVE_PLAYER | false |
| 2200004 | 14 | 7 | NO_ALIVE_PLAYER | false |

### Tactical Action Distribution

| Value | Count |
|---|---:|
| ATTACK | 8 |
| CAPTURE | 127 |
| EXECUTE | 1 |
| HEAL | 1 |
| HIDE | 13 |
| MOVE | 121 |
| SEARCH | 255 |
| SKIP | 42 |

### Social Action Distribution

| Value | Count |
|---|---:|
| NONE | 568 |

### Communication Distribution

| Value | Count |
|---|---:|
| NONE | 568 |

## Stability

- decisions: 568
- invalid Candidate: 0
- stale Candidate: 0
- model fallback: 0
- abnormal games: 0
