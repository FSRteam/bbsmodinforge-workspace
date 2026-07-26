# bbs_snow 插件移植到 NeoForge 1.21.1 并兼容 BBS FSR

## Goal

把 `bbs_FSloveCML_1.21.1/`（Fabric 1.20.1 插件，`rootProject.name = bbs_snow`，mod id `bbs_fslovecml`）整体移植到 NeoForge 1.21.1，作为**独立 addon 子项目** `bbs-snow-addon/` 落地，依赖并兼容本仓库 `new/` 的 BBS FSR（`mod_id=bbs`，`0.0.10`，NeoForge `21.1.222`，Java 21）。

移植后玩家把 `bbs-snow-addon` jar 与 BBS FSR 一起放进 mods 目录，即可获得 bbs_snow 在 1.20.1 Fabric 上的全部功能。

## 源码事实（已核实，不是估算）

| 项 | 数值 |
|---|---|
| Java 源文件 | 180（`src/main` + `src/client`） |
| 总行数 | ~22,800 |
| Mixin / Accessor | 76 个，9,162 行（占 40%） |
| Mixin 目标类 | 65 个不同类 |
| **target 为 `net.minecraft.*` 的 Mixin** | **0 个** |
| Mixin 目标在 BBS FSR 中存在 | **65 / 65 全部存在，包名一致（`mchorse.bbs_mod.*`）** |
| `net.fabricmc.api` 引用 | 30 处 |
| `net.minecraft.client` 引用 | 48 处 |
| accesswidener | 空文件（仅版本头），无需转 AT |

### 这两条事实决定了整个移植的难度曲线

1. **所有 Mixin 都注入 BBS 自己的类，没有一个注入原版 Minecraft 类。** BBS 的类名在运行时不混淆，因此**不需要 mojmap 重映射，不需要 refmap**。跨版本 Mixin 移植中最贵的那部分成本直接归零。
2. **65 个目标类在 BBS FSR 里全部存在且包名未变。** 移植不是重写，是**逐个校验注入点签名是否随 1.20.1→1.21.1 与 Fabric→NeoForge 改造而漂移**。

真正的成本在：注入点方法名/描述符漂移、Fabric 入口与事件 → NeoForge 入口与 `BBSAddon` v2 契约、yarn → mojmap 的 48 处原版类名翻译、以及 **NeoGradle 下 addon 工程带 Mixin 的构建基础设施（仓库现有两个 addon 都没有 Mixin，属于全新基建）**。

## Requirements

### R1 工程与构建

- R1.1 新建 `bbs-snow-addon/` 子项目，构建形态对齐仓库既有 addon（`bbs-premiere-export` 的 NeoGradle `userdev 7.1.21` 模式），`compileOnly`/`implementation` 依赖 `../new/build/libs/BBS_FSR_beta_v0.0.10.jar`。
- R1.2 版本对齐 BBS FSR：`minecraft_version=1.21.1`、`neo_version=21.1.222`、`java_version=21`、`options.release = 21`。源码 Java 17 语法需升级到 21 编译通过。
- R1.3 `META-INF/neoforge.mods.toml` 声明对 `bbs` 的 `required` 依赖（`ordering=AFTER`），并声明三份 mixin config。
- R1.4 **NeoForge 下 addon 注入 BBS 类的 Mixin 基础设施必须打通并被验证**：mixin 注解处理器可用、`[[mixins]]` 被 FML 加载、注入在游戏内实际生效、refmap 关闭（目标不混淆）。这是全项目最高风险项，必须最先做掉。
- R1.5 `../new` jar 缺失或过期时构建给出明确错误（照搬 `bbs-premiere-export` 的 `verifyBbsCoreJar` 模式）。

### R2 加载入口

- R2.1 Fabric 的四个 entrypoint（`main` / `client` / `bbs-addon` / `bbs-addon-client`）改造为 NeoForge 形态：`@Mod` 主类 + BBS FSR 的 API 2.0 `mchorse.bbs_mod.api.addon.BBSAddon` 契约（`descriptor/discover/register/setup/unload`）。
- R2.2 客户端专属逻辑走 NeoForge 的 `Dist.CLIENT` 隔离，不得在服务端类加载客户端类。
- R2.3 `net.fabricmc.api.ModInitializer` / `ClientModInitializer` / `EnvType` / `@Environment` 全部替换为 NeoForge 等价物，不留 Fabric 依赖。

### R3 功能等价

移植后以下功能必须与 1.20.1 Fabric 版行为一致：

- R3.1 **PBR 与纹理分级** — 骨骼级 PBR 法线/高光通道、PBR 关键帧工厂、Iris 纹理包装兼容、模型纹理分级着色器、色彩分级屏幕效果。
- R3.2 **Cubic 渲染与动画** — VAO/Cube/Axis/Matrix 渲染器扩展、附加动画器（additive）、程序化动画器、Geo 动画解析扩展、枢轴变换。
- R3.3 **粒子扩展** — 碰撞外观、碰撞染色、粒子伪装（morph）三个新组件及其 UI 分区、粒子发射器与运动碰撞注入。
- R3.4 **UI 框架扩展** — 迷你窗口系统（`UIMiniWindow` / `UIMiniWindowManager` / 停靠宿主 / 缩放手柄）、浮层、覆盖面板缩放。
- R3.5 **回放编辑器与关键帧** — 骨骼纹理轨道、骨骼层级折叠、快捷回放覆盖面板、动画导出覆盖面板、关键帧内嵌检视、动作时间轴、姿态编辑器扩展、变换枢轴编辑。
- R3.6 **剪辑类型** — 快捷栏剪辑（Hotbar）、电影剪辑（Cinematic）、颜色剪辑（Color）及其 UI 与轨道标题。
- R3.7 **流体模拟** — `FluidForm` 伪装类型、流体渲染器、流体控制器与模拟、流体 UI 面板。
- R3.8 **CML 设置面板** — 8 个设置分类（general / appearance / editor / display / fluid_simulation / model_blocks / pose_track_selection / replay_editor）与分区标题控件。
- R3.9 **工具集** — 动画文件操作、IK/姿态动画导出、Molang 变量作用域、音频拖入导入、原版音效资源、骨骼优先级、预设数据操作。

### R4 去重

- R4.1 **`bbs-premiere-export/` 已经是 bbs_snow `premiere/` 模块的 NeoForge 移植版**（`XmemlGenerator`、`TickToFrameConverter`、`UIPremiereExportSettingsOverlayPanel`、`IndividualAudioExporter`、`PremiereExportHandler` 同名同职）。**不得重复移植 premiere 模块**，只做差异比对，把源插件独有而现有 addon 缺失的部分（`SrtGenerator`、`AudioTrackPacker`、`PremierePathUrl`、`PremiereExportActions`）以补丁形式并入 `bbs-premiere-export`，或明确记录为不移植。
- R4.2 移植前须确认 BBS FSR 主 mod 是否已内建等价功能，已内建的不再重复实现。已核实 `new/` 当前**不含** pbr / miniwindow / fluid / texture-grade 任何实现。

### R5 兼容性

- R5.1 `compat/TogetherClientNetworkReplaySelectionMixin` 这类第三方 mod 兼容注入，目标 mod 不在场时不得导致崩溃或 mixin apply 失败（用 `requiredMods` 或软注入）。
- R5.2 Iris 相关注入沿用 BBS FSR 主 mod 的做法（`bbs.client.iris.mixins.json` 已用 `requiredMods=["iris"]`）。
- R5.3 不修改 `new/` 主 mod 源码。若某功能确实无法通过 Mixin 从 addon 侧实现，须单独提出，由用户决定是否放开对主 mod 的改动。

## Constraints

- 不破坏 BBS FSR 现有行为；addon 不在场时 BBS FSR 必须完全正常。
- 不引入 Fabric 运行时依赖。
- 构建验证遵守仓库红线：**禁止 `gradlew ... | tail` 这类管道，必须确认真实 exit code 并复核日志**（管道会吞掉退出码造成假绿）。
- 裸 JVM 单测不得触发 `BBSMod` 静态初始化。

## Task Map

| # | 子任务 | 范围 | 依赖 |
|---|---|---|---|
| S1 | addon 工程骨架与 Mixin 基建 | R1、R2；最小 Mixin 端到端验证 | — |
| S2 | 核心数据层与 API 契约 | `main/mixin` 12 个、`api/` 23 个接口、`settings/`、数据侧 `utils/` | S1 |
| S3 | PBR 与纹理分级 | `pbr/` 10、`client/screen/` 3、相关 Mixin 与 shader 资源（R3.1） | S2 |
| S4 | Cubic 渲染与动画 | `cubic/` 2、Cubic/Animator/Geo/ModelFormRenderer 系 Mixin（R3.2） | S2 |
| S5 | 粒子扩展 | `particles/` 4、`ui/particles/` 4、粒子系 Mixin（R3.3） | S2 |
| S6 | UI 框架扩展与迷你窗口 | `ui/miniwindow/` 5、Dock/Dashboard/BaseMenu/Overlay/Screen 系 Mixin（R3.4） | S2 |
| S7 | 回放编辑器与关键帧扩展 | `ui/film/replays/`、Replays/Keyframe/Pose/Actions/Transform 系 Mixin、导出类 `utils/`（R3.5） | S2、S6 |
| S8 | 剪辑类型与快捷栏 | `clips/` 5、`ui/film/clips/` 3、Clips 系 Mixin（R3.6） | S2 |
| S9 | 流体模拟 Form | `simulation/` 2、`forms/` 2、`ui/forms/` 4（R3.7） | S2 |
| S10 | Premiere 差异补齐、本地化与集成验收 | R4.1、语言文件、整体打包与游戏内验收 | S1–S9 |

S3–S9 之间无相互依赖，可并行推进。

## Acceptance Criteria

跨子任务的整体验收：

- [ ] AC1 `cd bbs-snow-addon && ./gradlew build` 真实 exit code 为 0，产出可加载的 jar。
- [ ] AC2 76 个 Mixin 全部成功 apply，游戏日志中无 mixin 注入失败、无 `defaultRequire` 报错。
- [ ] AC3 BBS FSR + bbs-snow-addon 双 mod 启动进入世界，打开 BBS 面板不崩溃。
- [ ] AC4 R3.1–R3.9 每一项在游戏内可见、可操作，且行为与 1.20.1 Fabric 版一致（逐项冒烟，结果记录在 S10）。
- [ ] AC5 单独加载 BBS FSR（不带本 addon）行为与移植前完全一致。
- [ ] AC6 Together / Iris 等第三方 mod 不在场时不产生任何加载错误。
- [ ] AC7 `new/` 主 mod 源码零改动（`git diff new/` 为空），除非用户另行批准。

## Out of Scope

- 不新增 bbs_snow 在 1.20.1 上没有的功能。
- 不重构 bbs_snow 的既有设计（即使实现方式可优化）；先等价移植，优化另开任务。
- 不移植源仓库的 `addon-project/`（那是另一个更小的独立 addon，功能为 Hotbar/Cinematic/Color/Video clip，与本次 S8 范围重叠，需在 S8 中比对取舍）。
- 不移植 `temp/`、`Error/`、`pr7-ci-fix/`、`audio_fix/` 等源仓库的临时与实验目录。
