# BBS UI Theming Rules (mod UI, `new/`)

> 适用范围:`new/src/client` 的 BBS 自有 UI 框架(非 web)。
> 数据契约:`new/docs/theme-spec/README.md`;运行时:`mchorse.bbs_mod.ui.themes`。

## 取色规约

1. **新 UI 代码禁止硬编码语义色**。语义色一律走 `BBSSettings` 语义函数:
   - 表面:`chromeSurface()/baseSurface()/raisedSurface()/deepSurface()/dividerColor()/inputSurface()`
   - 文字:`textColor()/mutedTextColor()`;阴影开关 `textShadow()`
   - 强调:`primaryColor(alpha)`(内部处理"跟随主题/用户覆盖")
   - 状态:`positiveColor()/negativeColor()/warningColor()/activeColor()/highlightColor()/cursorColor()`
2. 语义函数返回**完整 ARGB**(不透明)。与 alpha 面罩组合时:
   - 需要重设 alpha:`BBSSettings.color(fn(), Colors.A75)` 或 `Colors.setA(fn(), a)`
   - 传入"后续会 `| Axx` 补 alpha"的 API(如 `menu.action` 的 color、keyframe 通道色):
     传 `fn() & Colors.RGB`,避免 `0xbb.. | 0xff..` 污染 alpha 位。
3. **允许保留 `Colors.*` 常量**的场合(不是语义色):
   - 图标 tint 的白(`batcher.icon(icon, Colors.WHITE, ...)`)——图标换色走主题贴图 atlas;
   - gizmo 轴色(`PLANE_XZ/XY/ZY`、RED/GREEN/BLUE 作向量分量色)、关键帧/曲线通道色;
   - 纯 alpha 面罩/黑遮罩/阴影黑(`Colors.A50` 单独使用等)、颜色数学中间量;
   - 内容颜色编码(clip 分类色、CDN 状态色板、camera copy 菜单的类型三色);
   - `Colors.GRAY`(0xff888888)——**没有对应 token**(mutedTextColor 是 0xffaaaaaa,值不等),
     禁用态/更暗文字暂保留常量,v2 再考虑 textDisabled token。
4. 控件默认色字段(`UIButton.textColor`、`UIToggle.color`、`UIText`、`UILabel`、`Textbox`)以
   `Colors.WHITE` 为"跟随主题"哨兵:render 期 `== Colors.WHITE ? BBSSettings.textColor() : 字段`。
   给控件显式设色时不受主题影响;想要主题字色就不要设色。
5. 文字绘制默认色的汇聚点在 `Batcher2D.text/textShadow/textCard`(无 color 重载)与
   `text(label,x,y,color,shadow)`(textShadow token 拦截、light darkenWhite)。改动文字全局
   行为只动这些单点,不要在调用点散改。

### Refreshed 扩展 token 契约

- `UITheme` / `ThemeParser` 支持 `colors` 根下的扩展语义 key:
  `field_fill/field_border/tab_active_line/tab_active_gradient/area_tint/area_tint_light/`
  `drop_fill/drop_border/splitter_active/splitter_idle/shadow_muted/trackpad_scrub/`
  `notification_fill/notification_text/selection_fill/selection_outline/icon_pressed/`
  `icon_disabled/scrollbar_shadow`。消费端只经 `BBSSettings.*Color()` 访问,不得直接查 JSON。
- dark 主题中旧版没有对应视觉的 key 使用 ARGB `0` 哨兵。消费端遇到 `0` 必须走原绘制
  分支或不绘制,不能把透明黑当作已配置效果；已有视觉的 key 默认值必须等于旧常量。
- 新增/接入扩展 token 时,`ThemeCoreTest` 至少覆盖:dark 对拍、显式解析、坏值回退、base
  继承。`docs/theme-spec/example-theme/theme.json` 与 jar 内 example 必须继续逐字节一致。

### 带前导图标的内嵌文本框

- `UITextbox.icon(Icon)` 为原地编辑框启用左侧语义图标槽；`barColor(int)` 只覆盖槽右侧
  色条，未指定时使用主题 primary。未调用 `icon(...)` 时 `Textbox.contentInset` 必须为 0，
  旧文本绘制、光标、选择和鼠标索引路径保持不变。
- 图标是纯绘制装饰。不要缩小 `Textbox.area`、拦截点击或把控件替换成弹窗预览；背景、
  focus 边框和点击区域仍覆盖完整输入框。文本绘制与 `getIndexAt(...)` 必须消费相同的
  content inset。
- 图标槽填充读取 `fieldFillColor()`，ARGB `0` 时回退 `chromeSurface()`；圆角只读取
  `cornerWidget()`。极窄控件只在 content inset 非 0 时把可用宽度钳制到非负，避免改变
  普通文本框的兼容路径。
- MoLang/表达式字段使用代码、轴向、时间等语义图标；资源 ID、事件名、日志、声音名和
  普通数值字符串不得伪装成表达式字段。

```java
// Good: 保留内嵌编辑，只增加语义装饰。
field.icon(Icons.X).barColor(Colors.RED);

// Bad: 为了放图标缩小命中区域，或点击后改用独立表达式弹窗。
field.textbox.area.x += 20;
field.textbox.area.w -= 20;
```

验证要求：`testParticleRuntimeConsistency` 覆盖默认/带 inset 的鼠标索引与极窄宽度边界；
通用控件或主题绘制变更后还必须运行 `compileClientJava`、`testThemeCore` 和完整 `check`。

## 主题运行时红线

- 渲染热路径只读 `ThemeManager.current()` 的字段;禁止渲染期做字符串查表/解析/IO。
- `BBSSettings.register` 及任何服务端可达路径禁止 touch `ThemeManager`(dedicated server
  不得触发主题类初始化)。
- 主题 id 参与文件路径拼接,必须过 `ThemeManager.isValidId`(`[a-z0-9_-]+`)。
- 动画(motion 层)只做绘制期插值,严禁改命中判定/事件分发/元素生命周期。
  **唯一例外(v2 motion-exit)**:`UIOverlay` 与 `UIContextMenu` 的退场动画——关闭请求即
  语义关闭(`setEnabled(false)` 断输入、事件立即发出),元素仅为反向动画多渲染若干帧,
  tween settle 后经 `context.render.postRunnable` 延迟物理移除(避免渲染遍历中改树)。
  其它任何元素不得效仿。

## v2 动效/视觉规约

- **弹簧内核**:`UITween` 内部支持 `spring` 分支(欠/临界/过阻尼闭式解,打断速度连续);
  接口 `snap/to/update/isSettled` 不变,调用方零改动。参数 `response`(秒)+ `damping`
  (阻尼比),经 `motion.speed` 缩放。settled 后必须短路回旧渲染路径(逐位等价红线)。
- **alpha 栈**:整元素淡入淡出统一走 `Batcher2D.pushAlpha/popAlpha`(乘子恒 1 时逐位
  等价);文字在极低 alpha 有 discard 跳变,由 Batcher2D 统一 clamp,调用点不处理。
- **圆角**:半径来源仅 `BBSSettings.cornerChrome()/cornerPanel()/cornerWidget()` 三级 token,
  控件禁止私设半径。绘制走 `Batcher2D.roundedBox/roundedFrame/filledCircle`;半径 0(或
  <0.5)必须落回旧 `box()` 分支(v1 主题逐位等价基准:dark/light/example 的 corner_radius
  全 0,不得改动)。
- **track 编排**:overlay/context_menu 的进出场变换由条目 `preset/tracks` 参数化
  (`UIThemeMotionTracks`,固定属性集 alpha/scale/x/y,应用顺序 translate→scale→alpha);
  无 tracks 的条目保持接入点内置变换。退场反向复用同一编排,不新建参数。
- **装饰/背景**:贴花与根背景统一走 `UIThemeBackdrop`(client helper),绘制顺序
  背景→dim→贴花→主 UI;永不参与命中。背景消费点:UIDashboard 与
  `UIBaseMenu.renderDefaultBackground`,不要在别的面板私接。
- **停靠交互表面**:`UIFilmPanel` 与通用 `UIDockLayout` 的面板拖动柄、分割条和投放
  高亮统一调用 `UIDockStyleRenderer`,禁止在宿主内复制绘制分支。Refreshed 分支分别消费
  `area_tint*`、`splitter_*`、`drop_*` 与 `cornerWidget()/cornerPanel()`;对应扩展 token
  全为 0 时必须进入旧 primary 方形/单像素路径。
- **仪表盘整体自动隐藏**:`UIDashboardPanels` 的活动面板与底栏共享同一
  `hiddenAmount * rootHeight` 纵向位移。位移必须写入实际布局坐标并统一 resize,使绘制和
  命中同步;根容器保持静止以承载底部 reveal strip。禁止仅移动底栏或只变换渲染矩阵。
- **解析期定死**:一切新 schema(tracks/decorations/corner_radius/background_mode)在
  `ThemeParser` 解析为不可变结构,坏值逐 key 回退 + warn(延续 v1 契约);渲染期零查表零分配。

```java
// Correct: both dock hosts share one renderer and preserve the legacy sentinel path.
UIDockStyleRenderer.renderDropZone(context, target.area, dropZone, DROP_EDGE_MARGIN);

// Wrong: a host reads drop tokens and reimplements its own square highlight.
context.batcher.box(target.area.x, target.area.y, target.area.ex(), target.area.ey(), BBSSettings.dropFillColor());
```
