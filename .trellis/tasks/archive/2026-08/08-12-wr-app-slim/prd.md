# webremote app.js 瘦身（PRD 存底）

父任务：`.trellis/tasks/08-12-wr-frontend-overhaul/`（共同约束全部适用）。复杂任务：激活前必须补 `design.md` + `implement.md`。排在 media 拆分之后。

## Goal

受「搬类不搬方法」硬约束的渐进瘦身：

1. **先补测试锁行为**（当前无覆盖）：`setTransportOverlay` 四态与 12s/6s 超时兜底、toast `note()` key 去重/合并计数、`requestRoleUpgrade` 不断线升级流程。
2. DemoFixture + demo wire 编码器（app.js:292-555，约 264 行，与 App 生命周期无关）移入独立模块（走 9 处发布清单）。
3. `updateDiagnostics`（140 行写 55 个 diag 节点）改「id → 取值/格式化函数」数据表驱动，表为可无 DOM 测试的纯结构。
4. `handleJsonMessage`（80 行 14 路分发）改查表分发。
5. App 方法全部保留为薄委托，不移出 app.js。

## 验收标准

- [ ] 新增测试先行（红→绿）；既有 webTest 全绿；App.prototype 既有方法未删改签名。
- [ ] WebTest 导出只增不删。
- [ ] `verifyWeb` + `testServerSmoke` + 浏览器冒烟全绿。
