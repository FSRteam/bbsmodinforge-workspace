# FSR 新手教程插件

## Goal

让 FSR 新玩家通过交互式、角色出镜的教程掌握核心工作流（Dashboard 总览 → 伪装 → 影片编辑 → 创作工具 → 导出 → 系统设置），配合男女两种讲解风格与固定视角人物出镜，降低学习门槛。

## Context

### 交付形态：独立 NeoForge addon（原「热插件」方案已作废）

规划期设想用 BBS 热插件 SPI（`META-INF/bbs-plugin.json`、`BBSPlugin.prepare(BBSPluginContext)`、`dashboard_panels` 能力、jar 覆盖即热替换）交付。实施期核实 FSR 的正式面板契约后改为独立 addon，理由：

- `BBSClientApi.registerDashboardPanel(descriptor, spec, factory)` 是 FSR 面板注册的正式 API 2.0 入口，宿主在 `UIDashboard` 构造期通过 `BBSDashboardPanelHostRegistry.installAll` 统一投影，不需要 `RegisterDashboardPanelsEvent` 降级路径。
- 面板 chrome 由宿主拥有，addon 只提供 `BBSDashboardPanelContent` 内容根；注册被拒只损失教程入口，Dashboard 本身可用。
- addon 形态与 `bbs-snow-addon`、`bbs-together-addon`、`bbs-photon-addon` 的已验证工程模式一致，源包和 L10n 注册都有现成范例可循。

代价（明确接受）：不再支持「覆盖 jar 即热更新文案」，改文案需重启客户端。

### 依赖与既有能力

- `08-05-fsr-plugin-dashboard-api` 提供的面板契约已落地为 `BBSClientApi` / `BBSDashboardPanelSpec` / `BBSDashboardPanelContent`。
- 内置模型与姿势库：`player/steve`、`player/alex` + `poses.json` 预设姿势。`player/alex` 自身不带 `poses.json`，其模型配置声明 `pose_group: player/steve`。
- `UIFormRenderer extends UIModelRenderer` 可按固定 camera/yaw/pitch/distance 在 UI 里渲染模型，皮肤通过 `ModelForm.texture` 替换。
- 素材现状：只有男款 `tutorial_male.png`（64×64），女款素材未提供。
- 讲解风格定位：男版高冷简洁；女版猫娘活泼带语气词。风格切换只换台词，不换姿势。

## Requirements

### 核心功能

1. **Dashboard 独立教程面板**
   - addon 通过 `BBSClientApi.registerDashboardPanel` 注册本地 id 为 `tutorial` 的面板，宿主组合出 `bbstutorial:tutorial`；标题走语言键，图标 `Icons.HELP`。
   - 左侧固定宽度区域渲染 3D 人物出镜（`UIFormRenderer`），按当前步骤播放对应姿势。
   - 右侧显示章节标题、进度、台词、操作指引、预期画面、提示，以及 [上一步][下一步][跳过教程]。
   - 章节跳转通过右上角列表图标弹出 `UIListOverlayPanel`；进度持久化到 `config/bbs/settings/tutorial-progress.json`（`BBSMod.getSettingsPath`）。

2. **52 章 343 步内容覆盖**

   教学法：已知→类比→操作→验证→扩展；痛点先行，工具后置；每章只引入一个新概念。

   运行时权威内容为 `assets/bbstutorial/assets/tutorial/content.json`（`_meta.version` 3.0.0，`totalChapters` 52，`totalSteps` 343），由任务目录的 `chapters-*-skeleton.json` 收敛而来。章节按六幕组织：

   | 幕 | 章数 | 步数 | 覆盖范围 |
   |----|-----|-----|---------|
   | 入门 | 5 | 32 | Dashboard 九大面板、按键体系、第一次变身、FK 姿势、预设与热键 |
   | 伪装 | 10 | 66 | 形态类型全解、特殊形态、IK 链与进阶、物理骨链与风力、骨骼约束、形态键、动画状态 |
   | 影片 | 16 | 112 | 影片编辑器、关键帧基础/插值/可动画属性/视图、实时录制、回放系统、录制组、演员控制、行为编辑器、回放管理、运动路径、摄像机剪辑与图层、注视跟随、程序化镜头 |
   | 创作 | 14 | 94 | 像素画工具箱与图层、纹理管理、模型配置与装备、模型方块、粒子从入门到表达式 |
   | 导出 | 2 | 11 | 批量导出、素材导入 |
   | 系统 | 5 | 28 | 设置总览、主题与皮肤、插件管理、实体选择器与图形计算器、音频与语言编辑器 |

   规划期的「11 章 59 步」是早期骨架，已被上述内容取代。

   **结构事实**（源码核实）：Dashboard 有 9 个面板；Replay 不是独立面板，而是 Film 面板内的编辑器（右 Shift 直达）；Export 不是独立面板，视频导出走 F4 / F6 与设置面板。

   **已验证按键**（`BBSModClient.java`）：Dashboard = `0`、Morphing = `B`、取消伪装 = `.`、播放 = 右 Ctrl、暂停 = `\`、录制 Replay = 右 Alt、Replay 编辑器 = 右 Shift、手动录像 = `F4`、Film 播放并录制 = `F6`、传送到回放 = `Y`、物品编辑器 = `Home`。

3. **人物出镜与姿势联动**
   - 左侧 `UITutorialCharacter extends UIFormRenderer` 渲染 `player/steve`（男）或 `player/alex`（女）。
   - 默认皮肤为 addon 源包内的 `bbstutorial:textures/skins/tutorial_male.png`；女款纹理缺失时回退男款纹理，但保留纤细模型与女版台词。
   - 每步关联一个姿势名，姿势按**模型声明的 pose group** 解析（不是模型 id），未命中再回退 `player/steve`；命名缺变体后缀时重试 `<name>_1`；全部未命中保留上一个姿势，不闪 T-pose。
   - 视角固定 yaw=25°、pitch=10°、distance=16（`UIModelRenderer.distance` 为平方尺度，约合 2.5 格），禁用拖拽旋转与滚轮缩放。
   - 皮肤自定义：人物区域右下角按钮 → tinyfd 文件选择器 → 校验 64×64 或 64×32 PNG → 复制进 BBS assets 目录（而非引用原路径）→ 清掉纹理缓存 → 路径写入进度文件。

4. **多语言支持**
   - addon 打包 `assets/bbstutorial/assets/strings/zh_cn.json`、`en_us.json`，覆盖全部 24 个 UI chrome 键（标签、按钮、tooltip、状态文本、章节标题模板、进度模板、皮肤对话框文案）。
   - 所有 chrome 文本经 `TutorialKeys` 走 `L10n`；每个键是延迟 `IKey`，在 `get()` 时读取当前 `L10n`，因此面板 spec 可在 mod 构造期创建，且挂载中切语言即时生效。
   - L10n provider 在 `BBSClientApi.onClientStarted` 注册后 `reload()`：宿主在客户端初始化期创建并立即 reload 核心 L10n，更早注册会被抢在前面。
   - provider 只对 `zh_cn` / `en_us` 返回 Link，其它语言返回空列表——否则 `L10n.reload()` 会为不存在的语言文件打印堆栈。英文永远先加载，天然是 fallback 层。
   - 343 步的章节/步骤正文**不进语言文件**：它是作者中文剧本，随 `content.json` 交付。

5. **进度持久化与跳过**
   - 记录讲解风格、皮肤路径、`skipped`、当前章节/步骤 id 与已完成步骤 id 集合。
   - 位置按 **id** 而非下标存取，未知 id 回退到开头，插入章节不会把读者传送到别处。
   - [跳过教程] 可反向切回 [恢复教程]。

### 技术约束

6. **Addon 契约**
   - `BBSApi.registerAddon(DESCRIPTOR, BBSTutorialAddon::new)` 在 mod 构造期调用；descriptor 声明 `apiVersion 2.0`、`side CLIENT`、`compatPolicy API2_ONLY`、能力 `CLIENT_UI` + `SOURCE_PACKS`、`requiredMod("bbs")`。
   - 资源通过 `InternalAssetsSourcePack(MOD_ID, "bbstutorial", "assets/bbstutorial/assets", …)` 注册，因此 `Link` 前缀 `bbstutorial` 映射到 jar 内 `assets/bbstutorial/assets/`。
   - 客户端入口经 `BBSTutorialClientStarter` 按名反射到 `mchorse.bbs_tutorial.client.BBSTutorialClient.initialize()`；这条链编译期无法校验。
   - 面板内容根必须是无 parent 的 `UIElement` 并实现 `BBSDashboardPanelContent`，不得继承宿主面板类。

7. **UI 组件与主题适配**
   - 使用现有 `UILabel`/`UIButton`/`UIIcon`/`UIScrollView`/`UIText`/`UIListOverlayPanel`。
   - 颜色与圆角每帧从 `BBSSettings` 读取（`accentColorRGB`、`textColor`、`mutedTextColor`、`warningColor`、`positiveColor`、`cornerPanel`、`raisedSurface`），不在构造期缓存，切主题即时生效。

8. **构建与打包**
   - 独立工程 `bbs-tutorial-plugin/`，被 workspace 根 `.gitignore` 忽略，源码不进主仓库。
   - NeoGradle `net.neoforged.gradle.userdev` + BMCLAPI 镜像（遵循 `photon-addon-fsr-port` 教训：addon 工程不能用 moddev）。
   - `./gradlew build` 串联 `testTutorialContent`（内容结构）与 `testTutorialWiring`（反射入口、面板 id、descriptor、语言契约）。

### 用户体验

9. **非侵入式设计**：教程面板是普通 Dashboard 面板，不拦截 Dashboard/Morphing/Film 的正常操作，提供跳过选项。

10. **视觉一致性**：人物区与正文区各有 `roundedBox` 卡片背板，圆角取自主题 token（方角主题读回 0 即保持方角）；当前步骤已读时显示勾选图标。

## Acceptance Criteria

自动可验证：

- [x] addon descriptor 声明 API 2.0、`CLIENT_UI` + `SOURCE_PACKS`、`API2_ONLY`、`requiredMod("bbs")`；面板本地 id 通过宿主 `LOCAL_ID` 正则。
- [x] `BBSTutorialClientStarter` 反射目标类存在，且有 `public static void initialize()`。
- [x] `content.json` 52 章 343 步，章节/步骤 id 唯一、order 连续、必需字段完整，解析器降级行为一致。
- [x] 24 个代码请求的 UI 键在 `zh_cn.json` 与 `en_us.json` 中都存在、非空、`%s` 数量与代码传参一致；语言文件不含无人读取的死键。
- [x] `TRANSLATED_LANGUAGES` 声明的语言与随包语言文件一一对应，且必含 `en_us`。
- [x] `./gradlew build` 通过（真实 exit code 0），jar 内含两份语言文件、皮肤纹理与 `content.json`。

需人工冒烟（未执行，见 implement.md「人工冒烟边界」）：

- [ ] Dashboard 显示教程面板按钮，打开后左侧人物出镜、右侧显示当前步骤，切步骤时姿势与文本同步更新。
- [ ] 关闭重开 Dashboard 后进度保留；[跳过教程] 写入 `skipped` 并可切回 [恢复教程]。
- [ ] 切换 zh_cn / en_us 后面板文本即时更新，无需重建面板；第三种语言只落到英文且日志无缺文件堆栈。
- [ ] 切换 dark / light / strawberry 主题后卡片颜色与圆角跟随。
- [ ] 皮肤选择器可选 PNG 并即时换装；尺寸不符时弹出拒绝提示并保持原皮肤。
- [ ] 删除 addon jar 后 Dashboard 不残留教程按钮。

## Out of Scope

- 宿主侧真实 UI 导航、锚点与覆盖层 API 由 `08-21-fsr-guided-tour-overlay-api` 提供；本任务不回填教程内容到该 API，消费接线另行验收。
- 不实现视频/gif 演示嵌入。
- 不实现交互式练习验证（如「必须真正打开一次 Morphing 才能下一步」）。
- 不实现双人搭档对讲，采用单角色旁白 + 姿势表达。
- 不再承诺覆盖 jar 热替换文案（改为 addon 后由 mod 生命周期决定）。
- 女款皮肤素材待补充，纹理暂回退男款。
- Dashboard 首次打开主动提示教程为可选优化，不做。
