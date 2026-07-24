# Design: FSR 主题/皮肤系统 —— 总体架构

本文件是全部子任务共享的架构基准。子任务 design.md 只写各自模块的细化,不重复本文内容;
与本文冲突时以本文 + `new/docs/theme-spec/README.md`(数据格式契约)为准。

## 1. 现状事实(2026-07-23 调研,均已核实)

| 事实 | 位置 |
|---|---|
| 语义色函数(chrome/base/raised/deep/divider/input surface、primaryColor、panelShadow)已存在,内部按 `theme`(0/1)+ `backgroundBrightness` 计算,LIGHT_*/DARK_* 常量硬编码 | `new/src/main/java/mchorse/bbs_mod/BBSSettings.java:196-317` |
| 62 个客户端文件已走语义色;146 个文件仍有约 880 处 `Colors.*` 硬编码 | `grep` 统计 |
| `Colors` 是纯静态常量 + 颜色数学工具;`Colors.parse` 支持 `#RRGGBB/#AARRGGBB` | `new/src/main/java/mchorse/bbs_mod/utils/colors/Colors.java` |
| 2D 渲染原语收口在 Batcher2D(box/gradient/bevelBox/dropShadow/icon/text/texturedBox/clip) | `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/utils/Batcher2D.java` |
| 图标 = 单张 atlas `assets:textures/icons.png`,`Icons.ICONS` 名字注册表,`Icons.ATLAS` 静态 Link | `new/src/main/java/mchorse/bbs_mod/ui/utils/icons/Icons.java` |
| `assets:` 链接解析顺序:`DynamicSourcePack(ExternalAssetsSourcePack(config/bbs/assets))` → `InternalAssetsSourcePack(jar 内 assets/bbs/assets/)`;外部同路径文件覆盖内置 | `new/src/main/java/mchorse/bbs_mod/BBSMod.java:526-543` |
| 设置 UI 自动按 `Settings.categories` 生成分区;值→控件由 `UIValueMap.register(Class, factory)` 决定,`ValueInt` 有 COLOR/MODES 等 subtype 特化 | `new/src/client/java/mchorse/bbs_mod/settings/ui/UISettingsOverlayPanel.java`、`UIValueMap.java` |
| `SettingsBuilder` 提供 `category(id, icon)/getInt/getFloat/getBoolean/getString/getRL` | `new/src/main/java/mchorse/bbs_mod/settings/SettingsBuilder.java` |
| 缓动注册表 `Interpolations.MAP: Map<String, IInterp>`,键名如 `linear/sine_in/sine_out/sine_inout/quad_*/cubic_*/quart_*/quint_*/exp_*/circle_*/back_*/elastic_*/bounce_*`(另有 hermite/bezier 等关键帧专用,不适合 UI tween) | `new/src/main/java/mchorse/bbs_mod/utils/interps/Interpolations.java` |
| UI 框架无动画层;唯一的平滑是 `Scroll` 的 lerp 平滑滚动(`BBSSettings.scrollingSmoothness`) | `new/src/client/java/mchorse/bbs_mod/ui/utils/Scroll.java:589` |
| 旧配置迁移已有先例:`BBSSettings.migrateLegacySettings(MapType)` | `BBSSettings.java:371` |
| `Icons.BRUSH` 存在,可作皮肤分类图标 | `Icons.java:246` |

## 2. 数据结构(先定数据,再定代码)

### 2.1 主题文档(磁盘上的真理)

一个主题 = `themes/<id>/` 文件夹 + `theme.json`。完整格式契约见
`new/docs/theme-spec/README.md`(逐 key 表、默认值、回退规则)——**那份文档是对外规范,
实现与其冲突时修规范或修实现,二选一并记录**。要点:

- `format`(int,当前 1)、`name/author/description`(元信息)、`base`(继承的主题 id)、
  `variant`("dark"|"light",供 `isLightTheme()` 语义)。
- `colors.surface.*`(chrome/base/raised/deep/divider)、`colors.accent.primary`、
  `colors.text.*`(primary/muted)、`colors.state.*`(positive/negative/warning/active/highlight/cursor)。
- `style.*`(text_shadow/bevel/panel_shadow,bool)。
- `textures.icons`、`textures.background`(`assets:` 链接字符串或 null)。
- `motion`:`enabled`、`speed`,以及 `overlay/panel_switch/hover/notification/context_menu/scrollbar`
  六个条目,各含 `enabled/duration(ms)/easing(Interpolations.MAP 键)`。

### 2.2 运行时解析结果

```
mchorse.bbs_mod.ui.themes  (main source set,与 ui.utils.icons 同层)
├── UITheme        不可变的解析结果:全部 token 的 int/boolean/Link/MotionSpec 字段 + id/name/author/variant
├── UIThemeMotion  单个动画条目 {enabled, durationMs, IInterp easing}
├── ThemeManager   静态入口:current()/reload()/getThemes()/setTheme(id)
└── ThemeParser    MapType → UITheme,含 base 继承链合并与逐 key 校验
```

- `UITheme` 全字段解析期定死(不做渲染期字符串查表)。渲染热路径只读字段,零分配。
- 继承:`ThemeParser` 先解析 base 链(深度上限 4,循环检测),后到者覆盖;链尾隐式接内置 dark
  的完整默认值,因此任何 token 缺失都有值,**不存在部分初始化的主题**。
- 解析失败(JSON 坏 / format 不认识):日志 warn + 返回上一个可用主题或内置 dark;绝不抛出。

### 2.3 主题发现

- 内置:jar `assets/bbs/assets/themes/<id>/theme.json`(源码位于
  `new/src/client/resources/assets/bbs/assets/themes/`,与 `textures/icons.png` 同根;
  即 `assets:themes/<id>/theme.json`)。
- 外部:`config/bbs/assets/themes/<id>/theme.json`(`BBSMod.getAssetsFolder()`)。
- 枚举:通过 `BBSMod.provider` 对 `assets:themes` 做链接枚举(InternalAssetsSourcePack 与
  ExternalAssetsSourcePack 均有递归列举能力,实现时核实具体 API;不行就退化为
  内置 id 硬清单 + 外部 File 列目录)。同 id 时外部覆盖内置(与 assets 覆盖语义一致)。

## 3. 接线策略(向后兼容,零调用点爆炸)

### 3.1 颜色:BBSSettings 语义函数保持 API 不变

`chromeSurface()/baseSurface()/raisedSurface()/deepSurface()/dividerColor()/inputSurface()/
panelShadow*()/isLightTheme()` 的**签名与调用点全部不动**,内部改为读
`ThemeManager.current()` 的字段;`backgroundBrightness` 仍在这些函数内后处理。
LIGHT_*/DARK_* 常量迁入内置 light/dark 主题 JSON,`BBSSettings` 里删除。

新增语义入口(供 widget-tokens 子任务替换硬编码用),同样放 `BBSSettings`(与现有函数同风格):
`textColor()/mutedTextColor()/positiveColor()/negativeColor()/warningColor()/activeColor()/
highlightColor()/cursorColor()/textShadow()`。

`Colors.*` 里**纯语义**的常量(ACTIVE/POSITIVE/NEGATIVE/INACTIVE/HIGHLIGHT/CURSOR/CONTROL_BAR)
由 widget-tokens 子任务按映射表替换为上述函数;**非语义**常量(WHITE/GRAY 系、PLANE_XZ 等
gizmo 轴色、剪辑分类色)保留为常量。映射表在 widget-tokens 的 design.md 里维护。

### 3.2 强调色:用户覆盖 × 主题默认

- 新增 `ValueBoolean accentFollowsTheme`(默认 true):true 时 `primaryColor()` 返回主题
  `colors.accent.primary`;false 时返回用户 `primaryColor` 设置值。
- 用户在设置里改动强调色选择器时自动置 false(皮肤栏里提供"跟随主题"开关切回)。
- 迁移:已有用户配置里 `primary_color` ≠ 默认值 0xff3242 → 首次迁移时置 `accentFollowsTheme=false`,
  保住老用户自定义色。

### 3.3 主题选择的设置值与迁移

- `ValueString themeId`(category `skins`,默认 `"dark"`),`ValueInt theme` 删除。
- `migrateLegacySettings`:`personalization.theme` 0→`themeId="light"`、1→`"dark"`。
- `tooltipStyle`(invisible,默认跟 theme)一并核查:若语义是"跟随明暗",改读 variant。

### 3.4 贴图

- 图标换肤:`Icons.ATLAS` 引用保持不变;在图标绘制的纹理绑定处(Batcher2D 的 icon 路径)
  做一次 Link 重映射——当前主题 `textures.icons` 非空且待绑定 Link == `Icons.ATLAS` 时,
  绑定主题提供的 Link。单点改动,所有 Icon 生效。
- 背景图:主题 `textures.background` 作为 `BBSSettings.backgroundImage` 为空时的默认值
  (用户显式设置仍最高优先)。
- 其他任意贴图:主题包作者直接利用现有外部 assets 覆盖机制(放同路径文件),规范文档
  写清楚玩法,不需要代码。

### 3.5 动画(详设计在 motion-layer 子任务)

- client 侧新增 `ui/utils/motion/`:极小的 tween 基建(进度 0..1 + 起始时间戳 + IInterp),
  消费 `UITheme` 的 `UIThemeMotion` 条目;有效时长 = `duration / (theme.speed × 用户速度倍率)`,
  任一开关关闭 ⇒ 时长 0 ⇒ 立即到位(与今日行为逐帧一致)。
- **红线**:只在 render 里插值绘制参数(位移/缩放/透明度/颜色),不碰 `area` 命中判定、
  不碰事件分发、不碰元素树生命周期(教训:2026-07-21 手势所有权拆除)。
- 用户设置:`motionEnabled`(bool,默认 true)、`motionSpeed`(float 0.5–2,默认 1),
  放皮肤分类,与主题 motion 参数相乘。

### 3.6 生效与重载

- `setTheme(id)`:写 `themeId` 设置值 → `ThemeManager` 重解析 → 下一帧全 UI 生效
  (所有消费方每帧读函数,无缓存,天然即时)。
- `reload()`:重新枚举 + 重解析当前主题(含贴图 Link 失效处理:若主题贴图文件被删,
  回退默认 atlas)。由皮肤栏"重载"按钮触发;不做文件监听(简单、可控)。
- 客户端限定:`ThemeManager.current()` 只会被渲染路径调用;dedicated server 不触发类初始化
  即不加载(`BBSSettings.register` 里不得主动 touch ThemeManager)。

## 4. 模块边界(子任务 = 边界)

```
theme-core          ui.themes 包 + BBSSettings 接线 + 内置 dark/light JSON + 迁移 + 单测
theme-widget-tokens client 端 Colors.* → 语义函数替换(映射表驱动的机械扫)
theme-motion-layer  ui/utils/motion + 6 接入点 + 2 个用户设置值
theme-skin-settings-ui  skins 分类 + UIThemeOverlayPanel(列表/预览/操作)+ lang
theme-builtin-packs 内置主题资源 + 演示主题 + docs 对齐校验 + 贴图覆盖示例
```

接口交接点:
- core 冻结 `UITheme` 字段面后,motion/settings-ui 才能并行(它们只读字段)。
- settings-ui 里动画开关/速度两个设置值由 motion-layer 定义(`BBSSettings.motionEnabled/motionSpeed`),
  settings-ui 只负责摆进 skins 分类 UI;若 motion-layer 未先行,settings-ui 预留位置即可。

## 5. 取舍记录

| 决策 | 备选 | 理由 |
|---|---|---|
| 保持 BBSSettings 函数为唯一颜色 API,内部换引擎 | 新 Theme facade 全量替换调用点 | 62 文件零改动;GPT 大扫描只处理硬编码部分,风险减半 |
| UITheme 解析期定死字段 | 渲染期 map 查 token | 渲染热路径(每帧数千次取色)零哈希零分配 |
| 手动重载按钮 | WatchService 文件监听 | 简单;主题作者改完点一下即可,无跨平台监听坑 |
| 图标只重映射 atlas 一张图 | 每个 Icon 可单独换 | atlas 本来就是单文件;粒度够用,规范简单 |
| 动画 render-only | 引入动画驱动的布局/输入状态 | 输入红线;历史事故 |
| 主题放 BBS assets 体系 | Minecraft resource pack | BBS UI 的 `assets:` 链接不走 MC 资源栈;复用现有外部覆盖 + 枚举 |
| JSON 无注释,规范全在 README | JSON5/带注释解析 | 不改 BBS data 解析器;`_`(下划线开头)key 约定为注释位,解析器忽略未知键 |

## 6. 风险

1. **880 处硬编码的误判**:有些 `Colors.WHITE` 是"永远白"(如图标 tint),有些是"文字主色"。
   映射表 + 逐文件人审(widget-tokens 任务内置抽查环节),dark 基线逐像素比对兜底。
2. **light 主题下的硬编码白字**:现状 light 主题下已有对比度问题的地方,token 化后按
   `text.primary` 变黑可能改变现状视觉——widget-tokens 任务里规定:凡 token 化导致 light 下
   视觉变化的,属于"修复"而非回归,记录清单即可。
3. **动画与远程输入(bbs-web-remote)交互**:motion 层严禁触碰输入状态即可规避;
   motion-layer 验收含远程会话冒烟。
4. **主题枚举 API 不确定**:AssetProvider 递归列举在两个 SourcePack 的能力需实现期核实,
   已给退化路径(§2.3)。
