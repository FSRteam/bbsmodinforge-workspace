# 伪装编辑器按骨骼附魔效果

## Goal

在伪装（Form）编辑器中选中某根骨骼后，可为该骨骼单独设置附魔光效，并定义附魔颜色、不透明度与动画运动速度；效果需与已有的盔甲附魔、手持物品附魔共存不冲突。

## Requirements

### R1 骨骼级附魔三态模式
用户可对**每根骨骼**独立设置附魔模式：
- **关闭** —— 无附魔效果（默认）
- **全表面** —— 采样原版附魔纹理覆盖整个骨骼表面，UV 随时间滚动，观感与附魔武器/盔甲一致
- **仅边缘** —— 基于法线与视线夹角的 Fresnel rim，只有轮廓发光，中间保持原贴图

### R2 可调参数
- **附魔颜色**
- **附魔不透明度**
- **附魔动画运动速度**

### R3 关键帧动画
上述参数需与现有 pose 属性一样**支持时间轴 K 帧**。
- 颜色、不透明度、速度参与正常插值
- **模式是离散值，不能线性插值**，需按阈值取阶跃

### R4 与既有附魔共存（用户原话「兼容物品跟模型的边缘附魔效果」）
骨骼附魔与已有的盔甲附魔（`ArmorRenderer.renderGlint`，走 `RenderType.armorEntityGlint()`）、手持物品附魔（原版 `ItemRenderer`）**同屏时不打架**：
- 不互相覆盖
- 不闪烁（Z-fighting）
- 半透明排序正确

### R5 旧存档兼容
新增字段在旧存档中缺失时取默认值（模式=关闭），不得报错或改变既有伪装的外观。

### R6 编辑器交互
- 复用现有骨骼选中机制（多选、右键「应用到所有子骨骼」、撤销/重做）**不改动选中逻辑本身**
- 参数变更实时反映到渲染
- 新增 UI 文案需 `en_us.json` / `zh_cn.json` **双语言 key 对等**

## Acceptance Criteria

- [x] `PoseTransform` 新增字段，且 `identity` / 两个 `lerp` 重载 / `equals` / `copy` / `add` / `toData` / `fromData` **全部同步更新**
- [x] 旧存档读取不报错，附魔默认关闭（`glintSpeed` 特意用带默认值 1F 的读取，否则旧档会读成 0 导致动画静止）
- [x] 三种模式在编辑器中可切换并实时生效
- [x] 颜色、不透明度、速度可调且可 K 帧
- [x] ~~三条渲染路径均生效~~ → **VAO + CPU 即时两条已接入；BOBJ 不支持，见下**
- [x] 拾取（`stencilMap != null`）路径**不渲染** glint pass，骨骼点选不受影响
- [x] 与盔甲/物品附魔同屏无闪烁、无覆盖（两者都在 vanilla 管线内，与 BBS 自绘模型不共享状态）
- [x] i18n key 中英对等（实测 1947/1947，双向零缺失）
- [x] `cd new && ./gradlew build` **真实 exit code 为 0 且日志含 `BUILD SUCCESSFUL`**

## 追加需求（用户 2026-07-26 反馈后扩展）

### R7 修复：附魔覆盖本体材质、透明面变黑
**根因**：`ShaderInstance` 既不读也不应用 shader json 里的 `"blend"` 块 —— 那是 `RenderType` 层面的机制。glint pass 直接用 `ShaderInstance` 驱动 VAO，绕过 `RenderType`，因此声明的加性混合**从未生效**；叠加 fragment 输出 alpha=1，就变成不透明覆盖，条纹暗处 rgb≈0 呈现纯黑。

**修复**：新增 `cubic/render/GlintRenderState`，手动 `enableBlend` + 加性 `blendFuncSeparate(ONE,ONE, ZERO,ONE)`（保留目标 alpha，附魔不该让透明面变实心）+ `depthMask(false)`，绘制后复原。同时 fragment 里 `intensity *= baseAlpha`，使半透明面上的附魔相应变淡。

### R8 扩展：杂项分类的 Form 也支持附魔
附魔原本挂在 `PoseTransform`（逐骨骼），只有带骨架的模型可用。现新增 **Form 级整体附魔**：
- `Form` 基类新增 `glintMode` / `glintColor` / `glintSpeed`，随 `ValueGroup` 自动序列化
- `UIGeneralFormPanel` 的 Display 区新增控件（与 `lighting`/`shaderShadow`/`additiveColor` 并排），**所有 Form 编辑器共用**
- 新增 `Form.supportsGlint()` 能力查询（默认 false），控件只在真正渲染附魔的 Form 上显示 —— 避免出现调了没反应的开关。`ModelForm` 亦返回 false，因其有更精细的逐骨骼附魔

| Form | 支持 | 实现方式 |
|---|---|---|
| Billboard | ✅ | 复用 quad 几何，以附魔色重建顶点后二次绘制 |
| Extruded | ✅ | 走同一个 `ModelVAORenderer`，与骨骼附魔同构 |
| Block | ✅ | `GlintCaptureBufferSource` 把 vanilla 的分桶输出重定向到统一 `NEW_ENTITY` 缓冲，再二次绘制 |
| Item | ✅ | 同上；与物品自带的原版附魔是两条独立通道，互不干扰 |
| Label / Trail | ❌ | 用户决定不做（字形无法线；拖尾无颜色字段且几何为扁平） |
| Anchor / SoundSphere / SoundCone / VanillaParticle | — | 本身不可见 |

Block/Item 采用用户选定的「完整支持」路线：开启附魔时让 vanilla 再跑一遍几何生成（有性能开销，不开则零成本），换取颜色/不透明度/速度/边缘模式全可调。

### 未覆盖范围（如实记录）

**BOBJ 格式模型不支持骨骼附魔。** `BOBJModel.applyPose()` 带有原作者注释「lighting/color transform attributes are not supported in this branch」—— 该格式**从来就没有**任何 per-bone 颜色/光照数据通路，`BOBJModelVAO` 的颜色是整个 mesh 一次 draw call 的单值，不是逐骨骼。

补齐它需要新建一整套「per-bone uniform 数组 + 顶点主导骨骼索引」的数据通路（可参考 `BOBJModelVAO.dominantBones` 现有的拾取用实现思路），是独立且量级明显更大的工作，不属于本任务范围。`.bbs.json` / `.geo.json` / `.vox` 格式（含全部内置玩家模型）走 VAO 或 CPU 路径，不受影响。

**CPU 即时路径的动画速度按模式共享**，非逐骨骼 —— 该路径整个模型打包成一次 draw call，无法承载 per-bone uniform。它只服务无法烘焙 VAO 的模型（shape key / weld），VAO 主路径保持逐骨骼速度。

## Constraints

- BBS 自定义模型**绕过 vanilla `RenderType`/`MultiBufferSource`**，走自研 VAO 直绘 + 自研 core shader（`BBSShaders.getModel`），因此**无法复用 `RenderType.entityGlint()`，必须新写 shader**
- 数据层在 `new/src/main/java/`，渲染与 UI 在 `new/src/client/java/`
- 排在 `07-26-blockbuster-armor-model` **之后**执行，两者交汇于 `ModelFormRenderer`
- 验证深度：编译通过即可，渲染效果由用户入游戏验收

## Notes

- 数据层有现成模板：`PoseTransform` 已有 `fix` / `color` / `lighting` 三个 per-bone 属性，新字段照抄其写法
- 渲染同步无需额外事件：`Model.applyPose()` 每帧把 `PoseTransform` 搬进 `ModelGroup`，渲染器逐骨骼读取
- 项目内**无**可复用的 3D rim/outline 实现（`selection.fsh` 是 2D 屏幕空间 mask，UI 专用）
- `model.vsh` 已输出 normal varying 但 `model.fsh` 未使用，做 Fresnel 可直接复用
- 研究记录见 `research/bone-editor-and-pose-data.md`
