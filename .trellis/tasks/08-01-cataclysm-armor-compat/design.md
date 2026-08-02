# 灾变模组盔甲兼容 — 技术设计

## 背景与现状（反编译确认）

Cataclysm 3.32 / Lionfish-API 3.1（1.21.1 NeoForge）盔甲机制：

- Lionfish-API 3.1 是**纯动画库**（mowzie + citadel 系），**不含任何盔甲渲染类**。
- 灾变所有盔甲物品 `extends Cataclysm_Armor extends ArmorItem`，`getEquipmentSlot()` 正常。
- 灾变所有盔甲模型 `extends HumanoidModel`（如 `Cursium/Ignitium/Bone_Reptile_*_Model`）。
- 自定义模型 hook：`CustomArmorRenderProperties.getHumanoidArmorModel()` 返回对应自定义 `HumanoidModel`，走标准 NeoForge `IClientItemExtensions`。
- **Cursium（烁镍）幽灵特效层**：`renderCustomArmor()` 额外渲染半透明 ghost，靠灾变的 `HumanoidArmorLayerMixin` 注入，BBS form 渲染不走 `HumanoidArmorLayer`，故缺失。

BBS 侧现有：
- `ArmorRenderer.renderSlot()`：`item instanceof ArmorItem` 判断 → 走 `resolveModel()` → `IClientItemExtensions.getHumanoidArmorModel()`. **已具备调灾变自定义模型的能力。**
- `ArmorRenderer.resolveModel()`：**仅 `entity instanceof MCEntity` 且其 mcEntity 是 `LivingEntity` 时才解析 mod 模型**；否则返回 vanilla 模型。

## 核心缺口

### 缺口 A：预览/编辑器场景（StubEntity）无法解析 mod 盔甲
- `BaseFilmController.createEntities` 用 `StubEntity(world)` 承载 form 预览。
- `resolveModel` 因 entity 不是 `MCEntity` 而返回原版模型 → 编辑器/预览里灾变盔甲显示为原版形状。
- **actor 回放模式**用 `MCEntity`，已能解析，故"回放 actor 模式已支持"。

### 缺口 B：Cursium 幽灵特效层缺失
- BBS 不在 `HumanoidArmorLayer` 里渲染盔甲，灾变的 `renderCustomArmor` 特效（ghost 半透明层）不会触发。

## 设计

### 方案 A：给 StubEntity 预览提供 LivingEntity 引用（解决缺口 A）

为 `StubEntity` 增加一个可选的 `LivingEntity` fallback（如编辑器预览关联主玩家 `LocalPlayer`），使 `resolveModel` 能拿到 LivingEntity 调 mod hook。

`StubEntity` 新增：

```java
private LivingEntity entityOverride;
public void setEntityOverride(LivingEntity e) { this.entityOverride = e; }
public LivingEntity getEntityOverride() { return this.entityOverride; }
public boolean isMcEntity() { return this.entityOverride != null; }
public LivingEntity getMcEntity() { return this.entityOverride; }
```

`ArmorRenderer.resolveModel()`（`ArmorRenderer.java:219-229`）扩展：当 entity 是 `MCEntity` 或 StubEntity 带 override 时，都能拿到 LivingEntity：

```java
private HumanoidModel<?> resolveModel(ItemStack itemStack, IEntity entity, EquipmentSlot slot, HumanoidModel<?> original) {
    LivingEntity livingEntity = null;
    if (entity instanceof MCEntity mc && mc.getMcEntity() instanceof LivingEntity le) {
        livingEntity = le;
    } else if (entity instanceof StubEntity st && st.getMcEntity() instanceof LivingEntity le) {
        livingEntity = le;
    }
    if (livingEntity == null) return original;
    HumanoidModel<?> replacement = IClientItemExtensions.of(itemStack).getHumanoidArmorModel(livingEntity, itemStack, slot, original);
    return replacement == null ? original : replacement;
}
```

**实体注入点**：`createEntities` 被 `FilmEditorController`（编辑器）与 `WorldFilmController`（世界播放）共用。在 `BaseFilmController` 增加受保护钩子 `resolvePreviewLivingEntity()`（默认 null），`FilmEditorController` 覆写返回 `Minecraft.getInstance().player`。仅在 editor 注入 LocalPlayer，world 播放不注入（那里 actor 才是主体）。

### 方案 B：Cursium ghost 特效层（本轮纳入，用户确认）

灾变 Cursium ghost 使用 `CMRenderTypes.GHOST` = `RENDERTYPE_ENERGY_SWIRL_SHADER` + `TRANSLUCENT_TRANSPARENCY` + `LEQUAL_DEPTH_TEST` + 自定义幽灵纹理 `textures/armor/cursium_armor_ghost.png`，带 125/255 固定 alpha 的鬼影闪烁效果。BBS 现有 `GlintRenderState`（第 179-184 行）已通过 `RenderType.energySwirl()` 处理 energy-swirl shader + 时间偏移 + 半透明管线。

差异：
- 使用灾变 ghost 纹理（而非 `ENCHANTED_GLINT_ENTITY`）
- 固定 alpha = 125/255（而非着色器颜色控制）
- 用于 armor 几何体本身的半透明覆盖层（而非 glint overlay 模式）

实现方案：

1. **`ArmorRenderer.renderSlot()` 尾部追加 ghost 判定**：对 Cursium 材质的物品，在 `renderArmorPart` 后追加一次 `renderGhost(part, matrices, vertexConsumers, light, color)`。
2. **`renderGhost` 方法**：构建 `RenderType.energySwirl(cursiumGhostTexture, u, v)` + 半透明状态，复用 `GlintRenderState` 类似的 RenderType 与状态管理。纹理 `ResourceLocation` 在运行期通过字符串构造（无编译期灾变依赖）。
3. **Cursium 判定**：通过物品 registry id（`cataclysm:cursium_helmet/chestplate/leggings/boots`）在运行期匹配——反射式字符串比较，无需灾变类引用。
4. **半透明排序**：通过 `FormTranslucentQueue` 提交 ghost 渲染，保证正确的前后排序。

**光影兼容**：`energySwirl` 是原版标准 RenderType，Iris 为其提供显式支持路径（与 charged-creeper/wither armor 同）。在 Iris 光影世界渲染时，优先 energySwirl 路径；降级到 `armorEntityGlint` 作为后备。
**已知限制**：某些不具备 `energySwirl` RenderType 支持的光影包可能显示为纯白色或缺失。不影响无光影的基准行为。

## 兼容性与风险

- 方案 A（缺口 A）：StubEntity 新增字段+方法（纯增量），resolveModel 加 else-if 分支（无 override 时行为不变）。风险低。
- 方案 B（ghost 层）：在 ArmorRenderer 中增加 Cursium 特判（通过 registry id 字符串匹配），无编译期灾变依赖。ghost RenderType 复用 `GlintRenderState.energySwirl` 路径已验证。风险中低。
- 不引入 GeckoLib / Iron's Lib 依赖。
- `new/` 无灾变模组时编译运行不受影响（无灾变类直接引用）。

## 测试

- 编译通过（无灾变依赖）。
- 运行时装配：在开发 mods 目录放入 Cataclysm + Lionfish-API jar。
- 验证纹理：Ignitium/Cursium/Bone_Reptile 自定义模型形状与材质正确。
- Cursium ghost 层：验证半透明鬼影闪烁效果，含光影下显示。
- 覆盖场景：actor 回放 + 编辑器预览。

## 主要改动文件

- `new/src/main/java/mchorse/bbs_mod/forms/entities/StubEntity.java`（新增 LivingEntity override）
- `new/src/main/java/mchorse/bbs_mod/forms/entities/IEntity.java`（可选的 isMcEntity 接口方法）
- `new/src/client/java/mchorse/bbs_mod/cubic/render/vanilla/ArmorRenderer.java`（resolveModel 扩展 + ghost 渲染）
- `new/src/client/java/mchorse/bbs_mod/film/BaseFilmController.java`（createEntities → resolvePreviewLivingEntity 钩子）
- `new/src/client/java/mchorse/bbs_mod/ui/film/controller/FilmEditorController.java`（覆写 resolvePreviewLivingEntity 返回 LocalPlayer）