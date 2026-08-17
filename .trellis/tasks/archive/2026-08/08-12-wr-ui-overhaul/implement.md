# 执行计划：webremote UI 全量修复

约定：每个 Step 是独立回滚点；Step 内验证命令全绿才进入下一步。验证命令在 `bbs-web-remote/` 目录运行：`./gradlew.bat verifyWeb`、`./gradlew.bat testServerSmoke`（Windows）。

> **回滚机制（2026-08-12 实测）**：`bbs-web-remote/` 不在 git 跟踪范围（根 `.gitignore` `/*` 白名单模式只跟踪 `.trellis` 等控制面），无法用 commit 做回滚点。改用备份文件：每个 Step 动手前把将改动的文件复制到 `bbs-web-remote/backup/wr-ui-overhaul/step<N>/`，回滚 = 拷回。08-01 任务当年同样用 `backup/*.orig`。

## Step 0（A0 使能）

- [x] build.gradle `verifyWeb*Syntax` 从硬编码下标改为遍历 `webRuntimeScripts` 动态注册（任务名/依赖语义等价）
- [x] `media.js:52` 字面 NUL → `\x00` 转义
- 验证：`verifyWeb` 全绿；`rg FrameAssembler bbs-web-remote/...web/media.js` 能命中（文件恢复文本可见）

## Step 1（A1 令牌）

- [x] `:root` 令牌族（玻璃背景档、accent/focus/warning/danger 透明度族、`--z-*` 梯度表）
- [x] styles.css 全文字面值 → 令牌（像素级等值替换，不归并近似值）
- 验证：`verifyWeb`；人工抽查四断点（>900 / 560–900 / <560 / <400）

## Step 2（A1 治理）

- [x] `.glass-panel` 复合类，六面板挂类去重
- [x] CSS id 选择器 → 类选择器（HTML 加 class，id 保留）；冗余 `[hidden]` 规则删除
- 验证：`verifyWeb` + `testServerSmoke`（marker `.app-shell`/`.quality-control` 仍在）

## Step 3（A2 HUD 插槽）

- [x] `.hud-layer` 插槽层（pointer-events 分层）+ connection-card/participants/diagnostics/toast 区/mobile-controls 入槽
- [x] 手工避让坐标清除，断点 media query 改插槽级重排；工具栏默认位令牌化（JS 不动）
- 验证：`verifyWeb`；floating-toolbar.test 绿；四断点浮层无遮挡

## Step 4（A2 小屏可达）

- [x] `mobileControlButton` 加入 mobile-controls；`updateControlUi` 同步镜像节点；`bindUi` 绑 `toggleControl`
- 验证：mobile-input / session-lifecycle webTest 绿；≤560px 人工确认控制入口可用

## Step 5（A3 i18n 模块与发布）

- [x] 新建 `i18n.js`（`ns.I18n`：t/hydrate/setLocale/addCatalog，zh-CN 内建）
- [x] 9 处发布同步：index.html、webRuntimeScripts、FIXED_RESOURCE_PATHS、smoke 逐资源+缓存断言、10 个 webTest require 链、app.js 解构 + WebTest 导出、webTestTasks
- [x] 新增 `i18n.test.js`（t 缺 key 回退 / 参数插值 / hydrate 遍历）
- 验证：`verifyWeb`（新链 + 新测试）+ `testServerSmoke`（`/i18n.js?wire=1.1` 断言）

## Step 6（A3 文案抽离）

- [x] 批 1：app.js / media.js / ui.js / control.js 动态文案走 `t()`（zh-CN 逐字不变）
- [x] 批 2：index.html `data-i18n`/`data-i18n-attr` + 启动 `hydrate(document)`
- 验证：全部 webTest 不改断言全绿（文案逐字回归）

## Step 7（A3 浅色主题）

- [x] `html[data-theme="light"]` 令牌覆盖组（对比度过检）
- [x] 顶栏 `#themeToggle` + localStorage `bbs-web-remote.theme` + `applyTheme()`（dataset/theme-color/color-scheme 同步）
- 验证：`verifyWeb`；双主题人工对比度抽查；刷新保持

## Step 8（收尾）

- [x] `verifyWeb` + `testServerSmoke` 最终全绿
- [x] 真实 Jetty URL 浏览器冒烟（console 无启动错误、连接表单可用、主题/语言水合正常）
- [x] spec 增补（令牌/插槽/i18n/主题约定）到 `web-browser-client-architecture.md`
