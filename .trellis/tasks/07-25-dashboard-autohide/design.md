# Design: 仪表盘任务栏自动隐藏

执行者注意:红线见 `.trellis/spec/frontend/bbs-ui-theming.md`。参考实现:`temp/bbs-refreshed-2.0-public-1.20.1-decompiled/src/mchorse/bbs_mod/ui/dashboard/panels/UIDashboardPanels.java:41-199`(1.20.1 Fabric,只抄机制不抄代码)。我方锚点:`new/src/client/java/mchorse/bbs_mod/ui/dashboard/panels/UIDashboardPanels.java`。

## 1. 设置(全局,非主题)

BBSSettings 新增(`refreshed` 分类下,与其它 Refreshed 行为开关集中):
- `dashboard_auto_hide_taskbar`(bool,默认 **false**——与 Refreshed 的 true 不同,存量用户现状无此行为);
- `dashboard_taskbar_hide_delay`(int ms,默认 100,范围 100-15000)。

动画时长**不设全局设置**——样式归主题 motion(见 §3),与父任务 PRD"设置管开关/延迟、主题管样式"的分工一致。

## 2. 隐藏机制(整体位移,无生命周期例外)

状态机(全部放 UIDashboardPanels 私有字段):
- `hiddenAmount ∈ [0,1]`:0=完全展示,1=活动面板和底栏全部滑出。由 UITween(弹簧)驱动,目标值 0/1。
- 触发:设置开启时,每帧(render 内)检测鼠标是否在任务栏区域或底部 16px 启示条内;离开累计超过 delay → target=1;进入任务栏区域立即 target=0;启示条 hover 350ms → target=0。常量:`TASKBAR_HEIGHT=20`(用现有实际高度,勿硬编码新值,读现有布局常量)、`REVEAL_STRIP_PX=16`、`REVEAL_HOVER_MS=350`。
- 应用方式:**布局坐标位移 + 命中一致**。活动面板和任务栏都按 `hiddenAmount * UIDashboardPanels.area.h` 向下平移;活动面板高度始终保留 `rootHeight - TASKBAR_HEIGHT`,不再通过减少 bottom inset 扩面。每次位移后统一 resize,使子控件绘制和命中使用同一实际坐标。**不 setEnabled,不移除元素**——完全滑出时二者仍在树上,但位于屏幕外且原位置无命中。
- settled 短路:hiddenAmount==0 且未启用/未在动画时,布局与现状**逐位一致**;完全隐藏时仍按当前根高度重算位移,窗口 resize 后不能露出残边。
- 设置关闭(默认):整个状态机不进入,零开销,行为逐位等于现状。
- motion 关闭(UIMotions.enabled()==false 或 entry 关):target 改变时 hiddenAmount 直接跳变 0/1,无动画。

Refreshed 用 EXP_INOUT 定时缓动;我们**不移植 Easings**,统一走 UITween 弹簧内核(v2 既有),观感由 motion 参数调。

## 3. 主题 motion key `taskbar_hide`

- UITheme 新 entry `taskbar_hide`(UIThemeMotion,ThemeParser motion 段照抄现有 entry 解析),UIMotions 加静态访问器。
- 默认(defaultDark):**enabled**、弹簧参数照 layout entry 的默认档(自动隐藏功能本身有全局开关把门,entry 默认开不影响四旧主题——设置关则整条路径不进入)。
- refreshed/strawberry 主题可自定义参数;docs/theme-spec 同步新 key。

## 4. Ctrl+S 切换(保存优先)

- 我方 `Keys.SAVE` 已=Ctrl+S(Keys.java:25)。新增 KeyCombo `TOGGLE_TASKBAR_AUTOHIDE`(同键 S+LCTRL,独立 lang key)。
- 注册位置:UIDashboard(或 UIDashboardPanels 所属 root)层级的 keys().register——**必须**注册在比各面板保存回调更浅(更晚匹配)的层级。现有 keybind 分发:焦点/深层元素先消费;film editor 等已注册 SAVE 的面板会先吃掉 Ctrl+S → 保存;未注册面板落到 dashboard 层 → 切换自动隐藏并 toast/通知一次当前状态(用现有 UINotifications,复用现有文案模式,新增 lang key)。
- 实现时必须实测验证优先级顺序(film 面板内按 Ctrl+S = 保存且不切换;dashboard 空面板按 = 切换),把结果记入任务 journal;若分发顺序不满足"深层先",改为在切换回调里检查当前 panel 是否声明了 SAVE(查 keybind 注册表)再决定是否让行,并在 journal 记录方案变更。
- 切换写回 `dashboard_auto_hide_taskbar` 设置值(持久化)。

## 5. 测试

- themeCoreTest:`taskbar_hide` entry 解析/坏值回退/settled 短路(UIMotionsTest 模式)。
- 命中一致性与 Ctrl+S 优先级为游戏内冒烟项(写入父任务冒烟清单):活动面板+底栏完整滑出、底部启示条唤回、滑出中点击面板/底栏原位置不应误触、窗口 resize 后不露边、Ctrl+S 两种界面行为。
