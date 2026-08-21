# FSR 新手教程热插件实施计划

## 当前基线

- 运行时权威内容为 `assets/bbstutorial/assets/tutorial/content.json`（52 章 343 步，`_meta.version` 3.0.0）；`tutorial-script.json` 与 `chapters-*-skeleton.json` 是编辑源，不在运行期读取。
- 交付形态是独立 NeoForge addon，不是热插件；面板走 `BBSClientApi.registerDashboardPanel`。
- `chapter-plan.json` 与后续骨架的章节命名/主题已分叉，实施时以已验证骨架和运行时代码契约为准，不把两套内容静默混合。
- 工程位于独立的 `bbs-tutorial-plugin/`，不把该工程源码移入 workspace 主工程。

## 阶段一：内容收敛

- [x] 根据 `chapter-plan.json` 为 ch46–ch52 生成骨架，覆盖导出、导入、设置、主题、插件、选择器/图形计算器、音频/语言编辑器等未覆盖域。
- [x] 为新增骨架运行 `_check.py`，确认章节/步骤 ID 唯一、顺序连续、必需字段完整。
- [x] 将 ch01–ch52 的章节数据收敛成运行时 `content.json`；保留原始骨架作为编辑源，不直接依赖任务目录运行。
- [x] 校验运行时数据总章节数、步骤数、字段降级行为与 `TutorialContent` / `TutorialStep` 解析器一致。
- [x] 补齐 ch01–ch22 缺失的 147 条男/女台词，改为手写而非模板化生成。

## 阶段二：插件基础与面板接入

- [x] 核对 `bbs-tutorial-plugin` 的 NeoGradle、manifest、资源路径和 BBS API 依赖。
- [x] 完成教程面板注册：`BBSClientApi.registerDashboardPanel` 是 FSR 的正式面板 API，宿主在 `UIDashboard` 构造期通过 `BBSDashboardPanelHostRegistry.installAll` 统一投影，因此不需要 `RegisterDashboardPanelsEvent` 降级路径。
- [x] 补上缺失的客户端入口 `BBSTutorialClient`（`BBSTutorialClientStarter` 反射引用的类此前不存在）。
- [x] 让 Dashboard 能打开教程面板，空内容或坏内容只记录日志，不阻断 Dashboard。
- [x] 运行独立工程的编译、jar 构建和产物内容审计。

## 阶段三：教程交互

- [x] 接入章节/步骤导航、上一页/下一页、跳过和进度文件读写。
- [x] 将当前步骤文本、预期画面、提示和角色姿势绑定到面板刷新流程。
- [x] 接入男/女文案切换与皮肤路径设置；无女款素材时安全回退到内置男款皮肤。
- [x] 修正女版角色姿势来源：`player/alex` 不带 `poses.json`，改为按模型声明的 pose group 解析并回退到 `player/steve`。
- [x] 适配现有主题 token（`BBSSettings` 的强调色/文本色/静默色/警告色），面板内容由宿主拥有生命周期，状态不泄漏到其他面板。

## 阶段四：验证与收尾

- [x] 运行 JSON/结构校验、Java 编译、jar 审计和可执行的逻辑测试。
- [x] 新增 `testTutorialWiring`：钉住反射入口、面板 id 合法性与 descriptor 契约——这三项编译期无法发现，只会表现为「面板在游戏里不存在」。
- [x] 接入 chrome 多语言：24 个 UI 键走延迟 `IKey` + `L10n`，随包 `zh_cn` / `en_us`，provider 在 client-started 注册并按 `TRANSLATED_LANGUAGES` 过滤语言。
- [x] 扩展 `testTutorialWiring` 覆盖语言契约（缺键 / 空值 / `%s` 数量 / 死键 / 声明语言与随包文件一致），并用反例确认它真的会失败。
- [x] 对无法自动验证的客户端视觉、热替换和文件选择器场景明确记录为人工冒烟边界。
- [x] 按实际实现重写 `prd.md` 与 `design.md`：热插件方案作废理由、52 章 343 步、addon 契约、语言层设计。
- [ ] 更新相关 spec 与 Trellis 任务状态，完成前不得提交与本任务无关的工作树改动。

## 阶段五：多语言

- [x] 核实 `L10n.getKey(key)` 的缺失键语义：返回的 `LangKey.get()` 是 `content` 而非 key，所以「缺键」不会抛异常，只会静默显示 fallback——英文玩家会看到中文。这正是需要契约测试的原因。
- [x] `TutorialKeys` 全部改为延迟 `IKey`，在 `get()` 时读当前 `L10n`（spec 在 mod 构造期创建，L10n 那时不存在；面板挂载中可切语言）。
- [x] `UITutorialPanel` 与 `TutorialSkinPicker` 的内联中文 chrome 全部替换为语言键，含三处 tinyfd 对话框文案。
- [x] 新增 `strings/zh_cn.json`、`strings/en_us.json`，24 键齐备，审计确认已进 jar。
- [x] provider 用 `register(BBSTutorialClient::translationLinks)` 而非 `registerOne`：只对随包语言返回 Link，避免 `L10n.reload()` 为不存在的语言文件打印堆栈。

## 人工冒烟边界

裸 JVM 与 Gradle 覆盖不到、必须真机确认的项（当前**未执行**）：

1. Dashboard 出现教程面板按钮，打开后左侧人物出镜、右侧显示当前步骤。
2. 上一步 / 下一步切换时姿势与文本同步；女版讲解风格的姿势确实生效（pose group 回退链只能真机验证）。
3. 关闭重开 Dashboard 后进度保留；跳过 / 恢复切换正常。
4. 切换 zh_cn ↔ en_us 后已挂载面板文本即时更新；换到第三种语言只落英文且日志无缺文件堆栈。
5. 切换 dark / light / strawberry 主题后卡片颜色与圆角跟随。
6. tinyfd 皮肤选择器：选中合法 PNG 即时换装；尺寸不符时弹拒绝框并保持原皮肤；同名文件重选像素确实更新。
7. 删除 addon jar 后 Dashboard 不残留教程按钮。

## 验证命令

```bash
python _check.py chapters-01-10-skeleton.json
python _check.py chapters-11-20-skeleton.json
python _check.py chapters-19-25-skeleton.json
python _check.py chapters-46-52-skeleton.json
cd ../../../../bbs-tutorial-plugin
./gradlew build     # 含 testTutorialContent + testTutorialWiring
```

## 已知边界

- `_check.py` 会对 instruction 里加粗的教学描述（例如「左右拖动鼠标」「限制关节能转到的角度范围」）报「疑似非真实 UI 名称」。这些不是控件名，属于误报；八个骨架文件的结构性 errors 均为 0。
- 首版只随包男款皮肤 `tutorial_male.png`；选择女性讲解风格时保留纤细模型与女版台词，纹理回退到男款。
- 343 步正文不进语言文件，只有 24 条 UI chrome 有中英两版；英文玩家读到的步骤正文仍是中文，这是内容分工而非缺陷。
- 交付形态从热插件改为独立 addon，因此不再支持「覆盖 jar 即热更文案」，改文案需重启客户端。作废理由记在 `prd.md` §Context。

## 回滚点

- 内容生成阶段只新增/修改任务目录中的教程数据，不触碰主工程源码。
- 插件实现阶段按文件组提交前检查 `git diff --name-only`，发现混入其他会话改动时停止暂存并按路径隔离。
- 若 Dashboard 新 API 尚未可用，保留事件降级实现，不阻塞内容数据与独立工程构建。
