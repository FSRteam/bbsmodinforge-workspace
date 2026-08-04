# Design: 附魔 UI 文本精简与 alpha 光效修复

## 根因分析

### 文本问题
- 纯文案改动，`zh_cn.json` / `en_us.json` 三个 key 的值即可；UIKeys / Java 引用无需改。
- 切换按钮 4 个选项 key 不变，只改值。

### alpha 光效失效根因
- `energySwirl` RenderType 使用 `ADDITIVE_TRANSPARENCY`（`blendFunc(ONE, ONE)`），混合完全忽略 alpha。
- 1.21.1 `rendertype_energy_swirl.fsh`：
  ```glsl
  vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
  if (color.a < 0.1) { discard; }
  fragColor = color * linear_fog_fade(...);
  ```
  `color.rgb` 只乘 `vertexColor.rgb`，**不乘 alpha**；加法混合里 `color.a` 又不参与输出。
- 结果：非装甲管线（无 Iris 的 整体/边缘）下，`glintColor.a` 被一路传到顶点色，但渲染端完全不消费 → 滑块无效果。
- 原版模式 / Iris 世界渲染走 `armorPipeline`：`RenderSystem.setShaderColor(color.r,g,b,a)` 写进 `ColorModulator`，armor glint shader 用 `TRANSLUCENT_TRANSPARENCY` 混合 → alpha 已生效，**不动**。

## 修复策略：能量漩涡路径 pre-multiply

在非 armor 的 energy_swirl 路径，把 **用户 alpha pre-multiply 进 RGB**，顶点 alpha 通道改为纯几何掩码（边缘用 / 整体用 1）。加法混合的可见贡献 `texture.rgb * vertexColor.rgb * ColorModulator.rgb` 因此随 alpha 线性缩放，0 时完全不可见。

### 触点
1. **`GlintRenderState.renderVao`**（VAO 路径：形态整体 + 模型骨骼 整体）
   - armor 分支不变。
   - 非 armor：`ModelVAORenderer.render(..., color.r*color.a, color.g*color.a, color.b*color.a, 1F, ...)`。
2. **`CubicGlintCubeRenderer.renderGroup`**（CPU 路径：模型 整体 fallback + 全部 边缘）
   - `setColor` 改为 pre-multiplied rgb，`a=1F`。
   - `emit` 里 `this.a * group.color.a * vertexAlpha` → alpha 通道只剩几何掩码（边缘的视角强度），rgb 已含用户 alpha 缩放。
3. **`GlintTintVertexConsumer`**（captured 路径：方块/物品 整体/边缘）
   - 构造时把 rgb pre-multiply 用户 alpha；alpha 字节置 0（alpha==0）或 255。
   - `edgeColor` 逻辑不变：`alpha = round(255 * intensity)` 几何掩码 + `color & 0xFFFFFF`（pre-multiplied rgb）。

### 不变
- armorPipeline 分支（原版 / Iris 世界）完全不动 → 光影下不回归。
- `GlintRenderState.drawMesh/drawBuffer` 不改（mesh 顶点色已在上游烘焙好，非 armor 只用烘焙色）。
- 数据模型、序列化、`glintMode` 索引、`glintSpeed`、`glintTransform` 全部不动。
- 不新增字段、不扩 alpha 范围。

## 数据流（修复后，整体模式）
```
用户 alpha=a
→ 顶点 rgb' = rgb * a, 顶点 alpha=1
→ energy_swirl fsh: color.rgb = texture.rgb * rgb' * 1
→ ADDITIVE(ONE,ONE) 加法混合输出 rgb' 加成
→ a 线性控制光效强弱, a=0 时 rgb'=0 不可见 ✓
```

## 兼容性
- 已存形态：颜色数据仍是原 ARGB，读回不变。
- 边缘模式：几何掩码从「用户 alpha 与视角强度耦合」解耦为「纯视角几何」，band 宽度不再随 alpha 变，强度由 alpha 控制——更符合直觉，属本次意图内的行为修正。
