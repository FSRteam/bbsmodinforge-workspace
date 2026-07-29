# 伪装编辑器按骨骼附魔效果

## Goal

在伪装（Form）编辑器中选中某根骨骼后，可为该骨骼单独设置附魔光效，并定义附魔颜色、不透明度与动画运动速度；效果需与已有的盔甲附魔、手持物品附魔共存不冲突。

## Requirements

### R1 骨骼级附魔四态模式
用户可对**每根骨骼**独立设置附魔模式：
- **关闭** —— 无附魔效果（默认）
- **全表面** —— 采样原版附魔纹理覆盖整个骨骼表面，UV 随时间滚动，观感与附魔武器/盔甲一致
- **仅边缘** —— 基于法线与视线夹角的 Fresnel rim，只有轮廓发光，中间保持原贴图
- **原版** —— 直接使用 `RenderType.armorEntityGlint()` 的 shader、纹理、混合和纹理矩阵，行为与 Minecraft 原版盔甲附魔一致

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
- [x] 四种模式在编辑器中可切换并实时生效
- [x] 颜色、不透明度、速度可调且可 K 帧
- [x] ~~三条渲染路径均生效~~ → **VAO + CPU 即时两条已接入；BOBJ 不支持，见下**
- [x] 拾取（`stencilMap != null`）路径**不渲染** glint pass，骨骼点选不受影响
- [x] 与盔甲/物品附魔同屏无闪烁、无覆盖（两者都在 vanilla 管线内，与 BBS 自绘模型不共享状态）
- [x] i18n key 中英对等（最终实测 1962/1962，双向零缺失）
- [x] `cd new && ./gradlew build` **真实 exit code 为 0 且日志含 `BUILD SUCCESSFUL`**
- [x] Form 级与逐骨骼附魔都可独立编辑附魔层的 XYZ 位移、缩放、旋转，并支持保存、撤销与关键帧插值
- [ ] 游戏内确认模型与影片 Pose 面板折叠附魔分组后立即更新后续控件和滚动边界；影片 Pose 不再产生拉伸骨骼列表造成的长空白

## 追加需求（用户 2026-07-26 反馈后扩展）

### R7 修复：附魔覆盖本体材质、透明面变黑
**根因**：旧实现直接用 BBS 的 `model_glint` core shader 驱动 VAO，绕过了 `RenderType`。这既漏掉了 RenderType 的混合状态，也让 Iris/光影包无法把 draw call 路由到已知的 entity gbuffer，结果分别表现为透明区域发黑和光影下整个效果被跳过。

**修复**：删除独立 `model_glint` shader。原版模式始终使用 `RenderType.armorEntityGlint()`；无光影时，全表面与仅边缘使用 `RenderType.energySwirl(...)` 保留可调颜色与边缘渐变；Iris 世界光影启用时，三种模式统一进入光影包明确实现的 `armor_glint` 程序。所有活动效果统一关闭深度写入；恒等覆层使用 `EQUAL` 保留本体写入的透明遮罩，只有实际做过 XYZ 变换的覆层使用 `LEQUAL`。边缘 Fresnel 在 CPU 计算后写入顶点 alpha，不再依赖自定义 fragment shader。

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

Item 的第二次几何生成只捕获基础 RenderType。若物品本身带原版附魔，`ItemRenderer` 会用 `VertexMultiConsumer` 同时请求基础层和 vanilla glint 层；`GlintCaptureBufferSource` 对五种 glint RenderType 返回丢弃 consumer，避免同一几何在自定义 pass 中写入两次。原版附魔仍由正常物品 pass 绘制，因此两种效果保持独立。

### R9 附魔层 XYZ 变换面板

附魔选项新增独立的 `glint_transform`，包含 XYZ 位移、缩放、旋转（含现有 Transform 的第二旋转层）：

- Form 级整体附魔与逐骨骼附魔均支持，默认恒等变换，不改变旧存档外观。
- 变换只作用于附魔二次绘制，不移动或缩放本体模型；边缘模式下可据此调整发光边框的位置、大小和方向。
- 逐骨骼变换随 `PoseTransform` 参与 copy / equals / add / 两种 lerp / 序列化，并继承现有 Pose 关键帧、骨骼多选、应用到子骨骼、撤销/重做语义。
- Form 级变换使用 `ValueTransform`，进入现有 Form 值树和时间轴。
- VAO、CPU 即时、Billboard、Extruded、Block、Item 路径统一进入同一个 `GlintRenderState`；BOBJ 仍遵守既有不支持范围。
- 所有路径都在模型/骨骼局部空间应用变换：VAO 在提交 RenderType draw 前组合模型矩阵，CPU 即时、Billboard、Block、Item 在顶点进入已含摄像机的空间前应用，避免旋转或缩放随摄像机位置变化。
- 所有 Form 级附魔在拾取路径跳过，避免二次绘制污染伪装/骨骼选择缓冲。
- 多材质 VAO 的附魔二次绘制仍按材质分批，保持几何范围与本体一致。

### R10 修复：单骨骼附魔污染整个模型与场景深度

- 逐骨骼附魔不得插入后续骨骼的本体材质之间；VAO、CPU 与 hybrid 路径都必须先完成完整本体，再提交任何附魔 pass。
- 场景内半透明本体延迟绘制时，附魔必须进入同一个 `FormTranslucentQueue`，并排在对应本体命令之后。
- 嵌套调用 vanilla RenderType 前后必须恢复调用方的 shader、纹理/纹理矩阵、颜色、混合、深度、剔除、lightmap 与 overlay 相关状态。
- 未变换的附魔使用 `GL_EQUAL`，不得填充本体纹理已丢弃的透明像素，也不得穿透位于前方的方块或实体；已变换的附魔保留 `GL_LEQUAL`。
- CPU 合批按“恒等/已变换”拆开，避免一根已变换骨骼迫使整批未变换骨骼使用宽松深度测试。

### R11 修复：光影包方向半圆、Photon 缺失与 IterationRP 黑底

- CPU 边缘 Fresnel 的顶点位置、法线与视点必须位于同一空间。视点由“绘制时仍会应用的 ModelView”的逆矩阵把 view-space 原点变换回来，不能假定所有 PoseStack 烘焙路径的摄像机恒为 `(0,0,0)`。
- VAO 本体与 `GL_EQUAL` 边缘网格必须使用相同的矩阵分割点：边缘顶点保持骨骼局部坐标，绘制时复用本体的 `global * bone` ModelView。不得在 CPU 先算 `bone * vertex` 再只提交 `global`，否则浮点舍入会使精确深度测试随方向呈半圆失效。
- Iris 世界光影启用时，全表面、仅边缘和原版三种模式都使用 `RenderType.armorEntityGlint()`，因为 Photon 与 IterationRP 都只为 `gbuffers_armor_glint` 提供附魔专用合成契约。该路径必须保留 RenderType 建立的原版纹理矩阵；上一版额外覆盖自定义矩阵导致两种自定义模式在所有实测光影包中消失。光影下自定义速度允许降级，不能以可见性为代价强制保留。
- IterationRP 的实体程序会把原先的 energy-swirl 覆层作为普通实体材质输出，透明区域可能参与不正确的黑色合成；切到其 `Armor_Glint` 程序后使用光影包的专用附魔混合。
- Photon 的 `gbuffers_armor_glint.fsh` 不读取 `gl_Color`，因此只能保证附魔正确进入光影合成和保持可见；自定义颜色、透明度与顶点 Fresnel 强度在该包下可能降级为其原生附魔外观，属于光影包程序能力限制。

### R12 修复：附魔分组折叠后的空白与 Pose 布局

- `UISection` 在滚动面板内展开或折叠后，必须立即重排最近的 `UIScrollView`，同步后续控件位置、`scrollSize` 与当前滚动边界；不得依赖缩放游戏窗口后才恢复。
- 影片 Pose 编辑器不得再把骨骼列表强行拉伸到视口底部，避免新增或折叠附魔分组时把释放高度吸收到空列表区域。
- 影片 Pose 的宽、窄两种自适应布局都必须把模式、颜色、速度与附魔层变换保留在同一个 `UISection` 中，与模型编辑面板一样可整体折叠。

### 未覆盖范围（如实记录）

**BOBJ 格式模型不支持骨骼附魔。** `BOBJModel.applyPose()` 带有原作者注释「lighting/color transform attributes are not supported in this branch」—— 该格式**从来就没有**任何 per-bone 颜色/光照数据通路，`BOBJModelVAO` 的颜色是整个 mesh 一次 draw call 的单值，不是逐骨骼。

补齐它需要新建一整套「per-bone uniform 数组 + 顶点主导骨骼索引」的数据通路（可参考 `BOBJModelVAO.dominantBones` 现有的拾取用实现思路），是独立且量级明显更大的工作，不属于本任务范围。`.bbs.json` / `.geo.json` / `.vox` 格式（含全部内置玩家模型）走 VAO 或 CPU 路径，不受影响。

**CPU 即时路径的动画速度按模式共享**，非逐骨骼 —— 该路径整个模型打包成一次 draw call，无法承载 per-bone uniform。附魔层变换已在 CPU 生成顶点时按骨骼分别烘焙，因此只有速度受此限制。它只服务无法烘焙 VAO 的模型（shape key / weld），VAO 主路径保持逐骨骼速度。

## Constraints

- BBS 自定义模型的本体仍走自研 VAO，但附魔二次绘制必须显式调用已知的 vanilla `RenderType` 状态并使用其当前 shader；不得再引入独立 core shader，否则 Iris/光影包无法可靠接管
- 数据层在 `new/src/main/java/`，渲染与 UI 在 `new/src/client/java/`
- 排在 `07-26-blockbuster-armor-model` **之后**执行，两者交汇于 `ModelFormRenderer`
- 验证深度：编译通过即可，渲染效果由用户入游戏验收

## Notes

- 数据层有现成模板：`PoseTransform` 已有 `fix` / `color` / `lighting` 三个 per-bone 属性，新字段照抄其写法
- 渲染同步无需额外事件：`Model.applyPose()` 每帧把 `PoseTransform` 搬进 `ModelGroup`，渲染器逐骨骼读取
- 项目内**无**可复用的 3D rim/outline 实现（`selection.fsh` 是 2D 屏幕空间 mask，UI 专用）
- `model.vsh` 已输出 normal varying 但 `model.fsh` 未使用，做 Fresnel 可直接复用
- 研究记录见 `research/bone-editor-and-pose-data.md`
- 最终集成验证中，完整 `compileClientJava --rerun-tasks` 与排除无关音频测试的全量构建均通过；未排除时仅 `testAudioLifecycle` 在 `AudioLifecycleTest.java:1336` 因 `firstPreview == null` 失败，该音频回归不属于本任务。
