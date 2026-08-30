# 执行清单：IRLights 上游同步与 FSR 兼容

## 规划完成前

- [x] 核验远端 `port/1.21.1` 提交和本地 README 基线。
- [x] 比对当前 iterationRP 补丁，确认光束强度是“声明存在但未注册”的设置列表缺陷。
- [x] 读取外部 NeoForge Addon、共享跨层思考指南和历史 IRLights 端口设计。

## 实施顺序

1. 记录远端浅克隆的提交号、文件清单和本地/上游差异。
2. 对共享 Java/资源文件执行语义 diff；只合入对 NeoForge/BBSFSR 仍适用的上游行为。
3. 保留或合并本地 FSR 专用源码：Mojmap 类型、BBSFSR 注册/API、IRL Core 客户端源码、运行时验证、Mixin 配置和资源路径。
4. 更新 `src/client/resources/assets/irlite/patches/iterationrp.irlights`：
   - 同步上游最新 patch body；
   - 保留 SSBO host ownership 和 voxel slider 双锚点；
   - 给 `IRLITE_VL_INTENSITY` 补上 Iris 可解析的允许值列表；
   - 将 `IRLITE_VL_INTENSITY` 加入 `screen.IRLIGHTS` 与 `sliders=`。
5. 增加轻量静态回归检查，至少验证每个 iterationRP 补丁中该选项同时满足 define、screen 和 sliders 三个条件，避免再次只写 label。

## 实际同步结果

- `bliss.irlights`、`bsl.irlights`、`complementaryreimagined.irlights`、`photon.irlights`、`rethinkingvoxels.irlights`、`solas.irlights` 与上游当前提交内容完全一致。
- `iterationrp.irlights` 仅保留本地 FSR 的 SSBO ownership 与双 voxel slider 锚点差异，并补上 `IRLITE_VL_INTENSITY` 的 Iris 允许值、屏幕项和滑杆项。
- 本地 `src/client/java/org/qualet/irl` IRL Core、BBSFSR/Mojmap Java 端口、NeoForge 元数据和现有 Mixin 配置均已存在，不重复导入 Fabric 上游文件。
- 明确排除 Fabric/Loom 构建配置、Fabric entrypoint、`fabric.mod.json`、Yarn 代码和已由 BBSFSR 正式 API 替代的注册 Mixin。

## 验证命令

在 `bbs-irlights-addon/` 下按顺序执行：

```text
gradlew.bat verifyExternalMods
gradlew.bat compileClientJava
gradlew.bat build
```

并执行以下只读检查：

```text
rg -n "net\.fabricmc|fabric\.mod\.json|IRLITE_VL_INTENSITY|screen\.IRLIGHTS=|sliders=" src
jar tf build/libs/*.jar
```

验收要点：

- 构建实际使用 `verifyExternalMods`，BBSFSR jar 不早于 `new/src`；
- JAR 有 NeoForge 元数据、Mixin 配置、IRLite 资源，没有 BBS core 类和 Fabric 元数据；
- iterationRP 的 `IRLITE_VL_INTENSITY` 出现在屏幕注册和滑杆注册中；
- 不产生 `new/` 源码改动。

## 风险文件与回退点

- `build.gradle` / `gradle.properties`：只能保持当前 NeoGradle/BBSFSR 端口合同，不直接复制上游 Fabric 配置。
- `src/client/resources/assets/irlite/patches/iterationrp.irlights`：最小文本改动，保留两处本地兼容差异。
- `src/client/java/qualet/irlite/client/light/LightCollector.java`、Mixin 和 IRL Core 文件：如有上游同名改动必须手工合并并重新编译。
- 任何新增同步文件先验证包名、Minecraft mapping 和目标 BBS API，再写入工作区。

## 完成门槛

- [x] 任务从 planning 正式启动后完成上述实施顺序。
- [x] 构建和静态检查通过；完整客户端人工视觉验证仍需在可用图形环境中执行。
- [x] 运行 `trellis-check`，确认规范、跨层数据流和测试覆盖。
- [x] 评估 `trellis-update-spec`：该规则是 iterationRP 补丁的局部合同，已由插件内 `verifyPatchResources` 自动执行，不扩散到 BBSFSR 通用规范。

## 最终验证记录

- 2026-08-20 再次核验远端 `port/1.21.1`，提交仍为 `c76a73f0e209da12505d3e9bff56d8e5155f47e4`。
- 六个非 iterationRP 补丁与上游 SHA-256 内容一致；iterationRP 的语义 diff 只包含允许值、设置屏幕、滑杆、双 voxel 锚点和 host SSBO ownership 差异。
- `verifyPatchResources`、`verifyExternalMods`、`compileClientJava`、`build` 全部通过；Gradle 测试源集为 `NO-SOURCE`，补丁回归由 `verifyPatchResources` 覆盖。
- 本地 IRL 解析器读取 iterationRP 成功：`parsedOps=11`、`target=IterationRP`、`marker=IRLITE`、`contract=1`。
- 发布 JAR 包含 NeoForge 元数据、Mixin、IRL Core 和 iterationRP 补丁；`mchorse/bbs_mod/**=0`、`fabric.mod.json=0`。
- 未启动完整 Minecraft 客户端进行 iterationRP 设置界面和实际光束亮度的人工视觉检查。
