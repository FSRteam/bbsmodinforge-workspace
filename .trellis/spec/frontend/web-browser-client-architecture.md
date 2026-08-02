# bbs-web-remote 浏览器端架构规约（`bbs-web-remote/src/client/resources/assets/bbswebremote/web/`）

> 适用范围：bbs-web-remote 的浏览器端 bundle（非 BBS mod UI、非 `new/`）。
> 此文档记录该 bundle 的模块化加载契约、测试机制与重构约束。

## 1. 模块化 IIFE 命名空间加载契约

浏览器端为**无构建步骤**的纯 `<script>` 多文件加载。每个模块是独立 IIFE，挂到
`global.BBSWebRemote`（`ns`）下的职责域子对象，供 app.js 解构取回。

```
protocol.js → error.js → style.js → media.js → input.js → control.js → ui.js → app.js
```

- 加载顺序**三处必须同步**：`index.html` 的 `<script>` 标签、`build.gradle` 的
  `webRuntimeScripts` 数组、8 个 `webTest/*.test.js` 的 `require()` 行。
- 依赖单向无环：error/style 无依赖；media 依赖 style；input/control/ui 依赖 protocol；
  app 依赖全部。
- 各模块 `ns.<Domain> = Object.freeze({...})`。模块文件开头必须校验前置模块已加载
  （`if (!ns) throw new Error("protocol.js must load before ...")`）。
- app.js IIFE 顶部从 `ns.*` 解构取回所有类/函数；**禁止模块间互相依赖作用域**，
  一切经 `ns`。

涉及的唯一服务端文件是 `build.gradle`（注册 `webRuntimeScripts` 与
`verifyWeb*Syntax` 逐个 `--check` 任务）。

## 2. WebTest 导出与 Node 无依赖测试机制

- `namespace.WebTest`（app.js 末尾 `Object.freeze` 块）导出类与工具函数，供
  `webTest/*.test.js` 无依赖 Node 加载；`globalThis.window = globalThis` + FakeElement
  模拟 DOM (`document.getElementById`/`querySelectorAll`/`addEventListener`)。
- **测试机制是硬约束**：新增模块文件必须同步加入 WebTest 导出才能被测试；
  WebTest 导出形状向后兼容（新增导出可，勿删改既有）。
- **「搬类不搬方法」约束是前提**：`session-lifecycle`/`media-reconnect` 用
  `Object.create(App.prototype)` 构造 App 再调 `app.acceptSessionOpen`/
  `app.consumeBinary` 等方法断言——**App 类的方法不能移出**。可拆的是「非 App 的
  独立类 + 独立工具函数」。App 方法体中的纯逻辑若可无 DOM 测试，抽成模块纯函数/
  类后 App 委托（渐进、只抽安全项）。
- 新增 webTest 后须在 `webTestTasks`（build.gradle）注册对应 `testWeb*` 任务，
  否则不进 `verifyWeb`。

## 3. 前端纯改动边界

- 本 bundle 的改动**禁止触碰** `src/client/java/` 服务端与 wire 协议
  （wire `1.1` / Addon / API `2.0`）。
- `FIXED_RESOURCE_PATHS`（ClasspathStaticHandler）只是**缓存优化非白名单**：
  `getClasspathResource` 对非列表路径走 `loadClasspathResource` 正常加载。前端新增
  js 文件**无需**也**不应**改服务端该列表。

## 4. 状态与 UX 约定

- 控制权三层状态（permission/role/lease）向用户收敛为三词主线：**观看中 → 可控制 →
  操作中**。统一由 `controlStateLabel()`（control.js，`ns.Control`）产出
  `{label, tone}`；诊断面板 `diagRole` 保留详细描述（". 协控 · 持有操作权"）与
  面向用户主线不冲突。
- 诊断面板分「基础」+「高级」两级（高级折叠 + localStorage 记忆展开偏好，
  键 `bbs-web-remote.diagnostics`）。diag 字段 id 必须与 app.js `$("diag...")` 引用
  一一对应。
- 传输降级可见状态（`setTransportOverlay`）：negotiating/switching/stalled/autoplay
  四态 + 超时兜底；toast 用 `key` 参数去重合并（`note(msg, announce, {level, key})`）。
- UX 恒定约束：降级期间保留上一帧或 overlay 占位，不留静默黑屏。
