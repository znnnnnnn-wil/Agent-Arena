# 60 秒交互演示

入口为 `src/main/resources/static/demo.html`。它复用项目的 `arena-3d.js` 和本地素材，不创建游客、不读取私人对局、不调用模型，也不依赖 Java 服务或数据库。首页主按钮和 README 均提供入口。可通过 `node e2e/static-server.cjs` 单独预览。

## 内容来源

`src/main/resources/static/assets/demo/decisions.json` 摘录自开发工作区已有的 `target/battle-real-model-acceptance-2026-10-07.json`，模型为 qwen-max。仅摘录三个场景各自的第一条记录：

| 场景 | Seed | 战术行动 | 社会行动 |
| --- | --- | --- | --- |
| REVIVE_ALLY | 3100000 | REVIVE | 无 |
| REVIVE_NO_MEDKIT | 3100100 | GUARD | 无 |
| FULFILL_MEDKIT | 3100600 | HIDE | FULFILL_PROMISE |

公开节选只保留场景标识、seed、行动、目标、模型摘要及产生的事件类型。原始报告属于本地验收产物，不作为页面运行依赖。逐项核对过节选与源记录中的字段；未包含 API Key、配置、完整 Prompt 或模型原始输出。

三个场景是独立的受控测试，不是同一场完整比赛，也不构成对模型整体表现的评分。每段 20 秒，依次展示局势（前 6 秒）、原始决策摘要（第 6 秒起）、记录中的结果（第 13 秒起）。地图、角色、位置、生命值、护甲及动画为说明机制的场景重建，页面已明确标注。

## 使用与维护

演示舞台采用偏写实生存风格：`survival-world.js` 提供本地生成的林地与关节角色，`survival-stage.js` 将动画与演示进度同步。救援、防御和隐蔽兑现承诺分别对应三个独立场景；无医疗包片段不会播放队友起身。暂停、进度回退和减少动画偏好均由同一时间轴驱动。该舞台只应用于演示；正式对局的舞台继续使用现有规则与素材。

- `demo.html?autoplay=1`：点击后自动播放。普通入口尊重系统减少动画偏好，减少动画时初始暂停。
- `demo.html#t=26`：从第 26 秒打开；分享按钮复制当前时间位置。剪贴板不可用时显示可选中的完整链接。
- 切换到后台时暂停；演示结束后显示创建对局入口。拖动进度和章节跳转不写入游戏会话或账户数据。
- WebGL 不可用时保留五区域地图和全部决策解说。
- 后续替换内容时应使用可核验的公开记录，同时更新数据、说明、场景文案及首页截图。
- 静态服务器仅用于演示；页面中的创建对局入口需要正常运行的后端。需要外部访问时，由部署者将静态文件或完整应用部署至自己的域名。

验证命令：`node --check src/main/resources/static/demo.js`、`npx playwright test e2e/demo.spec.js`。演示只读取同源静态资源。

可选运行 `npm run demo:record` 导出 WebM 演示视频和桌面、手机截图到 `target/demo/`。需已安装项目的 Playwright 浏览器；可通过 `PLAYWRIGHT_BROWSERS_PATH` 指定本机浏览器缓存。导出脚本使用 4174 端口，结束后自动停止服务；生成文件不提交到仓库。

## GitHub Pages

在线演示地址为 `https://znnnnnnn-wil.github.io/Agent-Arena/`，由独立的 `gh-pages` 分支根目录发布。

运行 `npm run build:demo` 生成 `target/demo-site/`。构建只打包演示页、3D 舞台、字体、必要素材、公开决策节选、演示视频和许可证；不打包主应用、模型配置、数据库或验收原始报告。构建将资源路径改为相对地址，以支持 `/Agent-Arena/` 子目录，并把需要后端的创建操作改为 GitHub 项目指南链接。

需要更新网站时，先重新生成该目录，再将其中的内容正常提交到 `gh-pages` 分支。不要强制推送或覆盖其他站点分支。公开的视频位于 `src/main/resources/static/assets/demo/Agent-Arena-60s.webm`，是从本项目演示页导出的展示素材。
