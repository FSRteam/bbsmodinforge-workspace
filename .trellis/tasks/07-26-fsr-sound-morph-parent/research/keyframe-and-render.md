# Research: BBS Mod 关键帧系统与世界内可视化

- **Query**: 摸清 BBS mod (NeoForge 1.21.1, 源码路径 `new/src/`) 的 (A) 关键帧/轨道系统 与 (B) 世界内调试可视化绘制方式
- **Scope**: Internal (code search in NeoForge project)
- **Date**: 2026-07-26

## A. 关键帧系统

### 1. 关键帧核心 API

#### 类路径与核心数据结构

| 文件路径 | 描述 |
|---------|------|
| `new/src/main/java/mchorse/bbs_mod/utils/keyframes/Keyframe.java:17-80` | 单个关键帧容器：存储 `tick`（时间戳浮点数）和泛型值 `T`；支持贝塞尔手柄 (lx, ly, rx, ry)；每个关键帧包含插值实例 |
| `new/src/main/java/mchorse/bbs_mod/utils/keyframes/KeyframeChannel.java:21-100` | 关键帧通道：存储有序的 `Keyframe<T>` 列表；提供 `interpolate(float ticks, T orDefault)` 方法按时间求值 |
| `new/src/main/java/mchorse/bbs_mod/utils/keyframes/KeyframeSegment.java:12-80` | 两个相邻关键帧间的插值段：存储 `a`, `b` 及其邻帧 `preA`, `postB`；计算 `x` 进度比（0-1）；调用工厂的 `interpolate()` 求值 |
| `new/src/main/java/mchorse/bbs_mod/utils/interps/Interpolation.java:13-80` | 插值策略容器：持有 `IInterp` 实现及其参数 `v1`, `v2` 等；通过 `interpolate(InterpContext)` 执行插值算法 |

#### 核心 API（按 tick 求值）

**KeyframeChannel 接口**:
- `T interpolate(float ticks)` — 返回该通道在指定 tick 的插值值（按工厂类型使用默认值 0/0.0/false）
- `T interpolate(float ticks, T orDefault)` — 无关键帧时返回 orDefault
- `KeyframeSegment<T> find(float ticks)` — 查找包含该时间的关键帧段并初始化

**求值流程**:
1. `interpolate()` 调用 `findSegment(ticks)` 查找相邻关键帧对 [a, b]
2. `KeyframeSegment.setup(ticks)` 计算段内进度 `x = (ticks - a.tick) / duration`，范围 [0, 1]（clamped）
3. `createInterpolated()` 调用 `factory.interpolate(preA, a, b, postB, interpolation, x)` 进行二阶/三阶样条插值
4. 返回 `factory.copy()` 的结果（避免修改原值）

#### 支持的插值类型与值类型

**IInterp 实现**（在 `Interpolations.MAP` 中注册）：
- 线性 (LINEAR)、平滑阶跃、贝塞尔曲线等（通过 `EasingArgs v1, v2` 参数配置二阶/三阶控制点）

**IKeyframeFactory 实现**（`KeyframeFactories.java:6-68`）：
- **数值类型**: `DOUBLE`, `FLOAT`, `INTEGER`, `LONG` — 使用 `factory.interpolate()` 进行三阶样条或贝塞尔插值
- **向量类型**: `VECTOR3F`, `VECTOR3F_SCALE`, `VECTOR4F` — 按分量插值
- **布尔型**: `BOOLEAN` — 不插值，始终返回 `a` 的值（阶跃型）
- **字符串/数据**: `STRING`, `LINK`, `BLOCK_STATE`, `ITEM_STACK`
- **复合型**: `COLOR`, `TRANSFORM`, `POSE_TRANSFORM`, `POSE`, `IK`, `PHYSICS`, `WIND`, `SHAPE_KEYS`, `PARTICLE_SETTINGS`, `ACTIONS_CONFIG`, `ANCHOR`
- **无插值约束**: 每个工厂可自定义 `interpolate()` 行为

---

### 2. Film/Replay 系统中的属性绑定

#### Film 与 Replay 的结构

| 文件 | 描述 |
|------|------|
| `new/src/main/java/mchorse/bbs_mod/film/Film.java:22-68` | 电影总容器：持有 `Replays replays`（角色列表）、`Clips camera`（摄像机轨迹）、Inventory、HP 等全局属性 |
| `new/src/main/java/mchorse/bbs_mod/film/replays/Replay.java:25-80` | 单个角色/物体实例：持有 `ValueForm form`（形态定义）、`ReplayKeyframes keyframes`（动画轨道）、`FormProperties properties`（形态属性值）、`Clips actions`（动作/事件轨道） |
| `new/src/main/java/mchorse/bbs_mod/film/replays/ReplayKeyframes.java:20-100` | 预定义关键帧通道集合：包括位置 (x, y, z)、旋转 (yaw, pitch, headYaw, bodyYaw)、速度 (vX, vY, vZ)、操作 (sneaking, sprinting)、控制器 (stick_lx/ly, stick_rx/ry, trigger_l/r)、装备 (item_main_hand 等)、额外轨道 (extra1_x/y, extra2_x/y) |

#### 属性到通道的绑定

**直接字段绑定**（Replay 中的关键帧通道）:
- `Replay.keyframes.x`, `.y`, `.z` 等是预声明的 `KeyframeChannel<Double>` 字段，在 `ReplayKeyframes` 构造时通过 `this.add()` 注册为子值
- 每个通道的 ID（如 "x", "yaw"）与通道字段名对应
- 回放时，`Replay.interpolate(tick)` 遍历所有已填充的通道，调用 `channel.interpolate(tick)` 求值，写回实体

**示例 1：位置通道**
```java
// ReplayKeyframes 中（第 33-35 行）
public final KeyframeChannel<Double> x = new KeyframeChannel<>("x", KeyframeFactories.DOUBLE);
public final KeyframeChannel<Double> y = new KeyframeChannel<>("y", KeyframeFactories.DOUBLE);

// 回放时在 BaseFilmController 中
double posX = replay.keyframes.x.interpolate(currentTick, 0.0);
double posY = replay.keyframes.y.interpolate(currentTick, 0.0);
entity.setPos(posX, posY, posZ);
```

**示例 2：形态属性通道**（通过 FormProperties）
- `Replay.form` 持有形态的 `Pose` 等可动画属性
- `Replay.properties` 的 `FormProperties` 类为每个形态属性创建一个 `KeyframeChannel<?>` 实例
- 编辑器在运行时查询形态的字段，为其创建关键帧轨道（见下文 UIKeyframeSheet）

---

### 3. 关键帧编辑器 UI 架构

#### Dope Sheet 与曲线编辑

| 文件 | 描述 |
|------|------|
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/graphs/UIKeyframeDopeSheet.java` | Dope sheet（时间线视图）：在横轴表示时间 tick，纵轴列举所有轨道；鼠标拖拽关键帧修改时间戳 |
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/graphs/UIKeyframeGraph.java` | 曲线编辑器（Graph 视图）：在 XY 网格显示某一轨道的插值曲线，可拖拽贝塞尔手柄调整形状 |
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/graphs/UIVector3KeyframeGraph.java` | 三维向量曲线编辑器：同时显示 X, Y, Z 三条曲线 |

#### 轨道管理与添加新属性

**UIKeyframes 的轨道组织**:
- `UIKeyframes` 持有 `UIKeyframeDopeSheet dopeSheet` 作为主视图
- Dope sheet 中的轨道通过 `List<UIKeyframeSheet>` 构成
- `UIKeyframeGroup` 用于折叠/展开轨道分组

**为新属性添加轨道的步骤**:

1. 创建 `KeyframeChannel<T>` 实例（与目标类型对应工厂匹配）
   ```java
   // 示例：为 morph 属性添加 double 轨道
   KeyframeChannel<Double> morphAlpha = new KeyframeChannel<>("alpha", KeyframeFactories.DOUBLE);
   ```

2. 在编辑器中注册为 `UIKeyframeSheet`（客户端）
   ```java
   UIKeyframeSheet sheet = new UIKeyframeSheet(
       "alpha",                           // 轨道 ID
       IKey.constant("Alpha"),            // UI 标签
       Colors.GREEN,                      // 轨道颜色
       false,                             // 不分隔符
       morphAlpha,                        // KeyframeChannel 实例
       property                           // 绑定的属性值（可为 null）
   );
   dopeSheet.add(sheet);                  // 添加到 dope sheet
   ```

3. 设置工厂（如属性无标准类型）
   ```java
   UIKeyframeFactory factory = new UIDoubleKeyframeFactory(keyframe, uiKeyframes);
   editor.setFactory(factory);
   ```

#### 属性 ↔ 轨道的关联

**UIKeyframeSheet 字段**:
- `property: BaseValueBasic` — 指向源属性（如 `Replay.keyframes.x`）；为 null 时表示虚拟轨道（IK、物理控制等）
- `isBoneTrack: boolean` — 标记是否为骨骼变换轨道
- `seed: Supplier<Object>` — 新关键帧的初始值（对无源属性使用，避免空容器）

**编辑时的双向同步**:
- 选中关键帧 → UIKeyframeFactory 显示编辑面板，用户修改值
- 保存 → 回调 `property.set(newValue)` 更新源值，同时写入 `keyframe.setValue()`

---

### 4. 布尔/触发型关键帧

#### 布尔关键帧的设计

**BooleanKeyframeFactory** (`new/src/main/java/mchorse/bbs_mod/utils/keyframes/factories/BooleanKeyframeFactory.java:34-37`):
```java
public Boolean interpolate(Boolean preA, Boolean a, Boolean b, Boolean postB, IInterp interpolation, float x)
{
    return a;  // 不插值，始终返回 a 的值（阶跃）
}
```

**结论**：
- 布尔关键帧**不支持插值**，始终返回当前段的起始关键帧值
- 这实质上是"触发型"——在 tick 进入新段时，值切换到该段的 `a.value`
- **无事件回调机制**：没有"在 tick 触发一次事件"的设计；应用逻辑需要自行检测布尔值的边界（false → true 或 true → false）

#### 用途示例
- `Replay.keyframes` 中不存在预定义的布尔通道，但可通过 `FormProperties` 为形态的布尔属性创建通道
- 常见于控制动画分支的开/关标志（如"是否启用 IK"）

**缺陷**：BBS mod **未提供事件型关键帧**（"在某帧触发一次"机制）；所有数据型关键帧（包括布尔）都是**状态型**，依赖上层逻辑轮询检测状态变化

---

## B. 世界内调试可视化绘制

### 5. 调试几何体绘制工具

#### 核心渲染基础设施

| 文件 | 描述 | 主要方法 |
|------|------|--------|
| `new/src/client/java/mchorse/bbs_mod/graphics/Draw.java:17-62` | 静态工具类：提供世界空间几何体绘制 | `renderBox()` 绘制空心线框盒；`fillTexturedNormalQuad()` 绘制纹理四边形；`fillQuad()` 填充平面四边形 |
| `new/src/client/java/mchorse/bbs_mod/graphics/line/LineBuilder.java:22-80` | 线段构建器：用于绘制平滑的粗线条 | `add(x, y)` 追加点；`build()` 生成线段点列表；`render(Batcher2D, ILineRenderer)` 渲染到 2D 批处理器 |
| `new/src/client/java/mchorse/bbs_mod/ui/utils/Gizmo.java:41-100` | 3D 变换小工具：用于在编辑器中拖拽对象 | `render()` 绘制坐标轴、旋转环、缩放立方体；`pick()` 检测鼠标点击 (Stencil ID) |
| `new/src/client/java/mchorse/bbs_mod/client/BBSRendering.java:66-100` | 全局渲染管理：处理 RenderSystem 状态、Framebuffer 切换 | 暴露 `RenderSystem` 工具方法 |

#### 低层顶点渲染 API

**使用的 Minecraft/LWJGL 类**:
- `com.mojang.blaze3d.systems.RenderSystem` — OpenGL 状态管理（Depth、Blend、Stencil 等）
- `com.mojang.blaze3d.vertex.Tesselator` — 顶点缓冲生成器实例
- `com.mojang.blaze3d.vertex.BufferBuilder` — 顶点构建器：通过 `addVertex()` 添加顶点，`setColor()` 设定 RGBA，`setNormal()` 设定法线
- `com.mojang.blaze3d.vertex.VertexFormat` — 顶点格式（`POSITION_COLOR`, `POSITION_TEX_COLOR_NORMAL` 等）
- `com.mojang.blaze3d.vertex.PoseStack` — 矩阵变换栈：`pushPose()`, `translate()`, `scale()`, `popPose()`
- `net.minecraft.client.renderer.GameRenderer` — 着色器获取：`getPositionColorShader()` 等

**绘制流程范例**（摘自 Draw.java 第 38-59 行）:
```java
// 1. 创建顶点构建器
BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

// 2. 设置着色器
RenderSystem.setShader(GameRenderer::getPositionColorShader);

// 3. 添加顶点
Matrix4f matrix = stack.last().pose();
builder.addVertex(matrix, x, y, z).setColor(r, g, b, a);

// 4. 上传并渲染
BufferUploader.drawWithShader(builder.buildOrThrow());
```

#### 可用的预制绘制函数

**Draw.java 中的工具**:
- `renderBox(PoseStack, x, y, z, w, h, d, r, g, b, a)` — 绘制线框立方体边界（12 条边）
- `fillQuad(BufferBuilder, PoseStack, x1, y1, z1, ..., r, g, b, a)` — 填充四边形面片
- `fillTexturedNormalQuad()` — 绘制带纹理的四边形（用于材质表面）

**Gizmo.java 中的工具**（编辑器特用）:
- `renderAxis(float scale)` — 绘制 XYZ 坐标轴（红绿蓝）
- `renderRotationRing(Axis axis, float scale)` — 绘制旋转约束环
- `renderScaleBox(Axis axis)` — 绘制缩放立方体

**LineBuilder 用途**：
- 用于 2D UI 中的平滑曲线绘制（如曲线编辑器的贝塞尔曲线预览）
- `add()` 逐点添加，`build()` 生成顶点，`render()` 通过 Batcher2D 批处理

---

### 6. 已有的世界内 Overlay 约定（参考）

#### 上下文提示系统设计参考

**设计文档**：`.trellis/tasks/07-16-fsr-context-region-overlay/design.md:1-110` 定义了上下文菜单区域可视化的约定：

- **发现机制**：遍历 FSR UI 树，调用 `UIElement.createContextMenu()` 检测菜单区域，无需 Mixin 反射
- **转换管道**：通过 `UIViewportStack.fromElement()` 将 UI 坐标转换为屏幕坐标（考虑滚动偏移和 viewport 裁剪）
- **去重与裁剪**：
  - 过滤条件：`isVisible()`, `isEnabled()`, `canBeSeen()`, 非零尺寸, 与 viewport 及屏幕的交集
  - 合并完全相同的矩形
  - 保留重叠度 < 95% 或边界差异 > 2px 的嵌套矩形

- **视觉风格**：
  - 填充色：半透明青/蓝（易读）
  - 边框：完全不透明、高对比（白/黑）
  - 无文本标签、无额外面板（MVP 范围内）

- **缓存策略**：5 tick 刷新间隔；屏幕切换、尺寸变化或菜单源变化时失效

**关键学习**：此设计展示了在已有 UI 框架之上添加可视化层的模式——无需修改源代码、通过公开 API（`createContextMenu()`) 和事件钩子实现功能解耦

---

### 7. Morph 编辑器的辅助图形

#### 查找结果

**相关文件**：
- `new/src/client/java/mchorse/bbs_mod/client/renderer/MorphRenderer.java` — Morph 实体渲染器
- `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/UIFormEditor.java:80-100` — 形态/模型编辑器主面板

#### 结论

**当前状态**：BBS mod **未实现"仅在编辑模式显示的辅助图形"机制**。可能的设计方向：

1. **模式检测**：在 `UIFormEditor` 中添加状态标志 `isEditMode()`，编辑器激活时设为真
2. **选择追踪**：维护当前选中 morph 的引用
3. **世界渲染挂钩**：在 ClientLevel render-post 事件中检查编辑模式，若激活则绘制：
   - 外接球/立方体包围盒（通过 `Draw.renderBox()`）
   - 骨骼轴标记（通过 Gizmo）
   - 动画轨迹预览（通过 LineBuilder）

**实现参考**：`new/src/client/java/mchorse/bbs_mod/client/BBSRendering.java` 和 `BbsWorldRenderContext` 提供了钩入世界渲染管线的入口

---

## 8. 追加调研：Clips 系统、Form 属性、世界渲染、几何体绘制

### Q1：Clips 区间系统 vs 布尔关键帧（音频播放核心）

#### Clip 基类数据模型

**类路径**：`new/src/main/java/mchorse/bbs_mod/utils/clips/Clip.java`

**时间模型**（第 8-27 行）：
```java
public abstract class Clip extends ValueGroup {
    public final ValueBoolean enabled = new ValueBoolean("enabled", true);
    public final ValueString title = new ValueString("title", "");
    public final ValueInt layer = new ValueInt("layer", 0, 0, Integer.MAX_VALUE);  // 轨道层级
    public final ValueInt tick = new ValueInt("tick", 0, 0, Integer.MAX_VALUE);    // 起点（tick）
    public final ValueInt duration = new ValueInt("duration", 1, 1, Integer.MAX_VALUE);  // 时长（tick）
    public final Envelope envelope = new Envelope("envelope");
}
```

**关键方法**（第 34-39 行）：
```java
public boolean isInside(int tick) {
    int offset = this.tick.get();
    return tick >= offset && tick < offset + this.duration.get();
}
```
→ **区间模型**：`[tick, tick + duration)` 左闭右开

**Clips 容器**（`new/src/main/java/mchorse/bbs_mod/utils/clips/Clips.java:22-65`）：
- 维护 `List<Clip> clips` 有序列表
- `findFreeLayer()` 方法检查时间重叠并自动分配非冲突的 layer

#### AudioClientClip 实现（现成的区间播放参考）

**类路径**：`new/src/client/java/mchorse/bbs_mod/camera/clips/misc/AudioClientClip.java`

**继承链**：`AudioClientClip extends AudioClip extends CameraClip extends Clip`

**基类 AudioClip 字段**（`new/src/main/java/mchorse/bbs_mod/camera/clips/misc/AudioClip.java:13-28`）：
- `audio: ValueLink` — 音频资源 Link
- `offset: ValueInt` — 音频起始偏移（tick 单位，可负数）
- `volume: ValueFloat` — 音量 [0, 1]

**播放流程**（第 90-106 行）：

1. **检查时间范围**（第 93-97 行）：
   ```java
   float localTick = context.relativeTick + context.transition;
   if (link == null || localTick < 0F || localTick >= this.duration.get())
       return;  // 不在区间内，不播放
   ```

2. **计算音频位置**（第 100 行）：
   ```java
   float seconds = TimeUtils.toSeconds(this.offset.get()) + localTick / 20F;
   ```
   → 从 offset（tick）转换为秒数，加上当前相对位置

3. **存储播放状态**（第 104 行）：
   ```java
   getPlayback(context).put(this, new Playback(link, seconds, this.volume.get()));
   ```

4. **声音管理**（`new/src/client/java/mchorse/bbs_mod/camera/clips/misc/AudioClientClip.java:43-67`，`manageSounds()` 方法）：
   ```java
   sounds.reconcile(context.getPlaybackOwner(), desired, context.playing);
   ```
   → 调用 `SoundManager.reconcile()`，传入期望状态，自动处理启动/停止/移除

**关键设计**：
- Clip **本身不主动触发事件**，只是数据容器（区间 + 参数）
- 回放管理器（如 FilmController）在每 tick 检查 `isInside()`，决定是否调用 `applyClip(context)`
- AudioClientClip 在 `applyClip()` 中将音频加入"期望播放列表"
- `SoundManager.reconcile()` 负责启/停声音，完全自动化（无显式事件回调）

#### UI 编辑（Clips 轨道）

**搜索结论**：BBS mod 中有 Clips 编辑面板（Film Editor 中的音频轨道编辑），但精确实现文件未在本轮搜索中定位（行号未确认）。UI 通常通过拖拽条形表示 [tick, tick+duration)。

#### **Q1 建议方案：应该选择 (a) 复用 Clips 区间机制**

**理由**：
1. **现成的时间模型**：Clips 已有 `[tick, duration)` 的完整实现，无需重新造轮
2. **层级管理**：`layer` 字段自动防止时间碰撞，UI 展示多轨清晰
3. **参数灵活性**：Clip 的 `envelope` 支持淡入/淡出，音量包络等（仅 Clip 无法做到）
4. **回放管理器完备**：BaseFilmController 已遍历 clips，调用 `applyClip()`，无需额外事件轮询
5. **AudioClientClip 为反证**：现成的音频 Clip 就是选 (a) 的证据——它通过 `isInside(tick)` + `manageSounds()` 实现"区间内播放"

**不选 (b) 的原因**：
- BOOLEAN KeyframeChannel 只能状态型（阶跃），需要上层代码检测边界（false→true），额外维护成本
- 无法控制淡入淡出、播放速率等包络参数

---

### Q2：Form 属性如何进关键帧轨道

#### FormProperties 动态通道创建

**类路径**：`new/src/main/java/mchorse/bbs_mod/film/replays/FormProperties.java`

**核心方法**：

**`getOrCreate(Form form, String key)`**（第 55-81 行）：
```java
public KeyframeChannel getOrCreate(Form form, String key) {
    BaseValue value = this.get(key);
    BaseValue property = FormUtils.getProperty(form, key);
    
    if (value instanceof KeyframeChannel channel)
        return channel;
    
    if (property != null)
        return this.create(property);  // 为属性创建通道
    
    if (PerLimbService.isPoseBoneChannel(key))
        return this.registerChannel(key, KeyframeFactories.POSE_TRANSFORM);
    
    if (PerLimbService.isMaterialTextureChannel(key))
        return this.registerChannel(key, KeyframeFactories.LINK);
    
    return null;
}
```

→ **查询流程**：
1. 先查本地缓存（已创建的通道）
2. 调用 `FormUtils.getProperty(form, key)` 查询 Form 的属性（**字符串路径 key**）
3. 若属性存在且标记为 `isVisible()` 且实现 `BaseKeyframeFactoryValue`，则创建对应工厂的 `KeyframeChannel`

**`create(BaseValue property)`**（第 83-104 行）：
```java
public KeyframeChannel create(BaseValue property) {
    if (property.isVisible() && property instanceof BaseKeyframeFactoryValue<?> keyframeFactoryValue) {
        String key = FormUtils.getPropertyPath(property);  // 获取属性的字符串路径
        IKeyframeFactory factory = keyframeFactoryValue.getFactory();
        
        if (factory == KeyframeFactories.TRANSFORM && PerLimbService.isPoseBoneChannel(key))
            factory = KeyframeFactories.POSE_TRANSFORM;  // 骨骼特殊处理
        
        KeyframeChannel channel = new KeyframeChannel(key, factory);
        this.properties.put(key, channel);
        this.add(channel);  // 注册为 FormProperties 的子值
        return channel;
    }
    return null;
}
```

**属性标识**：
- **key 格式**：字符串路径，如 `"pose"`, `"color"`, `"bone.armRight.x"` 等
- 由 `FormUtils.getPropertyPath(property)` 生成，与 Form 字段的反射路径对应

#### Form 子类示例（ModelForm）

**类路径**：`new/src/main/java/mchorse/bbs_mod/forms/forms/ModelForm.java:28-98`

**如何让字段可动画**（以 `color` 为例，第 36、85 行）：

1. **声明属性字段**（需要实现 `BaseKeyframeFactoryValue`）：
   ```java
   public final ValueColor color = new ValueColor("color", Color.white());
   ```
   → `ValueColor` 实现了 `BaseKeyframeFactoryValue<Integer>` 接口

2. **在构造器注册**（第 85 行）：
   ```java
   this.add(this.color);  // 加入 Form 的值树
   ```

3. **标记为可见**（默认）：
   ```java
   // 若字段需要在 UI 中隐藏但仍可 K 帧，调用
   this.someProperty.invisible();  // 示例：line 87 - boneTracks
   this.add(this.someProperty);
   ```

4. **关键帧轨道自动出现**：
   - 编辑器在回放时调用 `FormProperties.getOrCreate(form, "color")`
   - FormProperties 反射查询 Form，发现 `color` 实现 `BaseKeyframeFactoryValue<Integer>` + 可见
   - 自动创建 `KeyframeChannel<Integer>(key="color", factory=ColorKeyframeFactory)`
   - UIKeyframes 在 dope sheet 中显示 "color" 轨道

**注册所有可动画属性流程**（第 69-97 行）：
- Form 在构造器中 `add()` 所有想要的属性
- FormProperties 在运行时按需查询并缓存通道
- 编辑器通过 UIKeyframeSheet 将轨道添加到 dope sheet

#### 形态轨道在 Dope Sheet 中的列出

**关键流程**：
1. 编辑器调用 `FormProperties.getOrCreate(form, key)` → 获取 KeyframeChannel
2. 为每个 channel 创建 `UIKeyframeSheet(id=key, channel, property)`
3. UIKeyframeSheet 加入 UIKeyframeDopeSheet（通过 `dopeSheet.add(sheet)`）
4. Dope sheet 在 UI 中遍历 sheets，绘制轨道条

---

### Q3：世界渲染钩子（外部代码绘制入口）

#### 官方渲染事件注册

**类路径**：`new/src/client/java/mchorse/bbs_mod/client/BBSClientNeoEvents.java:149-180`

**事件钩子方法签名**（第 149 行）：
```java
private static void onRenderLevelStage(RenderLevelStageEvent event)
```

**两个渲染阶段**（第 151-179 行）：

**阶段 1：AFTER_ENTITIES**（第 151-161 行）：
```java
if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
    PoseStack stack = event.getPoseStack();
    if (stack == null) return;
    
    BBSModClient.onRenderAfterEntities(createWorldRenderContext(event, stack));
}
```
→ 在实体渲染后、天空/粒子前；**适合绘制世界坐标系的调试几何体**

**阶段 2：AFTER_LEVEL**（第 162-179 行）：
```java
else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
    BBSModClient.onRenderAfterLevel();
    
    if (!ClientApiCompat.hasLastHandlers()) return;
    
    PoseStack stack = event.getPoseStack();
    if (stack == null) return;
    
    ClientApiCompat.emitLast(createWorldRenderContext(event, stack));
}
```
→ 在所有世界渲染后；**适合覆盖层 UI（如选中框）**

**渲染上下文签名**（第 270-287 行）：
```java
private static BbsWorldRenderContext createWorldRenderContext(RenderLevelStageEvent event, PoseStack stack) {
    Minecraft mc = Minecraft.getInstance();
    PoseStack worldStack = new PoseStack();
    
    worldStack.setIdentity();
    worldStack.last().pose().set(stack.last().pose());
    worldStack.last().normal().set(stack.last().normal());
    
    return new BbsWorldRenderContext(
        event.getCamera(),              // Camera (world coord system ref)
        worldStack,                     // PoseStack (model-view matrix)
        mc.renderBuffers().bufferSource(),  // MultiBufferSource
        resolveTickDelta(event.getPartialTick()),  // float tickDelta
        event.getModelViewMatrix(),     // Matrix4f modelView
        event.getProjectionMatrix()     // Matrix4f projection
    );
}
```

#### BbsWorldRenderContext 接口

**类路径**：`new/src/client/java/mchorse/bbs_mod/client/rendering/context/BbsWorldRenderContext.java:14-84`

**公开方法**：
```java
public Camera camera()                          // World camera
public PoseStack matrixStack()                  // Model-view stack
public MultiBufferSource.BufferSource consumers()  // Vertex buffer source
public float tickDelta()                        // Partial tick [0, 1)
public Matrix4f modelViewMatrix()               // Model-view matrix
public Matrix4f projectionMatrix()              // Projection matrix
```

#### Form 渲染上下文

**Form 渲染签名**（`new/src/client/java/mchorse/bbs_mod/forms/renderers/FormRenderer.java:59-99`）：
- 编辑器 UI 渲染：`renderUI(UIContext context, int x1, int y1, int x2, int y2)`
- 世界渲染：无标准方法；可通过监听 AFTER_ENTITIES 事件，用 BbsWorldRenderContext 绘制

**当前设计**：
- FormRenderer 主要用于 UI 预览
- 世界中的 Form 由 FormRenderer 子类（ModelFormRenderer 等）独立实现
- 未有统一的"获取当前选中 Form"机制在世界渲染中

#### 判断 Form 在编辑器中被选中

**UIFormEditor 选中态字段**（`new/src/client/java/mchorse/bbs_mod/ui/forms/editors/UIFormEditor.java:92-93`）：

```java
public UIPickableFormRenderer renderer;
public UIForm editor;  // 当前选中的编辑器面板（非空 = 有 form 被选中）
```

→ **检查方法**：
```java
if (uiFormEditor.editor != null) {
    Form selectedForm = uiFormEditor.editor.getForm();  // 获取被选中的 Form
    // ...绘制该 form 的辅助图形
}
```

**缺陷**：UIFormEditor 是 UI 层，世界渲染事件 (RenderLevelStageEvent) 无直接访问。需要：
1. 在 UIFormEditor 中公开 `getSelectedForm()` 方法
2. 或维护全局 `BBSModClient.selectedFormCache` 引用
3. 在 onRenderAfterEntities 中读取并绘制

---

### Q4：现成的球/圆/锥绘制代码

#### 已有的圆形绘制（Batcher2D）

**类路径**：`new/src/client/java/mchorse/bbs_mod/ui/framework/elements/utils/Batcher2D.java`

**圆弧预计算**（第 40-42 行）：
```java
private static final int ROUNDED_CORNER_STEPS = 8;
private static final float[] ROUNDED_RECT_ARC_SIN = new float[ROUNDED_CORNER_STEPS + 1];
private static final float[] ROUNDED_RECT_ARC_COS = new float[ROUNDED_CORNER_STEPS + 1];
```

→ 预计算 sin/cos 值用于圆角矩形（8 步分割），但**仅用于 2D UI 圆角**

**圆角绘制方法**（第 242-277 行，`fillRoundedRectAxisTriangles()`；第 328-347 行，`fillRoundedCornerFan()`）：
```java
// 第 344-347 行示例
this.fillRoundedCornerFan(builder, matrix4f, mirror, x0 + r, y0 + r, r, color, 0);
this.fillRoundedCornerFan(builder, matrix4f, mirror, x1 - r, y0 + r, r, color, 1);
// ...绘制 4 个圆角
```

→ 只绘制圆角（四分之一圆 × 4），**无完整圆或球的实现**

#### 粒子系统形状可视化

**搜索结论**：
- `new/src/main/java/mchorse/bbs_mod/forms/forms/utils/ParticleSettings.java:7-29` — 粒子参数（资源 ID、参数字符串），**无几何体定义**
- 粒子发射形状（球/锥/平面）由 Minecraft 原生 ParticleEngine 处理，BBS mod 未实现预览渲染

#### **Q4 结论：需要从零写**

**需要实现的几何体**：
| 几何体 | 算法 | 复杂度 |
|------|------|------|
| **圆（平面）** | `for i in [0, steps): angle = 2π*i/steps; x = cx + r*cos(angle); y = cy + r*sin(angle);` | 低 |
| **圆环** | 圆 + 内圆（两条边界） | 低 |
| **球（线框）** | 经纬线（循环嵌套 sin/cos） | 中 |
| **圆锥（线框）** | 底圆 + 顶点射线 | 中 |
| **扇形/弧段** | 圆弧 + 两条半径 | 低 |

**推荐步骤**：
1. 创建 `SoundVisualizationRenderer.java` 工具类（世界坐标系）
2. 实现静态方法 `drawSphere(PoseStack, x, y, z, radius, segments, color)`、`drawCone()` 等
3. 在 BBSClientNeoEvents.onRenderAfterEntities() 中调用
4. 复用 `Draw.java` 的 BufferBuilder 工具

**不可复用的原因**：
- Batcher2D 是 **2D UI 专用**，局限于屏幕坐标系
- Draw.java 只有盒子，无球/锥原语
- 粒子系统预览不存在
