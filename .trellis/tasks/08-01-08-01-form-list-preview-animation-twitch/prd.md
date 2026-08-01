# Form 列表预览默认动画抽搐重复

## Goal

4 个菜单的"伪装子菜单"里，Form 列表缩略图播放的默认动画抽搐重复（而不是平滑播放）；进入编辑后的 3D 预览在各菜单表现不同。定位根因并修复。

## 症状（用户实测，2026-08-01）

| # | 菜单 | 列表缩略图 | 编辑 3D 预览 |
|---|------|-----------|-------------|
| 1 | 实体选择器 | 大部分 Form 默认动画抽搐重复 | 抽搐重复 |
| 1' | 实体选择器 | 末影龙例外：列表不播放任何动画（静止），预览也完全静止 | 同左 |
| 2 | 伪装菜单 | 抽搐重复（同 1） | 全部正常播放 |
| 3 | 影片菜单 | 抽搐重复（同 1） | 全部正常播放 |
| 3' | 影片菜单 | 烈焰人例外（后经用户澄清：是模型方块菜单选中过的 Form） | 同左 |
| 4 | 模型方块菜单 | 所有 Form 默认动画抽搐重复（含末影龙） | 全部正常播放 |
| 4' | 模型方块菜单 | 在列表上选中任一 Form → 其默认动画恢复正常播放 | 同左 |

## 分析结论（2026-08-01）

- 列表缩略图路径 `MobFormRenderer.renderInUI` 不调用 `Form.update`（不走 renderer.tick），实体 `tickCount` 冻结 → `ageInTicks = tickCount + partialTick` 随 partial tick 0..1 振荡 → 时间型 idle 动画抽搐。
- 3D 预览走 `UIPickableFormRenderer.update` → `form.update(entity)` → `renderer.tick` → `entity.tick()`，平滑。
- 各菜单预览差异 = 是否 `palette.updatable()`（实体选择器不调 → 预览也抽搐；伪装/影片调 → 正常；模型方块 `setTarget(世界实体)` → 正常）。
- 例外统一为同一机制：模型方块"选中即恢复" = 选中把全局 form 实例赋给方块 → `ModelBlockEntity.tick` → `Form.update` → `renderer.tick` 持续 tick **缓存在 form 上的共享 renderer**；影片"烈焰人正常" = 该 form 之前被赋给过模型方块。
- 末影龙静止 = dragon 动画是 tick 驱动的（`EnderDragon.tick` 维护 wing flap 状态），不 tick 完全静止。

## 修复尝试（未成功，已回退）

- 实现：`MobFormRenderer.advanceUIAnimation` 在 partial tick 回绕时 `entity.tick()`，接入 `renderInUI`；新增源码级回归检查 `uiPreviewsAdvanceTheAnimationClock`。
- 编译 + 5 个测试套件全绿。
- **用户游戏内验证：问题未解决，判定该修复方向不成立。**

## 状态（2026-08-01）

**延期，不修复。** 用户要求记录进 trellis 但不修复。所有动画修复代码已回退，仅保留 NBT 面板修复（`08-01-08-01-item-picker-nbt-registry-ops`，提交 `4d89f78d`）。

- 分支 `work/mcaxolotl-item-picker-nbt-registry` 当前只含 NBT 修复，无动画改动。
- 后续若再调查，需先推翻"列表不 tick → ageInTicks 振荡"假设或换方向（如动画状态复位、transition 采样、form 动画时钟），并结合用户实测复现。

## 范围外

- actor+fp 回放渲染（08-01-07-31-actor-fp-replay-invisible-render）
- 相机/声音等其他预览
