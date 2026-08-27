# LLM Action Pipeline 全链路审计与控制变量实验

## 实验配置

- provider: QWEN
- model: qwen-turbo
- repeats per scenario: 5
- total calls: 60
- temperature: 0.4; maxTokens: 900; retries: 0
- API Key: 仅进程环境变量，未写入文件

## 真实调用链

`BattleAgentContext -> BattleAgentContextBuilder -> ModelGateway -> raw response -> structured parser/repair -> BattleAiDecision -> sanitizeAction -> executeWithDecision -> BattleRoyaleGameEngine.execute -> EngineEvent`

## Scenario Results

| Scenario | Parsed | Sanitized | Executed | Fallback | Repair |
|---|---:|---:|---:|---:|---:|
| ATTACK_CLEAR_OPPORTUNITY | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| SEARCH_CLEAR_OPPORTUNITY | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| HEAL_CLEAR_OPPORTUNITY | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| REVIVE_CLEAR_OPPORTUNITY | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| ACCEPT_ALLIANCE | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| FULFILL_PROMISE | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| COOPERATIVE_CONTRIBUTION | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| INFORMATION_BROKER | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| ATTACK_NEGATIVE | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| HEAL_NEGATIVE | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| REVIVE_NEGATIVE | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |
| PROMISE_NEGATIVE | {MOVE=5} | {MOVE=5} | {MOVE=5} | 0 | 0 |

## Transformation Matrix

| Model/Parsed -> Executed | Count |
|---|---:|
| MOVE -> MOVE | 60 |

## Pipeline Rates

- action preservation rate: 100.00%
- sanitize change rate: 0.00%
- fallback rate: 0.00%
- parser repair rate: 0.00%
- validation failures: 0 observed; execution events: production engine events captured

## Schema / Prompt 审计

- Tactical enum includes MOVE, SEARCH, CAPTURE, REST, ATTACK, HIDE, HEAL, GUARD, REVIVE, EXECUTE, SKIP; legacy TRADE remains enum but sanitize maps it to SEARCH.
- Social schema is nested in `BattleSocialDecision`; current prompt example couples `PROPOSE_ALLIANCE` with `PRIVATE`, so `EXAMPLE_BIAS_RISK` is present.
- `PROPOSE_ALLIANCE + PRIVATE` is represented as two independent model fields; production code does not automatically create PRIVATE.

## Root Cause Classification

- Confirmed Context/Schema/Parser/Sanitize/Validation/Execution bug: none observed by this harness.
- Any legal opportunity not selected while prompt/context/schema/sanitize remain intact is classified as `PROMPT_STRATEGY` or `MODEL_CAPABILITY`, not as a gameplay bug.

## Next Experiment

Use one-variable prompt ablations: remove the coupled JSON example, then separately add an explicit legal-action priority list; keep model, temperature, state, seed and repeats fixed.
