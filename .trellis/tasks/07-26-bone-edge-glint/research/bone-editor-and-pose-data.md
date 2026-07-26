# 研究：伪装编辑器骨骼选中 & per-bone 数据存储

日期：2026-07-26 · 来源：Explore agent 只读调研（未修改任何文件）

> 术语：本 mod 的「伪装」= **Form**；骨骼编辑面板 = **Pose Editor**（不叫 Morph）。
> 核心链路：`UIModelFormPanel` → `UIModelPoseEditor` → `UIPoseEditor` → `Pose` / `PoseTransform`。

## 1. 入口类与骨骼选中状态

- `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/UIModelFormPanel.java`
  - `:32` `public UIModelPoseEditor poseEditor;`
  - `:159-161` `startEdit()` 把 `form.pose`（ValuePose）、模型骨骼层级、被禁用骨骼列表灌入 `poseEditor`
  - `:174-179` `pickBone(String bone)` 外部入口（如点击 3D 视口骨骼）→ `poseEditor.selectBone(bone)`
- `new/src/client/java/mchorse/bbs_mod/ui/utils/pose/UIPoseEditor.java`
  - 当前选中骨骼实际存在 `UIBoneList.list`（`UIPoseBoneStringList`，**支持多选**）
  - `:36` `private static String lastLimb` 仅用于记忆上次编辑骨骼，面板重建后自动重选
  - `:308` `selectBone(String bone, boolean additive)`：非 additive 走 `setCurrentScroll` 单选；additive 走 `toggleIndex` 多选并保证至少一个选中
  - `:197` `getGroup()` 返回 `groups.list.getCurrentFirst()`，即主选骨骼名（字符串 = 模型 group key）
- `new/src/client/java/mchorse/bbs_mod/ui/utils/pose/UIBoneList.java` — 骨骼列表控件

## 2. per-bone 属性数据结构

容器：**`Map<String, PoseTransform>`**，key = 骨骼名。

`new/src/main/java/mchorse/bbs_mod/utils/pose/Pose.java:21`
```java
public final Map<String, PoseTransform> transforms = new HashMap<>();
```
`Pose.get(name)`（`:129`）查不到则懒创建默认 `PoseTransform` 入 map —— 语义上所有骨骼都「存在」一个默认 transform，只是不会被序列化。

- `Transform`（`utils/pose/Transform.java`）：`translate/scale/rotate/rotate2`（`Vector3f`）
- `PoseTransform extends Transform` 追加了三个**现成的 per-bone 附加属性范例**：
  ```java
  public float fix;                                          // PoseTransform.java:14
  public final Color color = new Color().set(Colors.WHITE);  // :15
  public float lighting;                                     // :16
  ```

命名风格：小写驼峰无前缀；容器字段 `public final`，值对象字段 `public`（无 getter/setter，直接改字段 + 手动 notify）。

## 3. 序列化与旧档兼容

接口 `IMapSerializable`（`toData(MapType)` / `fromData(MapType)`），格式为自研 `MapType`（`mchorse.bbs_mod.data.types`，NBT/JSON 通用抽象）。

- `Pose.toData/fromData`（`:202-241`）：**只序列化非默认骨骼**（`!entry.getValue().isDefault()`），整体包一层 `"pose"` key。存档中缺失的骨骼 = 全默认。
- `Transform.toData/fromData`（`:159-180`）：`t/s/r/r2` 四个 vector；`fromData` 先 `identity()` 再用带默认值的 `DataStorageUtils.vector3fFromData` 读 —— 老档兼容标准做法。
- `PoseTransform.toData/fromData`（`:121-139`）：先 `super.*`，再 `putFloat("fix")` / `putInt("color")` / `putFloat("lighting")`；`fromData` 用 `data.getInt("color", Colors.WHITE)` **带默认值的 getter** 兜底旧档。
- `isDefault()`（`:142-145`）靠 `equals(DEFAULT)` 静态哨兵判断。

### ⚠ 新增字段的标准套路（照抄 `fix`/`color`/`lighting`）
1. `PoseTransform` 加字段
2. `identity()` 重置为默认值
3. `equals()` / `copy()` / `lerp()` / `add()` **必须同步更新** —— 否则新字段不会被正确判默认、不会被拷贝/插值
4. `toData()` 加 `putXxx`；`fromData()` 加带默认值的 `getXxx` → 自动向后兼容
5. **不用改** `Pose.toData/fromData`（逐骨骼透传）

## 4. 编辑面板 UI 组件用法

均在 `UIPoseEditor` 构造器（`:54-104`）中作为字段：

```java
// 数值滑块 UITrackpad
this.fix = new UITrackpad((v) -> this.applyFixToSelection(v.floatValue()));
this.fix.limit(0D, 1D).increment(1D).values(0.1, 0.05D, 0.2D);
this.fix.tooltip(UIKeys.POSE_CONTEXT_FIX_TOOLTIP);

// 颜色选择器 UIColor
this.color = new UIColor((c) -> this.applyColorToSelection(c));
this.color.withAlpha();

// 开关 UIToggle
this.lighting = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_LIGHTING, (b) -> this.applyLightingToSelection(b.getValue()));
this.lighting.h(UIConstants.CONTROL_HEIGHT);
```

三者都用 `.context((menu) -> menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () -> this.applyChildren(...)))` 挂右键「应用到所有子骨骼」。
面板收尾统一 `this.add(this.groups, UI.labelRow(...), UI.row(this.color, this.lighting), this.transform...)`（`:103`）。

## 5. 编辑器 → 渲染端同步

**没有独立 dirty flag**，两条路径：

1. **数据层通知**（负责 undo/redo 与存档脏标记，与渲染无关）：`ValuePose extends BaseKeyframeFactoryValue<Pose>`（`settings/values/core/ValuePose.java`），改值前后 `preNotify()` / `postNotify()`（例 `UIModelPoseEditor.java:34-36` 的 `pastePose`）触发 `IValueListener` 链。
2. **渲染层每帧重新 apply pose，天然实时同步**：
   - `ModelFormRenderer.java:312/813/1275` 每帧 `model.model.applyPose(this.getPose(this.renderPose))`
   - `Model.applyPose(Pose)`（`cubic/data/model/Model.java:128-180`）遍历 `pose.transforms`，把字段写进 `ModelGroup`（`:154-155` `group.color.copy(transform.color)`、`group.lighting = transform.lighting`）
   - 真正落顶点：`cubic/render/CubicCubeRenderer.java:408/418`、`CubicVAORenderer.java:78-90` 读 `group.color` / `group.lighting`

**结论**：只要新字段走同一条 `PoseTransform → Model.applyPose → ModelGroup → CubicCubeRenderer/CubicVAORenderer` 管线即可生效，**无需额外事件/回调**。

## 6. 新增 per-bone glint 属性的最小侵入方案

1. `PoseTransform.java`：加字段（bool 或 float 强度，建议仿 `lighting` 用 float 以支持权重混合）；同步 `identity()` / `equals()` / `copy()` / `lerp()` / `add()` / `toData` / `fromData`
2. `Model.java:135-167` `applyPose()` 循环加一行 `group.glint = transform.glint;`
3. `ModelGroup.java`（`:28-29` 旁）加运行时字段
4. 渲染端 `CubicCubeRenderer` / `CubicVAORenderer`：`group.glint` 非零时叠加 glint 专用 RenderType（类似原版盔甲附魔的 double-pass 半透明纹理叠加）
5. UI：`UIPoseEditor` 加控件字段 + 构造器 + `pickBones()` 回显 + `applyGlintToSelection()`；`UIModelPoseEditor` 仿 `setLighting` 加 `setGlint` 做 `preNotify/postNotify` 包裹
6. **骨骼选中机制完全不用动** —— 多选 / 应用到子骨骼 / 撤销机制自动继承

## 关键文件清单

```
new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/UIModelFormPanel.java
new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/widgets/UIModelPoseEditor.java
new/src/client/java/mchorse/bbs_mod/ui/utils/pose/UIPoseEditor.java
new/src/client/java/mchorse/bbs_mod/ui/utils/pose/UIBoneList.java
new/src/main/java/mchorse/bbs_mod/utils/pose/Pose.java
new/src/main/java/mchorse/bbs_mod/utils/pose/PoseTransform.java
new/src/main/java/mchorse/bbs_mod/utils/pose/Transform.java
new/src/main/java/mchorse/bbs_mod/settings/values/core/ValuePose.java
new/src/main/java/mchorse/bbs_mod/forms/forms/ModelForm.java
new/src/main/java/mchorse/bbs_mod/cubic/data/model/Model.java
new/src/main/java/mchorse/bbs_mod/cubic/data/model/ModelGroup.java
new/src/client/java/mchorse/bbs_mod/forms/renderers/ModelFormRenderer.java
new/src/client/java/mchorse/bbs_mod/cubic/render/CubicCubeRenderer.java
new/src/client/java/mchorse/bbs_mod/cubic/render/CubicVAORenderer.java
```
