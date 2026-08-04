# 技术设计：结构性能力光影式热重载

任务：`.trellis/tasks/07-23-fsr-plugin-structural-hot-reload`
前置阅读：`prd.md`、`.trellis/spec/new/backend/hot-plugin-runtime-contract.md`、上一期 `.trellis/tasks/07-21-fsr-plugin-hot-reload/design.md`

## 1. 设计总览

一句话：把六个结构性注册面（forms/clips/particles/keymappings/entity renderer/block-entity renderer）改造成 **owner-aware 世代化注册表**，并在现有换代流程外围加一个 **光影式重载协调器**（snapshot → teardown → publish → rebuild → refresh），使插件的一整代结构性贡献可以像 Iris 重载光影那样被完整销毁重建。

不改变的东西（上一期合同全部保持）：

- artifact 校验 / shadow copy / watcher / 串行生命周期线程 / candidate-first / 原子 root swap / lease-drain / ledger 反向清理 / `RESTART_REQUIRED` 断路器 / 诊断状态机。
- Addon API 2.0 启动期语义、v1/v2 二进制合同、frozen network id。
- 结构性 jar entry（mixin/AT/coremod/native/NeoForge metadata）与 minecraft_registry/jni/native capability 的拒绝逻辑。

新增/修改的东西：

| 层 | 内容 |
| --- | --- |
| API（SPI） | `BBSPluginContext` 新增 `forms()/clips()/particles()`；客户端 context 新增 `keyMappings()/renderers()`；接口形状对齐 `BBSFormRegistry`/`BBSClipRegistry`/`BBSParticleRegistry`/`ClientApiCompat` 注册签名 |
| capability | `BBSPluginCapability` 六项翻转 hotSafe=true；`PluginArtifactValidator` 相应放行 |
| 宿主注册面 | `MapFactory` 撤销修复 + owner-aware 包装；`FormUtilsClient`/form editors/`FormCategories`/keymapping/renderer 世代化 |
| 运行时 | 新增 `PluginStructuralReloadCoordinator`（光影式重载编排）+ 主线程安全点栅栏 + 实例 snapshot/rebuild + 客户端投影刷新 |
| 降级 | 缺失类型占位（form）/ 跳过（clip、particle）+ 数据保真回写 |

## 2. 关键决策与理由

### 2.1 为什么是"光影式"（全量重建）而不是增量迁移

- form/clip 天然 `MapType` 数据驱动，"序列化 → 销毁 → 重建"与打开存档共用同一条代码路径，正确性由既有路径背书；实例级 class-to-class 迁移则需要为每种类型写迁移器，复杂度不可控。
- 旧代 classloader 必须不可达才能回收；任何"旧实例继续存活"的方案都会让插件 Class 逃逸。全量重建从机制上保证换代后只有新代 Class 存活。
- 用户已明确同意重载瞬间的"闪断"观感（与光影重载一致）。

### 2.2 世代化注册表的形状

不发明新注册表类型，而是给现有注册面加 **owner-aware 包装层**（宿主内置注册不动、无 owner）：

```java
/* 概念形状，命名实现期可调 */
final class GenerationScopedRegistrar
{
    /* 每次注册记 (owner, key, 恢复动作)，塞进该 generation 的 PluginContributionLedger；
       ledger 反向清理时执行撤销。注册入口只在 prepare 阶段开放（复用 events 的 seal 模式）。 */
}
```

- common 侧落点：`FormArchitect`（`BBSMod` 持有的实例）、camera/action 两个 `MapFactory<Clip, ClipFactoryData>`、particle 组件表。
- `MapFactory.unregister(String)` 的 String/Link bug（`factory.remove(key)` 用 String 匹配 `Link` 键永不命中、`data.remove(clazz)` 键类型也错）：新增 `unregister(Link)` 正确实现；旧方法保留并委托（不破坏二进制兼容）。
- 客户端侧落点：`FormUtilsClient.map`（Class→IFormRendererFactory）、form editor 注册表（Phase 0 盘点具体类）、`FormCategories`（重载后刷新分类/最近列表，剔除或恢复插件 form 条目）。

### 2.3 keymapping / renderer：从"一次性窗口"到"可重放注册表"

`ClientApiCompat` 的 `StructuralRegistrationWindow` 是启动期一次性消费模型（`RegisterKeyMappingsEvent`/`EntityRenderersEvent` 消费后关闭）。热插件不能走它。方案：

- 新建客户端 **世代化重放注册表**（keymapping、entity renderer、block-entity renderer 各一）：记录 `(owner, registration)`；代际变更后在主线程安全点"重放"到 Minecraft 运行时。
- keymapping 重放 = 运行期把 `KeyMapping` 加入/移出 `Minecraft.options.keyMappings` 数组并触发按键索引重建（`KeyMapping.resetMapping()` 一类）；具体 NeoForge 1.21.1 落点由 Phase 0 研究确定（含 options.txt 持久化行为：移除后保存不得丢失用户对该键的绑定数据——若无法保证则由宿主自行持久化插件键位绑定）。
- renderer 重放 = 换代后对 `EntityRenderDispatcher`/`BlockEntityRenderDispatcher` 做受控重建或定向替换（两个候选：触发一次 dispatcher 级 reload——与资源重载同路径、成本高但稳；或仅替换受影响 EntityType 的 renderer 条目——成本低，需确认 `EntityRendererProvider.Context` 运行期可构造）。Phase 0 出结论，默认取"与资源重载同路径"的稳妥方案。
- 启动期语义等价：游戏启动时若插件已在目录（reconcile 加载），注册表在 vanilla 窗口事件时机同样把贡献重放进去（或在窗口后走同一条运行期重放路径），保证"启动期在目录"与"运行期放入"结果一致（prd 验收第 2 条）。
- 边界：renderer 只能挂到**已存在的** EntityType/BlockEntityType 上（MC registry 冻结）；SPI 文档写明。

### 2.4 实例 snapshot / rebuild（光影式的核心步骤）

受影响实例 = 其类型 `Link`（form/clip）或组件（particle）归属于"即将被撤销的 generation"的运行实例。

- **判定**：换代前从旧代 ledger 拿到该插件注册的类型集合 `S`；扫描持有点中类型 ∈ S 的实例。
- **snapshot**：对每个受影响实例调用既有序列化路径（form：`FormUtils.toData`；clip：clip 的 `toData`），记录 `(持有点定位, MapType)`。
- **teardown**：撤销注册（ledger 反向）+ 把持有点上的实例替换为占位/置空。
- **rebuild**（新代 publish 之后）：对每条 snapshot 用新代注册表反序列化重建（form：`FormUtils.fromData`；clip：factory `fromData`），写回持有点；类型在新代不存在 → 留在占位态（§2.5）。
- **持有点清单**（Phase 0 必须全量盘点并写入 research/，已知起点）：
  - film/replay 数据模型中的 form（replays 的 form 属性、keyframe 内嵌 form）；
  - film timeline 的 camera/action clips；
  - UI 打开态：`UIFilmPanel`、form 编辑器当前编辑对象、`FormCategories` 的 recent/user 分类引用；
  - 播放/预览运行态：ActionPlayer、粒子发射器（使用插件 particle 组件的 emitter）；
  - dashboard 之外的散点（morph/model editor 等）以 Phase 0 盘点为准。
- **豁免**：kind=content 插件与八项既有 hotSafe 能力不进 snapshot 流程（它们没有结构性类型贡献），保持上一期行为。

### 2.5 缺失类型降级（卸载/新版缩减能力）

- 数据侧：**永不剥离**。反序列化遇到未注册类型时保留原始 `MapType`（确认既有缺 addon 容错路径的行为；若现状是丢弃/异常，则补"未知类型数据保持器"）。保存时原样写回，插件恢复后完整回读。
- 呈现侧：form → 缺失占位（不渲染或渲染占位标识，编辑器显示"缺失插件类型"）；clip → timeline 上保留块但跳过执行；particle → emitter 停止该组件。
- 全链路（打开/播放/保存 film）不得因缺失类型抛异常。

### 2.6 重载协调器与线程模型

新增 `PluginStructuralReloadCoordinator`（名字可调），挂在 `BBSPluginManager.applyPath` 的换代流程上：

```
生命周期线程                                  客户端主线程
─────────────                                ─────────────
validate + shadow + prepare candidate
（结构性注册进 candidate ledger，staged，
  尚未对宿主生效）
        │
        ├── 提交"安全点任务"并等待 ──────────▶ tick 边界安全点：
        │                                     1. guard 检查（导出/录制中 → 拒绝，
        │                                        状态=RELOAD_REJECTED_BUSY，保旧代）
        │                                     2. snapshot 受影响实例
        │                                     3. teardown 旧代结构性贡献（ledger 反向）
        │                                     4. 原子 publish（现有 root swap）
        │                                     5. commit 新代结构性注册到宿主注册面
        │                                     6. rebuild 实例 + 重放 keymapping/renderer
        │                                     7. UI 刷新（FormCategories/clip picker/按键界面）
        ◀── 安全点任务完成 ───────────────────┘
drain 旧代 lease → stop → ledger close →
classloader close（现有流程）
```

- 结构性 3~6 步必须在**同一个主线程安全点内完成**（一个 tick 边界任务），杜绝"类型已撤销但实例还在渲染"的中间帧。
- dedicated server 没有客户端投影：同流程去掉 renderer/keymapping/UI 步骤，snapshot/rebuild 仅覆盖服务端持有点。
- 与现有合同的衔接：candidate prepare 失败仍在生命周期线程内闭环（保旧代，不进安全点）；安全点内 teardown/publish 之后的 rebuild 失败**不回滚换代**（新代已生效），失败实例进占位 + 诊断（对齐"光影重载失败显示错误但不恢复旧 pipeline"的心智模型；比试图回滚半个世界更可靠）。
- 导出/录制 guard：`VideoRecorder`/`VideoExportSession` 活跃时拒绝换代（记 `RELOAD_REJECTED_BUSY`，watcher intent 保持 pending，导出结束后 reconcile 重试）。

### 2.7 SPI 形状（对齐 Addon 2.0）

```java
public interface BBSPluginContext
{
    /* 既有：descriptor/generation/dataDirectory/diagnostics/events/own */
    BBSPluginFormRegistry forms();       // register(Link id, Class<? extends Form>)
    BBSPluginClipRegistry clips();       // registerCamera/registerAction(Link, Class, ClipFactoryData)
    BBSPluginParticleRegistry particles();
}

/* 客户端 entrypoint 专用（dedicated server 不解析） */
public interface BBSPluginClientContext extends BBSPluginContext
{
    BBSPluginKeyMappingRegistry keyMappings();   // register(KeyMapping)
    BBSPluginRendererRegistry renderers();       // entity/blockEntity renderer + form renderer factory + form editor
}
```

- 注册仅 `prepare` 阶段开放（复用 events 的 `sealEvents` 模式，prepare 返回后 seal）；每次注册进 candidate ledger。
- 客户端 context 的引入方式对齐现有 client entrypoint 机制（`BBSPluginManifest.clientEntrypoint` 实例收到的 context 即 client 版），不新增 NeoForge 类型到 SPI。
- capability 声明与 facade 使用校验：manifest 未声明对应 capability 时 facade 抛 `IllegalStateException`（与 addon 2.0 的 capability gating 一致；Phase 0 确认 addon 侧是否有此 gating，保持同一严格度）。

### 2.8 validator / capability 翻转

- `BBSPluginCapability`：六项 `hotSafe` 改 true。枚举顺序、wireName 别名不变（二进制/wire 兼容）。
- `PluginArtifactValidator`：`CAPABILITY_RESTART_REQUIRED` 分支自然放行六项；`RELOAD_RESTART`（manifest 自声明 restart）与 `STRUCTURAL_ENTRY`（jar 内 mixin/AT/coremod/native/NeoForge metadata）逻辑不动。
- `minecraft_registry`/`jni`/`native_library`/`mixin`/`access_transformer`/`coremod` 仍 hotSafe=false。

## 3. 失败矩阵（增量部分）

| 条件 | 要求结果 |
| --- | --- |
| candidate prepare 中结构性注册抛异常 | candidate ledger 反向清理，旧代保持 ACTIVE（沿用现有合同） |
| 安全点 guard 命中（导出/录制中） | 拒绝换代，状态 `RELOAD_REJECTED_BUSY`，旧代不动，intent 待重试 |
| snapshot 单实例序列化失败 | 该实例按"缺失占位"处理 + 诊断；不阻塞其余流程 |
| teardown/publish 之后 rebuild 单实例失败 | 占位 + 诊断；换代不回滚；其余实例继续重建 |
| 新代不再提供某类型 | 存量数据保留原样，呈现降级（占位/跳过），保存回写不丢字段 |
| keymapping/renderer 重放失败 | 诊断 + 该项贡献不生效；不崩溃、不阻断其他贡献 |
| 卸载后插件 Class 残留静态缓存 | 视为泄漏缺陷；上一期 `SUSPECTED_LEAK` 遥测口径覆盖，测试断言缓存清零 |

## 4. 兼容性与回滚

- 全部改动限于 `new/` 仓库；SPI 只增不改（additive），Addon API 2.0 与既有热插件 8 项能力回归不变。
- 阶段回滚点 = 每 Phase 一个 commit（implement.md）；capability 翻转放在最后阶段提交，翻转前所有基建可独立合入且对现网行为零影响（六项能力仍被拒），天然灰度。
- 风险最高的两块（keymapping options 持久化、renderer 运行期重建）都有降级方案：keymapping 由宿主侧自行持久化绑定；renderer 取"与资源重载同路径"的全量重建。

## 5. 测试策略

- `pluginHotReloadTest` 套件扩展：世代化注册表单元测试（注册/撤销/重放/基线回归）、snapshot-rebuild 往返测试（form/clip 数据保真）、缺失降级测试、100 次交替 reload 泄漏测试（沿用既有 fixture 机制，`FixtureJarBuilder` 增结构性能力 fixture）。
- `testClientStructuralRegistration` 扩展：一次性窗口与世代化注册表并存时启动期语义等价。
- 回归门：`testAddonLifecycle`、`testBinaryCompatibility`、`compileJava`、`compileClientJava`、`pluginHotReloadTestClasses`。
- 手动 smoke（NeoForge client + dedicated server）：prd 验收清单逐条，含真实 film 中换代/卸载/恢复。
