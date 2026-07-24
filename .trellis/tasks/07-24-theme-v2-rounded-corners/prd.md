# 圆角渲染原语与主题半径参数

## Goal

为 Batcher2D 增加圆角渲染原语并接入核心控件,半径由主题参数控制。这是参考图"柔软质感"的根本原语(优先于 nine-slice:零美术成本、纯数值参数)。实现方案直接移植 BBS Refreshed 的已验证做法(父任务 design.md 第 3.1 节)。

## Requirements

- R1 Batcher2D 新增 `roundedBox`(填充)、`roundedFrame`(边框+填充)、`filledCircle` 原语:中心+边条纯色三角形,四角 mask 纹理三角扇(双线性采样抗锯齿),mask 缺失时退化纯几何扇;不写自定义 shader。
- R2 半径为主题参数 `style.corner_radius`,三级:`chrome`(弹层/浮窗)、`panel`(卡片/面板)、`widget`(按钮/输入框/开关);全部默认 0。
- R3 半径 0(含 r<0.5 钳制)时走原 `box()` 路径,渲染逐位等价 v1 —— 四个 v1 内置主题外观不变。
- R4 控件接入第一批:弹层面板背景、按钮、输入框、开关(UIToggle 重绘为圆角轨道+圆形滑块的 macOS 样式,仅当 widget 半径>0)、列表选中态、右键菜单。后续控件可增量接入。
- R5 mask 资源(`rounded_rect_mask` / `filled_circle_mask`)进内置 assets,允许主题用同名资源覆盖(assets 覆盖语义天然支持)。
- R6 与主题 alpha 颜色兼容(半透明圆角面板正确混合)。

## Out of Scope

- 圆角裁剪(scissor 仍矩形;内容溢出圆角的场合首批控件中不存在)。
- nine-slice 贴图(独立任务)。
- 软阴影(nine-slice 任务顺带评估)。

## Constraints

- 参考实现是 1.20.1 Fabric intermediary 映射,移植需转译为 NeoForge 1.21.1 官方映射(BufferBuilder/Tesselator/RenderSystem 对应 API)。
- 逐控件分批替换绘制调用,每批独立可回滚;热路径零分配(扇顶点数固定)。
- 新增演示:内置主题(建议 strawberry,与父任务演示主题合并)展示三级半径。

## Acceptance Criteria

- [ ] 半径 0 时四个 v1 内置主题渲染逐位等价(对拍验证思路写入 check)。
- [ ] 半径>0 时首批控件圆角正确、四角抗锯齿、半透明混合正确、极端尺寸(w/h < 2r)不破面。
- [ ] `testThemeCore` 扩充 corner_radius 解析用例(缺省 0/坏值回退/继承)。
- [ ] 全量 check 真实 exit code 0;用户游戏内冒烟确认。
