# Decision Objective A/B 实验报告

## Hypothesis

检验生产 Prompt 的顶层长期胜利目标是否因缺少即时状态比较框架，导致 qwen-turbo 将 MOVE 作为通用默认动作。

## Current Decision Objective

```text
你正在参加一局多智能体大逃杀，目标是成为最后幸存者。
```

## Experimental Objectives

### D0_CURRENT

```text
你正在参加一局多智能体大逃杀，目标是成为最后幸存者。
```

### D1_BALANCED_OBJECTIVE

```text
本局最终胜利条件仍是成为最后幸存者。
你的任务是在每次行动时，根据当前实际知道的信息选择当前最合理的行动。
做决定时，同时比较当前即时状态与风险、已经出现的明确行动机会、需要处理的社会关系、协议或义务、资源与位置的后续影响，以及最终获胜这一长期目标。
不要因为某一种长期战略通常有价值，就忽略当前已经存在的明显状态或机会；每次都应依据当前局势重新判断总体收益。
```

### D2_DECISION_CHECKLIST

```text
本局最终胜利条件仍是成为最后幸存者。
在选择行动时，按以下检查框架权衡当前实际信息：
第一，检查是否存在需要立即处理的状态，如严重受伤、直接危险、倒地玩家或迫近环境风险。
第二，检查是否存在当前即可执行且收益明确的机会，如攻击、搜索、治疗、救援、交易或履行已有协议。
第三，再考虑移动、区域资源规划、长期目标与未来布局。
这不是固定优先级规则；如果后项整体收益更高仍可选择后项。你必须根据当前信息自行权衡。
```

### D3_OPPORTUNITY_FIRST_NEUTRAL

```text
本局最终胜利条件仍是成为最后幸存者。
根据当前观察选择行动：先比较当前已经合法且可立即执行的具体机会，再考虑为未来资源或位置而行动。
长期胜利条件很重要，但不能自动覆盖当前已经出现的具体机会。
选择你认为当前总体收益最高的行动。
```

## Prompt Diff Validation

| Variant | Changed sections | Other production sections |
|---|---|---|
| D0_CURRENT | [] | byte-identical |
| D1_BALANCED_OBJECTIVE | [DECISION_OBJECTIVE] | unchanged |
| D2_DECISION_CHECKLIST | [DECISION_OBJECTIVE] | unchanged |
| D3_OPPORTUNITY_FIRST_NEUTRAL | [DECISION_OBJECTIVE] | unchanged |

等值替换测试验证 GameState fixture、Persona、Memory、Social Context、Rules、Region Affordance、Action order、JSON Example、输出格式均未变化；四组均保留最后幸存者胜利条件。

## Experiment Config

- provider/model: QWEN / qwen-turbo
- temperature: 0.4
- maxTokens: 900
- retries: 0
- scenarios: ATTACK, SEARCH, HEAL, REVIVE（复用既有 fixture）
- repeats: 5
- requested decisions: 80
- completed so far: 80

## Overall Result

| Variant | Relevant | MOVE | Non-MOVE | Diversity | Context Ref | Actions |
|---|---:|---:|---:|---:|---:|---|
| D0_CURRENT | 0.00% | 100.00% | 0.00% | 1 | 45.00% | [MOVE] |
| D1_BALANCED_OBJECTIVE | 20.00% | 80.00% | 20.00% | 2 | 45.00% | [MOVE, REVIVE] |
| D2_DECISION_CHECKLIST | 25.00% | 50.00% | 50.00% | 4 | 50.00% | [HEAL, MOVE, REVIVE, SEARCH] |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 25.00% | 75.00% | 25.00% | 2 | 65.00% | [MOVE, REVIVE] |

## ATTACK

| Variant | ATTACK | MOVE | Non-MOVE | Context Ref | Tactical response | Distribution |
|---|---:|---:|---:|---:|---:|---|
| D0_CURRENT | 0/5 | 5/5 | 0/5 | 4/5 | 0/5 | {MOVE=5} |
| D1_BALANCED_OBJECTIVE | 0/5 | 5/5 | 0/5 | 1/5 | 0/5 | {MOVE=5} |
| D2_DECISION_CHECKLIST | 0/5 | 0/5 | 5/5 | 1/5 | 0/5 | {HEAL=2, SEARCH=3} |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 0/5 | 5/5 | 0/5 | 3/5 | 0/5 | {MOVE=5} |

## SEARCH

| Variant | SEARCH | MOVE | Non-MOVE | Context Ref | Distribution |
|---|---:|---:|---:|---:|---|
| D0_CURRENT | 0/5 | 5/5 | 0/5 | 5/5 | {MOVE=5} |
| D1_BALANCED_OBJECTIVE | 0/5 | 5/5 | 0/5 | 5/5 | {MOVE=5} |
| D2_DECISION_CHECKLIST | 0/5 | 5/5 | 0/5 | 5/5 | {MOVE=5} |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 0/5 | 5/5 | 0/5 | 5/5 | {MOVE=5} |

## HEAL

| Variant | HEAL | MOVE | Non-MOVE | Context Ref | Distribution |
|---|---:|---:|---:|---:|---|
| D0_CURRENT | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| D1_BALANCED_OBJECTIVE | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| D2_DECISION_CHECKLIST | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |

## REVIVE

| Variant | REVIVE | MOVE | Non-MOVE | Context Ref | Distribution |
|---|---:|---:|---:|---:|---|
| D0_CURRENT | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| D1_BALANCED_OBJECTIVE | 4/5 | 1/5 | 4/5 | 3/5 | {MOVE=1, REVIVE=4} |
| D2_DECISION_CHECKLIST | 5/5 | 0/5 | 5/5 | 4/5 | {REVIVE=5} |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 5/5 | 0/5 | 5/5 | 5/5 | {REVIVE=5} |

## Context Utilization

| Variant | ATTACK | SEARCH | HEAL | REVIVE | Overall |
|---|---:|---:|---:|---:|---:|
| D0_CURRENT | 80.00% | 100.00% | 0.00% | 0.00% | 45.00% |
| D1_BALANCED_OBJECTIVE | 20.00% | 100.00% | 0.00% | 60.00% | 45.00% |
| D2_DECISION_CHECKLIST | 20.00% | 100.00% | 0.00% | 80.00% | 50.00% |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 60.00% | 100.00% | 0.00% | 100.00% | 65.00% |

## reasonSummary Patterns

### ATTACK

| Variant | Keyword hits | Summaries |
|---|---:|---|
| D0_CURRENT | 4/5 | {向B区移动以获取食物，符合长期生存策略。=1, 向B区移动以获取食物资源，符合RESOURCE_HOARDER目标。=1, 向B区移动以获取食物，符合公开协作目标需求，同时降低暴露风险。=1, 前往C区以完成协作目标并获取资源，同时观察P2动向。=1, 向B区移动以获取食物，符合公开协作目标需求并增强生存能力。=1} |
| D1_BALANCED_OBJECTIVE | 1/5 | {向B区移动以获取食物资源，同时避开P2的高风险区域A。=2, 向B区移动以获取食物资源，符合私人目标RESOURCE_HOARDER，并为后续协作目标提供支持。=1, 向B区移动以获取食物资源，同时避开当前区域的高风险玩家P2。=1, 向B区移动以获取食物资源，同时观察P2的动向。=1} |
| D2_DECISION_CHECKLIST | 1/5 | {搜索A区以获取资源，同时观察P2的状态。=1, 优先处理当前区域资源，为私人目标和协作目标积累物资。=1, P2处于CRITICAL状态，可能需要医疗救助，优先保障其生存以获取潜在合作机会。=1, 搜索A区以获取资源，同时观察P2状态变化。=1, 治疗自己以确保生存，保持健康状态应对后续挑战。=1} |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 3/5 | {向B区移动以获取食物资源，符合当前生存需求且安全。=1, 向B区移动以获取食物资源，符合长期生存目标且当前无直接威胁。=1, 向B区移动以获取食物，满足协作目标需求并增强生存能力。=1, 向B区移动以获取食物资源，符合当前生存需求且风险可控。=1, 向B区移动以获取食物，补充资源以支持长期生存，并为可能的协作目标做准备。=1} |
### SEARCH

| Variant | Keyword hits | Summaries |
|---|---:|---|
| D0_CURRENT | 5/5 | {前往B区域获取食物，为后续协作目标做准备。=1, 前往C区获取饮水和弹药，支持资源囤积目标。=1, 前往B区域获取食物，以完成协作目标。=1, 前往B区域获取食物，为协作目标做准备。=1, 前往B区域获取食物，符合长期生存策略。=1} |
| D1_BALANCED_OBJECTIVE | 5/5 | {向B区域移动以获取食物资源，符合当前生存需求。=1, 前往B区域获取食物，以完成协作目标并支持长期生存策略。=1, 前往B区域获取食物，以满足协作目标需求并提升生存几率。=1, 前往B区获取食物，满足协作目标需求，同时为后续行动储备资源。=1, 向B区域移动以获取食物资源，符合长期生存需求。=1} |
| D2_DECISION_CHECKLIST | 5/5 | {优先移动至B区域获取食物资源，符合长期生存需求。=1, 优先移动至B区域获取食物，符合资源囤积目标且当前无直接威胁。=1, 向B区域移动以获取食物，符合长期生存目标且当前无直接威胁。=1, 向B区域移动以获取食物，满足协作目标需求。=1, 优先移动到B区域获取食物资源，符合长期生存策略。=1} |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 5/5 | {当前无可见对手，移动至B区获取食物资源以完成协作目标。=1, 优先获取食物资源以完成公开协作目标，同时为长期生存积累物资。=1, 前往B区域获取食物，满足协作目标需求并增强生存能力。=1, 向B区域移动以获取食物，满足协作目标需求。=1, 前往B区域获取食物，为后续资源积累和协作目标做准备。=1} |
### HEAL

| Variant | Keyword hits | Summaries |
|---|---:|---|
| D0_CURRENT | 0/5 | {前往B区域获取食物，补充生存资源以支持长期生存目标。=1, 前往C区获取饮水和弹药以完成协作目标=1, 前往C区以完成协作目标并获取饮水资源，同时接近可能的其他玩家。=1, 前往C区域获取饮水和弹药以完成协作目标，同时观察周围环境。=1, 前往C区域以完成协作目标，同时获取水和食物资源。=1} |
| D1_BALANCED_OBJECTIVE | 0/5 | {向B区域移动以获取食物资源，符合当前生存需求。=1, 向B区域移动以获取食物资源，符合RESOURCE_HOARDER目标需求。=1, 向B区域移动以获取食物，补充资源以支持长期生存和完成协作目标。=1, 向B区域移动以获取食物，符合公开协作目标需求并补充资源。=1, 向B区域移动以获取食物，补充资源并增强生存能力。=1} |
| D2_DECISION_CHECKLIST | 0/5 | {向B区域移动以获取食物资源，符合长期生存策略。=2, 前往C区域以完成协作目标并收集资源，符合长期生存策略。=1, 向B区域移动以获取食物资源，支持公开协作目标并提升生存能力。=1, 向B区域移动以收集食物，符合公开协作目标需求。=1} |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 0/5 | {前往B区域获取食物，补充资源以支持长期生存和协作目标。=1, 前往C区以完成协作目标并获取资源，符合当前可立即执行的机会。=1, 向B区域移动以获取食物资源，符合当前生存需求且区域安全。=1, 前往B区域获取食物，满足协作目标需求并补充资源。=1, B区域有食物资源，有助于完成公开协作目标并补充生存资源。=1} |
### REVIVE

| Variant | Keyword hits | Summaries |
|---|---:|---|
| D0_CURRENT | 0/5 | {向B区移动以获取食物，补充资源并支持公开协作目标。=1, 前往B区获取食物，满足协作目标需求并补充资源。=1, 向C区移动以完成协作目标并获取补给=1, 向B区移动以获取食物，符合公开协作目标需求。=1, 向B区移动以获取食物，补充生存资源并为后续行动做准备。=1} |
| D1_BALANCED_OBJECTIVE | 3/5 | {P2处于倒地状态且在安全区域A，救援可增强联盟关系并提升生存机会。=1, 向B区移动以获取食物资源，同时避免与P2直接冲突。=1, P2 is downed in A with low health. Rescuing them could build trust and potentially gain an ally. They are in a safe region and have no immediate threats.=1, 救援P2可获得信任并获取情报，符合信息贩子目标，且其状态允许救援。=1, 救援P2以维持联盟并获取潜在合作机会。=1} |
| D2_DECISION_CHECKLIST | 4/5 | {P2处于倒地状态且在我所在区域，救援符合当前联盟协议，同时可增强信任关系。=1, 救援P2可获得高信任度，且其状态为DOWNED，可能成为未来盟友。=1, P2 is downed with critical health and has a high trust level. Rescuing them could strengthen our alliance and provide future benefits.=1, 救援P2符合当前利益，其处于濒死状态且与我有高信任关系，可获取情报或建立合作。=1, P2处于DOWNED状态且在A区，当前区域安全，救援可提升联盟关系并可能获得后续合作机会。=1} |
| D3_OPPORTUNITY_FIRST_NEUTRAL | 5/5 | {优先救援当前区域的Downed玩家，利用高信任关系和现有医疗包资源，提升联盟稳定性并获取潜在合作价值。=1, 当前P2处于DOWNED状态且在安全区域A，救援可增强联盟关系并获取潜在合作资源，同时符合私人目标中资源积累的长期策略。=1, 优先救援已知信任度高且处于关键位置的玩家，提升生存机会和潜在协作价值。=1, 救援P2可获得信息优势，符合私人目标且风险可控。=1, P2处于Downed状态且在A区，与我同区域，救援成本低且符合我的私人目标和联盟关系。=1} |

## Pipeline Verification

- Raw traces: 80
- parser repair: 0
- fallback: 0
- sanitize changed: 3
- parsed == executed: 77/80
- errors: 0（实验若抛出请求错误会停止，绝不以 fallback 冒充结果）

### Parsed → Sanitized → Executed Transformation Matrix

| Parsed (MODEL_SELECTED) | Sanitized | Executed | Count |
|---|---|---|---:|
| HEAL | HEAL | HEAL | 2 |
| MOVE | MOVE | MOVE | 61 |
| REVIVE | REVIVE | REVIVE | 14 |
| SEARCH | ATTACK | ATTACK | 3 |

D2/ATTACK 中 3 次 `SEARCH → ATTACK → ATTACK` 是生产 sanitizer 的替换，不能计为模型主动选择 ATTACK；Strict Relevant 与 Tactical response 始终按 Parsed/MODEL_SELECTED 统计。

### API and Artifact Validation

- real model decisions / HTTP requests: 80 / 80
- parser repair / fallback / errors: 0 / 0 / 0
- credential_pattern_hits: 0（报告、JSONL、实验源码和测试）
- Maven: 242 tests, 0 failures, 0 errors, 0 skipped
- production Prompt: 本轮未修改

## Root Cause

PARTIAL / CASE C：新 Objective 只恢复 REVIVE，不能解释 ATTACK、SEARCH、HEAL 的剩余 MOVE 塌缩；剩余主因应转入 ACTION_AFFORDANCE 验证。

## Recommended Production Decision Architecture

B：下一轮只进行 Action Affordance Experiment，检验动态合法动作及其可执行条件的表达；不要继续改写 Objective。

本轮未修改生产 Prompt；这里只记录下一轮建议。
