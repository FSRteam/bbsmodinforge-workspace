# FSR 右键区域提示插件

## Goal

为 `new/` 中的 FSR（BBS FS）NeoForge 影片模组制作一个独立插件。用户绑定并触发快捷键后，插件在当前 FSR 界面上高亮可通过右键触发功能的区域，帮助发现旧界面中隐藏的右键入口。

## Confirmed Facts

- FSR 主项目位于 `new/`，目标环境为 Minecraft 1.21.1、NeoForge 21.1.222、Java 21，当前模组版本为 2.3.1。
- 插件应建立为根目录下的新独立 Gradle/NeoForge 项目，并将 `../new/build/libs/bbs-2.3.1.jar` 作为本地必需依赖；现有 `bbs-web-remote/` 和 `bbs-structure-addon/` 可作为脚手架参考。
- FSR 的 `UIScreen` 可公开取得当前 `UIBaseMenu`，`UIBaseMenu` 可公开取得根元素，`UIElement` 可公开遍历子元素及读取元素 `Area`。
- 通用右键菜单由 `UIElement.context(...)` 注册，并在 `UIElement.mouseClickedContextMenu(...)` 中统一触发；注册状态目前保存在私有字段中，没有公开的只读查询 API。
- 另有少量界面在 `subMouseClicked` 等逻辑中直接处理右键，这些不一定属于“打开菜单”的右键入口。
- `new/` 当前存在未提交改动；插件实现不应修改或覆盖这些改动。

## Requirements

- 插件作为独立客户端辅助模组运行，并声明对 `bbs` 的必需依赖。
- 在 Minecraft 控制设置中注册一个可重绑定的快捷键。
- 快捷键采用按住显示模式：按键保持期间显示全部提示，松开后立即隐藏；不采用持久开关状态。
- 仅当当前屏幕是 FSR `UIScreen` 时显示提示；普通 Minecraft 界面不显示。
- 提示方式是在每个对应的可右键区域内部绘制边框或半透明高亮，使用户能直接看出该区域支持右键；不以独立列表或说明面板代替区域标注。
- 按住快捷键时同时显示当前页面中所有符合条件的右键菜单区域，而不是只提示鼠标当前悬停的区域。
- 提示层不能阻挡鼠标、键盘、滚轮或原有右键菜单。
- 高亮应尊重元素的可见、启用、裁剪和实际屏幕区域，避免标出屏幕外或被遮挡的元素。
- 对嵌套/重叠区域进行合并或降噪，避免提示层变成难以辨认的色块。
- 至少提供简体中文和英文的模组名、快捷键分类、快捷键名称与必要提示文本。
- 首版优先保持插件独立，不修改 FSR 主项目；如果插件侧无法稳定识别通用右键注册状态，再单独评估给 FSR 增加只读 Addon API。
- MVP 只识别右键后会弹出上下文菜单的区域；取消拖拽、插入关键帧等不弹菜单的特殊右键行为不纳入高亮范围。

## Acceptance Criteria

- [ ] 插件能在干净的 NeoForge 1.21.1 客户端中与 FSR 2.3.1 一起加载。
- [ ] 快捷键可在 Minecraft 控制设置中重绑定；按住时显示提示，松开后在下一次界面刷新时全部消失。
- [ ] 每个被识别的可右键区域都在其实际可交互矩形内显示清晰高亮框，并随布局和裁剪变化同步更新。
- [ ] 在至少影片编辑器、回放编辑器、关键帧编辑器、模型/伪装编辑器和数据列表中，已注册的通用右键菜单区域能够被高亮。
- [ ] 不含右键功能的普通元素不会被大面积误标。
- [ ] 高亮期间仍能正常使用原界面；关闭提示后界面状态与输入行为不受影响。
- [ ] 切换 FSR 页面、打开/关闭浮层、调整 GUI 缩放和窗口尺寸后，高亮区域会正确刷新。
- [ ] 对空菜单、动态不可用菜单、重叠父子区域和滚动裁剪区域有验证用例或明确的手工测试清单。
- [ ] `gradlew.bat build` 成功，并完成面向 FSR 代表性界面的手工验收。

## Out of Scope for MVP

- 自动执行右键操作或替用户打开菜单。
- 修改旧界面的菜单内容、交互逻辑或视觉样式。
- 在世界场景中标注方块/实体的原版右键交互。
- 扫描所有任意第三方 GUI；首版只针对 FSR UI 框架。

## Open Questions

- None for MVP planning.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
