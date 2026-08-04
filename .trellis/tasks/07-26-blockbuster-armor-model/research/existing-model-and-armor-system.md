# 研究：本项目模型系统与盔甲渲染现状

日期：2026-07-26 · 来源：Explore agent 只读调研

## ⚠ 核心结论

**本项目已内建一套完整的、数据驱动的「自适应盔甲人物模型」系统**，与 Blockbuster 同属 mchorse 的同一套设计谱系。任务 2 不是「从零移植缺失功能」，而是**对照上游确认差距**。

已内置模型（`new/src/client/resources/assets/bbs/assets/models/`）：
- `player/`：`steve`、`steve_3d`、**`steve_bends`**、**`steve_bends_3d`**、`alex` 及变体（共 8 个）
- `emoticons/`
- 无 `fred` 或自定义 mob 内置模型

## 1. 模型格式与加载

- 加载入口 `new/src/client/java/mchorse/bbs_mod/cubic/model/ModelManager.java:96-140`
  - `getModel(id)` → 异步 `loadModel(id)`，通过 `AssetProvider.getLinksFromPath` 扫描 `assets:models/<id>/`，**纯目录发现，无需 Java 注册表**
  - 多 loader 链（`:56-63`）：`BOBJModelLoader`(.bobj/.obj)、`CubicModelLoader`(.bbs.json)、`GeoCubicModelLoader`(.geo.json，Bedrock/Blockbench)、`VoxModelLoader`(.vox)
  - `config.json` 与几何分离加载（`:142-154`）→ `ModelConfig`
  - 热重载：`IWatchDogListener`，`isRelodable()` 按扩展名判断（`:196-215`）
- 用户覆盖：`saveConfig()` 写 `config/bbs/assets/models/<id>/config.json`（fork 语义，`:156-178`）
- 走自研 `AssetProvider` / `Link` 虚拟资源层，**不是** NeoForge/vanilla `ResourceManager` reload listener

## 2. 骨骼层级与 Pose

- `IModel`（`new/src/main/java/mchorse/bbs_mod/cubic/IModel.java:14-67`）：骨骼查询 + Pose 应用 + 动画驱动
- `ModelGroup`（`cubic/data/model/ModelGroup.java:17-59`）：`parent`/`children`、`cubes`/`meshes`、`initial`/`current` 变换，运行时叠加层 `orient`（四元数，避免万向锁）与 `offset`（IK stretch）
- `ModelInstance`（client 侧）持有 `vaos: Map<ModelGroup, Map<String, ModelVAO>>`
- Pose 叠加：`ModelForm.pose` + `poseOverlay` + `additionalOverlays`（`ModelFormRenderer.java:175-217`）

## 3. 盔甲渲染 —— 已存在的完整实现

### a) 数据驱动骨骼映射

`assets/bbs/assets/models/player/steve/config.json:13-41`
```json
"armor_slots": {
    "helmet":    { "group": "armor_helmet" },
    "chest":     { "group": "armor_chest" },
    "leggings":  { "group": "armor_leggings" },
    "left_arm":  { "group": "armor_left_arm" },
    "right_arm": { "group": "armor_right_arm" },
    "left_leg":  { "group": "armor_left_leg" },
    "right_leg": { "group": "armor_right_leg" },
    "left_boot": { "group": "armor_left_boot" },
    "right_boot":{ "group": "armor_right_boot" }
}
```
模型作者在几何里放置对应命名的挂点骨骼（默认在 `disabledBones` 中隐藏），盔甲即跟随骨骼变换 —— **这就是「自适应/弯曲」的机制**。

### b) 类型定义

- `ArmorType`（`cubic/model/ArmorType.java:5-15`）：9 槽位，各映射到原版 `EquipmentSlot`
- `ArmorSlot`（`ArmorSlot.java:8-37`）：`group`（骨骼名）+ `transform`（附加偏移），支持纯字符串或带 transform 的 map 解析
- `ModelConfig`（`cubic/model/config/ModelConfig.java`）：`armorSlots: ArmorSlotsValue`，`rebuild()` 缓存进 `armorSlotsCache: Map<ArmorType, ArmorSlot>`（`:158-159, 210-213`）

### c) 渲染管线

- `ArmorRenderer`（`cubic/render/vanilla/ArmorRenderer.java:41-137`）**直接复用原版 `HumanoidModel`**：持有 `innerModel`/`outerModel`，`renderArmorSlot()` 读 `IEntity.getEquipmentStack(slot)`，按 `ArmorMaterial.Layer` 逐层渲染贴图、`ArmorTrim`、以及**附魔光效（`hasFoil` → `renderGlint`）**
- `ModelFormRenderer.renderArmor()`（`:585-613`）：取骨骼世界矩阵 `this.bones.get(armorSlot.group).matrix()`，push/应用 `armorSlot.transform`，调 `ActorEntityRenderer.armorRenderer.renderArmorSlot(...)`；主流程在 `:480-483` 遍历 `model.getArmorSlots()`
- 同一挂点抽象复用于物品：`items_main`/`items_off` + 第一人称 `fp_main`/`fp_offhand`，渲染在 `renderItems()`（`:615-675`）与 `renderArm()`（`:677-783`）

> **`ArmorRenderer` 里已有 glint 渲染** —— 这与任务 1（骨骼边缘附魔）的「兼容物品与模型的边缘附魔」直接相关，是两个任务的交汇点。

## 4. 玩家伪装渲染路径

- `Morph`（`morphing/Morph.java`）+ `IMorphProvider`，挂在玩家实体（`PlayerEntityMorphMixin`）
- 拦截点 `mixin/client/PlayerEntityRendererMixin.java:21-65`：`render()` HEAD 注入 → `MorphRenderer.renderPlayer()` 并取消原版；`getRenderOffset()` 清零；`renderHand()` 转发 `FormRenderer.renderArm()`
- `MorphRenderer.renderPlayer()`（`client/renderer/MorphRenderer.java:30-74`）→ `morph.getForm()` → `FormUtilsClient.render()` → `ModelFormRenderer`
- 装备读取统一走 `IEntity.getEquipmentStack(EquipmentSlot)`（`forms/entities/IEntity.java`，`MCEntity` 包装 `LivingEntity.getItemBySlot()`），因此同一套渲染同时服务「伪装玩家」与 `ActorEntity`

## 5. 新增模型的改动面评估

1. **零 Java 改动路径（首选）**：新建 `assets/bbs/assets/models/<id>/`，放几何（`.bbs.json`/`.geo.json`/`.bobj`/`.vox` 任一）+ 命名为 `armor_*` 的挂点骨骼 + 照抄 `player/steve/config.json:13-41` 的 `armor_slots`。渲染、`HumanoidModel` 复用、trim、glint 全自动生效。
2. **挂点命名/坐标系不同**：骨骼改名或在 `armor_slots` 用带 `transform` 的 map 形式做局部偏移矫正，不碰渲染代码。
3. **唯一可能动 Java 的场景**：新模型需要非 `HumanoidModel` 的盔甲几何（非人形比例穿模）。`ArmorRenderer.java:34-38` 硬编码 `innerModel`/`outerModel` 两个 `HumanoidModel<?>`。
4. **不需要改**：`ModelManager` 加载、`PlayerEntityRendererMixin`/`MorphRenderer` 伪装拦截、`ModelFormRenderer.renderArmor()`/`renderItems()` 挂点管线。

## 遗留待查项

- `ActorEntityRenderer.armorRenderer` 的初始化位置
- `GeoCubicModelLoader` / `CubicModelLoader` 对骨骼命名的解析细节
- `UIModelFormPanel` 等编辑器里是否已有 `armor_slots` 可视化编辑面板
- **`ArmorRenderer` 是否支持模组自定义盔甲模型**（NeoForge `IClientItemExtensions#getHumanoidArmorModel`）—— 用户要求「自适应**模组**盔甲」，这可能是真正的差距
- `steve_bends` 与上游 Blockbuster 目标模型的具体差异
