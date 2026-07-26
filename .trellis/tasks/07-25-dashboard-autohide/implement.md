# Implement: 仪表盘任务栏自动隐藏

前置阅读:prd.md + design.md;父任务 research §1;spec bbs-ui-theming;依赖 07-25-refreshed-theme-tokens 完成(需其 ThemeParser/UITheme 基础改动先合入)。

## Checklist(按序)

- [x] 1. BBSSettings:`dashboard_auto_hide_taskbar`(false)+ `dashboard_taskbar_hide_delay`(100,100-15000)
- [x] 2. UITheme/ThemeParser/UIMotions:`taskbar_hide` motion entry(照现有 entry 模式)
- [ ] 3. themeCoreTest:entry 解析/回退/settled 测试已通过;中间全量 check == 0 尚未满足
- [x] 4. UIDashboardPanels:hiddenAmount 状态机 + 活动面板/底栏整体布局位移 + 命中一致(design §2,逐条落实:不 setEnabled、不移除元素、设置关=零开销逐位一致)
- [x] 5. Keys:`TOGGLE_TASKBAR_AUTOHIDE`(S+LCTRL)+ dashboard 层注册 + 切换通知(lang key 新增,en_us + zh_cn)
- [ ] 6. 实测 keybind 优先级(design §4 两个场景),结论记 journal;不满足则按 design §4 备选方案改
- [x] 7. docs/theme-spec:taskbar_hide key 文档 + example-theme 全 key 显式 + jar 同步
- [ ] 8. 终验证:`cd new; ./gradlew check *> ../check.log; "REAL_EXIT=$LASTEXITCODE"` == 0;冒烟项写入父任务清单

## 2026-07-26 用户反馈修订

- 原实现只滑出 20px 底栏,用户确认“仪表盘边框”应包含上方活动面板。
- 修订后位移距离取当前根容器高度,活动面板与底栏共享同一 slide;活动面板保持固定高度并同步更新子控件命中区域。
- `compileClientJava`、`testThemeCore`、`testUiMotions`、`testUiInputMailbox` 与 `jar` 分别以真实 exit code 0 通过;组合执行测试曾受 NeoForge `debug-*.log.gz` 轮转诊断干扰,单独重跑通过。
- 当前成品:`new/build/libs/BBS_FSR_beta_v0.0.10.jar`,7,939,350 bytes,SHA-256 `C5EBA48297AA81E20897D9E88EA14B6C0793301E73D4429D6AFDEB3722A54750`。
- 完整滑出、底边唤回和窗口 resize 仍需游戏内冒烟。

## 回滚点

步骤 3 / 8 两个安全点。改动面:BBSSettings、ui/themes 三件、UIMotions、UIDashboardPanels、Keys、lang、docs、themeCoreTest。

## 禁区

不移植 Easings/EXP_INOUT(用 UITween);不改任务栏布局与图标排布;不动兄弟任务领地(UIToggle/UINotifications 渲染细节/UIIcon/Scroll)。切换通知调用 UINotifications 现有 API 属允许(不改其内部)。
