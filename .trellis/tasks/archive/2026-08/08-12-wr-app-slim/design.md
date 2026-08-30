# 设计：app.js 瘦身

前置：media-split 已完成。硬约束：「搬类不搬方法」——App 方法全部留在 app.js（可为薄委托）；WebTest 导出只增不删。

## 1. 测试先行（锁行为，红→绿流程按"新测试对现状即绿"理解）

新文件 `src/webTest/app-behavior.test.js`（注册 webTestTasks `testWebAppBehavior`），覆盖：

- `setTransportOverlay`：none/negotiating/switching/stalled/autoplay 五态 hidden/dataset.kind/textContent；negotiating→stalled 6s 兜底、任意态 12s 超时清除（fake setTimeout/clearTimeout 注入或 global stub）。
- `note()`：同 key 去重合并（active toast 不重复入列）、level 样式键、历史计数递增。
- `requestRoleUpgrade`：未连接拒绝；发送后 roleUpgradePending=true、3s 无响应回 false+pending 清除；controlButton 文案联动（升级中）。
- 用 `Object.create(App.prototype)` + 手工 stub 依赖（参照 onboarding.test.js 范式）。

## 2. demo.js 抽离（约 264 行）

- app.js 的 DemoFixture 类 + demo wire 编码辅助（command/colorBytes/concatBytes/demoQuad/demoClip/demoText/demoSurface/demoTexture/demoCursor，约 292-555 行区域）整体搬入新文件 `demo.js`。
- `ns.Demo = Object.freeze({ DemoFixture })`；前置校验 protocol（+用到的 ns 成员）。
- 加载序：… → media → **demo** → input → control → ui → app。
- app.js 顶部解构 `const { DemoFixture } = namespace.Demo;`；App.startDemo 中 `new DemoFixture(this)` 不变（方法留 app.js）。
- WebTest 导出追加 `DemoFixture`。
- 9 处发布同步（script 标签 / webRuntimeScripts / 描述表 / FIXED_RESOURCE_PATHS / smoke 断言 marker `ns.Demo` / 缓存断言 / 全部 webTest require 插 media 之后）。

## 3. updateDiagnostics 表驱动

- 模块级纯结构 `DIAGNOSTIC_ROWS = [{ id: "diagAccessUrl", value: (app, m) => ... }, ...]`（55 项与现有节点一一对应，含 wide/explanation 标记在 HTML 已有）。
- `updateDiagnostics()` 变为遍历表写节点（textContent 比较 + 赋值，语义与现在一致）。
- 表内取值函数是纯读取（app 字段/已有格式化函数），可无 DOM 测试：WebTest 导出 `DIAGNOSTIC_ROWS`，app-behavior.test.js 断言行数与关键行 id 存在。

## 4. handleJsonMessage 查表分发

- 模块级 `JSON_MESSAGE_HANDLERS = { SESSION_OPEN: (app, message) => ..., ... }`（14 路与现 switch 一致；default 路径保留）。
- `handleJsonMessage` 变为查表调用薄委托；方法留 app.js。
- WebTest 导出 `JSON_MESSAGE_HANDLERS`（键集合断言进 app-behavior.test.js）。

## 5. 明确不做

- 不动 App 方法签名/位置；不拆 App 类；不新增依赖方向（demo.js 仅 protocol/i18n）。
- 不改 wire/协议；不改既有测试断言。
