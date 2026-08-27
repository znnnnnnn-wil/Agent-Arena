# Prompt A/B 消融实验与 MOVE 策略塌缩根因定位

## Current Prompt Anatomy

`CURRENT PROMPT SECTION ORDER`（按 `BattleAgentContextBuilder.build` 的真实追加顺序）：

1. System instruction 与知识边界
2. Persona
3. 当前观察：Self、Public World、地图、Visible Players
4. Memory
5. Communication History
6. Relationship
7. Alliance
8. Promise
9. Trade
10. Hidden Goal
11. Cooperative Objective
12. Rumor
13. Information Trade
14. Visible Downed Players
15. Game Rules 与区域资源特色
16. JSON Example / 输出要求
17. Social Action 可选值
18. Tactical Action 可选值与目标约束

生产源码及实际生成文本均为正常 UTF-8；此前终端乱码是 PowerShell 默认解码显示问题，不是 Prompt 内容损坏。

## MOVE Bias Audit

| 检查项 | 真实结果 | 判断 |
|---|---|---|
| Java enum | `BattleActionType` 第一项是 MOVE | 存在静态位置风险，但 enum 未直接发送给模型 |
| Prompt action order | 生产列表第一项是 MOVE | 存在位置风险 |
| Structured schema enum | 不存在；当前为普通文本生成后本地 JSON 解析 | 无 enum 顺序偏置实验条件 |
| JSON Example | tactical action 是 SEARCH，不是 MOVE | 不支持 MOVE example imitation 假设 |
| Default | DTO、parser、sanitize 均无 null→MOVE 默认 | 无默认值偏置 |

消融后的最终评级：`MOVE_POSITION_BIAS_RISK = LOW`。P3 将 MOVE 放最后、P4 将目标动作放第一、P5 随机排列后仍全部选择 MOVE，因此静态顺序不是本次塌缩的有效根因。

## Experiment Configuration

- Provider / model：QWEN 百炼 / `qwen-turbo`
- Temperature：0.4
- Max tokens：900
- Retries：0
- Experiment seed：1,100,000
- 场景：ATTACK、SEARCH、HEAL、REVIVE
- 每场景每 Variant：5 次
- P0–P7 有效分析决策：160
- P8：未运行；两次诊断 Harness 空值错误产生了重复费用，为遵守 `<220` 成本目标而跳过可选实验
- 实际 API 请求：204（包含重复的 P0/P1、一次失败 repair 请求和一次成功 repair 请求）
- API/network error：0
- 被纳入分析的 fallback：0
- 被纳入分析的 parser repair：1
- 另有 1 个被丢弃的 P2 尝试连续两次解析失败，未作为实验结果使用

Trace 持久化说明：实验运行时曾生成 P2–P7 的 120 条完整 Prompt/Raw/Parsed/Sanitized/Executed Trace。公开源码仓库仅保留本聚合报告，不再分发包含完整 Prompt、内部状态和模型原始输出的 JSONL。P0/P1 两轮均完整跑完并输出逐次 Parsed/Sanitized/Executed 分布，但因随后暴露的 nullable confidence / position 统计 Harness Bug，JVM 退出前尚未持久化这 40 条内存 Trace；为遵守 API 成本上限，没有第三次重复调用。报告对此不伪造 Raw 文本。

四个场景在所有 Variant 中由同一构造器、相同 seed 和相同 GameState 参数生成；`expectedRelevantAction` 只用于离线计分，未进入 Prompt。

## Prompt Length

| Variant | 字符范围 | 估算 token 范围 | Section count |
|---|---:|---:|---:|
| P0 Current | 3275–3815 | 936–1090 | 14 |
| P1 Minimal | 460–597 | 132–171 | 2 |
| P2 No Example | 2982–3522 | 852–1007 | 14 |
| P3 MOVE Last | 3275–3815 | 936–1090 | 14 |
| P4 Target First | 3275–3815 | 936–1090 | 14 |
| P5 Random Order | 3275–3815 | 936–1090 | 14 |
| P6 No Social | 2307–2458 | 660–703 | 3 |
| P7 No Persona | 3055–3595 | 873–1028 | 13 |
| P8 Affordance | 3555–4095 | 1016–1170 | 15（仅本地构建，未调用模型） |

## Overall Result

以下均按 `MODEL_SELECTED_ACTION`（parsed）统计，不用最终执行动作冒充模型选择：

| Variant | Relevant | MOVE | SKIP | Other | 结论 |
|---|---:|---:|---:|---:|---|
| P0 Current | 0/20 (0%) | 20/20 (100%) | 0 | 0 | 稳定复现 |
| P1 Minimal | 5/20 (25%) | 10/20 (50%) | 0 | 5/20 HIDE | 仅 REVIVE 恢复 |
| P2 No Example | 0/20 | 20/20 | 0 | 0 | Example 不是根因 |
| P3 MOVE Last | 0/20 | 20/20 | 0 | 0 | MOVE 最后仍塌缩 |
| P4 Target First | 0/20 | 20/20 | 0 | 0 | 目标动作第一仍无效 |
| P5 Random Order | 0/20 | 20/20 | 0 | 0 | 非 first-option bias |
| P6 No Social Context | 0/20 | 20/20 | 0 | 0 | Social overload 不是独立根因 |
| P7 No Persona | 0/20 | 20/20 | 0 | 0 | Persona 不是独立根因 |

P1 的 ATTACK 五次模型原始选择均为 HIDE；生产 sanitize 的现有攻击价值规则把这五次改成 ATTACK。因此若只看 Engine 最终动作，会错误地把 P1 ATTACK 记为 5/5 正确。

## Per Scenario

### ATTACK

| Variant | Model selected | Sanitized / Executed |
|---|---|---|
| P0 | MOVE 5 | MOVE 5 |
| P1 | HIDE 5 | ATTACK 5 |
| P2–P7（各 Variant） | MOVE 5 | MOVE 5 |

### SEARCH

所有 P0–P7 均为 `MOVE 5/5`，Raw/Parsed/Sanitized/Executed 一致。

### HEAL

所有 P0–P7 均为 `MOVE 5/5`，包括 HP=20、MEDKIT=2、安全区且无可见敌人的 P1 Minimal。

### REVIVE

| Variant | Model selected | Sanitized / Executed |
|---|---|---|
| P0 | MOVE 5 | MOVE 5 |
| P1 | REVIVE 5 | REVIVE 5 |
| P2–P7（各 Variant） | MOVE 5 | MOVE 5 |

## P0 Current

```text
ATTACK: MOVE 5/5
SEARCH: MOVE 5/5
HEAL: MOVE 5/5
REVIVE: MOVE 5/5
MOVE rate: 100%
Relevant action rate: 0%
```

## P1 Minimal

```text
ATTACK: HIDE 5/5（sanitize/execute 为 ATTACK 5/5）
SEARCH: MOVE 5/5
HEAL: MOVE 5/5
REVIVE: REVIVE 5/5
MOVE rate: 50%
Relevant action rate（模型原始选择）: 25%
```

极简化能打破“所有场景都 MOVE”，证明完整 Prompt 确有结构/内容干扰；但它没有恢复 SEARCH/HEAL，也没有让模型原始选择 ATTACK，因此不能归结为单纯 token 过长。

## P2 No Example

20/20 MOVE。删除唯一 JSON Example 没有改变动作分布，`FEW_SHOT / EXAMPLE_BIAS` 被否定为主要原因。

## P3 MOVE Last

20/20 MOVE。MOVE 从第 1 项移动到第 11 项后分布不变。

## P4 Target Action First

20/20 MOVE。ATTACK/SEARCH/HEAL/REVIVE 分别放第一仍无效；MOVE 此时位于第 11 项。

## P5 Random Order

20/20 仍选择 MOVE，但 MOVE 的展示位置分布如下：

```text
position 1 = 1/20 = 5%
position 2 = 3/20 = 15%
position 3 = 2/20 = 10%
position 4 = 3/20 = 15%
position 5 = 3/20 = 15%
position 6 = 1/20 = 5%
position 7 = 4/20 = 20%
position 8 = 3/20 = 15%
```

模型不是选择第一项，而是在任何位置持续选择 MOVE。

## P6 No Social Context

20/20 MOVE。删除 Memory、Relationship、Alliance、Promise、Trade、Rumor、Hidden Goal、Objective、Information Trade、Communication History 后无变化。Social Context 不是独立根因。

## P7 No Persona

20/20 MOVE。删除 persona description、traits、speaking style、risk preference、behavior guidance 后无变化。Persona 不是独立根因。

## P8 Action Affordance

`NOT_RUN_COST_CAP`。Variant 及测试已实现、本地 Prompt 构建已验证，但没有发起真实 API 请求。

## Structured Output Enum Experiment

`NOT_APPLICABLE`。`OpenAiCompatibleModelFactory` 没有启用 JSON mode、response format 或 schema enum；`ModelGateway.generateStructured` 是普通文本生成后由 Jackson 本地解析。因而不能真实控制“Schema enum 顺序”，所有 Variant 已保持相同 `BattleAiDecision` 类型。

## Position Bias Analysis

P3/P4/P5 三组证据共同排除了简单位置偏置。Java enum 的 MOVE-first 也没有直接进入请求 Schema，因此不应再把 enum 顺序视为主因。

## Example Bias Analysis

生产示例 tactical action 是 SEARCH，不是 MOVE；P2 删除示例后仍 20/20 MOVE。示例不是 MOVE 塌缩主因。示例中的 `PROPOSE_ALLIANCE + PRIVATE` 仍可能解释社会字段的绑定偏置，但与 tactical MOVE 塌缩无直接证据关系。

## Context Overload Analysis

- P1 将 Prompt 从约 936–1090 tokens 降到 132–171 tokens，MOVE 从 100% 降到 50%，并恢复 REVIVE。
- P6 虽删掉大量社会上下文并缩短到约 660–703 tokens，仍 100% MOVE。
- P7 仅删 Persona 也无效果。

所以 `CONTEXT_OVERLOAD` 只是一部分解释。真正相关的差异不是“社会信息数量”或“人格”，而是 P1 同时删除了生产规则尾部、区域资源特色、全套社会/规则措辞，并把动作改成动态合法 affordance 子集。

## reasonSummary Analysis

P2–P7 的 120 条 trace 中，MOVE 理由高度模板化：

- 7 次：“前往B区域获取食物，提升生存能力。”
- 5 次：“向B区移动以获取食物，符合公开协作目标需求并补充资源。”
- 大量变体重复“去 B 获取食物”“为协作目标准备”“避免与 P2 冲突”。

这与生产 Prompt 的固定语句“区域特色：A武器、B食物、C饮水与弹药……”高度对应。即使 HEAL 场景 HP=20 且有 2 个医疗包，模型仍优先套用“移动到 B 获取食物”的模板；P6 已删除真实 Objective 上下文后，reasonSummary 仍声称“符合公开协作目标”。

因此记录：

```text
MOVE_REASON_TEMPLATE_COLLAPSE = CONFIRMED
CONTEXT_UTILIZATION_FAILURE = CONFIRMED
REGION_AFFORDANCE_ANCHORING = STRONG SUSPECT
```

## Pipeline

160 个纳入分析的决策中：

```text
Raw/Parsed action preservation: 160/160
Parsed == Sanitized == Executed: 155/160
Sanitize changed: 5/160（均为 P1 ATTACK 的 HIDE → ATTACK）
Fallback: 0
Parser repair: 1（P2，修复后仍为 MOVE）
Validation failure: 0
Execution error: 0
```

Pipeline 没有把其他模型动作普遍改成 MOVE；相反，它在 P1 ATTACK 中把 HIDE 改成 ATTACK。MOVE 塌缩发生在模型选择阶段。

## Model Capability Analysis

不能把结论简化为“模型完全无能力”：P1 中模型 5/5 正确识别 REVIVE，说明 qwen-turbo 能理解至少一种非 MOVE 战术机会。但它在更完整 Prompt 中对四种机会全部忽略，且在最简 HEAL/SEARCH 中仍 MOVE，表明存在明显的上下文利用与策略选择退化。

本轮没有更强模型的已授权配置，因此按要求跳过 Model Comparison，没有擅自换模型或 Key。

## Root Cause

```text
PRIMARY CAUSE:
qwen-turbo 在当前生产 Prompt 中发生 MOVE_REASON_TEMPLATE_COLLAPSE / CONTEXT_UTILIZATION_FAILURE。
模型被“长期生存 + 区域资源特色（尤其 B=食物）+ MOVE 可指定目标区”的组合强烈锚定，反复套用去 B 获取资源的策略模板。

SECONDARY CAUSE:
PROMPT_STRUCTURE / COMPLEXITY。极简化能恢复 REVIVE 并把 MOVE 率从 100% 降到 50%，但 Social Context 或 Persona 单独删除均无效。

已排除为主要原因:
ACTION_ORDER_BIAS、JSON_EXAMPLE_BIAS、SCHEMA_ENUM_BIAS、PARSER、SANITIZE、ENGINE。
```

由于没有单独运行 `NO_REGION_AFFORDANCE`，`REGION_AFFORDANCE_ANCHORING` 仍属于强证据推断而非最终单变量因果确认。

## Recommended Prompt Architecture

仅给建议，本轮不实施：

1. 下一轮先做唯一必要的单变量实验：P0 只删除“区域特色”一句，另做只删除“长期资源/协作目标措辞”的对照，确认锚定来源。
2. 若确认，采用两阶段 Prompt：先用动态合法 tactical affordance 做战术选择，再把社会上下文用于 socialAction；不要粗暴删除社会系统。
3. 再用 P0 与最佳重构 Variant 对照一个已合法配置的更强模型，以区分 Prompt 敏感性和 qwen-turbo 能力上限。

## Production Code

生产 `BattleAgentContextBuilder` 未修改。新增内容仅包括实验 Harness、Prompt Variant、Trace metadata、测试和报告。
