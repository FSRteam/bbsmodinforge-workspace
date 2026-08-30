# BBS++ → FSR NeoForge addon 实施计划

## 阶段 0：基线和隔离

- [ ] 创建独立 addon 目录和 NeoForge userdev 工程；不触碰 `new/` 和现有未提交改动。
- [ ] 克隆/下载上游并固定提交 `9e2235ba3f4ddf19501815df452bbc15aed2c865`，记录源码清单、许可证、README 功能清单和 SHA。
- [ ] 记录 FSR 本地 JAR 版本、API/Addon 入口、已有 addon 模板和可选依赖探测方式。
- [ ] 建立功能矩阵：上游类/功能 → FSR API、NeoForge 事件、Mixin 或明确降级结论。

## 阶段 1：映射辅助和 NeoForge 骨架

- [ ] 在临时 Fabric Loom 工程运行 `migrateMappings`，保存 Yarn → Mojang 辅助结果。
- [ ] 建立 NeoForge `gradle.properties`、`build.gradle`、`settings.gradle`、`neoforge.mods.toml`、common/client 源集和 Mixin 配置。
- [ ] 接入 Java 21、Minecraft 1.21.1、NeoForge 21.1.222 和 FSR 本地 JAR；增加依赖存在性/版本防呆任务。
- [ ] 完成 common/client addon 构造期注册；验证最小 addon 能启动、注册和退出。

## 阶段 2：common 基础功能

- [ ] 迁移设置模型、配置持久化、运行状态和基础命令。
- [ ] 迁移公共 API、关键帧轨道定义、Form/数据结构和网络/生命周期接线。
- [ ] 迁移资源包、许可证、图标和语言基础结构。
- [ ] 编译 common；运行 dedicated server，确认没有客户端类加载。

## 阶段 3：客户端编辑器和关键帧

- [ ] 迁移关键帧轨道中文化和 fallback 语言键。
- [ ] 迁移姿势骨骼树、搜索、骨骼参数刷、改动骨骼标识、跳过骨骼关键值。
- [ ] 迁移 BBS++ 专用剪贴板、时间线滚轮、剪辑拖动、标尺/播放头和撤销记录增强。
- [ ] 迁移新影片库、新伪装布局、动画转姿势搜索、音效树、纹理管理器和双击剪辑编辑。
- [ ] 每个 UI/Mixin 组完成客户端启动和目标操作 smoke test。

## 阶段 4：Form、粒子和媒体功能

- [ ] 迁移物品喷射 Form、编辑器、渲染器、模拟参数、距离/数量限制和 IRLights 阴影边界。
- [ ] 迁移 AAA 粒子 Form、触发指令、选择界面持久化和起止帧保护；无 AAA 依赖时安全禁用。
- [ ] 迁移结构棒/结构伪装和群系相关能力；对照现有 FSR Form API 避免重复实现。
- [ ] 迁移视频伪装；MediaPlayer 缺失或非 Windows x64 时只禁用并给出诊断。
- [ ] 完成 Form 序列化/反序列化、编辑器打开、世界播放和重载验证。

## 阶段 5：光影、渲染和外部兼容

- [ ] 迁移光影控制按钮和新光影曲线选择器。
- [ ] 迁移世界播放光影曲线、太阳/天气/天空色和 Iris 参数曲线。
- [ ] 迁移 UV/图片伪装、PBR/纹理过滤、发光色块、模型贴图和颜色膨胀修复。
- [ ] 分离 Iris/Sodium、IRLights、VFX/VFX Lights、Together/Xavin 等兼容配置和 classloading 边界。
- [ ] 在 vanilla、Iris/Sodium、逐个可选依赖环境中分别启动；核对 Mixin 应用和渲染路径。

## 阶段 6：全量修复项和差异收口

- [ ] 按上游 README/提交清单逐项核对所有“原版增强”和“原版修复”。
- [ ] 对每个无法一比一实现的功能记录原因、替代方式、影响和是否需要用户接受；完整等价目标下不得静默删减。
- [ ] 清理 Fabric 命名空间、旧入口、旧 refmap、无效依赖和未使用资源。
- [ ] 检查 LGPL 许可证、作者署名、来源说明和发布元数据。

## 阶段 7：质量验证和交付

- [ ] 执行 `./gradlew.bat --no-daemon compileJava compileClientJava --console=plain`。
- [ ] 执行 addon 的 `check`、`build` 和必要的专用验证任务；确认执行的是测试任务而非仅测试类编译任务。
- [ ] 执行 `git diff --check`，检查 release JAR 内容：NeoForge 元数据、Mixin/资源、无 Fabric 元数据、无 BBS 核心类。
- [ ] 启动真实 NeoForge client，验证 API READY、无 MixinApplyError、无客户端 classloading 错误、无重复 source pack/L10n 报错。
- [ ] 启动 dedicated server，验证 common 注册和可选依赖缺失路径。
- [ ] 完成关键功能手动 smoke：设置、关键帧编辑、姿势树、剪贴板、时间线、影片库、伪装、物品喷射、AAA、结构、视频、光影曲线、Iris/Sodium 兼容。
- [ ] 将验证命令、日志摘要、JAR 审计、剩余风险和功能矩阵状态写回任务文档；通过质量门后再请求 `task.py start`/进入实现阶段或提交变更。

## 风险文件和回滚点

- 风险最高：`build.gradle`、NeoForge 元数据、Mixin 配置、渲染/光影类、物品喷射与 AAA Form、视频伪装和可选 compat 包。
- 回滚点：上游快照、映射完成、NeoForge 骨架启动、common 编译、每组 client Mixin、每个复杂 Form、vanilla/Iris 启动、发布 JAR 审计。
- 禁止用降低 Mixin `require`、吞掉异常或强行跳过测试来“修复”启动失败；必须定位目标漂移或可选依赖边界。

## Runtime Incident Record

- 2026-08-24：外部 NeoForge 1.21.1 实例加载旧版 `BBS-PlusPlus-FSR-2.6.3-fsr.1.jar` 后启动崩溃。
- 根因：`UIPropTransformBlockbenchMixin` 注入 `mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform.enableMode(I)V`，FSR 0.0.11 已不存在该方法；日志首个致命错误为 `InvalidInjectionException`，不是 Sodium/C2ME/Iris 的普通警告。
- 修复：从 `bbsplusplus.mixins.json` 移除该 Mixin，并删除对应源码；新增 `verifyNoStaleMixinTargets` 构建门，防止旧目标再次进入发布 JAR。
- 修复版 JAR：`BBS-PlusPlus-FSR-2.6.3-fsr.1.jar`，SHA-256 `120F1BF90A08C406F62F1ADAA0EEC86219DB21BE4FC8A50665B63628656BD4AB`。
- 已验证：`build` 成功；发布 JAR 含 `META-INF/neoforge.mods.toml` 和 `bbsplusplus.mixins.json`，不含 `fabric.mod.json`，配置中不含 `UIPropTransformBlockbenchMixin`。
- 外部实例处理：用修复版 JAR 替换旧文件后重新启动；旧文件应先改名保留备份，避免两个同 mod id 的 JAR 同时存在。

## FSR 2.5 Sync Baseline

- 2026-08-26：后续 addon 编译暂时改用 `../new-fs25-sync/build/libs/BBS_FSR_beta_v0.0.11.jar`。
- `new-fs25-sync` 与当前 `new` 同为 Minecraft 1.21.1 / NeoForge 21.1.222，但它是待合并的 FS 2.5 同步分支；addon 最终合入主线前需把该依赖路径切回 `../new/` 并重新跑完整构建与客户端验证。
- 已完成一轮 FS 2.5 API 修正：结构保存改用 `Path`，结构资源 ID 改用 `ResourceLocation.fromNamespaceAndPath`，姿势标记渲染改用 1.21 `Tesselator.begin` / `BufferBuilder.build`，移除 Fabric 物品栏事件依赖。
- 已补入姿势骨骼状态/插值/树形元数据/参数刷基础类、UV API 和结构保存基础类；当前源码规模为 172 个 Java 文件。
- `new-fs25-sync` 依赖下 `compileJava`、`build` 和 `verifyNoStaleMixinTargets` 已通过；真实客户端加载 BBS 0.0.11 与 BBS++，完成 BBS addon 注册、ItemSpray Form 注册、资源重载和集成服进入，无新的 MixinApplyError。
- 当前运行日志仍有 GLFW clipboard 转换错误（65545），属于私有剪贴板轮询与 Windows 外部剪贴板编码交互问题，未阻塞启动；后续单独处理，不能当作功能验收通过。
- 已继续补回视频伪装编辑器/选择器、结构表单渲染与命名界面、物品喷射调试和模拟相关实现、姿势/UV 基础类；当前 addon 源码规模为 219 个 Java 文件。
- 已将 1.21.1 buffer、NBT、ResourceLocation、屏幕背景渲染和 FS 2.5 相机 API 差异修正到可编译状态；删除与现有 `ItemSprayFormRenderer` 重复且未被主路径使用的旧全局喷射辅助类，避免两套状态模型冲突。
- 剪贴板修复已落盘：`WindowClipboardMixin` 读取系统剪贴板时转由 Minecraft `KeyboardHandler` 处理，规避 GLFW 65545；普通文本写入逻辑保持不变。
- 最新 `new-fs25-sync` 依赖下 `compileJava` 与 `build` 通过；发布 JAR SHA-256 为 `C4463FB85D1971536AF3A43E72676DA90F9185A13F7C56B36E9339DA5B762167`，测试 `runs/client/mods` 已同步该 JAR 和 FS 2.5 BBS JAR。
- 2026-08-27：真实客户端回归时发现 `runs/client/mods` 中的旧 BBS++ JAR 会与开发 classpath 重复加载，导致已移除的 `ModelFormRendererMixin` 继续生效。验证期间暂时移出重复 JAR 后，源码版本完成 BBS++ 初始化、Pose/Model/Extruded Form Mixin、资源重载和 ItemSpray/Video Form 注册；测试目录随后恢复最新 JAR。
- 2026-08-27：`new-fs25-sync` 基线下，当前源码 `compileJava` 与 `build` 通过；最新 JAR SHA-256 为 `C4463FB85D1971536AF3A43E72676DA90F9185A13F7C56B36E9339DA5B762167`。

## 2026-08-27 Structure/VFX Follow-up

- 修复结构功能漏注册：`bbs:structure` 通过 `RegisterFormsEvent` 注册，`StructureForm` 通过 `RegisterFormCategoriesEvent` 进入 BBS 表单选择器。
- 修复结构棒漏注册：改用 NeoForge `DeferredRegister` 注册 `bbs:structure_stick`，并在 `BuildCreativeModeTabContentsEvent` 中加入 `bbs:main` 创造模式物品栏。
- 补齐结构棒模型、贴图、声音和中英文语言键；加入基础 NeoForge 框选、线框预览和本地 `.nbt` 导出桥。
- 将 AAA 粒子、Iris 光影曲线、BBS-VFX 选区和 IRLights 投影注入拆成独立可选 Mixin 配置；当前目标为 `new-fs25-sync` 的 NeoForge 类名。

## 2026-08-28 AAA/ShaderCurves Verification

- AAA 粒子完整性确认：对照上游 2.6.3，表单字段、FormArchitect 注册、Extra 分类、渲染器、编辑面板、特效选择器、AAA_EFFECT 关键帧、四个动态输入、四个触发器、循环/起止帧/智能定格/速度/缩放、模型方块源跟踪、时间轴寻帧、X-Ray 透深度和资源重载清理链路均已保留。
- 使用本地 `aaa-particles-neoforge-1.21.1-2.2.0.jar`（内置 Architectury 13.0.8）进行真实客户端验证：`AAAParticleForm` 已注册，`bbsplusplus-aaa.mixins.json` 的四个注入均成功应用，Effekseer 原生库加载，外部 `config/bbs/assets/effeks/debug.efkefc` 成功解析并注入 AAA 注册表。客户端无 Mixin 致命错误；关闭阶段先清理 addon 发射器，再交给 AAA 关闭原生库。
- AAA 稳定性修复：选择器打开文件夹改为非阻塞进程回调；客户端停止阶段清理所有 addon 发射器；`EffectDefinitionMixin` 的最终字段标注补齐。缺失 AAA 前置时主 addon 仍可启动并跳过 AAA 能力。
- 光影曲线完整性修复：新增 NeoForge `IrisCompat`，光影曲线选择器的日月偏角与焦点条目改为由 Iris 存在性控制，不再错误依赖旧 `bbstools`。
- 光影曲线 OpenGL 状态修复：焦点深度纹理事务保存/恢复活动纹理、纹理绑定、读写 FBO、清屏颜色；深度值有限性与 `[0,1]` 限幅；移除每帧 `glFinish`；客户端停止时释放临时纹理/FBO。
- 使用本地 Complementary 光影包进行 Iris 组合启动验证：`ShaderCurvesMixin`、`IrisRenderingPipelineMixin`、`CelestialUniformsMixin`、`CenterDepthSamplerMixin`、`ShadowRendererMixin` 全部成功应用，Iris 管线创建完成，客户端正常退出且 Gradle 返回成功。由于当前显卡不适合长时间运行光影，太阳角度动画、阴影方向和拉焦视觉效果需在另一台支持光影的电脑做最终手动 smoke。
- 最终 addon 构建：`clean build` 成功；发布 JAR `build/libs/BBS-PlusPlus-FSR-2.6.3-fsr.1.jar`，SHA-256 `F40C23AA4BB5A061D4A48FA053B52ABFC8A218E129971E370E5B29F6CAD3F39D`；JAR 不含 `fabric.mod.json`、`mchorse/bbs_mod/**` 或开发 smoke 类。

## 2026-08-28 Curve Localization Follow-up

- 修复曲线新增后轨道标题退回原始通道 ID 的问题：`UIKeyframeSheetMixin` 同时覆盖 FSR 的六参数和七参数构造器，对 `brightness`、`sun_rotation`、`weather`、`chroma_sky_color`、Iris `sun_path_rotation`、`center_depth` 以及 `curve.*` 动态参数统一使用本地化标题。
- 新增 `CurveChannelNames`，动态 Iris 参数按当前语言查找，当前语言缺失时回退英语，最后才回退原始 ID；不依赖中文开关。
- 内置 Iris 曲线显示为“太阳角度 / 阴影方向”和“焦点深度”，英文对应 `Sun Angle / Shadow Direction`、`Focus Depth`；基础 `sun_rotation` 显示为“太阳角度 / Sun Angle”。
- `compileJava` 与 `build` 均通过；发布 JAR SHA-256 为 `0D9674B85EDABED873F1C0A7D8D8B8505B47BC27E35ABA99E466394FC7297B83`。未启动游戏，光影视觉效果留给支持光影的测试机验证。

## 2026-08-28 Localization Audit Follow-up

- 补齐模型、广告牌和挤出形态的 UV 控件语言键：`uv_transform`、U/V 偏移、U/V 缩放、统一缩放和 UV 旋转。
- 补齐视频广告牌编辑器、视频选择器、视频预设、姿势参数刷，以及视频/UV 关键帧的中英文资源；视频关键帧标题按 `VideoBillboardForm` 上下文解析，避免 `width`、`loop` 等通用属性互相覆盖。
- 恢复 UV 关键帧扩展注册和 `bbspp_uv_*` 中文兜底映射。源码扫描的可见字面量语言键审计结果为 `MISSING_VISIBLE_LITERAL_KEYS=0`；隐藏设置仅保留为内部配置，不计入可见 UI 审计。
- `compileJava`、`build`、JSON 解析和 `git diff --check` 均通过；最新发布 JAR SHA-256 为 `B4D72FF3CC099B537AB69D8BFD404957A58291506595B43FEDB037E003DE21D6`。未启动游戏。
