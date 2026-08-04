# 灾变模组盔甲兼容

## Goal

让 BBS `new/` 的 `item_head/item_chest/item_legs/item_feet` 盔甲关键帧轨道能够正常渲染 L_Ender's Cataclysm（灾变）模组的自定义盔甲模型，而不只是显示成原版盔甲形状。

> 范围：Iron's Lib / GeckoLib 盔甲兼容不在本任务（见父任务 prd.md）。本任务只针对 Cataclysm（依赖 Lionfish-API 的 1.21.1 NeoForge 版本）的盔甲渲染兼容。

## Requirements

- 让灾变盔甲模型在 BBS 的盔甲轨道上正常显示（正确的自定义模型、纹理解析）。
- 覆盖场景：**回放/actor 模式 + 编辑器预览**（用户确认"编辑器也支持 mod 盔甲"）。
- Cursium（烁镍）套装的**幽灵半透明特效层**也需正常渲染（灾变魔改个性效果）——需要参考灾变的半透明逻辑做进 BBS，并适配光影（用户确认"纳入本轮，参考灾变半透明逻辑做进 BBS 并顺带适配"）。
- 不引入 GeckoLib / Iron's Lib 编译期依赖。

## 关键技术约束（研究结论）

灾变（Cataclysm 3.32, 1.21.1)盔甲机制已通过反编译确认：

1. **模型**：所有灾变盔甲模型 `extends HumanoidModel`（如 `Cursium/Ignitium/*_Model`）。
2. **物品**：`extends Cataclysm_Armor extends ArmorItem`，`getEquipmentSlot()` 正常。
3. **自定义模型 hook**：`CustomArmorRenderProperties.getHumanoidArmorModel()` 返回对应自定义 `HumanoidModel` —— **BBS 的 `ArmorRenderer.resolveModel()` 已调用此 hook**（`ArmorRenderer.java:226`）。
4. **Cursium 幽灵特效**：`CustomArmorRenderProperties.renderCustomArmor()` 在 `HumanoidArmorLayerMixin` 中注入，额外渲染一层半透明 ghost。BBS 的 form 渲染不走 `HumanoidArmorLayer`，**该特效层缺失**。

因此本任务的核心缺口是：

- **缺口 A（预览）**：`BaseFilmController.createEntities` / 编辑器用 `StubEntity`，而 `resolveModel` 要求 `entity instanceof MCEntity` 才能拿 `LivingEntity` 调 `getHumanoidArmorModel`。需让 StubEntity 预览场景也能拿到一个 `LivingEntity`（如主玩家 `LocalPlayer`），使 mod 盔甲模型可解析。
- **缺口 B（Cursium 幽灵层）**：需在 BBS 的盔甲渲染路径补上灾变 `renderCustomArmor` 的特效层（Cursium ghost）。

## Acceptance Criteria

- [ ] 灾变盔甲（Ignitium / Cursium / Bone_Reptile 等 `getHumanoidArmorModel` 自定义模型）在 BBS 盔甲轨道上显示自定义造型与正确纹理。
- [ ] 回放 actor 模式与编辑器预览均显示 mod 自定义盔甲。
- [ ] Cursium 套装的幽灵半透明特效层正常渲染（含能源旋涡 semi-transparent + 深度测试适配），且**在多数光影下（含 Iris/Sodium）显示正常**。
- [ ] 原版盔甲 / 无 mod 环境的渲染行为完全不变（backward compatible）。
- [ ] 不引入 GeckoLib / Iron's Lib 依赖，`new/` 在无灾变模组环境仍能编译运行。
- [ ] `new/` 编译通过。

## Notes

- Lionfish-API 本身无盔甲渲染代码（纯动画库），灾变盔甲全部走标准 `IClientItemExtensions.getHumanoidArmorModel()`，因此接入点是 BBS 侧的处理，无需改动灾变/Lionfish。
- 验证时需在运行时安装 Cataclysm + Lionfish-API（见 implement.md 的测试装配方案）。
