# 研究报告

本目录保存 Agent Arena 使用合成场景生成的聚合实验报告，用于解释决策架构、Prompt 消融和仿真结果。报告不代表模型供应商的官方基准，也不应用来推断真实用户行为。

## 阅读入口

- [大逃杀仿真报告](simulation-report.md)
- [行为诊断汇总](behavior-diagnostic-report.md)
- [候选动作决策架构](candidate-decision-architecture-report.md)
- [Prompt 消融实验](prompt-ablation-report.md)
- [Prompt 因子实验](prompt-factorial-report.md)
- [决策目标 A/B 实验](decision-objective-ab-report.md)
- [动作可供性 A/B 实验](action-affordance-ab-report.md)
- [目标与可供性交互实验](objective-affordance-interaction-report.md)
- [模型控制对照](model-control-comparison-report.md)
- [真实模型聚合样本](llm-sample-report.md)

## 数据边界

公开仓库只保留聚合报告。逐决策 trace 包含完整 Prompt、内部状态和模型原始输出，即使测试场景是合成的，也不作为源码发布。运行实验时产生的根目录 `*-traces.jsonl` 和 `*-report.md` 已被 Git 忽略。

如需复现实验，请从相应 `*Main` 类运行，并自行保存输出。提交新的聚合报告前必须确认：

1. 数据完全来自合成场景；
2. 不含 API Key、访问令牌、账号数据或服务端地址；
3. 模型输出的再分发符合对应供应商条款；
4. 报告能说明模型、参数、样本数和异常样本处理方式。
