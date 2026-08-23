# Dashboard 锚点覆盖扩充 · 实施计划

## 前置

读 `prd.md` 与 `design.md`（**§0 两条既有语义**：`panelId` 填错会指到错的面板；重复 id 必须保持硬失败）。

工作目录 `new/`（BBSFSR 宿主，独立 git 仓库）。**有其他会话在并发修改**，遇到与本任务无关的编译错误先排除他人改动，提交按路径隔离。

已核实的可达 public 字段清单见 `prd.md` Context 表格，不必重新普查。

## 阶段一：登记点组织与测试加强（先做）

顺序是刻意的：先把护栏立起来，再批量加锚点，这样每批新增都立刻被校验。

- [x] 按 `design.md` §2 采用 A（集中分派）：锚点契约集中在 `UIDashboard` 的私有登记方法中，避免扩大面板 public 面。
- [x] 把 `DashboardGuideContractTest` 的锚点检查从文本清单改为**反射枚举**：取 `BBSDashboardAnchors` 全部 `public static final String` 常量，逐个断言在 `UIDashboard` 有登记点。
- [x] 加 `panelId` 正确性校验：面板锚点的登记调用参数须与锚点前缀对应。
- [x] 反例验证：常量漏登记和面板 id 填错均以 exit `1` 失败；恢复后专项测试 exit `0`。

## 阶段二：Film 与 Morphing（优先，覆盖 178 步）

- [x] Morphing：补 `palette_list` / `palette_editor`（`UIFormPalette` 的 public `list` / `editor`）。
- [x] Film：补齐选择、录制、预览、影片操作、四个打开按钮、三个编辑器和 camera/replay/action 时间轴。
- [x] 每个锚点的 `panelId` 填对应面板 id。
- [x] 每批登记后运行 `testClientStructuralRegistration`，最终专项测试通过。
- [x] 常量加入 `BBSDashboardAnchors`，命名 `<panel>.<control>`。

## 阶段三：创作幕相关面板（94 步）

- [x] Particles：`renderer` / `selection` / `dock` / `lock_layout` / `layout_presets` / `play_pause`。
- [x] Model Editor：`general` / `renderer` / `splitter`。
- [x] Textures：`picker`。
- [x] Model Blocks：`list` / `pick_edit` / 四个 toggle。

## 阶段四：其余面板（39 步）

- [x] Audio：`pick` / `play_pause` / `save_colors` / `editor`。
- [x] Graph：`canvas` / `expression` / `help`。
- [x] Plugins：`list` / `rescan` / `open_folder` / `install` / `auto_apply`。

## 阶段五：验证与收尾

- [x] 目标控件不可达时**不登记**；`UIReplaysEditor` 无公开 `clips`，因此 replay timeline 指向公开 `keyframeEditor`，未扩大宿主 public 面。
- [x] `./gradlew build`，显式确认真实 exit code 为 `0`：
  ```bash
  cd new
  ./gradlew build
  echo "EXIT=$?"
  ```
  不得用 `gradlew | tail` 之类吞退出码的管道（项目有假绿事故记录）。
- [x] 既有导航、输入穿透、失败隔离和二进制兼容测试全绿。
- [x] 已核对 `new/` 改动只涉及本任务三个源码/测试文件；根仓库并发改动未混入。
- [x] 更新 spec §3.4.4 的锚点段落。
- [x] 修订 `08-21-tutorial-guided-mode-slice` 的覆盖面和填法说明。
- [x] 人工 UI 冒烟未执行，仍保留在任务验收边界中。

## 验证命令

```bash
cd new
./gradlew testClientStructuralRegistration   # 阶段一/二/三/四每批之后
./gradlew build
echo "EXIT=$?"
```

## 已知边界

- 只覆盖一级入口控件。属性字段、右键菜单项、弹层内控件、具体取值（486 个去重加粗片段的大多数）不在范围内。
- 不做 tooltip / L10n 键反查的自动发现（`08-21` 已拒绝，改名会静默失配）。
- 非 Dashboard 屏幕的锚点不做。

## 回滚点

- 阶段一独立可交付：测试加强本身就有价值（挡住未来的常量漂移）。
- 阶段二/三/四各自独立，按阶段提交。只做完 Film + Morphing 也能让教程覆盖一半以上内容。
- 若发现某个面板的目标控件普遍不可达，**停下**记录原因，不要为它改宿主可见性。

## 最终验证记录

- `testClientStructuralRegistration`：通过，`EXIT=0`。
- 故障注入 1：新增未登记常量，测试准确失败，`EXIT=1`；已恢复。
- 故障注入 2：把 `MORPHING_PALETTE_LIST` 的 `panelId` 改为 Film，测试准确失败，`EXIT=1`；已恢复。
- `build`：80 个任务完成，`BUILD SUCCESSFUL`，真实 `EXIT=0`。
- 人工 UI 冒烟未执行：面板切换、滚动裁剪、UI scale/窗口缩放和帧率仍需在游戏内确认。
