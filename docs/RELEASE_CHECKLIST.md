# 发布清单

创建 Release 或把仓库推荐给外部使用者前，维护者应确认：

- [ ] GitHub 默认分支为 `main`，且完整功能分支已经合并；
- [ ] 从全新 clone 按 `README.md` 的 Docker Compose 步骤可以启动；
- [ ] `./mvnw verify` 和 `npm run test:e2e:functional` 通过；
- [ ] README 中的功能、命令和文件在默认分支真实存在；
- [ ] `LICENSE`、`THIRD_PARTY_NOTICES.md` 与新增素材许可证完整；
- [ ] `git ls-files` 不包含 `.env`、本地配置、日志、测试产物或原始模型 trace；
- [ ] 对提交历史执行凭据扫描，发现真实凭据时先吊销再清理历史；
- [ ] 创建带版本号的 Git tag，并在 Release Notes 中写明 migration 与兼容性变化；
- [ ] 镜像 digest 和固定的 GitHub Actions 提交仍来自可信上游；
- [ ] `SECURITY.md` 和 `DATA_AND_PRIVACY.md` 与当前行为一致。

当前仓库以单实例自托管为发布边界。二次开发者增加公网、多租户或横向扩展能力时，应维护自己的安全与运维清单。
