# BBSFSR 的 addon 协议（两代并存）与外部模组接入路径

来源：`new/src/main/java/mchorse/bbs_mod/{addon,api/addon}/**`、`BBSMod.java`、`api/client/BBSClientApi.java`

## 1. 两代协议

BBSFSR 同时维护 API 1.x 与 API 2.0，二者由不同的收集器管理，**可以共存**（`BBSMod` 各有独立的
`registerAddon` 重载与独立的 pending 队列，`bindAddonSystems` 同时绑定 collector 与 manager）。

### API 1.x —— `mchorse.bbs_mod.events.BBSAddonMod`

```java
public interface BBSAddonMod {}   // 空标记接口
```

其 javadoc 直接给出了外部模组的接入规则（原文）：

> Internal addons may register through `BBSAddonRegisterEvent` on BBS' mod bus.
> **External NeoForge mods should call `BBSMod.registerAddon(...)` during mod construction**,
> because their own mod bus does not receive events posted on BBS' mod bus.
> Collected addons are then resolved by `LoaderAccess#getEntrypoints("bbs-addon", ...)`.

- 注册入口：`BBSMod.registerAddon(String addonId, Supplier<? extends BBSAddonMod> supplier)`（`BBSMod.java:971`）
- 行为：入 `pendingAddonRegistrations` 队列，collector 就绪后落地（`BBSMod.java:1080`）
- 事件消费：实现类上写 `@Subscribe`（`mchorse.bbs_mod.events.Subscribe`）标注的方法，
  `BBSAddonCollector.bridgeTo(EventBus)`（`:239`）把 addon 桥接进 `BBSMod.events`
- 注册窗口有关闭时机：`closeRegistrationWindow()`（`:313`）、`closeExternalRegistrationWindow()`（`:324`）
  → **必须在 mod 构造期注册，晚于此则被拒**
- 另有 Fabric 侧兼容：`FabricAddonEntrypointImporter` 用纯反射读 `bbs-addon` entrypoint，
  Connector 环境下自动导入；纯 NeoForge 安装下 `Class.forName` 失败即静默跳过（`:179-184`）

### API 2.0 —— `mchorse.bbs_mod.api.addon.BBSAddon`

```java
public interface BBSAddon
{
    BBSAddonDescriptor descriptor();
    default void discover(BBSAddonContext context) {}
    default void register(BBSAddonRegistrationContext context) {}
    default void setup(BBSAddonSetupContext context) {}
    default void unload(BBSAddonContext context) {}
}
```

- 注册入口：`BBSMod.registerAddon(BBSAddonDescriptor, Supplier<? extends BBSAddon>)`（`:817`）
  或 `BBSMod.registerAddon(Supplier<? extends BBSAddon>)`（`:826`，descriptor 从 addon 取）
- 每个回调都被 `BBSAddonExecutionBoundary` 包住 —— 单个 addon 崩溃不会拖垮核心启动
- 结构化注册门面（`BBSAddonRegistrationContext`）：
  `resources()` / `forms()` / `settings()` / `clips()` / `particles()` / `network()` / `events()`
  - `BBSFormRegistry.register(Link id, Class<? extends Form> formType)` → `BBSRegistrationResult`
  - `BBSResourceRegistry.registerSourcePack(ISourcePack)` / `registerSourcePackFirst(ISourcePack)`
  - **没有** form renderer / form panel / icon / L10n 门面 → 客户端侧仍需 `BBSClientApi` + 静态注册
- 官方示例：`addon/demo/BBSAddonDemoApi2Mod.java`

### `BBSAddonDescriptor`

`builder(String addonId)` + `displayName` / `addonVersion` / `apiVersion` / `side` /
`compatPolicy` / `capability(BBSAddonCapability)` / `requiredMod` / `optionalMod` / `namespace`。

`BBSAddonCapability` 枚举：`SOURCE_PACKS, FORMS, SETTINGS, CLIPS, PARTICLES, CLIENT_RENDER, CLIENT_UI, NETWORK, EVENTS`

**无 authors / description / icon / homepage 字段** —— 上游 `AddonInfo` 的展示信息在 BBSFSR 无处安放。

## 2. 事件总线只有一条

BBSFSR 全部注册事件都 post 到 `BBSMod.events`（`public static final EventBus`，`BBSMod.java:165`）：

| 事件 | 派发点 |
|---|---|
| `RegisterL10nEvent` | `BBSModClient.java:400` |
| `RegisterClientSettingsEvent` | `BBSModClient.java:425` |
| `RegisterDashboardPanelsEvent` | `UIDashboard.java:94` |
| `RegisterFormsEvent` | `BBSMod` 表单初始化期 |

`EventBus` API：`register(Object)`（`:23`）、`registerCloseable(Object)`（`:32`）、
`subscribe(Class<T>, Consumer)`（`:88`）、`post(Object)`（`:155`）。

推论：上游把 addon 拆成 common(`BBSPhotonAddon`) + client(`BBSPhotonClientAddon`) 两个类，
在 BBSFSR 下**可以合并成一个 `BBSAddonMod`**，也可以保留两个类分别注册到同一条总线 —— 后者改动更小。

## 3. 各注册需求的落地通道汇总

| 需求 | 上游写法 | BBSFSR 通道 |
|---|---|---|
| 注册 Form 类型 | `RegisterFormsEvent.getForms().register(Link, Class)` | `@Subscribe registerForms(RegisterFormsEvent e)` → `e.register(Link, Class)`（`RegisterFormsEvent.java` 有此便捷方法）；或 API 2.0 `context.forms().register(...)` |
| 注册 Form 渲染器 | `RegisterFormsRenderersEvent.registerRenderer(...)` | `FormUtilsClient.register(PhotonForm.class, PhotonFormRenderer::new)`（`:102`） |
| 注册 Form 编辑面板 | `RegisterFormsRenderersEvent.registerPanel(...)` | `UIFormEditor.register(PhotonForm.class, UIPhotonForm::new)`（`:147`） |
| 注册图标 | `RegisterIconsEvent.register(Icon)` | `Icons.register(Icon)`（`:260`） |
| 注册 source pack | `BBSMod.getProvider().register(new InternalAssetsSourcePack(...))` | **完全一致，无需改动** |
| 注册 L10n | `RegisterL10nEvent` / `BBSModClient.getL10n().register(...)` | **完全一致，两条都在** |
| 注入 Extra 表单分类 | `BBSModClient.getFormCategories().getExtraForms().getExtraCategory().addForm(...)` | **完全一致**（`BBSModClient.getFormCategories()` 在 `:170`）；亦有 `RegisterFormCategoriesEvent` |
| 关键帧编辑器覆写 | 读写 `UIKeyframeFactory.FACTORIES`（public） | `FACTORIES` 已私有化 → 用 `UIKeyframeFactory.register(KeyframeFactories.STRING, factory)`（`:74`）；要委托旧工厂需反射或直接 `new UIStringKeyframeFactory(...)` |
| 轨道颜色/图标 | 反射 `UIReplaysEditor.COLORS` / `ICONS` | 字段仍私有，**反射方案继续可用且是唯一路径** |
| 客户端 tick / 启动钩子 | Fabric `ClientTickEvents` / `ClientLifecycleEvents` | `BBSClientApi.onStartClientTick` / `onEndClientTick` / `onClientStarted` |
| Addons 展示面板 | `BBSModClient.registerAddon(AddonInfo)` | **无等价物，删除** |
| 渲染器/面板反注册 | 无 | BBSFSR 额外提供 `FormUtilsClient.unregisterPluginRenderer`（`:112`）、`UIFormEditor.unregisterPluginPanel`（`:157`） —— 为插件热卸载准备，本任务可选用于 `unload` 回调 |

## 4. 构建依赖形态

上游 `build.gradle` 依赖链（Sinytra 路线）：

```
additionalRuntimeClasspath org.sinytra:connector          ← BBS CML 是 Fabric 模组才需要
implementation org.sinytra.forgified-fabric-api:...       ← 同上
implementation maven.modrinth:bbs-cml-edition:1.10.3-1.21.1
implementation maven.modrinth:photon-editor:mc1.21.1-2.1.4-neoforge   ← 原生 NeoForge 版
implementation maven.modrinth:ldlib:mc1.21.1-2.2.0-neoforge           ← Photon 的依赖
implementation maven.modrinth:{sodium,iris,watermedia}                ← 与 addon 功能无关
```

换成 BBSFSR 后：

- `connector` + `ffapi` **不再必需**（BBSFSR 是原生 NeoForge）→ 可移除，同时移除 `org.sinytra.adapter.userdev` 插件
- `bbs-cml-edition` → 换成 BBSFSR 本地产物 `new/build/libs/BBS_FSR_beta_v0.0.10.jar`（7.9 MB，已存在）
  - BBSFSR 有 `maven-publish`（`new/build.gradle:88,708`），可 `publishToMavenLocal` 后按坐标依赖；
    或直接 `flatDir` / `files(...)` 引用 jar
  - BBSFSR 分 `main` / `client` 两个 sourceSet（`new/build.gradle:144-154`），
    fat jar `archiveFileName = "${archives_base_name}.jar"`（`:688`）两者都含 → 单 jar 依赖即可
- `photon-editor` + `ldlib` 保留（原生 NeoForge 版）
- `sodium` / `iris` / `watermedia` 与本 addon 功能无关，可移除以缩短解析链

## 5. 推荐接入方案

**API 1.x 为主 + API 2.0 descriptor 为辅**，理由：

1. 上游全部注册逻辑基于 `@Subscribe` + `Register*Event`，而这些事件在 BBSFSR **原样存在** →
   API 1.x 路径下 `BBSPhotonAddon` / `BBSPhotonClientAddon` 只需换基类为 `implements BBSAddonMod`，方法体不动。
2. `BBSAddonMod` javadoc 明确指定外部 NeoForge 模组走 `BBSMod.registerAddon(String, Supplier)`。
3. API 2.0 的 registration context 缺少 form renderer / panel / icon / L10n 门面，
   客户端注册无论如何都要走 `FormUtilsClient` / `UIFormEditor` / `Icons` / `BBSClientApi` 静态入口 ——
   即用 API 2.0 也省不掉这部分。
4. 保留一个 `BBSAddonDescriptor` 常量并走 API 2.0 注册，可获得 capability 声明与
   `BBSMod.getAddonDiagnostics()` 诊断可见性，弥补 Addons 面板的缺失。
