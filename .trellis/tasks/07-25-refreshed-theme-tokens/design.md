# Design: Refreshed 主题 token 与内置主题包

执行者注意:本任务**只改主题体系与资源**,不做动效/自动隐藏(那是兄弟任务)。红线见 `.trellis/spec/frontend/bbs-ui-theming.md`,必读。

## 1. 新增 token 集(UITheme + ThemeParser + BBSSettings 语义函数)

在 `new/src/main/java/mchorse/bbs_mod/ui/themes/UITheme.java`(现有 token 见 :24-37)新增下列 int 颜色 token,ThemeParser 按现有 `colors` 段规则解析(base 链继承、坏值回退 warn):

| token(JSON key) | 语义 | Refreshed 参考值(refreshed 主题用) |
|---|---|---|
| `field_fill` | 输入框填充 | -15066079 |
| `field_border` | 输入框边框 | -12956328 |
| `tab_active_line` | 标签活跃下划线 | -12171686 |
| `tab_active_gradient` | 标签/图标活跃渐变 | -2008660392 |
| `area_tint` / `area_tint_light` | 区域着色两档 | -15198178 / -15000798 |
| `drop_fill` / `drop_border` | 拖放提示填充/边框 | 1142034968 / -1440077258 |
| `splitter_active` / `splitter_idle` | 分割条两态 | -1434813812 / 0x22FFFFFF |
| `shadow_muted` | 柔和阴影 | -14277074 |
| `trackpad_scrub` | 轨迹垫擦除 | -9539972 |
| `notification_fill` / `notification_text` | 通知背景/文字(边框色沿用通知类型色,不做 token) | mulRGB(-15789804,0.95F) / 现文字色 |
| `selection_fill` / `selection_outline` | 框选填充/描边(供 visual-details 子任务用) | 见其 design |
| `icon_pressed` / `icon_disabled` | 图标按下/禁用态背景(供 visual-details 用;active=tab_active_gradient) | 见其 design |
| `scrollbar_shadow` | 滚动条阴影(0=无,供 visual-details 用) | -2013265920 |

**dark 默认值规则(最重要)**:每个新 token 的 dark 默认值 = **当前接入点实际使用的常量**;若该视觉当前不存在(如 scrollbar_shadow、icon_pressed),dark 默认 = 0(哨兵:功能关闭/沿用旧路径)。禁止把 Refreshed 值当 dark 默认。本任务**只定义 token 与解析**,接入点替换由使用方子任务做;因此 dark 默认值要与使用方任务对齐:先在本任务里给出保守值(0 或明显等价常量),使用方接入时若发现现场常量不同,回本任务修默认值并补对拍。

落点清单:
- UITheme.java:新字段(final)+ Builder 复制 + 构造。
- ThemeParser.java:`colors` 段新 key 解析(照抄现有 color 解析 helper);defaultDark 默认值表。
- BBSSettings.java:每个 token 一个语义函数(照 v1 模式,读 ThemeManager.current())。
- ThemeCoreTest:dark 对拍表逐 token 断言;坏值回退断言(挑 2 个 key 抽样);base 链继承断言。

## 2. 内置主题包 `themes/refreshed/`

位置:`new/src/client/resources/assets/bbs/assets/themes/refreshed/theme.json`(client source set,与 strawberry 同级)。ThemeManager 内置 id 白名单加 `"refreshed"`。

内容要求:
- `base`: `"dark"`;`light`: false。
- 核心色:surface 族全部取 `#0F1114`(-15789804)系:surfaceChrome=UI_CANVAS、surfaceBase=UI_CANVAS、surfaceRaised 取 mulRGB(UI_CANVAS,1.25) 手算值(实现时算好写死,JSON 不支持表达式)、surfaceDeep=mulRGB(UI_CANVAS,0.58)(=UI_FILM_EDIT_ZONE)、surfaceDivider=UI_TAB_ACTIVE_LINE。
- accentPrimary=-7508552(ACCENT_PURPLE_DEFAULT);stateCursor=-11012822;其余 state 族沿用 dark。
- 上表全部新 token 填 Refreshed 参考值。
- corner_radius:chrome 5 / panel 5 / widget 5(Refreshed 默认 `ui_corner_radius_*`=5)。
- motion:显式写出当前全部 entry,统一使用 spring 类型;兄弟任务新增 `toggle`/`taskbar_hide` 后同步补入,禁止继承 dark 的 ease 类型。
- icons:从 `temp/bbs-refreshed-2.0-public-1.20.1.jar` 逐字节复制原始 1024x1024 atlas 到 `themes/refreshed/icons.png`,并配置 `"icons": "assets:themes/refreshed/icons.png"`。`Icon.textureW/textureH=256` 定义的是逻辑 UV 空间,原版 Refreshed 同样以 256 逻辑坐标消费 1024 物理像素 atlas,因此不得缩放、重绘、锐化或改色。
- 背景:不设贴图(background 缺省),backgroundMode/dim 缺省。

## 3. docs 同步

- `new/docs/theme-spec/README.md`:新 token 逐 key 文档(语义、默认、取值)。
- `new/docs/theme-spec/example-theme/theme.json`:全 key 显式,补新 token;与 jar 内副本逐字节同步(同步方向 docs → resources;Files.mismatch 测试把关)。

## 4. 红线回顾

- 不删不改现有五主题的现有 key;dark 逐位对拍必须绿。
- parse-time 固化;渲染期零查找零分配;`Colors.GRAY` 无 token 一律保留(见 spec)。
- 本任务不改 UIDashboardPanels、UIToggle、UINotifications、UIIcon、Scroll、UIClips/UIKeyframes 的行为代码;但负责让使用方所需的 token、motion schema 和主题资源完整可用。
