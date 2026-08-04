# S9 技术设计：流体模拟 Form

## 1. 交付边界

S9 以旧源码树的 7 个 fluid owner 为行为基线，在 addon 内按 common/client 重新分层：

```text
bbs-snow-addon/src/
|-- main/java/mchorse/bbs_snow/
|   |-- forms/FluidForm.java
|   `-- BBSSnowAddon.java                         # RegisterFormsEvent
`-- client/java/mchorse/bbs_snow/
    |-- forms/renderers/FluidFormRenderer.java
    |-- simulation/{FluidSimulation,FluidController}.java
    |-- ui/forms/{FluidUIKeys,UIFluidForm,UIFluidFormPanel}.java
    |-- BBSSnowModClient.java                     # renderer/editor/category
    `-- verification/BBSSnowRuntimeVerification.java
```

实际可增加一个很小的 `FluidMeshBudget`/grid helper 作为预算与 verifier 的单一 owner；不得为了匹配旧目录数量复制无调用者类。现有 `FilmsControllerAccessor` 直接复用，不增加第二个 Films accessor 或 ExtraFormSection Mixin。

## 2. 注册与类加载

### 2.1 Common

`BBSSnowAddon` 增加 `@Subscribe onRegisterForms(RegisterFormsEvent)`。注册前读取 `event.forms.getTypeClass(Link.bbs("fluid"))`：空位时注册 `FluidForm.class`；相同 class 视为幂等；不同 class 只告警并保留现有 owner。

`FluidForm` 只依赖 BBS common value/Form/Link API。19 个 value 以旧 id 和顺序定义，`getFluidMode()` 对反序列化后的整数做钳制。客户端 renderer 仍由 BBS 的 Form runtime 懒挂载，不在 common 构造器中触碰 client 类。

### 2.2 Client

`BBSSnowModClient` 的构造期调用一个幂等 `registerFluidClient()`：

- `FormUtilsClient.register(FluidForm.class, FluidFormRenderer::new)`；
- `UIFormEditor.register(FluidForm.class, UIFluidForm::new)`；
- 若已有不同 factory，记录冲突并保留 foreign owner，不静默替换。

`onClientStarted` 在 source pack 和 form categories 已可用后加入一个 `new FluidForm()`。使用 addon 自己保存的 exemplar 引用和布尔/identity guard 避免 lifecycle 重放导致重复项；不反射 `ExtraFormSection`。

## 3. 数据流与状态所有权

```text
FluidForm serialized values
  -> renderer-owned FluidSimulation + deterministic clock
  -> tick: ensure grid -> collect bounded interactions -> add forces -> one fixed step
  -> render: choose logical phase -> read simulation -> emit bounded mesh
  -> UI: edit the same value tree -> BBS save/clone/keyframe paths
```

- `FluidForm` 是持久化配置 owner。
- 每个 `FluidFormRenderer` 拥有自己的 simulation、controller、deterministic PRNG/state counter 和 debug snapshot；不同 Form 不共享可变水面。
- `IEntity`/`context.simulationOwner` 决定跨帧历史归属。UI、物品和 preview 使用稳定局部 owner，不扫描世界；世界/Film entity 使用语义 world matrix。
- render pass 是 simulation 的只读 consumer：不能调用 `step()`、随机加力或按墙钟判断更新。配置/owner/时间线发生明确 discontinuity 时允许在受控入口重建/reset，但一次 render pass 的次数不能改变最终状态。

## 4. 时间与确定性

`FluidSimulation.step()` 是固定离散更新。renderer 维护整数 simulation tick；`tick(IEntity)` 每次最多推进一次。湍流事件从固定 seed、simulation tick 和网格尺寸派生，不使用全局 `Random` 或 `Math.random()`。

渲染相位选择顺序：

1. 有效 `FormRenderingContext.timelineTick`：直接用 timeline tick 计算 procedural/drop phase；
2. 世界实体：使用 entity age + transition；
3. UI/preview：使用 renderer 的逻辑 tick + transition。

Film 暂停时 `timelinePlaying=false`，render 只重读同一 phase，tick 侧不为该 timeline owner推进可见模拟。向后 seek、owner 变化、mode/grid 改变触发 deterministic reset，避免保留来自未来帧的水面。FULL_OCEAN 的交互水波仍是有状态模拟；验收保证相同顺序输入可复现，不承诺从任意中间帧无预滚直接重建完整历史。

## 5. 模拟与网格预算

预算常量集中在一个 owner，并由 runtime verifier 直接读取：

| Budget | 初始目标 |
|---|---:|
| simulation max axis | 256 |
| simulation max cells | 65,536 |
| render max axis | 256 |
| render max quads | 65,536 |
| interaction samples/tick | 2,048 |
| per detailed model | 512 |
| world/Film entity candidates | 分别设有限子预算 |

网格规划器先按旧密度（ocean `size * 8`、procedural/drop `16 * subdivisions`）得到期望值，再按 max-axis 和 total-cell budget 等比缩放，至少保留合法的边界单元。几何范围仍覆盖完整 `sizeX x sizeZ`；预算只改变步长，不缩小流体。

`FluidSimulation` 使用两个复用 `float[]` buffer，稳定尺寸不分配。重建只发生在实际计划网格变化时；粘度/阻尼在 step 前从 sanitized runtime 参数更新。所有采样与高度查询对边缘安全钳制，法线退化时回退 `(0,1,0)`，不得输出 NaN/Infinity。

## 6. 三种几何语义

### 6.1 FULL_OCEAN

顶面按 simulation height 双线性/平滑插值生成。subdivisions 提升期望 render density，但最终受预算裁剪。turbulence 通过 deterministic force 进入 simulation。

`fillBlock=false` 只发射顶面。`fillBlock=true` 额外沿顶面四条边发射到 `y=-abs(sizeY)` 的侧面，并发射底面；侧面顶部跟随边缘波高，底部保持平面。这样 `sizeY` 和 `fillBlock` 具备可验证效果，但内部仍不做体积求解。

### 6.2 PROCEDURAL

保留旧多频正弦波与 turbulence 频段，使用逻辑 phase，并叠加 simulation height。法线由相邻逻辑采样或解析差分计算。`waveFrequency` 仅作用于 procedural 分支；UI 在 FULL_OCEAN 中禁用它。

### 6.3 WATER_DROP

保留经纬球拓扑、surface tension/viscosity wobble 和 simulation displacement。stacks/slices 经过同一预算 planner。平滑/平面法线都要处理极点退化，不在 inner loop 创建临时 vector 数组。

## 7. 交互控制器

controller 每 tick 构造一个 identity-deduped controller snapshot：现有 `FilmsControllerAccessor` 提供正在播放 controller，打开的 Film editor controller 仅在不重复时加入。先找 owning controller，再用 `BaseFilmController` 的当前 Mojmap/FSR matrix API计算 anchor 后的 surface semantic matrix。

世界实体查询只在 `entity.getWorld()!=null`、surface matrix 可逆且当前 owner 允许 world interaction 时进行。候选 AABB 由流体 world-space X/Z 轴长度决定；超过候选预算后停止采样。

Film replay 路径：

- 跳过 fluid owner 自身、null form、null renderer 和已经处理的实体；
- `fluidRealisticModelInteraction=false`：标准 Cubic 取首个有效 root/group，BOBJ 取 root bone，其他 Form 取可用 root matrix；
- setting 开启：标准 Cubic cube 采 corner/face center，mesh 采三角顶点/centroid，但按单模型和全局 budget 截断；
- BOBJ 保持 root fallback，不读取 VAO/GPU mesh。

debug snapshot 只复制最终接受的 bounded sample。debug 从 true 变 false 时立即 clear；renderer release 也 clear。

## 8. 渲染 API 与状态事务

Yarn 类型按 1.21.1 Mojmap 适配：`Minecraft`、`PoseStack`、`ShaderInstance`、`DefaultVertexFormat`、`Tesselator`、`VertexFormat.Mode`、`BufferUploader`、`Vec3`、`AABB`、`Level`。

renderer 对照当前 Billboard/Trail/FormRenderingContext API：

- UI 用 `ModelFormRenderer.getUIMatrix` 和 `PoseStack`；
- world 使用 `FormRenderingContext.stack/world/renderSpace`，不从 camera-relative stack 反推 simulation history；
- 纹理从 BBS texture manager 解析，缺失时使用 addon/core white fallback；
- lightmap、overlay、blend、cull、depth 和 shader color 的修改放在平衡的 `try/finally` 事务中；
- Iris shadow pass、picking、inventory/UI 和 world translucent queue 遵守当前 `FormRenderer` contract，不把 debug line 写入普通 picking id。

## 9. UI 与预设

`UIFluidForm` 使用当前 `UIForm`/`UIFormPanel` 模式注册默认 panel 和 general panel。布局沿用旧分区，但使用当前主题语义控件，不硬编码 UI 语义色。

预设以一个方法批量写目标 values，再调用面板 refresh；`setValue` 只同步 widget，不逐个 notify 形成递归。Custom 不改值。mode visibility 规则：

- FULL_OCEAN：ocean + fillBlock；
- PROCEDURAL：ocean + waveFrequency，不启用 fillBlock；
- WATER_DROP：drop controls。

当前 BBS Form panel 本身直接写 value tree，没有通用 undo transaction API；S9 不伪造第二套 undo history。预设原子性通过单 callback 和完整刷新保证。

## 10. 验证与回滚

严格 verifier 分四层：

1. registry/data：id、factory、19 values、clone/data roundtrip；
2. simulation/controller：determinism、pause/no-render-step、budget、singular/null、realistic/root fallback；
3. UI/resources：panel、mode/preset、关键 widget、30x2 l10n；
4. render consumer：三种模式非空/有限 vertex 计数、fillBlock 几何差异、至少一次真实 draw branch。

回滚 S9 只需删除 fluid factory/client/category 注册、S9 类和 30 个翻译键，并移除 verifier 分支。现有 S2 setting/accessor可继续保留且不会在没有 Fluid Form 时执行。任何需要改 `new/` 的发现都先回滚到 addon-only checkpoint，再请求单独授权。
