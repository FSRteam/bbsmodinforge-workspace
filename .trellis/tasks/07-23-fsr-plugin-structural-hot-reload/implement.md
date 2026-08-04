# 执行计划：结构性能力光影式热重载

执行者：GPT/Codex（sub-agent）。每个 dispatch prompt 必须以 `Active task: .trellis/tasks/07-23-fsr-plugin-structural-hot-reload` 开头。
上下文顺序：`implement.jsonl` → `prd.md` → `design.md` → 本文件。
工作目录：`new/`（独立 git 仓库）。验证命令均在 `new/` 下执行（Windows 用 `gradlew.bat`）。

## 全局约束

- 只改 `new/`；SPI additive-only；Addon API 2.0 与既有 8 项 hotSafe 能力零回归。
- 每个 Phase 结束：编译 + 相关测试通过后 commit（一个 Phase 一个回滚点）；commit 信息前缀 `plugin-structural-reload:`。
- 遇到与 design.md 冲突的事实（如既定落点不可行）：停下记录到 `research/`，回到主会话复核，不得擅自改设计边界。
- capability 翻转（Phase 6）之前，所有基建改动对现网行为必须零影响（六项能力仍被拒），保证每个中间 commit 可独立发布。

## 当前进度（2026-07-27）

- Phase 0-6 已由 `new/` 提交 `3193d7a8` 实现。
- 本轮在当前集成工作区执行干净验证：`testPluginHotReload` 通过（含 100 次 1.0/2.0 交替重载），`compileJava`、`compileClientJava`、`pluginHotReloadTestClasses`、`testAddonLifecycle`、`testClientStructuralRegistration`、`testBinaryCompatibility`、`testNetworkSecurity` 全部通过。
- `buildSmokeFixturePlugins` 通过，已生成 `new/build/smoke-plugins/bbssmokefixture-1.0.0.jar` 与 `bbssmokefixture-2.0.0.jar`。
- 真实 NeoForge client 生命周期冒烟通过：唯一稳定源 `bbssmokefixture.jar` 从 1.0 generation 1 `ACTIVE/COMMIT` 原子替换为 2.0 generation 2 `ACTIVE/COMMIT`，删除后 generation 2 `DRAIN/UNLOAD`，恢复 1.0 后 generation 3 `ACTIVE/COMMIT`；最终稳定源 SHA-256 回到 1.0。带版本号的 sibling JAR 会按合同触发 `DUPLICATE_ID`，移除 sibling 后的完整链路未再出现该错误。
- 客户端窗口优雅退出验证通过：generation 3 正常 `DRAIN/UNLOAD`，日志无 `hot plugin shutdown did not complete cleanly`；新增 `testClientStructuralRegistration` 生产接线回归检查并通过。
- 2026-07-27 最终复验：当前工作树 `compileClientJava` 与 `testPluginHotReload` 通过（含 100 次交替换代与缓存/classloader 基线回归）。
- 剩余发布门仅为 NeoForge client / dedicated server 手动 smoke 与用户确认收尾。

## Phase 0 — 研究与盘点（只写 research/，不改产品代码）

产出到 `.trellis/tasks/07-23-fsr-plugin-structural-hot-reload/research/`：

- [x] `holders-inventory.md`：form/clip/particle 实例持有点全量清单。扫描起点：film/replay 数据模型（replay form 属性、keyframe 内嵌 form）、timeline camera/action clips、`UIFilmPanel`、form 编辑器打开态、`FormCategories`（recent/user）、`ActionPlayer`、粒子 emitter、morph/model editor。每条记录：类、字段、读写线程、序列化路径。
- [x] `missing-type-behavior.md`：`FormUtils.fromData` / clip factory 反序列化遇到未注册类型的现状（返回 null？丢数据？异常？），以及打开缺 addon 存档的既有容错路径；给出"未知类型数据保持器"是否需要新建的结论。
- [x] `keymapping-runtime.md`：NeoForge 1.21.1 运行期增删 KeyMapping 的可行落点（`Minecraft.options.keyMappings` 数组重放 + 索引重建 API + options.txt 持久化行为）；结论二选一：原生数组重放 or 宿主自行持久化插件键位。可用 `new/build/neoForm/*/steps/decompile/outputs.jar` 反编译源核对 Mojmap 真实签名。
- [x] `renderer-runtime.md`：`EntityRenderDispatcher`/`BlockEntityRenderDispatcher` 运行期重建两条候选路径（dispatcher 级 reload vs 定向替换条目）的可行性与成本；默认取稳妥方案（与资源重载同路径），给出结论。
- [x] `static-caches.md`：持有插件 Class/实例的静态缓存全量清单（起点：`FormUtilsClient.map`、form editor 注册表、`FormCategories`；grep `static.*Map<Class` 与 form/clip 相关 static 字段）。
- [x] `addon-capability-gating.md`：确认 Addon 2.0 registry facade 是否校验 capability 声明，热插件 facade 保持同一严格度。
- 验证：research 文件齐全、每份有明确结论；无产品代码 diff。
- Review gate：主会话审阅 research 结论与 design.md 一致性，冲突则先修订 design 再继续。

## Phase 1 — common 侧世代化注册基建

- [x] `MapFactory`：新增 `unregister(Link)` 正确实现（同时修 `factoryInverse`/`data` 键 bug）；旧 `unregister(String)` 保留并委托。补单元测试覆盖注册/撤销往返。
- [x] 新增 owner-aware 注册包装（design §2.2 `GenerationScopedRegistrar` 概念）：绑定 `(pluginId, generation)`，撤销动作进 `PluginContributionLedger`；宿主内置注册不经过它、行为不变。
- [x] 接入三个 common 注册面：`FormArchitect`、camera clips、action clips、particle 组件表（`BBSAddonManager.particleComponents()` 对应的宿主表）。
- 验证：`gradlew.bat compileJava pluginHotReloadTestClasses` + 新增单元测试通过；`testAddonLifecycle` 回归通过。
- 回滚点：commit `plugin-structural-reload: phase1 common generation-scoped registries`。

## Phase 2 — 客户端侧世代化注册基建

- [x] `FormUtilsClient.map`、form editor 注册表、`FormCategories`：加 owner-aware 注册/撤销入口（内置注册不变）；撤销后无插件 Class 残留（配测试断言）。
- [x] 新建 keymapping / entity renderer / block-entity renderer 三个世代化重放注册表（design §2.3），实现按 Phase 0 结论；含启动期语义等价路径（reconcile 时机加载的插件与 vanilla 窗口注册结果一致）。
- [x] `ClientApiCompat` 现有一次性窗口行为保持不变（`testClientStructuralRegistration` 回归）。
- 验证：`gradlew.bat compileClientJava` + 扩展的 `testClientStructuralRegistration` + 新增单元测试通过。
- 回滚点：commit `plugin-structural-reload: phase2 client replayable registries`。

## Phase 3 — SPI facade 扩展

- [x] 新增 `BBSPluginFormRegistry` / `BBSPluginClipRegistry` / `BBSPluginParticleRegistry` / `BBSPluginKeyMappingRegistry` / `BBSPluginRendererRegistry` 接口（`api.plugin` 包，形状对齐 addon 2.0 对应接口）与 `BBSPluginClientContext`。
- [x] `PluginGenerationContext` 实现新 facade：仅 prepare 阶段开放（复用 seal 模式）、注册进 candidate ledger、capability 未声明时抛 `IllegalStateException`。
- [x] client context 装配对齐现有 client entrypoint 机制；dedicated server 不解析 client facade 类。
- 验证：`gradlew.bat compileJava compileClientJava pluginHotReloadTestClasses`；facade seal/gating 单元测试。
- 回滚点：commit `plugin-structural-reload: phase3 spi facades`。

## Phase 4 — 光影式重载协调器

- [x] 新增 `PluginStructuralReloadCoordinator`：实现 design §2.6 时序（生命周期线程 prepare → 主线程安全点 guard/snapshot/teardown/publish/commit/rebuild/replay/UI 刷新 → 生命周期线程 drain/close）。
- [x] 主线程安全点任务机制（tick 边界一次性完成结构性 3~6 步）；dedicated server 分支（无客户端投影步骤）。
- [x] snapshot/rebuild 实现：按 Phase 0 `holders-inventory.md` 清单逐持有点接入；form/clip 走既有 `toData`/`fromData` 路径。
- [x] 导出/录制 guard（`VideoRecorder`/`VideoExportSession` 活跃 → `RELOAD_REJECTED_BUSY`，intent 保持待重试）。
- [x] `BBSPluginManager.applyPath` 接入协调器；kind=content 与纯 hotSafe 能力插件绕过 snapshot 流程（保持上一期行为）。
- 验证：`gradlew.bat compileJava compileClientJava` + snapshot-rebuild 往返测试、安全点时序测试（`pluginHotReloadTest` 扩展）。
- 回滚点：commit `plugin-structural-reload: phase4 reload coordinator`。

## Phase 5 — 缺失降级与失败路径

- [x] 按 Phase 0 `missing-type-behavior.md` 结论实现/加固：未知类型数据保持（保存回写不丢字段）、form 缺失占位、clip 跳过执行、particle 组件停用。
- [x] rebuild 单实例失败 → 占位 + 诊断，不回滚换代、不中断其余重建（design §3 失败矩阵逐条落地）。
- [x] 诊断状态补充：`RELOAD_REJECTED_BUSY`、rebuild 失败明细进 plugin diagnostics。
- 验证：缺失降级测试（打开/播放/保存 film 全链路无异常）、失败注入测试通过。
- 回滚点：commit `plugin-structural-reload: phase5 degradation and failure paths`。

## Phase 6 — capability 翻转 + fixture + 全量验收

- [x] `BBSPluginCapability` 六项翻转 hotSafe=true；`PluginArtifactValidator` 放行核对（`STRUCTURAL_ENTRY`、`RELOAD_RESTART`、后 6 项 capability 拒绝逻辑不动，配负向测试）。
- [x] `FixtureJarBuilder` 扩展：结构性能力 fixture 插件 1.0/2.0（form+clip+particle+keymapping+renderer），含 rebuild 失败注入变体。
- [x] `testPluginHotReload` 扩展：prd 验收清单可自动化项逐条（安装/换代/卸载恢复/降级/100 次交替 reload 基线与泄漏）。
- 验证（全量门）：`gradlew.bat compileJava compileClientJava pluginHotReloadTestClasses` + `testPluginHotReload` + `testAddonLifecycle` + `testClientStructuralRegistration` + `testBinaryCompatibility`。
- 回滚点：commit `plugin-structural-reload: phase6 capability flip and acceptance`。

## Phase 7 — 手动 smoke 与收尾（主会话/用户参与）

- [ ] NeoForge client 手动 smoke：prd 验收清单中 UI/观感项（form 列表、clip picker、按键设置、renderer 生效、换代闪断观感、导出 guard 提示）。
- [ ] dedicated server smoke：common 能力加载/换代/卸载。
- [x] 更新 spec：`.trellis/spec/new/backend/hot-plugin-runtime-contract.md` 增补结构性能力合同（世代化注册、安全点、snapshot/rebuild、降级矩阵）。
- [ ] 用户确认后按仓库惯例提交收尾（`new/` 仓库，须用户确认后 commit）。
