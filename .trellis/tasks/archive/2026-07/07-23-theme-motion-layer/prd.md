# PRD: UI 动画层与动画 token

父任务:`.trellis/tasks/07-23-fsr-theme-system`。
依赖:`07-23-theme-core` 已完成(消费 `UITheme` 的 motion 字段)。可与 widget-tokens 并行,
但注意二者都会触碰 framework 文件,分次执行更稳。

## 目标

给 UI 框架加一个极小的绘制期动画层,让主题 JSON 能配置 6 个接入点的
开关/时长/缓动(缓动 = `Interpolations.MAP` 键名),外加用户级全局开关与速度倍率。
**动画关闭时逐帧行为与现状一致。**

## 需求

1. 基建 `mchorse.bbs_mod.ui.utils.motion`(client):tween 状态对象 + 求值工具,
   消费 `UITheme` 的 `UIThemeMotion`;有效时长 = `duration / (theme.motionSpeed × BBSSettings.motionSpeed)`,
   任一 enabled=false(主题条目、主题总开关、用户总开关)⇒ 立即到位。
2. 六个接入点(全部实现):
   - **overlay**:`UIOverlay`/`UIOverlayPanel` 打开与关闭,淡入 + 轻微缩放(0.95→1)。
   - **panel_switch**:dashboard 面板切换,短促表面色纱罩淡出(veil,用 `baseSurface()`)。
   - **hover**:`UIButton`/`UIIcon`/`UIToggle` 的 hover 颜色从瞬变改为按时长渐变。
   - **notification**:`UINotifications` 滑入/滑出 + 淡入淡出。
   - **context_menu**:右键菜单(`UIContextMenu` 系)出现时淡入 + 从锚点微展开。
   - **scrollbar**:滚动条静止一段时间后淡出,滚动/悬停恢复。
3. 用户设置值:`BBSSettings.motionEnabled`(bool,默认 true)、`BBSSettings.motionSpeed`
   (float 0.5–2.0,默认 1.0),注册进 `skins` 分类(UI 摆放归 skin-settings-ui 任务)。
4. **红线(不可妥协)**:动画只影响绘制(位移/缩放/alpha/颜色插值),不得改变:
   命中判定区域(`area`)、事件分发时机与顺序、元素 add/remove 生命周期、焦点/捕获状态。
   关闭动画路径必须与旧代码路径等价(不是"时长为 0 的动画",而是可短路直达旧行为)。

## 验收标准

- [ ] `compileClientJava` + `./gradlew check` 通过(含 uiInputMailboxTest、filmEditorTest 等
      输入相关 source set)。
- [ ] `motionEnabled=false` 时:6 接入点行为与改动前一致(重点:overlay 打开当帧即可点击、
      菜单当帧可选、通知立即出现)。
- [ ] 默认主题:6 接入点均有可感知过渡;`motionSpeed=0.5/2.0` 快慢明显;
      主题 JSON 单独关掉某一项只影响该项。
- [ ] 缓动名对照:同一接入点换 `sine_out`→`bounce_out`,曲线可辨。
- [ ] 动画进行中输入正确:overlay 淡入期间点击按其**最终**位置命中;滚动条淡出后 hover
      立即恢复且可拖;拖拽剪辑/gizmo/时间轴全程无反弹或卡死(gesture 回归)。
- [ ] bbs-web-remote 远程会话冒烟:远程操作 UI 期间开关动画,无输入异常。

## 约束

- 时间源用现有 UI 渲染循环可得的毫秒时钟(核实 `UIContext` 里已有的 tick/partialTicks 惯例,
  不新开线程、不用系统定时器)。
- 不给每个 UIElement 强加动画状态字段;只有 6 接入点自己持有 tween 状态。
- Scroll 已有的平滑滚动(`scrollingSmoothness`)保持原样,不并入本层。
