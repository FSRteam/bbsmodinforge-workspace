# FSR 插件结构性能力光影式热重载

## Goal

消除上一期（`07-21-fsr-plugin-hot-reload`）遗留的能力覆盖缺口：当前 FSR 热插件运行时把 forms、clips、particles、key mappings、entity renderer、block-entity renderer 六项能力一律判为结构性并拒绝加载（`RESTART_REQUIRED`），导致这类插件既不能热重载、也无法通过热插件通道在启动期加载，只能退回启动期 Addon / NeoForge mod 路线。

本期目标（用户判据）：**凡是"退出游戏、放入插件、重启后能通过 FSR/Addon 通道运行"的插件能力，热插件运行时都必须允许运行期加载与热重载。** 重载模型采用用户点名同意的"光影包式"方案：像 Iris 重载光影那样，对插件的一整代贡献做完全 teardown 再从数据重建，不做实例级增量迁移。

## Confirmed Facts

- 目标运行时：Minecraft 1.21.1、NeoForge 21.1.x、Java 21；`new/` 为独立 git 仓库。
- 上一期已交付的基建可直接复用：generation classloader、`PluginContributionLedger`（反向清理）、`ActivePluginIndex`（原子 root swap + lease/drain）、shadow artifact store、目录 watcher、串行生命周期控制器、诊断状态机（见 `.trellis/spec/new/backend/hot-plugin-runtime-contract.md`）。
- `BBSPluginCapability` 当前 hotSafe=false 的 12 项中，前 6 项（forms/clips/particles/key_mappings/entity_renderer/block_entity_renderer）是 Addon API 2.0 启动期就支持的宿主能力；后 6 项（minecraft_registry/mixin/access_transformer/coremod/jni/native_library）连 Addon API 2.0 都不支持，属于 NeoForge mod 层的启动期机制。
- `PluginArtifactValidator` 对非 hotSafe capability 与结构性 jar entry（mixin json、AT cfg、coremod、native、NeoForge metadata）一律返回 `RESTART_REQUIRED`，启动期 reconcile 与运行期走同一条拒绝路径。
- Addon API 2.0 的启动期注册面：`BBSAddonRegistrationContext`（resources/forms/settings/clips/particles/network/events）+ 客户端 `ClientApiCompat` 的一次性 `StructuralRegistrationWindow`（KeyMapping、EntityRenderer、BlockEntityRenderer），窗口关闭后拒绝注册。
- 宿主注册面现状（本期要世代化的对象）：
  - `FormArchitect extends MapFactory<Form, Void>`（common，Link→Class）；`MapFactory.unregister(String)` 存在但有 bug——用 String 去 remove `Link` 键永远不命中，实际没有可用撤销路径。
  - camera clips / action clips：两个 `MapFactory<Clip, ClipFactoryData>`。
  - 客户端 `FormUtilsClient.map`：静态 `Map<Class, IFormRendererFactory>`，持有插件 Class 即 classloader 泄漏点；另有 form editor 注册与 `FormCategories`（UI 分类）。
  - particles：`BBSAddonManager.particleComponents()` 路径 + `ParticleManager`（文件驱动资源）。
- BBS 的 form/clip 本质是 `MapType` 数据驱动（存档即数据），实例可从数据重建——这是光影式"数据外置 + 重建"可行的根本前提。
- EntityType/BlockEntityType 注册属于 Minecraft registry（冻结）；热插件的 renderer 能力只能为**已存在的**类型注册/覆盖 renderer，不能新增类型。Addon API 2.0 同样受此限制。

## Product Positioning

- 本期是 FSR Hot Plugin Runtime 的第二期，扩展能力面而非另起系统；上一期的 artifact/watcher/generation/ledger 合同全部保持。
- "光影式重载"定义：换代时旧代的全部结构性贡献被完整撤销、受影响的运行实例被序列化为数据后销毁，新代发布后从数据重建实例并刷新客户端投影（renderer/keymapping/UI）。重载瞬间允许出现一次可感知的"重建闪断"（与光影重载观感一致），换取模型简单与可靠。
- 硬边界（保持 `RESTART_REQUIRED`，且与用户判据自洽——这些能力重启游戏后也无法经 FSR/Addon 通道运行，必须安装成 NeoForge mod）：Minecraft registry 新增、Mixin、access transformer、coremod、JNI/native library。
- 热插件仍是本机完全信任代码，不是安全沙箱。

## User-visible Experience

- 把带 forms/clips/particles/keymapping/renderer 能力的插件包放入 `config/bbs/plugins/`：游戏运行中自动加载，form 出现在 form 列表、clip 出现在 clip picker、按键出现在按键设置、renderer 生效，无需重启。
- 覆盖同 id 新版本：场景/film 中正在使用的插件 form/clip 实例被自动序列化并用新代类型重建，数据（keyframe、属性）保留；观感类似按下光影重载键。
- 删除插件包：正在使用的实例降级为"缺失类型"占位（与打开缺 addon 存档的行为一致），数据不丢失、游戏不崩溃；重新放回插件后实例恢复。
- 失败反馈：候选新版本准备失败时旧版本继续工作；重建阶段失败产生占位 + 诊断而非崩溃；诊断面板可看到重载各阶段状态。

## Requirements

### Capability 翻转与校验

- `BBSPluginCapability` 中 forms、clips、particles、key_mappings、entity_renderer、block_entity_renderer 六项翻转为 hotSafe；validator 不再因这些 capability 拒绝。
- 结构性 jar entry 扫描（mixin/AT/coremod/native/NeoForge metadata）与后 6 项 capability 的拒绝逻辑保持不变。
- manifest 显式声明 `reload=restart` 的插件仍按作者意愿要求重启。

### 宿主注册面世代化

- 六个注册面全部改为 owner-aware：注册绑定 `(pluginId, generation)` 并记入 ledger，撤销走 ledger 反向清理；宿主内置注册（非插件）不受影响。
- 修复或绕过 `MapFactory.unregister` 的 String/Link bug，提供真正可用的撤销路径。
- 客户端持有插件 Class 的静态缓存（`FormUtilsClient.map`、form editors、`FormCategories` 及实现期盘点出的其他缓存）必须全部纳入世代化撤销，teardown 后不残留插件 Class 引用。
- 客户端 keymapping/renderer 由一次性 `StructuralRegistrationWindow` 模型升级为"可重放"注册表：每次代际变更后在主线程安全点重放（注册/撤销/重建 renderer、重排 keymapping），启动期行为与现有窗口语义等价。

### SPI 扩展

- `BBSPluginContext`（及客户端侧 context）新增 forms/clips/particles/keyMappings/renderers 注册 facade；接口形状对齐 Addon API 2.0 的对应 registry 接口，降低 addon→hot plugin 迁移成本。
- facade 仅在 `prepare` 阶段开放注册（对齐现有 events 的 seal 模式）；所有注册返回 ledger 所有的可撤销句柄。
- 不暴露 NeoForge bus/registry 类型；dedicated server 不解析 client-only facade。

### 光影式重载协调器

- 换代流程扩展为：candidate prepare（结构性注册 staged）→ 主线程安全点栅栏 → 受影响实例 snapshot（序列化为 MapType）→ 旧代结构性贡献 teardown → 原子 publish → 从 snapshot 重建实例 → 客户端投影刷新（renderer 重建、keymapping 重排、form/clip/按键 UI 刷新）。
- snapshot/rebuild 只针对受影响实例（使用了将被替换/移除类型的实例）；判定按类型归属，不做逐字段 diff。
- 候选失败保留旧代（沿用现有合同）；rebuild 单实例失败降级为缺失占位 + 诊断，不中断其余重建、不崩溃。
- 正在进行视频导出/录制时的换代按"拒绝并提示稍后重载"处理，不在导出中途换代。
- 结构性能力插件在游戏启动期（reconcile）加载时走同一条路径，客户端投影在 UI 初始化时自然读到已注册贡献。

### 失效降级

- 插件卸载或新版本不再提供某类型时，引用该类型的存量数据保持原样存储；运行侧以缺失占位呈现（form）或跳过执行（clip/particle），与现有"打开缺 addon 存档"的容错路径一致；不得抛异常中断 film 打开/播放/保存。
- 保存路径必须保留缺失类型的原始数据（不因缺失而剥离），插件恢复后数据完整回读。

### 兼容与回归

- Addon API 2.0 启动期行为、现有 v1/v2 二进制合同、frozen network id 不变。
- 上一期 8 项 hotSafe 能力（events/network/resources/settings/ui_mirror/render_surface/film_collaboration/executors）行为回归不变。
- 上一期全部生命周期合同（candidate-first、原子 swap、drain、`RESTART_REQUIRED` 断路器、shadow copy、watcher 语义）保持。

## Acceptance Criteria

- [ ] 一个声明全部六项新能力的 fixture 插件 1.0 在游戏运行中放入插件目录后自动 ACTIVE：其 form 出现在 form 列表并可添加到 film、clip 出现在 picker 并可放入 timeline、粒子组件可用、keymapping 出现在按键设置且可触发、entity/block-entity renderer 对已存在类型生效。
- [ ] 同一 fixture 在游戏启动期已在目录中时，启动后上述效果同样全部生效（启动期与运行期同一条加载路径）。
- [ ] 场景中存在 1.0 的 form/clip 实例时覆盖 2.0 包：实例被自动 snapshot 并用 2.0 类型重建，keyframe/属性数据保留，行为切换为 2.0；旧代注册面无残留。
- [ ] 删除 ACTIVE 包：使用中的 form 显示缺失占位、clip 跳过执行，film 可继续打开/播放/保存且保存后数据完整；重新放回后实例恢复正常。
- [ ] rebuild 单实例失败（fixture 注入）只产生占位与诊断，其余实例正常重建，游戏不崩溃，旧代不复活。
- [ ] 视频导出进行中触发换代被拒绝并给出可读提示，导出完成后可正常重载。
- [ ] 100 次 1.0/2.0 交替 reload 后：六个注册面条目数回到基线、无插件 Class 残留于静态缓存、ledger/线程回基线、classloader `WeakReference` 可进入回收队列（沿用上一期泄漏遥测口径）。
- [ ] 带 mixin/AT/coremod/native entry 或声明 minecraft_registry 的插件仍在 entrypoint load 前被拒为 `RESTART_REQUIRED`。
- [ ] `manifest reload=restart` 的插件仍要求重启。
- [ ] 上一期验收清单中的回归项（8 项 hotSafe 能力、watcher 语义、候选失败保旧代、Windows 覆盖源 JAR）全部保持通过。
- [ ] `testAddonLifecycle`、`testPluginHotReload`（扩展后）、`testClientStructuralRegistration`、`testBinaryCompatibility`、`compileJava`、`compileClientJava`、`pluginHotReloadTestClasses` 全部通过；NeoForge client + dedicated server 手动 smoke 清单执行通过。
- [ ] 用户审核并批准 `prd.md`、`design.md` 与 `implement.md` 后，任务才可进入实现阶段。

## Out of Scope

- Minecraft registry 新增（EntityType/BlockEntityType/Item/Block 等）、Mixin、access transformer、coremod、JNI/native library 的热加载——保持 `RESTART_REQUIRED` 硬边界。
- 实例级增量迁移 / 跨版本内存状态迁移（光影式全量重建替代）。
- 新增 EntityType/BlockEntityType 后再挂 renderer（renderer 能力仅覆盖已存在类型）。
- 热加载任意 NeoForge mod、安全沙箱、远程安装、多插件依赖级联重载（沿用上一期 Out of Scope）。
- 现有 `bbs-web-remote` 等 addon 的实际迁移（本期只保证 SPI 形状对齐使迁移可行）。

## Open Questions

当前没有阻塞规划的问题。keymapping 运行期增删在 NeoForge 1.21.1 的具体落点（options 数组重放 vs 其他机制）、renderer 重建的最小成本路径（dispatcher reload vs 定向替换）由实现期 Phase 0 研究后在 design 既定框架内选择。
