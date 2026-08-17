# WebRemote 手机手势修正与版面重排（简约）

## 背景

08-12-wr-frontend-overhaul 五个子任务完成后，用户实测提出四点反馈：

1. **trackpad 误点击 bug**：虚拟鼠标模式下"只要移动就一直处于点击状态"。
   根因：`trackpadMove` 的长按拖拽判定使用**累计位移** `movedPx`（永不重置），
   指针移动累积 >24px 后，任何 ≥350ms 停顿再移动即触发 PRESS。
2. 版面乱、遮挡严重、玻璃拟态风格不喜欢。
3. 手机连接后右缘一列按钮（5 个 hub 按钮 + 飞出面板 + 摇杆 + 动作键）太挤。
4. 要求"前端设计简约，不要过度设计"；手势按主流远控 App 规范重做（Exa 调研）。

## 调研结论（Exa：CRD / MS RDP / RealVNC / Jump Desktop / VMware Horizon 共识）

trackpad 模式标准手势：
- 单指滑动 = 只移动光标（不点击）
- 单指轻点 = 左键单击；双击 = 双击
- 双指轻点 = 右键；三指轻点 = 中键（VNC/CRD）
- 按住不动（出现视觉提示）后拖动 = 按住拖拽
- 双指滑动 = 滚轮；双指捏合 = 缩放

## 方案

### input.js 触控板手势重写

- 触摸记录新增 `startX/startY/downAt`（`trackTouch()`），位移一律按"相对落点"计算。
- 常量：`TRACKPAD_TAP_MAX_MS=260`、`TRACKPAD_TAP_MAX_TRAVEL_PX=12`、
  `TRACKPAD_HOLD_MS=320`、`MULTI_TAP_MAX_MS=280`、`MULTI_TAP_TRAVEL_PX=18`、
  `MULTI_GESTURE_ACT_PX=8`；删除旧的累计位移判定。
- `trackpadHoldTimer`（setTimeout 320ms）+ `trackpadHoldExpire()`：落点静止按下 ≥320ms
  才 PRESS，之后拖动即按住拖拽；移动超 12px 立即取消计时（移动永不按压）。
- `maybeTrackpadMultiTap(maxTravel)`：双指轻点=右键（button 1）、三指=中键（button 2）；
  手势已 zoom/pan 或滚动累计 ≥8px（`gestureActed`/`gestureMagnitude`）或超时则不判点。
  最后一指抬起允许在 `GESTURE_DRAIN` 态判定（2→1 抬指顺序坑）。
- `#virtualCursor` `data-pressed` 按压视觉反馈；`setVirtualCursorPressed()`。
- setPointerMode / releaseAll / trackpadCancel 全量清理 hold timer 与手势计数字段。

### 版面重排（简约路线）

- 手机端：右缘单个 `.menu-fab`（☰，#mobileMoreButton，垂直居中）+ 唯一
  `.mobile-menu`（#moreMenu）扁平列表——请求控制（强调色首项）/ 键盘 / 虚拟鼠标 /
  游戏模式 / L·R·M + 修饰键 / 分隔线 / 打开 BBS / 交出操作权 / 参与者 / 连接详情 /
  主题。原 mousePanel、moreMenu 飞出面板、5 按钮 hub 全部合并删除。
- 参与者面板默认 hidden，仅从菜单 `revealParticipantsPanel()` 打开；
  `updateLeaseUi` 不再自动取消 hidden。
- styles.css 全量重写为扁平皮肤：实色面板、单一 `--shadow`、无 backdrop-filter/
  渐变/玻璃分档；token 精简为 `--bg/--panel/--panel-2/--line*/--text/--muted/
  --accent*/--ok/--warn/--danger`；浅色主题同构覆盖。
- i18n：更新 touchBody/trackpadTitle/trackpadNote/demo.hintDefault 手势文案，
  moreTitle→"菜单"，删除死键（brandTagline/eyebrow/mobileHubAria/mobileMouseTitle/
  mousePanelAria/control.short*）。

## 验收

- [x] 移动指针（含长时间停顿后移动）不产生任何点击（回归测试）
- [x] 轻点=单击、双击=双击
- [x] 双指轻点=右键、三指轻点=中键
- [x] 按住静止 ~0.3s 后拖动=按住拖拽，抬手释放
- [x] 双指滑动=滚轮且滚动后抬指不误判点击
- [x] 手机端只有 1 个悬浮按钮 + 1 个菜单；参与者/诊断默认隐藏
- [x] verifyWeb 13/13（含 mobile-input 新增 6 组 trackpad 断言）
- [x] testServerSmoke 通过（静态资源 + 缓存 + CSP 断言）
- [x] HTML id（129 个）/i18n 键（299 个）与 JS 引用一致性校验通过

## 未尽事项

- 浏览器 IAB webview 在本环境无法挂载，移动端视口截图验证未执行；
  已用静态一致性校验兜底，建议真机验收手势手感（灵敏度 1.7、hold 320ms、
  双击 300ms 均为可调常量）。
