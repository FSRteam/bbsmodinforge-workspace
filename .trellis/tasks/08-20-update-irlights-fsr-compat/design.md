# 技术设计：IRLights 上游同步与 FSR 兼容

## 1. 边界

目标工程是一个独立 NeoForge mod，物理目录为 `bbs-irlights-addon/`。上游分支只作为
源码、补丁和文档的内容来源，不直接作为构建系统来源。当前端口的运行合同是：

```text
NeoForge 21.1.222 + Java 21
        |
        +-- BBSFSR 0.0.11 local jar (compileOnly)
        +-- Iris 1.8.8 / Sodium 0.6.13
        +-- IRL Core sources in src/client/java/org/qualet/irl
```

## 2. 同步策略

1. 在临时目录浅克隆远端 `port/1.21.1`，记录提交号、时间和文件清单。
2. 以相对路径和内容 hash 做比对，再对同名文件做语义 diff；不使用目录覆盖。
3. 文件按三类处理：
   - **直接同步**：补丁资源、上游文档、与 Mojmap/BBSFSR 无关的纯 Java 逻辑，只有在当前路径和包结构一致时才覆盖。
   - **手工合并**：同时含 Fabric 到 NeoForge 改名、BBSFSR API 适配、partial tick/运行时验证或 IRL Core 集成的文件；以本地端口为主体吸收上游行为。
   - **明确排除**：Fabric/Loom 构建文件、Fabric entrypoint、`fabric.mod.json`、上游 BBSModMixin 以及上游已被正式 BBSFSR API 替代的注册 Mixin。
4. 资源路径保持当前 JAR 结构：`src/client/resources/assets/irlite/patches/*.irlights`。上游根目录 `patches/` 只作为输入，不直接改变运行时资源路径。

## 3. iterationRP 修复

当前补丁已经包含最新上游 GLSL 主体和 FSR 兼容的两处差异：

- 不在 shaderpack 内强行追加 `iris.features.required=... SSBO`，由当前宿主/补丁合同负责 host shader feature ownership；
- `PT_VOXEL_RESOLUTION` 与 `PT_VOXEL_RESOLUTION_X PT_VOXEL_RESOLUTION_Y` 两种锚点均可插入滑杆。

上游最新设置注入目前只把 `IRLITE_VOLUMETRIC` 放进 `screen.IRLIGHTS`，并把主强度、镜面强度和 Toon 数值放进
`sliders=`；`IRLITE_VL_INTENSITY` 虽有语言文本，但数值 `#define` 没有 Iris 要求的允许值列表，也没有注册到屏幕树/滑杆列表。

修复方式：给 `#define IRLITE_VL_INTENSITY` 补上与其他 IRLights 补丁一致的 `// [允许值...]` 注释，在同一条
`screen.IRLIGHTS = ...` 注册中加入它，并在两种 `sliders=` 锚点生成的追加项中加入它。这样 Iris 才能发现该数值选项并按滑杆渲染；
Java 端 `VlGlobalsBuffer` 的 UBO 值和 shader 的未绑定回退不需要改动。

## 4. 兼容性与回滚

- 不改变 BBSFSR 依赖版本和 `verifyExternalMods` 的 API 门槛。
- 任何上游 Java 变更都先通过本地编译，再决定是否手工合并；不能用降低 Mixin `require` 的方式掩盖不兼容。
- 若完整客户端因外部窗口/显卡环境无法启动，仍完成静态、构建和补丁解析检查，并把人工验证缺口写入结果。
- 失败时可按文件回退；不触碰 `new/`、上游临时克隆或其他工作区插件。

## 5. 预期数据流

```text
iterationrp.irlights
  -> IrlPatchParser / IrlPatchApplier
  -> shaders.properties screen + sliders injection
  -> Iris option discovery
  -> IRLITE_VL_INTENSITY value
  -> VlGlobalsBuffer UBO (irlite_vlA.x)
  -> volumetric shader runtime multiplier
```
