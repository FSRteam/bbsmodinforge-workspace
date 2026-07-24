# v2 技术设计总纲 + BBS Refreshed 2.0 研究报告

本文是父任务级技术方向与参考实现研究结论。各子任务启动前的 `design.md` 引用本文坐标展开,不重复抄录。

## 一、动画架构:四层升级路线

### 层 1 弹簧内核(motion-engine)

"macOS 感"的最大来源是弹簧物理 + 速度连续的打断,而非更多接入点。

- `UITween` 保持 `snap/to/update/isSettled` 接口不变,内部增加速度状态与弹簧求解分支 —— v1 六接入点零改动自动升级。
- 弹簧用**欠阻尼闭式解**(非逐帧积分,变帧率无累积误差):
  `x(t) = target + e^(-ζωt)(A·cos(ω_d t) + B·sin(ω_d t))`,A/B 由打断时刻的当前值与当前速度确定;ζ≥1 走临界/过阻尼分支。约 60 行。
- 参数化学 Apple:`response`(自然周期,秒)+ `damping`(阻尼比,<1 过冲)。全局 `motion.speed` 缩放 response。
- settle 判定 `|x-target|<ε && |v|<ε`,settled 后短路直达旧渲染路径(红线延续)。
- Batcher2D 增加**全局 alpha 乘栈**(push/pop 乘子,作用于最终顶点色):整元素淡入淡出的统一手段,退场动画(层 2)的前提。注意 MC 字体渲染在极低 alpha 有 discard 跳变,需 clamp 下限。

### 层 2 退场对称(motion-exit)

- 生命周期红线有意识松动:关闭请求 → 元素进入"退场态"(只渲染、命中判定即刻失效)→ 反向动画完成 → 真移除。
- v1 遗留"overlay 关闭反放残影"在此正式解决。

### 层 3 覆盖面(motion-coverage)

按参考实现验证过的机制逐项接入(坐标见研究报告):平滑滚动、hover 缩放/按下回弹(pose 变换,UIOverlay 有先例)、仪表盘面板布局过渡("写回 flex"机制)。

### 层 4 编排(motion-tracks)

- 每接入点固定可动画属性集(alpha/scale/x/y),主题给每条 track 起点值 + 弹簧或时长曲线参数;预设变换 SCALE / SLIDE_FROM_RIGHT 等作为快捷方式。
- **不做 keyframe DSL**:Apple 对常规 UI 也只暴露弹簧参数 + 预设过渡;全 keyframe 是过度设计且易产出灾难动画。
- theme.json 示例(全部新键可选,v1 条目继续有效):

```json
"overlay": {
  "type": "spring", "response": 0.35, "damping": 0.82,
  "tracks": { "alpha": {"from": 0}, "scale": {"from": 0.95}, "y": {"from": 8} }
}
```

## 二、视觉架构

- **圆角是根本原语,nine-slice 是增强**:参考图的柔软感 80% 来自圆角 + 配色 + 装饰;圆角只需数值参数无需美术,故 rounded-corners 先行,nine-slice 殿后。
- 主题新增 `style.corner_radius` 组(建议三级:chrome 弹层/panel 卡片/widget 控件),0 = v1 直角逐位等价。
- 贴花层:主题声明 `decorations: [{texture, anchor, offset, scale, opacity, layer}]`,渲染叠加、永不参与命中。
- 背景扩面:v1 `background` 仅 `UIDashboard.java:367` 消费;扩为根面板通用 + `mode: cover|tile|stretch` + 可选暗化系数。

## 三、BBS Refreshed 2.0 研究报告

反编译根:`temp/bbs-refreshed-2.0-public-1.20.1-decompiled/`(src/ 可读源;1.20.1 Fabric + intermediary 映射,`class_287`=BufferBuilder 一类名称移植时需转译为我们的 NeoForge 1.21.1 官方映射)。导航:`README_UI_GUIDE.md`。

### 3.1 圆角渲染(→ rounded-corners 直接移植)

`src/mchorse/bbs_mod/ui/framework/elements/utils/Batcher2D.java:129-299`:

- 结构:中心+四边条用纯色三角形(五宫格),四角用 **mask 纹理三角扇**(`textures/gui/rounded_rect_mask.png`,GL_LINEAR 双线性采样得到抗锯齿),原版 position_color / position_tex shader,零自定义 GLSL。
- mask 缺失时优雅退化为纯几何三角扇(无抗锯齿但不崩)。`clampRoundedRectRadius` 防溢出;`r<0.5` 直接走旧 `box()`(天然满足"半径 0 逐位等价")。
- `roundedBox`(:172)填充、`roundedFrame`(:235)边框+填充双层、`filledCircle`(UIToggle.java:144 调用)圆形。
- `ui/utils/UIArt.java:16-17` mask 资源常量;`UICornerRadii.interfaceChrome()`(UIOverlayPanel.java:244 调用)集中半径配置 —— 我们改为主题参数。
- 现成样板:`UIToggle.renderSkin`(UIToggle.java:120-148)= macOS 风格开关(圆角轨道 + filledCircle 滑块 + 颜色插值 scratch 零分配)。

### 3.2 弹层开场动画(→ motion-tracks 预设先例)

`ui/framework/elements/overlay/UIOverlayPanel.java`:

- :68-114 开关与进度:`useOpenPanelAnimation()` 可覆写、设置 `textEntryOpenAnim`(bool)+ `DurationSec`(0.5 默认)+ `StartScale`(0.85 默认,BBSSettings.java:201-204,微调项 `.invisible()` 隐藏);固定时长 + `EXP_OUT`。
- :204-228 变换应用:matrices push → SLIDE 时 `translate((1-eased)*w, 0)` → 围绕中心 scale → render → pop。枚举 `OpenPanelAnimTransform { SCALE, SLIDE_FROM_RIGHT }`(:256);设置面板覆写为 SLIDE_FROM_RIGHT(UISettingsOverlayPanel.java:43)。
- :95-97 `getOpenPanelBackdropFade()` 膜透明度与面板动画同源绑定。
- :230-241 **deferred focus**:动画完成(t≥0.9999)才应用焦点,避免动画中文本框光标错位 —— 细节讲究,照抄。
- 无退场动画(close 直接 closeItself)—— 我们层 2 补齐,是超越点。

### 3.3 布局过渡(→ motion-coverage;修正"即时模式做不了布局动画"的判断)

`ui/film/UIFilmPanel.java:1970-2144`:

- 机制:布局变更前 `beginLayoutTransitionCapture()` 快照各面板归一化 bounds(from)→ 应用新布局 → `endLayoutTransitionCapture()` 快照 to → 之后每帧 `advanceLayoutTransition()` 把插值 bounds **写回 flex(`panel.relative(...)...`)并真实 `resize()`** —— 渲染与命中始终一致,无"画的位置≠点的位置"问题。
- 打断:`captureInterpolatedLayoutBoundsAsFrom()`(:2093)把当前插值状态冻结为新 from(值连续)。
- 阈值:from/to 差异 <5e-4 不动画(:2067)。
- 代价:动画期间每帧重布局 —— 只适合面板级(数量少、布局树浅),不做每控件通用机制。
- 设置:`filmEditorLayoutTransitionEnabled` + `Sec`。

### 3.4 平滑滚动(→ motion-coverage,约 5 行核心)

`ui/utils/Scroll.java:273-313`:

- 双值模型:`targetScroll`(逻辑,滚轮/拖拽即时改)+ `scroll`(视觉,每帧 `lerp(scroll, target, min(1, tickDelta/2.5))` 指数趋近 —— 本质一阶弹簧,无过冲)。
- 设置 `scrollingSmoothness`(默认 true)、灵敏度分横竖(BBSSettings.java:229-234)。
- 我们移植时:视觉值用于渲染偏移,逻辑值用于命中/边界;与 v1 scrollbar 淡出动画共存。

### 3.5 对照结论(不参考项)

- `ui/theme/UiThemeCache.java`:硬编码常量单例,无 JSON/继承/多主题 —— 我们 v1 架构领先一代,主题系统不需要参考它;但其取色点(textFieldBorder 等)可作为控件级 token 命名参考。
- 通知动画 `Lerps.envelope + QUAD_INOUT`:v1 已对齐同类实现,无新信息。
- 全部动效为固定时长 + EXP_OUT 类曲线,无真弹簧、无速度继承 —— 用大初速曲线掩盖打断跳变。弹簧内核是我们对参考实现的代际超越点。

## 四、风险与回滚

- 圆角接入面广(逐控件替换绘制调用):按控件类分批,每批独立可回滚;半径 0 等价性保证任何回滚点渲染正确。
- 弹簧内核替换 UITween 内部:v1 测试(testUiMotions)全部保留跑绿 + 新增弹簧数学用例(闭式解对打表)。
- 退场动画碰生命周期:仅 UIOverlay/UIContextMenu 两个封闭出口,退场态标志 + 命中短路,行为开关可一键回 v1 即时移除。
- alpha 栈动 Batcher2D 顶点色路径:乘子恒 1 时逐位等价,可静态验证。
