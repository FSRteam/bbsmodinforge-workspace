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
