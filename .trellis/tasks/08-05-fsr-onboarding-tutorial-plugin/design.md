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
│   │   ├── TutorialActions.java         # 允许的手势白名单（16 个）
│   │   ├── TutorialGender.java          # 讲解风格：id/label（只决定台词）
│   │   └── TutorialLook.java            # 模型款式：id/label/模型/皮肤路径
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
step:    { id, order, action, dialogueMale, dialogueFemale, instruction, userWillSee, hint }
```

对应的运行时类型是普通 final class（不是 record，与 BBS 内部风格一致），全部字段经 `MapType.getString(key, fallback)` 读取，缺字段降级为空串。台词按讲解风格取，缺失时取另一版本，两版都缺则空串——单步缺文案不会中断阅读。

`action` 在解析期就过 `TutorialActions.orIdle`：白名单外的名字（含缺字段）降级为 `idle`。这一步不能省——`Animator.createAction` 对找不到的动画直接返回 null，那个 slot 就没有 playback，上一个动作会一直播下去，症状是「角色卡在上一个手势」而不是任何报错。

动作取值来自宿主共享的 `assets/actions.bobj`，实际用到 9 个（早期 9 种静态姿势一对一映射而来）：

| 旧姿势 | 现动作 | 步数 |
|--------|--------|-----|
| `crossed_arms` | `emote_pointing` | 75 |
| `thinking_2` | `emote_thinking` | 66 |
| `huh` | `emote_confused` | 51 |
| `praise_the_sun` | `emote_salute` | 49 |
| `bow` | `emote_bow` | 45 |
| `tada_1` | `emote_tada` | 42 |
| `wave_1` | `emote_wave` | 11 |
| `awww` | `emote_shrug` | 3 |
| `shy` | `emote_laughing` | 1 |

白名单比实际用量宽（16 个），留给后续脚本增补而不必同时改代码。

步骤正文是作者中文，不进语言文件（见 `prd.md` §4）。

### 2.2 进度持久化

`config/bbs/settings/tutorial-progress.json`（`BBSMod.getSettingsPath`）：

```json
{
  "version": 1,
  "gender": "male",
  "look": "female",
  "skin": "textures/skins/tutorial/my_skin.png",
  "skipped": false,
  "currentChapter": "ch09_ik_chains",
  "currentStep": "ch09_s3_target",
  "completedSteps": ["ch01_s1_open", "ch01_s2_layout"]
}
```

`gender` 与 `look` 是两个独立字段：前者选台词，后者选模型与皮肤。拆分前写下的文件没有 `look`，读取时按 `TutorialLook.matching(gender)` 推出——那正是它当初的语义，老读者不会因为升级换掉模型。

**为什么存 id 不存下标**：内容还在增删章节，存下标会让老读者在下一版被静默传送。`seek()` 把 id 解析成下标，未知章节 id 直接放弃恢复（回到开头），未知步骤 id 回到该章第一步，都不抛异常。

写盘时机：切讲解风格、切模型款式、切皮肤、切跳过、跳章、上一步/下一步各自 `save()`。教程是低频交互，逐次写盘比攒批次丢进度划算。

### 2.3 语言键

只有 UI chrome 进语言文件，共 27 键，统一前缀 `bbstutorial.ui.`：

```
title dialogue instruction expectation hint
previous next skip resume chapters
pick_skin skin_dialog skin_rejected skin_rejected_body skin_failed skin_failed_body
style gender.male gender.female
look look.male look.female
missing_content skipped step_done heading progress
```

`heading` 和 `progress` 是格式串（`%s`）；`style` 与 `look` 的参数本身是另一个键（`gender.<id>` / `look.<id>`）。

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
- 人物区左下角两个图标按钮：讲解风格（`Icons.BUBBLE`）与模型款式（`Icons.USER`），右下角是皮肤选择（`Icons.IMAGE`）。两个切换互不牵连。
- 章节标题、幕名与进度行在 `render` 里直接绘文本，不占布局元素。

### 3.3 主题读取时机

`applyTheme()` 在每次 `refresh()` 调用，`renderCards()` 每帧读 `cornerPanel()` / `raisedSurface()`。构造期缓存颜色会让切主题后面板保持旧配色直到 Dashboard 重建。

### 3.4 人物渲染与动作

`UITutorialCharacter extends UIFormRenderer`，固定 `setRotation(25, 10)` + `setDistance(16)`，`subMouseClicked` / `subMouseScrolled` 直接返回 false。

模型是 `emoticons/steve` / `emoticons/alex`，不是 `player/*`。三条源码事实把这个选择变成唯一选项：

1. **动画只给 emoticons**。`BOBJModelLoader` 里 `if (id.startsWith("emoticons/"))` 才把 `defaultAnimations`（`Link.assets("actions.bobj")` 加 `assets/emotes/*.bobj`，后者当前为空）注入 `instance.animations`。别的模型 `getAnimations()` 里没有任何 `emote_*`，`createAction` 一律返回 null。
2. **只有 emoticons 骨架能弯手臂**。`emoticons/steve/default.bobj` 有 `low_left_arm` / `low_right_arm` / `*.end`；`player/steve/steve.bbs.json` 的 21 个 group 里没有下臂。用户要的「手臂弯曲」在 `player/*` 上物理上做不到。
3. **手势动画确实驱动这些骨**。`emote_pointing` 的骨列表是 `left_arm, low_left_arm, low_body, body, low_right_arm, right_arm`——正是下臂在动。

动作写入方式：

```
playAction(name)
  → TutorialActions.orIdle(name)               白名单外降级 idle
  → new ActionsConfig(); actions.put("idle", ActionConfig(name, loop, fade=5))
  → model.actions.set(config)                  换新对象，不原地改
```

**为什么只用 `idle` 这一个 slot**：`Animator.getActions()` 是 15 个固定名，`controlActions` 按实体运动状态挑主动作，而预览实体永不移动，所以主动作恒为 `idle`。`base_pre` / `base_post` 看似是叠加层，实际 `applyActions` 里以 `blend=1F, skipInitial=false` 调用，`animateGroup` 会把该动画不涉及的骨骼 lerp 回 `Transform.DEFAULT`——它们是覆盖层不是叠加层，塞手势进去会把 idle 抹掉。

**为什么换新 `ActionsConfig` 而非原地改**：`ensureAnimator` 只在 `!Objects.equals(actionsConfig, this.lastConfigs)` 时重跑 `animator.setup(model, config, true)`，而它保存的是一份 `copy()`。原地改会让新旧对象是同一个引用、比较相等，setup 不会重跑，动作不换。`fade=true` 的那次 setup 也正是交叉淡入的来源。

**动画时钟必须自己推**。`UIFormRenderer.renderUserModel` 传 `.simulationOwner(this)`，非 null，所以 `ModelFormRenderer.renderInUI` 里 `if (sourceOwner == null) advanceUIAnimation(context)` 不成立；而 `UIModelRenderer.update()` 只做 `timer++` 与 `entity.setAge`。因此覆写 `update()` 调 `this.form.update(this.entity)`，照 `UIPickableFormRenderer:349-363` 的范式。不推的话动作会冻在起始关键帧对上，partial tick 还会让它在两帧之间来回抖。

姿势体系（`poses.json` / `PoseManager` / pose group 回退链）整体移除：`emoticons/*` 没有 `poses.json`，且动作已覆盖表达需求，保留一条永远回退到 `player/steve` 的死链只会误导。

皮肤解析：自定义皮肤走 `Link.assets(path)`；否则用 `bbstutorial:` 前缀指向源包内纹理，且先用 `getResource` 探测——女款纹理尚未随包，探测失败时回退男款纹理，但模型与台词仍按各自的选择。

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

解析 `content.json`：章节/步骤 id 唯一、order 连续、必需字段存在、总数与 `_meta` 一致，且每步的 `action` 落在 `TutorialActions` 白名单内。

### 4.2 `testTutorialWiring`

钉住**编译期发现不了、只表现为「面板在游戏里不存在 / 角色不动」**的五类契约：

| 检查 | 失败时的真实症状 |
|-----|----------------|
| starter 常量指向的类存在且有 `public static void initialize()` | 面板静默缺失 |
| 面板本地 id 匹配宿主 `LOCAL_ID` 正则 | 宿主拒绝注册 |
| descriptor 的 addon id / apiVersion / `CLIENT_UI` / `requiredMod` / 非 `LEGACY_COMPAT_ONLY` | 宿主拒绝 addon |
| 语言键契约 | 英文玩家看到中文，或 `String.format` 抛异常后显示原始 key |
| 手势白名单每一项都在宿主 `actions.bobj` 里 | 角色卡在上一个手势，无任何报错 |

语言契约的具体做法：

- 从 `TutorialKeys.java` 正则抽 `key(...)` / `value(...)` 的 id 与 fallback；
- 运行期拼接的 id（`gender.<id>`、`look.<id>`）用一条通用正则 `ENUM_KEY_CALL` 捕获前缀与变量名，再按 `ENUM_SOURCES` 映射到对应枚举源文件展开常量——加第三组枚举标签时只需在映射里加一行；
- 从 `BBSTutorialClient.java` 解析 `TRANSLATED_LANGUAGES = Set.of(...)`，用它决定要检查哪些语言文件（而不是在测试里再硬编码一份语言列表），并要求必含 `en_us`；
- 对每个语言文件检查：键缺失、值为空、`%s` 数量与代码传参不符、语言文件里有无人读取的死键。

手势白名单检查读宿主 jar 内的 `/assets/bbs/assets/actions.bobj`，按 `^an <name>$` 抽出全部 85 个动画名。这需要把 FSR jar 放进 `contentTestRuntimeOnly`——只读资源，不加载任何 BBS 类，`BBSMod` 的静态初始化不会被触发。jar 缺失时打印 SKIP 而非 FAIL，保持工程在没有本地 FSR 构建时仍可 `check`。

`SourceText` 从 `src/main/resources` 而不是 classpath 读**自己的**资源：测试要断言文件位于源包需要的那个路径，从 classpath 读会让放错目录的文件照样通过。宿主资源没有源码副本，另走 `readClasspath`。

反例验证（已执行）：
- 早期：删掉 `en_us` 一个键并把 `progress` 的 `%s` 从 4 个改成 2 个 → 报出两条，exit 1。
- 本轮：往白名单塞 `emote_does_not_exist` → `FAIL gesture "emote_does_not_exist" is not in /assets/bbs/assets/actions.bobj`，exit 1；删掉 `en_us` 的 `look.female` → `FAIL en_us is missing "bbstutorial.ui.look.female"`，exit 1。两次均已恢复并重建通过。

## 5. 关键技术决策

### 5.1 为什么放弃热插件改用 addon

见 `prd.md` §Context。核心是 `BBSClientApi.registerDashboardPanel` 才是 FSR 的正式面板契约，热插件路径需要的 `dashboard_panels` 能力与 `BBSPluginClientContext` 在当前 FSR 里不是面板注册的入口。代价是失去覆盖 jar 热更文案。

### 5.2 为什么正文不进语言文件

343 步 × 5 个字段 ≈ 1700 条，全进语言文件会把作者剧本变成翻译工程，且中文正文本身就是权威内容。语言文件只承担 27 条 UI chrome，翻译成本可控。

### 5.3 为什么每个 key 都是延迟 lambda

面板 spec 在 mod 构造期创建（L10n 不存在），tooltip 与按钮文本在挂载期反复读取（读者可能中途切语言）。延迟解析一次解决两端：早期不 NPE，后期不缓存旧语言。

### 5.4 为什么固定视角

手势动画是按单一角度授权的；可拖拽预览会让读者在句子中途把角色转到背面。同时省掉拖拽与步骤切换的冲突处理。

### 5.5 为什么把讲解风格与模型款式拆成两个枚举

原来 `TutorialGender` 一个枚举同时携带 id / label / 模型 / 皮肤，切「女版」既换台词也换模型。这把两个正交选择焊在一起：想要高冷男版台词配女款模型的读者无法表达。拆成 `TutorialGender`（只有 id / label，决定台词）与 `TutorialLook`（模型 / 皮肤），面板给两个独立按钮，进度文件两个独立字段。旧进度文件按 `TutorialLook.matching(gender)` 迁移，语义与拆分前一致。

## 6. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 反射入口改名 | 面板静默消失 | `testTutorialWiring` 钉住常量与方法签名 |
| 面板注册被宿主拒 | 无教程入口 | 只记日志，Dashboard 不受影响 |
| 脚本写了不存在的动作名 | 角色卡在上一个手势，无报错 | 解析期 `orIdle` 降级 + 内容检查拒绝白名单外取值 |
| 宿主换掉 / 改名 `actions.bobj` 里的动画 | 同上 | `testTutorialWiring` 逐项核对宿主 jar 内的动画名 |
| 动画时钟没人推 | 动作冻结并随 partial tick 抖动 | 覆写 `update()` 调 `form.update(entity)` |
| 女款纹理缺失 | 指向不存在的纹理 | 资源探测失败回退男款纹理，保留所选模型与台词 |
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

人工冒烟边界见 `implement.md`：客户端视觉、动作播放与皮肤、语言切换、主题切换、tinyfd 选择器、addon 删除后残留，裸 JVM 测试全部覆盖不到。动作是否真的在动、手臂是否真的在弯，只能在客户端看。
