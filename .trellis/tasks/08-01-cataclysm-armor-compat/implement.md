# 灾变模组盔甲兼容 — 实施计划

## 前置（2026-08-02 更新）
- PRD 范围已更新：灾变盔甲 + **铁魔法（Iron's Spellbooks / GeckoLib）盔甲**，用户确认"铁魔法盔甲适配可以跟这个一起开始"。
- design.md 已明确灾变缺口 A/B；GeckoLib 接入方案详见 `research/geckolib-armor-integration.md`。
- 铁魔法机制（研究代理实证）：`ExtendedArmorItem extends ArmorItem implements GeoItem`，走 `GeoItem.createGeoRenderer → GeoRenderProvider.getGeoArmorRenderer`，**不走 IClientItemExtensions**。BBS 需在 `renderSlot` 顶部插 geo 分支。

## 实施顺序

### Step 1：`StubEntity` 增加 LivingEntity override（已实施）
- 新增字段 `private LivingEntity entityOverride;` + `setEntityOverride/getEntityOverride/isMcEntity/getMcEntity`。

### Step 2：`ArmorRenderer.resolveModel` 扩展（已实施）
- 现有 `MCEntity` 分支已改为也处理 `StubEntity`（带 override 时）；抽了 `resolveLivingEntity` 公共方法。
- 纹理解析改用 `ClientHooks.getArmorTexture`（复用 `resolveLivingEntity`）。

### Step 3：为 editor 预览注入 LocalPlayer（已实施）
- `BaseFilmController` 新增受保护钩子 `resolvePreviewLivingEntity()`（默认 null）。
- `FilmEditorController` 覆写返回 `Minecraft.getInstance().player`。
- `createEntities` 创建 StubEntity 后 `setEntityOverride(resolvePreviewLivingEntity())`。

### Step 4：Cursium ghost 特效层（已实施）
- `ArmorRenderer.renderSlot()` 尾部 Cursium 检测 + `renderGhost()`（energy-swirl + `FormTranslucentQueue`）。
- 判断通过 registry id 字符串匹配，无灾变编译期依赖。

### Step 5：铁魔法（GeckoLib）盔甲接入（已实施 2026-08-02）
- **依赖**：`build.gradle` 已加 Cloudsmith geckolib maven 仓库 + `clientCompileOnly "software.bernie.geckolib:geckolib-neoforge-1.21.1:4.7.5.1"`（compileOnly 不进成品 jar，无 geckolib 环境照旧）。
- **新增类** `GeoArmorSupport.java`（client source set，唯一 geckolib 直引点）：
  - `isAvailable()`：`Class.forName("...GeoRenderProvider")` 缓存。
  - `isGeoArmor(ItemStack)`：无实体哨兵调用 `getGeoArmorRenderer` 检查 instanceof GeoArmorRenderer。
  - `renderGeoArmor(...)`：getGeoArmorRenderer → prepForRender → renderToBuffer(matrices, null, light, NO_OVERLAY, 0xFFFFFFFF)；raw 强转解决泛型捕获问题；整体 try/catch 兜底。
- **`ArmorRenderer` 改动**：
  - `renderSlot` 顶部插 geo 分支：`GeoArmorSupport.isGeoArmor(itemStack)` 时 `renderGeoArmor(...)` + return。
  - 新增 `renderGeoArmor(...)`：`resolveLivingEntity`（null 直接 return）、置 `skinning=false`（geo 刚性渲染）。
  - geo 分支不叠加 trim/ghost/glint。
- **坐标偏移修复（2026-08-03，用户反馈"位置往下、大小偏小"）**：
  - 第一次尝试：`matrices.mulPose(Axis.XP.rotationDegrees(180F))` 抵消 BBS 的 ROTATE_X_180 → **用户反馈方向反了（倒过来）**。说明 BBS 的 ROTATE_X_180 与 GeoArmor 内部 scale(-1,-1,1) 组合后方向是正确的，不能抵消。
  - 修正方案：**保留 BBS 的 ROTATE_X_180，只做平移补偿** `matrices.translate(0, -1.5F, 0)`（向上 1.5 格），抵消 GeoArmor 内部 translate 落在翻转空间导致的向下偏移。
- **编译已验证**（`compileJava compileClientJava` 通过）。

### Step 6：编译与测试
- `new/` 编译通过（含 geckolib compileOnly）。
- 运行时装配（用户环境已有）：geckolib 4.9.2 + irons_lib + iron_spellbooks + cataclysm + lionfishapi。验证：
  - 无 geckolib 时行为不变（isGeoArmor false）
  - 有铁魔法时 wizard/法师盔甲显示 geo 造型与纹理
  - 编辑器预览（StubEntity override 生效后）正常

## 验收复核清单（评审用）
- 灾变自定义模型（Ignitium/Cursium/Bone_Reptile）在 BBS 盔甲轨道上渲染为自定义造型。
- 回放 actor 模式 + 编辑器预览均显示 mod 盔甲。
- Cursium ghost 半透明鬼影闪烁效果正常（含光影下）。
- 铁魔法（GeckoLib）盔甲显示 geo 造型与纹理。
- 原版盔甲、无 mod 环境行为完全不变。
- geckolib 为 compileOnly（非硬依赖），`new/` 无 geckolib 环境能编译运行。

## 回滚点
- 全部为增量：`StubEntity` 加字段/方法、`resolveModel` 加分支、`createEntities` 注入、`renderSlot` geo 分支。可逐步回退。
- GeoArmorSupport 整体 catch 兜底，异常时回退原路径。
