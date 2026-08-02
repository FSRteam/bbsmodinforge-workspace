# Implement: 附魔 UI 文本精简与 alpha 光效修复

## Step 1 — 文案精简（zh_cn.json + en_us.json）

| key | 当前(zh_cn) | 改为 |
|-----|------------|------|
| `bbs.ui.pose.context.glint-transform` | 附魔层变换 | 附魔 |
| `bbs.ui.pose.context.glint-speed` | 附魔速度 | 速度 |
| `bbs.ui.pose.context.glint-off` | 无附魔 | 无 |
| `bbs.ui.pose.context.glint-full` | 整体附魔 | 整体 |
| `bbs.ui.pose.context.glint-edge` | 边缘附魔 | 边缘 |
| `bbs.ui.pose.context.glint-vanilla` | 原版附魔 | 原版 |

en_us 对应：
- `glint-transform`: Glint layer transform → Glint
- `glint-speed`: Glint speed → Speed
- `glint-off/full/edge/vanilla`: No glint → No; Full glint → Full; Edge glint → Edge; Vanilla glint → Vanilla

tooltip 不改。

## Step 2 — alpha 渲染链修复

- `GlintRenderState.renderVao`：非 armor 分支 pre-multiply rgb，顶点 alpha 用 1F。
- `CubicGlintCubeRenderer.renderGroup`：`setColor` pre-multiply rgb，`a=1F`。
- `GlintTintVertexConsumer`：构造 pre-multiply rgb，alpha 字节 0/255。

## Step 3 — 验证

- 编译：`new/` 里 gradlew compile 相关 source set。
- 回归：`uiInputMailboxTest`（PoseFormRegressionSourceTest 等对 glint key 与源码字符串的断言）。
- 冒烟：编辑器里调 alpha 观察 整体/边缘 光效（需用户手动）。
- 检查现有未提交 diff 不冲突（UIGeneralFormPanel 已有他人改动，只触碰 glintColor 附近的既有行，不重叠）。

## 提交策略
- 用户确认后：`new/` 仓库只 commit 本次改动文件，不动他人未提交文件。
