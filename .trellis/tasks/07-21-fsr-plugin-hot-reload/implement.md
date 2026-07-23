# FSR Hot Plugin Runtime — Implementation Plan

## Delivery Strategy

按“先证明类加载器可卸载，再建立 owner-aware 能力，最后接 watcher/管理面”的顺序交付。每个阶段都有独立 gate；任一 gate 失败就停在该阶段，不把半成品自动加载器合入生产路径。

建议拆成连续提交，但保持一个 Trellis task，避免多个子任务同时修改 `BBSMod.java`、addon broker、client registries 和 `build.gradle`：

1. feasibility/engine；
2. public SPI + artifact validation；
3. owner-aware contribution layer；
4. transactional lifecycle；
5. watcher/ops/diagnostics；
6. docs/demo/real runtime closure。

## Phase 0 — Feasibility Gate

- [ ] 建立隔离 spike，不接入默认启动路径。
- [ ] 引入/打包 PF4J 3.15.0（internal + shaded/relocated），确认许可证 notice。
- [ ] 用 custom loader policy 强制 FSR/Minecraft/NeoForge/SPI parent-first，plugin package child-first。
- [ ] 构建两个同 id、同 SPI 的 fixture JAR；保持 v1 ACTIVE，同时在独立 generation container 中准备 v2。证明标准单 PF4J manager 的 duplicate-id 限制不会被误用；若 PF4J per-generation container 不可行，切换到 internal loader fallback。
- [ ] 在目标 NeoForge 1.21.1 dev client 和 dedicated server run 中重复同样流程。
- [ ] 验证源 JAR 在 Windows 运行期间可被覆盖，shadow JAR 在 loader close 后可删除。
- [ ] 验证 host `BBSPlugin` 类型只有一份，`instanceof` 不因重复 API class 失败。
- [ ] 在隔离测试 JVM 中证明清除宿主引用后一个 fixture loader 可进入回收队列；把 GC 观察作为 telemetry，不把未及时回收单独视为 teardown failure。
- [ ] 记录 ADR：PF4J adopt 或 fallback internal engine；未通过不得进入 Phase 1。

Phase 0 rollback：移除 spike/source set/dependency，生产行为零变化。

## Phase 1 — Public SPI, Manifest, Artifact Store

- [ ] 新增 common-safe `mchorse.bbs_mod.api.plugin`：entrypoint、context、descriptor/manifest DTO、state、stop reason、diagnostics、limits。
- [ ] 保持 `BBSApiVersion.CURRENT == 2.0`；不修改已有 public method descriptor。
- [ ] 定义 `META-INF/bbs-plugin.json` schema v1 和 typed decoder，区分 `kind=code|content`、`commonEntrypoint`/`clientEntrypoint`、`side=common|client|dedicated_server`。
- [ ] 实现 validation：文件 containment、regular file/symlink、JAR/entry/manifest limits、id/version/API/side/capability/dependency、entrypoint、protected package。
- [ ] 在 entrypoint load 前拒绝 NeoForge mod metadata、Mixin/AT/coremod 和 native library entries；不启用全 JAR extension class scanning。
- [ ] `kind=content` 禁止 `.class`/native/NeoForge metadata，使用 host-owned resource/data schema，不创建 plugin classloader。
- [ ] 实现 SHA-256 artifact identity 和 `plugin-cache/<id>/<hash>` shadow-copy 原子提交。
- [ ] 实现 current/previous retention metadata；candidate 未 commit 不推进 current。
- [ ] 实现 discovery/reconciliation snapshot，但只输出 diagnostics，不自动执行插件。
- [ ] 为 client entrypoint 使用 client source-set adapter；dedicated server 只解析 common entrypoint 字符串，不触碰 client class。
- [ ] 为 invalid/truncated/duplicate/conflicting Addon id/unsupported capability 编写 fixture。

Phase 1 gate：manifest fuzz/bounds、path containment、shadow copy、binary compatibility、dedicated-server class loading tests 通过。

## Phase 2 — Owner-aware Contribution Layer

### Core ownership primitives

- [ ] 新增 `PluginOwner(pluginId,generation)`、`PluginLease`、`PluginContributionLedger`。
- [ ] ledger 逆序、幂等 close；聚合异常且继续释放后续资源。
- [ ] 新增 ACTIVE acquire/drain counter 和 generation fence；测试 acquire/swap/drain race。
- [ ] 新增 immutable generation contribution snapshot 与单根 `ActivePluginIndex`；所有 hot-safe adapter 从同一 root acquire，禁止多 map 依次切换。
- [ ] 新增 managed executor/scheduler/resource owner；callback 包装 TCCL + exception boundary。

### Internal events

- [ ] 将 `EventBus.register` 内部扩展为可返回 subscription/lease；保留旧 void API 行为。
- [ ] hot path 在长期 subscriber collection 中只保存 host dispatcher/proxy；generation callback 留在 `ActivePluginIndex` snapshot。支持按 subscription/owner unregister，不在 dispatch 中持锁，CopyOnWrite snapshot 语义明确。
- [ ] hot plugin facade 只暴露 owner-aware subscribe，不暴露裸 bus。

### Network broker

- [ ] `AddonPayloadBroker` receiver entry 增加 owner/generation；fixed NeoForge payload id 不变。
- [ ] fixed broker map 保存 host route proxy；generation receiver 留在 staged/active snapshot。实现 atomic root swap、unregister/clear owner。
- [ ] dispatch acquire ACTIVE generation；旧 generation 结果不能写入新 route。
- [ ] 保持 Addon API 2.0 existing receiver duplicate/namespace/limit/diagnostic contract。

### Resources and settings

- [ ] `AssetProvider` 增加 host-owned source-pack proxy、remove owner、cache invalidation；hot facade 只接受 namespace/resource-root DTO，不接受插件 `ISourcePack`。
- [ ] source pack resolver 不再以 plugin `Class` 作为唯一 resource loader anchor；使用 generation resource resolver/lease。
- [ ] settings hot facade 限制为 host-owned declarative value schema/action route，不接受 plugin `BaseValue`；pending save 先 flush/fence，打开的 overlay 在主线程关闭/重建。
- [ ] client main thread 执行语言/resource/settings UI refresh，并加 generation fence。

### Client runtime APIs

- [ ] UI Mirror/Film collaboration registration统一进入 ledger；长期 registry 只保存 host proxy，close 清 addon/generation state。
- [ ] Render Surface registration新增 host proxy、close/unregister、drain、generation fence。
- [ ] client tick/render/disconnect/stopping runtime hooks新增 owner-aware host subscription；generation callback 只从 active snapshot acquire，热插件不得调用永久 static-list API。
- [ ] key mapping/renderer structural APIs 在 manifest validation 阶段返回 `RESTART_REQUIRED`。

Phase 2 gate：每个 hot-safe capability 都有 stage -> active-root swap -> invoke -> close owner -> no invoke 的确定性测试；settings overlay open/pending save 不再保留旧 generation；旧 Addon API 回归全过。

## Phase 3 — Transactional Plugin Manager

- [ ] 实现 `BBSPluginManager`、`PluginGeneration`、serial lifecycle controller 和状态机。
- [ ] 实现 load：validate -> shadow -> `kind=code` 的 per-generation container/classloader 或 `kind=content` 的 immutable content snapshot -> side-specific staging -> atomic publish。
- [ ] 实现 reload：candidate prepare、incumbent-preserving failure、atomic route swap、old drain/stop/ledger close/loader close。
- [ ] 在 public SPI/doc/test 中约束 constructor/prepare/start：不得绕过 context 产生不可撤销全局副作用；回滚保证仅覆盖 contract-compliant host-owned/staged effects。
- [ ] 实现 disable/enable/unload/delete reconciliation，全部幂等。
- [ ] 实现 candidate cleanup 和 commit rollback snapshot。
- [ ] 实现 timeout、late future/callback fencing、stale intent/hash rejection。
- [ ] 确定性的 teardown failure（drain timeout、host owner/route/ledger 残留、managed resource/thread 未停止）标记 `RESTART_REQUIRED`，阻止该 id 后续 auto reload；仅 classloader GC 延迟记录 `SUSPECTED_LEAK`。
- [ ] 有活动 dependent 时拒绝单独 reload；MVP 不实现 cascade。
- [ ] `BBSMod` 只在 core 初始化完成后启动 manager；server/client shutdown 按 owner-before-container 顺序关闭。
- [ ] integrated server 分别完成 server/client staging prerequisite 后才交换共享 active root；post-commit refresh 全部 generation-fenced。
- [ ] client disconnect/world close 只释放 session-owned资源，不卸载 process-owned plugin generation。
- [ ] hot plugin id 与既有 Addon v1/v2 id 冲突时 fail closed，不改变原 winner。

Phase 3 gate：contract-compliant invalid candidate keeps v1、single-active-generation、in-flight drain、stop failure、ledger failure、manual previous reload、shutdown ordering tests 通过。

## Phase 4 — Watcher and Reconciliation

- [ ] 新增可关闭的 `WatchService` daemon，只提交 intent；首次 create/modify/atomic replace/delete 默认自动 apply，不要求用户执行 rescan/reload。
- [ ] 实现 750 ms path/id debounce 和两次稳定 fingerprint 检查。
- [ ] 支持 `.tmp -> .jar` atomic rename；忽略 temp/cache/non-JAR。
- [ ] `OVERFLOW`、invalid key、startup、manual rescan、低频巡检统一触发 full reconciliation。
- [ ] 同一 id 的新 hash supersede 旧 pending transaction；过时 candidate commit 前再次校验。
- [ ] 默认 `autoApply=true`；`autoApply=false` 时才停在 `RELOAD_PENDING`。client/server 状态提示统一由 lifecycle service 投影。
- [ ] manager shutdown 先停止新 intent，再 close watcher，最后 drain lifecycle controller。

Phase 4 gate：partial write、event storm、rename、delete/recreate、overflow/full scan、shutdown while blocked、自动首次安装与删除卸载等测试通过；不使用不可靠的固定 sleep 作为唯一断言。

## Phase 5 — Management, Diagnostics, Rollback

- [ ] 增加统一 service API 与权限 gate。
- [ ] 注册 `/bbs plugins list|info|rescan|reload|enable|disable`；不动态注册任意 plugin Brigadier tree。
- [ ] client-only 本地入口复用同一 service，不复制状态机。
- [ ] 新增 bounded diagnostics/history DTO：generation/hash/state/durations/contributions/leak/restart-required。
- [ ] 结构化、限频日志；diagnostics 不保留 Throwable/plugin/ClassLoader 强引用。
- [ ] 显式 `reload --previous` 从已验证 previous shadow copy走正常 transaction。
- [ ] config/state 原子写入，unknown fields 保留或明确版本化迁移；不会把 JAR 放入 state file。
- [ ] 明确 full-trust warning、生产默认手动策略和管理权限。

Phase 5 gate：权限、状态投影、bounded history、previous rollback、config corruption/recovery tests 通过。

## Phase 6 — Test Harness, Demo, Documentation

- [ ] `build.gradle` 新增 `pluginHotReloadTest` source set、fixture-JAR tasks、`testPluginHotReload` JavaExec，并接入 `check`。
- [ ] demo 插件仅使用 hot-safe SPI，提供 common/client entrypoint、1.0/2.0 行为和故意失败版本；副作用只能通过 staged/managed context 产生。
- [ ] 增加 content-only fixture：无 entrypoint，资源/语言/声明式 UI 数据替换后自动刷新，失败时保留旧 snapshot。
- [ ] 文档包含：概念边界、manifest、Gradle packaging、capability matrix、生命周期、managed resources、生产策略、诊断、故障排查、Windows 工作流。
- [ ] Addon API 文档增加“NeoForge addon vs hot plugin”章节，明确旧 addon 不会自动变热。
- [ ] 写现有 addon 迁移清单：移除 `@Mod`/direct NeoForge listeners/static singleton ownership，改用 context/ledger；单独建迁移任务。
- [ ] 连续 100 次 fixture 1.0/2.0 reload；断言唯一 active root、registry/lease/thread 数回到基线。
- [ ] 在隔离 JVM 观察 fixture loader weak reference 回收；记录 metaspace/classloader 统计，未回收只形成 bounded `SUSPECTED_LEAK` telemetry。
- [ ] 真实 Windows client：新包 drop-in 自动安装、覆盖自动更新、删除自动卸载；资源/settings/network/UI 生效，settings overlay 打开且 pending save 时保留配置，content-only 包呈现资源/光影包式刷新反馈，invalid v3 不影响 v2。
- [ ] 真实 dedicated server：load/reload/unload、权限和 network broker；确认无 client class initialization。
- [ ] 真实 shutdown：world close/disconnect/client stop/server stop 各走正确 owner teardown。

Phase 6 gate：自动与手动 acceptance map 完整，所有未测项明确保留为 release blocker。

## Expected Primary File Areas

实现前以实际代码为准，预计风险文件域：

- `new/build.gradle`、依赖打包和 test source sets；
- `new/src/main/java/mchorse/bbs_mod/BBSMod.java`；
- `new/src/main/java/mchorse/bbs_mod/api/plugin/**`；
- `new/src/main/java/mchorse/bbs_mod/plugin/**`；
- `new/src/main/java/mchorse/bbs_mod/events/EventBus.java`；
- `new/src/main/java/mchorse/bbs_mod/resources/AssetProvider.java`；
- `new/src/main/java/mchorse/bbs_mod/network/compat/AddonPayloadBroker.java`；
- `new/src/client/java/mchorse/bbs_mod/api/client/BBSClientApi.java` 与 runtime registries；
- `new/src/pluginHotReloadTest/**`；
- `new/docs/addon-api-2.md` 与新 hot-plugin 文档。

开始实现前必须检查 dirty `new/` worktree；当前有大量其他任务改动。共享文件发生重叠时，先按所有权协调，禁止 reset/checkout 用户改动。

## Validation Commands

按风险由小到大串行运行，避免当前资源受限构建并发：

```powershell
Set-Location new
./gradlew.bat testPluginHotReload
./gradlew.bat testAddonLifecycle
./gradlew.bat testClientStructuralRegistration
./gradlew.bat testBinaryCompatibility
./gradlew.bat compileJava compileClientJava
./gradlew.bat check
./gradlew.bat build
git diff --check
```

Phase 0/6 另运行 production-shaped NeoForge client/dedicated-server smoke；`check` 不能代替真实 runtime gate。

## Review Gates

### API/compatibility review

- public SPI common/client split 是否正确；
- Addon API 2.0 descriptor 与 frozen IDs 是否完全不变；
- public API 是否泄漏 PF4J/internal manager/classloader 类型；
- protected-package 和 duplicate API copy 是否 fail closed。

### Lifecycle review

- 每个强引用从何处创建、由谁关闭；
- candidate failure、commit failure、old retire failure 各自状态；只对 contract-compliant host-owned effects承诺回滚；
- active call/future/callback 是否全部带 generation；
- owner 先释放资源，再丢 owner/classloader 引用；GC 延迟与 deterministic teardown failure 分开；
- shutdown/disconnect/world close 是否区分 process-owned 与 session-owned资源。

### Concurrency review

- watcher 只提交 intent；
- lifecycle transaction 是否全局串行；
- main/render/server thread boundary；
- route swap 与 acquire/drain 是否线性化；
- timeout 后旧代码是否仍能写入新 generation。

### Security/operations review

- 文件 containment、symlink、JAR bounds、hash/shadow copy；
- management 权限和 full-trust 文案；
- production 默认是否 manual；
- diagnostics/log/history 是否有界且不保留旧对象。

## Rollback Points

- Phase 0：删除实验依赖/fixture，无 runtime 行为。
- Phase 1：保留 parser/artifact tests，manager 不启动。
- Phase 2：owner-aware API 可先只供 core 使用，旧注册入口继续工作。
- Phase 3：用 feature flag 默认关闭 hot runtime；异常时回退到启动期 addon 路径。
- Phase 4：关闭 watcher，保留手动 manager。
- Phase 5：隐藏管理 UI/command，不影响 core lifecycle。
- 发布后：`hot_plugins.enabled=false` 完全禁用扫描/执行；移除插件 JAR 后重启恢复纯 Addon API 2.0 行为。

## Deferred Follow-ups

- 迁移 `bbs-web-remote` 为 hot plugin（需要清除 direct NeoForge listeners/static singleton/native WebRTC ownership）。
- 为 forms/clips/particles 设计 host proxy factory 和活动实例迁移语义。
- dependency DAG 的 `--cascade` prepare/commit transaction。
- artifact signature/marketplace/update service（单独安全设计）。
