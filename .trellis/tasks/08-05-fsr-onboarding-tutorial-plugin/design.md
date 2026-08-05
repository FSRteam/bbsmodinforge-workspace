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
    String id,              // "dashboard_overview", "morphing", "film", "replay", "export"
    IKey title,             // tutorial.chapter.dashboard_overview.title
    List<TutorialStep> steps
) {}

public record TutorialStep(
    String id,              // "dashboard_overview.open"
    IKey description,       // tutorial.step.dashboard_overview.open.text
    String poseId,          // "wave_1" -> 从 PoseManager 加载
    @Nullable IKey hint     // 可选提示文本
) {}
```

### 2.2 进度持久化

`config/bbs/tutorial-progress.json`:
```json
{
  "version": 1,
  "skipped": false,
  "currentChapter": "morphing",
  "currentStep": 2,
  "completedSteps": [
    "dashboard_overview.open",
    "dashboard_overview.panels",
    "morphing.open",
    "morphing.select_model"
  ]
}
```

## 3. 核心组件设计

（见完整 design.md）

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
