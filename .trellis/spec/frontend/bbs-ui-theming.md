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
- **解析期定死**:一切新 schema(tracks/decorations/corner_radius/background_mode)在
  `ThemeParser` 解析为不可变结构,坏值逐 key 回退 + warn(延续 v1 契约);渲染期零查表零分配。
