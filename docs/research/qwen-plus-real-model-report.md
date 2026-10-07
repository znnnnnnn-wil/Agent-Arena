# qwen-plus 真实模型实测（2026-10-07）

## 测试条件

- 供应商：阿里云百炼；模型：qwen-plus。使用项目生产 Prompt、模型网关、候选解析和行动引擎。
- temperature=0.4，maxTokens=900，超时45秒，SDK重试0。结构化响应解析失败时网关仍可能发起一次格式修复调用。
- 专项：8类合成场景，每类3次；种子3100000起，每类间隔100。
- 整局：6人、最多12轮、种子3200000；逐回合检查状态不变量。
- 密钥只在测试进程环境中提供，公开报告不含密钥、账号信息、原始响应或逐决策理由。
- 首次Java尝试被执行环境拒绝网络连接，0个成功样本；获得联网权限后重新运行。
- 连通性检查另有1次极短请求，13个Token。整轮测试未汇总供应商Token用量与账单。

## 专项结果

| 场景 | 样本 | 战术选择 | 社会选择 | 实际特殊事件 |
| --- | ---: | --- | --- | --- |
| 盟友倒地，有医疗包 | 3 | REVIVE × 3 | NONE × 3 | BATTLE_PLAYER_REVIVED × 3 |
| 盟友倒地，无医疗包 | 3 | EXECUTE × 2、HIDE × 1 | LEAVE_ALLIANCE × 2、NONE × 1 | BATTLE_PLAYER_EXECUTED × 2 |
| 可出售情报 | 3 | HIDE × 3 | NONE × 3 | 无 |
| 可购买情报且能付款 | 3 | HIDE × 3 | PROPOSE_ALLIANCE × 1、NONE × 2 | 无 |
| 已知情报不应重复购买 | 3 | HIDE × 3 | PROPOSE_ALLIANCE × 1、NONE × 2 | 无 |
| 无付款资源 | 3 | HIDE × 3 | PROPOSE_ALLIANCE × 1、NONE × 2 | 无 |
| 有医疗包可履约 | 3 | HIDE × 3 | PROPOSE_ALLIANCE × 1、FULFILL_PROMISE × 2 | BATTLE_PROMISE_FULFILLED × 2 |
| 无医疗包不能履约 | 3 | HIDE × 2、MOVE × 1 | PROPOSE_ALLIANCE × 1、NONE × 2 | 无 |

## 完整对局

- 终止原因：MAX_ROUNDS；结束轮次：12；决策数：62；异常：False。
- 决策来源：{"MODEL": 62}。
- 战术候选解析：{"SELECTED": 62}。
- 社会候选解析：{"INCOMPATIBLE_TACTICAL_SOCIAL_PAIR": 1, "SELECTED": 61}。
- 战术行动：{"ATTACK": 7, "CAPTURE": 2, "EXECUTE": 2, "HIDE": 40, "MOVE": 6, "SEARCH": 4, "SKIP": 1}。
- 社会行动：{"ACCEPT_ALLIANCE": 2, "CONTRIBUTE_OBJECTIVE": 3, "LEAVE_ALLIANCE": 1, "PROPOSE_ALLIANCE": 2, "PROPOSE_PROMISE": 1, "null": 53}。
- 特殊事件：{"AI_DECISION": 62, "BATTLE_ACTION_EXECUTED": 62, "BATTLE_ALLIANCE_FORMED": 2, "BATTLE_ALLIANCE_LEFT": 1, "BATTLE_ALLIANCE_PROPOSED": 2, "BATTLE_FINISHED": 1, "BATTLE_LOCAL_MESSAGE": 4, "BATTLE_OBJECTIVE_CONTRIBUTED": 3, "BATTLE_OBJECTIVE_EXPIRED": 1, "BATTLE_PLAYER_DOWNED": 3, "BATTLE_PLAYER_ELIMINATED": 2, "BATTLE_PLAYER_EXECUTED": 2, "BATTLE_PLAYER_EXPOSED": 5, "BATTLE_PROMISE_EXPIRED": 1, "BATTLE_PROMISE_PROPOSED": 1, "BATTLE_RELATIONSHIP_CHANGED": 11, "BATTLE_SUPPLY_CAPTURED": 2, "BATTLE_SUPPLY_CONTESTED": 2, "BATTLE_SUPPLY_DROP": 3, "BATTLE_ZONE_DAMAGE": 2, "BATTLE_ZONE_UPDATED": 4}。
- 完成的结构化模型决策调用：86（不含连通性请求；不等于底层HTTP调用次数）。

## 结果边界

24个专项样本和1局完整对局用于发现行为问题，不能作为胜率平衡、不同模型优劣或稳定行为频率的证据。成功选择候选与实际行动效果应分别解读；本轮不通过强制AI选择某动作来提高展示频率。

## 复现

运行 BattleRealModelBehaviorTest，设置 ARENA_REAL_MODEL_TEST=true、QWEN_API_KEY 和 ARENA_REAL_MODEL_NAME=qwen-plus。详细合成场景结果保存在被Git忽略的 target/real-model-behavior-results.json。

模型和接口依据：[阿里云官方调用说明](https://www.alibabacloud.com/help/en/model-studio/model-calling-in-sub-workspace)。
