# PRD: FSR 主题/皮肤系统(高度 DIY 换肤)

## 背景

FSR(`new/`,NeoForge 1.21.1 的 BBS mod)的 UI 外观目前只有一个二值明暗主题:
`BBSSettings.theme`(0=light/1=dark)驱动 `chromeSurface()/baseSurface()/raisedSurface()/
deepSurface()/dividerColor()` 等语义色函数(62 个客户端文件在用),再加一个用户可调的
`primaryColor` 强调色。其余大量颜色硬编码在 `Colors.*` 常量上(146 个客户端文件、约 880 处)。
UI 框架完全没有动画/过渡层。贴图层面,`assets:` 链接天然支持被 `config/bbs/assets/` 外部目录
覆盖(`ExternalAssetsSourcePack` 优先于 jar 内 `InternalAssetsSourcePack`)。

用户需要一套**高度 DIY 的换肤方案**:主题包(颜色 + 贴图 + 样式开关 + 动画参数)以 JSON 文件
形式存在,玩家可以自己写主题、放进游戏目录即可用;设置里新增"皮肤"栏管理主题;并提供一份
规范样例(单独文件夹)作为第三方作者的模板与实现验收契约。

## 用户确认的决策(2026-07-23)

1. **一期范围 = 全套一步到位**:主题核心(JSON token + ThemeManager + 热重载)+ 全部共享控件
   颜色 token 化 + 动画层 + 皮肤设置栏 + 内置暗/亮主题迁移。
2. **动画接入点全都要**:浮层开合、面板切换、按钮/图标 hover 渐变、通知滑入滑出、右键菜单
   展开、滚动条淡出,共 6 个点,外加全局开关与速度倍率;主题 JSON 里每项可配
   时长 + 缓动 + 开关,缓动直接引用现有 `Interpolations.MAP` 的字符串键。
3. **规范样例**:由本次规划直接产出,放在 `new/docs/theme-spec/`(规范 README + example-theme
   完整样例包)。实现必须与该规范对齐,规范即验收标准;实现过程中若发现规范不合理,修改规范
   须在任务文档中记录理由。
4. **Trellis 结构 = 父任务 + 按模块子任务**,便于分次交给 GPT(codex)执行。

## 需求

### R1 主题包(数据格式)
- 主题 = 一个文件夹,内含 `theme.json`(格式见 `new/docs/theme-spec/README.md`)与可选贴图。
- 覆盖能力:语义颜色(表面/强调/文字/状态)、样式开关(文字阴影、bevel、面板阴影)、
  贴图覆盖(图标 atlas、默认背景图)、动画参数(6 接入点各自的 开关/时长/缓动 + 全局开关/速度)。
- 支持 `base` 继承:未写的 token 沿继承链回退,最终回退到内置 dark。
- 内置主题:`dark`、`light`(与迁移前视觉完全一致);用户主题放
  `config/bbs/assets/themes/<id>/`,同 id 覆盖内置。

### R2 运行时
- ThemeManager:发现(内置 + 外部)、解析、校验、回退(坏 JSON 不崩溃,回退 dark 并记日志)、
  手动重载(设置里按钮,改完 JSON 即刷)。
- 切换主题立即生效,无需重启游戏或重开界面。

### R3 设置"皮肤"栏
- 设置(齿轮 → UISettingsOverlayPanel)新增"皮肤"分类(`skins`,图标 `Icons.BRUSH`)。
- 内容:主题选择器(列表 + 每个主题的名称/作者/色板预览)、"重载主题"按钮、"打开主题文件夹"
  按钮、"导出模板"按钮(把规范样例写到用户主题目录作为起点)、强调色是否跟随主题的开关、
  动画全局开关与速度倍率。
- 文案:en_us + zh_cn 双语。

### R4 视觉零回归
- 迁移后,选择内置 `dark` 主题时,UI 与迁移前逐像素等价(颜色值一致、无新动画默认关闭时
  行为一致);`light` 同理。这是所有子任务共同的基线约束。

### R5 规范样例
- `new/docs/theme-spec/README.md`:逐 key 规范(token 表、缓动名字表、贴图覆盖路径、
  回退规则、版本化)。
- `new/docs/theme-spec/example-theme/`:完整可拷贝的样例包(theme.json + 说明 + 贴图目录结构)。

## 任务地图(子任务与顺序)

| 顺序 | 任务 | 交付物 | 依赖 |
|---|---|---|---|
| 1 | `07-23-theme-core` (P0) | UITheme token 模型、ThemeManager、JSON 解析/继承/回退、`BBSSettings` 语义函数改走主题、`theme` 0/1 → `themeId` 迁移、内置 dark/light JSON | 无 |
| 2 | `07-23-theme-widget-tokens` (P1) | 共享控件与编辑器硬编码 `Colors.*` 收敛到 token,含常量→token 映射表 | 1 |
| 3 | `07-23-theme-motion-layer` (P1) | UIMotion 基建 + 6 接入点 + 动画 token 消费 + 全局开关/速度 | 1(与 2 并行) |
| 4 | `07-23-theme-skin-settings-ui` (P1) | 设置"皮肤"栏 + 主题选择器 overlay + 重载/打开文件夹/导出模板 + 双语文案 | 1(与 2、3 并行;动画开关项依赖 3 的设置值) |
| 5 | `07-23-theme-builtin-packs` (P2) | 内置主题资源落地(dark/light + 1 个演示主题)、docs/theme-spec 与实现对齐校验、贴图覆盖示例 | 1–4 |

依赖以各子任务 prd/implement 中的文字为准(Trellis 树不表达依赖)。

## 跨子任务验收标准(父任务最终集成审查)

1. `./gradlew check` 全量通过(含各专项 test source set)。
2. dev 客户端:设置 → 皮肤 → 在 dark/light/演示主题之间切换,dashboard、影片编辑器、
   设置面板、右键菜单、通知、tooltip 全部即时换色,无残留旧色。
3. 把 `docs/theme-spec/example-theme/` 拷进 `config/bbs/assets/themes/`,重载后列表出现、
   可选用、色板预览正确;故意写坏 JSON 重载,不崩溃、回退 dark、日志有警告。
4. 旧配置(`theme: 0/1`)升级后自动迁移为对应 themeId,视觉不变。
5. 动画:默认主题下 6 个接入点均有过渡;全局开关关闭后与迁移前逐帧行为一致;速度倍率生效。
6. 规范文档与实现一致(builtin-packs 任务里逐 key 校验清单)。

## 约束

- **输入安全红线**:动画只做绘制期变换,严禁改变命中判定区域、事件分发时机或元素生命周期
  (2026-07-21 刚拆除过 GPT 引入的中央手势捕获机器,教训见项目记忆 gesture-ownership-teardown)。
- 不引入新第三方依赖;JSON 解析走 BBS 自己的 data 体系(`MapType`/`DataToString`)。
- 不改 Addon API 2.0 冻结面;主题能力后续如需暴露给插件,另开任务。
- 遵循 mchorse UI 框架惯例;`Colors.parse` 已支持 `#RRGGBB/#AARRGGBB`,颜色解析复用它。
- 服务端(dedicated server)路径不得触发主题加载(主题只在客户端渲染路径被消费)。
- 只改 `new/` 仓库;提交须用户确认。
