# 参与贡献

感谢你改进 Agent Arena。提交代码前请先创建 Issue，说明问题、预期行为和实现范围；小型修复可以直接提交 Pull Request。

## 开发环境

- Java 17
- Maven 3.9 或项目自带的 Maven Wrapper
- MySQL 8.4（数据库迁移集成测试需要）
- Node.js 22（仅用于 Playwright 测试）

本地启动和测试命令见 `README.md`。不要提交 `.env`、`.agent-arena-local.properties`、日志、浏览器测试产物或真实模型响应。

## 提交要求

1. 变更保持聚焦，并说明行为变化和兼容性影响。
2. 新增或修改业务规则时补充对应单元测试。
3. 改动用户流程时运行 Playwright 功能测试。
4. 数据库结构只能通过新的 Flyway migration 演进，不要修改已发布 migration。
5. 不要在日志、fixture、截图或研究报告中写入 API Key、访问令牌、真实用户数据或未经授权的模型输出。
6. 新增第三方代码或素材时，在 `THIRD_PARTY_NOTICES.md` 中记录来源、版本和许可证。

## 验证

```bash
./mvnw test
npm ci
npm run test:e2e:functional
```

需要验证真实 MySQL migration 时，按 `README.md` 配置 `ci` profile 后运行 `./mvnw verify`。

提交 Pull Request 即表示你有权提交相关内容，并同意贡献按本项目的 MIT License 分发。
