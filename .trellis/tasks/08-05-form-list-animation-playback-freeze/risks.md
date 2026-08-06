# 风险登记：伪装列表 Form 动画正常播放与冻结开关

记录本任务实施过程中发现的全部风险，含已发生并修复的回归、剩余的理论风险，以及排查噪音时的判别依据。

---

## A. 已发生并已修复的回归

### A1 渲染器构造触碰原版注册表（严重，已修）

- **现象**：`./gradlew build` 中 `:testIkPhysicsConsistency` 崩溃
  `ExceptionInInitializerError` → `IllegalArgumentException: Not bootstrapped (registry minecraft:game_event)`
  栈：`ItemStack.<clinit>` ← `StubEntity.<init>(StubEntity.java:67)` ← `FormRenderer.<init>` ← `IKPhysicsConsistencyTest$TrackingRenderer.<init>`
- **原因**：把 `private final StubEntity uiTickEntity = new StubEntity();` 作为字段初始化器写进 `FormRenderer`，于是**每个** renderer 构造时都会跑；`StubEntity()` 无参构造遍历 `EquipmentSlot` 写入 `ItemStack.EMPTY`，触发 `BuiltInRegistries` 静态初始化。headless 测试从不 bootstrap 原版注册表。
- **修复**：改为懒初始化，`getUITickEntity()` 首次调用时才构造（`FormRenderer.java`）。缩略图动画路径之外的 renderer 永远不会创建它。
- **隐式契约（本次学到）**：**`FormRenderer` 及其子类的构造过程不得触碰任何原版注册表 / `ItemStack` / `BuiltInRegistries`。** 多个 headless 测试套件直接 `new` 渲染器子类，构造期的注册表访问会让它们整体崩溃。往基类加字段时尤其容易踩中。
- **回归防线现状**：`:testIkPhysicsConsistency` 事实上守住了这条契约（它构造 `TrackingRenderer`）。若未来新增基类字段，该测试会再次报警。

---

## B. 剩余风险（未修复，按影响排序）

### B1 冻结豁免的选中判定是"最近一 tick"而非精确的每绘制点

- **位置**：`FormRenderer.isRecentlySelected()`，容差 `currentUITick - lastSelectedTick <= 1`
- **成因**：一个 form 的渲染器是共享的，同一帧内可能被多个列表绘制（实体选择器的行列表 + 伪装选择器的网格 + 编辑器部件列表）。时钟由**本 tick 第一个**绘制点结算，而它未必是持有选中态的那个。容差让顺序差异在下一 tick 自愈。
- **残余影响**：取消选中后，动画会多播放最多 1 tick（50 ms）才停住。
- **理论边界**：若同一 form 的多个绘制点恰好跨越 tick 边界，可能出现 1 帧的冻结/播放闪烁；下一 tick 自愈。
- **调参余地**：若实测发现容差不够，可放宽到 2 tick，代价是取消选中的响应延迟 +50 ms。

### B2 冻结判定是 renderer 级，不是每列表级

- **成因**：同上，渲染器共享。若同一个 Form 实例同时出现在两个列表、且在 A 中选中、在 B 中未选中，两处都会播放动画。
- **判断**：符合需求语义（"选中该 Form 时让这个 Form 正常播放"），且实践中"最近"类目会 `FormUtils.copy` 出独立实例，重合概率低。
- **若要改**：需把时钟从 renderer 上移到"每绘制上下文一份"，成本远高于收益。

### B3 `ModelFormRenderer` 复用姿态路径不受本机制管辖

- **位置**：`ModelFormRenderer.renderInUI(..., sourceOwner, sourceEntity)`，仅 `sourceOwner == null` 时调 `advanceUIAnimation`
- **判断**：`sourceOwner != null` 是影片 / 模型方块的 `renderPreviewThumbnail`，本就该沿用调用方节奏，不应被缩略图时钟接管。
- **风险**：这两条路径的动画表现**未随本任务改动**，也未在本轮手工验收里覆盖。若它们原本就有抽搐，本任务不会修好。

### B4 外部 tick 记账依赖 `recordExternalTick` 的调用纪律

- **位置**：`MobFormRenderer.tick` / `ModelFormRenderer.tick` 开头
- **风险**：未来若新增实现 `ITickable` 的 `FormRenderer` 子类而忘记调用 `recordExternalTick()`，该 form 在"预览 + 缩略图同屏"时会双倍速播放 —— 正是本任务中途出现过的症状。
- **缓解建议**：如果后续再加同类子类，考虑把记账收进基类的模板方法，或补一条源码级回归检查。

### B5 `externalTickCount` 溢出（可忽略）

- `long` 计数，20 TPS 下约需 1.46e10 年溢出。仅为完整性记录。

### B6 每个渲染器的内存开销

- 新增 5 个 `long` + 1 个 `boolean` + 1 个 `StubEntity` 引用（懒建），约 56 字节/实例。渲染器数量与 Form 数量同阶，影响可忽略。

---

## C. 排查噪音的判别依据（非本任务缺陷）

### C1 `:testUiMotions` 打印 "Not bootstrapped" 堆栈但测试通过

- **栈**：`UIMotions.enabled` → `ThemeManager.current` → `ThemeParser.resolveChain` → `ThemeManager.loadThemeMap` → `BBSMod.<clinit>`(`BBSMod.java:216`) → `DeferredHolder.bind` → `BuiltInRegistries` → `Not bootstrapped`
- **性质**：**既有的、有意为之的良性回退**。`ThemeManager.loadThemeMap` 显式 `catch (Exception | LinkageError e)`，注释写明"LinkageError covers running without a bootstrapped mod runtime (e.g. plain-JVM tests): resolution falls back to code defaults"。`ExceptionInInitializerError` 属于 `LinkageError`，被吞掉、记 WARN、回退默认主题，所以 `UIMotionsTest: all tests passed`。
- **与 A1 的区别**：症状同为 "Not bootstrapped"，但 A1 发生在 `FormRenderer.<init>`，且 `IKPhysicsConsistencyTest` 没有这层 catch → 真失败。**排查时不要把 C1 当成 A1 的残留。**
- **判别方法**：看栈里是否出现本任务改动的 11 个文件；C1 的栈中一个都没有。

---

## D. 尚未验证 / 未交付的项

### D1 未交付：源码级回归测试（AC10）

本轮未新增覆盖"列表路径推进动画时钟"与"冻结开关 + 选中豁免"的回归测试。现有防线只有 `:testIkPhysicsConsistency` 间接守住 A1 的构造契约。B4 的记账纪律因此也没有自动化防线。

### D2 尚未游戏内验证的场景

- 影片菜单的 Form 预览（`sourceOwner != null` 复用姿态路径，见 B3）
- 模型方块菜单（`setTarget` 世界实体驱动，预览不自驱动）
- 冻结开关与 Form 自身 `paused` 关键帧驱动（runtime value）叠加时的优先级
- 世界内伪装 / 模型方块 / 影片回放动画无回归（AC8 按用户实测通过，但未逐一穷举）
