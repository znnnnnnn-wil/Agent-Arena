# Agent Arena 真实 LLM_SAMPLE 报告

## 配置

- provider: QWEN / 百炼 OpenAI-compatible
- model: qwen-turbo
- games: 10
- seeds: 500000–500009
- max rounds: 20
- API Key: 仅进程环境变量，未写入文件或日志

## 调用真实性

- MODEL decisions: 1009
- MODEL_FALLBACK: 0
- abnormal games: 0
- real-model ratio: 100.00%

## 决策分布

- tactical: {MOVE=905, SKIP=104}
- social: {PROPOSE_ALLIANCE=69}
- communication: {PRIVATE=69}

## 关键社会事件

- BATTLE_ALLIANCE_FORMED: 0
- BATTLE_ALLIANCE_BETRAYED: 0
- BATTLE_PROMISE_ACCEPTED: 0
- BATTLE_PROMISE_FULFILLED: 0
- BATTLE_PROMISE_BROKEN: 0
- BATTLE_PLAYER_REVIVED: 0
- BATTLE_RUMOR_SHARED: 0
- BATTLE_INFORMATION_TRADE_COMPLETED: 0
- BATTLE_OBJECTIVE_COMPLETED: 0

## Promise 与显著性

- Accepted: 0
- Fulfilled: 0
- Broken: 0
- legal opportunities: 0
- legal but not fulfilled: 0
- decisions whose Context contained Accepted Promise: 0
- reasonSummary mentioned promise/commitment: 0 (0.00%)
- legal opportunity ignored in decision: 0

## 其他漏斗

- Objective completed: 0/10; average completion 0.00%
- FIELD_MEDIC completed: 0/20; legal opportunities 0; decisions 0
- INFORMATION_BROKER completed: 0/20; opportunities 0; proposals 0
- Rescue: 0/78; choices/opportunities 0/0

## 代表性对局索引

- seed 500008: rounds=16, winner=1, socialEvents=17, modelDecisions=107
- seed 500007: rounds=14, winner=1, socialEvents=13, modelDecisions=102
- seed 500002: rounds=12, winner=2, socialEvents=9, modelDecisions=88
- seed 500004: rounds=13, winner=8, socialEvents=9, modelDecisions=96
- seed 500000: rounds=14, winner=1, socialEvents=7, modelDecisions=102

完整逐决策 reasonSummary、Agent 知情状态与 server truth 保存在本次运行的内存结果中；报告只输出聚合，避免泄露凭证或大对象。
