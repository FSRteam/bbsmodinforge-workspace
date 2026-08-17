# 执行计划：app.js 瘦身

验证命令在 `bbs-web-remote/` 运行：`gradlew.bat verifyWeb`、`gradlew.bat testServerSmoke`。

## Step 1（测试先行）

- [x] 新建 `src/webTest/app-behavior.test.js`（setTransportOverlay 五态+超时 / note 去重 / requestRoleUpgrade 流程 / DIAGNOSTIC_ROWS·HANDLERS 占位断言后补）
- [x] build.gradle `webTestTasks` 注册 `testWebAppBehavior`
- 验证：verifyWeb 全绿（新测试对现状即绿 = 行为锁定）

## Step 2（demo.js 抽离）

- [x] DemoFixture + demo 编码辅助搬入 demo.js（ns.Demo）
- [x] app.js 解构 + WebTest 导出追加 DemoFixture
- [x] 9 处发布同步（加载位 media 之后）
- 验证：verifyWeb + testServerSmoke（`/demo.js` 断言）+ 浏览器离线演示冒烟

## Step 3（诊断表驱动）

- [x] `DIAGNOSTIC_ROWS` 纯结构 + `updateDiagnostics()` 遍历化（输出逐字不变）
- [x] WebTest 导出 DIAGNOSTIC_ROWS；app-behavior.test.js 补行数/关键 id 断言
- 验证：verifyWeb（session-lifecycle diag 断言全绿）

## Step 4（消息分发查表）

- [x] `JSON_MESSAGE_HANDLERS` 表 + `handleJsonMessage` 薄委托（default 路径保留）
- [x] WebTest 导出；app-behavior.test.js 补键集合断言
- 验证：verifyWeb + testServerSmoke + 浏览器冒烟

## Step 5（收尾）

- [x] implement.md 勾选；任务归档
