# BBS++ → FSR NeoForge addon 技术设计

## 1. 边界与交付形态

本任务新增独立目录 `bbs-bbsplusplus-addon/`（最终目录名可在实现阶段按仓库命名约定确认），包含自己的 Gradle 工程、源码、资源、许可证和构建验证任务。它依赖 `new/build/libs/BBS_FSR_beta_v0.0.11.jar` 的已发布本地快照，不修改 `new/` 核心源码。

上游源码先保存为只读基线目录/快照，记录提交 SHA；迁移代码放在 addon 工程中，避免把上游 Fabric 构建文件直接当作最终工程。

## 2. 分层架构

```text
上游 BBS++ 2.6.3 (Fabric/Yarn, 1.20.1)
        │
        ├─ 临时 Loom 工程：Yarn → Mojang 映射辅助
        │
        ▼
NeoForge addon 工程 (Mojmap, 1.21.1, Java 21)
        │
        ├─ common：设置、状态、命令、数据、可选依赖探测
        ├─ client：编辑器/UI/关键帧/资源包/语言/渲染入口
        ├─ mixin：仅保留没有公开 FSR/BBS 扩展点的注入
        └─ compat：Iris、Sodium、IRLights、VFX、AAA、MediaPlayer、Together 等
        │
        ▼
当前 FSR/BBS addon API + NeoForge 事件 + 可选 Mixin
```

## 3. 加载和注册

- 主 `@Mod` 类只依赖 common 类型，并在 NeoForge mod 构造期调用 FSR/BBS 的外部 addon 注册入口。
- 客户端 bootstrap 单独放在 client 源集，负责客户端事件、UI、语言、source pack、渲染器和 key mapping。
- 优先使用 `BBSMod.registerAddon` / BBS Addon API 2.0 及 `BBSClientApi` 等公开接口；每个注册结果必须记录并按失败矩阵降级。
- 注册必须遵守 FSR 外部 addon contract：构造期注册 addon；source pack 和 L10n 在客户端启动时按“先 source pack、再 provider、最后 reload”顺序执行；避免重复注册。
- API 2.0 没有公开能力时，才保留直接注册或 Mixin，并在设计/验收记录中说明原因。

## 4. 映射和平台迁移策略

1. 在隔离的 Fabric Loom 工程中固定 Minecraft 1.20.1/Yarn `1.20.1+build.10`，运行 `migrateMappings` 生成辅助变更。
2. 将映射结果作为参考，不把它当作可直接编译的 NeoForge 代码。
3. 按 common、client、Mixin 三类逐个对照 FSR 1.21.1 源码/JAR 中同名实现，处理 1.21 API 改动（如 `Minecraft`、`PoseStack`、`ResourceLocation`、`GuiGraphics`、渲染 buffer 和事件签名）。
4. Mixin 逐个复核目标类、方法、描述符、静态/实例语义、`require` 值和可选依赖边界；真实客户端启动是最终证据。

## 5. 功能模块边界

| 模块 | 主要内容 | 首选接入方式 | 验收重点 |
|---|---|---|---|
| 基础与设置 | 设置项、状态、命令、语言键 | FSR/BBS API + NeoForge 事件 | 保存/加载、默认值、语言切换 |
| 关键帧与编辑器 | 轨道中文化、姿势树、骨骼刷、跳过骨骼、剪贴板、时间线 | FSR UI/关键帧扩展；必要时 UI Mixin | 打开、编辑、保存、重载 |
| 影片/伪装 UI | 新影片库、新伪装布局、双击剪辑、纹理管理器 | FSR UI API；必要时 client Mixin | 面板交互和状态恢复 |
| Form 与粒子 | 物品喷射、AAA 粒子、结构/视频伪装 | Form/renderer/editor 注册 API | 无依赖和完整依赖两种环境 |
| 光影与渲染 | 光影控制、曲线、PBR/UV、发光色块、渲染修复 | FSR 渲染 API、Iris/Sodium 可选桥接、必要 Mixin | vanilla 与 Iris/Sodium 启动/渲染 |
| 外部兼容 | IRLights、VFX/VFX Lights、Together/Xavin、MediaPlayer | capability 检测 + 独立 compat 配置 | 缺失时不崩溃，存在时启用 |
| 修复项 | 输入法、曲线、拖拽、播放头、静音、颜色条等 | 逐项选择 API 或 Mixin | 回归测试和人工路径 |

## 6. Mixin 和可选依赖隔离

- 必须把通用 Mixin、客户端 Mixin、FSR API accessor Mixin、Iris/Sodium Mixin、第三方兼容 Mixin 分开配置。
- 目标类不存在或可选模组缺失时，兼容注入必须使用软失败策略（独立配置、`@Pseudo`、`require = 0` 或 NeoForge 条件加载，具体以目标类实际情况决定）。
- 任何会改变全部模型/全部渲染路径的全局替换都要优先否决，改用状态限定或局部拦截，避免覆盖 FSR 自身渲染器。
- Mixin 包中不得混入非 Mixin 辅助类；辅助逻辑放在普通包中。

## 7. 数据、资源与许可证

- 语言文件、图标、shader、source pack 和配置模板全部进入 addon 自己的资源命名空间。
- 语言 provider 只为实际打包的语言返回 link；按 FSR source-pack/L10n contract 检查 key 完整性和格式参数数量。
- 发布 JAR 不得嵌入 `mchorse/bbs_mod/**` 核心类，不得含 `fabric.mod.json` 或 Fabric 运行时元数据。
- 根目录保留上游 LGPL-3.0-or-later 许可证、版权与来源说明；新增文件标明迁移来源和必要修改。

## 8. 失败降级和回滚

- 单个可选依赖不可用：记录诊断并禁用对应 capability，不影响核心 addon 启动。
- 单个 Form/轨道/渲染器注册失败：保留其它注册，记录明确诊断，不让 Dashboard 或客户端启动失败。
- Mixin 目标漂移：先停在对应模块，不通过降低全局检查或吞异常掩盖；回滚到上一个可启动模块快照。
- 每完成一组模块就保存编译、JAR、客户端启动和人工 smoke 证据，作为回滚点。
