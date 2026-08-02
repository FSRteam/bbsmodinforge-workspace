# 灾变模组盔甲兼容 — 实施计划

## 前置
- PRD 已确认范围：只做灾变盔甲（Iron's/GeckoLib 后置）。
- design.md 已明确两个缺口与方案。

## 实施顺序

### Step 1：`StubEntity` 增加 LivingEntity override
- 新增字段 `private LivingEntity entityOverride;`
- 新增 `setEntityOverride / getEntityOverride / isMcEntity/getMcEntity`（暴露 LivingEntity）。

### Step 2：`ArmorRenderer.resolveModel` 扩展
- 将现有 `MCEntity` 分支改为也处理 `StubEntity`（带 override 时）。
- 无 LivingEntity 时维持返回 original（backward compatible）。

### Step 3：为 editor 预览注入 LocalPlayer（可覆写钩子）
- `createEntities` 被 `FilmEditorController` 与 `WorldFilmController` 共用，不能无条件注入 LocalPlayer（world 回放播放时 actor 才是主体）。
- 在 `BaseFilmController` 新增受保护的钩子 `resolvePreviewLivingEntity()`，默认返回 `null`。
- `FilmEditorController` 覆写返回 `Minecraft.getInstance().player`。
- `createEntities` 创建 StubEntity 后调用 `setEntityOverride(resolvePreviewLivingEntity())`。
- 仅 editor 注入，world 播放不注入。

### Step 4：Cursium ghost 特效层（方案 B，用户确认纳入本轮）
- 在 `ArmorRenderer.renderSlot()` 尾部追加 Cursium 检测。
- `ArmorRenderer` 新增 `renderGhost()` 方法：用 `RenderType.energySwirl()` + ghost 纹理（`cataclysm:textures/armor/cursium_armor_ghost.png`）+ 半透明状态，复用 BBS `GlintRenderState` 类似的 energy-swirl 管线。
- Cursium 判断通过物品 registry id（`cataclysm:cursium_helmet/chestplate/leggings/boots`）字符串匹配，无灾变编译期依赖。
- ghost 渲染通过 `FormTranslucentQueue` 提交以正确排序。
- 注：此步骤在运行时需要灾变 ghost 纹理在资源包中可用。

### Step 5：编译与测试
- `new/` 编译通过。
- 运行时装配 Cataclysm + Lionfish-API，验证：
  - actor 回放 + 编辑器预览的灾变自定义盔甲模型
  - Cursium ghost 半透明闪烁效果
  - 有/无光影下均正常

## 验证命令
- 编译：项目既定 NeoGradle 编译命令。
- 运行时冒烟：dev mods 目录放 Cataclysm + Lionfish-API，触发 BBS 盔甲轨道。

## 验收复核清单（评审用）
- 灾变自定义模型（Ignitium/Cursium/Bone_Reptile）在 BBS 盔甲轨道上渲染为自定义造型。
- 回放 actor 模式 + 编辑器预览均显示 mod 盔甲。
- Cursium ghost 半透明鬼影闪烁效果正常（含光影下）。
- 原版盔甲、无灾变环境行为完全不变。
- 未引入 GeckoLib / Iron's Lib 依赖。

## 回滚点
- 全部为增量：`StubEntity` 加字段/方法、`resolveModel` 加分支、`createEntities` 注入。可逐步回退。
- 若注入 LocalPlayer 引发预览异常，可先只回退 Step 3（保留 resolveModel 扩展，等确认注入方案）。
