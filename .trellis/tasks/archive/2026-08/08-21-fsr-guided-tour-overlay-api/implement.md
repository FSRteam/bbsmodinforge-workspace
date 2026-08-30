# FSR 宿主侧教程引导覆盖层 API · 实施计划

## 前置：先读，再动手

1. `prd.md`（需求与验收）与 `design.md`（**§0 三条必读事实**）。
2. `.trellis/spec/new/backend/external-addon-integration-contract.md` §3.4.2 / §3.4.3——现有 addon 面板契约与动画预览契约，新能力要与它们同构。
3. 宿主源码：`UIDashboard.java`、`UIDashboardPanels.java`、`BBSClientApi.java`、`BBSDashboardPanelHostRegistry.java`、`UIExtensionDashboardPanel.java`、`UIChalkboard.java`。

工作目录是 `new/`（BBSFSR 宿主工程）。**`new/` 有其他会话在并发修改**，遇到与本任务无关的编译错误先排除他人改动，提交时按路径隔离。

## 阶段一：设计定型

- [x] 锚点方案在 `design.md` §1.2 的 A / B / A+B 里选定，把理由写回 §1.2。这个决定影响后面所有工作量，先定。
- [x] 确定覆盖层与 `UIChalkboard`、`menu.overlay` 弹层、taskbar 自动隐藏的层级顺序，写回 `design.md` §1.3。
- [x] 确定导航 future 的完成语义（「已排入切换」还是「切换已完成」）。注意 `setPanel` 经 `runAfterCapturedMouseRelease` 延后，选后者需额外挂钩。写回 §1.1。
- [x] 命名与包位置对齐既有风格：值对象放 `api/client/`，宿主实现放 `client/`，参照 `BBSDashboardPanelSpec` / `DashboardPanelContribution` 的分层。

## 阶段二：面板导航

- [x] 引入内置面板 id 常量表，与 `UIDashboard.registerPanels()` 的 9 个面板对应（Sandbox 仅开发环境注册，需决定是否给 id 并说明）。
- [x] 实现按 id 解析目标面板：内置走 id 表，addon 面板走 `<addonId>:<localId>`（`UIDashboard.extensionPanels` 已按 `fullId()` 建索引）。
- [x] `BBSClientApi` 新增导航入口，线程模型照 `BBSUiOpenDispatcher`：任意线程提交、客户端线程执行、返回 future。不新造并发模型。
- [x] 三种失败可区分：Dashboard 未创建 / 不在 Dashboard 屏幕 / id 不存在。均返回结果，不抛异常。
- [x] 诊断记录接 `BBSMod.recordAddonClientDiagnostic`，phase 与既有 API 一致。

## 阶段三：控件锚点解析

- [x] 按阶段一选定的方案实现锚点登记或解析。
- [x] 返回值为值对象：屏幕矩形 + 可用性状态。**不返回 `UIElement`。**
- [x] 不可用状态至少区分：目标面板未挂载 / 控件不存在 / 控件不可见（`isVisible()`）/ 被滚动出视口（`canBeRendered` + `Area.intersects`）。
- [x] 每次查询重新解析，不缓存矩形（UI scale 可变、面板可滚动）。
- [x] 首版覆盖教程 ch01–ch05 涉及的锚点：9 个 Dashboard 面板按钮 + 各面板主入口控件。范围以 `08-05` 的 `content.json` 前五章 `instruction` 字段为准。

## 阶段四：覆盖层槽位

- [x] 定义 addon 侧内容接口，形状对标 `BBSDashboardPanelContent`：只要求 root，其余回调给默认实现。
- [x] 宿主侧包装类照 `UIExtensionDashboardPanel`：拒绝已有 parent 的 root，每个回调 try/catch 后 `reportFailure(phase, error)`。
- [x] 宿主注册表照 `BBSDashboardPanelHostRegistry`：注册表 + 投影 + 失败回滚 + Dashboard 构造期统一投影（对应 `installAll`）。
- [x] 挂载点是 **root 层**，照 `UIChalkboard` 的 `full(getRoot())` + `getRoot().add(...)`。**不得挂在任何面板下**（`design.md` §0.1）。
- [x] 输入穿透：默认只有覆盖层自身可见子元素区域吃事件。用 `mousePropagation` / `EventPropagation` 实现，`UIRenderable` 天然不吃输入可用于纯绘制部分。
- [x] 显式的显示/隐藏与注销；注销后 root 上无残留。
- [x] 注册链与诊断照 `registerDashboardPanel`（validate → 投影 → `REGISTER_CLIENT` / `CLIENT_SETUP`）。

## 阶段五：测试

沿用 `new/` 既有测试组织方式，不引入新框架。裸 JVM 测试不得触发 `BBSMod` 静态初始化。

- [x] 内置面板 id 表与 `registerPanels()` 双向一致（缺 id、多余 id 都失败）。
- [x] 锚点标识与宿主控件对应关系（宿主改布局/改键应失败，而非静默指错）。
- [x] 输入穿透：覆盖层可见区域外的点击到达底层面板。
- [x] 失败隔离：内容工厂抛异常 / 注册被拒 / 注销后，Dashboard 可用且无残留。
- [x] 每项新增检查都用**反例**验证它真的会失败（缺失登记、重复注册、工厂/生命周期异常、空白区与交互区点击、幂等注销均有相反场景断言）。

## 阶段六：验证与收尾

- [x] `./gradlew build` 通过，**确认真实 exit code 为 0**。不得用 `gradlew | tail` 之类吞退出码的管道判断（项目已有假绿事故记录）。
- [x] `git status --porcelain` 已分类；`new/` 本任务源码路径明确，其他并发会话改动与生成物不纳入本任务。
- [x] 更新 `.trellis/spec/new/backend/external-addon-integration-contract.md`：新增一节（放在 §3.4.3 之后），含三项能力的契约、层级关系、输入穿透默认值、future 语义、锚点稳定性保证。
- [x] 修订 `.trellis/tasks/08-05-fsr-onboarding-tutorial-plugin/prd.md` 的 Out of Scope 第一条。
- [x] API 形状若与 `design.md` §1 偏离，回写文档。
- [x] 记录人工冒烟边界（见下文），明确标注未执行。

## 验证记录

- 2026-08-21：项目指定 Gradle 9.7.0 执行 `build`，`BUILD SUCCESSFUL`，80 个任务，真实 `EXIT=0`。
- 专项覆盖：客户端编译、结构注册、Addon API 二进制兼容、addon 生命周期、UI 输入邮箱全部通过。
- `git diff --check` 通过。

## 人工冒烟边界（未执行）

以下项目需要启动 Minecraft 客户端并加载真实 addon，本次没有执行，不能用裸 JVM 测试替代：

- addon 导航后实际切到目标面板，跨面板覆盖层人物继续渲染和播放动作。
- 窗口缩放、UI scale 改变、面板滚动后，箭头/高亮仍对准目标控件。
- 覆盖层区域外可正常点击按钮、拖动时间轴。
- 切换主题与语言后，覆盖层外观和文本同步变化。
- 删除 addon jar 后，Dashboard 无覆盖层残留且无异常日志。

## 验证命令

```bash
cd new
./gradlew build
echo "EXIT=$?"        # 必须显式确认，不能靠管道尾部输出判断
```

## 已知边界

- 覆盖层跨屏幕（非 Dashboard 的 BBS 菜单）不在范围内。
- 热插件路径不提供等价能力。
- 锚点首版只覆盖 ch01–ch05；后续章节按需增补。
- 不提供合成输入，读者必须自己点。

## 回滚点

- 阶段二/三/四各自独立可编译，按阶段提交。
- 覆盖层若层级或穿透方案被推翻，导航与锚点两项仍可独立交付（教程届时退化为「自动跳面板 + 面板内提示」，仍优于现状）。
- 发现需要改动现有 `BBSClientApi` 签名或 `BBSDashboardPanelContent` 行为时**停下**：那超出本任务的增量约束，需要重新决策。
