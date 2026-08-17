# webremote UI 全量修复：样式治理 + HUD 插槽 + i18n + 浅色主题

父任务：`.trellis/tasks/08-12-wr-frontend-overhaul/`（共同约束见父 prd「边界与约束」，全部适用）。

## Goal

在不改变 wire 协议与交互逻辑的前提下，修复 `bbs-web-remote` 浏览器端 UI 的结构性欠账：样式令牌化与去重、浮层布局系统化、小屏功能可达、文案可本地化、支持浅色主题。

## 需求

### R1 样式治理（视觉外观不变）

- 颜色/透明度/层级全部经 CSS 自定义属性令牌；玻璃卡片模式抽象为复用类；CSS 不再使用 id 选择器（id 保留给 JS）；与全局 `[hidden]` 重复的逐元素规则清理。
- 视觉外观像素级不变：令牌按现值 1:1 建立；近似透明度（0.84/0.86/0.88/0.9）归并为可选子步，归并须经截图确认后才允许。

### R2 浮层布局系统

- 除画布工具栏（可拖动，保持自由浮层）与画布状态层（transport-overlay / empty-state / touch-hint / canvas-hint / portrait-notice）外，其余浮层进入 HUD 插槽层（四角 + 底部中央 + 底栏），消灭 `top:62px`、`bottom:72px` 类手工避让坐标。
- 断点行为从逐面板坐标覆盖改为插槽级重排；全部断点下浮层不相互遮挡。

### R3 小屏可达性

- ≤560px 用户仍能请求/释放控制与申请角色升级：mobile-controls 提供控制入口，语义与桌面 `controlButton` 一致。
- 键盘入口沿用既有 ⌨（mobileKeyboardButton）；<400px 缩放按钮可继续隐藏（双指捏合有手势替代）。

### R4 i18n 文案抽离

- 新增 i18n 模块：消息目录 + `t(key, params)`，缺 key 回退现文案；zh-CN 目录逐字等于现有文案（保证既有测试不改断言仍绿）。
- JS 动态文案与静态 HTML 文案（含 title/aria-label/placeholder）均经目录解析；HTML 走 `data-i18n` 属性 + 启动一次性水合。
- 本任务不要求第二语言内容，但目录结构必须支持后续新增 locale。

### R5 浅色主题

- 提供浅色主题令牌组与顶栏切换按钮；选择持久化到 localStorage（新键 `bbs-web-remote.theme`）；默认深色（现状不变，CSP 禁内联下无闪烁）。
- 浅色下状态 tone 色/文本对比度满足可读性（关键组合按 WCAG AA 近似抽查）。
- `meta[name=theme-color]` 与 `color-scheme` 随主题同步；forced-colors 行为不变。

## 验收标准

- [ ] `verifyWeb`（含新增 i18n 测试与全链 require）+ `testServerSmoke`（含新文件资源/缓存断言）全绿。
- [ ] 既有 10 个 webTest 不修改断言前提下全绿（i18n 化后 zh-CN 文案逐字不变）。
- [ ] >900 / 560–900 / <560 / <400 四档检查：A1 完成时与基线视觉一致（等值替换 + 截图抽查）；A2/A3 后浮层无遮挡、控制入口小屏可达。
- [ ] 浅色/深色切换即时生效、刷新后保持；theme-color 与 color-scheme 同步。
- [ ] 真实 Jetty URL 浏览器冒烟无 console 启动错误。

## Out of Scope

- 交互逻辑/协议行为变更；`FloatingToolbar`/`RemoteInput` 逻辑改动；error.js 错误目录并入 i18n（留给后续任务）；第二语言翻译内容；状态通道统一。
