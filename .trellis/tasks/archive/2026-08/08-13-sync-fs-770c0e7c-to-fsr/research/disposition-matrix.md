# 处置矩阵:FS `4220b453..770c0e7c` → FSR

区间 16 个提交,每项必须落为 `ported` / `target-equivalent` / `rejected` 并附证据。
逐提交行为分析见 `fs-upstream-diff-2026-08-13.md`,分组设计见 `../design.md`。

- `fsr_initial_commit = 637d6f76c5cdb08915c37682ef33ed0aa4c23e51`
- `fsr_terminal_commit = 19d4d0302ad8fd79a66baf231604ddd632c3a212`
- `fsr_target_branch = codex/sync-fs-770c0e7c-to-fsr`
- worktree = `.worktrees/new-fs-770c0e7c`

| 组 | 上游提交 | 主题 | 处置 | FSR 提交 | 证据 / 备注 |
|---|---|---|---|---|---|
| E1 | `baafc5a4` | 全半透明纹理模型自透 | ported | `89dc7d6c` | `ModelInstance` 的分层实体几何恢复深度写；`FormTranslucentQueue` 对平面 form 保持显式不写深度；来源级断言在 `RenderRuntimeMigrationSourceTest`。 |
| E2 | `770c0e7c` | 物品 form 渲染乱面 | ported | `89dc7d6c` | `RenderLayerCommand` 恒写深度，移除按层名分支；`CustomVertexConsumerProvider` 保留 FSR 延迟队列所有权。 |
| F | `861257a7` | VIEW 空间 gizmo 正对相机 | ported | `fe01d69f` | `Gizmo.applyViewShear` 同时覆盖绘制/stencil，`GizmoDrag` 的 VIEW 拖拽基保持未 shear；回归断言在 `PoseFormRegressionSourceTest`。 |
| A4 | `95a110e2` | 编辑器 resize 同步参数面板 | target-equivalent | `637d6f76` | 基点已有 `UIKeyframeEditor.replaceEditor` 的 target→editor resize 顺序；本批仅补来源级断言，未重复改动。 |
| C4 | `d0f3754d` | 选择/编辑按钮间距 | ported | `8e490d02` | `UINestedEdit` 改用 `UIConstants.MARGIN`，由 `RenderRuntimeMigrationSourceTest` 固定。 |
| D1 | `fe552a58` | 滑条绝对网格步进 | ported | `874d67e5` | `UISliderTrackpad` 增加绝对 snap、Shift/Alt 细分和浮点清理；数值设置提供 `slider(step)`。 |
| D2 | `0773f9df` | FOV 回 trackpad | ported | `874d67e5` | FOV 移除自动 slider，UI 缩放保留显式 `0.25` 步进，代码位于 `BBSSettings`。 |
| C1 | `459e9db7` | 背景色调阶梯(OKLab) | ported | `8e490d02` | dark/light 主题 surface token 更新为中性色阶，`docs/theme-spec` 同步；未绕过主题解析层。 |
| C2 | `52c5db8f` | 阴影/高光拆开关 | ported | `8e490d02` | 新增高光、浮层透明度、渐变边框设置；`bevelBox` 全库清扫并统一为 `surfaceBox`。 |
| C3 | `bb1ac00f` | UIToggle 轻量化 | ported | `8e490d02` | UIToggle 使用 deep surface/主题色轨道和中性把手，保留浅色主题 token。 |
| B1 | `8a293161` | 热键栏九轨道 + inventory 移除 | ported | `19d4d030` | 删除 `Film.inventory`/旧 `Inventory`，新增九个 `item_slot_*`、selectedSlot 派生主手、`FilmLegacy` 和 NeoForge 网络载荷。 |
| B2 | `c02aa6e6` | 旧电影捡起物品消失 | ported | `19d4d030` | 空轨道不覆盖运行时拾取物品，`migrateWorn` 迁移旧盔甲/副手；`HotbarFilmMigrationTest` 覆盖回归。 |
| B3 | `8c48a185` | 物品轨道分家族配色 | ported | `19d4d030` | `UIReplaysEditor.setupItemColors` 为九槽/副手/盔甲分组配色，并接入 HOTBAR/装备图标。 |
| H | `378bac69` | IK/物理专属图标 | ported | `4a81674c` + `19d4d030` | 图集和 `Icons.IK/PHYSICS/HOTBAR` 在 `4a81674c`，表单/轨道使用点随 H/B3 提交完成；坐标为 `(224,160)/(240,160)/(0,176)`。 |
| G | `7f99e757` | 小数缩放 pixel art 平滑 | rejected | — | FSR 当前 `src/client/java/mchorse/bbs_mod/mixin/client/GameRendererMixin.java` 只有相机/FOV/世界渲染注入，没有上游 `getRenderTypeTextProgram` 等文本 program 目标；`client/BBSShaders.java` 仅注册 model/multilink/subtitles/selection/picker，`assets/bbs/shaders/core/` 无 pixelart 资源；`Batcher2D` 仍直接使用 vanilla `GameRenderer::getPositionTexColorShader`。上游 1.20.4 的 18 文件 shader + mixin 集合不能在 NeoForge 1.21.1 直接复用，需另立项重做 JSON/GLSL/Mojmap 注入并验证回退，超出本次低风险迁移范围。 |
| I | `5b8d714f` | UITrackpad ABI 协变覆写 | rejected | — | FSR `src/client/java/mchorse/bbs_mod/ui/framework/elements/input/UITrackpad.java:29` 使用 `UINumericInput<UITrackpad>` 自类型链，但没有承诺 Fabric addon 的 `limit(DD)LUITrackpad;` 等旧二进制描述；外部集成契约只冻结注册/渲染器/面板/图标等入口（`external-addon-integration-contract.md:66-82`），UITrackpad 不在公开 API。上游 139 行协变 shim 是 Fabric 1.20.x 生态修复，当前 FSR 不应为未冻结的内部控件引入 ABI 层；未来若冻结该类再补覆写与二进制回归。 |
