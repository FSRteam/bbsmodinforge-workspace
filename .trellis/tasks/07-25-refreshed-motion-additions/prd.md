# Refreshed 动效补全(通知/toggle/文本/拖动)

父任务:`.trellis/tasks/07-25-fsr-refreshed-skin/`(design.md D3/D5,research §4)。依赖子任务 07-25-refreshed-theme-tokens。

## Goal

补齐 v2 未覆盖的四项动效,全部走 UITween 弹簧 + 主题 motion entry,render-only,不改布局与操作。

## Requirements

- R1 通知滑入:notification motion entry **我们已有**(v1 六接入点之一)——本项只做:滑入轨迹对齐 Refreshed(从右侧滑入,research §4)可经 tracks 配置表达、UINotifications 颜色改走 token 子任务的 notification_*;不新建 entry。
- R2 `toggle`:UIToggle 滑块位置+颜色插值(Refreshed easeOutExpo → 弹簧);off/settled 逐位旧渲染。
- R3 `text_entry`:文本输入弹层打开缩放 0.85→1.0(UIOverlayPanel);**先确认 v2 overlay entry 是否已覆盖**,已覆盖则只补 start_scale 参数 key,不新建 entry。
- R4 `drag_follow`:UIDraggable 拖动平滑跟随(自研,Refreshed 无);渲染层跟随、命中用真实位置;默认关(=旧即时行为)。
- R5 四旧主题(dark/light/example/amber)默认全关;strawberry/refreshed 打开;UIMotionsTest 扩展覆盖新 entry 的 settled 短路。
- R6 `text_entry_open_anim` 与 `film_editor_layout_transition_enabled` 两个用户开关显示在 Refreshed 设置分类并分别门控 overlay/layout;Refreshed 主题仍负责 spring 曲线类型与参数。
- R7 皮肤分类的 `motion_enabled` 与 `motion_speed` 必须提供中英文名称和说明,设置界面不得显示原始本地化 key。
- R8 主题选择弹层的“导出模板主题”必须导出**当前选中主题**,而不是固定导出 `example`。内置 Refreshed 导出时必须同时复制它引用的图标、背景和装饰资源,并把同主题目录链接改写到导出目录,所得模板可直接重载使用。

## Acceptance Criteria

- [ ] 四项 entry 解析/回退/settled 短路测试绿;旧主题下逐位无差异
- [ ] refreshed 主题下四项动效可见且可调参
- [ ] 点击 UIToggle 时滑块位置和颜色沿 `toggle` spring 曲线运动;窗口/编辑器切换开关关闭时立即回落无动画路径
- [ ] 皮肤分类的动效总开关与速度均显示本地化文本
- [ ] 选中 Refreshed 后导出得到 Refreshed 的 `theme.json` 与 `icons.png`;选中其他主题时导出内容随选择变化
- [ ] `gradlew check` 真实 exit code == 0
