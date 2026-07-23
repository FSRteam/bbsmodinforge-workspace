# Addon/API 2.0 技术设计

## 范围

本设计只覆盖 `new` 中的 NeoForge 1.21.1 代码库。`old` 不参与实现、不参与迁移基线、不作为自动检测目标。所谓“旧 addon”在本设计中只指 `new` 当前已经存在的 Addon v1 / compat 使用方式。

目标不是一次性重写 BBS 内部系统，而是在现有 NeoForge 生命周期上增加一层稳定、可版本化、可诊断的公共 API，并把当前 v1 行为收束为明确兼容层。

## 参考依据

- `new/src/main/java/mchorse/bbs_mod/BBSMod.java`：当前启动生命周期、addon 收集窗口、内部事件桥接、forms/source packs/settings/network 初始化顺序。
- `new/src/main/java/mchorse/bbs_mod/addon/*`：现有 Addon v1 协议、collector、bridge、自检。
- `new/src/main/java/mchorse/bbs_mod/loader/NeoForgeLoaderAccess.java`：`bbs-addon` lookup、ModList scan diagnostics、一次性告警。
- `new/src/main/java/mchorse/bbs_mod/events/EventBus.java`：内部反射事件总线和 handler 异常隔离。
- `new/src/client/java/mchorse/bbs_mod/client/compat/ClientApiCompat.java`：客户端过渡 API。
- `new/src/main/java/mchorse/bbs_mod/network/compat/NetworkCompat.java`：当前冻结 payload 兼容层。
- `docs/plan/02-common-lifecycle/addon-protocol-v1.md`：Addon v1 现有规范。
- `docs/plan/11-loader-resource-access/migration-spec.md`：LoaderAccess 兼容边界。
- `docs/plan/04-networking/migration-spec.md`：网络迁移约束。
- NeoForge 1.21.1 官方文档：mod event bus、`mods.toml`、payload registration。
- Blender Python API / RNA 参考：稳定元数据、注册/注销、有限公开类型、运行时校验、文档化入口。

## 设计原则

- 公共 API 最大化开放的是“能力面”，不是内部可变对象。Addon 能注册资源、forms、settings、clips、particles、render hooks、network payloads，但不能直接持有内部 manager 并任意改写。
- API 2.0 必须版本化。每个 addon 都要能声明 API 版本、addon 版本、能力、依赖、运行端和兼容策略。
- API 2.0 必须兼容当前 v1。v1 addon 继续按当前 collector/bridge/event 语义运行，并收到一次性迁移提示。
- 任何 addon 失败都默认隔离到 addon 自身。核心 BBS 启动不应因第三方 addon 的注册异常而崩溃。
- 注册必须有生命周期窗口。窗口关闭后默认拒绝结构性注册，只允许显式标注为 runtime-safe 的扩展。
- common 与 client API 必须分包，避免服务端加载客户端类。
- 诊断必须能回答三个问题：谁注册了什么、为什么被拒绝、当前走的是 v2 还是 v1。

## 公共包边界

建议新增稳定公共包：

- `mchorse.bbs_mod.api`：公共入口、版本常量、异常类型、通用 id/diagnostics。
- `mchorse.bbs_mod.api.addon`：addon 描述符、生命周期接口、兼容分类。
- `mchorse.bbs_mod.api.registry`：通用 registry facade 与注册结果。
- `mchorse.bbs_mod.api.resources`：source pack / asset provider facade。
- `mchorse.bbs_mod.api.forms`：form、form category 注册 facade。
- `mchorse.bbs_mod.api.settings`：settings 注册 facade。
- `mchorse.bbs_mod.api.clips`：camera/action clip 注册 facade。
- `mchorse.bbs_mod.api.particles`：particle component / factory 注册 facade。
- `mchorse.bbs_mod.api.network`：v2 payload facade 与 legacy raw-buffer bridge。
- `mchorse.bbs_mod.api.render`：common-safe render contracts，仅放不触发客户端类加载的接口。
- `mchorse.bbs_mod.api.client`：client-only API，放在 `src/client/java`。
- `mchorse.bbs_mod.api.diagnostics`：addon 状态、错误、统计快照。

内部适配层保留在现有包或新增 `mchorse.bbs_mod.addon.v2` / `mchorse.bbs_mod.addon.compat`，不承诺给 addon 作者直接依赖。

## 核心类型

### Addon 描述符

`BBSAddonDescriptor` 应为不可变对象，至少包含：

- `addonId`：稳定 id，建议与 NeoForge mod id 或声明 namespace 一致。
- `displayName`：日志和诊断使用。
- `addonVersion`：addon 自身版本。
- `apiVersion`：目标 BBS API 版本，例如 `2.0`。
- `acceptedApiVersions`：可选范围，用于未来 2.x 兼容。
- `side`：`COMMON`、`CLIENT`、`DEDICATED_SERVER`。
- `capabilities`：声明需要的能力，例如 `FORMS`、`SETTINGS`、`SOURCE_PACKS`、`CLIENT_RENDER`、`NETWORK`。
- `requiredMods` / `optionalMods`：依赖的 NeoForge mod id。
- `namespaces`：允许注册的资源 namespace。
- `compatPolicy`：是否允许降级到 v1/current compat。

校验规则：

- `addonId` 不能为空，只允许小写字母、数字、下划线、连字符、点号。
- 禁止第三方 addon 默认注册到 `bbs` namespace；如确需共享 namespace，必须在 descriptor 中显式声明并输出 WARN。
- `apiVersion` 主版本不匹配时默认拒绝 v2 生命周期，尝试 compat policy。
- 缺少必要 mod dependency 时跳过 addon 并记录为 `SKIPPED_DEPENDENCY`。

### Addon 生命周期接口

建议公共接口：

```java
public interface BBSAddon
{
    BBSAddonDescriptor descriptor();

    default void discover(BBSAddonContext context) {}
    default void register(BBSAddonRegistrationContext context) {}
    default void setup(BBSAddonSetupContext context) {}
    default void clientRegister(BBSClientAddonContext context) {}
    default void clientSetup(BBSClientAddonContext context) {}
    default void unload(BBSAddonContext context) {}
}
```

生命周期映射到当前 `BBSMod`：

- `DISCOVER`：收集 addon 和 descriptor，对应当前 `BBSAddonRegisterEvent` / `BBSMod.registerAddon(...)` 收集窗口。
- `REGISTER_COMMON`：在 source packs、forms、clips、settings 初始化时提供 facade，对应当前内部 `RegisterSourcePacksEvent`、`RegisterFormsEvent`、`RegisterSettingsEvent` 等事件。
- `COMMON_SETUP`：所有 common 注册结束后调用，适合校验和缓存。
- `REGISTER_CLIENT`：client sourceSet 中执行，只能接触 client facade。
- `CLIENT_SETUP`：客户端桥接完成后执行。
- `RUNTIME`：tick、render、server events、network receive 等运行期回调。
- `UNLOAD`：server stop / client stop 的尽力清理；不承诺热卸载 mod。

每个生命周期 callback 都由 `AddonExecutionBoundary` 包裹：

- 捕获 `Exception` 和 `LinkageError`。
- 记录 addon id、phase、callback、协议版本、来源 mod。
- 将 addon 标记为 `FAILED_PHASE`，后续是否继续取决于 capability 是否 critical。
- 默认不向外抛出，开发环境可启用 strict diagnostics。

## 自动检测与兼容分类

检测结果用 `BBSAddonProtocol` 表示：

- `API2_DECLARED`：提供 `BBSAddonDescriptor` 并注册 `BBSAddon`。
- `API1_REGISTERED`：通过当前 `BBSAddonRegisterEvent` 或 `BBSMod.registerAddon(...)` 注册 `BBSAddonMod`。
- `CURRENT_COMPAT`：使用 `client.compat`、`network.compat` 或当前内部 register event 的 addon。
- `METADATA_ONLY`：scan 或 metadata 显示像 addon，但没有可执行注册入口；只诊断，不自动 classload。
- `INVALID`：id、版本、side、dependency 或 class contract 不合法。

检测来源按优先级合并：

1. API 2.0 显式注册入口。
2. 当前 v1 collector 已收集对象。
3. `LoaderAccess#getEntrypoints("bbs-addon", BBSAddonMod.class)`。
4. `NeoForgeLoaderAccess` 的 ModList scan diagnostics。
5. 可选 metadata hints，例如 `mods.toml` 的自定义属性；只作为诊断增强，不作为唯一加载机制。

兼容策略为 `兼容优先 + 明确警告 + 逐步迁移`：

- v2 addon 走 v2 生命周期。
- v1/current addon 保持现有语义，通过 `LegacyAddonAdapter` 进入 API 2 诊断索引，同时继续桥接到内部 `EventBus`。
- 重复 addon id 继续 first-wins + reject-later，但诊断中记录 kept/rejected class、protocol、source mod。
- metadata-only 不崩溃、不隐式加载，输出迁移提示。

## 注册上下文与 facade

`BBSAddonRegistrationContext` 是注册期唯一入口：

- `resources()`：注册 source pack / asset source。
- `forms()`：注册 form type、form factory、form category。
- `settings()`：注册 settings module。
- `clips()`：注册 camera clip、action clip、converter。
- `particles()`：注册 particle component、runtime factory、UI editor hooks。
- `network()`：注册 addon-scoped payload。
- `events()`：注册 BBS runtime hooks。
- `diagnostics()`：记录 addon 自定义诊断信息。

每个 facade 返回 `BBSRegistrationResult`：

- `accepted(id)`
- `duplicate(id, keptBy)`
- `rejected(id, reason)`
- `skipped(id, dependency)`
- `deferred(id, phase)`

facade 规则：

- 不暴露 `FormArchitect`、`MapFactory`、`AssetProvider`、`UIDashboard`、`L10n` 等内部 mutable 实例。
- facade 内部转换为现有 event 或 manager 调用。
- registry 使用 `LinkedHashMap` 保持确定性顺序。
- 默认 first-wins；允许 replace 的扩展点必须显式声明并记录。
- 关闭窗口后结构性注册拒绝，runtime hook 可继续注册到 runtime-safe 列表。

## 事件模型

API 2.0 不要求 addon 直接依赖内部 `EventBus` 的反射 `@Subscribe`。推荐：

- v2 addon 用 lifecycle + typed hooks。
- v1 addon 继续使用 `BBSAddonMod` + `@Subscribe` 接当前事件。
- 内部仍可复用 `EventBus`，但由 adapter 负责把 v2 facade registration 转为内部行为。

公共事件需要满足：

- 事件类型稳定。
- 参数只读或 facade 化。
- handler 异常隔离。
- 事件有明确触发 phase。
- 不在 common API 中出现 client-only 类。

## 扩展点规范

### Source packs / assets

- v2 公开 `BBSResourceRegistry`。
- addon 可注册 `ResourcePackSource` 或 BBS asset source facade。
- 现有 `RegisterSourcePacksEvent(AssetProvider)` 保留为 v1 兼容。
- 资源 namespace 默认必须属于 addon descriptor。

### Forms

- v2 公开 `BBSFormRegistry`。
- 支持 form type、serializer/factory、display metadata、category。
- 内部 adapter 写入 `FormArchitect` 和 `RegisterFormCategoriesEvent`。
- 当前直接暴露 `FormArchitect` 的 v1 event 保留。

### Settings

- v2 公开 `BBSSettingsRegistry`。
- addon 注册 settings module descriptor 和 builder callback。
- adapter 内部调用 `BBSMod.setupConfig(...)`。
- 注册必须发生在 settings 窗口内，避免资源 reload 早期空引用。

### Camera / action clips

- v2 公开 `BBSClipRegistry`。
- 注册 clip class、icon、color、converter metadata。
- adapter 内部写入当前 `MapFactory<Clip, ClipFactoryData>`。
- 禁止 addon 直接修改 `BBSMod.getFactoryCameraClips()` 返回的 mutable factory；该 getter 可逐步标记为 internal/legacy。

### Particles

- v2 公开 `BBSParticleRegistry`。
- 第一阶段只开放 component/factory 注册，不承诺完整编辑器 UI API。
- 后续可扩展 editor panel、preview renderer、serialization schema。

### Render / client

- common 包只放 `IBbsWorldRenderContext` 这类不触发 Minecraft client 类加载的接口。
- client-only facade 放 `src/client/java/mchorse/bbs_mod/api/client`。
- 现有 `ClientApiCompat` 继续作为 v1/current compat，输出迁移 warning。
- renderer registration 通过 NeoForge client event adapter 执行。

### Network

- v1/current 继续使用 `NetworkCompat` 的冻结 raw buffer 通道。
- v2 公开 `BBSNetworkRegistry`，按 addon namespace 注册 payload id、direction、version、codec、handler。
- v2 payload 默认使用 NeoForge typed `CustomPacketPayload` / `StreamCodec` 风格。
- raw `FriendlyByteBuf` 只保留为 legacy bridge，并在文档中标注不推荐。
- payload 注册窗口绑定 NeoForge `RegisterPayloadHandlersEvent`；窗口关闭后拒绝新 payload type。
- 每个 addon 的 network version 单独记录，避免与 BBS core frozen channel 混在一起。

## 诊断与自检

新增 `BBSAddonDiagnostics`：

- `addonId`
- `displayName`
- `modId`
- `protocol`
- `apiVersion`
- `state`
- `acceptedRegistrations`
- `rejectedRegistrations`
- `warnings`
- `failedPhase`
- `lastErrorClass`

状态机：

- `DISCOVERED`
- `ACCEPTED`
- `BRIDGED_LEGACY`
- `REGISTERED_COMMON`
- `REGISTERED_CLIENT`
- `READY`
- `SKIPPED`
- `FAILED`

自检扩展当前 `BBSAddonProtocolSelfCheck`：

- 验证 v2 descriptor 校验。
- 验证 v1 lookup 仍可用。
- 验证 duplicate first-wins。
- 验证 late registration warning。
- 验证 client-only API 不在 dedicated server 加载。
- 验证 NetworkCompat frozen payload 不被 v2 改动。

## 文档与示例

实现阶段应新增：

- `new/docs/addon-api-2.md`：面向 addon 作者的规范。
- `new/src/main/java/.../api/**`：Javadocs。
- `new/src/main/java/.../addon/demo` 或新 demo：同时覆盖 v2 common、client、network、v1 compat。

根目录 `docs/` 可继续存研究资料和外部参考，但后续本地 commit 只包含 `new`，因此需要提交的用户文档应放在 `new/docs`。

## 主要权衡

- 直接暴露内部 manager 最开放，但会放大崩溃面和未来重构成本；本设计选择 facade，以注册能力开放换稳定边界。
- 严格拒绝 v1 可减少兼容负担，但会破坏当前迁移成果；本设计保留 v1 并输出迁移提示。
- metadata 自动 classload 能接近 Fabric entrypoint 体验，但 NeoForge 下风险更高；本设计只把 metadata 作为诊断和提示，执行入口仍要求显式注册。
- 一次性完整改造范围过大；实施应先搭 API 骨架和兼容索引，再逐步迁移各扩展点。

## 回滚形态

- API 2.0 所有入口默认通过 manager/adapter 接入，保留当前 v1 collector 和 internal EventBus。
- 若 v2 出现问题，可关闭 v2 discovery，只保留 v1 路径。
- `NetworkCompat` frozen payload 不被 API 2.0 修改；网络失败可单独回滚 v2 network facade。
- `new/docs/addon-api-2.md` 与 demo 可独立回滚，不影响核心启动。
