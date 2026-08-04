# Implement: theme-core 执行清单

前置阅读顺序:父任务 design.md → 本任务 prd/design → `new/docs/theme-spec/README.md`。
工作目录:`new/`(独立 git 仓库)。禁止未经用户确认 commit。

## 清单

### A. 数据模型与解析
- [x] A1 建包 `mchorse.bbs_mod.ui.themes`:`UITheme`、`UIThemeMotion`(照 design 骨架)。
- [x] A2 `ThemeParser`:MapType→UITheme;先写"代码级 dark 默认值"构造;再实现逐 key 读取
      (缺 key 取 base 值)、base 链合并(≤4 层、visited 集合防环)、颜色 `Colors.parseWithException`
      + try/catch、缓动 `Interpolations.MAP` 查表回退 `sine_out`。
- [x] A3 `ThemeManager`:枚举(provider `getLinksFromPath` + 内置硬清单 + 外部目录列举三者并集)、
      current 缓存(按请求 id 键控,防坏 id 每帧重解析)、setTheme/reload、
      坏主题回退链(目标主题→内置 dark JSON→代码级默认)、id 白名单 `[a-z0-9_-]+`(路径安全)、
      贴图缺失解析期校验(渲染零成本)。
- [x] A4 单测:`src/themeCoreTest/`(仓库惯例:独立 source set + JavaExec main,无 JUnit)
      `ThemeCoreTest` 12 个用例:全字段解析/缺 key 继承/双层链合并/循环链/深链截断/坏色坏缓动/
      坏 JSON/format 拒载/`_` 键忽略/JSON null 贴图重置/迁移 5 例/内置 JSON 对拍(值写死)+
      规范样例解析。`./gradlew testThemeCore` 通过;已加入 `bbsVerificationTaskNames`(随 check 跑)。

### B. 内置主题 JSON
- [x] B1 `themes/dark/theme.json`(全 key 显式,值逐项照抄旧常量)、`themes/light/theme.json`
      (base:"dark" 继承,只覆盖 surface 五色 + variant,与现状"light 只换表面色"一致)。
- [x] B2 格式对拍规范:一致;偏差见下方记录段。

### C. BBSSettings 接线
- [x] C1 语义函数内部改读 `ThemeManager.current()`;删 LIGHT_*/DARK_* 十常量与
      LIGHT_THEME/DARK_THEME/DEFAULT_THEME;`backgroundBrightness` 后处理保留。
- [x] C2 新增 9 个语义函数(textColor/mutedTextColor/positive/negative/warning/active/highlight/
      cursor/textShadow)。
- [x] C3 `theme`(ValueInt)删除 → `themeId`(ValueString,"dark")+ `accentFollowsTheme`
      (ValueBoolean,true),新分类 `skins`(Icons.BRUSH,personalization 之后);
      `primaryColor()` 内部走 `primaryColorBase()`(follows→主题 accent,否则用户色);
      panelShadow* 同步走 primaryColorBase。
- [x] C4 迁移:`personalization.theme` 0→"light"/1→"dark" 写入 `skins.theme_id` 并删旧键;
      `primary_color` ≠ 默认 → `skins.accent_follows_theme=false`。已有 `skins.*` 时不重复迁移。
- [x] C5 `tooltipStyle`:核实为遗留死值(设置 int 无任何消费点;真正消费点 `TooltipStyle.get()`
      读的是 `theme`)→ 删除设置项;`TooltipStyle.get()` 改读 `isLightTheme()`,删无人用的
      `get(int)`。
- [x] C6 图标 atlas 重映射:`ThemeManager.resolveIconAtlas`(默认主题路径 = 一次 null 判断),
      接入 Batcher2D `icon()`/`iconArea()` 与 UIMultiLinkEditor 的直接 ATLAS 绑定(全仓库仅此
      三处纹理绑定);背景图:UIDashboard.background() 用户为空时取主题 background。
- [x] C7 全文检索 `BBSSettings.theme`/`tooltipStyle` 引用清零(grep 空);BBSModClient 的
      theme/tooltipStyle `.modes(...)` 已删(UIKeys.ENGINE_THEME_*/ENGINE_TOOLTIP_STYLE_* 常量
      暂留,lang 清理归 skin-settings-ui)。
- [x] lang:`bbs.config.skins.*` 6 键已加 en_us + zh_cn(其余语言缺键回退英文)。

### D. 验证
- [x] D1 `./gradlew compileJava compileClientJava` 通过;`testThemeCore` 通过。
- [ ] D2 `runClient` 人工冒烟(**待用户游戏内验证**:dark/light 视觉对比、外部主题枚举/选中/
      reload、四种坏 JSON 回退、旧配置迁移)。本会话无法操作游戏 UI。
- [x] D3 `./gradlew check` 全量(见会话记录)。

## 规范偏差记录

1. **颜色 alpha 规范化**:6 位 `#RRGGBB` 解析后补 `FF` alpha(规范 §3"不写 alpha 即不透明"),
   `UITheme` 全部颜色字段为完整 ARGB。widget-tokens 替换裸 `Colors.ACTIVE`(无 alpha 常量)与
   alpha 组合处时,需改写为 `BBSSettings.color(activeColor(), A75)` 形式(`color()` 先 &RGB 再
   |alpha,语义等价)。对拍断言:`activeColor()==0xff0088ff==A100|Colors.ACTIVE`。
2. **themeId 呈现**:theme-core 阶段 skins 分类里 themeId 是普通文本框(输 id 回车即生效,坏 id
   自动回退 dark 渲染),skin-settings-ui 任务替换为专用主题选择器。
3. **"用户改主色自动置 accentFollowsTheme=false"**(父 design §3.2)不能用 postCallback 实现
   (配置文件加载也走 set,会误触发),移交 skin-settings-ui 在颜色选择器交互层处理。
   theme-core 只保证迁移保护(旧自定义色 → false)。
4. **light 主题 text/state 色**:照抄现状(继承 dark 的白字),light 下对比度修复留给
   widget-tokens 按"修复类变化"处理(父 design 风险 2)。
5. `TooltipStyle.get(int)` 公共方法被删(全仓库无调用);addon 若依赖需改用 `get()`。

## 回滚

新包 + BBSSettings 内部改动 + 两个 JSON 资源,单 commit 粒度 revert 干净;
迁移代码只在旧键存在时触发,回滚后旧配置仍可读(迁移不删 personalization 整组;
`theme` 键被删除的配置在回滚版本里回到默认 dark,颜色无损)。
