# FSR 新手教程插件 · 设计方案

> 本文档已按实际实现重写。规划期的热插件方案（`BBSPlugin.prepare`、`META-INF/bbs-plugin.json`、255 个语言 key、11 章 59 步）已作废，作废理由见 `prd.md` §Context。

## 1. 架构概览

```
bbs-tutorial-plugin/
├── src/main/java/mchorse/bbs_tutorial/
│   ├── BBSTutorialMod.java              # @Mod 入口：registerAddon + 客户端启动
│   ├── BBSTutorialAddon.java            # descriptor + 源包注册
│   ├── BBSTutorialClientStarter.java    # 按名反射到客户端入口（避免服务端类加载）
│   ├── content/
│   │   ├── TutorialContent.java         # content.json 单次加载 + 降级
│   │   ├── TutorialChapter.java
│   │   ├── TutorialStep.java
│   │   └── TutorialGender.java          # 两位讲解者：id/label/模型/皮肤路径
│   └── progress/TutorialProgress.java   # 按 id 存取的进度持久化
├── src/client/java/mchorse/bbs_tutorial/client/
│   ├── BBSTutorialClient.java           # 面板注册 + L10n provider
│   └── ui/
│       ├── UITutorialPanel.java         # 面板内容根（UIElement + BBSDashboardPanelContent）
│       ├── UITutorialCharacter.java     # 固定视角人物渲染
│       ├── TutorialKeys.java            # 延迟解析的 chrome 语言键
│       └── TutorialSkinPicker.java      # tinyfd 选皮肤 + 校验 + 导入
├── src/contentTest/java/mchorse/bbs_tutorial/content/
│   ├── TutorialContentTestMain.java     # 内容结构校验
│   ├── TutorialWiringTestMain.java      # 运行期契约校验
│   └── SourceText.java                  # 读源码/资源文本，不加载 BBS 类
└── src/main/resources/
    ├── META-INF/neoforge.mods.toml
    └── assets/bbstutorial/assets/
        ├── strings/{zh_cn,en_us}.json
        ├── textures/skins/tutorial_male.png
        └── tutorial/content.json
```

`src/client` 是独立 source set：客户端类不进服务端类路径，`BBSTutorialMod` 只在 `FMLEnvironment.dist == Dist.CLIENT` 时经反射触达它们。

## 2. 数据模型

### 2.1 章节与步骤

`content.json` 结构（`_meta` + `chapters`）：

```
chapter: { id, order, act, titleZh, steps[] }
step:    { id, order, pose, dialogueMale, dialogueFemale, instruction, userWillSee, hint }
```

对应的运行时类型是普通 final class（不是 record，与 BBS 内部风格一致），全部字段经 `MapType.getString(key, fallback)` 读取，缺字段降级为空串、缺 pose 降级为 `crossed_arms`。台词按讲解风格取，缺失时取另一版本，两版都缺则空串——单步缺文案不会中断阅读。

步骤正文是作者中文，不进语言文件（见 `prd.md` §4）。

### 2.2 进度持久化

`config/bbs/settings/tutorial-progress.json`（`BBSMod.getSettingsPath`）：

```json
{
  "version": 1,
  "gender": "male",
  "skin": "textures/skins/tutorial/my_skin.png",
  "skipped": false,
  "currentChapter": "ch09_ik_chains",
  "currentStep": "ch09_s3_target",
  "completedSteps": ["ch01_s1_open", "ch01_s2_layout"]
}
```

**为什么存 id 不存下标**：内容还在增删章节，存下标会让老读者在下一版被静默传送。`seek()` 把 id 解析成下标，未知章节 id 直接放弃恢复（回到开头），未知步骤 id 回到该章第一步，都不抛异常。

写盘时机：切风格、切皮肤、切跳过、跳章、上一步/下一步各自 `save()`。教程是低频交互，逐次写盘比攒批次丢进度划算。

### 2.3 语言键

只有 UI chrome 进语言文件，共 24 键，统一前缀 `bbstutorial.ui.`：

```
title dialogue instruction expectation hint
previous next skip resume chapters
pick_skin skin_dialog skin_rejected skin_rejected_body skin_failed skin_failed_body
style gender.male gender.female
missing_content skipped step_done heading progress
```

`heading` 和 `progress` 是格式串（`%s`）；`style` 的参数本身是另一个键（`gender.<id>`）。

## 3. 核心组件设计

### 3.1 面板注册

```
BBSTutorialMod (构造期)
  └─ BBSApi.registerAddon(DESCRIPTOR, BBSTutorialAddon::new)
  └─ [CLIENT] BBSTutorialClientStarter.initialize()
       └─ 反射 mchorse.bbs_tutorial.client.BBSTutorialClient.initialize()
            ├─ BBSClientApi.onClientStarted(→ activateTranslations)
            └─ BBSClientApi.registerDashboardPanel(DESCRIPTOR, spec, UITutorialPanel::new)
```

`spec.title(TutorialKeys.panelTitle())` 传的是延迟 `IKey`：spec 在构造期创建，此时 `BBSModClient.getL10n()` 还不存在，而 tooltip 每次绘制都会重新 `get()`。

注册结果只写日志：`result.accepted()` 为假时教程没有入口，Dashboard 照常可用。

### 3.2 面板内容

`UITutorialPanel extends UIElement implements BBSDashboardPanelContent`：

- **不能继承宿主面板类**。宿主自己用 `UIExtensionDashboardPanel` 包装 addon 内容，并要求内容根没有 parent。
- `root()` 返回 `this`，`onAppear()` 触发 `refresh()`。
- 布局：左侧固定 190px 人物区 + 右侧滚动正文（台词/操作/预期/提示四段，各带小标题）+ 底部三按钮行。预期与提示为空时整段隐藏。
- 章节标题、幕名与进度行在 `render` 里直接绘文本，不占布局元素。

### 3.3 主题读取时机

`applyTheme()` 在每次 `refresh()` 调用，`renderCards()` 每帧读 `cornerPanel()` / `raisedSurface()`。构造期缓存颜色会让切主题后面板保持旧配色直到 Dashboard 重建。

### 3.4 人物渲染与姿势

`UITutorialCharacter extends UIFormRenderer`，固定 `setRotation(25, 10)` + `setDistance(16)`，`subMouseClicked` / `subMouseScrolled` 直接返回 false。

姿势解析链：

```
poseGroup()  → ModelInstance.getPoseGroup()（模型异步加载，未就绪时退回模型 id）
findPose(g)  → PoseManager.getData(g).getMap(name) → 未命中试 name + "_1"
未命中       → 试 POSE_GROUP_FALLBACK = "player/steve"
仍未命中     → 保留上一个姿势（不闪 T-pose）
```

**关键事实**：姿势按 pose group 存放，不按模型。`player/alex` 没有自己的 `poses.json`，其模型配置声明 `pose_group: player/steve`——直接用模型 id 查会让女版角色全程无姿势。

皮肤解析：自定义皮肤走 `Link.assets(path)`；否则用 `bbstutorial:` 前缀指向源包内纹理，且先用 `getResource` 探测——女款纹理尚未随包，探测失败时回退男款纹理，但模型与台词仍是女版。

### 3.5 皮肤导入

tinyfd 选文件 → `ImageIO` 校验 64×64 或 64×32 → **复制**到 `BBSMod.getAssetsPath("textures/skins/tutorial/…")` → `BBSModClient.getTextures().delete(link)` 清缓存 → 返回 assets 相对路径。

复制而非引用原路径：读者移动或删除源文件后角色不该变空白。清缓存是因为同名文件重选时纹理已被上传，不清就还是旧像素。

### 3.6 L10n 接入

```java
BBSClientApi.onClientStarted(client -> activateTranslations());

// activateTranslations():
l10n.register(BBSTutorialClient::translationLinks);
l10n.reload();
```

三个约束决定了这个形状：

1. **时机**：`BBSModClient.onInitializeClient()` 创建 L10n 后立即 reload，addon 构造期更早，那时注册的 provider 会在源包就绪前被读一次。
2. **语言过滤**：`translationLinks` 只对 `TRANSLATED_LANGUAGES`（`zh_cn`、`en_us`）返回 Link。`L10n.reload()` 对不存在的 Link 会打印失败与堆栈，法语客户端不该在日志里看到教程的报错。
3. **fallback**：`L10n.reload()` 总是先加载 `en_us` 再加载当前语言，所以未翻译语言自动落到英文，不需要额外处理。

`TutorialKeys.value()` 用 `l10n.getKey(full, fallback)`：`computeIfAbsent` 让同一个 `LangKey` 实例先持有随包中文，语言文件加载时原位覆盖 `content`。整段包在 try/catch 里——读翻译不该让面板崩。

## 4. 测试设计

两个裸 JVM `JavaExec` 入口，都挂在 `check` 上。**不加载任何 BBS 类**（会触发 `BBSMod` 静态初始化），改为读源码与资源文本。

### 4.1 `testTutorialContent`

解析 `content.json`：章节/步骤 id 唯一、order 连续、必需字段存在、总数与 `_meta` 一致。

### 4.2 `testTutorialWiring`

钉住**编译期发现不了、只表现为「面板在游戏里不存在」**的四类契约：

| 检查 | 失败时的真实症状 |
|-----|----------------|
| starter 常量指向的类存在且有 `public static void initialize()` | 面板静默缺失 |
| 面板本地 id 匹配宿主 `LOCAL_ID` 正则 | 宿主拒绝注册 |
| descriptor 的 addon id / apiVersion / `CLIENT_UI` / `requiredMod` / 非 `LEGACY_COMPAT_ONLY` | 宿主拒绝 addon |
| 语言键契约 | 英文玩家看到中文，或 `String.format` 抛异常后显示原始 key |

语言契约的具体做法：

- 从 `TutorialKeys.java` 正则抽 `key(...)` / `value(...)` 的 id 与 fallback；
- `gender.<id>` 的 id 在运行期拼接，从 `TutorialGender` 枚举常量展开；
- 从 `BBSTutorialClient.java` 解析 `TRANSLATED_LANGUAGES = Set.of(...)`，用它决定要检查哪些语言文件（而不是在测试里再硬编码一份语言列表），并要求必含 `en_us`；
- 对每个语言文件检查：键缺失、值为空、`%s` 数量与代码传参不符、语言文件里有无人读取的死键。

`SourceText` 从 `src/main/resources` 而不是 classpath 读资源：测试要断言文件**位于源包需要的那个路径**，从 classpath 读会让放错目录的文件照样通过。

反例验证（已执行）：删掉 `en_us` 一个键并把 `progress` 的 `%s` 从 4 个改成 2 个，测试报出这两条并以 exit 1 失败，随后恢复。

## 5. 关键技术决策

### 5.1 为什么放弃热插件改用 addon

见 `prd.md` §Context。核心是 `BBSClientApi.registerDashboardPanel` 才是 FSR 的正式面板契约，热插件路径需要的 `dashboard_panels` 能力与 `BBSPluginClientContext` 在当前 FSR 里不是面板注册的入口。代价是失去覆盖 jar 热更文案。

### 5.2 为什么正文不进语言文件

343 步 × 5 个字段 ≈ 1700 条，全进语言文件会把作者剧本变成翻译工程，且中文正文本身就是权威内容。语言文件只承担 24 条 UI chrome，翻译成本可控。

### 5.3 为什么每个 key 都是延迟 lambda

面板 spec 在 mod 构造期创建（L10n 不存在），tooltip 与按钮文本在挂载期反复读取（读者可能中途切语言）。延迟解析一次解决两端：早期不 NPE，后期不缓存旧语言。

### 5.4 为什么固定视角

姿势是按单一角度授权的；可拖拽预览会让读者在句子中途把角色转到背面。同时省掉拖拽与步骤切换的冲突处理。

## 6. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 反射入口改名 | 面板静默消失 | `testTutorialWiring` 钉住常量与方法签名 |
| 面板注册被宿主拒 | 无教程入口 | 只记日志，Dashboard 不受影响 |
| 姿势组解析失败 | 角色僵直 | pose group → `_1` 变体 → steve 三级回退，最后保留上一姿势 |
| 女款纹理缺失 | 指向不存在的纹理 | 资源探测失败回退男款纹理，保留女版模型与台词 |
| 进度文件损坏 | 无法恢复位置 | catch 后从头开始，日志告警 |
| 未翻译语言 | 日志刷缺文件堆栈 | provider 按 `TRANSLATED_LANGUAGES` 过滤，其余返回空列表 |
| 语言键与代码漂移 | 英文玩家看中文 / 格式化异常显示 raw key | 语言契约测试覆盖缺键、空值、格式符数量、死键 |
| 用户皮肤尺寸错误 | 拉伸模糊 | 校验 64×64 / 64×32，不符拒绝并提示 |
| 多会话并发改 `new/` | 编译失败 | 独立工程只 compileOnly 依赖 FSR jar，不触碰 `new/` |

## 7. 验收测试计划

自动（已执行，见 `prd.md` 勾选项）：

```bash
cd bbs-tutorial-plugin
./gradlew build     # 含 testTutorialContent + testTutorialWiring
```

人工冒烟边界见 `implement.md`：客户端视觉、姿势与皮肤、语言切换、主题切换、tinyfd 选择器、addon 删除后残留，裸 JVM 测试全部覆盖不到。
