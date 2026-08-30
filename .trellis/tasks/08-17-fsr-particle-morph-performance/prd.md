# FSR 暴雪粒子与伪装回放性能优化

## Goal

降低大量 Snowstorm/暴雪粒子和大量套用 Form（伪装）的回放角色同时存在时的客户端 CPU、内存分配与渲染准备开销，同时保持现有画面、粒子事件、动画、IK、物理和视频导出结果一致。

## Confirmed Facts

- `ParticleEmitter` 使用 `ArrayList` 保存粒子，当前逐个删除死亡粒子；同一 tick 大量粒子死亡时会重复搬移数组。
- 粒子渲染时，每个粒子先刷新发射器变量并计算全部曲线，再刷新粒子变量并再次计算全部曲线。
- `particle_update_expression` 目前也会在渲染采样时执行，导致一帧多次渲染时产生额外计算和潜在副作用。
- Actor 回放的可见 Form 由 Minecraft 实体渲染器负责视锥裁剪；`BaseFilmController` 仍会为 Actor 的编辑 Gizmo 准备隐藏的 Form 渲染上下文。
- 非 Actor 回放由 `BaseFilmController` 直接遍历渲染，当前没有视锥裁剪。
- `MobFormRenderer` 每 tick 对内部实体重复写入六个装备槽，即使装备没有变化。
- 项目现有渲染契约要求动画时间、IK、物理和约束不能通过少算帧来近似。

## Requirements

- 粒子死亡清理必须保持存活粒子的原顺序，并在一次线性扫描内完成。
- 粒子渲染必须保持每粒子的 Molang 变量与曲线结果，但同一粒子采样不得重复计算整套曲线。
- `particle_update_expression` 只在粒子逻辑更新/创建路径执行，纯渲染采样不得再次执行。
- 非 Actor 回放只在拥有明确、稳定的自定义 Form hitbox 且没有相对/锚点位移时进行视锥裁剪；不可靠边界必须保守放行。
- Actor 回放在不需要编辑 Gizmo 时跳过隐藏 Form 的渲染准备；正常 Actor 实体渲染保持不变。
- Mob Form 只在装备内容实际变化时写入对应装备槽。
- 不把 Minecraft 世界碰撞、实体或 Form 状态放到工作线程。
- 不引入 GPU Compute、近似动画降频或跨角色共享独立 IK/物理状态。

## Acceptance Criteria

- [x] 同一 tick 批量死亡的粒子以 O(n) 方式清理，新增粒子和存活粒子的顺序正确。
- [x] 粒子渲染路径每个粒子只执行一次曲线刷新，且不执行 `particle_update_expression`。
- [x] 粒子逻辑更新和创建仍执行 `particle_update_expression`，事件与生命周期测试通过。
- [x] 非 Actor 回放的安全 hitbox 视锥裁剪不会应用于相对回放、锚点回放或无明确 hitbox 的 Form。
- [x] 关闭编辑轴/Gizmo 时，Actor 回放不再进入隐藏 Form 的渲染准备；开启时行为不变。
- [x] Mob Form 的六个装备槽仅在物品或数量变化时同步。
- [x] `testParticleRuntimeConsistency`、`testFilmEditorMigration` 和 `compileClientJava` 通过。
- [ ] 独立工作树保持干净交付，不修改原 `new` 工作目录中的未提交内容。

## Out Of Scope

- GPU Compute 粒子模拟和跨发射器透明粒子全局合批。
- 会改变动画采样精度的距离 LOD、IK/物理降频和近似 Pose 共享。
- 自动推导任意嵌套 BodyPart、粒子和动态骨骼的精确世界包围盒。
