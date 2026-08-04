# PRD: 主题核心 —— token 体系 + ThemeManager + 热重载

父任务:`.trellis/tasks/07-23-fsr-theme-system`(prd/design 为总纲,先读)。
数据格式契约:`new/docs/theme-spec/README.md`。
依赖:无(本任务是其余全部子任务的地基,必须最先完成)。

## 目标

把 `BBSSettings` 里硬编码的二值明暗主题(LIGHT_*/DARK_* 常量 + `theme` 0/1)替换为
可加载的主题文档体系:`ui.themes` 包(UITheme/ThemeManager/ThemeParser)+ 内置
dark/light 主题 JSON + 设置迁移。**对外颜色 API(BBSSettings 语义函数)签名不变。**

## 需求

1. `mchorse.bbs_mod.ui.themes` 包(main source set):
   - `UITheme`:解析结果,不可变;含父 design §2.2 列出的全部字段(颜色 int、样式 bool、
     贴图 Link、6 个 `UIThemeMotion`、id/name/author/description/variant)。
   - `ThemeParser`:`MapType` → `UITheme`;`base` 继承链合并(深度≤4、循环检测);
     颜色用 `Colors.parseWithException`;缓动查 `Interpolations.MAP`,查不到用 `sine_out` 并 warn;
     未知键忽略(`_` 开头键为约定注释位);任何解析失败不抛出到调用方。
   - `ThemeManager`:`current()`(永不返回 null)、`setTheme(String id)`、`reload()`、
     `getThemes()`(枚举内置+外部,外部同 id 覆盖内置)、坏主题回退 dark + 日志 warn。
2. 内置主题 JSON:`new/src/client/resources/assets/bbs/assets/themes/dark/theme.json` 与
   `themes/light/theme.json`(内置 `assets:` 资源在 **client** source set,与
   `textures/icons.png` 同根;main 侧 ThemeManager 运行时经 provider 类加载器可读),
   token 值 == 现有 LIGHT_*/DARK_* 常量与 `Colors` 语义常量(逐值照抄,不许"顺手调色")。
3. `BBSSettings` 接线:
   - `chromeSurface()/baseSurface()/raisedSurface()/deepSurface()/dividerColor()/inputSurface()/
     panelShadowOpaqueColor()/panelShadowTransparentColor()/isLightTheme()` 内部改读
     `ThemeManager.current()`;`backgroundBrightness` 后处理保留;LIGHT_*/DARK_* 常量删除。
   - 新增语义函数:`textColor()/mutedTextColor()/positiveColor()/negativeColor()/warningColor()/
     activeColor()/highlightColor()/cursorColor()/textShadow()`(本任务只提供,替换调用点归
     widget-tokens 任务)。
   - `ValueInt theme` → `ValueString themeId`(默认 "dark",挂新分类 `skins`,`Icons.BRUSH`);
     `migrateLegacySettings` 加 0→"light"/1→"dark" 迁移。
   - `accentFollowsTheme`(ValueBoolean,默认 true)+ `primaryColor()` 语义(父 design §3.2),
     含旧用户自定义色保护迁移。
4. 图标 atlas 重映射:图标绘制纹理绑定处,当前主题 `textures.icons` 非空时把 `Icons.ATLAS`
   映射为主题 Link(父 design §3.4);背景图默认值接线同节。
5. 服务端安全:dedicated server 不触发 ThemeManager 类初始化(语义函数只被客户端渲染调用;
   `BBSSettings.register` 不得 touch ThemeManager)。

## 验收标准

- [ ] `compileJava`、`compileClientJava` 通过;新增单测通过(见 implement.md 测试段)。
- [ ] dev 客户端:`themeId=dark` 与迁移前 dark 视觉逐像素一致;light 同理(抽查 dashboard、
      设置面板、影片编辑器三个界面)。
- [ ] 外部主题:`config/bbs/assets/themes/<id>/theme.json` 能被 `getThemes()` 枚举、
      `setTheme` 选中生效;`reload()` 后 JSON 修改即时可见。
- [ ] 坏 JSON(语法错/缺 format/缓动名不存在/颜色非法)各造一例:不崩溃、回退合理、日志 warn。
- [ ] `base` 继承:只写 `colors.accent.primary` 一个 key 的主题,其余全部与 base 一致。
- [ ] 旧配置迁移:`theme:0/1` → themeId 正确;自定义过 primary_color 的旧配置迁移后
      `accentFollowsTheme=false` 且颜色保留。
- [ ] `./gradlew check` 无回归。

## 约束

- 不引入依赖;JSON 走 `MapType/DataToString`。
- 不改任何调用语义函数的客户端文件(那是 widget-tokens 的事)。
- 内置 JSON 值与现常量逐值相等是硬性要求(视觉零回归的根)。
