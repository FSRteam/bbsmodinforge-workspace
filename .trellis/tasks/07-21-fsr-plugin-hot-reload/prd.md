# FSR 插件热重载

## Goal

为 FSR 增加一套由 FSR 自己托管的运行期插件系统：游戏运行中把插件包放进目录即可自动安装并刷新可见效果，覆盖包即可自动换代，删除包即可自动卸载；整个流程不要求用户执行 rescan/reload 命令。符合“可热重载”合同的插件可在不重启 Minecraft / 专用服务器的情况下加载、替换、禁用和卸载，失败候选不得破坏当前正在工作的版本。

该能力的首要价值是缩短 FSR 扩展的开发反馈周期，并为可独立部署的运行期扩展提供明确的生命周期、资源所有权、诊断和回滚边界。

## Confirmed Facts

- 目标运行时是 Minecraft 1.21.1、NeoForge 21.1.x、Java 21。
- 现有 Addon API 2.0 是启动期协议：注册窗口会关闭，duplicate id 遵循 first-wins，旧 v1/v2 二进制兼容合同必须继续保持。
- `BBSAddon` 已声明 `unload(...)`，但当前 manager 不调用它；addon 实例、身份、注册贡献和类加载器均没有释放路径。
- NeoForge mod bus、payload type、DeferredRegister、Mixin 和 access transformer 属于启动期结构，不能通过本任务伪装成可安全热卸载。
- `EventBus`、`AssetProvider`、`AddonPayloadBroker`、若干 client registry 当前会长期持有插件对象或 `Class`，必须先增加 owner/generation 和可撤销句柄。
- UI Mirror 与 Film collaboration 已有 `AutoCloseable` 订阅模式，可作为新插件资源所有权的参考。

## Product Positioning

- 新功能称为 **FSR Hot Plugin Runtime**，与 NeoForge mod loading 明确区分。
- 现有 NeoForge addon 和 Addon API 2.0 继续按启动期方式工作；不会被自动纳入热重载。
- 热插件是本机完全信任代码，不是安全沙箱。安装 JAR 等价于允许其在 Minecraft JVM 内执行任意代码。
- 插件目录默认启用自动监听与自动应用；管理命令用于诊断、强制重试、禁用和回滚，不是正常安装流程的必要步骤。拥有插件目录写权限本身已等价于拥有 JVM 代码执行权限，服务器管理员仍可配置 `autoApply=false`。

## User-visible Experience

- **首次安装**：游戏已运行时，把合规包放入 `config/bbs/plugins/`；文件稳定后自动校验、加载、启用并刷新相关 FSR UI/资源/回调。
- **更新**：构建工具覆盖或原子替换同 id 包；FSR 自动准备新 generation、切换并刷新，候选失败时继续显示旧版本效果。
- **卸载**：删除包；FSR 自动停止新调用、清理贡献并刷新受影响界面/资源。
- **反馈**：显示有界状态提示：正在安装/刷新、已生效、更新失败且保留旧版、需要重启；详细原因进入 plugin diagnostics。
- **content-only fallback**：对于不需要 Java 逻辑的资源、语言、声明式 UI/设置或 FSR 可重建数据，允许使用无 entrypoint 的 content plugin。它不更换 classloader，而是像刷新光影包/资源包一样重建受影响的 FSR 投影，作为最稳定、最快的刷新路径。

## Requirements

### Plugin artifact and compatibility

- FSR 从独立插件目录发现插件包；每个包只包含一个插件和一个版本化 manifest。`kind=code` 可分别声明 common/client entrypoint，`kind=content` 不包含 Java entrypoint。
- manifest 在加载插件类之前完成大小、格式、id、版本、side、API 范围、能力和入口类校验；dedicated server 只解析/加载 common entrypoint，物理 client 才能加载 client entrypoint。
- 热插件使用新的 additive public SPI；不得改变 Addon API `2.0`、现有 JVM descriptor、frozen network id 或 v1/v2 first-wins 语义。
- common/client 代码保持物理 side 隔离；专用服务器不得解析或初始化 client-only SPI 类型。

### Lifecycle and replacement

- 支持首次 load、同 id reload、disable、enable、JAR 删除后的 unload，以及手动 rescan。
- create/modify/atomic replace/delete 经稳定性校验后默认自动提交对应 load/reload/unload；手动命令只作为控制与恢复入口。
- 每个插件版本拥有独立 generation 和独立 classloader；共享 FSR/Minecraft/NeoForge/API 类型只来自宿主 classloader。
- reload 必须在旧 generation 保持 ACTIVE 时，用独立 generation container 完整准备同 id 候选版本，再原子切换活动 generation。对遵守 hot SPI、且只通过 staged context 产生宿主管理副作用的插件，候选验证、构造、准备或激活失败时旧 generation 保持可用。
- 切换后不再向旧 generation 分发新调用；已进入的调用有界排空后执行 stop/unload、关闭宿主持有资源并关闭 classloader。
- 旧 generation 出现确定性的 drain timeout、宿主 ledger/route 未清理、managed resource/thread 未停止时，系统必须标记 `RESTART_REQUIRED` 并阻止继续自动重载。classloader 只是在观察窗口内尚未被 GC 时，仅记录 `SUSPECTED_LEAK` 遥测，不能单独作为逻辑卸载失败或强制重启依据。
- MVP 不做任意运行期对象的跨版本迁移；持久配置和宿主拥有的数据可以保留，插件私有内存状态由新版本重新建立。

### Ownership and hot-safe capabilities

- 所有热插件贡献都必须绑定 `(pluginId, generation)` owner，并由宿主维护可逆 ledger。
- MVP 至少支持以下热安全能力：FSR internal event subscription、addon broker receiver、声明式 FSR source pack、仅由宿主值类型构成的声明式 settings module、已有可关闭的 UI Mirror/Render Surface/Film collaboration 订阅，以及宿主管理的 executor/timer/`AutoCloseable` 资源。hot facade 不接受插件自定义 `ISourcePack`、`BaseValue` 或其他会把插件 `Class` 留在全局 manager/UI 的对象。
- public SPI 不暴露 NeoForge event/mod bus；文档必须明确，插件绕过 SPI 直连全局 bus、创建未托管线程或把插件类写入 static cache 后，宿主不保证可卸载。对可观测的遗留线程/owner/route 必须诊断并标记 `RESTART_REQUIRED`，但不得声称能以沙箱方式拦截任意 Java 调用。
- manifest 声明 forms、clips、particles、key mappings、entity/block-entity renderer、Minecraft registry、Mixin、access transformer、coremod、JNI/native library 时，MVP 必须在 entrypoint load 前拒绝并返回 `RESTART_REQUIRED`，不能半注册。full-trust 插件故意隐瞒并绕过 SPI 的行为不在此保证内。

### File monitoring and operations

- 目录监听只产生 reload intent；实际生命周期由单一串行控制器执行，不能在 watcher、render 或网络线程直接变更注册表。
- 监听必须处理重复/乱序事件、未写完 JAR、原子 rename、删除、`OVERFLOW` 和丢事件；`OVERFLOW` 或状态不确定时执行目录全量 reconciliation。
- FSR 不直接从用户正在覆盖的 JAR 加载类；校验完成后使用内容哈希 shadow copy，确保 Windows 上源 JAR 可替换。
- 提供自动应用状态提示，以及 `list/info/rescan/reload/enable/disable` 管理入口、权限检查、结构化日志和可读取的 generation/版本/hash/state/last-error 诊断。
- 系统不下载或远程安装代码；远程 marketplace、签名信任链和在线更新不属于本任务。

### Failure handling and observability

- invalid manifest、API/side 不兼容、duplicate id、坏 JAR、链接错误、回调异常、超时和资源关闭失败都有稳定状态与诊断；逻辑清理失败与 classloader `SUSPECTED_LEAK` 遥测分开记录。
- 候选版本失败不覆盖最后一个可用 shadow copy；至少保留 current/previous 两个已验证 artifact，供显式回滚。
- 日志必须包含 plugin id、generation、artifact hash、状态迁移、触发来源、耗时和错误类型，但不得长期强引用旧 classloader/插件对象。

### Developer experience

- 提供最小示例插件、manifest schema、Gradle 打包说明、热安全能力表、迁移指南和故障排查说明。
- 首个发布以测试 fixture / demo 插件证明完整链路；`bbs-web-remote` 等现有 NeoForge addon 的迁移是后续独立工作，不作为核心运行时完成的前提。

## Acceptance Criteria

- [ ] 一个仅使用 hot-safe SPI 的 fixture 插件 1.0 可在真实 NeoForge client 和 dedicated server 中加载并工作；dedicated server 不解析或初始化其 client entrypoint。
- [ ] 游戏运行中把新的 fixture 包放入空插件目录，无需命令即可自动安装；在文件稳定后的目标 3 秒内状态变为 ACTIVE，并能看到其 UI/资源/事件效果。
- [ ] 用 fixture 插件 2.0 JAR 替换 1.0 后，无需重启即可观察到 2.0 行为；旧 generation 回调不再收到事件，宿主 registry 中不再保留旧 broker route/source pack/settings/subscription 对象，新 generation 对应贡献完整生效。
- [ ] 删除 ACTIVE fixture 包后自动卸载并刷新；重新放回后自动安装，整个过程不依赖手动 rescan。
- [ ] 一个无 Java entrypoint 的 content fixture 可以像资源/光影包刷新一样自动替换资源、语言或声明式 UI 数据；失败内容包不覆盖当前可用内容。
- [ ] 对遵守 SPI 副作用合同的 fixture，候选 manifest 无效、构造抛异常、`LinkageError`、prepare/start 失败时，1.0 仍保持活动，所有 staged host contribution 被撤销且诊断准确。
- [ ] disable、删除 JAR 和显式 unload 会停止新调用、排空在途调用、关闭 ledger 与 classloader；重复调用幂等。
- [ ] Windows 上运行期间可以连续覆盖源 JAR；宿主只加载 shadow copy，不持有源文件锁。
- [ ] partial write、连续多次 modify、rename 和 `WatchService.OVERFLOW` 不会加载半个 JAR，最终状态与目录内容一致。
- [ ] manifest 声明的不支持结构能力及可审计的 NeoForge/Mixin/AT/coremod/native artifact 在 entrypoint load 和任何 hot SPI 发布前被拒绝为 `RESTART_REQUIRED`；文档明确绕过 SPI 的 full-trust 代码不受此保证。
- [ ] 100 次 fixture 1.0/2.0 交替 reload 后，活动 generation 始终唯一、宿主 route/registry/ledger/thread 数回到基线；独立的可回收 fixture 证明 classloader `WeakReference` 能进入回收队列。未在观察窗内回收只增加 bounded `SUSPECTED_LEAK` 遥测，不单独触发 `RESTART_REQUIRED`。
- [ ] reload 切换期间，新调用只进入 old 或 new 中一个完整 generation，不出现同 id 双执行或空路由窗口。
- [ ] 管理命令/UI 能显示 state、version、generation、hash、last transition/error；生产默认策略和权限限制生效。
- [ ] manifest/common/client entrypoint 合同明确；dedicated server 验证不加载 client-only 类，物理 client/integrated server 正确 staged 两侧 entrypoint；client teardown、disconnect/world close 与 plugin unload 的资源释放次序均幂等。
- [ ] settings overlay 打开且旧 module 有 pending save 时 reload：旧 generation 对象先 flush/fence 并从 UI/save worker 释放，新 host-owned module 重建且配置值保留。
- [ ] `testAddonLifecycle`、新 `testPluginHotReload`、`testClientStructuralRegistration`、`testBinaryCompatibility`、`check` 和 `build` 通过。
- [ ] 现有 Addon API 版本仍为 `2.0`，v1/v2 addon、frozen `s1..s15/c1..c19` 和当前独立 addon 启动行为不变。
- [ ] 用户审核并批准 `prd.md`、`design.md` 与 `implement.md` 后，任务才可进入实现阶段。

## Out of Scope

- 热加载或热卸载任意 NeoForge mod。
- 运行期应用/撤销 Mixin、access transformer、coremod、Minecraft registry 或原生库。
- 无重启迁移已有 world entity、Form/Clip/Particle 实例到新的插件 `Class`。
- Java 安全沙箱、恶意插件隔离、远程下载、marketplace、签名 CA 或自动更新。
- 多插件依赖图的级联热重载；MVP 中有活动 dependent 时拒绝 reload，后续再设计 cascade。
- 对现有 `bbs-web-remote`、`bbs-context-hints`、`bbs-structure-addon` 的自动转换。

## Planning Defaults

- 推荐评估 PF4J 3.15.0 作为每 generation 独立的内部 classloader/lifecycle container，但不使用一个标准 PF4J manager 同时管理同 id 两代，也不把 PF4J 类型暴露进 FSR public SPI。Phase 0 必须证明 incumbent ACTIVE 时可并行准备同 id candidate；否则使用同一 internal engine 接口下的自有 loader。
- 插件控制面一次只执行一个 lifecycle transaction。
- 插件目录默认 `autoApply=true`；可由服务器管理员关闭。自动模式仍遵守稳定文件、candidate-first、旧版保留和权限/诊断合同。
- 首版不迁移插件私有内存状态、不级联重载 dependents、不支持 structural capabilities。

## Open Questions

当前没有阻塞规划的问题。上述 defaults 是建议的 MVP；如用户希望热重载现有 NeoForge addon、支持 forms/clips/particles 或生产默认自动监听，需要重新评估范围和风险。
