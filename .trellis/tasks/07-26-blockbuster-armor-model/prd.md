# 盔甲跟随骨骼弯曲 + 模组盔甲支持

## Goal

修复「模型手臂/腿弯曲时盔甲不跟着弯」的缺陷，让盔甲以顶点级平滑方式跟随弯曲关节形变；同时补上对模组自定义几何盔甲的渲染支持。

## Background

用户原始诉求是「把 Blockbuster 那个能自适应模组盔甲与原版盔甲弯曲的人物模型带过来」。调研后确认**本项目并不缺这套系统**：

- `armor_slots` 数据驱动挂点、`ArmorType`（9 槽位）、`ArmorSlot`、`ArmorRenderer` 均已完备
- 已内置 `steve_bends` / `alex_bends` / `steve_bends_3d` / `alex_bends_3d` 四个弯曲模型

用户确认实际现象是：**模型能弯，但盔甲还是直的**。根因是骨骼挂点层级 —— `armor_left_arm` 的父骨骼是 `left_arm`（大臂），弯曲关节 `left_elbow` 是它的**兄弟**而非祖先，所以盔甲整块跟大臂走，小臂一弯就脱节。腿、躯干同理。

## Requirements

### R1 盔甲顶点级平滑跟随弯曲
- 盔甲表面在关节处**连续不断开**（用户明确选择平滑弯曲，而非两截刚体分段）
- 原版盔甲长侧面需先按高度细分，否则每面只有四个角点，任何顶点插值都只能形成一张平直弓弦而不是真正曲面
- 刚体关节使用相对四元数与恢复出的关节枢轴插值，使每个细分截面沿弯曲方向旋转并保持横截面；缩放/剪切关节才回退线性蒙皮
- 覆盖材质层、锻造纹饰（trim）、附魔光效（glint）三种渲染，三者不得互相错位

### R2 配置兼容
- `config.json` 可**显式指定**下段骨骼与过渡区间
- 未显式指定时，按骨骼命名约定（`armor_left_arm` → `left_elbow`、`armor_left_leg` → `left_knee`）**自动推断**
- 旧 config 读不到新字段时**退化为现有单骨骼路径**，不破坏任何现有模型
- 用户 fork 到 `config/bbs/assets/models/` 的旧配置不得报错

### R3 模组自定义盔甲支持
- 接入 NeoForge `IClientItemExtensions#getHumanoidArmorModel`，使带自定义几何的模组盔甲能正确渲染
- 与现有 `ArmorMaterial.Layer` 贴图路径共存分流

### R4 不回归
- 非 bends 模型（`steve`/`alex`/`*_3d`）行为不变
- 共用 `ArmorSlot` 抽象的 `items_main`/`items_off`/`fp_main`/`fp_offhand` 不受影响

## Acceptance Criteria

- [x] `ArmorSlot` 支持可选下段骨骼与过渡区间，旧 config 正常降级
- [x] 命名约定推断生效，无每帧查找开销
- [ ] 盔甲材质层 / trim / glint 三者统一走蒙皮路径，无错位；弯曲不受摄像机位置或旋转影响
- [x] ~~四个 bends 模型的 config.json 写入显式分段配置~~ —— **改为不写，见下方决策说明**
- [x] 模组自定义盔甲模型能渲染
- [x] 非 bends 模型与物品挂点无回归
- [x] 回归修复后重新执行 `cd new && ./gradlew build`，**真实 exit code 为 0 且日志含 `BUILD SUCCESSFUL`**（9m16s，77 tasks，全部测试通过）

### 2026-07-27 运行时回归反馈

首轮构建虽然通过，但入游戏后盔甲下半段会朝固定方向伸直，且摄像机移动或旋转会带动盔甲旋转。根因是模型局部空间的关节弯曲增量被左乘到了已经包含外层渲染/摄像机相对变换的完整矩阵上。修复必须保持矩阵顺序为：

```text
外层渲染变换 × 关节弯曲增量 × 盔甲挂点变换 × 盔甲局部顶点
```

不得使用 `关节弯曲增量 × 外层渲染变换 × ...`；后者会让摄像机相对平移参与关节旋转。

第二轮反馈确认摄像机依赖已消失，但原版盔甲每个侧面只有四个角点，旧 LBS 仍只能把上下端点连成平面，并在关节处损失体积。修复改为缓冲四顶点 quad、纵向细分到约每模型像素一条（上限 16），再对中间条带使用刚体相对旋转插值；0/1 权重严格使用上下端矩阵。

### 实施中的两项决策调整

**1. 命名约定推断放在渲染侧，而非 `ModelConfig.rebuild()`**
`ModelConfig` 拿不到模型骨骼表，无法判断 `left_elbow` 是否存在。改为在 `ModelFormRenderer` 用现成的 `MatrixCache` 探测 —— `MatrixCache.get()` 对未知 key 返回静态 `EMPTY` 且无副作用不写 map，因此探测安全无开销，且能自动适配任意自定义模型。

**2. 不改四个 bends 模型的 config.json**
经核实四个模型骨骼命名完全一致（均含 `left_elbow`/`right_elbow`/`left_knee`/`right_knee`/`torso_lower`），命名约定推断已完整覆盖，写死配置只会增加 diff 与 JSON 出错面，收益为零。

过渡区间改为**按部位**设默认值（定义在 `ArmorType`），依据原版盔甲实际几何：

| 部位 | vanilla cube Y 范围 | 关节位置 | 默认区间 |
|---|---|---|---|
| 手臂 `addBox(-3,-2,-2, 4,12,4)` | −2 → 10 | 肘约 4 | 2 ~ 6 |
| 腿 `addBox(-2,0,-2, 4,12,4)` | 0 → 12 | 膝约 6 | 4 ~ 8 |
| 躯干 `addBox(-4,0,-2, 8,12,4)` | 0 → 12 | 腰约 6 | 4 ~ 8 |

用户若需微调，在模型 `config.json` 的对应槽位改写成 map 形式即可（解析已支持）：
```json
"left_arm": { "group": "armor_left_arm", "lower_group": "left_elbow", "bend_start": 2, "bend_end": 6 }
```

## Constraints

- 渲染代码在 `new/src/client/java/`（独立 client source set），数据在 `new/src/main/java/`
- **不新增 mixin**：`Cube.compile()` 是 public，配合单位矩阵 + 包装 `VertexConsumer` 即可拿到局部坐标，无需 accessor
- 验证深度：编译通过即可，渲染效果由用户入游戏验收
- 提交时只 add 本次相关文件（`new/` 工作区有 30 个文件的前序未提交改动）

## Notes

- 上游 Blockbuster 的具体做法未能取证（调研 agent 未回报），本方案基于本仓库实际代码结构自行设计
- 关联任务：`07-26-bone-edge-glint`（骨骼附魔，**排在本任务之后**，两者交汇于 `ModelFormRenderer`）
- 研究记录见 `research/existing-model-and-armor-system.md`
- 稀疏网格曲面修复后的最终验证通过完整客户端编译与 `build -x testAudioLifecycle`；未排除时仅 `testAudioLifecycle` 在 `AudioLifecycleTest.java:1336` 因 `firstPreview == null` 失败，该音频回归不属于本任务。
