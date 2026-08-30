# webremote 前端重构总纲（父任务）

## Goal

对 `bbs-web-remote` 浏览器端做系统性重构：先完成 UI 全量修复（样式治理、HUD 插槽布局、小屏可达性、i18n 文案抽离、浅色主题），再依次完成代码卫生批、media.js 拆分、app.js 瘦身。父任务持有需求集、任务地图与跨子验收标准，本身不做实现。

## 背景（2026-08-12 只读分析结论）

- `app.js` 2749 行，App 类 2146 行 / 约 90 方法 / 直接引用约 100 个 DOM id（上帝对象）。
- `media.js` 2247 行装六个独立职责域（CanvasRenderer / ResourceCache / SurfaceCache / MediaMetrics+LinkFeedback / AdaptiveQualityController / HybridMediaTransport+RtcSurfaceCache）。
- 状态→UI 更新靠事件直写 + 500ms 诊断轮询 + 33ms 输入心跳三机制并行，无统一通道。
- UI 侧：令牌泄漏（玻璃背景/accent 透明度字面量散布）、玻璃卡片模式重复 6 次、z-index 手工散布、7 个浮层手工坐标避让、≤560px 控制入口 `display:none` 不可达、中文硬编码无 i18n、深色锁死。
- 隐藏地雷：`media.js:52` 字面 NUL 字节使文本工具把整文件当二进制跳过。
- 交互与可访问性基础扎实（aria/focus-visible/reduced-motion/forced-colors），重构不得倒退。

## 边界与约束（对全部子任务生效）

- **协议冻结**：wire `1.1` / Addon API `2.0` 不变；新增消息/字段必须增量、可选、向后兼容。
- **无构建步骤**：纯 `<script>` IIFE ns 模块，禁新框架/构建工具/npm 依赖。
- **CSP**：`script-src 'self'` / `style-src 'self'`，禁内联脚本与样式。
- **发布白名单**：`FIXED_RESOURCE_PATHS` 是真白名单（不注册即 HTTP 400）；新增/重命名顶层 js 须同步 9 处（index.html、webRuntimeScripts、verifyWeb*Syntax、FIXED_RESOURCE_PATHS、smoke 逐资源断言、smoke 缓存断言、全部 webTest require 链、app.js 解构/WebTest 导出、webTestTasks）。
- **测试硬约束**：WebTest 导出只增不删；App 方法不移出 app.js（「搬类不搬方法」）；既有 10 个 webTest 不改断言前提下保持全绿。
- **同源约束**：连接表单只收配对码/角色/显示名，地址从 `location.origin` 推导。
- **可访问性不倒退**；每个子任务独立可验证、可独立归档。
- 历史陷阱：08-01 归档中「FIXED_RESOURCE_PATHS 非白名单」结论已过时，以现行代码与 spec 为准。

## 子任务地图

| 子任务 | 优先级 | 范围 | 规划状态 |
|---|---|---|---|
| `08-12-wr-ui-overhaul` | P0 | A0 使能 + A1 样式治理 + A2 HUD 插槽/小屏可达 + A3 i18n/浅色主题 | 全套工件，本次实现 |
| `08-12-wr-code-hygiene` | P1 | 死代码清理、clamp 归位、spec 依赖链修正 | PRD 存底 |
| `08-12-wr-media-split` | P1 | media.js 拆 render/transport/cache，ns.Media 门面 | PRD 存底，激活前补 design/implement |
| `08-12-wr-app-slim` | P2 | 补测试后 demo 抽离、诊断表驱动、消息分发查表 | PRD 存底，激活前补 design/implement |
| `08-16-wr-mobile-experience` | P1 | 手机端右侧操作中枢、WASD/飞行、虚拟鼠标、鼠标流畅度 | 2026-08-16 增补，随 ui-overhaul 一并实施 |

状态通道统一（refreshUi 单入口）暂不立项，待 A–D 稳定后单独评估。

## 跨子任务验收标准

- [ ] `verifyWeb` + `testServerSmoke` 全绿；真实 Jetty URL 浏览器冒烟无启动 console 错误。
- [ ] wire `1.1` / API `2.0` 对外不变；旧服务端 + 新浏览器组合仍可连接工作。
- [ ] WebTest 既有导出与既有 10 个测试未删改、全绿。
- [ ] 每个子任务各自质量检查通过；父任务做最终集成 review。

## Out of Scope

- BBS 桌面 UI 响应式重排、浏览器端重实现 BBS 控件、协议语义变更、第二语言翻译内容。
