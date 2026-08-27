# 长期生存目标 × 区域资源特色 2×2 因果消融报告

## Hypothesis

检验 qwen-turbo 的 MOVE 塌缩来自 LONG_TERM_GOAL、REGION_AFFORDANCE、二者交互，或两者均非原因。

## Factor Definitions

- A+：逐字保留生产目标“你正在参加一局多智能体大逃杀，目标是成为最后幸存者。”
- A-：仅替换为中性任务“你需要根据当前观察和可执行行动，选择此刻最合理的行动，并遵守游戏规则。”
- B+：逐字保留生产区域特色映射。
- B-：仅删除区域→资源映射，保留当前位置、拓扑、安全区和 targetRegion 约束。

说明：Controlled fixture 的 Persona description 含英文 `Balances risk and long-term survival`，按“Persona 必须保持不变”的控制要求四组均保留。A- 移除的是生产 Builder 的顶层战略指令，不把 Persona 作为第二个变量顺便修改；因此测得的 A 主效应是保守估计。

## Prompt Diff Validation

| Variant | LT Goal | Region Affordance | Changed Sections |
|---|---|---|---|
| V00 | ON | ON | []（与生产 Prompt 逐字一致） |
| V01 | ON | OFF | [REGION_AFFORDANCE] |
| V10 | OFF | ON | [LONG_TERM_GOAL] |
| V11 | OFF | OFF | [LONG_TERM_GOAL, REGION_AFFORDANCE] |

B- 全文校验不含区域特色、A武器、B食物、C饮水与弹药、D护甲、E医疗；A- 全文校验不含战略性“最后幸存者/长期生存”指令。JSON Example、Action order、Persona、Social Context 和输出格式保持不变。

## Experiment Config

- provider/model: QWEN / qwen-turbo
- temperature: 0.4
- maxTokens: 900
- retries: 0
- scenarios: ATTACK, SEARCH, HEAL, REVIVE
- repeats: 5
- requested decisions: 80
- completed so far: 80
- successful decisions: 80
- estimated HTTP requests: 80
- parser repair: 0
- fallback: 0
- API/network errors: 0

## Overall 2×2 Results

| Variant | LT | Region | Strict Relevant | MOVE | Non-MOVE | Diversity | Actions |
|---|---|---|---:|---:|---:|---:|---|
| V00 | ON | ON | 0.00% | 100.00% | 0.00% | 1 | [MOVE] |
| V01 | ON | OFF | 0.00% | 100.00% | 0.00% | 1 | [MOVE] |
| V10 | OFF | ON | 20.00% | 80.00% | 20.00% | 2 | [MOVE, REVIVE] |
| V11 | OFF | OFF | 25.00% | 75.00% | 25.00% | 2 | [MOVE, REVIVE] |

## ATTACK

| Variant | ATTACK | MOVE | HIDE | Other | Distribution |
|---|---:|---:|---:|---:|---|
| V00 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V01 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V10 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V11 | 0 | 5 | 0 | 0 | {MOVE=5} |

## SEARCH

| Variant | SEARCH | MOVE | HIDE | Other | Distribution |
|---|---:|---:|---:|---:|---|
| V00 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V01 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V10 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V11 | 0 | 5 | 0 | 0 | {MOVE=5} |

## HEAL

| Variant | HEAL | MOVE | HIDE | Other | Distribution |
|---|---:|---:|---:|---:|---|
| V00 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V01 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V10 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V11 | 0 | 5 | 0 | 0 | {MOVE=5} |

## REVIVE

| Variant | REVIVE | MOVE | HIDE | Other | Distribution |
|---|---:|---:|---:|---:|---|
| V00 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V01 | 0 | 5 | 0 | 0 | {MOVE=5} |
| V10 | 4 | 1 | 0 | 0 | {MOVE=1, REVIVE=4} |
| V11 | 5 | 0 | 0 | 0 | {REVIVE=5} |

## MOVE Target Region

| Variant | MOVE count | A | B | C | D | E | B share |
|---|---:|---:|---:|---:|---:|---:|---:|
| V00 | 20 | 0 | 16 | 4 | 0 | 0 | 80.00% |
| V01 | 20 | 0 | 15 | 5 | 0 | 0 | 75.00% |
| V10 | 16 | 0 | 15 | 1 | 0 | 0 | 93.75% |
| V11 | 15 | 0 | 15 | 0 | 0 | 0 | 100.00% |

## reasonSummary Analysis

| Variant | 食物 | B区/B区域 | 资源 | 长期生存 | 最后幸存 | 协作目标 | 安全 | 医疗 | 受伤 | 敌人 | 救援 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| V00 | 16 | 16 | 11 | 6 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| V01 | 0 | 13 | 20 | 0 | 0 | 7 | 3 | 0 | 0 | 0 | 0 |
| V10 | 15 | 15 | 7 | 2 | 0 | 9 | 1 | 0 | 0 | 0 | 3 |
| V11 | 0 | 14 | 16 | 0 | 0 | 5 | 3 | 0 | 0 | 0 | 2 |

B- Prompt 已确认无区域→资源映射；B- 中仍出现“B区+食物”的次数：0。大于 0 时标记 `STALE_PATTERN_CONTINUATION`。

实际结果不触发 `STALE_PATTERN_CONTINUATION`：删除区域特色后，模型不再声称“B区有食物”。但它仍大量选择 B，说明区域特色影响了理由内容，却没有决定 MOVE 动作或 B 目标本身。

## Long-Term Goal Effect

MOVE 主效应（A+ 平均 MOVE率 − A- 平均 MOVE率）：+22.50 percentage points。

## Region Affordance Effect

MOVE 主效应（B+ 平均 MOVE率 − B- 平均 MOVE率）：+2.50 percentage points。

## Interaction Effect

Difference-in-differences：-5.00 percentage points。

## Context Sensitivity

- V00: scenario modal action diversity=1 [MOVE]
- V01: scenario modal action diversity=1 [MOVE]
- V10: scenario modal action diversity=2 [MOVE, REVIVE]
- V11: scenario modal action diversity=2 [MOVE, REVIVE]

## Pipeline

- successful MODEL decisions: 80
- fallback: 0
- parser repair: 0
- sanitize changed: 0
- parsed == executed: 80/80

## Root Cause

PRIMARY: LONG_TERM_GOAL_ANCHORING。

这是两个候选因素中的相对主因，而不是完整解释：A- 只在 REVIVE 场景恢复行为，使平均 MOVE 下降 22.5 个百分点；ATTACK、SEARCH、HEAL 完全不变，V11 仍有 75% MOVE。REGION_AFFORDANCE 对 MOVE 的主效应仅 2.5 个百分点，Interaction 为 -5 个百分点，不支持区域主因或正向叠加效应。

## Recommended Production Prompt Architecture

推荐重写 Decision Objective，使即时状态与长期胜利条件并列而非单向长期生存锚定。

生产 Prompt 未修改；以上仅为下一阶段设计建议。
