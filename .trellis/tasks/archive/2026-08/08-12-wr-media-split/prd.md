# webremote media.js 拆分（PRD 存底）

父任务：`.trellis/tasks/08-12-wr-frontend-overhaul/`（共同约束全部适用）。复杂任务：激活前必须补 `design.md` + `implement.md`。

## Goal

`media.js`（2247 行、六职责域）按职责拆分：

- `render.js`：CanvasRenderer + 解码工具（decodeImageBytes/flipDecodedVertically/sha256Hex 等）。
- `transport.js`：HybridMediaTransport + RtcSurfaceCache。
- `cache.js`：ResourceCache + SurfaceCache。
- 瘦身后的 `media.js` 留守 MediaMetrics / LinkFeedback / AdaptiveQualityController 与画质档函数。
- `ns.Media` 保留门面转发，WebTest 导出形状不变（只增不删）。
- `HybridMediaTransport.state` 中文文案（「协商中」等）改内部枚举 + i18n 目录映射（依赖 UI 任务已落地的 `ns.I18n`）。

## 约束

- 每个新文件走满 9 处发布同步清单（见父 prd）。
- 依赖保持单向无环：render/cache 依赖 protocol/style；transport 依赖 protocol/i18n。
- app.js 的 smoke marker 是字符串 `CanvasRenderer`（松散内容匹配），拆分后确认 app.js 仍含该引用或同步改 `ServerSmokeTestMain` 断言。
- 「搬类不搬方法」不适用于这些独立类（可整类搬移），但 App 侧解构行必须同步。

## 验收标准

- [ ] 全部既有 webTest 不改断言全绿；新文件各有资源断言。
- [ ] `verifyWeb` + `testServerSmoke` + 真实浏览器冒烟三层全绿。
- [ ] 拆分后各文件 < 1000 行，职责单一。
