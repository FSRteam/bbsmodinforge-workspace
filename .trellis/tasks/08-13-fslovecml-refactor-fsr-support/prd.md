# bbs_FSloveCML 重构与 FSR 双版本支持

## 背景

`bbs_FSloveCML`（仓库：`D:\编译任务\bbs_FSloveCML`，产物名 bbs_snow）是 BBS FS 的功能插件模组，
把 BBS CML Edition 及自研功能移植到 BBS FS 上。现状：

- Fabric 1.20.1 / Java 17 / Yarn，宿主 BBS FS 2.4（libs/bbs-2.4-1.20.1-*.jar）
- 约 202 个 Java 文件、2.65 万行；**86 个 mixin 占源码 43%（1.13 万行）**，大量业务逻辑写在
  mixin 的 @Unique 方法里
- 质量债：1352 行 God-mixin（UIFilmPanelMiniWindowMixin）、1 处 @Overwrite
  （CubicMatrixRendererMixin）、7 处 @Redirect、5 处反射进 BBS 私有内部类
  （UIVanillaSoundListMixin）、80 处宽泛 catch / 37 处 printStackTrace / 6 处空吞、
  两套平行小窗宿主实现（84 个重复行）、测试覆盖 <1%

## 目标

三平台产物，功能全量对齐；FSR 侧尽可能走 Addon API 2.0 / 内部事件 / public 注册表，
少用不稳定 mixin：

| 产物 | 平台 | MC | Java | 映射 | 宿主 |
|---|---|---|---|---|---|
| fabric-1.20.1 | Fabric | 1.20.1 | 17 | Yarn | BBS FS 2.4 |
| neoforge-1.20.1 | NeoForge 47.1 | 1.20.1 | 17 | Mojmap | FSR 1.20.1（fsr-neoforge-1.20.1 worktree，v0.0.11） |
| neoforge-1.21.1 | NeoForge 21.1 | 1.21.1 | 21 | Mojmap | FSR 主线 new/（v0.0.11） |

## 关键事实（已调研确认）

- BBS 内部类包路径三平台一致（mchorse.bbs_mod.*）；抽查 mixin 目标类在 FSR 1.20.1
  全部同名同包存在
- BBS UI 框架自封装（UIElement.render(UIContext)，UIContext 零 MC 类型导入），
  大部分 UI 逻辑/mixin 签名跨平台可复用；MC 类型触点集中在 MatrixStackUtils、
  BBSRendering、UIScreen 输入、Iris/粒子渲染
- FSR Addon API 2.0：settings/clips/forms/particles/network/events/resources facade
  + BBSClientApi（onHudRender/onAfterLevel 等）+ RegisterDashboardPanelsEvent 等内部事件
  + public 注册表（UIClip.register、UIClipRenderers.register、UIKeyframeFactory.register、
  KeyframeFactories.FACTORIES）
- Hot Plugin 路线不可行（禁 mixin），走**正常 NeoForge addon mod**
- Fabric 版 BBS 2.4 也有 EventBus + RegisterDashboardPanelsEvent/RegisterSettingsEvent，
  Fabric 侧同样可减 mixin
- 1.20.1 NeoForge 47.x 是 SRG 运行时，dev 联调需验证（阶段 3 第一步）；
  1.21.1 NeoForge 全程 Mojmap 无此问题

## 仓库结构（多平台模板，源码级共享）

```
bbs_FSloveCML/
├── core/            # 无 BBS/MC 依赖纯逻辑,独立模块+JUnit(已落地)
├── shared/          # 依赖 BBS 类型、平台无关(禁 import net.minecraft/com.mojang),
│   ├── src/main     # 纯源码目录,被各平台模块 srcDir 引入、按各自 BBS jar 编译
│   └── src/client
├── fabric-1.20.1/   # loom+Yarn;mixin 薄壳+Fabric 接线(已迁入)
├── neoforge-1.20.1/ # NeoGradle 47.x+Mojmap;BBSAddon 实现+精简 mixin(待建)
└── neoforge-1.21.1/ # NeoGradle 21.x+Java 21(待建)
```

原则：**mixin per-platform、逻辑全共享**；mixin 只留注入点薄壳（5-30 行），
业务下沉 shared/core（推广已有 UIReplayKeyframeEmbedHelper 薄壳模式）。

## 阶段与进度

### 阶段 0：基线与安全网 —— 已完成 2026-08-13
- [x] 基线提交 + tag `pre-refactor-baseline`（7ce5735）
- [x] FSR 1.20.1 dev jar（bbs-fsr-0.0.11-neoforge-1.20.1-dev.jar，源自 fsr-neoforge-1.20.1 worktree）
- [x] FSR 1.21.1 dev jar（bbs-fsr-0.0.11-neoforge-1.21.1-dev.jar，源自 new/ 主线；
      已弃用最初误用的 pr7-ci-fix v0.0.10 PR 快照）
- [x] ReplaySelfTest 转 JUnit5，修复 Test 任务空转（原 include 过滤匹配不到类）

### 阶段 1：多模块骨架 —— 已完成 2026-08-13
- [x] 源码迁入 fabric-1.20.1/，构建产物等价验证（224 class，mod json/mixins json 齐全）
- [x] core 模块落地：ReplayTimeMath/ReplayActionPolicy + 6 个 JUnit 用例
- [x] accessor mixin 目录对齐包声明（5 个文件移入 mixin/client/accessor/）
- [x] shared/ 源码层接线（fabric build.gradle srcDir 引入）

### 阶段 2：按 8 功能域逻辑下沉 + 质量修复（进行中）
每域闭环：下沉 → 单测 → `gradlew build` 回归 → 提交。横切要求：异常统一 SLF4J LOGGER
（禁 printStackTrace/空吞）、@Redirect 逐个评估降级、静态上下文类加 try/finally 配对。

| # | 域 | 主要文件 | 动作 | 状态 |
|---|---|---|---|---|
| 1 | 小窗停靠系统 | UIFilmPanelMiniWindowMixin(1352)+UIDockLayoutMiniWindowMixin(599)+ui/miniwindow | 两套宿主实现合并为 shared 的 MiniWindowDockHostSupport，mixin 薄壳化 | **完成**：1352→279、599→187；5 文件迁 shared；空吞 catch 与 25 处 printStackTrace 转 LOGGER |
| 2 | snow_actions 时间线 | ActionConfig/ActionPlayback/KeyframeSegment/UIActionsConfigEditor(647) 等 8 mixin | UI 逻辑下沉 shared/timeline；ReplayActionPolicy contains("sound") 收窄为动作剪辑词根匹配；补 ActionTimelineEvaluator 单测 | 进行中 |
| 3 | 动画状态编辑器 | UIAnimationStateEditorMixin(407)+UIFormEditorAnimationStateLayoutMixin(592) | 下沉到 AnimationStateLayoutSource 体系 |
| 4 | PBR/纹理调色 | FormPropertiesPBR/IrisTextureWrapperPBR/纹理分级 3 件套 | 内联 GLSL(约30行)移入 assets 资源管线 |
| 5 | pivot/cubic 渲染 | TransformMixin(12注入)+CubicMatrixRendererMixin(@Overwrite) | @Overwrite 改前后包夹注入；不行则向 FSR 提扩展点 |
| 6 | 粒子 Particle+ | ParticleEmitterMixin(387,8Shadow,1Redirect) 等 5 mixin | 渲染逻辑下沉，@Redirect 降级 |
| 7 | 音频/音效 | UIVanillaSoundListMixin(5处反射)+VanillaSoundResource(621,God) | 反射改 accessor mixin；God class 拆分 |
| 8 | 导出器 | PoseAnimationExporter/IKAnimationExporter(53重复行)+premiere | 合并公共 JSON 层；全仓异常处理收尾 |

验收门：mixin 包行数 11,327 → ≤3,000；每域 Fabric 手测回归。

### 阶段 3：FSR NeoForge 1.20.1 模块
1. hello-world 打通：NeoGradle 骨架 + mods.toml（依赖 modId=bbs）+ BBSAddon 实现 + runClient
   验证（含 SRG dev 联调可行性）
2. API 优先接线：设置→settings()；clip/关键帧类型→clips()+UIClip.register+
   UIClipRenderers.register+KeyframeFactories.FACTORIES+UIKeyframeFactory.register；
   面板→RegisterDashboardPanelsEvent；HUD/世界渲染→BBSClientApi；语言/资源→resources()。
   预计削减 1/3-1/2 mixin
3. 剩余 mixin 迁移：目标签名逐个核对（子代理批量比对 FSR 源码），MC 触点 Yarn→Mojmap
4. 全功能手测验收

### 阶段 4：FSR NeoForge 1.21.1 模块
- 基于 neoforge-1.20.1 接线，处理 Java 21、MC 1.21.1 渲染差异、FS master 漂移
- 宿主 jar 用 new/ 主线构建（v0.0.11+），不用 pr7-ci-fix 旧快照

### 阶段 5：验收矩阵 + CI
- 功能清单 x 3 平台验收表（小窗/纹理调色/IK 烘焙导出/Particle+/BonePBR/跟随轨道/pivot/
  snow_actions/音频导入/Premiere 导出/骨骼纹理/流体伪装等）
- 三平台一键构建脚本 + CI；README/文档更新

## 风险与对策

| 风险 | 对策 |
|---|---|
| BBS 2.4 与 FSR 内部 API 漂移 | shared 按平台源码编译、编译期暴露；漂移点收敛到平台桥接接口 |
| 1.20.1 NeoForge SRG dev 联调 | 阶段 3 第 1 步专项验证；备选生产环境手测 |
| @Overwrite/@Redirect 在 FSR 复现难 | 优先重写为组合注入；必要时向 FSR 提扩展点（走本 Trellis 工作区任务） |
| FSR 主线持续演进 | 移植前重建宿主 jar；neoforge 模块编译即回归 |
| 工程量大 | 按功能域派发子代理并行，每域独立验收，主干始终可构建 |

## 验收标准（总）

1. 三平台 jar 均可构建，功能清单全量通过手测
2. mixin 包行数 ≤3,000（薄壳），高风险注入（@Overwrite/反射/宽 @Redirect）清零或有书面豁免
3. core/shared 层单测覆盖时间线数学、动作策略、导出组装等纯逻辑
4. 异常处理统一 LOGGER，无空吞
