# 执行计划：盔甲跟随骨骼弯曲

## 步骤

### S1 扩展 `ArmorSlot` 数据结构
`new/src/client/java/mchorse/bbs_mod/cubic/model/ArmorSlot.java`
- 新增下段骨骼名 + 过渡区间起止字段（默认为空 / 0）
- `fromData` map 分支读取新字段，用带默认值的读取方式
- 字符串分支保持不变（走推断）

**验证**：编译通过。

### S2 新增蒙皮 `VertexConsumer` 包装类
`new/src/client/java/mchorse/bbs_mod/cubic/render/vanilla/SkinningVertexConsumer.java`（新文件）
- 实现 `VertexConsumer`，持有真实 consumer + 上下两个 `Matrix4f` + 过渡区间
- 覆盖 `Cube.compile` 实际调用的 11 参 `addVertex`：算权重 → 双矩阵变换 → lerp 位置与法线 → 转发
- 其余 `VertexConsumer` 方法委托给真实 consumer

**验证**：编译通过；确认覆盖的 `addVertex` 重载签名与 1.21.1 一致。

### S3 `ArmorRenderer` 接入蒙皮路径
`new/src/client/java/mchorse/bbs_mod/cubic/render/vanilla/ArmorRenderer.java`
- `renderArmorSlot` 增加下段矩阵与过渡区间参数（重载或新方法，保留原签名以免影响其他调用方）
- `renderArmorPart` / `renderTrim` / `renderGlint` 三处统一：有下段矩阵 → 单位 `PoseStack` + `SkinningVertexConsumer`；否则原 `part.render`

**验证**：编译通过；非 bends 路径代码分支未变。

### S4 `ModelFormRenderer.renderArmor()` 解析下段骨骼
`new/src/client/java/mchorse/bbs_mod/forms/renderers/ModelFormRenderer.java:587-615`
- 按 `ArmorType` 的命名约定表解析下段骨骼名（config 显式指定优先）
- `this.bones.get(lowerName).matrix()` 为 null → 走原路径
- 非 null → 构造上下两套 stack（各自 `transform` + `ROTATE_X_180`），调新的蒙皮渲染入口

**验证**：编译通过。

### S5 模组自定义盔甲支持
`ArmorRenderer.renderArmorSlot()`
- 接入 `IClientItemExtensions#getHumanoidArmorModel`，**先核实 NeoForge 1.21.1 确切签名**
- 取到模组模型则替代 `innerModel`/`outerModel`，取不到保持原样

**验证**：编译通过。

### S6 四个 bends 模型写入显式分段配置
`new/src/client/resources/assets/bbs/assets/models/player/{steve_bends,alex_bends,steve_bends_3d,alex_bends_3d}/config.json`
- `armor_slots` 各槽位补下段骨骼与过渡区间
- 保持 JSON 缩进风格与现有一致（Tab 缩进）

**验证**：JSON 可解析；编译通过。

### S7 全量构建验证
```bash
cd new && ./gradlew build > build-armor.log 2>&1; echo "REAL_EXIT=$?"
```

### S8 运行时坐标空间回归修复
- [x] 将下段矩阵从 `jointDelta * completedUpper` 改为 `renderBase * jointDelta * armorAttachment` 顺序
- [x] 上下段复用同一个盔甲挂点、附加 transform 与 `ROTATE_X_180` 应用函数
- [ ] 重新运行 `compileClientJava` 与全量 `build`
- [ ] 入游戏保持模型静止并移动/旋转摄像机，盔甲不得跟随摄像机旋转
- [ ] 弯曲手肘、膝盖和躯干，盔甲表面应连续弯曲而非向固定方向伸直

## 验证红线

⚠ **绝不能**用 `./gradlew ... | tail -N` 判断成败 —— 管道退出码取自 `tail`（恒 0），会吞掉 `BUILD FAILED` 造成假绿（本仓库有事故记录）。

**判绿标准**：真实 exit code 为 0 **且** 日志中出现 `BUILD SUCCESSFUL`，两者同时成立。

## 回滚点

- S1–S2 是纯新增，无回滚风险
- S3–S4 若蒙皮效果异常：下段矩阵解析处直接返回 null 即可全量退回原行为
- S6 若配置导致异常：删除 config 中新字段，退回命名约定推断

## 不做

- 不新增 mixin / accessor / AT
- 不写自动化测试（用户确认编译通过即可，且裸 JVM 测试会触发 `BBSMod` 静态初始化崩溃）
- 不动骨骼几何资源（`model.bbs.json` / `.bbmodel` 保持原样）
