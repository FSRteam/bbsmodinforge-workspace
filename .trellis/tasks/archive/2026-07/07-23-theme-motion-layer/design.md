# Design: theme-motion-layer 模块细化

总架构见父任务 design.md §3.5。红线重申:**render-only**。

## 基建

```java
// client: mchorse/bbs_mod/ui/utils/motion/UITween.java
public class UITween
{
    private float value;        // 当前值(已缓动)
    private float target;       // 目标 0/1
    private long startMs;
    private float startValue;

    public void to(float target, UIThemeMotion spec);  // 关闭态 ⇒ value=target 立即
    public float update(long nowMs);                    // render 里每帧调,返回当前值
    public boolean isDone();
}

// client: mchorse/bbs_mod/ui/utils/motion/UIMotions.java —— 静态工具
public static UIThemeMotion overlay()/panelSwitch()/hover()/notification()/contextMenu()/scrollbar();
    // = ThemeManager.current().X 与用户开关/速度合成后的有效 spec(disabled 合成为 duration=0)
public static boolean enabled();   // 主题总开关 && BBSSettings.motionEnabled
```

- `to()` 在开关关闭或时长≤0 时直接置位并 `isDone()=true` —— 调用方可用
  `if (tween.isDone()) { 旧代码路径 }` 完全短路,保证关闭等价性。
- 时钟:实现时核实 UI 渲染循环现有毫秒源(`UIContext`/render 传参或
  `Util.getMillis()` 等 MC 惯用),全层统一一个来源。

## 六接入点实现要点

| 接入点 | 文件(实现时核实) | 做法 | 输入安全 |
|---|---|---|---|
| overlay | `UIOverlay`/`UIOverlayPanel` render | 打开:全局 alpha 淡入 + 以面板中心 0.95→1 缩放(matrix push/scale 包住绘制);关闭:反向,**元素当帧移除、只有视觉残影反放**(不延迟 remove) | 命中区域始终=最终 area;关闭即时移除避免"幽灵面板吃点击" |
| panel_switch | `UIDashboardPanels.setPanel`/render | 记录切换时刻,在 panels 区域上叠一层 `baseSurface()` 色矩形,alpha 从 1→0 淡出 | 纯覆盖绘制,不碰 panel 生命周期(setPanel 逻辑零改动) |
| hover | UIButton/UIIcon/UIToggle render | 现有 `this.hover ? A : B` 改为 tween 目标 0/1,`Colors.lerp(B, A, tween)` | 只是取色;hover 检测本身不动 |
| notification | `UINotifications` render | 每条通知入场:从边缘偏移 N px + alpha 淡入;离场同理。位移用绘制偏移 | 通知不可交互(核实;若可点则命中区仍用最终位) |
| context_menu | `UIContextMenu` 基类 render | 出现:alpha 淡入 + 从锚点方向 4px 位移归零 | 当帧即可点(alpha 不影响命中);位移仅绘制偏移,菜单项命中区=最终位置。时长默认≤100ms,视觉位移小 |
| scrollbar | `Scroll` 渲染处 | 记录 lastActivityMs(滚动/拖拽/悬停);静止 >800ms 后 alpha 渐隐至 0;活动即回 1 | 淡出后拖拽仍按几何命中(只是画不出来也能摸到)——为避免"摸不到看不见的东西"的困惑,悬停到滚动条几何区立即恢复 alpha |

统一模式:每个接入点持有自己的 `UITween`(或时间戳),render 开头 `update(now)`,
用值改绘制参数;**任何 input/resize/生命周期方法零改动**。

## 与 widget-tokens 的冲突面

hover 接入点与 widget-tokens 都会改 UIButton/UIIcon/UIToggle 的取色表达式。
执行顺序上后做的一方 rebase 少量行即可;语义上正交(tokens 决定 A/B 两端颜色,
motion 决定两端之间怎么过渡)。

## 测试

- 现有输入测试全量跑:`BBSUiInputDispatcherTest`、uiInputMailboxTest/uiMirrorDemandTest/
  filmEditorTest source sets(动画不得改变任何断言结果)。
- 新增小单测:`UITweenTest`(到位/短路/速度倍率合成/缓动查表);其余接入点靠人工冒烟
  (prd 验收清单)——UI 渲染无 headless 断言基建,不为此新建。
