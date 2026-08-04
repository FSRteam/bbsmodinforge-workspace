# IRL 灯光插件 NeoForge 1.21.1 迁移设计

## 1. 基线与边界

### 输入基线

| 组件 | 固定来源 | 用途 |
|---|---|---|
| IRLights/BBS 插件 | `temp/bbs-irlights-addon-port-1.21.1-upstream`，`c76a73f0e209da12505d3e9bff56d8e5155f47e4` | 表单、编辑器、灯光收集、BBS 接入、Mixin、shaderpack 补丁 |
| IRL Core | `temp/irl-core-upstream`，`3624676e80833ac8f6c8beb961adea339d261ceb` | SSBO、阴影、Iris 采样器和补丁引擎 |
| BBSFSR | `new`，模组 `bbs` 版本 `0.0.11` | 目标 Addon API、Form、UI、渲染和运行生命周期 |
| 参考插件 | `bbs-photon-addon`、`bbs-snow-addon` | NeoGradle、外部 Addon 注册、客户端隔离、Mixin 分组和运行验证惯例 |

迁移只处理依赖 BBS 的 IRLights。独立编辑器、不依赖 BBS 的版本、Connector fix8 的诊断实验和额外功能不进入目标工程。

## 2. 目标工程

新建根目录工程 `bbs-irlights-addon`：

```text
bbs-irlights-addon/
  build.gradle
  gradle.properties
  settings.gradle
  gradle/wrapper/
  src/main/java/qualet/irlite/...
  src/main/resources/META-INF/neoforge.mods.toml
  src/client/java/qualet/irlite/...
  src/client/java/org/qualet/irl/...
  src/client/resources/irlite.client.mixins.json
  src/client/resources/assets/irlite/patches/*.irlights
```

- 模组 ID：`irlite`
- 展示名：`IRLights`
- 版本：`1.1.4-fsr`
- Java：21
- Minecraft：1.21.1
- NeoForge：21.1.222
- BBSFSR：`bbs [0.0.11,)`，`ordering="AFTER"`
- Iris：客户端必需，`[1.8.8,)`
- Sodium：使用 `mc1.21.1-0.6.13-neoforge` 作为开发/运行依赖，不打包

保留 `src/main` 与 `src/client` 两个源码边界。`main` 只放 Form、配置值、Addon 事件桥和通用入口；所有 Minecraft 客户端、BBS 客户端 UI、Iris、OpenGL 和 `irl-core` MC 渲染代码放入 `client`。JAR 合并两个输出，但专用服务器不会加载客户端 `@Mod(dist=CLIENT)` 类。

## 3. 构建与依赖

采用仓库已验证的 NeoGradle `net.neoforged.gradle.userdev:7.1.21`：

- `implementation net.neoforged:neoforge:21.1.222`
- `compileOnly files(../new/build/libs/BBS_FSR_beta_v0.0.11.jar)`
- `clientCompileOnly`/`clientRuntimeOnly` 使用 Modrinth 的 Iris、Sodium 固定版本
- Iris 的运行依赖 `jcpp`、`glsl-transformer` 由与 BBSFSR 相同的坐标提供，不复制 BBS 或 Iris 到插件 JAR
- 构建前检查 BBSFSR JAR 存在且不旧于 `new/src`；缺失时给出可执行的重建提示
- `processResources` 只展开 `META-INF/neoforge.mods.toml`；删除 Fabric Loader、Fabric API、Loom、Yarn 和 `fabric.mod.json`

`irl-core` 直接以源码并入客户端输出。原因：核心含 Minecraft 客户端类型和 Yarn 映射，继续发布 Fabric intermediary JAR再交给 NeoGradle消费没有可靠的重映射链；同 JAR源码迁移能让 Mojmap 编译器和 Mixin 验证直接发现问题，也避免运行时出现重复或旧版本核心。

## 4. 初始化与 BBSFSR 接入

### 通用入口

`Irlite` 改为 `@Mod("irlite")`：

1. 构造阶段立即调用 `BBSMod.registerAddon("irlite", IrlightsAddon::new)`。
2. `IrlightsAddon` 保留设置注册，并新增 `RegisterFormsEvent` 订阅，注册 `PointLightForm` 和 `SpotlightForm`。
3. 删除注入 `BBSMod#onInitialize` 的 `BBSModMixin`。

### 客户端入口

新增 `@Mod(value="irlite", dist=Dist.CLIENT)` 的客户端入口，构造阶段：

1. 注册独立客户端 Addon 对象，用 `RegisterL10nEvent` 注入原 `L10nMixin` 中的默认文案。
2. 使用 `BBSClientApi.onClientStarted` 延后加载会触及渲染器的静态类。
3. 客户端启动后注册 Form renderer、Form editor、Extra 分类、时间线颜色、补丁引擎、阴影 caster、HUD profiler 和 cookie sampler。
4. 注册过程必须幂等；重复生命周期回调不得重复添加 Form 或 source state。

原有补丁文件已作为类路径资源读取，不需要新的 BBS source pack。七个 `.irlights` 文件仍打入 `assets/irlite/patches`。

## 5. Mixin 处置

### 用正式 API 替代并删除

| 上游 Mixin | 替代方式 |
|---|---|
| `BBSModMixin` | `BBSMod.registerAddon` + `RegisterFormsEvent` |
| `ExtraFormSectionMixin` | `BBSModClient.getFormCategories().addExtraForm` |
| `FormUtilsClientMixin` | `FormUtilsClient.register` |
| `UIFormEditorMixin` | `UIFormEditor.register` |
| `L10nMixin` | 客户端 Addon 的 `RegisterL10nEvent` |
| `UIReplaysEditorColorMixin` | 对 `UIReplaysEditor.COLORS` 的隔离反射写入；失败仅降级为默认颜色 |

### 保留并迁移

| 分组 | Mixin | 理由 |
|---|---|---|
| BBS UI | `UISettingsOverlayPanelMixin` | BBSFSR 没有自定义设置分区控件扩展点 |
| 交互 | `UIFilmControllerMixin`、`UIPickableFormRendererMixin` | 聚光灯 guide 拖拽需要进入 BBS picking 时序 |
| 世界渲染 | `GameRendererLightMixin` | 灯光收集必须发生在 Iris 启用前，上传必须位于本帧 Camera 更新后 |
| 世界缓存 | `WorldBlockChangeMixin` | 客户端方块变化需要精准失效阴影缓存 |
| BBS accessor | `FilmsAccessor`、`WorldBlockEntityTickersAccessor` | 目标字段无公开等价 API |
| Iris | `CapturedRenderingStateClusterMixin`、`CompositeRendererTimerMixin`、`ProgramSamplersBuilderMixin`、`SamplerBindingCubeArrayMixin` | 聚类投影、性能计时和数组纹理采样器注入所需 |

普通客户端 Mixin 与 Iris Mixin 分成两个配置。Iris 配置在 `neoforge.mods.toml` 中使用 `requiredMods=["iris"]`，防止无 Iris 的环境尝试解析 Iris 类。所有配置升级到 `JAVA_21`。

本地 NeoForge Iris 1.8.8 JAR 已确认保留 `CapturedRenderingState#setGbufferProjection`、`CompositeRenderer#createProgram/renderAll`、`ProgramSamplers.Builder#build`、`SamplerBinding#updateSampler` 及 `IrisRenderSystem#bindTextureToUnit`，因此 Iris 注入逻辑无需改写，只需在编译和启动时验证描述符。

## 6. Yarn 到 Mojmap 迁移

迁移按编译单元小步进行，不做全局文本替换：

1. 先改包与类型，例如 `MinecraftClient -> Minecraft`、`ClientWorld -> ClientLevel`、`World -> Level`、`Vec3d -> Vec3`、`Box -> AABB`、`MathHelper -> Mth`、`MatrixStack -> PoseStack`、`RenderLayer -> RenderType`。
2. 再按目标 1.21.1 类核对方法与字段，例如 `renderWorld -> renderLevel`、`RenderTickCounter -> DeltaTracker`、`Camera#update -> Camera#setup`、客户端方块通知方法的完整描述符。
3. Mixin 一律登记目标类、方法、描述符和 `@At` 调用点。优先稳定的 `HEAD/TAIL`；必须保持 Camera 更新后上传的调用点时才使用 INVOKE 描述符。
4. 每完成一组立即运行 `compileClientJava`，用 NeoGradle/Mixin AP 的实际错误继续收敛，不凭名称猜测。

## 7. 数据与渲染流

```text
BBS Form 注册/反序列化
  -> PointLightForm / SpotlightForm 的 Value 数据
  -> Form renderer 与 LightCollector 计算本帧世界坐标
  -> FramePipeline 收集、阴影烘焙、排序
  -> Camera.setup 后上传 SSBO/UBO
  -> Iris ProgramSamplers 注入数组纹理
  -> 已打补丁的 shaderpack 消费灯光、阴影、体积光和 outline
```

Form ID 和所有 Value key 保持不变，因此已有 BBS 影片数据仍按原键保存、加载和关键帧插值。迁移不新建网络协议；依赖 BBS 原有 Form 数据同步和影片同步链。

## 8. OpenGL 与 Iris 约束

- 不在迁移中引入新的裸 FBO/viewport 状态切换。
- 阴影渲染继续使用上游已实现的状态保存/恢复，但必须在 Iris + Sodium 运行验证中检查黑屏、闪烁和 ESC 后残留。
- 任何新增 framebuffer 恢复都必须走 Minecraft/`GlStateManager` 管理路径，避免与 Iris 状态缓存失同步。
- Iris 内部 API 版本固定为 1.8.8；升级 Iris 不属于本任务，升级时需重新跑 `javap`/Mixin 启动验证。

## 9. 失败与回滚

- 构建脚手架、通用注册、Mojmap 核心、BBS UI Mixin、Iris Mixin 分批落地，每批可独立回退。
- 某个非关键装饰能力失败时只降级该能力，例如时间线颜色反射失败回到默认颜色；不得阻断灯光核心。
- GameRenderer、阴影缓存、Iris sampler 任一关键 Mixin 无法可靠应用时，不把插件报告为完成，也不通过降低 `required` 隐藏失败。
- 迁移只新增 `bbs-irlights-addon` 和当前任务文件；不修改 `new`、两个干净上游克隆或现有 Connector 工作副本。

## 10. 验收分层

自动验证覆盖：Java 21 编译、NeoForge 构建、依赖元数据、JAR 内容、Fabric/Yarn 残留扫描、Mixin 启动应用、BBS Form/renderer/editor 注册和补丁资源存在性。

人工验证覆盖：点光/聚光创建与编辑、聚光 guide 拖拽、保存重载、关键帧回放、模型/实体/方块阴影、体积光、cookie、outline、七种 shaderpack 补丁的应用/回滚、Iris 下无闪烁黑屏。
