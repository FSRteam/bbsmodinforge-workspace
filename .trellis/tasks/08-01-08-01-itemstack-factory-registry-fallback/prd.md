# ItemStackKeyframeFactory 静默 registry 回退

## Goal

`ItemStackKeyframeFactory.ops(provider)` 在 `currentRegistry()` 解析为 `null` 时回退纯 `NbtOps.INSTANCE`。非空附魔物品经 `toData` 编码失败 → 静默返回空 `{}`（整个物品丢失，非仅附魔）；`tryFromData` 解码失败 → 静默 `ItemStack.EMPTY`。无异常、无日志。

## 触发场景（当前未触发）

- 客户端线程且 `Minecraft.getInstance().level == null`（主菜单 / 未进世界 / 世界加载切换瞬间）
- 服务端线程且无 `ServerLifecycleHooks.getCurrentServer()`（启动/关闭、后台线程）
- headless / datagen / 无 registry 上下文

## 现状

- 正常路径 provider 都在（服务端回放=服务端 registry；客户端编辑器=level registry；`Inventory.stageForPlayer` 显式传 provider），故为潜在稳健性漏洞而非活动 bug。
- 风险是回归面：一旦从无 registry 线程调用，坏的 `{}` 会静默落盘，源 ItemStack 已丢弃、不可恢复。

## Requirements

- `toData`：非空物品 encode 失败时打告警日志（含 provider 是否缺失），而非静默返回空 `{}`。
- 不改 `tryFromData` 的网络暂存语义（decode 失败返回 empty 是有意的）。

## Acceptance Criteria

- [ ] 非空附魔物品在无 registry 上下文编码时产生告警日志
- [ ] 正常路径（有 registry）行为不回归
- [ ] `testReplayKeyframeConsistency` 仍全绿

## Notes

- 来源：NBT 兼容性审查 A2（代理 A+C 共识），与 08-01-08-01-item-picker-nbt-registry-ops 同源同类。
- 延期：用户要求记录但不修复（2026-08-01）。
