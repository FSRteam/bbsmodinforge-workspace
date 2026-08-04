# IRL 灯光插件 NeoForge 1.21.1 实施清单

## 0. 启动门禁

- [ ] 用户审阅 `prd.md`、`design.md`、`implement.md` 并明确同意开始实施。
- [ ] 运行 `task.py start`，确认任务状态从 `planning` 变为 `in_progress`。
- [ ] 加载 `trellis-before-dev`，读取目标层规范和开发前检查项。
- [ ] 保存主工作树 `git status --short` 快照，确认不碰现有无关改动。
- [ ] 再次核对两个输入基线的 HEAD 和洁净状态。

## 1. 新建原生 NeoForge 工程

- [x] 新建 `bbs-irlights-addon`，从仓库已验证插件复制最小 Gradle wrapper/NeoGradle 结构。
- [x] 配置 `src/main`、`src/client` 源码集和最终 JAR 合并，不把客户端类暴露给通用入口。
- [x] 固定 Java 21、Minecraft 1.21.1、NeoForge 21.1.222、Iris 1.8.8、Sodium 0.6.13。
- [x] 添加 BBSFSR `0.0.11` 本地 JAR存在性/新旧检查。
- [x] 创建 `neoforge.mods.toml`，声明 `bbs [0.0.11,)`、Iris 客户端依赖和两个 Mixin 配置。
- [x] 保留两份 MIT LICENSE 归属信息，修正旧 Fabric 元数据中的许可证不一致。

验证点：`gradlew tasks` 能配置成功；缺少 BBS JAR 时错误说明明确。

## 2. 迁移通用 Form 与 Addon 注册

- [x] 复制 `IrliteConfig`、`IrlitePresets`、`PointLightForm`、`SpotlightForm`，保持所有 Value key 和 Form ID 不变。
- [x] 将 `Irlite` 改成 NeoForge `@Mod` 通用入口。
- [x] 在构造阶段调用 `BBSMod.registerAddon("irlite", IrlightsAddon::new)`。
- [x] 给 `IrlightsAddon` 增加 `RegisterFormsEvent` 订阅并保留设置注册/旧配置迁移。
- [x] 删除 Fabric `ModInitializer` 和 `BBSModMixin` 路径。

验证点：`compileJava` 通过；通用源码不导入 `net.minecraft.client`、Iris、Fabric 或 OpenGL 类。

## 3. 客户端注册与资源

- [x] 新增客户端 `@Mod(dist=CLIENT)` 入口和客户端 Addon 事件桥。
- [x] 把 L10n 默认字符串从构造器 Mixin 移到 `RegisterL10nEvent`。
- [x] 在 `onClientStarted` 注册两个 Form renderer、两个 editor panel 和 Extra 分类样例。
- [x] 用隔离反射写入 IRLights 时间线颜色，失败时记录一次 WARN并保留默认样式。
- [x] 迁移 `IrliteClient` 初始化：Patcher、ShadowEngine、cookie sampler、HUD profiler。
- [x] 复制 icon、七个 `.irlights` 补丁和 blue-noise 资源，确保最终类路径与上游读取路径一致。
- [x] 移除 `IrliteDataGenerator` 和所有 Fabric Loader/Fabric API 入口。

验证点：客户端注册幂等；重复启动回调不重复添加分类或处理器。

## 4. 合并并迁移 irl-core

- [x] 将固定提交的 `org.qualet.irl.patcher` 纯 Java代码复制到客户端源码集。
- [x] 将 `org.qualet.irl.light`、`light.shadow`、`light.iris` 复制到客户端源码集。
- [x] 按文件迁移 Yarn 类型到 Mojmap，先完成轻量数学/缓冲类，再完成方块收集、阴影渲染和 FramePipeline。
- [x] 保持 SSBO binding、UBO布局、纹理 target、阴影 atlas、排序和 patch 语义不变。
- [x] 每批运行 `compileClientJava`，不累积跨批次映射错误。

回滚点：patcher、light 基础、shadow、Iris glue 四批分别可回退；不得为通过编译删减灯光功能。

## 5. 迁移插件客户端逻辑

- [x] 迁移 LightCollector、位置解析、cookie、shadow caster 和配置适配。
- [x] 迁移两个 Form renderer、guide renderer、SpotGuideDrag 和编辑面板。
- [x] 迁移 settings preset/debug/patcher UI，核对 BBSFSR 控件签名。
- [x] 迁移诊断 profiler/sweep，但默认保持关闭，不生成额外文件。
- [x] 对照 BBSFSR 当前 `FormRenderingContext`、Film、Replay、ModelBlock API修正调用，不替换 BBSFSR 源码。

验证点：Form 值保存/读取仍由 BBS Value 体系完成；没有新增自定义网络消息。

## 6. Mixin 分组迁移

### 6.1 BBS 与世界 Mixin

- [x] 建立 `irlite.client.mixins.json`，兼容级别 `JAVA_21`。
- [x] 迁移 `UISettingsOverlayPanelMixin`。
- [x] 迁移 `UIFilmControllerMixin` 和 `UIPickableFormRendererMixin`。
- [x] 迁移 `FilmsAccessor` 和 `WorldBlockEntityTickersAccessor`。
- [x] 迁移 `WorldBlockChangeMixin`，用 Mojmap 完整描述符锁定客户端方块通知方法。
- [x] 迁移 `GameRendererLightMixin`：`renderLevel(DeltaTracker)` HEAD 收集，并在 `Camera#setup` 后上传。

### 6.2 Iris Mixin

- [x] 建立 `irlite.client.iris.mixins.json`，仅在 Iris 已加载时启用。
- [x] 迁移四个 Iris Mixin，保持 `remap=false`。
- [x] 编译后核对生成 refmap；启动时开启 `mixin.debug.verbose`，并确认全局 injection count 未启用以避免 Iris 可选注入误报。

验证点：关键 Mixin 使用 `defaultRequire=1`；不通过把关键注入改成可选来隐藏问题。

## 7. 自动验证

按从小到大的顺序执行：

```powershell
cd bbs-irlights-addon
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon compileClientJava
.\gradlew.bat --no-daemon check
.\gradlew.bat --no-daemon build
```

- [x] `rg` 确认业务源码无 `net.fabricmc`、`fabric.mod.json`、Yarn 包名残留。
- [x] 检查 JAR 只含一个 IRL Core 副本，含两类 Form、两个 Mixin 配置、blue noise 和七个补丁。
- [x] 检查 `neoforge.mods.toml` 展开后的版本和 `bbs`/Iris 依赖。
- [x] 对最终 JAR 运行 `jdeps` 或等价类依赖扫描，确认通用入口不直接引用客户端类。
- [x] 运行 `git diff --check`。
- [x] 运行 BBSFSR 最相关的 Addon 生命周期/客户端注册检查，确认没有回归。

## 8. 运行验证

- [x] 用本地 BBSFSR、Iris、Sodium 启动客户端，确认无 Mixin apply、类加载、依赖或注册错误。
- [x] 执行结构化启动检查：Form、renderer、editor、Extra 分类、补丁资源存在。
- [x] 若环境允许，启动专用服务器或执行等价通用类加载检查。
- [ ] 检查 Iris 开关/世界进入/退出 BBS UI 后没有黑屏、闪烁或 GL 状态污染。

运行日志中以下任一项视为失败：`Mixin apply failed`、`InvalidInjectionException`、`NoSuchMethodError`、`NoSuchFieldError`、`NoClassDefFoundError`、OpenGL framebuffer 错误。

## 9. 人工验收队列

- [ ] 创建点光和聚光并编辑全部字段。
- [ ] 保存、退出、重新加载影片，Form 与关键帧不丢失。
- [ ] 拖动聚光灯 guide，数值和画面同步。
- [ ] 验证实体、模型方块、普通方块、半砖/栅栏/透明裁剪方块阴影。
- [ ] 验证 cookie、体积光、outline、阴影开关和四档质量。
- [ ] 对七种支持的 shaderpack 至少完成补丁 dry-run；有对应包时完成应用、Iris reload 和回滚。

无法在当前机器自动完成的项目必须在最终汇报中标为“待人工验收”，不得写成已通过。

## 10. 完成与提交门禁

- [x] 运行 `trellis-check` 并处理所有规格、编译、测试和一致性问题。
- [x] 最终汇报列出产物、验证结果、人工队列和已知风险。
- [ ] 在用户明确确认后，只按路径暂存 `bbs-irlights-addon` 与本任务自己的 Trellis 文件。
- [ ] 暂存后复核 `git diff --cached --name-only`，确保没有主工作树既有改动。
- [ ] 只有复核无误才创建本地 commit；不自动 push。
