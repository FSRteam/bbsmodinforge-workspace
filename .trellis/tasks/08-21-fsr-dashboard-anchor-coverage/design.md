# Dashboard 锚点覆盖扩充 · 设计方案

> 本任务是对已交付机制的**扩容**，不是重新设计。机制本身（`registerAnchor` / `resolveDashboardAnchor` / `AnchorTarget`）保持不变。

## 0. 两条必须守住的既有语义

### 0.1 `panelId` 填错会让锚点指到错的地方

`resolveDashboardAnchor` 的面板判定是：

```java
if (target.panelId() != null && !target.panelId().equals(this.activePanelId()))
    return PANEL_NOT_MOUNTED;
```

`panelId` 为 null 表示「不属于任何面板」（Dashboard chrome 用这个，如 `SETTINGS` / `SELECTORS`）。**面板内控件传 null 会绕过这道判定**：切到别的面板后该锚点仍被判为可用，于是解析出一个属于已卸载面板的矩形，教程在错误的面板上画高亮。

这是本任务最容易犯且不报错的错。每个新增锚点必须填对所属面板 id，并且要有测试挡住。

### 0.2 重复 id 是硬失败，不要为批量登记放宽它

```java
AnchorTarget previous = this.dashboardAnchors.putIfAbsent(anchorId, …);
if (previous != null) throw new IllegalStateException("Duplicate Dashboard anchor id: " + anchorId);
```

批量登记时若图省事改成 `put` 覆盖，就丢掉了「同一个 id 被两处登记」的保护——那种情况下后登记的静默胜出，症状是锚点指向另一个控件。保留 `putIfAbsent` + 抛异常。

## 1. 命名与分组

沿用既有约定 `<panel>.<control>`，与 `morphing.palette` / `morphing.demorph` 同构。面板前缀用 `BBSDashboardPanelIds` 的值，保证与面板 id 一致。

建议分组（实施时按已核实的 public 字段落值，不可达的不登记）：

```
morphing.*     palette / palette_list / palette_editor / demorph / from_mob
film.*         selection / recorder / preview / duplicate /
               open_menu / open_camera_editor / open_replay_editor / open_action_editor /
               camera_editor / replay_editor / action_editor / camera_timeline / replay_timeline / action_timeline
model_blocks.* list / pick_edit / toggle_enabled / toggle_shadow / toggle_global / toggle_look_at
particles.*    renderer / selection / dock / lock_layout / layout_presets / play_pause
model_editor.* general / renderer / splitter
textures.*     picker
audio.*        pick / play_pause / save_colors / editor
graph.*        canvas / expression / help
plugins.*      list / rescan / open_folder / install / auto_apply
```

数量约 45 个，加上已有的 9+9+5，总数在 70 上下。

**实施结果**：采用 A（集中分派）。锚点属于宿主对外契约，集中在 `UIDashboard` 的私有登记方法中，
按面板类型分组，便于和 `BBSDashboardAnchors` 常量表逐项核对；各面板无需新增自述接口或扩大 public 面。
Film 与 Morphing 的入口、编辑器和时间轴已与其余七个内置面板一起登记，统一由结构测试反射枚举校验。
`UIReplaysEditor` 没有公开 `clips` 字段，因此 `film.replay_timeline` 指向其现有公开 `keyframeEditor`；
没有为了获得一个名为 `clips` 的字段而修改宿主可见性。

## 2. 登记点组织

现状是 `registerBuiltInPanel` 里一个 `if (panel instanceof UIMorphingPanel morphing)` 分支登记三个 Morphing 锚点。扩到 9 个面板后这里会变成一长串 `instanceof` 分支。

实施时在两种形状里选一种并说明理由：

- **A. 保持集中**：`registerBuiltInPanel` 里按面板类型分派到各自的私有方法（`registerMorphingAnchors(id, panel)` 等）。改动集中在 `UIDashboard`，一眼能看全所有锚点。
- **B. 下沉到面板**：给面板一个可选的锚点自述接口，各面板报告自己的锚点。`UIDashboard` 不再认识具体面板类型，但锚点定义散落到 9 个文件。

倾向 A：锚点是**对外契约**，集中在一处便于与 `BBSDashboardAnchors` 常量表对照，也符合「契约由宿主统一拥有」的定位。B 的解耦收益在这里不明显，反而让「哪些锚点存在」变得难查。

## 3. 测试加强（本任务的实质要求之一）

现有 `DashboardGuideContractTest` 用源码文本匹配：

```java
check(dashboard.contains("BBSDashboardAnchors.MORPHING_PALETTE") && …);
```

这挡得住「登记被删」，挡不住「新增常量忘了登记」——加一个常量不写登记点，测试照样绿。锚点从 5 个涨到 ~50 个之后，靠人工维护匹配清单必然漂移。

**要求改成可枚举校验**：反射取 `BBSDashboardAnchors` 的全部 `public static final String` 常量，逐个断言在 `UIDashboard` 源码里有登记点。这样：

- 新增常量忘登记 → FAIL
- 删掉登记点 → FAIL
- 不需要在测试里维护第二份清单

`panelButton(...)` / `panelContent(...)` 是方法不是常量，仍按现有方式检查其调用存在。

**`panelId` 正确性**也要挡。源码文本匹配能做到的程度是：断言每个 `<panel>.<control>` 形式的锚点，其登记调用里的 `panelId` 参数与前缀一致。这不是运行期验证，但能挡住最常见的错填与复制粘贴遗漏。若能在既有测试基建里做运行期断言更好，实施时评估。

## 4. 性能

教程覆盖层每帧解析一次锚点。`resolveDashboardAnchor` 是一次 map 查找 + `canBeSeen()` + 视口相交计算，锚点总数增长**不影响单次解析成本**（不是遍历）。所以扩容本身不引入性能问题。

需要留意的是 `UIViewportStack.fromElement(element)` 要沿父链走一遍，深层控件的链更长。若冒烟发现帧率变化，实测数据与结论记进本文档；不要预先做缓存优化（会与「每帧重解析」的正确性要求冲突）。

## 5. 风险

| 风险 | 症状 | 缓解 |
|------|------|------|
| 面板内锚点 `panelId` 填 null | 切走面板后仍判可用，在错的面板画高亮 | §0.1 + §3 的 panelId 校验 |
| 批量登记改用 `put` 覆盖 | 重复 id 静默后者胜出 | §0.2 保留 `putIfAbsent` |
| 新增常量忘了登记 | 锚点永远 `CONTROL_NOT_FOUND`，高亮不出现且无报错 | §3 反射枚举校验 |
| 为登记而扩大 public 面 | 宿主契约面失控 | prd 明确禁止；不可达就不登记并记原因 |
| 一次铺完 45 个难验证 | 错填难定位 | §1 分批：Film / Morphing 先行 |
| 预先做锚点缓存 | 改 UI scale / 滚动后指错位置 | §4 禁止；正确性优先 |

## 6. 交付后需同步

- `.trellis/spec/new/backend/external-addon-integration-contract.md` §3.4.4：锚点段落需按实际覆盖面更新（现在写的是「first tutorial range also registers Dashboard settings/selectors and Morphing palette/demorph/from-mob controls」）。
- `.trellis/tasks/08-21-tutorial-guided-mode-slice/prd.md`：其「现有宿主锚点覆盖面」表格与「控件级无锚点」分类需按本任务结果修订；`design.md` §1.1 的填法方向表可以扩。
