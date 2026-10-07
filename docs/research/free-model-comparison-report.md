# 免费额度模型对比实测（2026-10-07）

## 模型选择与额度边界

依据用户控制台截图选择三个模型：qwen3.8-27b 剩余847.07K，qwen3.8-flash 剩余999.79K，kimi-k3 剩余945.61K Token，均显示已开启用完即停。调用华北2（北京）官方接口。额度耗尽由平台拒绝请求；本测试没有关闭保护开关，也没有调用截图中保护未开启的模型。

截图只反映当时的剩余额度。本测试没有账单查询权限，没有汇总整轮供应商实际Token用量或证明账单为零。

## 本轮选型建议

| 用途 | 优先模型 | 本轮证据 |
| --- | --- | --- |
| 继续验证社交与策略行为 | kimi-k3 | 专项能够选择情报买卖，整局资源交易1次、联盟建立5次；决策耗时中位数3.12秒 |
| 通用对照组 | qwen3.8-flash | 修复项目兼容性问题后66次整局决策全部合法，完成情报交易；决策耗时中位数3.17秒 |
| 保守型角色对照 | qwen3.8-27b | 66回合中32次防守、没有主动攻击；能够完成情报交易；决策耗时中位数3.71秒 |

这不是整体模型能力排名。Kimi的temperature必须使用1.0，两个Qwen使用0.4，且每个模型仅有一局对局。三款模型全部偏向保命：主动攻击分别为0、0、1次。应继续检查交战收益、信息交易价值和人格目标，不能只依靠更换模型提高战斗频率。

222次成功专项与整局决策全部来自模型，战术候选均成功解析。Kimi有1次社会候选与战术动作不兼容，被系统拒绝；这属于动作组合校验，不能与模型回退混同。

本次关闭思考模式的配置仅用于测试客户端，尚未改变应用的生产默认模式。复用这些模型时需要明确配置思考模式，不能直接把本次耗时当作生产默认耗时。

## 方法

- 各模型使用相同生产Prompt、候选动作与引擎，统一关闭思考模式。
- Qwen temperature=0.4；Kimi接口不支持0.4，改用1.0。maxTokens=4096，单次超时90秒，SDK自动重试0；结构化解析仍可能发起一次格式修复调用。
- 8类合成专项场景各1次；每个模型1局6人、最多12轮、种子3200000的完整对局。
- 模型客户端使用测试工厂设置模式参数，复用生产DNS校验和传输；不改变生产默认模型模式。
- 响应耗时是一次结构化决策完成耗时，含可能的格式修复，不含主动节流等待。
- 每回合检查状态不变量，系统回退不会记为成功的真实模型决策。

## 整局结果

| 模型 | 决策数 | 模型决策数 | 隐藏次数 | 攻击次数 | 异常 | 决策耗时中位数 |
| --- | ---: | ---: | ---: | ---: | --- | ---: |
| qwen3.8-27b | 66 | 66 | 0 | 0 | False | 3.71秒 |
| qwen3.8-flash | 66 | 66 | 17 | 0 | False | 3.17秒 |
| kimi-k3 | 66 | 66 | 1 | 1 | False | 3.12秒 |

## 专项场景选择

| 场景 | qwen3.8-27b | qwen3.8-flash | kimi-k3 |
| --- | --- | --- | --- |
| REVIVE_ALLY | REVIVE / NONE | REVIVE / NONE | REVIVE / NONE |
| REVIVE_NO_MEDKIT | MOVE / NONE | SKIP / NONE | SKIP / NONE |
| SELL_INFORMATION | MOVE / NONE | SEARCH / NONE | SEARCH / PROPOSE_INFORMATION_TRADE |
| BUY_INFORMATION | MOVE / PROPOSE_ALLIANCE | MOVE / PROPOSE_ALLIANCE | HIDE / ACCEPT_INFORMATION_TRADE |
| BUY_ALREADY_KNOWN | MOVE / NONE | ATTACK / NONE | SEARCH / PROPOSE_ALLIANCE |
| BUY_NO_PAYMENT | MOVE / NONE | GUARD / PROPOSE_ALLIANCE | SEARCH / PROPOSE_ALLIANCE |
| FULFILL_MEDKIT | SKIP / FULFILL_PROMISE | SKIP / FULFILL_PROMISE | MOVE / NONE |
| FULFILL_NO_MEDKIT | MOVE / NONE | GUARD / PROPOSE_ALLIANCE | SEARCH / PROPOSE_ALLIANCE |

## 详细计数

### qwen3.8-27b

完成结构化模型决策：74次。

```json
{
  "seed": 3200000,
  "players": 6,
  "stopRound": 12,
  "rounds": 12,
  "termination": "MAX_ROUNDS",
  "abnormal": false,
  "decisions": 66,
  "actions": {
    "CAPTURE": 1,
    "GUARD": 32,
    "MOVE": 18,
    "SEARCH": 5,
    "SKIP": 10
  },
  "socialActions": {
    "ACCEPT_ALLIANCE": 2,
    "ACCEPT_INFORMATION_TRADE": 1,
    "CONTRIBUTE_OBJECTIVE": 4,
    "LEAVE_ALLIANCE": 1,
    "PROPOSE_ALLIANCE": 4,
    "PROPOSE_INFORMATION_TRADE": 1,
    "REJECT_ALLIANCE": 1,
    "SHARE_RUMOR": 2,
    "null": 50
  },
  "sources": {
    "MODEL": 66
  },
  "tacticalResolutions": {
    "SELECTED": 66
  },
  "socialResolutions": {
    "SELECTED": 66
  },
  "events": {
    "AI_DECISION": 66,
    "BATTLE_ACTION_EXECUTED": 66,
    "BATTLE_ALLIANCE_FORMED": 2,
    "BATTLE_ALLIANCE_LEFT": 1,
    "BATTLE_ALLIANCE_PROPOSED": 4,
    "BATTLE_ALLIANCE_REJECTED": 1,
    "BATTLE_FINISHED": 1,
    "BATTLE_HIDDEN_GOAL_COMPLETED": 2,
    "BATTLE_INFORMATION_TRADE_COMPLETED": 1,
    "BATTLE_INFORMATION_TRADE_PROPOSED": 1,
    "BATTLE_LOCAL_MESSAGE": 10,
    "BATTLE_OBJECTIVE_COMPLETED": 1,
    "BATTLE_OBJECTIVE_CONTRIBUTED": 4,
    "BATTLE_PLAYER_EXPOSED": 10,
    "BATTLE_PRIVATE_MESSAGE": 29,
    "BATTLE_PUBLIC_MESSAGE": 1,
    "BATTLE_RELATIONSHIP_CHANGED": 4,
    "BATTLE_RUMOR_SHARED": 2,
    "BATTLE_SUPPLY_CAPTURED": 1,
    "BATTLE_SUPPLY_CONTESTED": 1,
    "BATTLE_SUPPLY_DROP": 3,
    "BATTLE_ZONE_DAMAGE": 7,
    "BATTLE_ZONE_UPDATED": 4
  }
}
```

### qwen3.8-flash

完成结构化模型决策：74次。

```json
{
  "seed": 3200000,
  "players": 6,
  "stopRound": 12,
  "rounds": 12,
  "termination": "MAX_ROUNDS",
  "abnormal": false,
  "failure": null,
  "decisions": 66,
  "actions": {
    "CAPTURE": 2,
    "GUARD": 24,
    "HIDE": 17,
    "MOVE": 9,
    "SEARCH": 6,
    "SKIP": 8
  },
  "socialActions": {
    "ACCEPT_ALLIANCE": 2,
    "ACCEPT_INFORMATION_TRADE": 1,
    "CONTRIBUTE_OBJECTIVE": 4,
    "PROPOSE_ALLIANCE": 3,
    "PROPOSE_INFORMATION_TRADE": 1,
    "REJECT_ALLIANCE": 1,
    "SHARE_RUMOR": 1,
    "null": 53
  },
  "sources": {
    "MODEL": 66
  },
  "tacticalResolutions": {
    "SELECTED": 66
  },
  "socialResolutions": {
    "SELECTED": 66
  },
  "events": {
    "AI_DECISION": 66,
    "BATTLE_ACTION_EXECUTED": 66,
    "BATTLE_ALLIANCE_FORMED": 2,
    "BATTLE_ALLIANCE_PROPOSED": 3,
    "BATTLE_ALLIANCE_REJECTED": 1,
    "BATTLE_FINISHED": 1,
    "BATTLE_HIDDEN_GOAL_COMPLETED": 2,
    "BATTLE_INFORMATION_TRADE_COMPLETED": 1,
    "BATTLE_INFORMATION_TRADE_PROPOSED": 1,
    "BATTLE_LOCAL_MESSAGE": 8,
    "BATTLE_OBJECTIVE_COMPLETED": 1,
    "BATTLE_OBJECTIVE_CONTRIBUTED": 4,
    "BATTLE_PLAYER_EXPOSED": 10,
    "BATTLE_PRIVATE_MESSAGE": 1,
    "BATTLE_RELATIONSHIP_CHANGED": 4,
    "BATTLE_RUMOR_SHARED": 1,
    "BATTLE_SUPPLY_CAPTURED": 2,
    "BATTLE_SUPPLY_CONTESTED": 2,
    "BATTLE_SUPPLY_DROP": 3,
    "BATTLE_ZONE_DAMAGE": 5,
    "BATTLE_ZONE_UPDATED": 4
  }
}
```

### kimi-k3

完成结构化模型决策：74次。

```json
{
  "seed": 3200000,
  "players": 6,
  "stopRound": 12,
  "rounds": 12,
  "termination": "MAX_ROUNDS",
  "abnormal": false,
  "decisions": 66,
  "actions": {
    "ATTACK": 1,
    "CAPTURE": 2,
    "GUARD": 21,
    "HIDE": 1,
    "MOVE": 27,
    "REST": 1,
    "SEARCH": 12,
    "SKIP": 1
  },
  "socialActions": {
    "ACCEPT_ALLIANCE": 5,
    "ACCEPT_TRADE": 1,
    "CONTRIBUTE_OBJECTIVE": 4,
    "LEAVE_ALLIANCE": 1,
    "PROPOSE_ALLIANCE": 5,
    "PROPOSE_TRADE": 1,
    "null": 49
  },
  "sources": {
    "MODEL": 66
  },
  "tacticalResolutions": {
    "SELECTED": 66
  },
  "socialResolutions": {
    "INCOMPATIBLE_TACTICAL_SOCIAL_PAIR": 1,
    "SELECTED": 65
  },
  "events": {
    "AI_DECISION": 66,
    "BATTLE_ACTION_EXECUTED": 66,
    "BATTLE_ALLIANCE_FORMED": 5,
    "BATTLE_ALLIANCE_LEFT": 1,
    "BATTLE_ALLIANCE_PROPOSED": 5,
    "BATTLE_FINISHED": 1,
    "BATTLE_HIDDEN_GOAL_COMPLETED": 1,
    "BATTLE_LOCAL_MESSAGE": 18,
    "BATTLE_OBJECTIVE_COMPLETED": 1,
    "BATTLE_OBJECTIVE_CONTRIBUTED": 4,
    "BATTLE_PLAYER_EXPOSED": 9,
    "BATTLE_PRIVATE_MESSAGE": 7,
    "BATTLE_PUBLIC_MESSAGE": 17,
    "BATTLE_RELATIONSHIP_CHANGED": 11,
    "BATTLE_SUPPLY_CAPTURED": 2,
    "BATTLE_SUPPLY_CONTESTED": 2,
    "BATTLE_SUPPLY_DROP": 3,
    "BATTLE_TRADE_COMPLETED": 1,
    "BATTLE_TRADE_PROPOSED": 1,
    "BATTLE_ZONE_DAMAGE": 12,
    "BATTLE_ZONE_UPDATED": 4
  }
}
```

## 补充与限制

- Flash前两次整局尝试在首个回合触发confidence缺省导致的Java空值拆箱；失败样本保存在target，修复项目兼容性缺陷后续测，不归咎于供应商鉴权或网络。
- 接口接受Kimi enable_thinking=false；连通性响应显示reasoning_tokens=0。该观察适用于本次调用接口，不能据此推断所有Kimi接入方式。
- 联通性检查：三模型默认思考模式各1次，合计372 Token；另一次Kimi关闭思考的验证使用29 Token。
- qwen3.8-27b 的默认深度思考预试验首个专项样本耗时64.93秒且成功救援；随后停止该配置，保留诊断样本，不混入关闭思考的比较。
- 之前qwen-plus已完成24个专项样本和1局12轮对局（62次整局决策），其轮次和参数与本轮不同，不纳入同表排名。
- 每个模型8个专项样本、1局完整对局不能证明整体模型能力或胜率平衡。本轮比较的是当前游戏Prompt下的非思考模式适配性。
- 本报告不包含密钥、模型原始回答、账号或逐决策Prompt。详细结果位于Git忽略的target目录。

## 复现

设置 ARENA_REAL_MODEL_TEST=true、ARENA_REAL_MODEL_QUICK=true、ARENA_REAL_MODEL_NAME 以及 QWEN_API_KEY；运行 BattleRealModelBehaviorTest。

## 官方资料

- [免费额度与价格](https://help.aliyun.com/zh/model-studio/model-pricing)
- [OpenAI兼容接口与模式参数](https://help.aliyun.com/en/model-studio/qwen-api-via-openai-chat-completions)
- [Kimi接口](https://help.aliyun.com/zh/model-studio/kimi-api)

## 结构化格式修复

成功运行日志中记录的格式修复成功次数：qwen3.8-27b：0；qwen3.8-flash：4；kimi-k3：0。这些请求增加响应耗时和Token消耗，不计为本地策略回退。
