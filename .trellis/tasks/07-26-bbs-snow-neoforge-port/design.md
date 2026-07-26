# 设计：bbs_snow → NeoForge 1.21.1 addon

本文只写**跨子任务的架构边界与统一约定**。各模块的具体改法在各自子任务的 `design.md` 里。

## 1. 落地形态与目录

```
bbsmodinforge/
├── new/                    # BBS FSR 主 mod（NeoForge 1.21.1, mojmap）— 只读，不改
├── old/                    # BBS 1.20.4 Fabric（yarn）— 只读，翻译对照用
├── bbs-premiere-export/    # 已有 addon，premiere 模块已在此，不重复移植
├── bbs-photon-addon/       # 已有 addon（ModDevGradle 形态，无 mixin）
└── bbs-snow-addon/         # ← 本次新建
    ├── build.gradle
    ├── gradle.properties
    ├── settings.gradle
    └── src/
        ├── main/java/mchorse/bbs_snow/...
        ├── main/resources/META-INF/neoforge.mods.toml
        ├── main/resources/bbs_snow.mixins.json
        ├── client/java/mchorse/bbs_snow/...
        └── client/resources/bbs_snow.client.mixins.json
                             bbs_snow.client.api.mixins.json
```

### 命名决策

| 项 | Fabric 原值 | NeoForge 新值 | 理由 |
|---|---|---|---|
| mod id | `bbs_fslovecml` | `bbs_snow` | 与 `rootProject.name` / `archives_base_name` / 显示名一致，消除"三个名字指一个东西"的混乱 |
| Java 包 | `mchorse.bbs_fslovecml` | `mchorse.bbs_snow` | 同上；一次性全局重命名，不留双轨 |
| 资源命名空间 | `bbs_fslovecml` | `bbs_snow` | 语言文件、shader 路径同步改 |

包名重命名是纯机械替换，在 S1 一次做完，后续子任务不再触碰。

> 源插件有一处历史遗留：语言文件同时存在于 `assets/bbs_fslovecml/lang/`（main）和 `assets/bbs_fslovecml/strings/`（addon-project）。NeoForge 侧统一到 `assets/bbs_snow/lang/`，BBS 自有的 l10n 机制（`mchorse.bbs_mod.l10n`）按主 mod 现行约定接入。

## 2. 构建：为什么选 NeoGradle userdev 而不是 ModDevGradle

仓库里两个 addon 用了两套构建插件：

- `bbs-photon-addon` → `net.neoforged.moddev 1.0.21`，**无 mixin**
- `bbs-premiere-export` → `net.neoforged.gradle.userdev 7.1.21`，**无 mixin**
- `new/`（主 mod）→ NeoGradle，**有 mixin，且 mixin config 里没有 `refmap` 字段**

bbs-snow-addon 是仓库里第一个带 Mixin 的 addon，因此**选 NeoGradle userdev 7.1.21，与主 mod 同构**：主 mod 已经证明这条路径下 mixin 能编译能加载，照抄它的配置比在 ModDevGradle 上另趟一遍便宜。

`bbs-premiere-export/build.gradle` 是最接近的模板（同插件、同 BBS jar 依赖模式、同 `verifyBbsCoreJar` 防呆），在它基础上加 mixin 即可。

### Mixin 基建的三条关键约定

1. **不需要 refmap。** 主 mod 三份 mixin config 都没有 `refmap` 字段。原因是生产与开发都跑 mojmap，NeoForge 不做名称重映射。源插件配置里的 `"refmap": "client-bbs_snow-refmap.json"` **必须删掉**。
2. **`compatibilityLevel` 从 `JAVA_17` 改为 `JAVA_21`**，与主 mod 对齐。
3. **注入目标全是 BBS 自己的类，完全不混淆。** 这是本次移植最大的运气：注入点用什么名字写下来，运行时就是什么名字。Mixin 移植的风险不在名称映射，而在**方法签名是否随 BBS 自身从 1.20.4-fabric 到 1.21.1-neoforge 的改造而漂移**。

### BBS 核心 jar 依赖

照搬 `bbs-premiere-export`：`compileOnly files("../new/build/libs/BBS_FSR_beta_v0.0.10.jar")` + `verifyBbsCoreJar` 任务（缺失/过期/缺少必需 class 时构建失败）。BBS 未发布到任何远程 Maven，这是唯一可行的依赖方式。

## 3. 加载入口：Fabric entrypoint → NeoForge

源插件有四个 entrypoint。**关键事实：BBS FSR 完整保留了源插件所用的旧 addon 事件机制** —— `mchorse.bbs_mod.events.BBSAddonMod`、`@Subscribe`、`RegisterSettingsEvent`、`RegisterSourcePacksEvent` 都在，客户端的 `RegisterL10nEvent` / `RegisterClientSettingsEvent` 也在（`src/client/.../events/register/`）。因此源插件的两个 addon 类**几乎可以原样保留，只换注册方式**。

| Fabric entrypoint | 实现类 | NeoForge 形态 |
|---|---|---|
| `main` | `BBSFSloveCML` (ModInitializer) | `@Mod("bbs_snow")` 主类构造函数 |
| `client` | `BBSFSloveCMLClient` (ClientModInitializer) | `@Mod(value="bbs_snow", dist=Dist.CLIENT)` 客户端类 |
| `bbs-addon` | `BBSFSloveCMLAddon` (`BBSAddonMod`) | **实现不变**，在外部 mod 构造期通过 `BBSMod.registerAddon(...)` 注册 |
| `bbs-addon-client` | `BBSFSloveCMLClientAddon` (`BBSAddonMod`) | 同上 |

标准接入姿势（**S1 实测修正过，不要照抄 BBS 自带的 demo**）：

```java
@Mod("bbs_snow")
public class BBSSnowMod
{
    public BBSSnowMod(IEventBus modBus)
    {
        BBSMod.registerAddon("bbs_snow", BBSSnowAddon::new);   // BBSAddonMod + @Subscribe，内容照抄源插件
    }
}
```

BBS 随后通过 `addonBridge.bridgeToInternalBus(events)` 把 addon 桥接到内部 EventBus，`@Subscribe` 方法照常收到事件。

#### ⚠️ 不要监听 `BBSAddonRegisterEvent`

S1 第一版就是照 `BBSAddonDemoBootstrap` 写的事件监听，**结果 addon 从未被注册，且全程无任何报错** —— 构建绿、启动绿、mod 列表里也有它，只是那个监听器永远不触发。日志时序才暴露出来：

```
[bbs-addon] posting BBSAddonRegisterEvent; currently collected 0 addon(s)
[bbs-addon] BBSAddonRegisterEvent finished; collected 1 addon(s)
[bbs-addon] registration window closed after collecting 1 addon(s)
[bbs_snow] mod constructed, waiting for BBSAddonRegisterEvent      ← 晚了
```

两个原因叠加：

1. BBS 用 `ModLoadingContext.get().getActiveContainer().getEventBus()` 拿到的是**自己的** mod bus，在自己的 `FMLConstructModEvent` 处理函数里 post。外部 mod 监听自己的 bus 收不到。demo 能收到只因为它就在 BBS 的 jar 里。
2. 就算拿到 BBS 的 bus，`ordering = "AFTER"`（硬依赖的正确声明）意味着 bbs_snow 构造时事件早已发完、窗口早已关闭。

#### 两个窗口的区别（这是关键）

`BBSAddonCollector.validateWindowAndId` 对内部/外部两条注册路径查**不同的窗口**：

| 窗口 | 谁用 | 关闭时机 |
|---|---|---|
| `registrationOpen`（事件窗口） | `BBSAddonRegisterEvent` 的注册 | BBS 的 `onConstructMod` 结束时，**极早** |
| `externalRegistrationOpen`（外部窗口） | `BBSMod.registerAddon(...)` | BBS 的 `onCommonSetup` 桥接时，**晚得多** |

`BBSMod.registerAddon(String, Supplier<? extends BBSAddonMod>)` 是 public 静态方法，并且带 `enqueueAddonRegistration` 排队：BBS 尚未初始化就先排队，等 collector 可用再消费。所以它对加载顺序完全免疫 —— 这才是 BBS 给**外部** addon 准备的入口，`BBSAddonRegisterEvent` 是 BBS 内部自用的。API 2.0 有对应的 `registerAddon(BBSAddonDescriptor, Supplier)` 重载，两条路都在。

### 为什么不用 API 2.0

`new/` 另有一套更新的 `mchorse.bbs_mod.api.addon.BBSAddon`（`descriptor/discover/register/setup/unload`，带执行边界保护）。**本次不用它**：源插件已完整实现旧机制，旧机制在 FSR 中仍是一等公民（demo 就用它），改用 API 2.0 只会平白增加一层改写。若后续 BBS 弃用旧机制，再单开任务迁移。

> `new/` 还有 `FabricAddonEntrypointImporter`，能识别 Fabric 风格 entrypoint。**不依赖它**——那是给未移植的第三方 Fabric addon 的兼容层，我们做原生 NeoForge addon。

### 已核实存在的注册 API

源插件入口类调用的以下 BBS API 在 FSR 中全部存在，签名待 S1/S2 逐个核对：`BBSMod.getFactoryCameraClips()`、`BBSMod.getForms()`、`BBSMod.getProvider()`、`BBSMod.events`、`KeyframeFactories.FACTORIES`、`UIClip.register`、`UIFormEditor.register`、`FormUtilsClient.register`、`UIKeyframeFactory.register`、`UIValueMap.register`、`InternalAssetsSourcePack`、`BBSModClient.getL10n()`。

### Fabric API 替换表

| Fabric | NeoForge |
|---|---|
| `net.fabricmc.api.ModInitializer` | `@Mod` 构造函数 |
| `net.fabricmc.api.ClientModInitializer` | `@Mod(dist=Dist.CLIENT)` / `FMLClientSetupEvent` |
| `@Environment(EnvType.CLIENT)` (14 处) | `@OnlyIn(Dist.CLIENT)`，或靠 sourceSet 分离直接去掉 |
| `FabricLoader.getInstance()` (3 处) | `ModList.get()` / `FMLPaths` |
| `ClientTickEvents` (2 处) | `ClientTickEvent.Post` (NeoForge 事件总线) |
| `ServerLifecycleEvents` | `ServerStartingEvent` / `ServerStoppingEvent` |
| `KeyBindingHelper` | `RegisterKeyMappingsEvent` |

## 4. 原版 API 翻译：yarn 1.20.1 → mojmap 1.21.1

原版接触面很小 —— 48 处引用，约 30 个不同类。完整清单：

| yarn (1.20.1) | mojmap (1.21.1) | 出现 | 备注 |
|---|---|---|---|
| `MinecraftClient` | `Minecraft` | 11 | |
| `client.util.math.MatrixStack` | `com.mojang.blaze3d.vertex.PoseStack` | 9 | |
| `client.render.BufferBuilder` | `com.mojang.blaze3d.vertex.BufferBuilder` | 6 | **1.21 大改**：`begin()` 语义变化，`end()` → `build()` 返回 `MeshData` |
| `client.render.VertexFormat(s)` | `com.mojang.blaze3d.vertex.VertexFormat(DefaultVertexFormat)` | 6 | |
| `client.gl.ShaderProgram` | `net.minecraft.client.renderer.ShaderInstance` | 5 | |
| `util.Identifier` | `resources.ResourceLocation` | 3 | 构造改为 `ResourceLocation.fromNamespaceAndPath` |
| `item.ItemStack` | `world.item.ItemStack` | 3 | |
| `resource.ResourceManager/Resource/ResourceFactory` | `server.packs.resources.*` | 5 | |
| `client.gui.DrawContext` | `client.gui.GuiGraphics` | 1 | |
| `client.gl.GlUniform` | `client.renderer.shaders.Uniform` | 1 | |
| `client.render.LightmapTextureManager` | `client.renderer.LightTexture` | 1 | |
| `client.render.WorldRenderer` | `client.renderer.LevelRenderer` | 1 | |
| `client.option.KeyBinding` | `client.KeyMapping` | 1 | |
| `client.util.InputUtil` | `com.mojang.blaze3d.platform.InputConstants` | 1 | |
| `client.util.Window` | `com.mojang.blaze3d.platform.Window` | 1 | |
| `client.sound.Sound` | `client.resources.sounds.Sound` | 1 | |
| `client.resource.language.I18n` | `client.resources.language.I18n` | 1 | |
| `client.gui.screen.Screen` | `client.gui.screens.Screen` | 1 | |
| `util.math.Vec3d/Box/BlockPos/MathHelper/RotationAxis` | `world.phys.Vec3/AABB` `core.BlockPos` `util.Mth` `com.mojang.math.Axis` | 5 | |
| `world.World` / `entity.Entity` | `world.level.Level` / `world.entity.Entity` | 2 | |
| `org.joml.*` / `org.lwjgl.*` / `RenderSystem` | 不变 | 36 | 无需翻译 |

**翻译方法不是查表硬猜，而是查 `old/` 与 `new/` 的同名 BBS 类。** BBS FSR 已经完整走过同一条迁移路（1.20.4-fabric → 1.21.1-neoforge），任何一个原版 API 在这个项目里该怎么写，`new/` 里都有现成答案。遇到分歧一律以 `new/` 的写法为准。

风险最高的是 `BufferBuilder`（6 处）与 `ShaderProgram`（5 处）—— 1.21 的渲染管线改动最大的两块，集中在 S3（PBR/纹理分级）与 S4（Cubic 渲染）。

## 5. duck-typing 接口层

源插件 `api/` 下有 23 个接口（`PBRModelFormAccess`、`TextureGradeHolder`、`BoneTextureHolder`、`PivotHolder`、`FloatLayerAccess` …），用途是让 Mixin 给 BBS 的类"挂"上新能力，再由普通代码通过接口访问 —— 即 Mixin 的 duck-typing 模式：

```java
@Mixin(ModelForm.class)
public class ModelFormMixin implements PBRModelFormAccess { ... }
// 使用侧：((PBRModelFormAccess) someModelForm).bbs_snow$getPBRData()
```

这个模式在 NeoForge 的 Mixin 下完全一致，**不需要改写**。这是 S2 必须先于 S3–S9 完成的原因：接口层是所有功能模块的地基。

约定：新增到 BBS 类上的成员一律加 `bbs_snow$` 前缀，避免与 BBS 自身或其他 addon 冲突。源插件若用了别的前缀（或无前缀），在 S2 统一。

### ⚠️ mixin 包下不能有非 mixin 类

Mixin 把 mixin config 声明的 `package` 下的**所有**类都视为 mixin，注入代码引用同包的辅助类会抛 `IllegalClassLoadError`。致命之处在于它**不在构建期报错，也不在启动期报错** —— S1 探针的 logger 伴生类一路绿灯，直到玩家第一次打开 BBS 仪表盘（`UIDashboard` → `UIBaseMenu.<init>` → 注入方法）才崩。

所以所有辅助类、常量类、工具类一律放在 `mchorse.bbs_snow.*` 下的非 mixin 包。移植源插件时要特别检查其 `mixin/` 目录下是否混有非 mixin 类，有就移出去。

## 6. 兼容性隔离

源插件的三份 config 全部 `defaultRequire: 1`，其中混着两个第三方注入：

- `IrisTextureWrapperPBRMixin` → 目标是 BBS 自己的 `mchorse.bbs_mod.utils.iris.IrisTextureWrapper`（不是 Iris 的类），但该类只在 Iris 在场时有意义。
- `compat/TogetherClientNetworkReplaySelectionMixin` → 目标 `mchorse.bbs_together.network.TogetherClientNetwork`，**源插件已用 `@Pseudo` + `remap=false` + `require = 0` 做了软注入**，Together 不在场时自然跳过。

因此按主 mod 的既有做法拆分：

```toml
[[mixins]]
config = "bbs_snow.mixins.json"

[[mixins]]
config = "bbs_snow.client.mixins.json"

[[mixins]]
config = "bbs_snow.client.api.mixins.json"

[[mixins]]
config = "bbs_snow.client.iris.mixins.json"
requiredMods = ["iris"]
```

即三份拆成四份，把 `IrisTextureWrapperPBRMixin` 移进独立的 iris config（对齐主 mod 的 `bbs.client.iris.mixins.json`）。Together 那个保留 `@Pseudo`/`require = 0` 现状即可，**不需要**单独 config。

`bbs_snow.client.api.mixins.json` 里的 5 个 accessor（`FilmsAccessor`、`UIClipsInvoker`、`UIClipsPanelAccess`、`UIDockLayoutAccess`、`UIFilmPanelLayoutAccess`）是纯访问器，可以并进 `client.mixins.json`，但保持三份的原始划分更利于对照源码，**保留不合并**。

## 7. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| **NeoGradle addon 工程下 mixin 注入 BBS 类未被验证过** | 若走不通，整个方案作废 | **S1 必须做端到端最小验证**：写一个只打日志的 mixin 注入 `UIBaseMenu`，实际启动游戏确认日志出现。不通过就立刻停下来重新评估形态（退路：合入 `new/` 主 mod，需用户批准） |
| 注入点签名随 BBS 演进漂移 | 单个 mixin apply 失败 | 逐个比对 `old/` 与 `new/` 的目标方法；`defaultRequire: 1` 会让失败立刻暴露，不会静默 |
| `BufferBuilder` / `ShaderProgram` 1.21 管线改动 | S3/S4 需实质重写而非翻译 | 以 `new/` 中 BBS 自己的渲染代码为范本 |
| 源插件 `addon-project/` 与 S8 功能重叠 | 重复实现 | S8 开始前先做差异比对，二选一 |
| Java 17 → 21 语法/API 差异 | 编译错误 | 量小，随编译报错逐个修 |

## 8. 不做的事

- 不改 `new/` 一行代码（AC7）。
- 不为兼容而保留 `bbs_fslovecml` 旧 id / 旧包名的别名层 —— 这是首次移植，没有存量用户需要兼容。
- 不引入 Mixin 之外的字节码操作手段（无 ASM 直写、无 coremod）。
