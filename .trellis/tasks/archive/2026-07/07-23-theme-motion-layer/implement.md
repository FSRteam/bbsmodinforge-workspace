# Implement: theme-motion-layer 执行清单

前置:theme-core 完成。widget-tokens 已先行(UIButton/UIIcon/UIToggle 的取色已 token 化,
本任务在其上接入 tween)。

## 清单

- [x] M1 基建:`ui/utils/motion/UITween.java`(0..1 tween,disabled/时长 0 ⇒ snap,
      `isSettled()` 供调用方短路走旧代码路径保证逐位一致)+ `UIMotions.java`
      (用户开关 × 主题开关 × 速度合成;entries 直接返回主题字段,零分配)。
      时钟源统一 `System.currentTimeMillis()`(仓库现惯例:UIContext.lastScroll、
      UIAudioRecorder.holdStart 均用它)。单测 `UIMotionsTest`(snap/禁用短路/时长合成/
      单调推进/精确到位/重定向/同目标幂等),注册 `testUiMotions` 进 verification 列表。
- [x] M2 设置值:`BBSSettings.motionEnabled`(true)/`motionSpeed`(1.0,0.5–2.0)注册进
      `skins` 分类。
- [x] M3 hover:UIButton(背景色 + 文字亮度 1−0.1f)、UIIcon(icon↔hover tint)、
      UIToggle(knob 色)。isSettled 短路 = 原三元表达式逐位。
- [x] M4 overlay:`UIOverlay.render` 打开时背景膜 alpha×f + 子树 pose scale 0.95→1
      (围绕 overlay 中心)。**偏差**:design 提的"关闭反放残影"未做——关闭仍即时移除
      (与现状一致),残影需要移除后代画容器,引入生命周期风险,v1 砍掉。
- [x] M5 context_menu:`UIContextMenu.render` 出现时 pose scale 0.97→1(围绕左上锚点)。
      当帧命中即最终位置(纯绘制变换)。
- [x] M6 notification:接管 `Notification.getFactor` 的既有滑入信封——入/离场段长度
      = 主题 duration(ms→tick),缓动 = 主题 easing;disabled ⇒ factor 恒 1(立即在位,
      prd 明确关闭态语义)。**偏差**:原版硬编码 500ms QUAD_INOUT,主题默认 150ms back_out,
      开启态视觉与旧版不同——属"动画层接管",规范默认值即契约。
- [x] M7 panel_switch:`UIDashboardPanels` 切换时(仅 lastPanel≠null)`switchVeil`
      snap(1)→to(0),render 尾部在 panel 区域画 `setA(baseSurface(), veil)` 纱罩;
      不吃输入、不碰 setPanel 逻辑;首次进入 dashboard 无纱罩。
- [x] M8 scrollbar:`Scroll.renderScrollbar` 新增带鼠标重载(旧签名 delegate,
      MIN_VALUE=无悬停检测);`scrollTo`(全部滚动路径的汇聚点)记录 `lastActivityMs`;
      静止 >800ms 淡出(主题时长/缓动),dragging/活动/悬停几何区 ⇒ snap(1) 立即显形;
      拖拽命中始终按几何(淡出只是画不出来)。6 个调用处升级传 `context.mouseX/mouseY`。
      Scroll 原有平滑滚动(`scrollingSmoothness`)未动。
- [ ] M9 主题参数联调(改 example 主题 motion 各项/极端时长/速度倍率)——**待用户游戏内**。
- [x] M10 回归:`./gradlew check` 全量(含 uiInputMailboxTest/uiMirrorDemandTest/
      filmEditorTest 等输入测试,见会话记录);拖拽三连与 bbs-web-remote 冒烟**待用户游戏内**。

## 红线自查

- 全部改动都在 render/getFactor 求值路径;唯二触碰非 render 方法的是
  `setPanelNow` 尾部记 veil 起点、`scrollTo` 记活动时间戳——均为"写时间戳供渲染消费",
  不改变任何逻辑分支、事件或生命周期。
- 命中判定零改动:overlay/menu 的 scale 是纯绘制变换,area 仍在最终位置;
  scrollbar 淡出后仍可按几何拖拽,悬停立即显形。
- 关闭态:hover/overlay/menu/veil/scrollbar 走 isSettled/duration≤0 短路 =
  逐位旧行为;notification 关闭 = 立即在位(prd 定义)。

## 回滚

每接入点独立可回滚(revert 对应文件);基建 + 任意子集可保留。设置键
motion_enabled/motion_speed 残留配置无害。
