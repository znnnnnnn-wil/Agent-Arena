# Objective × Action Affordance Interaction Experiment

## Hypothesis

检验逐字复用的 D3 Opportunity First Objective 与原样 A3 Dynamic Affordance 是否产生描述性交互效应。

## Factor and Prompt Diff Validation

| Variant | Objective | Affordance | Changed sections |
|---|---|---|---|
| I0_CURRENT_NONE | Current | None | [] |
| I1_D3_NONE | D3 | None | [DECISION_OBJECTIVE] |
| I2_CURRENT_A3 | Current | A3 | [ACTION_AFFORDANCE] |
| I3_D3_A3 | D3 | A3 | [DECISION_OBJECTIVE, ACTION_AFFORDANCE] |

D3 文本逐字引用第一轮常量，A3 的 wording、placement、state bindings、candidate filtering 逐字/逐逻辑复用第二轮实现。其余生产 Prompt byte-identical。

## Experiment Config

- QWEN / qwen-turbo; temperature 0.4; maxTokens 900; retries 0
- scenarios: ATTACK, SEARCH, HEAL, REVIVE; repeats: 5
- requested: 80; completed so far: 80

## 2×2 Interaction Table

```text
                         No Affordance       A3 Affordance
Current Objective        I0                  I2
D3 Objective             I1                  I3
```

| Variant | Relevant | MOVE | ATTACK | SEARCH | HEAL | REVIVE | Context Recognition | Affordance Recognition | Non-MOVE | Diversity | Parsed=Executed | Sanitize Changed |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| I0_CURRENT_NONE | 0.00% | 100.00% | 0.00% | 0.00% | 0.00% | 0.00% | 15.00% | 0.00% | 0.00% | 1 | 100.00% | 0.00% |
| I1_D3_NONE | 25.00% | 75.00% | 0.00% | 0.00% | 0.00% | 100.00% | 70.00% | 15.00% | 25.00% | 2 | 100.00% | 0.00% |
| I2_CURRENT_A3 | 15.00% | 85.00% | 0.00% | 0.00% | 0.00% | 60.00% | 50.00% | 15.00% | 15.00% | 2 | 100.00% | 0.00% |
| I3_D3_A3 | 30.00% | 55.00% | 20.00% | 0.00% | 0.00% | 100.00% | 75.00% | 10.00% | 45.00% | 4 | 85.00% | 15.00% |

## ATTACK

| Variant | State seen | Affordance explicitly recognized in reasonSummary | Target selected | MOVE | Tactical response | Distribution |
|---|---:|---:|---:|---:|---:|---|
| I0_CURRENT_NONE | 3/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| I1_D3_NONE | 4/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| I2_CURRENT_A3 | 4/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| I3_D3_A3 | 5/5 | 0/5 | 1/5 | 1/5 | 1/5 | {ATTACK=1, MOVE=1, SEARCH=3} |

## SEARCH

| Variant | State seen | Affordance explicitly recognized in reasonSummary | Target selected | MOVE | Current-region | MOVE-to-resource | Distribution |
|---|---:|---:|---:|---:|---:|---:|---|
| I0_CURRENT_NONE | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | 5/5 | {MOVE=5} |
| I1_D3_NONE | 5/5 | 0/5 | 0/5 | 5/5 | 0/5 | 3/5 | {MOVE=5} |
| I2_CURRENT_A3 | 3/5 | 0/5 | 0/5 | 5/5 | 0/5 | 5/5 | {MOVE=5} |
| I3_D3_A3 | 5/5 | 0/5 | 0/5 | 5/5 | 0/5 | 5/5 | {MOVE=5} |

## HEAL

| Variant | State seen | Affordance explicitly recognized in reasonSummary | Target selected | MOVE | HP seen | MEDKIT seen | Distribution |
|---|---:|---:|---:|---:|---:|---:|---|
| I0_CURRENT_NONE | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| I1_D3_NONE | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| I2_CURRENT_A3 | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |
| I3_D3_A3 | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | 0/5 | {MOVE=5} |

## REVIVE

| Variant | State seen | Affordance explicitly recognized in reasonSummary | Target selected | MOVE | DOWNED seen | Distribution |
|---|---:|---:|---:|---:|---:|---|
| I0_CURRENT_NONE | 0/5 | 0/5 | 0/5 | 5/5 | 0/5 | {MOVE=5} |
| I1_D3_NONE | 5/5 | 3/5 | 5/5 | 0/5 | 3/5 | {REVIVE=5} |
| I2_CURRENT_A3 | 3/5 | 3/5 | 3/5 | 2/5 | 3/5 | {MOVE=2, REVIVE=3} |
| I3_D3_A3 | 5/5 | 2/5 | 5/5 | 0/5 | 2/5 | {REVIVE=5} |

## Recognition Ladder

| Variant | Scenario | STATE SEEN | AFFORDANCE EXPLICITLY RECOGNIZED | ACTION SELECTED |
|---|---|---:|---:|---:|
| I0_CURRENT_NONE | ATTACK | 3/5 | 0/5 | 0/5 |
| I0_CURRENT_NONE | SEARCH | 0/5 | 0/5 | 0/5 |
| I0_CURRENT_NONE | HEAL | 0/5 | 0/5 | 0/5 |
| I0_CURRENT_NONE | REVIVE | 0/5 | 0/5 | 0/5 |
| I1_D3_NONE | ATTACK | 4/5 | 0/5 | 0/5 |
| I1_D3_NONE | SEARCH | 5/5 | 0/5 | 0/5 |
| I1_D3_NONE | HEAL | 0/5 | 0/5 | 0/5 |
| I1_D3_NONE | REVIVE | 5/5 | 3/5 | 5/5 |
| I2_CURRENT_A3 | ATTACK | 4/5 | 0/5 | 0/5 |
| I2_CURRENT_A3 | SEARCH | 3/5 | 0/5 | 0/5 |
| I2_CURRENT_A3 | HEAL | 0/5 | 0/5 | 0/5 |
| I2_CURRENT_A3 | REVIVE | 3/5 | 3/5 | 3/5 |
| I3_D3_A3 | ATTACK | 5/5 | 0/5 | 1/5 |
| I3_D3_A3 | SEARCH | 5/5 | 0/5 | 0/5 |
| I3_D3_A3 | HEAL | 0/5 | 0/5 | 0/5 |
| I3_D3_A3 | REVIVE | 5/5 | 2/5 | 5/5 |

## Main and Interaction Effects

所有数值均为本轮 Parsed 结果的百分点变化；interaction 为描述性 difference-in-differences，不是显著性检验。

| Metric | D3 effect without A3 (I1-I0) | D3 effect with A3 (I3-I2) | A3 effect under Current (I2-I0) | A3 effect under D3 (I3-I1) | Observed interaction delta |
|---|---:|---:|---:|---:|---:|
| Relevant | +25.00 pp | +15.00 pp | +15.00 pp | +5.00 pp | -10.00 pp |
| HEAL | +0.00 pp | +0.00 pp | +0.00 pp | +0.00 pp | +0.00 pp |
| SEARCH | +0.00 pp | +0.00 pp | +0.00 pp | +0.00 pp | +0.00 pp |
| ATTACK | +0.00 pp | +20.00 pp | +0.00 pp | +20.00 pp | +20.00 pp |
| REVIVE | +100.00 pp | +40.00 pp | +60.00 pp | +0.00 pp | -60.00 pp |
| MOVE | -25.00 pp | -30.00 pp | -15.00 pp | -20.00 pp | -5.00 pp |
| Context Recognition | +55.00 pp | +25.00 pp | +35.00 pp | +5.00 pp | -30.00 pp |
| Affordance Recognition | +15.00 pp | -5.00 pp | +15.00 pp | -5.00 pp | -20.00 pp |

### Objective Main Effect

- 无 A3 时，D3 令 Relevant `0% → 25%`、MOVE `100% → 75%`，来源全部是 REVIVE。
- 有 A3 时，D3 令 Relevant `15% → 30%`、MOVE `85% → 55%`；模型主动 ATTACK 增加到 1/5，REVIVE 从 3/5 恢复到 5/5。

### Affordance Main Effect

- Current Objective 下，A3 令 Relevant `0% → 15%`，仅恢复 REVIVE 3/5。
- D3 Objective 下，A3 令 Relevant `25% → 30%`；HEAL 和 SEARCH 增量均为 0，ATTACK 增加 20pp，但样本仅 1/5。

### Interaction Effect Interpretation

- Relevant 的 observed interaction delta 为 `-10pp`，没有总体正协同。
- HEAL、SEARCH interaction 均为 `0pp`。
- ATTACK 出现 `+20pp` 的有限描述性信号，但只有 1/5，Tactical response 同样只有 1/5。
- REVIVE 的 `-60pp` difference-in-differences 主要来自 D3 单独已达到 5/5 的天花板；I3 仍为 5/5，并未出现实际下降或负干扰。
- 以上全部是 descriptive interaction only，不是统计显著性结论。

## Pipeline

- raw: 80; parser repair: 0; fallback: 0; errors: 0

| Parsed | Sanitized | Executed | Count |
|---|---|---|---:|
| ATTACK | ATTACK | ATTACK | 1 |
| MOVE | MOVE | MOVE | 63 |
| REVIVE | REVIVE | REVIVE | 13 |
| SEARCH | ATTACK | ATTACK | 3 |

Relevant、Recognition 和 Tactical response 均按 Parsed/MODEL_SELECTED。

## API, Tests and Credential Validation

- real model decisions / HTTP requests: 80 / 80
- parser repair / fallback / errors: 0 / 0 / 0
- Maven: 263 tests, 0 failures, 0 errors, 0 skipped
- credential_pattern_hits: 0（报告、JSONL、实验源码和测试）
- production Prompt / sanitizer / validator / executor: 本轮均未修改

## Historical Replication

- I0 Current baseline: replicated，MOVE 20/20。
- I1 D3 historical REVIVE≈5/5: replicated，本轮 5/5。
- I2 A3 historical REVIVE≈2/5 且三核心为0: replicated，本轮 REVIVE 3/5。
历史数字仅作 reference，主表全部来自本轮80次。

## Root Cause Classification

PARTIAL / CASE B — REVIVE-only Interaction：组合仍只解决救援类机会。

## Recommended Next Step

下一轮进行 Affordance Section Placement × State Representation 严格受控实验；本轮不提前实施。

生产 Prompt、sanitizer、validator 和 executor 均未修改。
