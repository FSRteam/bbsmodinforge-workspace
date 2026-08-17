# FSR 新手教程热插件 · 设计方案

## 1. 架构概览

```
bbs-tutorial-plugin/
├── src/main/
│   ├── java/bbstutorial/
│   │   ├── TutorialPlugin.java           # 主入口，实现 BBSPlugin
│   │   ├── TutorialDashboardPanel.java   # 面板内容工厂 + 生命周期
│   │   ├── TutorialContentView.java      # 右侧步骤卡片 UI 根容器
│   │   ├── TutorialCharacterView.java    # 左侧人物渲染器（UIFormRenderer）
│   │   ├── TutorialProgress.java         # 进度持久化（JSON 读写）
│   │   ├── TutorialChapter.java          # 章节数据结构
│   │   └── TutorialStep.java             # 步骤数据结构（文本 key + 姿势名）
│   └── resources/
│       ├── META-INF/bbs-plugin.json      # 热插件 manifest
│       └── assets/bbs_tutorial/
│           ├── strings/
│           │   ├── zh_cn.json            # 中文翻译（完整）
│           │   └── en_us.json            # 英文翻译（占位）
│           ├── textures/skins/
│           │   ├── tutorial_male.png     # 男款皮肤（日式长风衣）
│           │   └── tutorial_female.png   # 女款皮肤（预留路径）
│           └── models/player/
│               └── tutorial_steve/       # 可选：自定义模型变体（先用内置 steve）
└── build.gradle                          # NeoGradle 构建脚本
```

## 2. 数据模型

### 2.1 章节与步骤

```java
public record TutorialChapter(
    String id,              // "ch01_dashboard", "ch02_morphing_basic", ...
    IKey title,             // tutorial.ch01.title, tutorial.ch02.title, ...
    List<TutorialStep> steps
) {}

public record TutorialStep(
    String id,              // "ch01_s1_open", "ch02_s1_open_b", ...
    IKey dialogueMale,      // 男版台词 key
    IKey dialogueFemale,    // 女版台词 key
    IKey instruction,       // 操作指引（男女共用）
    String pose,            // 姿势 ID（wave_1/thinking_2/crossed_arms/tada_1/bow/praise_the_sun/huh/awww）
    @Nullable IKey hint     // 提示（所有步骤均含 hint，nullable 为容错）
) {}
```

**权威内容源**：`tutorial-script.json` v2.0.0（11章59步）
- 章节：ch01_dashboard (5步) / ch02_morphing_basic (5步) / ch03_ik_vs_fk (5步) / ch04_form_types (5步) / ch05_presets (5步) / ch06_film_basics (7步) / ch07_replay_recording (6步) / ch08_export (5步) / ch09_constraints (5步) / ch10_physics (5步) / ch11_animation_states (6步)
- 字段对应：JSON `dialogueMale/dialogueFemale/instruction/pose/hint/userWillSee` → Record `dialogueMale/dialogueFemale/instruction/pose/hint`
- `userWillSee` 不进代码，仅作冒烟测试人工对照
- 风格定位（JSON `_meta.styles`）：男版=简洁/冷静/短句，女版=活泼/每句1-2个语气词（喵/啦/哦）

### 2.2 进度持久化

`config/bbs/tutorial-progress.json`（ID 取自 `tutorial-script.json`，不再使用旧的 `dashboard_overview` 系命名）:
```json
{
  "version": 1,
  "skipped": false,
  "gender": "male",
  "currentChapter": "ch03_ik_vs_fk",
  "currentStep": 2,
  "completedSteps": [
    "ch01_s1_open",
    "ch01_s2_layout",
    "ch01_s3_hover",
    "ch01_s4_switch",
    "ch01_s5_close",
    "ch02_s1_open_b"
  ]
}
```

**容错**：`currentChapter` / `completedSteps` 中出现未知 ID 时忽略该条并回退到最近一个已知步骤，不抛异常（教程内容会随版本增删）。

### 2.3 翻译 key 命名约定

JSON 的 `titleKey` 已给定章节标题 key（`tutorial.ch01.title` … `tutorial.ch11.title`），
步骤 key 按同一 `chNN` 前缀 + step id 展开，无需任何 slug 映射：

```
tutorial.panel.title                        # 面板标题
tutorial.chNN.title                         # 章节标题（JSON titleKey 原样使用）
tutorial.step.<stepId>.male                 # 步骤台词（男版·简洁冷静）
tutorial.step.<stepId>.female               # 步骤台词（女版·活泼带语气词）
tutorial.step.<stepId>.instruction          # 操作指引（男女共用）
tutorial.step.<stepId>.hint                 # 提示（男女共用，59 步全含）
tutorial.button.male / .female / .skin      # 风格切换与皮肤按钮
tutorial.ui.next / .prev / .skip            # 导航按钮
```

**stepId 直接取 JSON `step.id`**，其自带 `chNN_sN_` 前缀，天然唯一：

| 章节 id | 步数 | stepId 示例 | 展开后的 key |
|--------|-----|------------|-------------|
| `ch01_dashboard` | 5 | `ch01_s1_open` | `tutorial.step.ch01_s1_open.male` |
| `ch02_morphing_basic` | 5 | `ch02_s1_open_b` | `tutorial.step.ch02_s1_open_b.female` |
| `ch03_ik_vs_fk` | 5 | `ch03_s1_fk_pain` | `tutorial.step.ch03_s1_fk_pain.instruction` |
| `ch04_form_types` | 5 | `ch04_s1_categories` | `tutorial.step.ch04_s1_categories.hint` |
| `ch05_presets` | 5 | `ch05_s1_why_save` | … |
| `ch06_film_basics` | 7 | `ch06_s1_open_film` | … |
| `ch07_replay_recording` | 6 | `ch07_s1_replay_vs_keyframe` | … |
| `ch08_export` | 5 | `ch08_s1_ffmpeg_check` | … |
| `ch09_constraints` | 5 | `ch09_s1_constraint_concept` | … |
| `ch10_physics` | 5 | `ch10_s1_physics_concept` | … |
| `ch11_animation_states` | 6 | `ch11_s1_state_concept` | … |

**key 总量**：59 步 × 4（male/female/instruction/hint）+ 11 章标题 + 8 UI = **255 个 key**

章节内排序用 JSON `step.order`（章内 1..N），不要依赖数组下标。

**按键绑定**（JSON `_meta.keybinds_verified: true`，源 `BBSModClient.java:976-988`，我已独立复核一致）：

| 功能 | 默认键 | GLFW 常量 |
|-----|-------|----------|
| Dashboard | 数字键 **0** | `GLFW_KEY_0` |
| 打开 Morphing | **B** | `GLFW_KEY_B` |
| 取消伪装 | 句号 **.** | `GLFW_KEY_PERIOD` |
| 播放 Film | **右 Ctrl** | `GLFW_KEY_RIGHT_CONTROL` |
| 暂停 Film | 反斜杠 **\\** | `GLFW_KEY_BACKSLASH` |
| 录制 Replay | **右 Alt** | `GLFW_KEY_RIGHT_ALT` |
| 打开 Replays | **右 Shift** | `GLFW_KEY_RIGHT_SHIFT` |
| 手动录视频 | **F4** | `GLFW_KEY_F4` |
| 播放 Film 并录制 | **F6** | `GLFW_KEY_F6` |
| 传送到 Replay | **Y** | `GLFW_KEY_Y` |
| 物品编辑器 | **Home** | `GLFW_KEY_HOME` |

> ⚠️ **M 键与 Morphing 无关**：全仓 `GLFW_KEY_M` 仅出现在 camera/pixels/film_controller 的
> 内部 `KeyCombo`（`ui/Keys.java`），从未绑定 Morphing。旧版 `tutorial-script.md` 的
> 「Dashboard 按 B / Morphing 按 M」两处均错，该文件已被作者标记废弃，不作为实现依据。

**JSON `_meta.corrections` 两条结构性纠正**（影响章节划分，勿按旧版 5 章理解）：
- Export **不是** Dashboard 面板，视频生成走 F4/F6 按键流程
- Replay **不是** 独立面板，它是 Film 面板内的编辑对象，右 Shift 为直达入口

## 3. 核心组件设计

（见完整 design.md）

### 3.3 皮肤切换与风格管理

**风格切换按钮**：
- 位置：教程面板右上角，与关闭按钮同行
- UI：♂/♀ 图标，当前风格高亮（`UITheme.accent`）；对应 key `tutorial.button.male` / `tutorial.button.female`
- 交互：点击切换 → `BBSSettings.tutorialGender` 写入 → `TutorialContentView.refresh()` 重新加载台词
- 无动画：人物姿势保持，仅文本切换

**皮肤选择按钮**：
- 位置：人物渲染区域右下角，悬浮圆形按钮（`tutorial.button.skin` key，🎨图标）
- 交互：点击 → `FileDialog.showOpenDialog("*.png")` → 验证 64×64（或 64×32）→ `BBSSettings.tutorialSkin` 写入 → `TutorialCharacterView.reloadTexture()`
- 降级：读取失败时回退到内置皮肤，toast 提示错误，清空 `tutorialSkin` 设置

**数据持久化**：
`config/bbs/settings.json` 新增字段：
```json
{
  "tutorialGender": "male",
  "tutorialSkin": "D:/skins/my_skin.png"
}
```
- `tutorialGender`：`"male"` | `"female"`，默认 `"male"`
- `tutorialSkin`：可选字符串，未设置时用内置 `tutorial_male.png` 或 `tutorial_female.png`

## 4. 实现路线图

### 阶段一：基础框架（构建 + manifest + 降级面板）
- [ ] 创建 `bbs-tutorial-plugin/` 工程，配置 NeoGradle + BMCLAPI 镜像
- [ ] manifest 声明 `resources` + `dashboard_panels` 能力
- [ ] `TutorialPlugin.prepare()` 注册面板（先用 `RegisterDashboardPanelsEvent` 降级）
- [ ] 空面板能在 Dashboard 显示，点击打开无报错
- [ ] **验收**：`./gradlew buildPlugin` 成功，jar 放入 `config/bbs/plugins/` 后 Dashboard 显示 ★教程 按钮

### 阶段二：人物渲染与姿势
- [ ] `TutorialCharacterView extends UIFormRenderer`，加载 `player/steve` 模型
- [ ] 皮肤贴图 `tutorial_male.png` 打包到 jar，通过 `Link.create("bbs_tutorial:textures/skins/tutorial_male.png")` 寻址
- [ ] 视角固定 yaw=25°/pitch=10°/distance=2.5
- [ ] `playPose(poseId)` 从 `PoseManager` 加载姿势并应用到 `ModelForm.pose`
- [ ] **验收**：打开教程面板，左侧显示穿日式长风衣的 steve，做 wave_1 姿势

### 阶段三：步骤卡片与进度
- [ ] `TutorialProgress` 硬编码五章内容结构（每章 3-5 步）
- [ ] `TutorialContentView` 渲染步骤卡片，使用 `roundedBox` + v2 主题 token
- [ ] [上一步][下一步] 按钮切换步骤，触发 `onStepChanged` 回调
- [ ] 人物姿势随步骤切换（wave → thinking → tada → crossed_arms ...）
- [ ] 进度持久化到 `config/bbs/tutorial-progress.json`
- [ ] **验收**：完整走完五章，关闭重开后进度保留，姿势与文本对应

### 阶段四：多语言与主题适配
- [ ] `assets/bbs_tutorial/strings/zh_cn.json` 完整翻译（20+ keys）
- [ ] `en_us.json` 占位翻译（复制中文 key 作为默认值）
- [ ] 所有 UI 文本使用 `IKey`，通过 `L10n` 解析
- [ ] 步骤卡片颜色、圆角、按钮 hover 动画适配 v2 主题
- [ ] 右上角 ♂/♀ 切换按钮，点击后文本立即更新，不刷新姿势
- [ ] 人物区域右下角皮肤选择按钮，文件选择器 + 验证 + 持久化
- [ ] 热替换测试：修改皮肤文件 → 重新选择 → 人物立即换装
- [ ] **验收**：切换语言（中/英）文本更新，切换主题（dark/strawberry）颜色跟随

### 阶段五：跳过与热替换
- [ ] [跳过教程] 按钮写入 `skipped: true`，关闭面板
- [ ] 热替换测试：修改步骤文本 → 重新 buildPlugin → 覆盖 jar → 教程面板立即更新
- [ ] 删除插件后 Dashboard 不残留教程按钮
- [ ] **验收**：完整 AC 列表通过，人工冒烟无报错

## 5. 关键技术决策

### 5.1 为什么选热插件而非 NeoForge Addon？

**优势**：
- 用户可热替换（修改教程文案立即生效，无需重启游戏）
- 不占用 mod 槽位，轻量级交付
- 资源自动寻址（`PluginRoutingSourcePack` 自动注册）

**代价**：
- 依赖 Dashboard 面板 API（当前 in_progress，有降级路径）
- 需验证皮肤贴图从插件 jar 寻址是否通畅

### 5.2 为什么单角色 + 设置切换而非双人对讲？

**理由**：
- 文案量减半（一套对话 vs 两套对话）
- 姿势库足够表达情绪（thinking/huh/awww/tada），无需对话推进叙事
- 用户体验更聚焦（一个清晰的讲解者 vs 两人抢话）

**扩展性**：若后续需要双人，只需增加右侧第二个 `UIFormRenderer` + 对话气泡 UI，核心架构无需重构

### 5.3 为什么固定视角而非自由旋转？

**理由**：
- 教程人物是"配角出镜"，不是可操作的展示台
- 固定视角确保每个姿势的表现角度一致（wave_1 必须看到挥手，不能转到背面）
- 减少交互复杂度（无需处理拖拽旋转与步骤切换的冲突）

### 5.4 降级策略：为什么优先 RegisterDashboardPanelsEvent？

**原因**：
- `08-05-fsr-plugin-dashboard-api` 任务 in_progress，API 可能晚于本插件落地
- 旧事件在 spec 明确保留（§3.4.2："remains available and is still emitted"）
- 面板内容实现（`TutorialDashboardPanel`）与注册方式解耦，API 落地后只改注册代码

**迁移成本**：< 20 行代码（`prepare()` 里的注册逻辑），面板内容零改动

## 6. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Dashboard 面板 API 未完成 | 阻塞实施 | 使用 `RegisterDashboardPanelsEvent` 降级实现，API 落地后平滑迁移 |
| 姿势库加载失败（模型不存在） | 人物无姿势，僵硬站立 | 降级到默认 T-pose，日志警告，不阻塞教程流程 |
| 皮肤贴图寻址失败 | 人物显示默认 steve 皮肤 | 捕获异常，回退到内置 steve.png，诊断日志记录 |
| 进度文件损坏 | 无法读取进度 | catch 异常，删除损坏文件，从第 1 章重新开始 |
| 多会话并发修改 new/ | 编译失败 | 插件工程独立目录，只依赖 BBS jar（compileOnly），不触碰 new/ 源码 |
| 女款皮肤素材未提供 | 只能用男款 | 设置项暂时隐藏，`tutorial_female.png` 路径预留，素材到位后立即启用 |
| 用户皮肤文件尺寸错误 | 拉伸/模糊 | 验证 64×64 或 64×32，不符合拒绝并 toast 提示 |
| 皮肤文件路径失效 | 显示空白 | catch IOException，回退到内置皮肤，清空 `tutorialSkin` 设置 |

## 7. 验收测试计划

### 集成测试（手动冒烟）
1. **构建与加载**：`./gradlew buildPlugin` → 复制到 `config/bbs/plugins/` → 启动客户端 → Dashboard 显示 ★教程
2. **人物渲染**：打开教程面板 → 左侧显示 steve + 日式长风衣皮肤 + wave_1 姿势
3. **步骤切换**：点 [下一步] → 姿势变为 thinking_2 → 文本更新 → 进度 JSON 写入
4. **进度保留**：关闭 Dashboard → 重新打开 → 停留在上次步骤
5. **跳过功能**：点 [跳过教程] → `skipped: true` 写入 → 面板关闭
6. **热替换**：修改步骤文本 → 重新 build → 覆盖 jar → 教程面板文本立即更新
7. **主题适配**：切换到 strawberry 主题 → 步骤卡片颜色/圆角跟随变化
8. **语言切换**：改为英文 → 所有文本切换为英文占位
9. **删除清理**：删除 jar → 重启客户端 → Dashboard 不残留教程按钮
