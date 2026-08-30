# 设计：webremote UI 全量修复

## 1. 分阶段结构

A0 使能 → A1 令牌/治理 → A2 HUD 插槽 → A3 i18n + 主题。每阶段独立可验证、独立提交（回滚 = revert 对应提交）。

## 2. A0 使能项（先行小批）

- `bbs-web-remote/build.gradle` 的 8 个 `verifyWeb*Syntax` 任务（:370-419，硬编码 `webRuntimeScripts[0..7]` 下标）改为遍历 `webRuntimeScripts` 动态注册；`verifyWeb` 聚合依赖同步循环化。任务语义等价，为 A3 插入 i18n.js 扫清下标位移问题。
- `media.js:52` `boundedText` 正则字符类中的字面 NUL 字节改 `\x00` 转义（行为等价），恢复 grep/IDE 对该文件的可见性。

## 3. A1 令牌与治理（styles.css + index.html class，零 JS）

- `:root` 增补令牌：
  - 玻璃背景族：按实际出现值 1:1 定档（0.84 / 0.86 / 0.88 / 0.9 / 0.96 / 0.97 → `--glass-bg-*` 命名档），先不归并。
  - accent/focus/warning/danger 透明度族：把 `rgba(99,214,160,x)`、`rgba(126,200,255,x)`、`rgba(247,198,92,x)`、`rgba(255,120,120,x)` 等字面量离散为 `--accent-a*`/`--focus-a*` 等显式 rgba 令牌（不用 color-mix，零兼容风险）。
  - `--z-*` 梯度表：canvas-overlay 4 / hud 14–24 / connection 30 / ime 100 / toast-history 119 / toast 120。
- `.glass-panel` 复合类（border + radius + 半透明背景 + backdrop-blur + 阴影），挂到 connection-card、canvas-toolbar、participants-panel、diagnostics、touch-hint、custom-quality-panel，逐处消除重复声明；面板间差异保留在各自类。
- CSS id 选择器迁移为类（`#openBbsButton`→`.open-bbs-button` 等；HTML 加 class，id 不动，JS 零改动）。smoke marker `.app-shell` / `.quality-control` 类名不得变。
- `data-tone`/`data-state`/`data-level` 三套状态属性本任务不改名（牵动 app.js 与测试），仅在 spec 登记。

## 4. A2 HUD 插槽层

- `.remote-workspace` 下新建 `.hud-layer`：`position:absolute; inset:0; pointer-events:none`（面板自身 `pointer-events:auto`），grid 划分四角区 + 底部中央区，四角槽为 flex column + `--hud-gap` 堆叠。
- 槽位分配：左上=connection-card；右上=participants-panel（`padding-top: var(--hud-toolbar-clearance)` 预留工具栏默认停靠位）；右下=diagnostics；底部中央=toast / toast-history（评估后可保持 fixed，但坐标令牌化）；底栏=mobile-controls。
- **工具栏不入槽**：`FloatingToolbar` 启动即把 CSS 定位转为内联 `left/top`（ui.js:60-67 `clampCurrent`），拖动依赖 canvas-stage 容器矩形。工具栏保持 canvas-stage 绝对定位子元素，默认 top/right 用令牌表达，JS 与 floating-toolbar.test.js 零改动。
- 画布状态层不入槽（属于画布内容反馈，随画布铺满）。
- 小屏可达性：`mobile-controls` 增 `<button id="mobileControlButton">`；`App.updateControlUi()`（app.js:2121，集中写 controlButton 文案/禁用态/ title）同步更新该镜像节点；`bindUi` 为其绑定与 `controlButton` 相同的 `toggleControl` 处理。≤560px 时工具栏内 controlButton/keyboardButton 维持隐藏（入口转移至底栏）。
- 实施前 grep 确认既有 webTest 未断言被移动节点的父子结构（测试用 FakeElement 按 id 取节点，DOM 位置无关，预计零影响）。

## 5. A3 i18n + 浅色主题

### i18n.js 新模块

- 加载序：`protocol → i18n → error → style → media → input → control → ui → app`（i18n 仅校验 ns 存在，无依赖）。
- `ns.I18n = Object.freeze({ t, hydrate, setLocale, getLocale, addCatalog, ... })`；zh-CN 目录内建；`t(key, params, fallback)` 缺 key 回退 fallback/key；`hydrate(root)` 遍历 `[data-i18n]`/`[data-i18n-attr]` 一次性填充（纯遍历，可 FakeElement 测试）。
- 发布 9 处同步：index.html script 标签、webRuntimeScripts（A0 已循环化语法任务）、FIXED_RESOURCE_PATHS、smoke 逐资源断言（marker `ns.I18n`）、smoke 缓存断言列表、10 个既有 webTest require 链、app.js IIFE 顶部解构 + WebTest 导出（新增 `t`/`hydrate` 等，只增不删）、webTestTasks 注册新 `i18n.test.js`。
- 文案抽离两批：
  - 批 1（JS 动态）：app.js（note/toast、状态与按钮文案、诊断格式化词）、media.js（HybridMediaTransport 阶段文案「协商中」等）、ui.js（工具栏 title）、control.js（controlStateLabel 主线词）。zh-CN 逐字等于现文案 ⇒ control-state-label.test.js 等既有断言不改仍绿。
  - 批 2（静态 HTML）：可见文本 + title/aria-label/placeholder 加 `data-i18n` / `data-i18n-attr`，app.js 启动调 `I18n.hydrate(document)`。zh-CN 下水合结果与现 HTML 等价。

### 浅色主题

- 基于 A1 令牌建 `html[data-theme="light"]` 覆盖组：`--bg/--panel/--panel-2/--line/--text/--muted/玻璃背景族/画布棋盘底纹（变量化后覆盖）`；accent/warning/danger 色相不变、必要时调明度过对比度检查。
- 顶栏新增 `#themeToggle` icon 按钮（深色默认 ↔ 浅色）；localStorage `bbs-web-remote.theme` ∈ {dark, light}，默认 dark。
- app.js `applyTheme()` 于构造早期执行：设 `documentElement.dataset.theme`、同步 `meta[name=theme-color]` 与 `color-scheme`。CSP 禁内联 ⇒ 主题在 app.js 加载后应用；默认深色与现状一致，无闪烁。
- `forced-colors` 媒体查询行为不变。

## 6. 兼容与数据

- 新 localStorage 键仅 `bbs-web-remote.theme`；既有 5 键全部保留。
- 服务端仅 `ClasspathStaticHandler.FIXED_RESOURCE_PATHS` 与 `ServerSmokeTestMain` 断言扩条目，无行为变更。
- wire/信令/输入语义零接触。

## 7. 明确不做

- 不改 `FloatingToolbar`/`RemoteInput` 逻辑；不删改既有 WebTest 导出；不做第二语言内容；不动 error.js 目录结构（后续任务并轨）。
