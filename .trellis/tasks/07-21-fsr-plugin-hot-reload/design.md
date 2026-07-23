# FSR Hot Plugin Runtime — Technical Design

## 1. Decision Summary

FSR 不尝试运行期重载 NeoForge mod，而是在 FSR core 内建立第二条、边界更窄的热插件通道：

```text
NeoForge / ModLauncher (startup only)
  └─ FSR core + existing Addon API 1/2 addons
       └─ FSR Hot Plugin Runtime (runtime managed)
            ├─ plugin A generation 7 classloader
            └─ plugin B generation 3 classloader
```

现有 Addon API 2.0 继续服务“由 NeoForge 加载、启动期注册”的 addon。新 hot-plugin SPI 只暴露宿主可撤销、可按 generation fencing 的能力。两套协议可以共享 descriptor 概念和底层 broker，但不能共享“永久注册窗口”或假装彼此可热卸载。

主交互不是“运行命令重载”，而是 drop-in auto apply：运行中新增包自动安装，替换同 id 包自动换代，删除包自动卸载。管理命令是失败恢复与运维入口。对资源/声明式扩展另提供 `kind=content` 快速路径，其用户感受类似光影包/资源包刷新：短暂冻结受影响的 FSR 投影，重建资源和 UI 数据后恢复，不创建或替换 Java classloader。

内部 classloader/lifecycle engine 先评估 PF4J 3.15.0：它已有 plugin classloader、load/start/stop/unload 和状态基础设施。但标准 `AbstractPluginManager` 以 plugin id 为 map key，会拒绝同一 manager 中的同 id 两代；因此本设计不能直接用一个 PF4J manager 完成 candidate-first reload。可接受方案是每 generation 独立 PF4J container，或相同 internal engine 接口下的自有 loader。FSR 自行拥有全局 id/dependency、manifest、事务、能力 facade、watcher、线程调度、shadow copy、诊断和回滚。

PF4J 类型不得出现在 public API。若采用，依赖应 shade/relocate 进 FSR，附 Apache-2.0 notice，避免其他 mod 的 PF4J 版本冲突。Phase 0 必须在 incumbent ACTIVE 时并行创建/准备同 id candidate，而不只是 stop v1 后再 load v2；若每 generation PF4J container 与目标 NeoForge class/module layer、打包或清理合同不兼容，就以相同接口替换为最小 `URLClassLoader` engine。

## 2. Package and Artifact Boundaries

建议新增的逻辑边界：

```text
mchorse.bbs_mod.api.plugin.*
  public SPI、manifest DTO、状态/诊断 DTO、hot-safe facade

mchorse.bbs_mod.plugin.*
  host manager、artifact store、transaction、generation/lease ledger

mchorse.bbs_mod.plugin.loader.*
  PF4J adapter、classloader policy、entrypoint discovery

mchorse.bbs_mod.plugin.watch.*
  WatchService、debounce/stability、directory reconciliation

mchorse.bbs_mod.plugin.runtime.*
  owner-aware event/network/resource/settings/client adapters

src/client/.../plugin/*
  client-only facade bridge and management UI/command projection
```

磁盘布局：

```text
config/bbs/plugins/                         # 用户放置的源 JAR
config/bbs/plugin-state.json                # enable/disable 与 policy，不存代码
config/bbs/plugin-cache/<id>/<sha256>/      # 验证后的 shadow copy
config/bbs/plugin-cache/<id>/previous.json  # current/previous 已验证 artifact 指针
```

宿主绝不直接从 `plugins/` 源 JAR 运行代码。源 JAR 只读校验后复制到同文件系统的临时路径，fsync/close，再以原子 rename 提交到内容哈希目录。

## 3. Public SPI

建议以新的 additive SPI 表达运行期合同，而不是修改冻结的 Addon API 2.0：

```java
public interface BBSPlugin
{
    default void prepare(BBSPluginContext context) throws Exception {}
    default void start() throws Exception {}
    default void stop(BBSPluginStopReason reason) throws Exception {}
}

/* Client source set only. */
public interface BBSClientPlugin
{
    default void prepare(BBSClientPluginContext context) throws Exception {}
    default void start() throws Exception {}
    default void stop(BBSPluginStopReason reason) throws Exception {}
}
```

`BBSPluginContext` 是一次 generation 的 common-safe 宿主句柄，至少提供：

- immutable descriptor、plugin id、generation、side、diagnostic sink；
- owner-aware internal events、broker network、resources、settings；
- `own(AutoCloseable)`、managed executor/scheduler、main-thread dispatch；
- persistent plugin data/config directory，但不暴露任意 FSR manager。

`BBSClientPluginContext` 只存在于 client source set，增加 UI Mirror、Render Surface、Film collaboration 和 client runtime subscriptions。common 代码只把 client entrypoint 当作未解析类名保存；dedicated server 不加载 `BBSClientPlugin`/`BBSClientPluginContext`。

所有 facade 都由 context 自动记录 ledger。插件不需要自己保存每个 unregister token 才能被强制清理，但仍可保存 subscription 以便提前释放。context 在 PREPARE 结束后关闭 structural admission；runtime send/dispatch 能力只在 generation 为 ACTIVE 时工作。

manifest 建议为 `META-INF/bbs-plugin.json`，首版字段：

```json
{
  "schema": 1,
  "kind": "code",
  "id": "example",
  "version": "1.0.0",
  "commonEntrypoint": "example.ExamplePlugin",
  "clientEntrypoint": "example.client.ExampleClientPlugin",
  "api": "[1.0,2.0)",
  "side": "common",
  "capabilities": ["events", "network", "resources"],
  "dependencies": [],
  "reload": "hot"
}
```

约束：

- 一 JAR 一插件；id 与 descriptor 必须一致。
- `kind=code` 使用 generation classloader 与 entrypoint lifecycle；`kind=content` 禁止 `.class`/native/NeoForge metadata，省略 common/client entrypoint，只注册宿主可解析的资源和声明式数据。
- `side=common` 需要 common entrypoint，可选 client entrypoint；`side=client` 只允许 client entrypoint；`side=dedicated_server` 只允许 common entrypoint。缺失/多余组合在加载任何 entrypoint 前拒绝。
- 插件依赖首版只做解析和阻塞：目标有活动 dependent 时拒绝单独 reload。
- JAR 不能内嵌受保护包：`java.*`、`net.minecraft.*`、`net.neoforged.*`、FSR public API、PF4J internal relocation、SLF4J API。
- JAR 不能同时声明 NeoForge mod/Mixin/AT/coremod/native payload；发现 `neoforge.mods.toml`、Mixin config、access transformer、coremod 或 native library entry 时，在加载 entrypoint 前拒绝。
- `reload: restart` 可让同一 packaging 被发现和诊断，但不在运行期执行。
- manifest 先用受限 streaming/typed parser 读取，并限制 JAR、entry、manifest、字符串和集合大小。

## 4. Classloader Contract

每个 `(pluginId, generation)` 使用全新、可关闭的 classloader。委派策略：

1. JDK、Minecraft、NeoForge、FSR public API、SLF4J 和 plugin SPI 强制 parent-first。
2. 插件自己的 package 与私有 shaded dependencies child-first。
3. 禁止插件覆盖 host/protected packages；扫描 JAR entry 在定义类前拒绝。
4. 每个回调临时设置 thread context classloader 为该 generation，`finally` 恢复。
5. `ServiceLoader` 必须显式传 generation classloader，不依赖全局 TCCL。
6. unload 后先清除全部宿主引用，再 `close()` classloader；诊断只保留字符串、hash 和 weak reference。

加载器只解析当前物理 side 所需的 manifest entrypoint，不启用 PF4J annotation/extension 的全 JAR 扫描。dedicated server 只构造 common entrypoint；物理 client 在同一 generation/classloader 中分别构造 common 与 client entrypoint，并给它们不同的 typed context。

Windows 文件替换依赖两层保护：从 shadow copy 加载，且关闭 loader 释放 shadow JAR handle。源 JAR 因未被 classloader 打开而可持续覆盖。

## 5. State Machine

公开状态建议：

```text
DISCOVERED -> VALIDATED -> PREPARING -> STAGED -> ACTIVE
                                              -> FAILED

ACTIVE -> DRAINING -> UNLOADING -> LOGICALLY_UNLOADED
   |          |           |
   |          +-----------+-> RESTART_REQUIRED (deterministic teardown failure)
   +-> RELOAD_PENDING

任何非活动 artifact：DISABLED / INCOMPATIBLE / RESTART_REQUIRED / FAILED
GC telemetry（非 lifecycle state）：COLLECTED / SUSPECTED_LEAK
```

`ACTIVE` 是唯一可接收业务调用的状态。状态变化由一个 serial controller 提交，并携带单调 generation、transition id 和 artifact hash。watcher、命令、client/server lifecycle 只能提交 intent，不能直接改状态。

## 6. Ownership Ledger and Dispatch Fencing

`PluginGeneration` 持有：

- validated artifact + manifest + classloader；
- plugin instance；
- `PluginContributionLedger`；
- active-call counter / draining gate；
- state、timestamps、diagnostic snapshot；
- classloader weak reference（只用于卸载后观测）。

每个注册适配器返回或内部记录 `PluginLease extends AutoCloseable`。ledger 以注册的逆序关闭，单项失败被聚合但不会跳过后续清理。关闭必须幂等。

所有运行期路由通过稳定的 host-owned proxy。每个插件的 staged contributions 先形成 immutable generation snapshot；全局 `ActivePluginIndex` 以一个原子引用持有所有活动 snapshot。network/event/client/resource/settings adapter 在 acquire 时读取同一个 root snapshot，commit 只交换这一个 root pointer，不能分别替换多个全局 map。`EventBus`、`AssetProvider`、settings manager 和 client registries 中长期存在的只能是宿主 proxy/DTO，不能是 generation callback、插件 `Class` 或插件实例：

```text
NeoForge fixed broker/event/client hook
  -> host route table[pluginId]
  -> acquire ACTIVE generation lease
  -> invoke plugin callback
  -> release lease
```

切换 `ActivePluginIndex` 是一个原子操作。调用在 acquire 时固定 generation；新调用不会跨代，旧调用可以在 drain timeout 内完成。settings UI/resource cache 等必须在 Minecraft 主线程刷新的投影可以在 root swap 后重建，但读取旧投影时仍受 owner/generation fence 保护。generation fence 也用于 future、mailbox、GPU callback 和异步发送结果，防止旧任务在切换后写回新状态。

插件自己创建的裸线程无法被 Java 安全回收，因此 SPI 提供 managed executor/timer 并把它们纳入 ledger。卸载时额外检查：

- live thread 的 TCCL 是否为旧 classloader；
- host route/registry 是否仍有旧 generation owner；
- active-call counter 是否归零；
- classloader weak reference 是否在测试/诊断观察窗内进入 reference queue。

生产环境不通过强制 `System.gc()` 宣称成功；逻辑卸载只看 active calls、host routes、ledger 与 managed resources 的确定性结果。弱引用未回收只产生 bounded `SUSPECTED_LEAK` 趋势遥测，不单独触发 `RESTART_REQUIRED`。隔离测试可有界触发 GC 证明一个无宿主引用的 fixture loader 可回收。

## 7. Capability Matrix

| Capability | MVP | Required redesign |
|---|---:|---|
| FSR internal events | Hot | `EventBus.subscribe()` 返回 owner lease；unregister + generation fence |
| Addon broker receivers | Hot | fixed NeoForge payload 外层不变；receiver map 按 owner 可替换/移除 |
| FSR source packs | Hot | hot facade 只接受 namespace/resource-root DTO；`AssetProvider` 保留 host proxy，不保留插件 `ISourcePack`/`Class`，并支持 cache/resource invalidation |
| FSR settings module | Hot | 只接受 host-owned declarative value schema/action route；不接受 plugin `BaseValue`。reload 先 flush/fence pending save，关闭/重建打开的 overlay，再移除旧投影 |
| UI Mirror | Hot | 复用/统一 `AutoCloseable` subscription，加入 generation owner |
| Render Surface | Hot | 增加可关闭 registration 和 callback generation fence |
| Film collaboration | Hot | 复用 close + clear-addon-state，并加入 generation owner |
| Managed executor/timer/resource | Hot | context 创建/own，逆序幂等 close |
| Forms / Clips / Particles | Restart | 当前保存插件 `Class`，且活动对象无法安全换代 |
| Key binding / entity renderer / block entity renderer | Restart | NeoForge client structural registration 已冻结 |
| Blocks/items/entities/registries | Restart | NeoForge/Minecraft startup registry |
| NeoForge event/mod bus direct listener | Unsupported | SPI 不暴露；best-effort artifact audit，full-trust 插件仍可绕过，绕过后不承诺热卸载 |
| Mixin/AT/coremod/native JNI | Reject | JVM/ModLauncher/OS 结构不可可靠撤销 |

任何 manifest 声明 restart-only/rejected capability，都在 entrypoint class load 前停止。不能先执行部分 hot-safe 注册、再发现其余能力需要重启。

## 8. Reload Transaction

一次 reload 分为 prepare、commit、retire：

首次安装和自动更新使用同一 transaction；首次安装只是没有 incumbent 的 reload。`kind=content` 也走同一 validate/stage/atomic-root commit，但跳过 classloader/entrypoint 步骤，直接构建 immutable host-owned content snapshot。

### Prepare（后台 I/O + 受控回调）

1. 对源目录做完整 snapshot，计算 size/mtime/SHA-256。
2. 校验文件稳定、JAR/manifest、side/API/capability/dependency、protected packages。
3. 写入并验证 shadow copy。
4. 在与 incumbent 分离的 generation container 中创建 candidate classloader，以及当前 side 需要的 common/client entrypoint/context；一个标准 PF4J manager 不同时持有同 id 两代。
5. `prepare()` 写入 candidate-local transaction/ledger；注册项先进入 staging view，不暴露给 active routes。
6. 运行自检，状态进入 STAGED。

hot SPI 合同要求 constructor/prepare/start 不绕过 context 修改全局宿主状态；prepare 只能创建 staged contribution 或由 ledger 持有的、尚未接收 active route 的 quiescent resource。对遵守该合同的插件，任何 commit 前失败都会关闭 candidate ledger/classloader并保留 incumbent ACTIVE。full-trust 插件绕过 SPI 的外部/静态副作用不在回滚保证内。

### Commit（串行控制器 + 对应 Minecraft 主线程）

1. 再次确认 intent/hash 未被更新版本取代。
2. candidate `start()` 只 prime 宿主管理、仍未接收 active route 的资源，并完成最小 readiness check。
3. 原子发布 candidate route/contribution snapshot。
4. incumbent 进入 DRAINING，拒绝新 acquire；candidate 成为唯一 ACTIVE。
5. 若 commit 前失败，丢弃 candidate；若原子发布本身失败，恢复 incumbent snapshot。

### Retire（有界 drain + cleanup）

1. 等待 incumbent active calls 在 timeout 内归零。
2. 调用 `stop(RELOAD)`，异常隔离。
3. 逆序关闭 host ledger，清理 owner state。
4. 关闭 classloader并登记 weak reference observation。
5. 若出现确定性的 drain timeout、host owner/route/ledger 残留或 managed thread/resource 未停止，candidate 可继续提供已提交服务，但该 id 标记 cleanup failure + `RESTART_REQUIRED`，禁止后续自动 reload；不得输出完全成功。仅 weak reference 未回收不进入此分支。

此语义保证 contract-compliant candidate 的 host-owned contribution 在 commit 前失败时不影响旧版，也保证提交后最多一个 generation 接收新调用。它不承诺撤销第三方插件绕过 SPI 后对磁盘、世界、网络、NeoForge bus 或 global static 造成的任意副作用。

## 9. Watcher and Reconciliation

`WatchService` 只是低延迟提示源。官方合同明确指出 modify 事件不保证写入完成、事件数量/顺序依平台而异且可能 `OVERFLOW`。

设计：

- 监听 create/modify/delete；按 path 合并 750 ms debounce。
- 候选需在两次采样（建议间隔 250 ms）中 size/mtime/hash 稳定，或由 `.tmp -> .jar` 原子 rename 提交。
- 每次 transaction 前重读目录 snapshot，避免处理过时 intent。
- `OVERFLOW`、invalid watch key、启动、手动 rescan 和周期性低频巡检都执行 full reconciliation。
- reconciliation 以 artifact hash 为真源：create/load、hash changed/reload、missing/unload、unchanged/no-op。
- watcher 使用一个可关闭 daemon；关闭 `WatchService` 唤醒等待线程。它不调用插件代码。

默认 `autoApply=true`：稳定的 create/modify/atomic replace/delete 会自动提交 load/reload/unload。拥有本地插件目录写权限已经等价于进程代码执行权限；专用服务器管理员可显式设为 false，此时变化停在 `RELOAD_PENDING`，由管理入口确认。

正常 fixture 的体验目标是文件稳定后 3 秒内 ACTIVE/LOGICALLY_UNLOADED，不把超大包或插件自定义慢初始化纳入硬实时保证。每次自动 transaction 都投影一个有界状态提示：installing/refreshing、active、failed-kept-previous 或 restart-required。

## 9.1 Content-only Refresh Path

`kind=content` 是用户所说“类似光影包刷新”的保底能力，也是资源型插件的推荐模式：

1. manifest 和内容文件完成 bounds/hash/schema 校验；包内出现 `.class`、native、NeoForge/Mixin/AT metadata 则拒绝。
2. 后台解析语言、资源索引、声明式 settings/UI data 及未来明确支持的 FSR data schema，生成 immutable content snapshot。
3. 主线程交换 `ActivePluginIndex`，暂停旧 content projection 的新读取，重建资源/provider/UI cache。
4. 刷新完成后恢复显示；失败则继续使用旧 snapshot，不产生空白中间态。

该路径不会让任意 Minecraft resource pack、NeoForge registry 或第三方 shader mod 自动变成 FSR 插件；首版只处理 FSR 明确拥有、可重建的资源与声明式数据。

## 10. Threading

- 文件读取、hash、JAR/manifest validation 和 candidate class loading 在有界 lifecycle worker 上运行。
- lifecycle transaction 对全局串行；同一 plugin id 永远不会并发 load/reload/unload。
- 会改 common server runtime 的 commit/cleanup 在 server thread gate 上执行。
- client-only contribution commit/cleanup 在 Minecraft client thread gate 上执行。
- integrated server 的 common/client entrypoint prerequisite 分别在对应线程完成 staging；两侧都成功后才交换共享 `ActivePluginIndex`。swap 后的 UI/resource refresh 可分线程异步完成，但必须带 generation fence；任一侧无法 staging 时仍由旧 generation 提供服务。
- render thread只执行需要 GL 的既有 owner teardown；不等待磁盘/hash/classloading。
- plugin callback 有 timeout/diagnostic，但 Java 不能安全强杀任意第三方代码；超时使该 generation 进入 FAILED/RESTART_REQUIRED。
- client disconnect/world close 是 runtime session cleanup，不自动卸载 plugin；client stopping/server shutdown 执行全 manager shutdown。

## 11. Management and Diagnostics

管理面建议：

- `/bbs plugins list`
- `/bbs plugins info <id>`
- `/bbs plugins rescan`
- `/bbs plugins reload <id> [--previous]`
- `/bbs plugins enable|disable <id>`

正常安装/更新/删除不需要上述命令。专用服务器使用 operator permission；client-only/local management 走 client command或 FSR settings panel，但最终调用同一 service。诊断 DTO 只含稳定数据：

- id/display name/version/API/side/capabilities；
- state/generation/hash/source filename；
- last transition/reason/duration；
- accepted/closed contribution counts；
- active call count、managed resource/thread count；
- logical unload、deterministic cleanup failure、`RESTART_REQUIRED` 与独立的 `SUSPECTED_LEAK` 信息；
- bounded warning/error history（类名与 message，不持有 Throwable）。

日志统一 topic，例如 `topic=plugin.lifecycle`，每个 transition 只输出一条 summary，异常详情限频。

## 12. Security and Trust Boundary

- 热插件是 full-trust JVM code；文档和 UI 必须明确提示，不使用“sandbox”字样。
- canonicalize 插件/cache 路径并拒绝 root escape、symlink surprise、非 regular file、超限 JAR/entry/manifest。
- 校验 hash 的目的是完整性、版本身份和回滚，不代表作者可信。
- 不从网络下载代码，不把管理操作暴露给普通玩家或 Web Remote viewer。
- 插件数据目录按 id containment；id 在落盘前验证，不能参与任意路径拼接。

## 13. Compatibility

- `BBSApiVersion.CURRENT` 和 addon descriptor 默认仍为 `2.0`。
- v1 collector、v2 manager、first-wins identity、现有 registration window 与 frozen payload table 不变。
- hot-plugin id 使用独立 identity domain，避免同 id 自动替换已由 NeoForge 加载的 addon；发现冲突时拒绝并诊断。
- existing addon diagnostics 与 new plugin diagnostics 分开读取，可在 UI 合并展示。
- common source set 不引用 client-only public types；classloader 在 dedicated server 上不扫描 client entrypoint。
- 不依赖 `ModList` 发现 hot plugin；`LoaderAccess#getEntrypoints` 继续只表示既有启动期 addon。

## 14. Rollback and Recovery

- contract-compliant candidate 在 commit 前失败：关闭 candidate 的 host-owned/staged state，incumbent 原样继续。
- commit 发布失败：恢复 incumbent contribution snapshot，关闭 candidate。
- commit 后 old retire 失败：candidate 保持唯一路由，但标记 `RESTART_REQUIRED`，停止进一步自动 reload；管理员可选择重启。
- MVP 不承诺 post-commit 自动业务状态回滚。commit 后 candidate 失败时先停止新调用并诊断；若确定性 teardown 允许，可由管理员从 previous shadow artifact 重新走完整 reload transaction，否则要求重启。
- current/previous shadow copy 仅在新 generation 完成 commit 后轮换；清理由 bounded retention 执行，不删除活动或诊断引用的 artifact。
- manager 自身 shutdown 逐插件逆序关闭，聚合异常，最后关闭 watcher/lifecycle workers。

## 15. Verification Strategy

新增 `pluginHotReloadTest` source set 和 `testPluginHotReload` JavaExec，遵循现有无 JUnit 的 main/check 风格。测试 fixture 构建两个同 id 不同实现 JAR，并覆盖：

- manifest/JAR/protected-package validation；
- parent/shared API identity与 plugin-private dependency isolation；
- incumbent ACTIVE 时并行准备同 id candidate，验证 per-generation engine 不触发 PF4J duplicate-id 冲突；
- first load、reload、disable/enable/delete、manual rollback；
- drop-in create/replace/delete 自动 apply，不调用 rescan/reload 命令；
- content-only snapshot refresh、失败保留旧内容和无空白中间态；
- prepare/start/stop/ledger close 异常；
- invalid candidate keeps incumbent；
- atomic route generation and in-flight drain；
- owner unregister for every hot-safe capability；
- settings overlay open + pending save 时的 flush/fence/rebuild 和旧 generation 引用释放；
- partial write/debounce/rename/overflow reconciliation；
- Windows source JAR replacement；
- 100-cycle logical route/ledger/thread baseline harness；另用隔离 fixture 验证可回收 classloader，未回收只校验 bounded `SUSPECTED_LEAK` telemetry；
- client class absence on dedicated server；
- legacy Addon API binary/source behavior unchanged。

真实运行门：NeoForge client、dedicated server、Windows 文件替换、资源/settings UI 刷新、网络 broker 双向消息、disconnect/world close/client stop。自动测试通过不替代这些手测。

## 16. External Evidence

详细来源见 `research/exa-hot-reload.md`。关键依据：

- NeoForge lifecycle/mod bus 是启动期一次性流程。
- JDK `WatchService` 明确允许重复、延迟、丢失/overflow 和“写入尚未完成”。
- `URLClassLoader.close()` 是 Windows 释放 JAR handle 的必要步骤，但清除所有强引用才允许类卸载。
- PF4J 已实现 plugin classloader 和 load/start/stop/unload，可减少部分基础设施；其标准 manager 不允许同 id 两代并存，因此必须使用 per-generation container 或 internal loader fallback。FSR 仍拥有全局 identity、注册贡献撤销和 generation fencing。
