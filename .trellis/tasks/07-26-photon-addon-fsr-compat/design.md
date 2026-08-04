# 技术设计：BBS Photon Addon → BBSFSR

前置阅读：`prd.md` → `research/upstream-api-surface.md`（46 项 API 核对表）→
`research/fsr-addon-protocol.md`（两代 addon 协议与落地通道）。本文只写决策与差异。

## 1. 总体判断

上游本就是照 BBS 上游（Fabric 版）的 API 写的，BBSFSR 忠实保留了 `mchorse.bbs_mod.*` 包结构，
**40/46 个 API 零改动**。因此这不是「重写」，而是**三处定点手术**：

| 手术面 | 涉及文件 | 改动量 |
|---|---|---|
| A. addon 注册协议 | `BBSPhoton`、`BBSPhotonAddon`、`BBSPhotonClientAddon`、`BBSPhotonClient` | 重写这 4 个文件的注册段落 |
| B. Fabric → NeoForge / BBS 门面 | `BBSPhotonClient`（3 个事件）、`UIPhotonFormPanel`（2 处 `FabricLoader`） | 5 处替换 |
| C. 构建与元数据 | `build.gradle`、`gradle.properties`、`neoforge.mods.toml`、删 `fabric.mod.json` | 重写构建文件 |

`PhotonFormRenderer`（834 行，54%）、`PhotonForm`、`UIPhotonForm`、`UIPhotonFxKeyframeFactory`
**预期零改动或仅 import 不变**（已逐项核对签名，见下 §6）。

## 2. 工程结构

```
bbs-photon-addon/                     ← 新建，与 new/ 平级
├── LICENSE.md                        ← 原样保留（MIT）
├── README.md                         ← 注明上游 commit 与「本工程面向 BBSFSR」
├── build.gradle                      ← 重写（§3）
├── gradle.properties                 ← 重写（§3）
├── settings.gradle                   ← 保留（rootProject.name）
├── gradle/wrapper/                   ← 原样保留
├── gradlew / gradlew.bat             ← 原样保留
└── src/main/
    ├── java/elgatopro300/bbsphoton/  ← 8 个文件，4 个改 4 个几乎不动
    └── resources/
        ├── META-INF/neoforge.mods.toml   ← 改依赖声明
        └── assets/bbs_photon/**          ← 原样保留（10 个资源文件）
        （fabric.mod.json 删除）
```

**单 sourceSet（`main`）**：上游即单 sourceSet，NeoForge 不强制拆分；BBSFSR 之所以分 `main`/`client`
是它自身 Fabric 迁移的历史包袱。addon 直接依赖 BBSFSR 的 fat jar（两个 sourceSet 都在里面），
单 sourceSet 即可编译到 client 侧类。不引入无必要的结构复杂度。

## 3. 构建方案

### 3.1 依赖 BBSFSR

BBSFSR 未发布到任何远端 maven，用**本地 jar + 可配路径**：

```gradle
// gradle.properties
bbs_fsr_jar=../new/build/libs/BBS_FSR_beta_v0.0.10.jar

// build.gradle
dependencies {
    compileOnly files(project.bbs_fsr_jar)
    // 运行时由玩家安装的 BBSFSR 提供；若要 runClient 调试则改 additionalRuntimeClasspath
}
```

选 `compileOnly` 而非 `implementation`：addon 不该把 7.9 MB 的 BBSFSR 打进自己的 jar。
路径抽成 property 是为了 BBSFSR 版本号变化时只改一行（C2）。

备选（不采用）：`publishToMavenLocal` 后按坐标依赖 —— 多一个前置步骤，且 BBSFSR 的
`publishing` 配置（`new/build.gradle:708`）未验证坐标，暂不引入。

### 3.2 依赖裁剪

| 依赖 | 处置 | 理由 |
|---|---|---|
| `org.sinytra.adapter.userdev` 插件 | **删** | Fabric→NeoForge 适配层，BBSFSR 原生 NeoForge 不需要 |
| `net.neoforged.moddev` 插件 | **换成 `net.neoforged.gradle.userdev`** | 见 §3.2b —— moddev 的工具链下载绕过 Gradle repositories，在本环境不可用 |
| `org.sinytra:connector` | **删** | 同上 |
| `org.sinytra.forgified-fabric-api` | **删** | 同上（且 §4 已消除所有 `net.fabricmc.*` 引用） |
| `maven.modrinth:bbs-cml-edition` | **删** | 换成 §3.1 的本地 jar |
| `maven.modrinth:sodium` / `iris` / `watermedia` | **删** | 与本 addon 功能无关，纯粹拖慢依赖解析 |
| `maven.modrinth:photon-editor:mc1.21.1-2.1.4-neoforge` | **留** | 原生 NeoForge 版，`com.lowdragmc.photon.*` 的来源 |
| `maven.modrinth:ldlib:mc1.21.1-2.2.0-neoforge` | **留** | Photon 的依赖 |

NeoForge 版本对齐 BBSFSR 的 `neo_version=21.1.222`（上游是 `21.1.219`，取 BBSFSR 的值以免运行期
版本错配），属性名也用 `neo_version` 以便与 BBSFSR 对读。

### 3.2b 构建插件：从 moddev 改为 NeoGradle（实施期决策）

**规划时**沿用上游的 `net.neoforged.moddev 1.0.21`。**实测不可用**，改为 BBSFSR 同款
`net.neoforged.gradle.userdev 7.1.21`。逐层诊断过程（值得记下来，因为表象极具误导性）：

| 观察 | 当时的判断 | 真相 |
|---|---|---|
| 停在 `> Task :createArtifactManifest` 40 分钟，无日志、无缓存活动 | 以为是网络不通 | 不加 `--info` 看不到任何原因 |
| daemon 日志显示 `-Xmx512m` | 以为堆不足导致 stall | **误判**。堆偏小是真的（已加 `-Xmx2048m`），但不是卡住的原因 |
| `--info` 下报 `Could not download jst-cli-bundle-1.0.66.jar` + `Remote host terminated the handshake` | 以为 TLS 协议不兼容 | 仍不是。`curl` 同一 URL 返回 `http=200`，但 60 秒只下 1.26 MB / 34.8 MB（**≈21 KB/s 限速**），Gradle 的超时把慢速表现成了握手中断 |
| `resolutionStrategy.force` 到缓存中已有的 jst `1.0.67` 后，Gradle 解析过关、构建推进到 `transformSources` | 以为解决了 | 随即出现 `WARNING: net.neoforged.jst:jst-cli-bundle:1.0.66 is not present in the artifact manifest` + 它自己发起 `DL` |

**根因**：moddev 的 NeoForm 运行时（NFRT）**不走 Gradle 的依赖解析**，而是用自己的 HTTP 客户端按
自己的 artifact manifest 下载工具链。因此对它来说：Gradle 镜像无效、共享依赖缓存无效、
`resolutionStrategy.force` 只骗过 Gradle 层（反而让 manifest 与 NFRT 的期望版本对不上）。
在 `maven.neoforged.net` 被限速的环境里，moddev 没有可行的绕行方案。

**NeoGradle 为什么可行**：它把 NeoForge 当作普通 Maven 依赖
（`implementation "net.neoforged:neoforge:${neo_version}"`）解析，所有下载都经过 Gradle
repositories → 可以走 BMCLAPI 镜像，且 BBSFSR 已用同一插件同一版本构建成功，
`~/.gradle/caches` 里的 artifacts 全部命中。

随之而来的两处配套改动：

- `repositories` 与 `pluginManagement.repositories` 首位加
  `https://bmclapi2.bangbang93.com/maven`（BBSFSR 的第一选择，也是它能构建成功的原因）。
- NeoGradle 强制要求显式 java toolchain，否则配置阶段直接失败
  （`NeoGradle requires a configured java toolchain...`）→ 加
  `java { toolchain.languageVersion = JavaLanguageVersion.of(java_version) }` 与
  `java_version=21` 属性，形式照 `new/build.gradle:337-340`。

jst 的 `resolutionStrategy.force` 随 moddev 一并移除 —— NeoGradle 不需要它。

### 3.3 元数据

`neoforge.mods.toml` 依赖块：`neoforge [21.1,)` + `minecraft [1.21.1,)` + `bbs` + `photon`，
**删除 `connector` 依赖项**。

`bbs` 的 `versionRange` 必须从上游的 `[1.0.0,)` 改成 `[0.0.10,)`：BBSFSR 的 `mod_version` 是
`0.0.10`（`new/gradle.properties`），**上游的范围会直接拒绝 BBSFSR，游戏根本起不来**。
这是本次移植里最容易被忽略却致命的一处 —— 不是编译问题，是运行期加载器拒绝。

`ordering="AFTER"` 以确保 BBS 先于 addon 构造 —— 这对 §4.1 的注册窗口很关键。

删除 `fabric.mod.json`，同时 `processResources` 的 `filesMatching` 只保留 `META-INF/neoforge.mods.toml`。

## 4. addon 注册协议迁移（手术面 A）

### 4.1 决策：只走 API 1.x，不做 API 2.0 双注册

BBSFSR 有两代协议（详见 `research/fsr-addon-protocol.md` §1）。选 **API 1.x（`events.BBSAddonMod`）**：

1. 上游的注册逻辑全部基于 `@Subscribe` + `Register*Event`，而这些事件在 BBSFSR **原样存在**
   → 方法体不动，只换基类声明。
2. `BBSAddonMod` 的 javadoc 明确指定：*External NeoForge mods should call `BBSMod.registerAddon(...)`
   during mod construction*。这就是为本场景写的。
3. API 2.0 的 `BBSAddonRegistrationContext` 只有 `resources()/forms()/settings()/clips()/particles()/
   network()/events()` 门面，**没有** form renderer / panel / icon / L10n 门面 —— 用 API 2.0
   照样得走 `FormUtilsClient` / `UIFormEditor` / `Icons` 静态入口，省不掉任何工作。

**PRD R3.4 修正**：不做 API 2.0 双注册。API 1.x 的 collector 本身有
`getRegistrationDiagnostics()`（`BBSAddonCollector.java:295`），以 addonId 注册即获得诊断可见性。
仍定义一个 `BBSAddonDescriptor` 常量（`BBSPhoton.DESCRIPTOR`），供 `BBSClientApi` 的
descriptor-aware 重载在未来需要时使用，本次不参与注册。

### 4.2 注册时序

注册窗口会关闭（`BBSAddonCollector.closeExternalRegistrationWindow()`），所以**必须在 mod 构造期注册**：

```java
@Mod(BBSPhoton.MOD_ID)
public class BBSPhoton
{
    public static final String MOD_ID = "bbs_photon_addon";

    public BBSPhoton(IEventBus modEventBus)
    {
        /* 唯一的 common 侧注册路径 */
        BBSMod.registerAddon(MOD_ID, BBSPhotonAddon::new);

        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            BBSMod.registerAddon(MOD_ID + "_client", BBSPhotonClientAddon::new);
            BBSPhotonClient.init();
        }
    }
}
```

**删除上游的三重兜底**（PRD R3.5）：上游在构造期、`commonSetup`、`BBSPhotonClient.CLIENT_STARTED`
里各注册一次 `PhotonForm`，那是 Connector 环境下事件不确定投递的应对。BBSFSR 下
`RegisterFormsEvent` 投递路径确定（collector → `bridgeTo(BBSMod.events)`），保留单一路径。
`commonSetup` 监听器随之删除（不再有内容）。

client addon 与 common addon **注册成两个 addonId**：`BBSAddonCollector` 以 addonId 去重
（`BBSAddonIdentityRegistry`），同 id 注册两个对象会被拒。BBSFSR 只有一条总线，
两个 addon 对象都会被桥接到 `BBSMod.events`，客户端事件自然投递到 client addon。

### 4.3 两个 addon 类的改动

`BBSPhotonAddon`：`extends mchorse.bbs_mod.addons.BBSAddon` → `implements mchorse.bbs_mod.events.BBSAddonMod`，
`@Override` 去掉（接口无此方法），`@Subscribe registerForms(RegisterFormsEvent)` 方法体不动。

`BBSPhotonClientAddon`：`extends BBSClientAddon` → `implements BBSAddonMod`，然后：

| 上游 `@Subscribe` 方法 | 处置 |
|---|---|
| `registerDashboardPanels(RegisterDashboardPanelsEvent)` | **删**（实施期修正：事件虽存在，但上游方法体是空的，保留一个空订阅只是噪音） |
| `registerFormsRenderers(RegisterFormsRenderersEvent)` | **删**（事件不存在）→ 逻辑迁到 `BBSPhotonClient` 的静态注册（§5.1） |
| `registerFormCategories(RegisterFormCategoriesEvent)` | **删**（实施期修正：同上，方法体只有一句「实际在别处做」的注释；该注释迁到 `BBSPhotonClient` 的对应位置更有用） |
| `registerIcons(RegisterIconsEvent)` | **删**（事件不存在）→ 改 `Icons.register(...)`（§5.2） |
| `registerL10n(RegisterL10nEvent)` | 保留（事件存在），方法体不动 |

结果 `BBSPhotonClientAddon` 只剩 `registerL10n` 一个职责。

## 5. 客户端注册通道替换（手术面 A/B 交界）

`BBSPhotonClient.init()` 是改动最集中处。逐段设计：

### 5.1 渲染器与编辑面板

上游已把这两行当 fallback 写在 `CLIENT_STARTED` 里，现在**转正为唯一路径**，
且**不必等 CLIENT_STARTED** —— 这两个是纯静态 Map 注册，`init()` 里直接调用即可：

```java
FormUtilsClient.register(PhotonForm.class, PhotonFormRenderer::new);   // FormUtilsClient.java:102
UIFormEditor.register(PhotonForm.class, UIPhotonForm::new);            // UIFormEditor.java:147
```

### 5.2 图标

`RegisterIconsEvent` 不存在 → 用 `Icons.register(Icon)`（`Icons.java:260`，public static）：

```java
Icons.register(new Icon(PhotonFormRenderer.ICON, "bbs:photon", 0, 0, 40, 40, 40, 40));
```

参数照抄上游（上游注释说实际图标是 96×96，用 40×40 取样区 —— 保留原值，不擅自调整）。

### 5.3 关键帧编辑器扩展 —— 实施期改用更优方案

**规划时的方案（已废弃）**：上游读 `UIKeyframeFactory.FACTORIES`（当时 public）取出原 STRING
工厂，再 `put` 一个委托包装。BBSFSR 把 `FACTORIES` 改成 `private static final`
（`UIKeyframeFactory.java:34`），只暴露 `register(...)`（`:74`）—— 只能写不能读，
原计划改为「`register` 覆写 + 回落到 `new UIStringKeyframeFactory(...)`」。

**实施期发现（采纳）**：BBSFSR 的 `UIStringKeyframeFactory` 内建了一个专为此类扩展预留的装饰器
扩展点，且**当前在 BBSFSR 内零调用者** —— 它就是留给 addon 的：

```java
// UIStringKeyframeFactory.java:18,71-74
public static void registerDecorator(IStringKeyframeDecorator decorator)
public static interface IStringKeyframeDecorator { void decorate(UIStringKeyframeFactory factory, String track); }
// 配套的操作面（:44,:52）
public void addControl(UIElement element)
public void setStringValue(String value)
```

其构造器（`:35-41`）自己就做了 `sheet.id` 解析并把它作为 `track` 传给每个 decorator。于是：

```java
UIStringKeyframeFactory.registerDecorator(new PhotonFxKeyframeDecorator());
```

优于覆写方案的三点：

1. **零回归**：完全不触碰 BBS 的默认工厂注册，非 `photon_fx` 字符串轨道的行为在结构上不可能被影响
   → AC8 天然满足，不需要靠「回落分支写对了」来保证。
2. **代码更少**：`UIPhotonFxKeyframeFactory.java`（52 行子类）整个删除，换成
   `PhotonFxKeyframeDecorator.java`。上游「遍历 `scroll.getChildren()` 找 `UITextbox` 手动 setText」
   的 hack 被 `factory.setStringValue(...)` 取代（后者内部同时做 `graph.setValue` 与文本框同步）。
3. **无时序风险**：注册 decorator 只是往静态 List 追加，不与 `UIKeyframeFactory` 的静态初始化块
   竞争 → **RK3 时序风险消除**，可在 `init()` 里直接调用，不必等 `onClientStarted`。

文件变更：删 `client/gui/UIPhotonFxKeyframeFactory.java`，新增 `client/gui/PhotonFxKeyframeDecorator.java`。
这是本次移植中唯一偏离「保留上游文件结构」（R1.2）的地方，收益足以抵偿 diff 可读性的损失。

### 5.4 轨道颜色与图标

`UIReplaysEditor.COLORS` / `ICONS` 在 BBSFSR 仍为 `private static final Map`（`:86,87`），
无公开注册通道 → **反射是唯一路径，保留上游方案**，但按 PRD R4.6 强化降级：
反射失败只 `LOGGER.warn` + 轨道退化为默认样式，不抛出。

这是本设计里唯一的「脆弱点」，已在 §8 风险节登记。

### 5.5 source pack / L10n / Extra 分类

source pack 与 L10n 的通道与上游一致，原样保留：

```java
BBSMod.getProvider().register(new InternalAssetsSourcePack("bbs_photon", "assets/bbs_photon", BBSPhotonClient.class));
BBSMod.getProvider().register(new InternalAssetsSourcePack("bbs_photon_icons", "assets", BBSPhotonClient.class));
BBSModClient.getL10n().reload();   // source pack 就绪后再 reload 一次
```

**Extra 分类注入 —— 实施期修正**：上游的三级链
`getFormCategories().getExtraForms().getExtraCategory().addForm(...)` 在 BBSFSR **不存在**
（`FormCategories` 无 `getExtraForms()` / `getExtraCategory()`）。BBSFSR 提供了一个专用公开 API：

```java
// FormCategories.java:80,94
public void addExtraForm(Form form)      // 含 null 检查 + this.markDirty()
public void removeExtraForm(Form form)   // 配对的移除入口
```

改用 `BBSModClient.getFormCategories().addExtraForm(new PhotonForm())`。除了能编译，它还比上游的
链式调用多做了 `markDirty()` —— 上游那条路径不会标脏，理论上存在 UI 不刷新的问题。

L10n 注册本身留在 `BBSPhotonClientAddon.registerL10n`（`RegisterL10nEvent` 订阅），
但那次投递早于 source pack 注册，所以此处补一次 `reload()` 让字符串真正可解析。

三者仍需放在 `onClientStarted` 里 —— 上游注释说明了原因：`BBSResources.init()` 会重建表单分类，
必须在其之后注入。这条约束在 BBSFSR 下依然成立。

### 5.6 删除 Addons 面板注册

上游那段构造 `AddonInfo` 并调 `BBSModClient.registerAddon(info)` 的代码整段删除（PRD N2）：
BBSFSR 无 `AddonInfo` 类，`src/client/java` 下零引用，即无 addon 展示面板。
连带删除「验证 icon 资源存在性」的调试段落（`getResourceAsStream` 检查）——
那是上游为排查 Connector 资源问题加的临时诊断，与本移植无关。

## 6. Fabric API 替换（手术面 B）

| 位置 | 上游 | 替换为 | 依据 |
|---|---|---|---|
| `BBSPhotonClient` tick 看门狗（END） | `ClientTickEvents.END_CLIENT_TICK.register(c -> …)` | `BBSClientApi.onEndClientTick(c -> …)` | `BBSClientApi.java:107` |
| `BBSPhotonClient` tick（START） | `ClientTickEvents.START_CLIENT_TICK.register(…)` | `BBSClientApi.onStartClientTick(…)` | `:97` |
| `BBSPhotonClient` 启动钩子 | `ClientLifecycleEvents.CLIENT_STARTED.register(…)` | `BBSClientApi.onClientStarted(…)` | `:82` |
| `UIPhotonFormPanel:71` 打开 FX 文件夹 | `FabricLoader.getInstance().getGameDir().toFile()` | `BBSMod.getGameFolder()` | `BBSMod.java:322` |
| `UIPhotonFormPanel:169` 扫描 FX 目录 | 同上 | 同上 | 同上 |

用 `BBSMod.getGameFolder()` 而非 NeoForge 的 `FMLPaths.GAMEDIR` —— addon 已经依赖 BBS，
用 BBS 自己的门面少一个平台 API 面，也与「addon 只消费 BBS 公开 API」（R1.4 精神）一致。

替换后 `net.fabricmc.*` 引用归零（AC2）。

## 7. 零改动文件的签名核对

已逐项比对，确认下列文件**只需确认 import 可解析，无需改代码**：

| 文件 | 依赖的 BBSFSR 签名 | 核对结果 |
|---|---|---|
| `PhotonForm` | `Form.add(...)`、`ValueString`、`ValueBoolean` | 全在 `src/main/java`，一致 |
| `UIPhotonForm` | `UIForm<T>`（abstract）、`defaultPanel`（public，`UIForm:29`）、`registerPanel(T, IKey, Icon)`（`UIPanelBase:147`）、`registerDefaultPanels()`（protected，`UIForm:96`）、`L10n.lang(String)→IKey`（`L10n:67`）、`Icons.PARTICLE`（`Icons:115`） | 全部一致 |
| `UIPhotonFormPanel` | `UIFormPanel(UIForm)`（`:28`）、`options`（public，`:25`）、`form`（protected，`:23`）、`startEdit(T)`（`:51`）、`UIListOverlayPanel(IKey, Consumer<String>)`（`:18`）/`addValues`（`:53`）/`setValue`（`:46`）、`Icons.FOLDER`（`Icons:37`）、`AssetProvider.getSourceKeys()`（`:97`）/`getLinksFromPath(Link, boolean)`（`:174`） | 全部一致；仅 §6 的两处 `FabricLoader` 需改 |
| ~~`UIPhotonFxKeyframeFactory`~~ | — | **已删除**，由 `PhotonFxKeyframeDecorator` 取代（§5.3）。新类用的是 `UIStringKeyframeFactory.registerDecorator`（`:18`）/ `addControl`（`:44`）/ `setStringValue`（`:52`）与 `UIKeyframeFactory.getKeyframe()`（`:186`，public）、`getContext()` |
| `PhotonFormRenderer` | `FormRenderer<T>(T)`（`:45`）、`render3D(FormRenderingContext)`（protected，`:315`）、`renderInUI(UIContext,int,int,int,int)`（protected abstract，`:103`）、`ITickable.tick(IEntity)`、`FormRenderType`、`Texture`、`Transform` | 全部一致。上游把 `render3D`/`renderInUI` override 成 `public` —— Java 允许放宽可见性，合法 |

`PhotonFormRenderer` 对 Photon 内部（`EntityEffectExecutor`、`FXRuntime`、`Emitter`、`IFXObject`）
的访问与 BBS 无关，Photon 版本不变则原样可用（PRD Q2）。

**额外核对：反射访问的 BBS 字段名**。`PhotonFormRenderer` 还用
`context.getClass().getField(...)` 反射读 `FormRenderingContext` 的字段（`:370,381,411`）——
这类访问**编译期查不出问题，只会在运行时抛 NoSuchFieldException**，因此单独核对：

| 反射字段 | BBSFSR 位置 | 结果 |
|---|---|---|
| `ui` | `FormRenderingContext.java:31`（`public boolean ui`） | 一致 |
| `stack` | `:22`（`public PoseStack stack`） | 一致 |
| `type` | `:18`（`public FormRenderType type`） | 一致 |

同时核对直接字段访问：`context.entity`（`:20`）、`context.transition`（`:28`）、
`Form.transform`（`Form.java:42`，`ValueTransform`）、`UIContext.render`（`:32`）/
`batcher`（`:33`）—— 全部一致。**RK6 的运行期风险因此大幅下降**。

## 8. 风险与对策

| # | 风险 | 影响 | 对策 |
|---|---|---|---|
| RK1 | `UIReplaysEditor.COLORS`/`ICONS` 反射依赖私有字段名 | BBSFSR 重命名字段 → `photon_fx` 轨道失去专属颜色/图标 | 反射失败降级为 warn + 默认样式（R4.6）。功能非核心，不阻塞主流程 |
| RK2 | Modrinth maven 不可达（C3） | `photon-editor`/`ldlib` 解析失败，构建卡住 | 预留 `libs/` + `flatDir` 本地 jar 回退路径，在 `build.gradle` 里保留 `flatDir { dirs 'libs' }`。**实测已解析成功**（阶段 1，`REAL_EXIT=0`），本条未触发 |
| RK7 | `maven.neoforged.net` 在本工作区被限速至 ~21 KB/s，使 moddev 的工具链下载无法完成 | 构建不可完成 | **已解决**：换用 NeoGradle + BMCLAPI 镜像，见 §3.2b（含完整误判链，排查时先读那一节）。**通用排查提示**：NeoForge 构建长时间停在某个 task 上时，先加 `--info` —— 默认输出会完全隐藏原因 |
| ~~RK3~~ | ~~`UIKeyframeFactory` 覆写时序早于其静态初始化~~ | — | **已消除**：改用 decorator 扩展点后不再覆写任何工厂注册，无时序竞争（§5.3） |
| RK4 | BBSFSR 版本升级导致 jar 名变化 | 编译失败 | 路径抽成 `gradle.properties` 的 `bbs_fsr_jar`（§3.1），一行可改 |
| RK5 | 无法在本会话内做游戏内验证 | 运行期缺陷发现不了 | AC1–AC4 由编译与 grep 自动判定；AC5–AC8 明确标注为交用户冒烟 |
| RK6 | Photon 的 `com.lowdragmc.photon.*` 在 NeoForge 版中 API 与 Fabric 版有差异 | `PhotonFormRenderer` 编译失败 | 上游用的本就是 neoforge 版 photon-editor（`mc1.21.1-2.1.4-neoforge`），版本不变即无差异；若编译报错则逐点处理并记录 |

## 9. 回滚点

工程完全独立于 `new/`（R1.4：不改 BBSFSR 源码），最坏情况 `rm -rf bbs-photon-addon/` 即可回滚，
对仓库其余部分零影响。实施期按 `implement.md` 的阶段划分逐阶段验证，每阶段可独立回退。
