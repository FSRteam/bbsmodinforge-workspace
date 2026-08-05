# FSR 新手教程热插件

## Goal

让 FSR 新玩家通过交互式、角色出镜的教程快速上手核心功能（Dashboard 总览 → 形态 Morphing → 电影编辑器 Film → 录制回放 Replay → 视频导出），配合 v2 主题系统与男女款人物皮肤特定视角引导出镜，降低学习门槛，提升首次使用体验。

## Context

- **v2 主题系统已落地**（`fsr-theme-system-plan`）：弹簧动画、圆角、退场动画、装饰层、内置 5 个主题（含 strawberry 演示全特性）
- **Dashboard 面板 API 正在实现**（`08-05-fsr-plugin-dashboard-api` in_progress）：热插件通过 `dashboard_panels` 能力 + `BBSPluginClientContext.dashboardPanels()` 注册面板，生命周期 `open -> appear -> update* -> disappear -> close`，热替换保留位置
- **内置模型与姿势库**：`player/steve`、`player/alex`（含 `_bends`/`_3d` 变体）+ 30 个预设姿势（`poses.json`：wave/thinking/tada/bow/praise_the_sun/shy/crossed_arms/huh/awww…）
- **UI 渲染能力**：`UIFormRenderer extends UIModelRenderer` 可在 UI 里按指定 camera/yaw/pitch/distance 渲染模型，皮肤用 `ModelForm.texture` 替换
- **素材现状**：`temp/男雪新日式长风.png` 一张 64×64 MC 皮肤，女款待补充（设计阶段先按单角色 + 设置可切换方案进行，资源路径预留）

## Requirements

### 核心功能

1. **Dashboard 独立教程面板**
   - 热插件通过 `dashboard_panels` 能力注册一个 `bbs_tutorial:guide` 面板，标题 `tutorial.title`（多语言），图标 `Icons.HELP`
   - 面板左侧 1/3 区域渲染 3D 人物出镜（`UIFormRenderer`），按当前步骤播放对应姿势（从 `poses.json` 加载）
   - 右侧 2/3 区域显示当前章节标题、步骤卡片、说明文本、[上一步][下一步] 按钮
   - 支持章节跳转菜单（顶部 tab 或侧边栏），进度持久化到 `config/bbs/tutorial-progress.json`

2. **五章核心内容覆盖**
   - **第 1 章 · Dashboard 总览**：Dashboard 按钮位置、面板切换、Film/Morphing/Replay/Export 面板简介（3-4 步）
   - **第 2 章 · 形态 Morphing**：按 M 打开、Model 分类选择、套用模型、调整姿势、保存 preset（4-5 步）
   - **第 3 章 · 电影编辑器 Film**：新建 Film、添加 Actor、时间轴、关键帧、播放预览（5-6 步）
   - **第 4 章 · 录制回放 Replay**：开始录制、停止、回放、导出为 Film（3-4 步）
   - **第 5 章 · 视频导出**：Export 面板、分辨率/帧率设置、导出路径、ffmpeg 配置检查（3-4 步）

3. **人物出镜与姿势联动**
   - 左侧 `UIFormRenderer` 渲染 `player/steve` 或 `player/alex` 模型（根据用户设置 `BBSSettings.tutorialCharacter`）
   - 默认皮肤为 `bbs_tutorial:textures/skins/tutorial_male.png`（男款）或 `tutorial_female.png`（女款，待补充素材时实现）
   - 每个步骤关联一个姿势名（如 `wave_1`、`thinking_2`、`tada_1`、`crossed_arms`），通过 `PoseManager` 加载并应用到 `ModelForm.pose`
   - 视角固定：yaw=25°, pitch=10°, distance=2.5，确保人物正面略侧身朝向用户

4. **多语言支持**
   - 插件打包 `assets/bbs_tutorial/strings/zh_cn.json`、`en_us.json`
   - 所有 UI 文本、步骤说明、章节标题使用 `IKey` 引用，通过 `L10n` 解析
   - 注册 source pack（`resources` 能力）+ L10n provider（插件 `prepare` 阶段注册）

5. **进度持久化与跳过**
   - 用户完成步骤时记录 `(chapter, step)` 到 `config/bbs/tutorial-progress.json`
   - 面板提供 [跳过教程] 按钮，写入 `skipped: true` 后不再主动提示
   - Dashboard 首次打开时，若 `tutorial-progress.json` 不存在且未跳过，显示通知提示打开教程面板（可选，不阻塞操作）

### 技术约束

6. **热插件 SPI 契约**
   - manifest 声明 `"kind": "code"`，`"capabilities": ["resources", "dashboard_panels"]`，`"side": "client"`，`"reload": "hot"`
   - `clientEntrypoint` 实现 `BBSPlugin.prepare(BBSPluginContext)`，通过 `context.extension(BBSPluginClientContext.class).dashboardPanels()` 注册面板
   - 面板 content factory 返回 `BBSDashboardPanelContent`，提供 root `UIElement`、生命周期回调 `open/appear/update/disappear/close`
   - 资源自动通过 `PluginRoutingSourcePack` 寻址（`Link.assets("models/player/tutorial_steve/...")` → jar 内 `assets/...`）

7. **依赖 Dashboard 面板 API**
   - 本插件依赖 `08-05-fsr-plugin-dashboard-api` 任务产出的 `BBSPluginClientContext.dashboardPanels()` facade
   - 设计阶段按 spec（§3.4.2、§8.10）契约编程；实现阶段若 API 未落地，先用 `RegisterDashboardPanelsEvent` 降级实现（旧事件仍会发出）

8. **UI 组件与主题适配**
   - 教程面板 UI 使用现有 `UIElement` 组件（`UILabel`、`UIButton`、`UIIcon`、`UIScrollView`、`UIClickable`）
   - 颜色与圆角从 `BBSSettings.theme()` 读取（`UITheme.text.primary`、`surface.panel`、`accent`、`radius.control` 等），适配 v2 主题系统
   - 步骤卡片使用 `Batcher2D.roundedBox` + v2 圆角 token，按钮使用 `UIButton` + hover/press 动画

9. **构建与打包**
   - 插件代码放 `bbs-tutorial-plugin/` 独立工程（不进 workspace 仓库版本控制，与 `bbs-photon-addon` 一致）
   - 使用 NeoGradle (`net.neoforged.gradle.userdev`) + BMCLAPI 镜像（遵循 `photon-addon-fsr-port` 教训）
   - manifest、代码、资源打包为 `bbs-tutorial-<version>.jar`，输出到 `config/bbs/plugins/` 供冒烟测试

### 用户体验

10. **非侵入式设计**
    - 教程面板作为普通 Dashboard 面板存在，用户可随时切换到其他面板，不强制停留
    - 不拦截或劫持 Dashboard/Morphing/Film 的正常操作，教程仅作指引
    - 提供 [跳过教程] 选项，尊重老用户或不需要引导的用户

11. **视觉一致性**
    - 人物出镜区域背景使用 `UITheme.surface.panel` + 轻微阴影/分隔线
    - 步骤卡片使用 `accent` 色高亮当前步骤，已完成步骤显示勾选图标
    - 动画使用 v2 主题的 `UITween` 弹簧动画（章节切换、步骤卡片 hover）

## Acceptance Criteria

- [ ] 热插件 manifest 声明 `resources` + `dashboard_panels` 能力，`kind: code`，`side: client`，`reload: hot`
- [ ] Dashboard 显示 `★教程` 面板按钮，点击后打开教程面板，左侧渲染 steve 模型 + 姿势，右侧显示第 1 章第 1 步内容
- [ ] 点击 [下一步] 按钮，人物姿势切换为下一步对应姿势，右侧内容更新，进度写入 `tutorial-progress.json`
- [ ] 五章内容文本完整，中英文翻译覆盖所有步骤（英文可先用占位文本，中文必须完整）
- [ ] 人物皮肤为插件 jar 内的 `bbs_tutorial:textures/skins/tutorial_male.png`（男款日式长风衣），视角固定 yaw=25°/pitch=10°/distance=2.5
- [ ] 热替换插件（v1→v2 修改步骤文本）后，教程面板内容立即更新，当前章节与步骤位置保留
- [ ] 点击 [跳过教程] 后，`tutorial-progress.json` 写入 `skipped: true`，Dashboard 首次打开不再显示教程提示
- [ ] UI 颜色与圆角适配当前主题（切换 dark/light/strawberry 主题后教程面板样式跟随变化）
- [ ] 步骤卡片使用 `roundedBox` + v2 圆角 token，按钮 hover 有 `UITween` 弹簧缩放动画
- [ ] 插件构建通过（`./gradlew :bbs-tutorial-plugin:jar`），产物可放入 `config/bbs/plugins/` 并在开发客户端加载
- [ ] 人工冒烟：打开教程面板 → 完整走完五章 → 关闭重开 Dashboard → 进度保留 → 跳过教程 → 删除插件 → Dashboard 不残留教程按钮

## Out of Scope

- 不实现悬浮覆盖层 + 高亮真实 UI 控件（超出插件面板 API 契约，需额外挂点调研）
- 不实现视频演示或动画 gif 嵌入（资源体积与播放器复杂度）
- 不实现交互式练习验证（如"必须真正打开一次 Morphing 面板才能进入下一步"）
- 不实现双人搭档对讲（学徒 + 导师角色，文案量翻倍）—— 采用单角色旁白 + 姿势情绪表达
- 不覆盖模型编辑器/粒子/音频/主题设置等高级功能（聚焦核心工作流：形态→拍摄→导出）
- 女款皮肤素材待补充，设计与实现阶段先按男款进行，预留 `tutorial_female.png` 路径与设置项
- Dashboard 首次打开提示教程（AC 第 6 条）作为可选优化，核心验收不强制要求
