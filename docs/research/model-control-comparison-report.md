# qwen-turbo vs qwen-max 模型控制对照实验

## Experiment Config

- Same provider: QWEN / 百炼 OpenAI-compatible endpoint
- Same D3 + A3 Prompt, fixture, action order and production pipeline
- temperature 0.4; maxTokens 900; retries 0
- 4 scenarios × 5 × 2 models = 40 decisions

## Model Configuration

- M0_CONTROL: qwen-turbo
- M1_STRONG: qwen-max（先由账号 `/models` 列表确认，再以一次最小请求探针确认可调用）

## Prompt Equality Validation

每个 scenario/run 的两个模型 Prompt 使用 SHA-256 自动比较；不一致会在调用 M1 前终止。唯一运行配置差异是 modelName。

## Overall Comparison

| Model | Relevant | MOVE | Non-MOVE | Diversity | Context Recognition | Affordance Recognition | Parsed=Executed | Sanitize Changed |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| M0_CONTROL / qwen-turbo | 30.00% | 65.00% | 35.00% | 4 | 75.00% | 0.00% | 95.00% | 5.00% |
| M1_STRONG / qwen-max | 50.00% | 0.00% | 100.00% | 3 | 75.00% | 25.00% | 95.00% | 5.00% |

## HEAL

| Model | State seen | Affordance recognized | Target selected | MOVE | Distribution | reasonSummary |
|---|---:|---:|---:|---:|---|---|
| M0_CONTROL | 0/5 | 0/5 | 0/5 | 5/5 | {MOVE=5} | [向B区域移动以获取食物资源，符合当前生存需求且风险可控。, 向B区域移动以获取食物资源，符合公开协作目标需求并支持长期生存。, 向B移动以获取食物资源，符合当前公开协作目标需求。, 前往C区域以接近公开协作目标，同时获取饮水和弹药资源。, 移动到B区域以获取食物资源，符合当前可执行动作且有利于长期生存。] |
| M1_STRONG | 0/5 | 0/5 | 0/5 | 0/5 | {SEARCH=5} | [搜索资源并公开寻求合作机会以提高生存几率。, 在A区搜索资源，符合我的私人目标RESOURCE_HOARDER，同时保持隐蔽和安全。, 在A区搜索以获取更多资源，同时保持隐蔽和健康状态。, 当前没有其他玩家在视野内，搜索A区可能获得武器与战斗资源，为长期生存做准备。, 在A区搜索可能的资源，为长期生存做准备。] |

## SEARCH

| Model | State seen | Affordance recognized | Target selected | MOVE | Distribution | reasonSummary |
|---|---:|---:|---:|---:|---|---|
| M0_CONTROL | 5/5 | 0/5 | 0/5 | 5/5 | {MOVE=5} | [向B区域移动以获取食物资源，符合当前生存需求。, 向B区域移动以获取食物资源，符合当前生存需求和私人目标, 向B区域移动以获取食物资源，符合当前生存需求。, 优先获取食物资源，支持长期生存和协作目标, 移动到B区域获取食物资源，符合当前生存需求。] |
| M1_STRONG | 5/5 | 5/5 | 5/5 | 0/5 | {SEARCH=5} | [在当前区域搜索资源，以增加生存物资。同时保持隐蔽，避免不必要的冲突。, 搜索A区可能的资源以支持长期生存和私人目标。, 在A区搜索可能的武器与战斗资源，为长期生存做准备。, 在A区搜索可能的武器与战斗资源，以增强生存能力。, 当前没有直接威胁，搜索A区可能获得武器或战斗资源，同时公开寻求合作机会以推进私人目标。] |

## ATTACK

| Model | State seen | Affordance recognized | Target selected | MOVE | Tactical response | Distribution | reasonSummary |
|---|---:|---:|---:|---:|---:|---|---|
| M0_CONTROL | 5/5 | 0/5 | 1/5 | 3/5 | 1/5 | {ATTACK=1, MOVE=3, SEARCH=1} | [向B区移动以获取食物资源，同时避免与处于危急状态的P2直接冲突。, 当前在A区，可搜索获取资源，同时观察P2状态。, 向B区移动以获取食物资源，符合长期生存目标且当前可执行, P2 is in the same region and is critically injured, making him a high-value target for elimination., 向B区移动以获取食物资源，同时避免与处于临界状态的P2直接冲突。] |
| M1_STRONG | 5/5 | 0/5 | 0/5 | 0/5 | 0/5 | {HEAL=4, SEARCH=1} | [P2处于CRITICAL状态，通过救助建立信任并提议结盟，符合我的私人目标和当前局势。, 当前没有直接威胁，P2处于CRITICAL状态不太可能立即构成威胁。搜索A区可能获得有用的资源，符合我的私人目标。, 救助P2以完成我的私人目标FIELD_MEDIC，同时建立良好关系。, 救治P2以获取潜在盟友或信息，同时保持警惕。, P2处于CRITICAL状态，提供援助可以建立信任并符合我的私人目标。同时，通过提议联盟增加生存几率。] |

## REVIVE

| Model | State seen | Affordance recognized | Target selected | MOVE | Distribution | reasonSummary |
|---|---:|---:|---:|---:|---|---|
| M0_CONTROL | 5/5 | 0/5 | 5/5 | 0/5 | {REVIVE=5} | [救援P2符合联盟协议且有足够医疗包，可增强信任并获取潜在合作价值。, 救援P2可获得信任并可能获取资源，同时符合当前的私人目标和协作目标。, 救援P2符合私人目标FIELD_MEDIC，且其处于危险中，救援后可建立信任关系。, 救援P2可获得高信任度和潜在情报，且当前处于安全区域A，风险较低。, 救援P2可获得高信任度，且其私人目标可能与我相关，有利于长期合作。] |
| M1_STRONG | 5/5 | 0/5 | 5/5 | 0/5 | {REVIVE=5} | [救援盟友P2，保持联盟并增加生存机会。, 救援盟友P2以保持联盟和未来的合作机会，同时增加当前生存人数对抗其他敌人。, 救援倒地盟友以维持联盟和私人目标进度，同时考虑到当前安全区相对安全。, 救援盟友以保持联盟和获取潜在未来合作机会，同时符合我的人格设定。, 救援盟友P2以保持联盟并增加生存机会。] |

## Recognition Ladder

| Model | Scenario | STATE_SEEN | AFFORDANCE_RECOGNIZED | ACTION_SELECTED |
|---|---|---:|---:|---:|
| M0_CONTROL | HEAL | 0/5 | 0/5 | 0/5 |
| M0_CONTROL | SEARCH | 5/5 | 0/5 | 0/5 |
| M0_CONTROL | ATTACK | 5/5 | 0/5 | 1/5 |
| M0_CONTROL | REVIVE | 5/5 | 0/5 | 5/5 |
| M1_STRONG | HEAL | 0/5 | 0/5 | 0/5 |
| M1_STRONG | SEARCH | 5/5 | 5/5 | 5/5 |
| M1_STRONG | ATTACK | 5/5 | 0/5 | 0/5 |
| M1_STRONG | REVIVE | 5/5 | 0/5 | 5/5 |

### Interpretation

- qwen-max 明确证明模型能力影响 SEARCH 映射：从 turbo 的 0/5 恢复到 5/5，且每次都明确引用当前区域搜索机会。
- qwen-max 未恢复 HEAL：在 HP=20、MEDKIT=2 时仍选择 SEARCH 5/5，完全没有引用 HP 或医疗包。
- qwen-max 未恢复 ATTACK：它在 4/5 中选择 HEAL，并在摘要中表达“救助 P2”。但生产 HEAL 只能治疗自己，且 Actor 当时满血；这暴露了 Action 语义、候选效用和模型意图之间的架构性错配。
- 因而结果不是纯模型能力，也不是纯架构问题，而是 MIXED。

## MOVE Bias

| Model | MOVE | Reasons mentioning B/resource |
|---|---:|---:|
| M0_CONTROL | 13/20 | 15/20 |
| M1_STRONG | 0/20 | 11/20 |

## Pipeline

| Model | Parsed | Sanitized | Executed | Count |
|---|---|---|---|---:|
| M0_CONTROL | ATTACK | ATTACK | ATTACK | 1 |
| M0_CONTROL | MOVE | MOVE | MOVE | 13 |
| M0_CONTROL | REVIVE | REVIVE | REVIVE | 5 |
| M0_CONTROL | SEARCH | ATTACK | ATTACK | 1 |
| M1_STRONG | HEAL | HEAL | HEAL | 4 |
| M1_STRONG | REVIVE | REVIVE | REVIVE | 5 |
| M1_STRONG | SEARCH | ATTACK | ATTACK | 1 |
| M1_STRONG | SEARCH | SEARCH | SEARCH | 10 |

能力比较严格使用 Parsed/MODEL_SELECTED。

## Cost / Requests

- availability probe: 1 successful qwen-max request
- M0 decisions: 20
- M1 decisions: 20
- parser repair / fallback / errors: 0 / 0 / 0

## Test and Credential Validation

- Maven: 273 tests, 0 failures, 0 errors, 0 skipped
- credential_pattern_hits: 0（报告、JSONL、实验源码和测试）
- prompt pair mismatches: 0/20
- production Prompt / rules / sanitizer / validator / executor: 本轮均未修改

## Root Cause Classification

MIXED：强模型只恢复部分场景，模型能力与当前决策架构均有影响。

## Architecture Recommendation

优先 Candidate-based Decision Architecture，降低对模型推导合法动作的依赖，再决定默认模型等级。

生产 Prompt、规则、sanitizer、validator 和 executor 均未修改。
