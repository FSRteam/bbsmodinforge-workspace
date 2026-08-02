# 死亡关键帧轨道

## Goal

为 BBS 回放系统新增一个"死亡"关键帧通道（`death`），使得玩家/生物在被击杀时可以呈现出"模型变红 + 向一个方向倒下"的死亡动画，并作为一个可录制、可编辑的轨道存在于玩家/生物相关关键帧组中。

现有机制：`ReplayKeyframes` 已有 `damage`（受击 `hurtTime` 红闪）通道；`ActorEntityRenderer` 用 `deathTime` 作倒下旋转、`LivingEntityRenderer.getOverlayCoords` 作死亡变红。缺一个把"死亡状态"作为轨道的通道。

## Requirements

- 在 `ReplayKeyframes` 中新增 `death` 通道（`KeyframeChannel<Double>`，作用同布尔态：0 = 存活，>0 = 死亡）。
- `record()`：录制时根据实体的死亡/死亡进度写入 `death` 通道。
- `apply()`：回放时根据 `death` 通道驱动实体的死亡状态（死亡角度 + 死亡红闪 overlay）。
- 通道加入 `CURATED_CHANNELS` 汇总清单，供 UI 显示。
- 覆盖形态：玩家/生物相关轨道（回放、actor、编辑器预览统一生效）。用户决策已确认"玩家/生物轨道"。

## 关键技术约束（研究结论）

- 死亡渲染靠两层：
  1. **倒下角度**：`ActorEntityRenderer.setupTransforms`（`ActorEntityRenderer.java:84-89`）用 `entity.deathTime` 计算 Z 轴倒下角 `min(sqrt(deathAngle),1)*90°`。
  2. **死亡变红**：`LivingEntityRenderer.getOverlayCoords(livingEntity, 0F)` 依据 `hurtTime>0 || deathTime>0` 返回红色覆盖纹理。
- 需要让 `death` 通道映射到渲染时实体能读取的死亡状态（`deathTime` / overlay）。
- `IEntity` 目前无死亡状态字段，需新增 getter/setter，或在渲染路径中处理。

## Acceptance Criteria

- [ ] 新增 `death` 关键帧通道，出现在 `CURATED_CHANNELS` 中，可作为轨道录制/编辑。
- [ ] 录制时死亡状态正确写入 `death` 通道。
- [ ] 回放/actor 模式下，`death` 通道驱动模型变红 + 倒下（复用 `deathTime` 角度与 overlay）。
- [ ] 无死亡关键帧时行为与现状完全一致（backward compatible）。
- [ ] `new/` 编译通过，裸 JVM 相关测试（如有关）通过。
- [ ] 未引入 GeckoLib / Iron's Lib 依赖。

## Notes

- 死亡"向一个方向倒下"的角度方向由现有 `setupTransforms` 决定（Z 轴旋转），本轮保持该方向语义，不新增方向选择。
- 与 `damage` 通道的区别：`damage` 是瞬时受击红闪（`hurtTime`），`death` 是持续的死亡倒地状态。
