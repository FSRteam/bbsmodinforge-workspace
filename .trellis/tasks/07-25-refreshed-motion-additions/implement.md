# Implement: Refreshed 动效补全(通知/toggle/文本/拖动)

前置阅读:prd.md + design.md;spec bbs-ui-theming;父任务 research §3/§4;依赖 07-25-refreshed-theme-tokens 先合入。

## Checklist(按序)

- [ ] 1. 决策门 A(design §1):核对 UINotifications 是否已接 tracks;记 journal。未接则补接(照 UIOverlay 模式)
- [ ] 2. UINotifications 颜色改走 notification_fill/notification_text 语义函数;dark 对拍确认逐位
- [ ] 3. 决策门 B(design §3):验证 UIPromptOverlayPanel 是否走 UIOverlay 动画路径;新增 text_entry_open_anim 门控,不新建 entry
- [ ] 4. ThemeParser/UITheme/UIMotions:`toggle` + `drag_follow` 两个 entry(默认 disabled)
- [ ] 5. UIToggle 滑块动画(design §2):UITween + 位置/颜色插值;off/settled 逐位断言先写(TDD)
- [ ] 5a. film_editor_layout_transition_enabled 门控 UIFilmPanel 现有 layout 动画;关闭时即时切换
- [ ] 6. 中间验证 check == 0
- [ ] 7. UIDraggable drag_follow(design §4):先读懂位置写回机制并记 journal,再实现渲染层跟随;默认关
- [ ] 8. UIMotionsTest 扩展(两 entry + UIToggle 端点)
- [ ] 9. refreshed/strawberry 主题配置新动效参数(refreshed:toggle 开;drag_follow 关;通知 tracks 对齐 Refreshed 滑入)
- [ ] 10. 用户验收补充:skins motion_enabled/motion_speed 中英文翻译;选中主题导出 JSON + 同主题引用资源并安全改写链接;补导出回归测试
- [ ] 11. 核对现有 UIToggle spring、UITrackpad scrub、Scroll shadow 在源码与构建 JAR 中均存在,补缺失的可执行断言
- [ ] 12. docs/theme-spec 同步(README+example-theme+jar);终验证 `cd new; ./gradlew check *> ../check.log; "REAL_EXIT=$LASTEXITCODE"` == 0

## 回滚点

步骤 6 / 10 两个安全点;每个 entry 独立可 revert(未配置=旧行为)。

## 禁区

不新建 notification/text_entry entry;不搬 Refreshed 的 text_entry_open_anim* 设置;不改 UIDraggable 手势语义(命中/落点/所有权);不动 UIDashboardPanels/UIIcon/Scroll/UIClips(兄弟任务领地)。
