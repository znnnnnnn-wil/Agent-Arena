# Agent Arena

Agent Arena 是一个单实例自托管的 AI 对局平台。Spring Boot 同时提供 Web 前端与 REST/SSE API，MySQL 保存对局、事件和状态快照；用户可以组合模型与 Agent 人格，创建、推进、观战和复盘 AI 对局。

当前支持：

- 狼人杀：八个席位、隐藏身份、夜间技能、发言与投票；
- 简化大逃杀：区域移动、战斗、资源、联盟、承诺、交易与协作目标；
- 图灵潜伏局：一名真人与五名 AI，包含问答、怀疑、质询、辩护和投票阶段。

> 本项目是供学习、实验和二次开发使用的自托管参考实现，不是经过安全审计的公网多租户服务。公网运营所需的分布式限流、队列、计费防护和风控由部署者按自己的场景实现。

## 主要能力

- OpenAI、DeepSeek、Qwen 等供应商及 OpenAI-compatible 模型配置；
- 用户级模型配置、Agent 人格和阵容模板；
- 完整事件与脱敏公开事件分离存储；
- REST 回放、比赛投影、精彩片段和 SSE 实时观战；
- API Key AES-GCM 加密、访客令牌摘要存储和资源归属校验；
- Flyway 数据库迁移、Docker Compose 启动、JUnit 与 Playwright 测试。

## 项目结构

```text
src/main/java/com/zn/agentarena
├─ common/          通用响应、异常、枚举和工具
├─ config/          安全、运行参数和基础设施配置
├─ dao/             数据库实体与 Mapper
└─ modules/         按业务能力拆分的垂直模块
   ├─ agent/        Agent 人格
   ├─ game/         对局编排、快照与查询
   ├─ lineup/       阵容模板
   ├─ model/        模型配置、端点策略和调用网关
   ├─ replay/       回放与比赛投影
   ├─ spectator/    事件记录和 SSE
   ├─ battle/       大逃杀规则与 AI 决策
   ├─ turing/       图灵潜伏局规则与 AI 决策
   ├─ werewolf/     狼人杀规则与 AI 决策
   └─ user/         游客与本地账号
```

正式前端位于 `src/main/resources/static`，由 Spring Boot 直接托管。Node.js 只用于浏览器自动化测试。

## 环境要求

- Docker 与 Docker Compose；或 Java 17、Maven 3.9 和 MySQL 8.4；
- Node.js 22，仅在运行 Playwright 测试时需要；
- 至少 32 个随机字符的 `API_KEY_ENCRYPTION_SECRET`。

## 快速启动

1. 复制 `.env.example` 为 `.env`，替换其中所有示例密码和加密密钥。
2. 启动应用与 MySQL：

```bash
docker compose up --build
```

3. 打开 `http://localhost:8080`。Flyway 会自动创建或升级数据库结构。

停止容器但保留数据库：

```bash
docker compose down
```

`docker compose down -v` 会删除 MySQL volume 及其中全部数据，请只在确认不需要保留数据时使用。

## 本地开发

本地直接运行 Java 时，需要先准备 MySQL。复制 `.agent-arena-local.properties.example` 为 `.agent-arena-local.properties`，填写数据库账号、密码和加密密钥；该文件已被 Git 忽略。也可以使用 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`API_KEY_ENCRYPTION_SECRET` 和 `SPRING_PROFILES_ACTIVE` 环境变量。

```bash
./mvnw spring-boot:run
```

Windows PowerShell 使用：

```powershell
.\mvnw.cmd spring-boot:run
```

`MODEL_ALLOWED_HOSTS` 可配置逗号分隔的自托管 OpenAI-compatible 精确主机名；默认不允许任意自定义主机。`MODEL_MAX_CONCURRENT_CALLS` 控制单实例同时进行的模型调用数，默认值为 8。

## 身份与安全边界

- `POST /api/users/visitor` 创建游客身份，原始令牌只返回给浏览器，数据库仅保存 SHA-256 摘要；
- 私有 `/api/**` 必须携带 `X-Visitor-Token`，业务 userId 只从认证主体读取；
- 匿名访问仅限健康检查和 `/api/public/games/**`；
- 公开回放只使用 `public_payload`，运行中的隐藏身份和私有技能不会进入公开响应；
- 自定义模型地址必须是公开 HTTPS 地址且主机在服务端白名单中，本机、私网、链路本地和重定向会被拒绝；
- 模型 API Key 只在调用期间以明文存在于进程内存，数据库保存 AES-GCM 密文。

详细安全范围与漏洞报告方式见 [SECURITY.md](SECURITY.md)，持久化内容、密钥管理和数据生命周期见 [DATA_AND_PRIVACY.md](DATA_AND_PRIVACY.md)。

## 验证

```bash
./mvnw test
node --check src/main/resources/static/app.js
npm ci
npx playwright install chromium
npm run test:e2e:functional
npm run test:e2e:visual
```

`./mvnw verify` 还会执行 `DatabaseMigrationIT`，它需要 `ci` profile 指向一个真实 MySQL。GitHub Actions 已配置 MySQL 8.4 并执行完整后端验证和 Playwright 功能流程。

视觉测试分别维护 desktop 与 mobile Chromium 基线，并在 Windows CI 上比较。页面自带 Noto Sans SC 字体；视觉配置使用 SwiftShader，并固定为云端 runner 的低功耗档位，避免系统字体、显卡和 CI 核数改变布局或阴影。大逃杀在该档位仍加载外部角色和岛屿素材，功能测试也覆盖舞台加载与更新。界面有意变化后运行 `npm run test:e2e:update` 更新基线，再运行 `npm run test:e2e:visual` 审核。功能测试按配置在本地使用 4 个 worker、CI 使用 2 个 worker。

对局列表翻页、回放游标与实时重连行为见 [读取接口说明](docs/API_READS.md)。

## 运行约束

- 单次模型调用默认超时 25 秒，默认不自动重试；
- 同一 JVM 内同一场对局串行推进，模型调用发生在数据库事务外；
- 事务提交后才发布 SSE，断开连接由心跳清理；
- 限流、对局锁和 SSE 订阅表均为单实例内存实现；
- 横向扩容和公网多租户能力不属于当前仓库的实现范围。

## 研究报告

基于合成场景的聚合实验报告位于 [docs/research](docs/research/README.md)。公开源码不包含逐决策 trace，因为其包含完整 Prompt、内部状态和模型原始输出。运行实验生成的根目录 `*-traces.jsonl` 与 `*-report.md` 默认被 Git 忽略。

## 参与和许可

- 贡献流程：[CONTRIBUTING.md](CONTRIBUTING.md)
- 第三方代码与素材：[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- 项目许可证：[MIT License](LICENSE)
