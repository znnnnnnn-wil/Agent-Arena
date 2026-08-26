# Agent Arena

Agent Arena 是一个由 Spring Boot 托管前端与 API 的 AI 对局平台，当前支持狼人杀和简化大逃杀。对局状态、事件与快照写入 MySQL，公开对局可通过脱敏 REST API 和 SSE 实时观战。

## 项目结构

```text
src/main/java/com/zn/agentarena
├─ common/          通用响应、异常、枚举和工具
├─ config/          安全、运行参数和基础设施配置
├─ dao/             数据库实体与 Mapper
└─ modules/         按业务能力拆分的垂直模块
   ├─ game/         对局编排、快照与查询
   ├─ model/        模型配置、端点策略和调用网关
   ├─ werewolf/     狼人杀领域状态、引擎和 AI 决策
   ├─ battle/       大逃杀领域状态、引擎和 AI 决策
   ├─ replay/       回放 DTO
   ├─ spectator/    事件记录和 SSE
   └─ user/         游客身份
```

`modules/*/domain` 放的是业务层对象，例如模型运行参数、狼人杀状态和大逃杀状态；它们表达规则和行为，不等同于数据库表。数据库映射统一放在 `dao/entity`。最外层 `agentarena` 只是 Java 基础包，因此看起来会单独包住 `common`、`dao`、`modules` 等目录。

前端只有一个正式入口：`src/main/resources/static`。它由 Spring Boot 直接提供，不需要单独安装 Node 依赖。

## 快速启动

1. 复制 `.env.example` 为 `.env`，替换其中所有密码和加密密钥。
2. 启动应用与 MySQL：

```bash
docker compose up --build
```

3. 打开 `http://localhost:8080`。Flyway 会自动创建或升级数据库结构。

本地直接运行 Java 时，需要先准备 MySQL。可以复制 `.agent-arena-local.properties.example` 为 `.agent-arena-local.properties`，填写数据库账号、密码和至少 32 个随机字符的 `API_KEY_ENCRYPTION_SECRET`；该本地文件已被 Git 忽略。也可以继续通过环境变量提供这些配置。可选变量包括 `MYSQL_URL`、`MYSQL_USERNAME`、`MODEL_ALLOWED_HOSTS` 和 `SPRING_PROFILES_ACTIVE`。

```bash
mvn spring-boot:run
```

## 身份与接口边界

- `POST /api/users/visitor` 创建游客身份，返回的原始令牌只交给浏览器；数据库只保存 SHA-256 摘要。
- 私有 `/api/**` 接口必须携带 `X-Visitor-Token`，业务 userId 只从认证主体读取，不能由请求体或查询参数伪造。
- 匿名访问仅限健康检查和 `/api/public/games/**`。公开回放只返回 `public_payload`；未结束的狼人身份、模型配置和私有技能不会暴露。
- 自定义模型地址必须是解析到公网的 HTTPS 地址，并且主机名由运维通过逗号分隔的 `MODEL_ALLOWED_HOSTS` 显式放行；重定向已禁用。本机、私网、链路本地和用户信息 URL 会被拒绝。Ollama 仅使用服务端管理的本机默认地址。
- 模型配置采用软删除，避免已被对局引用时触发外键错误；API Key 使用服务端密钥加密，日志不会输出请求、响应或密钥。

## 验证

```bash
mvn test
node --check src/main/resources/static/app.js
npm ci
npx playwright install chromium
npm run test:e2e:functional
npm run test:e2e:visual
```

`mvn verify` 还会执行 `DatabaseMigrationIT`，它需要 `ci` profile 指向一个真实 MySQL。GitHub Actions 已配置 MySQL 8.4 服务并执行完整验证。

Playwright 功能测试使用固定 Mock API 覆盖“模型与人格组合创建对局”和“创建 Agent 人格”等用户流程，不依赖本地 MySQL。视觉测试分别维护 `desktop-chromium` 与 `mobile-chromium` 基线；界面有意变更后运行 `npm run test:e2e:update` 更新，再用 `npm run test:e2e:visual` 做一次只读比对。CI 默认执行跨平台稳定的功能流程，视觉基线由开发环境显式审核后提交。

## 运行说明

- 单次模型调用默认超时 25 秒，前端请求超时 35 秒，默认不自动重试；每实例默认最多同时执行 8 次模型调用，避免慢供应商耗尽请求线程或产生不可控的重复费用。
- 单用户的创建、连接测试和回合推进均有限流；HTTP 只暴露单步推进，自动播放在浏览器端最多 200 步，避免一个请求长时间占用线程。
- SSE 默认限制每场 100 个、每实例共 1000 个观战连接，并通过心跳清理断开的浏览器。
- 当前正式接口只创建和推进 AI 席位；未完成的真人行动与服务端批量运行入口没有对外暴露。
- 同一 JVM 内同一场对局串行推进；每次行动从最新数据库快照恢复，模型调用发生在数据库事务外，事务提交后才发布 SSE。
- 当前限流和对局锁是单实例实现。横向扩容前应替换为 Redis/数据库分布式限流与锁，并将模型推进迁移到有界任务队列。
