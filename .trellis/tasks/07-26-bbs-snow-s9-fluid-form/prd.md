# S9 流体模拟 Form

## Goal

在独立 `bbs-snow-addon` 中把旧版 `FluidForm` 迁移到 NeoForge 1.21.1，使玩家可以从 BBS 的 Extra Form 分类创建、保存、重新加载和编辑流体伪装，并在世界、Film 预览/播放及视频导出路径中稳定渲染三种流体模式。迁移必须保留旧数据格式，同时修复旧实现中公开但无效果的体积选项和可能拖垮客户端的无界网格。

## Confirmed Facts

- 父任务 R3.7 要求 `FluidForm` 类型、流体渲染器、控制器/模拟和流体 UI 面板。
- 旧实现由 7 个主要文件组成，含 19 个序列化值和 `FULL_OCEAN`、`WATER_DROP`、`PROCEDURAL` 三种模式。
- 当前 FSR 主 mod 没有等价 Fluid Form；当前 addon 只有 `fluidRealisticModelInteraction` 设置和为 S9 预留的 `FilmsControllerAccessor`。
- 目标注册入口已经明确：common 侧 `RegisterFormsEvent`，client 构造期 `FormUtilsClient.register`/`UIFormEditor.register`，client-started 阶段 `FormCategories.addExtraForm`。
- 旧实现的 `sizeY` 和 `fillBlock` 只存在于数据/UI，渲染器从不读取它们；照搬会继续产生可见的无效控件。
- 旧 FULL_OCEAN 最坏会构造约 100,614,150 个顶点/流体/帧，并且在渲染路径中按墙钟推进模拟、调用 `Math.random()`；这既不安全，也无法在暂停、逐帧和导出中复现。
- 旧 controller 每 tick 扫描邻近世界实体、全部 Film controller 和详细 Cubic 网格；BOBJ 没有公开详细网格数据，只能回退到实体/根骨骼采样。
- 英语/简中旧资源各有 30 个 fluid 键；目标 addon 当前还没有这些键。

## Requirements

### R1 数据、注册与兼容

- 注册唯一 form id `bbs:fluid`；若该 id 已被其他类型占用，保留外部 owner、记录明确告警，不静默覆盖。
- 保留 19 个 value 的旧 id、类型、默认值和 `mode` 序号语义，使旧版保存数据可直接读取，clone/保存/读取不丢字段。
- common 侧 `FluidForm` 和注册路径不得引用 Minecraft client、renderer、UI 或 addon client 包；专用服务端不能因 S9 类加载失败。
- renderer 和 editor factory 在 client 构造期幂等注册；Extra 分类中的示例 Fluid Form 在资源/分类初始化后幂等加入，不移植 `ExtraFormSectionMixin`。

### R2 三种模式的可见行为

- `FULL_OCEAN` 渲染受物理扰动驱动的水面，使用 `sizeX/sizeZ`、`waveAmplitude`、`flowSpeed`、`turbulence`、纹理、颜色、不透明度、细分和平滑着色。
- `PROCEDURAL` 渲染确定性的多频波浪并叠加物理扰动，`waveFrequency` 必须产生可观察差异。
- `WATER_DROP` 渲染球形液滴，`dropSize`、`surfaceTension`、`viscosity` 和 `flowSpeed` 必须产生可观察差异。
- `FULL_OCEAN` 的 `fillBlock=true` 必须使用 `sizeY` 生成顶面以下的四侧面和底面；`fillBlock=false` 仅渲染顶面。该修复是旧公开功能的补全，不扩展为体积流体求解器。
- 三种模式都必须正确使用所选纹理、ARGB 颜色、独立 opacity、light/overlay 和 Form transform；透明渲染后必须恢复调用者的 GL 状态。

### R3 模拟、时间与交互

- 模拟只在拥有该 Form 的 tick/lifecycle 路径按固定步推进；同一帧的 UI、阴影、Iris、motion-blur 或重复 render pass 不得重复推进状态。
- 程序波和液滴相位优先使用 Film `timelineTick`，其他上下文使用实体/renderer 的逻辑 tick；禁止 `System.currentTimeMillis()`、`nanoTime()` 和 `Math.random()` 参与可见结果。
- 暂停 Film 时相位和物理状态不得因继续重绘而前进；相同初始值、tick 序列和交互输入必须得到相同模拟结果。
- controller 使用语义世界/稳定局部空间和具体 simulation owner，不把相机/view matrix 写入跨帧历史。
- 默认交互采样世界实体和 Film replay；`fluidRealisticModelInteraction=false` 使用实体/根骨骼级采样，开启后对标准 Cubic cube/mesh 做详细采样，BOBJ 明确回退到根级采样。
- 奇异矩阵、null world、缺失 renderer、缺失 owner、空 controller 列表和已经销毁的实体必须安全跳过，不崩溃。

### R4 有界性能与资源生命周期

- 模拟网格、渲染网格、世界/Film 实体数和详细模型采样数必须由单一预算 owner 约束；用户参数只能降低或在预算内提升质量，不能突破硬上限。
- 目标初始预算为每轴最多 256、总计最多 65,536 个模拟单元和 65,536 个渲染 quad；详细交互最多 2,048 个 sample/tick，并对单实体设子上限。若实测仍超预算，可在不改变序列化格式的情况下下调。
- 超出预算时按长宽比降采样，不裁切几何范围；默认 `10 x 10`、subdivision 1 的视觉密度保持旧版基线。
- 网格尺寸或模式变化可以重建模拟一次；稳定运行时不得每帧创建与网格规模成正比的临时对象或保留关闭 debug 后的 sample。
- renderer `release()`、Form 替换和客户端关闭后不得遗留模拟、debug 或 controller-owned 状态。

### R5 编辑器、预设与本地化

- `UIFluidForm`/`UIFluidFormPanel` 提供三种 mode、19 个 value、纹理选择、带 alpha 的颜色选择，以及 Custom/Calm Ocean/Stormy Ocean/Viscous Drop 四个预设入口。
- mode 切换必须正确启用/禁用 Ocean、Drop 和 Procedural 专属控件；不能把不适用字段从序列化数据中删除。
- 三个非 Custom 预设必须以一次用户动作更新目标 values，并立即刷新面板，不产生回调递归或部分更新状态。
- 迁移旧版 30 个英语/简中 fluid 键，键集合和格式占位符保持对称；不建立第二个 fluid 翻译 namespace。

### R6 验证与范围保护

- 严格 runtime verifier 覆盖 form/factory/category/UI 构造、19 值往返、mode/preset、模拟确定性和边界、交互回退、预算、debug 清理及关键渲染分支。
- `verifyBbsCoreJar build` 必须真实退出 0；JAR 含 S9 common/client 类和两种语言资源，不内嵌 `mchorse/bbs_mod/**`，不含 Fabric/Yarn 旧引用。
- 严格客户端必须实际创建 `bbs:fluid` renderer/editor 并进入至少一个真实 mesh draw 分支；只有类存在或注册成功不算渲染验收。
- 不修改或提交 `new/` 与 `bbs_FSloveCML_1.21.1/`；需要主 mod 新 API 时停止并单独申请，不以隐式 core 改动解决。

## Acceptance Criteria

- [ ] AC1 `bbs:fluid` 的 common factory、client renderer、editor factory 和 Extra 分类示例均注册且幂等；专用服务端类加载边界通过。
- [ ] AC2 19 个 value 的默认值、非默认值 clone/serialization 往返和旧 mode 序号兼容通过，未知/越界 mode 安全钳制。
- [ ] AC3 FULL_OCEAN、PROCEDURAL、WATER_DROP 均产生非空有限几何，所有对应参数在自动或人工矩阵中有可观察效果。
- [ ] AC4 `sizeY/fillBlock` 不再是无效控件：关闭时只有水面，开启时生成正确深度的封闭盒状体积外壳。
- [ ] AC5 模拟按逻辑 tick 确定推进；暂停/重复 render pass 不推进，固定输入可复现，网格/采样永不超过预算。
- [ ] AC6 世界实体和 Film replay 能扰动水面；真实 Cubic setting 分支与 BOBJ/root fallback 均安全，debug 关闭后不保留旧 sample。
- [ ] AC7 UI 三种 mode、四个 preset、纹理/颜色及 19 个字段可操作；英语/简中 30 个键存在、对称并在运行时解析。
- [ ] AC8 构建、JAR 审计和严格客户端退出码为 0，日志无 Mixin/classloading/renderer/GL 错误；S9 人工视觉矩阵登记到 S10。
- [ ] AC9 本任务对 `new/`、旧源码树和用户现有四个无关 spec 修改的 diff 均为 0。

## Out of Scope

- GPU compute、Navier-Stokes/体积 CFD、方块级水传播或真实液体碰撞。
- BOBJ VAO 网格读回或为详细 BOBJ 交互新增主 mod API。
- 新增第四种流体模式、粒子泡沫、折射/反射 shader 或旧版没有的材质系统。
- 修改 BBS 主 mod、S7、S10/Premiere 或其他 addon 功能。

## Planning Review Decision

实施激活前需确认：接受“保留旧数据与主要视觉语义，但修复无效 `fillBlock/sizeY`、改为逻辑 tick 的确定性时间并施加硬性能预算”，而不是逐行复刻旧版的无界/墙钟行为。推荐接受该安全迁移方案。
