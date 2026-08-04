# 修复仪表盘菜单状态、伪装渲染与模型编辑回归

## Goal

恢复 BBSFSR 在伪装、影片和模型方块编辑流程中的菜单生命周期、预览渲染、交互快捷键和动画帧率一致性，且不改变现有表单数据格式。

## Confirmed Facts

- `UIDashboard.onClose()` 关闭面板但保留当前 panel；再次打开同一 panel 时 `UIDashboardPanels.setPanel()` 因对象相同直接返回，因此不会重新执行 `appear()`。
- 伪装和模型方块面板的 `appear()`/`disappear()` 负责相机控制器、第三人称视角和快捷键 owner；缺少重新激活会同时造成视角不跟随、WASD/滚轮失效、右键聚焦失效。
- 模型方块伪装 palette 是 panel 的动态子节点；被选中的方块从世界中移除后，`fill(null, ...)` 没有移除该 palette，旧实体引用可继续进入编辑回调。
- `FormRenderer.render()` 当前会在所有非 UI/non-picking 调用中启动 `FormTranslucentQueue`；编辑器 `UIFormRenderer` 使用 `PREVIEW + modelRenderer()`，但未设置 `context.ui`，所以半透明 MobForm 会被错误延迟到世界透明阶段。
- `MobFormRenderer` 的半透明实体层覆盖悦灵、恼鬼、玩家、猪灵、旋风人等共同受影响的实体族；该路径还负责预览中 vanilla entity 的阴影/视觉状态。
- `UIPropTransform.enableMode(1)` 把 S 永远绑定到三轴统一缩放，未使用已存在的 `scaleHotkeyOrder`；R 的 view/sphere 额外模式也依赖真实 gizmo ray 上下文。
- 模型编辑预览会执行动作求值和可拾取 stencil 渲染；动作模型在 normal + stencil 路径重复执行昂贵的姿态/蒙皮工作，导致明显掉帧。

## Requirements

- 关闭再打开仪表盘时，当前 panel 必须重新激活其 `appear()` 生命周期；切换到其他 panel 的行为保持不变。
- 伪装菜单重新进入后，世界相机、WASD 和滚轮控制、右键聚焦必须立即恢复；模型方块菜单同样恢复。
- 当前模型方块实体被移除或不再可编辑时，自动关闭其伪装 palette、移除 camera controller、清理动态子节点和旧引用。
- 半透明队列只能在真实世界渲染阶段启用；UI/影片/伪装编辑器预览必须立即绘制并完整渲染 MobForm，不得污染后续世界帧。
- MobForm 的阴影、深度、混合、视觉效果和 BBSFS 参考行为一致；Iris 阴影 pass 仍遵守 `shaderShadow` 配置。
- S 在当前选中肢体上循环 `X -> Y -> Z -> X` 轴向缩放；统一缩放只由 gizmo 中心句柄/显式统一操作触发。
- R 保留并可访问 view、sphere/arcball/trackball 以及 X/Y/Z 旋转模式；首次按键切换 gizmo 显示模式后下一次必须进入对应操作。
- 模型编辑器动作预览不得每帧重复无效地重建动作配置、拓扑或蒙皮 scratch；保持动画正确性和可拾取结果。
- 不修改既有 JSON/NBT/schema、公开 API、网络 ID 或用户未提交的无关改动。

## Acceptance Criteria

- [ ] 在伪装菜单保持选中并 ESC 退出后，通过 B 或 0 直接重入，旋转/平移相机时世界视角持续更新；WASD、滚轮和右键聚焦可用。
- [ ] 在模型方块菜单保持选中并退出后，通过右键或 0 直接重入，移动、速度滚轮和聚焦均可用。
- [ ] 破坏正在编辑的模型方块后，重新打开模型方块菜单不再残留伪装子菜单；尝试旧入口不会崩溃。
- [ ] 伪装菜单和影片伪装选择中，悦灵、恼鬼、玩家、猪灵、旋风人等 MobForm 的半透明部件完整渲染，视角移动和下一帧不丢失。
- [ ] 伪装编辑器的阴影/混合/深度效果与 BBSFS 参考一致，Iris shadow pass 不渲染关闭 `shaderShadow` 的表单。
- [ ] 连续按 S 在选中肢体上依次激活 X、Y、Z 缩放；中心 gizmo 仍可执行统一缩放。
- [ ] 连续按 R 可到达 view、sphere/arcball/trackball、X、Y、Z 模式；无 ray 上下文时跳过不可用模式但不破坏轴循环。
- [ ] 含动作的模型编辑预览在 normal/stencil 渲染下不出现明显帧率骤降；动画姿态与点击骨骼结果正确。
- [ ] `compileJava compileClientJava`、相关定向测试、`check`、`build` 和 `git diff --check` 通过。

## Out of Scope

- 重新设计伪装、影片或模型方块编辑器 UI。
- 改变动作、IK、物理、约束或半透明材质的数据格式。

## Open Questions

- 无阻塞问题。目标仓库为 `new/`；BBSFS 行为以现有迁移代码和 `temp/bbs-fs-2.3` 参考为准。
