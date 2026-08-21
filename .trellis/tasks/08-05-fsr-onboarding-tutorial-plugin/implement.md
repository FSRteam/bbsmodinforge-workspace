# FSR 新手教程 addon 实施计划

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
- [x] 将当前步骤文本、预期画面、提示和角色动作绑定到面板刷新流程。
- [x] 接入男/女文案切换与皮肤路径设置；无女款素材时安全回退到内置男款皮肤。
- [x] 适配现有主题 token（`BBSSettings` 的强调色/文本色/静默色/警告色），面板内容由宿主拥有生命周期，状态不泄漏到其他面板。

## 阶段五：动作化与男女解耦

用户反馈三条：模型要用自带 `emo` 开头的；人物要在面板里做自然动作（指出操作、弯手臂），不要静态姿势；讲解风格与模型款式不该绑在一起。

- [x] 换模型到 `emoticons/steve` / `emoticons/alex`。这是三条需求的同一把钥匙：`BOBJModelLoader` 只给 `emoticons/` 前缀注入 `actions.bobj` 的 85 个动画，也只有这批骨架带 `low_left_arm` / `low_right_arm`，`player/*` 上「弯手臂」物理上做不到。
- [x] 新增 `TutorialActions` 白名单（16 个：`idle` + 15 个手势 emote），解析期 `orIdle` 降级——`Animator` 对缺失动画静默跳过，不降级会让上一个手势一直播。
- [x] `content.json` 与八个骨架的 `pose` 字段迁移为 `action`，9 种静态姿势按语义一对一映射到 emote（343 步全覆盖，无未映射项）。
- [x] `UITutorialCharacter` 移除整套 `poses.json` / `PoseManager` / pose group 回退链，改为写 `ActionsConfig` 的 `idle` slot；覆写 `update()` 调 `form.update(entity)` 推动画时钟。
- [x] 拆 `TutorialGender`（只留 id / label，决定台词）与新增 `TutorialLook`（模型 / 皮肤），面板两个独立切换按钮，`TutorialProgress` 两个独立字段，旧进度文件按 `matching(gender)` 迁移。
- [x] 语言文件补 `look` / `look.male` / `look.female` 三键（27 键），`testTutorialWiring` 的枚举键展开改为按 `ENUM_SOURCES` 映射的通用形式，加第三组枚举只需加一行。
- [x] `testTutorialContent` 校验每步 `action` 在白名单内；`testTutorialWiring` 新增手势检查，逐项核对宿主 jar 内 `/assets/bbs/assets/actions.bobj` 的 `^an <name>$`（FSR jar 进 `contentTestRuntimeOnly`，只读资源不加载 BBS 类）。
- [x] 两条反例验证：白名单塞假名字 → FAIL exit 1；删 `en_us` 的 `look.female` → FAIL exit 1。均已恢复并重建通过（真实 exit code 0）。

## 阶段六：验证与收尾

- [x] 运行 JSON/结构校验、Java 编译、jar 审计和可执行的逻辑测试。
- [x] 新增 `testTutorialWiring`：钉住反射入口、面板 id 合法性与 descriptor 契约——这三项编译期无法发现，只会表现为「面板在游戏里不存在」。
- [x] 接入 chrome 多语言：27 个 UI 键走延迟 `IKey` + `L10n`，随包 `zh_cn` / `en_us`，provider 在 client-started 注册并按 `TRANSLATED_LANGUAGES` 过滤语言。
- [x] 扩展 `testTutorialWiring` 覆盖语言契约（缺键 / 空值 / `%s` 数量 / 死键 / 声明语言与随包文件一致），并用反例确认它真的会失败。
- [x] 对无法自动验证的客户端视觉、热替换和文件选择器场景明确记录为人工冒烟边界。
- [x] 按实际实现重写 `prd.md` 与 `design.md`：热插件方案作废理由、52 章 343 步、addon 契约、语言层设计。
- [x] 按动作化与男女解耦再次同步 `prd.md` / `design.md` / `implement.md`。
- [x] 更新相关 spec 与 Trellis 任务状态，完成前不得提交与本任务无关的工作树改动。

## 阶段七：多语言

- [x] 核实 `L10n.getKey(key)` 的缺失键语义：返回的 `LangKey.get()` 是 `content` 而非 key，所以「缺键」不会抛异常，只会静默显示 fallback——英文玩家会看到中文。这正是需要契约测试的原因。
- [x] `TutorialKeys` 全部改为延迟 `IKey`，在 `get()` 时读当前 `L10n`（spec 在 mod 构造期创建，L10n 那时不存在；面板挂载中可切语言）。
- [x] `UITutorialPanel` 与 `TutorialSkinPicker` 的内联中文 chrome 全部替换为语言键，含三处 tinyfd 对话框文案。
- [x] 新增 `strings/zh_cn.json`、`strings/en_us.json`，27 键齐备，审计确认已进 jar。
- [x] provider 用 `register(BBSTutorialClient::translationLinks)` 而非 `registerOne`：只对随包语言返回 Link，避免 `L10n.reload()` 为不存在的语言文件打印堆栈。

## 人工冒烟边界

裸 JVM 与 Gradle 覆盖不到、必须真机确认的项（当前**未执行**）：

1. Dashboard 出现教程面板按钮，打开后左侧人物出镜、右侧显示当前步骤。
2. 人物持续在动，手臂确实弯曲（下臂骨受驱动），不是静止姿势。裸 JVM 只能确认动画名存在，动没动看不到。
3. 上一步 / 下一步切换时动作与文本同步，且动作交叉淡入而非硬切（`ensureAnimator` 的 `fade=true` 路径只能真机验证）。
4. 讲解风格与模型款式两个按钮互不牵连：男版台词 + 女款模型可设，重开后保留。
5. 旧进度文件（没有 `look` 字段）升级后模型款式与升级前一致。
6. 关闭重开 Dashboard 后进度保留；跳过 / 恢复切换正常。
7. 切换 zh_cn ↔ en_us 后已挂载面板文本即时更新；换到第三种语言只落英文且日志无缺文件堆栈。
8. 切换 dark / light / strawberry 主题后卡片颜色与圆角跟随。
9. tinyfd 皮肤选择器：选中合法 PNG 即时换装；尺寸不符时弹拒绝框并保持原皮肤；同名文件重选像素确实更新。
10. 删除 addon jar 后 Dashboard 不残留教程按钮。

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
- 首版只随包男款皮肤 `tutorial_male.png`；选女款模型时保留纤细模型与所选台词，纹理回退到男款。
- 343 步正文不进语言文件，只有 27 条 UI chrome 有中英两版；英文玩家读到的步骤正文仍是中文，这是内容分工而非缺陷。
- 动作只用了白名单里的 9 个（由 9 种旧姿势一对一映射而来），另外 7 个是留给后续脚本增补的余量。逐步精修动作与台词的匹配度不在本轮范围。
- `assets/emotes/` 在宿主里是空目录，全部动画来自 `actions.bobj` 单文件；若宿主后续往那里加动画，白名单检查只读 `actions.bobj`，会漏掉新来源。
- 交付形态从热插件改为独立 addon，因此不再支持「覆盖 jar 即热更文案」，改文案需重启客户端。作废理由记在 `prd.md` §Context。

## 回滚点

- 内容生成阶段只新增/修改任务目录中的教程数据，不触碰主工程源码。
- 插件实现阶段按文件组提交前检查 `git diff --name-only`，发现混入其他会话改动时停止暂存并按路径隔离。
- 若 Dashboard 新 API 尚未可用，保留事件降级实现，不阻塞内容数据与独立工程构建。
