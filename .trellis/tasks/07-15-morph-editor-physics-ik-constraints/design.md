# 技术设计：伪装编辑器物理、IK 与骨骼约束系统性修复

## 根因模型

本次三个症状来自两类边界破坏，而不是三个独立功能缺陷：

1. **渲染空间进入模拟空间**：NeoForge 世界/实体渲染使用摄像机相对 `PoseStack`，编辑器预览还会显式乘入预览相机 view。迁移遗漏了 FS 的 `FormRenderingContext.world` 绝对世界矩阵链，物理历史因此记录观察者运动。
2. **求解阶段互相覆盖**：IK 与物理会生成组合方向，最终通用约束又清空组合方向，导致合法的 IK/物理结果被回退值替换。
3. **运行时所有权过粗**：物理历史按全局 `IEntity + modelId` 缓存，不能区分同一模型资源上的不同 `ModelForm` 实例和配置。

## 坐标空间契约

- `FormRenderingContext.stack` 仅服务渲染，允许是摄像机相对、实体局部、物品局部或编辑器 view 空间。
- `FormRenderingContext.world` 仅服务需要稳定世界语义的模拟与世界目标转换。
- `world` 在 `ENTITY` / `MODEL_BLOCK` 上由实体插值后的绝对位置与 body yaw 初始化；其他类型保持模型局部单位空间。
- 表单 transform、body-part transform、模型固有的 Y 轴翻转以及需要的骨骼附着矩阵必须同时推进 `stack` 与 `world`。
- 相机位置、相机 view、RenderSystem model-view 不得写入 `world`。
- 世界目标覆盖先用 `world` 的逆矩阵转换到模型空间；重力与非本地风保持世界方向，本地风仅由模型世界旋转转换一次。

## 求解阶段

每次姿态重置和动画/FK应用后按以下顺序执行：

1. 通用骨骼限制先约束基础 FK Euler 姿态。
2. IK 在已约束的 FK 姿态上求解，并通过现有 `IKSolver.Limit` 保证链内约束。
3. 物理在 IK 结果上求解，并通过现有 `ChainSolver` 角度限制保证链内约束。
4. 不再在末尾运行会清空 `orient` 的通用约束。

未参与 IK/物理的骨骼仍由第 1 步限制；参与程序求解的骨骼由各自求解器限制，避免结果被二次覆盖。

## 状态所有权

- 每个 `ModelFormRenderer` 拥有一个 `ModelPhysicsRuntime` 实例。
- 该 runtime 内按弱引用 `IEntity` 再按模型 ID 保存链历史。
- 不同表单 renderer 即使引用同一模型资源也不会共享物理历史。
- 编辑器预览、列表缩略图、世界实体、影片 stub 和物品 stub 因 renderer/target 不同而隔离。
- 编译后的物理配置缓存仍可静态共享，因为它是不可变派生数据；可变 Verlet 历史不得静态全局共享。

## 兼容与影响范围

- 不改变 IK、physics、constraints 的序列化格式。
- 主要修改 `FormRenderingContext`、`FormRenderer`、`ModelFormRenderer`、`BlockFormRenderer`、`MobFormRenderer` 和 `ModelPhysicsRuntime`。
- `new/` 当前存在其他未提交改动；实现不得覆盖或格式化无关文件。
- `ModelInstance.lastBaseTransform` 没有消费者，移除或停止写入，避免继续暗示共享模型资源拥有实例模拟状态。

## 验证与回滚

- 静态对照 FS 1.21.1 的 world 矩阵传播点，确保没有漏掉 push/pop 或局部变换。
- 运行 `compileJava compileClientJava`、`build`、`git diff --check`。
- 游戏内分别验证编辑器旋转相机、玩家移动后退出、多实体同屏、IK+constraints、physics+constraints、嵌套 body part 和模型方块。
- 修改集中在上述契约文件；若出现渲染回归，可独立回滚 world 链传播，不影响数据格式。

## 视口拾取 ID 契约（2026-07-16 二次复测）

- Gizmo 保留区不是旧的 1–6：当前完整句柄区间为 `[Gizmo.STENCIL_X, Gizmo.STENCIL_MAX]`。`StencilMap.objectIndex` 必须初始化为 `Gizmo.STENCIL_MAX + 1`，并为每个 `Gizmo.Handle` 建立非表单映射。
- `StencilMap.objectIndex` 是当前表单的全局拾取基址；cubic immediate、cubic VAO 与 BOBJ skinning 携带同序的局部骨骼索引，`picker_models` 按 BBSFS 原版契约计算 `Target + localIndex`。
- `fillStencilMap` 随后从同一基址、同一骨骼顺序注册 ID。IK/physics 调试标记只在普通骨骼注册完成后追加完整 ID，不得影响普通骨骼范围。
- 活动面板消费解析后的骨骼名称；GPU 拾取失败不能由面板分发补丁掩盖。

## 性能优化设计（2026-07-16）

### 不变量与可复用数据

- `ModelIKCache` 在配置编译时完成祖先优先排序，并为每条链缓存 wanted bones；渲染时不再复制/排序链或创建 wanted set。
- `ModelPhysicsCache.Compiled` 缓存全模型 physics wanted bones 与 chain ids；运行时只读取。
- IK renderer workspace 复用 pivot-frame map、当前关节位置容器和常用向量/四元数；不得挂到共享 `ModelInstance`。
- 每个 physics `InstanceState` 复用 pivot-frame map，每个 `ChainState` 复用 chain-frame 容器与 stiffness scratch。
- `ModelPivotFrames` 更新已有可变 frame 内容，而不是每次为同一骨骼创建三组向量/四元数。

### 算法边界

- 保持两骨 analytic、长链 FABRIK、带限制 CCD 的选择与用户配置迭代数。
- 保持 physics 每 tick 3 个固定子步及 deterministic catch-up，不以降采样作为首轮优化。
- 允许 JIT 继续优化局部数学对象；首轮优先消除确定会逃逸到集合/骨骼字段的分配和重复拓扑工作。

## IK 伸直与回正设计（2026-07-16）

- 将 `DEFAULT_SOFTNESS` 调整为 0；序列化中显式 softness 保持原值，缺省数据得到可完全伸直的硬 reach。
- 为每个 renderer + simulation owner + chain 保存上一稳定 bend normal，所有权与物理历史相同，不挂共享模型资源。
- 使用原 FS 的 20° hysteresis / 3° singularity 作为回正区间基线：进入奇异区间时优先 rest bend，区间边缘使用上次稳定法线避免翻面；明显离开后由当前 pole/live solve 接管。
- rest bend 不存在时退回上次稳定法线，再退回 deterministic perpendicular；不得使用摄像机轴。
- 该修复不新增数据字段或 UI；pole angle、pole target 与显式 softness 仍保持现有优先级。

### 直线角度恢复补充（截图复测后）

- bend-normal hysteresis 只决定“往哪一侧弯”，不能保证弯曲幅度为零；因此不能单独解决限制反方向的小负角。
- 两骨解析解根据有效目标距离预测关节弯曲角。硬 IK（softness=0）处于 3° straight-restore 区间时，直接构造退化三角形：中间点位于 root→target 轴上、距离 root 为第一段 rest length，末端位于两段总 rest length 处。显式 softness > 0 不进入此分支，继续保留渐近微弯。
- 该区间以内优先保持 authored straight/original local angle，允许目标与链端存在很小的死区；超过 3° 后恢复标准 analytic IK，20° bend-normal hysteresis 继续只负责防翻面。
- stretch 只能处理目标位于 solved tip 外侧的正向缺口；straight-restore 产生的“链端略超过内侧目标”不得被解释为反向 stretch/压缩。
- 3° 常量由 `IKSolver` 单点拥有，位置解和 `ModelIKApplier` 的奇异区间判定必须引用同一来源，避免两个阈值再次漂移。

### 最终旋转约束补充（游戏内二次复测后）

- 解析/FABRIK/CCD 位置解与最终骨骼渲染旋转是两个不同层次。位置共线不能证明最终 `ModelGroup.orient` / `BOBJBone.orient` 合法。
- `solveBendForLimits` 可继续用骨段方向搜索尽量可达的 bend plane，但 `buildChainOrientations*` 必须对完整 swing+roll 局部四元数执行最终 min/max 钳制。
- 子骨的 `parentWorld` 必须乘入钳制后的实际 orientation；不得继续用未受限候选四元数，否则下一节骨骼仍会在错误父坐标系中分解。
- 最终钳制可能让受限目标不再完全可达，这是正确的约束语义；不得为了保持位置骨线命中目标而允许渲染网格越过关节限制。

## IK maintain-offset 绑定语义（2026-07-17）

### 零输入不变量

- 每条链第一次进入有效 IK 状态时，在 renderer + simulation owner + model + chain 的 workspace 中记录目标与 pole 控制器相对链根父坐标系的绑定基准。
- 每帧动画、姿态和通用约束先生成当前 FK 链。该 FK 链是本帧 IK 的零输入姿态，不是只在配置创建时保存的静态骨架快照。
- 目标/pole 控制器仍在绑定基准、没有外部 override、`poleAngle=0` 且 tip rotation 没有相对变化时，IK 必须直接保留 FK，不写入新的 `orient` 或关节位置。

### 控制器变化

- 普通目标骨骼的运动按“当前控制器相对绑定控制器的位移”作用到本帧 FK tip；因此链动画可以继续变化，而控制骨骼的后续移动仍驱动 IK。
- 影片的绝对 `ik_targets` override 在权重 1 时仍精确命中世界目标；fade 从 maintain-offset 的本帧基准目标过渡到绝对 override。
- Pole 的普通骨骼运动和 `pole_targets` override 都转换为相对绑定 pole 的绕 root→target 轴角度变化。求解器从当前 FK bend plane 出发只施加这个角度变化，再叠加现有 `poleAngle`。
- 静态 pole 位置可在直链/退化姿态中提供弯曲侧 fallback，但不得在绑定瞬间把非退化 FK 肘/膝强行瞄准 pole。

### 旋转输出

- 方向/roll 重建必须以当前 FK 骨段 frame 为 source、IK solved frame 为 destination，应用相对修正；不得每帧从模型 rest frame 重建一个会覆盖 FK 的绝对姿态。
- target/tip rotation 同样使用 maintain-offset：控制器相对绑定旋转的 delta 叠加到本帧 FK tip，未旋转控制器时保持原 tip rotation。
- weight 在 FK 与相对修正后的 IK 结果间插值；约束仍钳制最终写给 renderer 的局部 quaternion。

### 生命周期

- workspace 创建、compiled config 变化、simulation owner 变化、链禁用后重新启用时重新绑定。
- 不把绑定基准挂到共享 `ModelInstance`；不同预览、世界实体和缩略图不得共享控制器基准。
