# S8 剪辑类型与快捷栏

## Goal

在独立 `bbs-snow-addon` 中把主源码树的 Hotbar、Cinematic 和 Color 影片剪辑迁移到 NeoForge 1.21.1，使三种剪辑能够注册、保存、重新加载、编辑全部轨道并在 Film 预览/播放/录制上下文中产生可见效果，同时复用 S3 已有的屏幕效果引擎。

## Confirmed Facts

- 父任务 R3.6 明确要求 Hotbar、Cinematic、Color 三种剪辑及其 UI 与轨道标题。
- 主源码树 `bbs_FSloveCML_1.21.1/src/` 拥有完整 Hotbar HUD、Cinematic、Color 数据和消费链；`addon-project/` 只保留较旧重复实现。
- `addon-project` 独有的 `VideoClip` 没有播放/渲染实现，且已被源历史删除，不属于当前产品行为。
- 当前 FSR 没有等价的三种剪辑；S3 已提供按 `ClipContext` 隔离的屏幕效果队列、GL 渲染器和世界渲染消费点。
- S3 的 `ColorEffect`/`ScreenEffectQueue` 当前位于 client 源集，而剪辑类型必须在 common 源集注册和反序列化；S8 必须先建立 common-safe 数据边界。
- 旧 Hotbar renderer 使用 1.20.1 GUI 图集，1.21.1 需按 Mojmap HUD sprite API 适配，不能机械替换映射名。

## Requirements

### R1 剪辑数据与注册

- 注册 `bbs:hotbar`、`bbs:cinematic`、`bbs:color` 三种剪辑工厂，不注册 Video。
- Hotbar 保留 25 个轨道、默认值、旧 layout 三轨到 `Vector4f` 的兼容迁移以及 hardcore 类型迁移。
- Cinematic 保留 8 个通道；Color 保留 overlay color、overlay alpha 和 13 个 color-grade 通道。
- 三种剪辑的 clone、保存、读取和旧数据迁移均不引用 client-only 类型。

### R2 屏幕效果边界

- 将纯数据 `ColorEffect` 和 `ScreenEffectQueue` 提升为 common-safe owner；GL shader、batcher 和渲染注入继续留在 client 源集。
- Color/Cinematic 只向 S3 队列发布效果，不复制 `ColorGradeRenderer`、`ScreenEffectRenderer` 或第二套 context 发现逻辑。
- 队列按具体 `ClipContext` 隔离、保留剪辑顺序、每帧只消费一次，结束/切换上下文后无残留。

### R3 Hotbar HUD

- Hotbar 剪辑把插值后的物品栏、选中槽、副手、生命/吸收、盔甲、饥饿、氧气、经验和布局状态发布到当前 `ClipContext`。
- client renderer 使用 Minecraft 1.21.1 HUD sprite/ItemStack 绘制 API，遵守 alpha、scale、位置和剪辑顺序。
- Film 预览、正常播放、录制和自定义尺寸预览沿用现有 FSR context owner，不读取玩家实时 HUD 状态替代剪辑数据。

### R4 UI 与本地化

- Hotbar、Cinematic、Color 都有可构造的 inspector/UI factory；所有轨道有稳定标题且可进入关键帧编辑。
- Hotbar UI 展示 25 个轨道；Cinematic 展示 8 个轨道；Color UI 提供 overlay color 和 14 个关键帧轨道。
- 新增英语/简中键保持对称，不恢复旧 namespace 或 `SnowUIKeys` 总入口。

### R5 兼容与范围

- 保持 addon 的 `defaultRequire: 1`、无 refmap、精确描述符和 `bbs_snow$` 前缀。
- 不修改 `new/`，不迁移 `addon-project/`，不实现 Video，不复制 S3 shader，也不带入 S9/Premiere 功能。

## Acceptance Criteria

- [x] AC1 `verifyBbsCoreJar` 和 `build` 退出码为 0；addon JAR 含三种剪辑、UI、本地化和必要资源，且不内嵌 `mchorse/bbs_mod/**`。
- [x] AC2 运行时注册表仅新增 Hotbar/Cinematic/Color，三种剪辑保存/读取/clone 往返保持全部属性和轨道，旧 Hotbar 数据迁移正确。
- [x] AC3 自动验证覆盖 25/8/14 条轨道的标题与 UI factory，三种 inspector 可构造且关键控件可见。
- [x] AC4 Hotbar 发布完整 HUD state；Color/Cinematic 发布正确缩放和 envelope 后的 screen effects；重叠剪辑按 `context.count`/队列顺序稳定。
- [x] AC5 common/server 类加载不触及 client 类；不同 `ClipContext` 不串数据，每个 context 的效果每帧只消费一次。
- [x] AC6 严格客户端实际应用新增/修改 Mixin，触发真实工厂、UI、发布和至少一次渲染消费路径；日志无 injection/helper/classloading 错误。
- [x] AC7 英语/简中键对称、旧 namespace/Fabric/Video/重复 S3 renderer 引用为 0，本任务对 `new/` 和旧源码树的修改为 0。
- [x] AC8 无法自动证明的 HUD 像素、复杂重叠、预览/录制尺寸和 addon-disabled 基线登记到 S10 人工验收。

## Out of Scope

- `addon-project/` 的任何源码合并或独立发布。
- `VideoClip` 及视频解码/纹理播放能力。
- 重写 S3 屏幕效果 shader、渲染器或 Film context 发现逻辑。
- S9 流体、Premiere 和新的非源功能。
