# FSR 宿主 Dashboard 锚点覆盖扩充（面板内一级入口）

## Goal

让教程能指到**面板内部**的控件，而不是只能指到 taskbar 上的面板按钮。

`08-21-fsr-guided-tour-overlay-api` 交付的锚点机制是好的，但覆盖面只有 5 个具名控件（Dashboard 的 settings / selectors，Morphing 的 palette / demorph / from_mob）加上 9 个面板按钮与 9 个面板内容根。结果是教程走进面板之后就没有可指的目标了——「带你到伪装面板」能做到，「指出该点哪个标签」做不到。

本任务把锚点补到覆盖九大面板的**一级入口控件**，为 `08-21-tutorial-guided-mode-slice` 及后续章节铺开提供指认目标。

## Context

### 触发原因

`08-21-tutorial-guided-mode-slice` 规划阶段逐条核对了教程前五章 32 步，发现：

- ch01–ch03 有一半步骤能用现有锚点（点面板按钮、打开伪装、取消伪装）。
- **ch04 与 ch05 整两章都在 Morphing 面板内部操作**（姿势标签、骨骼树、Rotation 滑块、右键菜单），现有锚点只到 `morphing.palette` 这一级，两章基本只能「带你到面板」然后放手。

统计全部 343 步的 `instruction`，加粗标记的操作目标有 696 处、去重 486 个。这 486 个里绝大多数是属性字段、右键菜单项、具体取值（`player/steve`、`广告牌大小`、`帧率`），不是一级入口——**本任务不追求覆盖它们**。但其中的标签页、编辑器切换、工具栏按钮是高频且稳定的，值得补。

### 已核实的可达性

锚点登记是 `registerAnchor(anchorId, panelId, Supplier<UIElement>)`，`Supplier` 延迟取值，所以能指向构造后才赋值的字段。九大面板的一级入口基本都是 public 字段，可直接登记：

| 面板 | 已核实的 public 入口 |
|------|---------------------|
| Morphing | `palette`（内含 public `list` / `editor`）、`demorph`、`fromMob` |
| Film | `selectionPanel`、`recorder`、`preview`、`duplicateFilm`、`cameraEditor` / `replayEditor` / `actionEditor`（各含 public `clips` 时间轴）、`openFilmMenu` / `openCameraEditor` / `openReplayEditor` / `openActionEditor` |
| Model Blocks | `modelBlocks`、`pickEdit`、`enabled`、`shadow`、`global`、`lookAt` |
| Particles | `renderer`、`selectionPanel`、`dock`、`lockLayoutButton`、`layoutPresetsButton`、`playPauseBtn` |
| Model Editor | `general`、`renderer`、`splitter` |
| Textures | `picker` |
| Audio | `pickAudio`、`plause`、`saveColors`、`audioEditor` |
| Graph | `canvas`、`expression`、`help` |
| Plugins | `list`、`rescan`、`openFolder`、`install`、`autoApply` |

Film 的覆盖面尤其重要：影片幕是教程里最大的一幕（16 章 112 步）。

### 内容分布（决定优先级）

| 幕 | 章数 | 步数 |
|----|-----|-----|
| 入门 | 5 | 32 |
| 伪装 | 10 | 66 |
| 影片 | 16 | 112 |
| 创作 | 14 | 94 |
| 导出 | 2 | 11 |
| 系统 | 5 | 28 |

## Requirements

### 1. 锚点覆盖范围

- 为九大面板各自的**一级入口控件**登记具名锚点，取值范围以上表已核实的 public 字段为准。
- 命名沿用现有约定：`<panel>.<control>`（如 `film.camera_editor`、`plugins.install`）。与既有 `morphing.palette` 同构。
- 优先级按内容分布：Film 与 Morphing 优先（178 步），其次创作幕涉及的 Particles / Model Editor / Textures / Model Blocks，最后 Audio / Graph / Plugins。
- **不要求**覆盖属性字段、右键菜单项、弹层内控件、具体取值。那些数量在数百级且随内容演进，不是稳定契约。

### 2. 登记方式与约束

- 沿用 `registerAnchor(anchorId, panelId, Supplier<UIElement>)`，`panelId` 必须填对应面板 id（否则锚点在别的面板激活时也会被解析成可用，指到错的地方）。
- 现有 `registerAnchor` 对重复 id 抛 `IllegalStateException`。新增批量登记不得破坏这个保护。
- 面板内容根之外的控件（Dashboard chrome）`panelId` 传 null，与现有 `SETTINGS` / `SELECTORS` 一致。
- 不得为了登记锚点而放宽宿主控件的可见性（不新增 public 字段、不改私有为公开）。**若某个目标控件不可达，就不登记它**，并在设计文档里记下原因——扩大宿主 public 面是比缺个锚点更大的代价。

### 3. 契约与测试

- `BBSDashboardAnchors` 的新常量与 `UIDashboard` 的登记点必须双向一致，两个方向都要能被测试挡住（有常量没登记、有登记没常量）。
- 现有 `DashboardGuideContractTest` 是源码文本匹配。本任务需**加强**为可枚举校验：从 `BBSDashboardAnchors` 反射取出全部常量，逐个确认在 `UIDashboard` 有登记点。这样新增常量忘了登记会失败，而不是靠人工维护一份清单。
- 锚点数量增长后，`resolveDashboardAnchor` 的性能不得退化到影响渲染（教程覆盖层每帧解析一次）。若成为问题，记录实测数据与结论。

### 4. 不改动的部分

- 不改三个 API 的方法签名与返回类型。
- 不改覆盖层挂载点、层级顺序、输入穿透默认值。
- 不改导航 future 的完成语义。
- 不动 `UIElement` / `UIViewportStack` 等框架类。

## Acceptance Criteria

自动可验证：

- [x] `new/` 的 `./gradlew build` 真实 exit code 0（80 个任务，最终 `EXIT=0`）。
- [x] `BBSDashboardAnchors` 全部常量都有对应登记点，反射枚举校验通过；反向也成立（无孤立登记）。
- [x] 重复 anchor id 仍由 `putIfAbsent` + `IllegalStateException` 硬失败，结构契约已锁定。
- [x] 每个新增锚点的 `panelId` 与其所属面板 id 一致，按常量值前缀自动校验。
- [x] 新增检查用反例确认会 FAIL：常量漏登记与 `panelId` 错填均 `EXIT=1`；恢复后 `EXIT=0`。
- [x] 既有导航、穿透、失败隔离和二进制兼容测试全部仍通过。

需人工冒烟：

- [ ] 在 Film 面板打开时解析 `film.*` 锚点，矩形落在对应控件上；切到别的面板后同一锚点返回 `PANEL_NOT_MOUNTED`。
- [ ] 滚动面板内容后锚点跟随，滚出视口时返回 `OUTSIDE_VIEWPORT`。
- [ ] 改 UI scale、缩放窗口后矩形仍对位。
- [ ] 锚点数量增长后 Dashboard 无可感知的帧率变化。

## Out of Scope

- 属性字段、右键菜单项、弹层内控件、具体取值的锚点。
- 为登记锚点而扩大宿主 public 面。
- 教程内容侧的字段填写与接线（属 `08-21-tutorial-guided-mode-slice` 及后续）。
- 锚点的自动发现机制（tooltip / L10n 键反查）——`08-21` 已明确拒绝，理由是改名会静默失配。
- 非 Dashboard 屏幕的锚点。
