# webremote 代码卫生批（PRD 存底）

父任务：`.trellis/tasks/08-12-wr-frontend-overhaul/`（共同约束全部适用）。排在 `08-12-wr-ui-overhaul` 之后执行。

## Goal

低风险清理与文档对齐：

- 删 `media.js:14-17` 死常量（QUALITY_STORAGE_KEY 四件套，与 app.js 逐字重复且未使用未导出）。
- 删 app.js 对 `disposeDecodedImage` 的无用解构。
- `clamp` 从 `ns.Media` 移至 `ns.Style`（解除 ui.js→media 依赖；`ns.Media.clamp` 保留转发以兼容既有引用）。
- 评估 `$` helper（app.js/input.js 重复定义）与 localStorage try/catch 样板是否值得收敛（不强制）。
- 修正 `.trellis/spec/frontend/web-browser-client-architecture.md` 依赖链描述（media 实际依赖 protocol+style；ui 依赖 media——clamp 归位后更新为 style）；补注 08-01 归档「FIXED_RESOURCE_PATHS 非白名单」结论已过时；登记 UI 任务新增的令牌/插槽/i18n/主题约定（若 UI 任务 Step 8 未完成该项）。

## 验收标准

- [ ] `verifyWeb` + `testServerSmoke` 全绿；既有 WebTest 导出未删（转发保留）。
- [ ] spec 描述与实测依赖拓扑一致。

轻量任务，预计 PRD-only 即可激活；如实施中发现影响面扩大，先补 design/implement。
