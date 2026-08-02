# 物品选择面板 NBT 编辑附魔物品变空气

## Goal

让物品选择面板与存储层使用一致的 registry-aware 编解码，使附魔物品 NBT 正确显示且编辑可往返。`94be896c` 只修了 `ItemStackKeyframeFactory` 的序列化（存储层），但物品选择面板 `UIUnifiedPickOverlayPanel` / `UIItemStackOverlayPanel` 仍用纯 `NbtOps.INSTANCE`，导致附魔物品 NBT 显示 `{}` 且一编辑就变空气。

## Requirements

- `UIUnifiedPickOverlayPanel`（物品关键帧/动作剪辑/物品 Form 实际使用的面板）NBT 显示与编辑改为 registry-aware 编解码。
- `UIItemStackOverlayPanel`（同类 bug，当前死代码）一并修复，保持一致。
- 复用 `ItemStackKeyframeFactory` 已有的 per-call registry 解析，避免重复实现。

## 涉及文件

- `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/widgets/UIUnifiedPickOverlayPanel.java`（主修复，2 处）
- `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/widgets/UIItemStackOverlayPanel.java`（同类 bug，2 处）
- `new/src/main/java/mchorse/bbs_mod/utils/keyframes/factories/ItemStackKeyframeFactory.java`（暴露 `currentOps()` 复用 registry 解析）

## Acceptance Criteria

- [x] 附魔物品在物品选择面板中 NBT 显示完整附魔数据（非 `{}`）——回归测试覆盖 encode 非空
- [x] 编辑附魔物品 NBT 后附魔保留，物品不变成空气——回归测试覆盖 parse 往返
- [x] 无附魔的普通物品行为不回归（同 ops，低风险）
- [x] 服务端回放编解码（94be896c 已修路径）不回归——`testEnchantedEquipmentSerializationRoundTrip` 通过
- [x] 编译通过（`compileJava` + `compileClientJava`）；源码级检查覆盖全部 4 处 `ItemStack.CODEC` + 纯 `NbtOps` 替换点

## 状态

- 分支 `work/mcaxolotl-item-picker-nbt-registry`（new 仓库），提交 `4d89f78d`
- `testReplayKeyframeConsistency` 全绿（含新增 `testEnchantedItemPickerNbtRoundTrip`）
- **PR #16 已创建**：https://github.com/FSRteam/bbs-FSR/pull/16（2026-08-01）
- 自动化测试通过；游戏内验证清单见 PR Testing 段

## Notes

- 存储层 `ItemStackKeyframeFactory` 的序列化逻辑（94be896c 已修）不改动。
- 其它 registry-held 组件类型（药水效果等）若显示异常，仅登记不在此任务处理。
