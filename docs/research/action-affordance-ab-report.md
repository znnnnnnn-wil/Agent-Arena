# Action Affordance A/B 实验报告

## Hypothesis

检验 qwen-turbo 的剩余 MOVE 塌缩是否来自 GameState → executable action 映射不可靠。

## Production Rule and Sanitizer Audit

- Affordance 候选仅来自 Agent 可观察状态；最终用 `BattleRoyaleGameEngine.validate` 判定。
- SEARCH 在上一轮 ATTACK fixture 中是合法动作。`SEARCH → ATTACK` 来自 sanitizer 的攻击收益策略替换，而非非法动作修复。
- 因 sanitizer 混合合法化与策略偏好，本实验不使用 sanitizer 生成候选，但保持其生产行为不变。
- HEAL 的真实生产校验条件是 MEDKIT > 0；引擎当前不禁止满血 HEAL。SEARCH 没有硬性资源枯竭禁用条件。

## Variants

- A0_CURRENT：生产 Prompt 逐字基线，无 computedAffordances。
- A1_LEGAL_ACTION_NAMES：仅插入动态合法动作名称。
- A2_ACTION_PRECONDITIONS：插入动作名称及当前满足的执行条件。
- A3_DYNAMIC_AFFORDANCE：插入动作名称及状态绑定字段。

## Prompt Diff Validation

A1/A2/A3 均只在生产输出约束前插入 `ACTION_AFFORDANCE` Section；Decision Objective、Persona、Memory、Social Context、Rules、Region、生产 Action order、JSON Example 和输出 schema 保持 byte-identical。A0 与生产 Prompt 逐字一致。

## Experiment Config

- provider/model: QWEN / qwen-turbo
- temperature: 0.4
- maxTokens: 900
- retries: 0
- scenarios: ATTACK, SEARCH, HEAL, REVIVE（复用既有 fixture）
- repeats: 5
- requested: 80
- completed so far: 80

## Overall Result

| Variant | Relevant/Selection | MOVE | Non-MOVE | Diversity | Context Recognition | Affordance Recognition | Parsed=Executed | Sanitize Changed | Actions |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| A0_CURRENT | 0.00% | 100.00% | 0.00% | 1 | 20.00% | 0.00% | 100.00% | 0.00% | [MOVE] |
| A1_LEGAL_ACTION_NAMES | 0.00% | 100.00% | 0.00% | 1 | 30.00% | 0.00% | 100.00% | 0.00% | [MOVE] |
| A2_ACTION_PRECONDITIONS | 0.00% | 100.00% | 0.00% | 1 | 30.00% | 0.00% | 100.00% | 0.00% | [MOVE] |
| A3_DYNAMIC_AFFORDANCE | 10.00% | 85.00% | 15.00% | 3 | 65.00% | 10.00% | 100.00% | 0.00% | [MOVE, REVIVE, SEARCH] |

## ATTACK

| Variant | Context seen | Affordance understood | Target selected | MOVE | Non-MOVE | Tactical response | Distribution |
|---|---:|---:|---:|---:|---:|---:|---|
| A0_CURRENT | 2/5 | 0/5 | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| A1_LEGAL_ACTION_NAMES | 3/5 | 0/5 | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| A2_ACTION_PRECONDITIONS | 4/5 | 0/5 | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| A3_DYNAMIC_AFFORDANCE | 4/5 | 0/5 | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |

reasonSummary：

- A0_CURRENT: [向B区移动以获取食物，符合长期生存策略。, 向B区移动以获取食物，符合资源囤积目标，同时避开P2的潜在威胁。, 向B区移动以获取食物，符合公开协作目标需求。, 向B区移动以获取食物，符合当前生存需求。, 向B区移动以获取食物，补充资源并提高生存能力。]
- A1_LEGAL_ACTION_NAMES: [向B区移动以获取食物，补充资源以支持长期生存和潜在合作。, 向B区移动以获取食物资源，符合RESOURCE_HOARDER目标。, 向B区移动以获取食物，符合公开协作目标需求。, 向B区移动以获取食物，符合公开协作目标需求, 向B区移动以获取食物，符合长期生存策略。]
- A2_ACTION_PRECONDITIONS: [向B区移动以获取食物，补充资源并提高生存能力。, 向B区移动以获取食物资源，符合RESOURCE_HOARDER目标。, 向B区移动以获取食物，支持公开协作目标。, 向B区移动以获取食物，符合公开协作目标需求。, 向B区移动以获取食物，补充资源以支持长期生存和协作目标。]
- A3_DYNAMIC_AFFORDANCE: [向B区移动以获取食物资源，同时避免与P2直接冲突。, 向B区移动以获取食物资源，同时远离P2的潜在威胁。, 移动到B区获取食物资源，符合长期生存策略。, 向B区移动以获取食物资源，同时避免与P2直接冲突。, 向B区移动以获取食物资源，同时避开P2的潜在威胁。]

## SEARCH

| Variant | Context seen | Affordance understood | Target selected | MOVE | Non-MOVE | Distribution |
|---|---:|---:|---:|---:|---:|---|
| A0_CURRENT | 2/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| A1_LEGAL_ACTION_NAMES | 3/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| A2_ACTION_PRECONDITIONS | 2/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| A3_DYNAMIC_AFFORDANCE | 5/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |

reasonSummary：

- A0_CURRENT: [前往B区域获取食物，为合作目标做准备。, 前往B区域获取食物，符合资源收集目标。, 前往B区域获取食物，支持公开协作目标并提升生存能力。, 向B区域移动以获取食物，符合长期生存策略。, 前往B区域获取食物，符合当前资源需求。]
- A1_LEGAL_ACTION_NAMES: [前往B区域获取食物，补充生存资源。, 前往B区域获取食物，支持资源囤积目标。, 前往B区域获取食物，以完成协作目标, 前往B区域获取食物，以完成协作目标。, 向B区域移动以获取食物资源，符合当前生存需求。]
- A2_ACTION_PRECONDITIONS: [移动到B区域获取食物，为协作目标做准备, 向B区域移动以获取食物资源，符合私人目标RESOURCE_HOARDER的长期生存策略。, 前往B区域获取食物，以完成协作目标。, 向B区域移动以获取食物资源，符合当前生存需求。, 前往B区域获取食物，支持协作目标。]
- A3_DYNAMIC_AFFORDANCE: [向B区域移动以获取食物资源，符合当前生存需求。, 移动到B区域获取食物资源，符合私人目标RESOURCE_HOARDER的长期生存策略。, 移动到B区域，获取食物资源以完成协作目标。, 移动到B区域获取食物资源，符合当前生存需求。, 向B移动以获取食物资源，符合长期生存策略。]

## HEAL

| Variant | Context seen | Affordance understood | Target selected | MOVE | Non-MOVE | Distribution |
|---|---:|---:|---:|---:|---:|---|
| A0_CURRENT | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| A1_LEGAL_ACTION_NAMES | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| A2_ACTION_PRECONDITIONS | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| A3_DYNAMIC_AFFORDANCE | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |

reasonSummary：

- A0_CURRENT: [向B区域移动以获取食物资源，符合长期生存策略。, 前往B区域获取食物，符合RESOURCE_HOARDER目标，同时为后续行动储备资源。, 前往B区域获取食物，满足协作目标需求。, 前往B区域获取食物，支持公开协作目标并提升生存能力。, 前往B区域获取食物，支持协作目标并增强生存能力。]
- A1_LEGAL_ACTION_NAMES: [前往B区域获取食物，支持协作目标并增强生存能力。, 前往B区域获取食物，支持资源囤积目标。, 向B区域移动以获取食物资源，符合当前目标需求。, 前往B区域获取食物，满足协作目标需求并提升生存能力。, 向B区域移动以获取食物，符合当前资源需求。]
- A2_ACTION_PRECONDITIONS: [向B区域移动以获取食物，支持公开协作目标。, 向B区域移动以获取食物资源，符合我的私人目标RESOURCE_HOARDER。, 前往B区域获取食物，支持协作目标并提升生存能力。, 前往B区域获取食物，以完成协作目标并保持生存资源。, 向B区域移动以获取食物资源，符合长期生存策略。]
- A3_DYNAMIC_AFFORDANCE: [向B区域移动以获取食物资源，同时观察其他玩家动向。, 向B区域移动以获取食物资源，符合RESOURCE_HOARDER目标。, 向B区域移动以获取食物资源，同时保持安全距离。, 移动到B区域以获取食物资源，符合当前生存需求。, 向B区域移动以获取食物资源，符合当前生存需求。]

## REVIVE

| Variant | Context seen | Affordance understood | Target selected | MOVE | Non-MOVE | Distribution |
|---|---:|---:|---:|---:|---:|---|
| A0_CURRENT | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| A1_LEGAL_ACTION_NAMES | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| A2_ACTION_PRECONDITIONS | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| A3_DYNAMIC_AFFORDANCE | 4/5 | 2/5 | 2/5 | 2/5 | 3/5 | {MOVE=2, REVIVE=2, SEARCH=1} |

reasonSummary：

- A0_CURRENT: [移动到B区获取食物，同时尝试与P5建立临时联盟以提高生存几率。, 向B区移动以获取食物，符合资源囤积目标，同时避开当前危险区域A。, 向B区移动以获取食物，符合长期生存策略，同时远离当前危险区域A。, 向B区移动以获取食物，符合公开协作目标需求并补充资源。, 向B区移动以获取食物，补充生存资源并支持协作目标。]
- A1_LEGAL_ACTION_NAMES: [向B区移动以获取食物，补充资源并提高生存几率。, 向B区移动以获取食物资源，符合我的资源囤积目标。, 向B区移动以获取食物，符合长期生存策略。, 向B区移动以获取食物，支持长期生存目标。, 向B区移动以获取食物，补充资源并提高生存能力。]
- A2_ACTION_PRECONDITIONS: [向B区移动以获取食物，提升生存能力。, 向B区移动以获取食物资源，符合私人目标和协作目标需求。, 向B区移动以获取食物，符合长期生存策略。, 前往B区获取食物，提升生存能力。, 向B区移动以获取食物，符合长期生存策略。]
- A3_DYNAMIC_AFFORDANCE: [向B区移动以获取食物资源，同时避免与P2直接接触。, 搜索当前区域以获取资源，支持私人目标和协作目标。, P2 is downed in the same region and has high trust. Reviving him could help with the field medic goal and provide mutual benefit., P2 is downed but has high trust and gratitude. Reviving them may build long-term cooperation., 向B区移动以获取食物资源，同时避免与P2直接接触。]

## Recognition Ladder

| Variant | Scenario | STATE SEEN | AFFORDANCE UNDERSTOOD | ACTION SELECTED |
|---|---|---:|---:|---:|
| A0_CURRENT | ATTACK | 2/5 | 0/5 | 0/5 |
| A0_CURRENT | SEARCH | 2/5 | 0/5 | 0/5 |
| A0_CURRENT | HEAL | 0/5 | 0/5 | 0/5 |
| A0_CURRENT | REVIVE | 0/5 | 0/5 | 0/5 |
| A1_LEGAL_ACTION_NAMES | ATTACK | 3/5 | 0/5 | 0/5 |
| A1_LEGAL_ACTION_NAMES | SEARCH | 3/5 | 0/5 | 0/5 |
| A1_LEGAL_ACTION_NAMES | HEAL | 0/5 | 0/5 | 0/5 |
| A1_LEGAL_ACTION_NAMES | REVIVE | 0/5 | 0/5 | 0/5 |
| A2_ACTION_PRECONDITIONS | ATTACK | 4/5 | 0/5 | 0/5 |
| A2_ACTION_PRECONDITIONS | SEARCH | 2/5 | 0/5 | 0/5 |
| A2_ACTION_PRECONDITIONS | HEAL | 0/5 | 0/5 | 0/5 |
| A2_ACTION_PRECONDITIONS | REVIVE | 0/5 | 0/5 | 0/5 |
| A3_DYNAMIC_AFFORDANCE | ATTACK | 4/5 | 0/5 | 0/5 |
| A3_DYNAMIC_AFFORDANCE | SEARCH | 5/5 | 0/5 | 0/5 |
| A3_DYNAMIC_AFFORDANCE | HEAL | 0/5 | 0/5 | 0/5 |
| A3_DYNAMIC_AFFORDANCE | REVIVE | 4/5 | 2/5 | 2/5 |

## SEARCH Mapping Patterns

| Variant | current-region resource/search | MOVE-to-resource |
|---|---:|---:|
| A0_CURRENT | 0/5 | 5/5 |
| A1_LEGAL_ACTION_NAMES | 0/5 | 5/5 |
| A2_ACTION_PRECONDITIONS | 0/5 | 5/5 |
| A3_DYNAMIC_AFFORDANCE | 0/5 | 5/5 |

## Pipeline

- raw traces: 80
- parser repair: 0
- fallback: 0
- errors: 0

| Parsed (MODEL_SELECTED) | Sanitized | Executed | Count |
|---|---|---|---:|
| MOVE | MOVE | MOVE | 77 |
| REVIVE | REVIVE | REVIVE | 2 |
| SEARCH | SEARCH | SEARCH | 1 |

所有成功率、Recognition 和 Tactical response 均按 Parsed/MODEL_SELECTED 统计。

## API, Tests and Credential Validation

- real model decisions / HTTP requests: 80 / 80
- parser repair / fallback / errors: 0 / 0 / 0
- Maven: 255 tests, 0 failures, 0 errors, 0 skipped
- credential_pattern_hits: 0（报告、JSONL、实验源码和测试）
- production Prompt / sanitizer: 本轮均未修改

## Root Cause Classification

FAIL / CASE C：即使显式提供动态 Affordance，模型仍未稳定建立并表达动作映射；需要检查 Section placement、状态表达、动作语义显著性或模型能力。

## Recommended Next Step

下一轮做 Affordance Section Placement × State Representation 的受控实验；若 Recognition 仍失败，再进行 qwen-turbo 与更强模型对照。

生产 Prompt 与生产 sanitizer 均未修改。
