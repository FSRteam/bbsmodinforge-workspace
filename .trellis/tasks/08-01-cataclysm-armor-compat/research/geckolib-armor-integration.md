# Research: BBS ArmorRenderer 支持 GeckoLib 盔甲（Iron's Spellbooks / Iron's Lib）的接入调查

- **Query**: 铁魔法（Iron's Spellbooks）盔甲如何注册自定义模型？NeoForge 1.21.1 上 GeoArmorRenderer 完整渲染调用链是什么？BBS 的 ArmorRenderer 需要什么才能支持 GeoArmorRenderer？是否需要 GeckoLib 编译期依赖？给出可实施的接入设计。
- **Scope**: mixed（本地反编译 + 本地 NeoForge sources + GitHub 上游源码 + GeckoLib 4.7.5.1 jar）
- **Date**: 2026-08-02

## 结论摘要（TL;DR）

1. **铁魔法（ISB）盔甲不是走 `IClientItemExtensions.getHumanoidArmorModel`，而是走 GeckoLib 的 `GeoItem.createGeoRenderer` → `GeoRenderProvider.getGeoArmorRenderer`**。ISB 盔甲物品确实是 `ArmorItem`（`ExtendedArmorItem extends ArmorItem implements GeoItem`），会通过 BBS `renderSlot` 的 `instanceof ArmorItem` 门；但其模型是 `GeoArmorRenderer`（geo 模型），BBS 现有的 `getPart + part.render` 路径**渲染不出真实几何**（只会画出 vanilla 内层骨架盒子）。
2. GeckoLib 4.7.5.1 在 NeoForge 上的接入 = 自己的 `HumanoidArmorLayerMixin`（`@WrapWithCondition` 包住 `renderArmorPiece`）+ `InternalUtil.tryRenderGeoArmorPiece`（`prepForRender` → `renderToBuffer`）。这条链路与 Cataclysm 走的 `IClientItemExtensions` 链路完全不同。
3. BBS 支持 GeoArmorRenderer 的可行路径：在 `renderSlot` 顶部插入 "geo 盔甲分支"，检测到 `GeoRenderProvider` 提供渲染器时：
   `getGeoArmorRenderer(entity, stack, slot, baseModel)` → `prepForRender(entity, stack, slot, baseModel, bufferSource, partial, 0,0,yaw,pitch)` → `renderToBuffer(matrices, null, light, NO_OVERLAY, 0xFFFFFFFF)`，并**禁用 skinning/弯折**。
4. 无 GeckoLib 环境仍要编译运行：加 `clientCompileOnly` GeckoLib 依赖 + 独立桥接类 + `Class.forName` 可用性哨兵（或纯反射）。当前灾变任务的 PRD 明确"不引入 GeckoLib/Iron's Lib 编译期依赖"，因此本接入属于**分离的未来子任务**。
5. 三个硬约束（决定设计）：
   - `GeoArmorRenderer.renderToBuffer` 必须 `currentEntity != null`，否则报错并空渲染 → **必须传真实 LivingEntity**（编辑器预览需复用灾变任务的 StubEntity override 方案）。
   - `renderToBuffer` **忽略调用方 buffer/bufferSource**，自取 `Minecraft.getInstance().levelRenderer.renderBuffers.bufferSource()`，走实时动画时钟 → **BBS 的 CustomVertexConsumerProvider 与 skinning 拦截全部失效**，geo 盔甲只能刚性渲染。
   - GeoArmorRenderer 的 HumanoidModel 零件是**原版内层骨架盒子**（bake `PLAYER_INNER_ARMOR`），`part.render` 会画出错误形状。

---

## 1. 铁魔法盔甲如何注册自定义模型（Q1）

### 1.1 调查源

- GitHub 上游（分支 `1.21`，mod_version `1.21.1-3.16.2-ice-spider-egg`）实际下载文件（本调查已存 `temp/isb-files/`）：
  - `src/main/java/io/redspace/ironsspellbooks/item/armor/ExtendedArmorItem.java`
  - `src/main/java/io/redspace/ironsspellbooks/item/armor/WizardArmorItem.java`
  - `src/main/java/io/redspace/ironsspellbooks/entity/armor/GenericArmorModel.java`
  - `src/main/java/io/redspace/ironsspellbooks/entity/armor/GenericCustomArmorRenderer.java`
  - `src/main/java/io/redspace/ironsspellbooks/entity/armor/DyeableArmorRenderer.java`
  - `gradle.properties` / `build.gradle`（依赖声明）
- 本地反编译 `temp/irons_lib-decompiled/`（Iron's Lib，同铁魔法系，transmog 盔甲同样全走 GeoArmorRenderer）。

### 1.2 事实：注册走 `GeoItem.createGeoRenderer`，不走 `IClientItemExtensions`

- `ExtendedArmorItem.java:32`：`public abstract class ExtendedArmorItem extends ArmorItem implements GeoItem` —— **铁魔法盔甲是 `ArmorItem`**（带 `getEquipmentSlot()`），但**不注册 `IClientItemExtensions`**。
- `ExtendedArmorItem.java:90`：`private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);`（GeoItem 动画实例缓存）。
- `ExtendedArmorItem.java:109-121`：`createGeoRenderer(Consumer<GeoRenderProvider>)` —— 匿名 `GeoRenderProvider`，其 `getGeoArmorRenderer(livingEntity, itemStack, equipmentSlot, original)` 懒建 `supplyRenderer()` 返回的渲染器：
  ```java
  @Override
  public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
      consumer.accept(new GeoRenderProvider() {
          private GeoArmorRenderer<?> renderer;
          @Override
          public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack, @Nullable EquipmentSlot equipmentSlot, @Nullable HumanoidModel<T> original) {
              if (this.renderer == null) this.renderer = supplyRenderer();
              return this.renderer;
          }
      });
  }
  ```
- `WizardArmorItem.java:36-38`：`supplyRenderer()` 返回 `new DyeableArmorRenderer<>(new GenericArmorModel<WizardArmorItem>("wizard")...)`。`GeoArmorRenderer` 的定位自 `GenericArmorModel.java:29-30`：`geo/<name>_armor.geo.json` + `textures/models/armor/<name>.png`。
- 全仓库 armor 目录（`src/main/java/io/redspace/ironsspellbooks/item/armor/`，26 个文件）均继承 `ExtendedArmorItem` 体系，无一个实现 `initializeClient(IClientItemExtensions)`。

> 关键推论：**"两者都有"不成立**。铁魔法系只提供 `GeoRenderProvider.getGeoArmorRenderer`。BBS 当前 `resolveModel`（`ArmorRenderer.java:345-357`）调 `IClientItemExtensions.of(itemStack).getHumanoidArmorModel(...)` 对 ISB 物品返回原版 `original` → BBS 会用原版 Inner/Outer 模型 + material 层纹理画出**方盒盔甲**（形状错，且 ISB material layer 纹理大多无意义，geo 纹理才是正主）。
>
> 注：GeckoLib 也兼容"历史风格"——wiki 的 Forge 示例用 `initializeClient` + `getHumanoidArmorModel` 内先手动 `prepForRender` 再返回 GeoArmorRenderer；且 `GeckoLibClientNeoForge.java:29-37` 在实体模型查询时两种路径都试。但 NeForge 1.21.1 + 4.7.5.1 的实际渲染优先走 mixin + GeoRenderProvider（见 §2），ISB 用的是现代风格。

---

## 2. NeoForge 1.21.1 上 GeoArmorRenderer 的完整渲染调用链（Q2）

### 2.1 本地可查源码清单

| 源 | 位置 |
|---|---|
| 原版 `HumanoidArmorLayer`（NeoForge 合并反编译） | 从 `neoformruntime/sourcesAndCompiledWithNeoForge_*.jar` 解出，本调查存 `temp/neoforge-vanilla-src/net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer.java` |
| NeoForge `IClientItemExtensions` / `ClientHooks` | 同 origin jar → `temp/neoforge-vanilla-src/net/neoforged/neoforge/client/...` |
| GeckoLib 4.7.5.1 源码 | 本调查 clone `temp/geckolib-1.21.1/`（该分支现为 4.9.2 开发版，但 GeoArmorRenderer / GeoRenderProvider / InternalUtil / mixin 与 4.7.5.1 逐方法签名一致，见 §2.2 的 javap 验证） |
| GeckoLib 4.7.5.1 精确 jar | 本调查自 Cloudsmith 下载 `temp/geckolib-4751/geckolib-neoforge-1.21.1-4.7.5.1.jar`（592,730 字节）并 javap 验证 |
| Iron's Lib 反编译 | `temp/irons_lib-decompiled/io/redspace/ironslib/...` |

### 2.2 端到端调用链（NeoForge 21.1.222 / Minecraft 1.21.1）

1. **原版层渲染入口** `HumanoidArmorLayer.render`（`HumanoidArmorLayer.java:45-61`）→ 对 CHEST/LEGS/FEET/HEAD 各调 `renderArmorPiece(...)`（12 参新重载，`HumanoidArmorLayer.java:69`）。
2. **GeckoLib 拦截**：`HumanoidArmorLayerMixin`（geckolib neoforge 模块，jar 内 `geckolib.mixins.json` → `client.HumanoidArmorLayerMixin`）用 `@WrapWithCondition` 包住 `renderArmorPiece(...;I;HumanoidModel;FFFFFF)V`（`temp/geckolib-1.21.1/neoforge/src/main/java/software/bernie/geckolib/mixin/client/HumanoidArmorLayerMixin.java:25-32`；4.7.5.1 jar 的 injection target 已用 `javap -v` 验证一致）。若 `InternalUtil.tryRenderGeoArmorPiece` 返回 `true`，则不执行原版 `renderArmorPiece`。
3. **判定与取渲染器** `InternalUtil.tryRenderGeoArmorPiece`（`temp/geckolib-1.21.1/common/src/main/java/software/bernie/geckolib/util/InternalUtil.java:37-60`）：
   - `item instanceof Equipable` 且 `equipable.getEquipmentSlot() == slot`（`InternalUtil.java:41-43`）——**比 `ArmorItem` 判据更宽**；
   - `GeoRenderProvider.of(item).getGeoArmorRenderer(entity, stack, slot, baseModel)`（`InternalUtil.java:45`）；
   - 若为 `null` 返回 false（非 geo 盔甲，回原版链路 → Cataclysm 等走 `IClientItemExtensions`）。
4. **prep + render**（`InternalUtil.java:50-57`）：
   ```java
   parentModel.copyPropertiesTo(baseModel);
   partVisibilitySetter.accept(baseModel, equipmentSlot);            // setPartVisibility
   geoArmorRenderer.prepForRender(entity, stack, equipmentSlot, baseModel,
       bufferSource, partialTick, limbSwing, limbSwingAmount, netHeadYaw, headPitch);
   baseModel.copyPropertiesTo((A)geckolibModel);
   geckolibModel.renderToBuffer(poseStack, null, packedLight, OverlayTexture.NO_OVERLAY, Color.WHITE.argbInt());
   ```
5. **`GeoArmorRenderer.renderToBuffer`**（`GeoArmorRenderer.java:329-352`）：
   - `currentEntity == null` → 打日志 `"A mod is attempting to render GeckoLib armor ({}) without using GeoRenderProvider! IClientItemExtensions is not supported for GeckoLib!"` 并直接 return（**空渲染**）。所以：必须 `prepForRender` 且实体非空。
   - **忽略传入 buffer**：`bufferSource = mc.levelRenderer.renderBuffers.bufferSource()`（339-340 行）；`buffer = ItemRenderer.getArmorFoilBuffer(bufferSource, renderType, currentStack.hasFoil())`（346 行，寒光/foil 自处理）。
   - `defaultRender(...)`（`GeoRenderer.java:126-156`）→ `preRender`（GeoArmorRenderer 覆写 `GeoArmorRenderer.java:311-325`：`applyBaseModel` + `grabRelevantBones` + `applyBaseTransformations` + 非 GeoAnimatable 实体的 `applyBoneVisibilityBySlot`）→ `actuallyRender`（`GeoArmorRenderer.java:360-387`：`translate(0, 24/16f, 0)` + `scale(-1,-1,1)` 翻转，`!isReRender` 时跑模型动画 `handleAnimations`，然后对 `topLevelBones()` 逐个 `renderRecursively`）→ `doPostRenderCleanup` 清空 `currentEntity/currentStack/animatable`（`GeoArmorRenderer.java:394-407`）。
6. 原版链路的"外置模型扩展"是**另一条路**：`renderArmorPiece` 内 `getArmorModelHook` → `ClientHooks.getArmorModel`（`ClientHooks.java:322-324`）→ `IClientItemExtensions.getGenericArmorModel`（`IClientItemExtensions.java:125-132`）→ `getHumanoidArmorModel`（107-109 行）。**GeoArmorRenderer 不走这条路渲染**（它会因 `currentEntity == null` 空渲染）。NeoForge 的 `setPartVisibility` 只用于非 geo 模型（`HumanoidArmorLayer.java:103-124`）。

> **对 1.21.1 来说"EquipmentLayerRenderer"不存在**：`EquipmentLayerRenderer` 是 1.21.2+ 的 vanilla 类，21.1.222 环境无此类（`sourcesAndCompiledWithNeoForge` jar 内查无）。铁魔法在 1.21.1 的接入点就是上述 `HumanoidArmorLayerMixin`。

### 2.3 4.7.5.1 精确 API 验证（javap，来自下载 jar）

- `GeoRenderProvider`：`static of(ItemStack)`、`of(Item)`；`default <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(T, ItemStack, EquipmentSlot, HumanoidModel<T>)`（4 参 + 实体版本，**这是 1.21.1/4.7.5.1 签名**；1.21.3+ 新版才改为 `getGeoArmorRenderer(ItemStack, EquipmentSlot)`，勿混）。
- `GeoArmorRenderer`：`prepForRender(Entity, ItemStack, EquipmentSlot, HumanoidModel<?>)`（deprecated，推荐全参版）、`prepForRender(Entity, ItemStack, EquipmentSlot, HumanoidModel<?>, MultiBufferSource, float, float, float, float, float)`、`renderToBuffer(PoseStack, VertexConsumer, int, int, int)`、`applyBoneVisibilityByPart(EquipmentSlot, ModelPart, HumanoidModel<?>)`。
- `InternalUtil.tryRenderGeoArmorPiece(...)` 签名与源码一致。

---

## 3. BBS ArmorRenderer 需要什么（Q3）

### 3.1 BBS 现状逐点核对

| BBS 现状 | 对 geo 盔甲的影响 |
|---|---|
| `renderSlot` 门 `item instanceof ArmorItem armorItem && armorItem.getEquipmentSlot() == armorSlot`（`ArmorRenderer.java:146`） | ISB 是 `ArmorItem`，**通过**。但 geckolib 的判据是 `Equipable`（`InternalUtil.java:41`），BBS 若只认 `ArmorItem` 可能漏掉"非 ArmorItem 但 Equipable 的 geo 盔甲"（当前 ISB 不受影响）。 |
| `resolveModel` → `IClientItemExtensions.getHumanoidArmorModel`（`ArmorRenderer.java:354`） | ISB 未注册该 hook → 返回 vanilla 模型 → BBS 画原版盒子形状（`getPart`+`part.render`，`ArmorRenderer.java:151,173,316-328`）。**形状错，纹理取 material layer**（`ClientHooks.getArmorTexture`，`ArmorRenderer.java:396`）。 |
| `resolveLivingEntity`：仅 `MCEntity`/`StubEntity.getMcEntity()` 能给 `LivingEntity`（`ArmorRenderer.java:365-378`） | geo 渲染**必须有真实 `LivingEntity`**（`GeoArmorRenderer.java:331`）。编辑器预览的 StubEntity 若无 override → 无法渲染 geo 盔甲（与灾变任务"方案 A"是同一个坑，复用同一修复）。 |
| skinning 弯折：`SkinningVertexConsumer` 拦截顶点（`ArmorRenderer.java:316-328`） | geckolib 自建 VertexConsumer 与 buffer，**拦截不到** → geo 盔甲必须禁用 skinning（刚性渲染）。 |
| 渲染管线：`CustomVertexConsumerProvider extends MultiBufferSource.BufferSource`（`CustomVertexConsumerProvider.java:19`），`renderArmorSlot(...)` 后 `consumers.draw()`（`ModelFormRenderer.java:613,639-640`） | geckolib 忽略传入 buffer，直写 `mc.levelRenderer.renderBuffers.bufferSource()` → **绕过 BBS 的延迟绘制/FBO/顶点 hijack**。actor 回放（世界渲染内调用）可见；预览/离屏场景需实测验证。 |
| ghost 层 / trim / glint 单件追加（`ArmorRenderer.java:176-191`） | geckolib 内部自管 foil（`GeoArmorRenderer.java:346`）且可能有自己的 RenderLayer（`GeoArmorRenderer.java:170-181`）→ BBS 不应再叠加 ghost/trim/glint。 |

### 3.2 可行接入路径（推荐）

在 `ArmorRenderer.renderSlot` 顶部、`item instanceof ArmorItem` 之前插一个 geo 分支（≈8 行改动 + 一个独立的 geo 支持类）：

```java
// ArmorRenderer.renderSlot(...) 开头，getEquipmentStack/Item 之后：
if (GeoArmorSupport.isGeoArmor(itemStack))                    // ① 无 geckolib 时恒 false
{
    this.renderGeoArmor(matrices, vertexConsumers, entity, itemStack, armorSlot, this.getModel(armorSlot), light);
    return;                                                    // 跳过原版 ArmorItem 路径
}
// 以下原 `item instanceof ArmorItem ...` 逻辑不变
```

```java
private void renderGeoArmor(PoseStack matrices, MultiBufferSource vertexConsumers, IEntity entity,
                            ItemStack stack, EquipmentSlot slot, HumanoidModel<?> baseModel, int light)
{
    LivingEntity living = this.resolveLivingEntity(entity);
    if (living == null)
        return;                       // ② geo 渲染必须真实实体（编辑器预览待 StubEntity override 修复）

    this.skinning = false;            // ③ 弯折/skinning 对 geo 几何不可拦截 → 刚性渲染

    // ④ 镜像 Iron's Lib HumanoidArmorLayerMixin / geckolib InternalUtil 的调用序列
    GeoArmorSupport.renderGeoArmor(matrices, vertexConsumers, living, stack,
                                   slot, baseModel, light);
}
```

新类 `GeoArmorSupport`（client source set，唯一允许直引 geckolib 类的地方）核心：

```java
// GeoRenderProvider.of(stack).getGeoArmorRenderer(entity, stack, slot, baseModel)
//    -> null / 非 GeoArmorRenderer 则返回 false（非 geo 盔甲，交给原版路径）
//    -> 否则：
//       renderer.prepForRender(entity, stack, slot, baseModel, vertexConsumers,
//                              partialTick, 0F, 0F, netHeadYaw, headPitch);
//       renderer.renderToBuffer(matrices, null, light,
//                               OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
//       return true;
```

细节与铁证：
- **baseModel 选择**：与 vanilla 一致，LEGS 用 inner、其余 outer（`ArmorRenderer.getModel` 已如此，`ArmorRenderer.java:330-333`；即 `InternalUtil.java:45` 例子里 Iron's Lib 传 `slot==LEGS?INNER_ARMOR:OUTER_ARMOR`，见 `TransmogClientHandler.java:69`）。
- **prepForRender 必须**：不调则 `renderToBuffer` 打错误日志并空渲染（`GeoArmorRenderer.java:331-336`）。Iron's Lib 自身在 mixin 里也调 `prepForRender(entity, stack, slot, baseModel, bufferSource, partialTick, limbSwing, ...)`（`irons_lib-decompiled/.../internal/mixin/HumanoidArmorLayerMixin.java:78`）再 `renderToBuffer(poseStack, null, packedLight, NO_OVERLAY, Color.WHITE.argbInt())`（第 80 行）。
- **netHeadYaw/headPitch/limbSwing 无可用值时传 0**：geckolib 的 deprecated `prepForRender(entity,stack,slot,baseModel)` 就是 0,0,0,0（`GeoArmorRenderer.java:465-472`）。
- **动画时钟**：`renderToBuffer` 内 `partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true)`（`GeoArmorRenderer.java:344`）取实时时钟；`animatable.getTick(entity)` 取实体 tick（`GeoArmorRenderer.java:372`）→ **geo 盔甲动画无法被 BBS 电影时钟 scrub/定格**（影视用途的已知局限，需在产品上决定是否接受）。
- **模型状态清理**：`renderToBuffer` 结束 `animatable=null`，`defaultRender` 尾 `doPostRenderCleanup`（`GeoRenderer.java:154`）清空上下文；渲染器实例由 ISB 侧缓存复用（`ExtendedArmorItem.java:115-117`），BBS 无需持有/释放。

### 3.3 明确不可行/要避免的路径

- **不要**把 GeoArmorRenderer 当普通 `HumanoidModel` 走 `getPart + part.render`：其 HumanoidModel 零件来自 `bakeLayer(PLAYER_INNER_ARMOR)`（`GeoArmorRenderer.java:94`），是原版内层骨架，`part.render` 只会画出**原版方盒**而非 geo 造型（袍摆/帽带/瞳孔全部缺失）。
- **不要**只依赖 `IClientItemExtensions` 拿 GeoArmorRenderer 后就直接 `renderToBuffer`：缺 `prepForRender` 会空渲染（见上）。
- **不要**对 geo 盔甲复用 BBS 的 trim/ghost/glint 层：会与 geckolib 自带 foil/RenderLayer 重复叠加。

---

## 4. 是否需要 GeckoLib 编译期依赖（Q4）

**是（推荐 compileOnly）**，若要直呼 `GeoRenderProvider` 等 API。三种可选方式：

1. **compileOnly + 独立桥接（推荐）**：
   - `new/build.gradle` 增加仓库+deps（ArmorRenderer 在 client source set → 用 `clientCompileOnly`）：
     ```gradle
     repositories { maven { url = "https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/" } }
     dependencies { clientCompileOnly "software.bernie.geckolib:geckolib-neoforge-1.21.1:4.7.5.1" }
     ```
   - Maven 坐标（ISB 实证）：`build.gradle:88` `implementation "software.bernie.geckolib:geckolib-neoforge-${minecraft_version}:${geckolib_version}"`，其中 `minecraft_version=1.21.1`、`geckolib_version=4.7.5.1`（`gradle.properties`）。仓库正是 Cloudsmith。`geckolib-neoforge-1.21.1` 在 maven 上有 4.6~4.9.2（本调查已从 metadata 拉取），铁魔法系钉死 **4.7.5.1**（Iron's Lib 硬依赖同版）。
   - **运行时护栏**：`GeoArmorSupport.isGeoArmor()` 先用 `Class.forName("software.bernie.geckolib.animatable.client.GeoRenderProvider") != null` 探测；为防极端环境还需把 geo 分支整体 `try/catch(Throwable)` 降级。`clientCompileOnly` 不出现在成品 jar → 无 geckolib 时 BBS 完全照旧。
2. **纯反射（零依赖增量）**：用 `Class.forName` + `getMethod(...).invoke(...)` 调 `GeoRenderProvider.of/getGeoArmorRenderer/prepForRender/renderToBuffer`。可行但脆弱（签名变化在编译期不可见），只适合"绝对不引依赖"的强约束。
3. **不引依赖则无法支持**：当前灾变任务 PRD（`.trellis/tasks/08-01-cataclysm-armor-compat/prd.md` 第 7、14、36 行）明确"不引入 GeckoLib / Iron's Lib 编译期依赖"，且父任务把 Iron's Lib/GeckoLib 兼容排除在外。因此 **GeoArmorRenderer 支持应列为独立规划的未来任务**（若采纳"有 geckolib 时装扮增强、无则原有行为"的软接入，Option 1 的隔离设计可以让它不触碰灾变任务的约束线）。

---

## 5. 接入设计总结（Q5）＋ 实施清单 / 验证

### 5.1 改动面

1. `new/src/client/java/mchorse/bbs_mod/cubic/render/vanilla/GeoArmorSupport.java`（新增，client source set，唯一 geckolib 直引点）：
   - `static boolean isAvailable()`（`Class.forName("software.bernie.geckolib.animatable.client.GeoRenderProvider")` 一次性缓存）；
   - `static boolean isGeoArmor(ItemStack)`（`isAvailable()` 且 `GeoRenderProvider.of(stack).getGeoArmorRenderer(null, stack, null, null) instanceof GeoArmorRenderer`）——注：此哨兵调用不带实体，安全（ISB 实现里 `renderer` 懒建）；
   - `static boolean renderGeoArmor(PoseStack, MultiBufferSource, LivingEntity, ItemStack, EquipmentSlot, HumanoidModel<?> base, int light)`：`getGeoArmorRenderer` → `prepForRender`（partialTick 传 BBS 侧可用的 partial/0）→ `renderToBuffer(matrices, null, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF)` → 返回渲染是否发生。
   - 整体 `try/catch(Throwable)` 返回 false 兜底。
2. `ArmorRenderer.java`：
   - `renderSlot` 顶部插 geo 分支（见 §3.2）；
   - 新增 `renderGeoArmor(...)`：先 `resolveLivingEntity`，null 直接 return；置 `this.skinning=false`。
3. （可选/复用）编辑器预览实体：geo 盔甲预览同样依赖 `resolveLivingEntity` 非空 → 复用灾变任务"StubEntity LivingEntity override + BaseFilmController.resolvePreviewLivingEntity"那套（该方案在 `design.md` 已设计，本子任务不改代码）。

### 5.2 行为矩阵

| 环境 | 行为 |
|---|---|
| BBS 无 geckolib ／ 无 ISB / Iron's Lib | `GeoArmorSupport.isGeoArmor` false → 原路径，100% 与现状一致 |
| 有 ISB（含 geckolib）｜ actor 回放（MCEntity） | geo 分支：正确 geo 造型 + 纹理 + 动画（实时时钟）；skinning 不生效 |
| 有 ISB（含 geckolib）｜ 编辑器预览（StubEntity 无 override） | `resolveLivingEntity==null` → 空渲染（与灾变"缺口 A"同源，需 StubEntity override 修复后才显示） |

### 5.3 风险与验证点

- **R1 缓冲绕过**：geo 渲染直写 levelRenderer 主 buffer（`GeoArmorRenderer.java:339-348`），绕过 `CustomVertexConsumerProvider`（`ModelFormRenderer.java:613`）与 `consumers.draw()` 延迟绘制。回放场景（世界渲染内）应无碍；编辑器/离屏渲染需实测排序与可见性。
- **R2 动画时钟**：无法随 BBS 电影时间 scrubbing（`GeoArmorRenderer.java:344,372`）。若产品要"关键帧定格地显示 geo 盔甲"，需额外研究（如暂停/覆盖 tick），当前结论：仅实时播放。
- **R3 版本锁定**：桥接类只按 4.7.5.1 API 写；若未来上游升到 4.8/4.9（1.21.1 也有），需回归（4.9.2 与 4.7.5.1 的 GeoArmorRenderer 签名一致，风险低；1.21.3+ 的 RenderState 系 API 是另一世界，勿混）。
- **V 验证清单**：① 无 geckolib 冒烟（行为不变）；② 有 ISB 回放 wizard_helmet/chestplate 显示 geo 造型与纹理；③ `wizard_hat` CLOTHING_VARIANT 变体（`GenericArmorModel.java:44-57`）正常；④ 编辑器预览（修好 StubEntity override 后）正常；⑤ foil 由 geckolib 自理不双画；⑥ 弯折轨道对 geo 盔甲回退为刚性且无顶点撕裂。

### 5.4 相关源码索引（本调查落盘，报告期后仍可查）

- `temp/geckolib-1.21.1/common/src/main/java/software/bernie/geckolib/renderer/GeoArmorRenderer.java`
- `temp/geckolib-1.21.1/common/src/main/java/software/bernie/geckolib/util/InternalUtil.java`
- `temp/geckolib-1.21.1/common/src/main/java/software/bernie/geckolib/animatable/client/GeoRenderProvider.java`
- `temp/geckolib-1.21.1/common/src/main/java/software/bernie/geckolib/animatable/GeoItem.java`、`animatable/SingletonGeoAnimatable.java`
- `temp/geckolib-1.21.1/common/src/main/java/software/bernie/geckolib/renderer/GeoRenderer.java`
- `temp/geckolib-1.21.1/neoforge/src/main/java/software/bernie/geckolib/mixin/client/HumanoidArmorLayerMixin.java`
- `temp/geckolib-1.21.1/neoforge/src/main/java/software/bernie/geckolib/platform/GeckoLibClientNeoForge.java`
- `temp/geckolib-4751/geckolib-neoforge-1.21.1-4.7.5.1.jar`（+ javap 验证）、`temp/geckolib-4751/maven-metadata.xml`
- `temp/neoforge-vanilla-src/net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer.java`
- `temp/neoforge-vanilla-src/net/neoforged/neoforge/client/ClientHooks.java`、`.../extensions/common/IClientItemExtensions.java`
- `temp/irons_lib-decompiled/io/redspace/ironslib/transmog/client/TransmogClientHandler.java`、`.../transmog/client/TransmogArmorRenderer.java`、`.../internal/mixin/HumanoidArmorLayerMixin.java`
- `temp/isb-files/*.java`（ISB 上游 1.21 分支要点文件）
- 网络：GeckoLib Wiki「Geckolib Armor (Geckolib4)」https://github.com/bernie-g/geckolib/wiki/Geckolib-Armor-(Geckolib4) 、「Geckolib 4 Changes」https://github.com/bernie-g/geckolib/wiki/Geckolib-4-Changes

## Caveats

- geckolib git 仓库**没有 tag**（`git ls-remote --tags` 为空），本调查以 clone 的 `1.21.1` 分支（现 HEAD ≈4.9.2 开发版）为源码参照，并用**真实下载的 4.7.5.1 jar + javap 逐签名核对**确保引用准确；文中 GeoArmorRenderer 行号来自 clone 源码（与 4.7.5.1 字节码行为一致，但行号是 4.9.2 盘的）。
- ISB 本调查用的是上游 `1.21` 分支（mod 3.16.2 / geckolib 4.7.5.1）；届时实施前应再 pull 该分支确认无接口漂移。
- "EquipmentLayerRenderer"在 1.21.1 **不存在**（1.21.2+ 才有），任务描述里的这一环节在 21.1.222 上实为 `HumanoidArmorLayer` + NeoForge mixin 完成。
- 编辑器/离屏预览下 geo 盔甲的主 buffer 直写行为**未经运行期验证**（本任务只读，未装配 ISB 实测），列为实施后首要验证项。