# 技术设计：伪装列表 Form 动画正常播放与冻结开关

## 架构概览

### 现有渲染路径对比

| 路径 | 调用栈 | 动画推进 | 表现 |
|------|--------|---------|------|
| **列表缩略图** | `UIFormCategory.render` → `FormUtilsClient.renderUI` → `MobFormRenderer.renderInUI` | ❌ 不调用 `form.update` | 抽搐 |
| **编辑 3D 预览** | `UIPickableFormRenderer.update` → `Form.update` → `renderer.tick` | ✅ 每帧 tick | 正常 |
| **世界渲染** | `Form.update` 从实体 tick 调用 | ✅ 每帧 tick | 正常 |

### 根因重述（继承自 `08-01-form-list-animation-playback-freeze`）

`MobFormRenderer.renderInUI` 不调用 `Form.update`，导致：
1. `entity.tickCount` 冻结在初始值（如 0）
2. 每帧计算 `ageInTicks = tickCount + partialTick`，其中 `partialTick ∈ [0, 1)`
3. 时间驱动型动画（四肢摆动、LimbAnimator、末影龙翅膀）采样到 `[0, 1)` 振荡时间 → 抽搐
4. 上次修复尝试（`advanceUIAnimation` 在 partial tick 回绕时 `entity.tick()`）被用户实测推翻

## 方案对比

> **注意**：以下 A/B/C 三个方案是规划期的推演。实施后 A 被实测推翻 —— 见本文档末尾"最终实现方案"。保留此节是为了记录"为什么伪造时钟不成立"这一结论。

### 方案 A：UI 独立动画时钟（规划期首选，**已被实测推翻**）

**思路**：
- 在 `MobFormRenderer` 维护独立的 `uiAnimationAge: float`（每个 renderer 实例独立）
- 列表渲染时每帧递增：`uiAnimationAge += context.getTransition()`
- 渲染前临时覆盖 `entity.tickCount = (int)uiAnimationAge`，渲染后恢复
- 不调用 `entity.tick()`，避免副作用

**优点**：
- ✅ 不触发实体世界副作用（AI、碰撞、网络同步）
- ✅ 与编辑预览隔离：编辑预览继续走 `Form.update`
- ✅ 冻结开关实现简单：检查开关决定是否递增 `uiAnimationAge`
- ✅ 选中豁免简单：从 `UIFormCategory.selected` 判断当前 form

**风险**：
- ⚠️ `entity.tickCount` 持续增长可能触发整型溢出（但实际影响小，Minecraft 原版同样面对）
- ⚠️ 需要确保临时覆盖 + 恢复的异常安全（用 try-finally）

**关键改动点**：
1. `MobFormRenderer`：
   - 新增字段 `private float uiAnimationAge = 0F;`
   - `renderInUI` 开头递增时钟（根据冻结开关与选中状态）
   - 调用 vanilla renderer 前临时设置 `entity.tickCount`，渲染后恢复
2. `BBSSettings`：新增 `freezeFormAnimations: ValueBoolean`
3. `UIFormCategory` / `UIFormList`：传递选中状态到渲染器（可选，或渲染时直接查询）

---

### 方案 B：调用 `Form.update` 但隔离共享 renderer

**思路**：
- 让 `renderInUI` 调用 `form.update(entity)`，但在调用前克隆 renderer 状态
- 渲染后恢复原状态，避免污染编辑预览

**缺点**：
- ❌ `entity.tick()` 仍会触发副作用（AI、寻路、数据同步）
- ❌ renderer 状态克隆复杂（`MobFormRenderer` 有大量暂停/resume 状态机字段）
- ❌ 共享 renderer 机制难以彻底隔离（form 可能在列表和编辑器中同时渲染）

**结论**：实现复杂且无法完全消除副作用，放弃。

---

### 方案 C：实体影子副本

**思路**：
- 为列表渲染创建实体的影子副本（`shadowEntity`），副本可以安全 tick
- 列表路径 tick 影子副本，编辑路径 tick 原始实体

**缺点**：
- ❌ 实体克隆成本高（NBT 序列化/反序列化）
- ❌ 每帧重建影子副本会导致帧率下降
- ❌ 影子副本的 AI / 数据同步仍可能产生副作用

**结论**：性能与复杂度都不可接受，放弃。

---

## 最终实现方案

**实际采用**：真实 tick 驱动 + 时钟归一 + 外部记账

### 核心思路

不伪造时钟，改为按 UI tick 真正推进实体与 animator 状态，同时用记账机制避免共享 renderer 被多条路径重复推进。

### 数据流

```
UIFormCategory.render()  [列表缩略图]
  └─> FormUtilsClient.renderUI(form, selected)
        └─> renderer.renderUI(context, ..., selected)
              ├─> 记录 selected 时间戳
              └─> renderer.renderInUI(context, ...)
                    └─> MobFormRenderer / ModelFormRenderer
                          ├─> pollUIAnimationTicks(context)  // 每 tick 只结算一次
                          │     ├─> 检查 lastUITick == context.getTick() → 已结算，返回 0
                          │     ├─> 计算 elapsed = min(tick - lastUITick, 10)
                          │     ├─> 减去 externallyTicked（预览已推进的 tick）
                          │     └─> 更新 uiAnimationFrozen = !shouldAdvanceUIAnimation()
                          ├─> advanceUIAnimation(context)  // 缩略图自驱动
                          │     └─> driveUIAnimation(ticks, () -> {
                          │           source.update();
                          │           this.tick(source);  // 内部调 entity.tick()
                          │         })
                          └─> 渲染时：
                                ├─> 冻结时固定 partial tick = UI_FROZEN_TRANSITION
                                └─> 未冻结时使用 context.getTransition()

UIPickableFormRenderer.update()  [编辑预览]
  └─> form.update(entity)
        └─> renderer.tick(entity)
              ├─> recordExternalTick()  // 记账：外部推进了 1 tick
              └─> entity.tick() + animator.update()
```

### 关键设计决策

#### D1：时钟推进时机（按 UI tick，不按渲染帧）

**关键约束**：渲染帧率（60~240fps）远高于 UI tick 率（20/s）。若每次 `renderInUI` 都 `+1`，动画会快 3~12 倍且随帧率漂移。

现有正确范式在 `UIModelRenderer.updateLogic`（`ui/framework/elements/utils/UIModelRenderer.java:263`）：按 `context.getTick()` 的差值补 tick，并对落后帧做上限保护。列表路径复用同一语义：

```java
private long lastUITick = Long.MIN_VALUE;

private void advanceUIAnimation(UIContext context, boolean isSelected)
{
    long tick = context.getTick();

    if (this.lastUITick == Long.MIN_VALUE)
    {
        this.lastUITick = tick;   // 首帧只对齐，不补
        return;
    }

    long delta = Math.min(tick - this.lastUITick, 10L);   // 与 UIModelRenderer 一致的补帧上限

    this.lastUITick = tick;

    if (delta <= 0L || !this.shouldAdvanceUIAnimation(isSelected))
    {
        return;
    }

    this.uiAnimationAge += delta;
}
```

`context.getTick()` 由 `UIScreen.update` → `UIBaseMenu.update` → `UIContext.update`（`UIContext.java:711`）每 UI tick 递增一次，所以这个时钟与编辑预览同频。

**渲染时的 partial tick**：`ageInTicks = tickCount + transition`，`transition` 仍由 vanilla 从传入的 `transition` 参数取，保持帧内插值平滑。冻结时 `uiAnimationAge` 不动，但 `transition` 仍在 0~1 振荡 —— 见 D6。

#### D2：冻结开关语义

```java
private boolean shouldAdvanceUIAnimation(UIContext context) {
    // 开关关闭：所有 form 推进
    if (!BBSSettings.freezeFormAnimations.get()) {
        return true;
    }
    
    // 开关启用：仅选中的 form 推进
    return this.isCurrentlySelected();
}

private boolean isCurrentlySelected() {
    // 从 UIFormCategory 获取选中状态
    // 需要在 renderInUI 时传入或通过静态上下文访问
    return false; // 占位，实现时补充
}
```

#### D3：选中状态传递

**选项 1**（推荐）：扩展 `FormUtilsClient.renderUI` 签名
```java
// FormUtilsClient
public static void renderUI(Form form, UIContext context, int x1, int y1, int x2, int y2, boolean isSelected)

// UIFormCategory.render 调用处
FormUtilsClient.renderUI(form, context, cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT, isSelected);
```

**选项 2**：通过 ThreadLocal 传递
```java
// FormUtilsClient
private static final ThreadLocal<Boolean> RENDER_SELECTED = ThreadLocal.withInitial(() -> false);

// UIFormCategory 在调用前设置
FormUtilsClient.RENDER_SELECTED.set(isSelected);
FormUtilsClient.renderUI(form, context, ...);
FormUtilsClient.RENDER_SELECTED.remove();
```

→ **选用选项 1**（签名扩展）：
- 更明确，调用栈一目了然
- 避免 ThreadLocal 忘记清理导致状态泄漏
- 其他 `renderUI` 调用点传 `false` 即可（非列表场景）

#### D4：时钟覆盖的异常安全

```java
int originalTickCount = this.entity.tickCount;
try {
    this.entity.tickCount = (int) this.uiAnimationAge;
    
    // 调用 vanilla EntityRenderer.render(...)
    Minecraft.getInstance().getEntityRenderDispatcher().render(
        this.entity, 0D, 0D, 0D, 0F, transition, stack, consumers, light
    );
} finally {
    this.entity.tickCount = originalTickCount;
}
```

#### D5：Form 自身 `paused` 的优先级

`Form.paused` 值（来自 `MobForm.paused: ValueBoolean`）语义：
- 该 form **自身设定**的暂停状态
- 可以是静态配置，也可以是关键帧驱动

新开关 `freezeFormAnimations` 语义：
- **全局 UI 行为**开关，不修改 form 数据

**优先级规则**：
```java
private boolean shouldAdvanceUIAnimation(UIContext context) {
    // 优先级 1：Form 自身 paused = true → 不推进（无论新开关状态）
    if (this.form.paused.get()) {
        return false;
    }
    
    // 优先级 2：新开关关闭 → 推进
    if (!BBSSettings.freezeFormAnimations.get()) {
        return true;
    }
    
    // 优先级 3：新开关启用 → 仅选中的推进
    return this.isCurrentlySelected();
}
```

### 边界情况处理

#### B1：实体初始化时 `tickCount` 不为 0

某些实体在 NBT 加载后 `tickCount` 可能非零。需要在 `ensureEntity()` 时记录初始值：
```java
private int entityInitialTickCount = 0;

private void ensureEntity() {
    // ... 现有实体创建逻辑 ...
    if (this.entity != null && this.entityInitialTickCount == 0) {
        this.entityInitialTickCount = this.entity.tickCount;
    }
}

// renderInUI 时覆盖为：initialTickCount + (int)uiAnimationAge
this.entity.tickCount = this.entityInitialTickCount + (int) this.uiAnimationAge;
```

#### B2：`uiAnimationAge` 溢出

`float` 可精确表示整数范围约 ±16M，对应 Minecraft 约 9 天连续运行。实际列表渲染不会持续这么久，但加保护：
```java
if (this.uiAnimationAge > 1_000_000F) {
    this.uiAnimationAge = 0F; // 回绕
}
```

#### B3：同一 Form 同时出现在列表和编辑器

- 列表路径走 `renderInUI`，修改 `uiAnimationAge`
- 编辑路径走 `render3D` + `Form.update`，修改 `entity.tickCount`

两者互不干扰：
- `uiAnimationAge` 仅在 `renderInUI` 中临时应用到 `entity.tickCount`，渲染后恢复
- 编辑路径的 `Form.update` → `renderer.tick` 正常推进 `entity.tickCount`

#### B4：ModelFormRenderer 的 `freezeModels` 开关

现有 `BBSSettings.freezeModels` 控制 `ModelFormRenderer` 的鼠标跟随角度：
```java
// ModelFormRenderer.getUIMatrix
if (BBSSettings.freezeModels.get()) {
    angle = -MathUtils.PI + MathUtils.PI / 8;
}
```

新开关 `freezeFormAnimations` 只控制时间推进，不影响 `freezeModels` 的角度冻结。两者可独立启用。

### 冻结时的稳定姿态

冻结时 `uiAnimationAge` 不再递增，`entity.tickCount` 保持固定值。动画采样到该固定时刻 → 稳定姿态。

需要确保 `uiAnimationAge` 初始值合理：
```java
private float uiAnimationAge = 0F; // 从 0 开始，对应实体生成时的默认姿态
```

### 测试契约

#### 源码级回归测试

新增 `MobFormUIAnimationTest`：
1. **测试冻结关闭时时钟推进**：
   - 创建 `MobFormRenderer`
   - 模拟多次 `renderInUI` 调用
   - 断言 `uiAnimationAge` 递增

2. **测试冻结启用且未选中时时钟静止**：
   - 启用 `freezeFormAnimations`
   - `isSelected = false`
   - 断言 `uiAnimationAge` 不变

3. **测试冻结启用且选中时时钟推进**：
   - 启用 `freezeFormAnimations`
   - `isSelected = true`
   - 断言 `uiAnimationAge` 递增

4. **测试 Form.paused 优先级**：
   - `form.paused = true`
   - 无论 `freezeFormAnimations` 和 `isSelected` 如何
   - 断言 `uiAnimationAge` 不变

### 兼容性

- 不改变 `Form.update` 语义：编辑预览、世界渲染继续调用 `form.update`
- 不改变 `MobFormRenderer` 的暂停系统：现有 `animationPaused` / `runtimePauseActive` 逻辑保持不变
- 不影响其他 `FormRenderer` 子类：`ModelFormRenderer` / `ItemFormRenderer` 等保持原逻辑

## 实施范围外

- ParticleFormRenderer / VanillaParticleFormRenderer 的列表动画（需要独立调查）
- ModelFormRenderer 的列表动画优化（当前已通过 `freezeModels` 角度冻结部分处理）
- 列表缩略图的光照 / 缩放 / 视角调整
