# Implement: 仪表盘任务栏自动隐藏

前置阅读:prd.md + design.md;父任务 research §1;spec bbs-ui-theming;依赖 07-25-refreshed-theme-tokens 完成(需其 ThemeParser/UITheme 基础改动先合入)。

## Checklist(按序)

- [x] 1. BBSSettings:`dashboard_auto_hide_taskbar`(false)+ `dashboard_taskbar_hide_delay`(100,100-15000)
- [x] 2. UITheme/ThemeParser/UIMotions:`taskbar_hide` motion entry(照现有 entry 模式)
- [ ] 3. themeCoreTest:entry 解析/回退/settled 测试已通过;中间全量 check == 0 尚未满足
- [x] 4. UIDashboardPanels:hiddenAmount 状态机 + 底栏向下/数据面板顶部栏向上布局位移 + 编辑主体锚定与命中一致(design §2,逐条落实:不 setEnabled、不移除元素、设置关=零开销逐位一致)
- [x] 5. Keys:`TOGGLE_TASKBAR_AUTOHIDE`(S+LCTRL)+ dashboard 层注册 + 切换通知(lang key 新增,en_us + zh_cn)
- [ ] 6. 实测 keybind 优先级(design §4 两个场景),结论记 journal;不满足则按 design §4 备选方案改
- [x] 7. docs/theme-spec:taskbar_hide key 文档 + example-theme 全 key 显式 + jar 同步
- [ ] 8. 终验证:`cd new; ./gradlew check *> ../check.log; "REAL_EXIT=$LASTEXITCODE"` == 0;冒烟项写入父任务清单

## 2026-07-26 用户反馈修订

- 原实现只滑出 20px 底栏;第一次反馈要求同时隐藏“上方的任务栏”时,曾错误地把整个活动面板根容器向下移动,导致编辑器主体整体折叠到屏幕下方。
- 最新纠正:活动面板根容器始终锚定 `y=0`;底栏仅向下移动自身 20px,`UIDataDashboardPanel.tabBar`(含 Film 顶部编辑器切换按钮)仅向上移动自身高度,编辑器主体通过减少上下 inset 扩展,不得整体位移。
- 当前面板顶部栏和上下 reveal strip 都参与 hover 保持/唤回;无顶部数据页栏的面板只隐藏底栏。
- 最新纠正后 `compileClientJava`、`testUiInputMailbox`、`testThemeCore`、`testUiMotions` 与 `jar` 分别以真实 exit code 0 通过;组合执行测试仍会因另一个进程占用 NeoForge `logs/latest.log` / `debug.log` 被 ERROR diagnostic 门禁终止,单独重跑 `testUiMotions` 通过。
- 当前成品:`new/build/libs/BBS_FSR_beta_v0.0.10.jar`,7,964,046 bytes,SHA-256 `8BF3CBA40D447182D6502C411B5F0135995439672FCAF98CD1147654E865FC83`;JAR 内三个 dashboard panel class 均已核对包含顶部 chrome 协调钩子。
- 完整滑出、底边唤回和窗口 resize 仍需游戏内冒烟。

## 回滚点

步骤 3 / 8 两个安全点。改动面:BBSSettings、ui/themes 三件、UIMotions、UIDashboardPanels、Keys、lang、docs、themeCoreTest。

## 禁区

不移植 Easings/EXP_INOUT(用 UITween);不改任务栏布局与图标排布;不动兄弟任务领地(UIToggle/UINotifications 渲染细节/UIIcon/Scroll)。切换通知调用 UINotifications 现有 API 属允许(不改其内部)。
