# 上游 API 依赖面 × BBSFSR 存在性核对

上游：`https://github.com/ElGatoPro300/bbs-photon-addon`（clone 快照置于会话 scratchpad `upstream-photon/`）
比对目标：`new/`（BBS FS mod，NeoForge 1.21.1，`mod_id=bbs`，`archives_base_name=BBS_FSR_beta_v0.0.10`）

## 1. 上游规模

| 文件 | 行数 | 职责 |
|---|---|---|
| `BBSPhoton.java` | 79 | `@Mod` 主入口，注册 addon + form（含重复兜底） |
| `BBSPhotonAddon.java` | 29 | `extends BBSAddon`，`@Subscribe registerForms` |
| `client/BBSPhotonClient.java` | 231 | 客户端 bootstrap：keyframe factory 覆写、轨道颜色/图标反射注入、tick 看门狗、source pack、L10n、Addons Panel 注册、Extra 分类注入 |
| `client/BBSPhotonClientAddon.java` | 78 | `extends BBSClientAddon`，6 个 `@Subscribe` |
| `client/gui/UIPhotonForm.java` | 19 | `extends UIForm<PhotonForm>` |
| `client/gui/UIPhotonFormPanel.java` | 196 | 编辑面板（FX 选择、暂停开关） |
| `client/gui/UIPhotonFxKeyframeFactory.java` | 52 | `photon_fx` 字符串关键帧的自定义编辑控件 |
| `client/render/PhotonFormRenderer.java` | 834 | 核心：`FormRenderer<PhotonForm> implements ITickable`，桥接 Photon `EntityEffectExecutor` |
| **合计** | **1537** | + 语言/贴图资源 10 个文件 |

## 2. BBS API 存在性核对（46 项）

包名前缀均为 `mchorse.bbs_mod.`。核对方式：对 `new/src/**/*.java`（1704 个 java 文件）做路径精确匹配。

### 2.1 存在且路径一致（40 项）

`BBSMod`、`BBSModClient`、`events.Subscribe`、`events.register.RegisterFormsEvent`、
`events.register.RegisterFormCategoriesEvent`、`events.register.RegisterL10nEvent`、
`events.register.RegisterDashboardPanelsEvent`、`forms.ITickable`、`forms.forms.Form`、
`forms.entities.IEntity`、`forms.FormUtilsClient`、`forms.renderers.FormRenderType`、
`forms.renderers.FormRenderer`、`forms.renderers.FormRenderingContext`、`graphics.texture.Texture`、
`l10n.L10n`、`resources.Link`、`resources.packs.InternalAssetsSourcePack`、
`settings.values.core.ValueString`、`settings.values.numeric.ValueBoolean`、
`ui.forms.editors.UIFormEditor`、`ui.forms.editors.forms.UIForm`、`ui.forms.editors.panels.UIFormPanel`、
`ui.film.replays.UIReplaysEditor`、`ui.framework.UIContext`、
`ui.framework.elements.buttons.{UIButton,UIIcon,UIToggle}`、
`ui.framework.elements.input.keyframes.{UIKeyframeSheet,UIKeyframes}`、
`ui.framework.elements.input.keyframes.factories.{UIKeyframeFactory,UIStringKeyframeFactory}`、
`ui.framework.elements.input.text.UITextbox`、
`ui.framework.elements.overlay.{UIOverlay,UIListOverlayPanel}`、
`ui.utils.icons.{Icon,Icons}`、`utils.keyframes.Keyframe`、
`utils.keyframes.factories.KeyframeFactories`、`utils.pose.Transform`

注意 main/client sourceSet 分裂：BBSFSR 把 `l10n.L10n`、`forms.FormUtilsClient`、`graphics.texture.Texture`、
`forms.renderers.*`、全部 `ui.framework.*` / `ui.forms.*` 放在 `src/client/java`，
`resources.Link`、`ui.utils.icons.*`、`utils.keyframes.*`、`settings.values.*` 放在 `src/main/java`。
addon 侧若也分 sourceSet，须按此划分；若单 sourceSet 编译则无影响（jar 内两者都在）。

### 2.2 缺失（6 项）

| 上游类 | BBSFSR 状态 | 替代方案 |
|---|---|---|
| `mchorse.bbs_mod.BBS` | 不存在 | `BBSMod`（`BBSMod.events` 公开字段替代 `BBS.getEvents()`；`BBSMod.getForms()` 替代 `BBS.getForms()`，返回 `FormArchitect`） |
| `addons.BBSAddon` | 不存在 | `events.BBSAddonMod`（API 1.x 标记接口）或 `api.addon.BBSAddon`（API 2.0 契约） |
| `addons.BBSClientAddon` | 不存在 | 无独立客户端 addon 基类；BBSFSR 只有一条总线 `BBSMod.events`，客户端事件也 post 到它（`BBSModClient.java:400,425`、`UIDashboard.java:94`） |
| `addons.AddonInfo` | 不存在 | **无等价物**。BBSFSR 客户端无任何 AddonInfo 引用，即没有 Addons 展示面板。身份信息只能落到 `api.addon.BBSAddonDescriptor`（无 authors/description/icon/URL 字段） |
| `events.register.RegisterFormsRenderersEvent` | 不存在 | `FormUtilsClient.register(Class, IFormRendererFactory)`（`:102`）+ `UIFormEditor.register(Class, Supplier<UIForm>)`（`:147`）—— 上游已把这两个当 fallback 用，直接转正 |
| `events.register.RegisterIconsEvent` | 不存在 | `Icons.register(Icon)`（`:260`，public static）或直接 `Icons.ICONS.put(...)`（`:10`，public static final Map） |

## 3. 签名 / 可见性差异（4 处）

| 位置 | 上游 | BBSFSR | 影响 |
|---|---|---|---|
| `FormRenderer.renderInUI` | `public void`（override） | `protected abstract`（`FormRenderer.java:103`） | 无：override 放宽可见性合法 |
| `FormRenderer.render3D` | `public void`（override） | `protected`（`FormRenderer.java:315`） | 无：同上 |
| `UIKeyframeFactory.FACTORIES` | `public static Map`，上游直接 `.get()` 读旧工厂再 `.put()` 委托 | **`private static final`**（`UIKeyframeFactory.java:34`），只暴露 `register(IKeyframeFactory<T>, IUIKeyframeFactoryFactory<T>)`（`:74`） | **实质适配点**：`register()` 只能覆盖不能读取。上游的「委托回原 STRING 工厂」模式需改为反射读取，或改为直接 `new UIStringKeyframeFactory(...)` 作为 fallback |
| `UIPanelBase.registerPanel` | `registerPanel(panel, L10n.lang(...), Icon)` | `registerPanel(T panel, IKey tooltip, Icon icon)`（`UIPanelBase.java:147`） | 需确认 `L10n.lang()` 返回 `IKey`（编译期验证） |

`UIReplaysEditor.COLORS` / `ICONS` 在 BBSFSR 仍为 `private static final Map`（`:86,87`），
上游的反射注入方案**继续可用**，且无公开注册通道 —— 反射是唯一路径。

## 4. Fabric API 依赖 → NeoForge 替代

上游有 3 处 Fabric 直接依赖（靠 Sinytra Connector 提供）。BBSFSR 是原生 NeoForge，
且已备好客户端兼容门面 `mchorse.bbs_mod.api.client.BBSClientApi`（`src/client/java/.../api/client/BBSClientApi.java`）：

| 上游（Fabric） | BBSFSR 门面 | 行号 |
|---|---|---|
| `ClientLifecycleEvents.CLIENT_STARTED.register(c -> …)` | `BBSClientApi.onClientStarted(Consumer<Minecraft>)` | `:82` |
| `ClientTickEvents.START_CLIENT_TICK.register(…)` | `BBSClientApi.onStartClientTick(Consumer<Minecraft>)` | `:97` |
| `ClientTickEvents.END_CLIENT_TICK.register(…)` | `BBSClientApi.onEndClientTick(Consumer<Minecraft>)` | `:107` |
| `net.fabricmc.loader.api.FabricLoader`（`UIPhotonFormPanel` 用于定位 Photon 配置目录） | 需换 NeoForge `FMLPaths` / `ModList` | — |

`BBSClientApi` 另有 `registerKeyBinding`、`onAfterEntities`、`onAfterLevel`、`onHudRender`、
`onClientStopping`、`onDisconnect`、`onEndWorldTick`、`registerEntityRenderer` 等，
均为 Fabric 事件的一一对应替代，本任务用到前三个即可。

## 5. 结论

- 40/46 个 BBS API 包名与位置**完全一致** —— 上游本就是照 BBS 上游（Fabric 版）写的，BBSFSR 忠实保留了包结构。
- 真正的适配工作集中在 **addon 注册协议**（6 个缺失项里 4 个）与 **Fabric 事件替换**（3 处）。
- `PhotonFormRenderer`（834 行，占总量 54%）依赖的 BBS API 全部存在且签名兼容，**预期零改动或仅改 import**。
- 唯一功能性缺口：**Addons 展示面板**在 BBSFSR 不存在，上游对应代码须删除（见 `prd.md` 非目标）。
