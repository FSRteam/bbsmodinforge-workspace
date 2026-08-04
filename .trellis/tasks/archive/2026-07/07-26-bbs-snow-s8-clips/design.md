# S8 技术设计：剪辑类型与快捷栏

## 1. 交付边界

S8 以 `bbs_FSloveCML_1.21.1/src/` 为唯一功能基线，迁移五个 common 数据 owner、三个 UI owner、Hotbar renderer 和必要的 Clips/BBSRendering 注入。`addon-project/` 只作为已完成的差异研究证据。

```text
bbs-snow-addon/src/
|-- main/java/mchorse/bbs_snow/
|   |-- clips/HotbarClip.java
|   |-- clips/HotbarState.java
|   |-- clips/screen/ColorClip.java
|   |-- clips/screen/CinematicClip.java
|   `-- screen/{ColorEffect,ScreenEffectQueue}.java
`-- client/java/mchorse/bbs_snow/
    |-- client/screen/{ColorGradeRenderer,ScreenEffectRenderer}.java  # S3 existing consumers
    |-- ui/film/UIHotbarRenderer.java
    |-- ui/film/clips/{UIHotbarClip,UICinematicClip,UIColorClip,ClipTrackTitles}.java
    `-- mixin/client/ Clips accessors/invokers and BBSRendering consumer extension
```

实际文件数以 target API 适配为准；不得为了保持源目录计数而复制无调用者或旧实现。

## 2. Common/client 分层

`ClipFactory` 注册、Film 数据反序列化和 `applyClip` 都属于 common 层。纯 DTO 与基于 `ClipContext.clipData` 的队列不得依赖 `Minecraft`、UI、batcher 或 GL 类。

```text
common clip interpolation
  -> HotbarState / ColorEffect DTO
  -> namespaced entry in ClipContext.clipData
  -> client BBSRendering hook resolves the same ClipContext
  -> HUD renderer / S3 ScreenEffectRenderer consumes once
```

S3 的 `ColorEffect` 与 `ScreenEffectQueue` 从 client 源集提升到 `mchorse.bbs_snow.screen` common 包，两个现有 client renderer 只更新 import。禁止保留平行 client/common DTO。

## 3. 剪辑注册与序列化

- common addon bootstrap 在 BBS 外部注册窗口内注册三种 clip factory。
- client bootstrap 注册三种 UI factory 和标题 owner。
- Hotbar 的 25 个 channels 继续由单一 `channels[]` 定义构造、UI 和验证顺序，避免三份清单漂移。
- Cinematic 的 8 个、Color 的 14 个关键帧 channels 采用相同规则；Color 的 `overlayColor` 是普通值，不计入 14 个 channels。
- `create()` 返回同类型空实例；BBS 基类拥有 clone/toData/fromData。验证必须往返非默认值，而不是只比较类型。

## 4. 渲染语义

### 4.1 Screen effects

Color/Cinematic 每次 `applyClip` 创建独立快照后入队，禁止复用同一可变 `ColorEffect` 实例跨 tick 或跨 context。envelope 在发布前应用；renderer 按入队顺序合成，并在成功获取快照时清空该 context 队列。

### 4.2 Hotbar

Hotbar state 包含复制后的 `ItemStack`，避免 Film keyframe 值被渲染路径修改。渲染顺序使用 `context.count`，布局的 x/y/scale/alpha 在 renderer 入口统一裁剪。

旧的 `textures/gui/icons.png`/`widgets.png` UV 路径不能进入目标。1.21.1 实现应对照当前 Minecraft HUD 的 sprite identifiers、heart type、armor/food/air/experience 与 hotbar selection API，并在固定 GUI scale 下做像素 smoke。

## 5. UI 设计

- `UIHotbarClip` 与 `UICinematicClip` 以 `channels[]` 生成 keyframe editors，并复用 BBS 当前 `UIClip`/`UIKeyframes` 布局。
- 新增 `UIColorClip`，提供 `overlayColor` 色板/输入和 14 个 channel editors；无 UI factory 的 Color 注册不算完成。
- `ClipTrackTitles` 是所有标题键的单一 owner；缺失键必须在严格 verifier 中失败。
- Clips accessor/invoker 只在当前 BBS 没有公开入口时使用，并保持 `defaultRequire: 1`。

## 6. 兼容与回滚

- 源 Yarn `net.minecraft.item.ItemStack` 改为 Mojmap `net.minecraft.world.item.ItemStack`；所有渲染 API 以 1.21.1 target 为准。
- 若 Hotbar renderer 的某个 1.20.1 HUD 分支无法等价适配，保留数据/UI 并把该具体视觉项列入 S10，不得退回旧图集或吞掉整个剪辑。
- 回滚 S8 时移除三种 factory/UI 注册、S8 common 数据类、Hotbar renderer，并把 S3 DTO/queue 物理路径与 imports 恢复为 S3 形态；S3 shader 与消费 hook 本体仍可独立工作。

## 7. 验证设计

- common-side verifier：factory 注册、clone/serialization、legacy Hotbar migration、channel counts/defaults/clamps、context isolation/order。
- client-side verifier：UI factory 和全部标题、关键控件可见、实际 BBSRendering context 消费、队列清空与无残留。
- artifact/static：JAR 边界、三种 clip/三个 UI 资源、无 Video/旧 namespace/Fabric/重复 renderer、common bytecode 不引用 client 包。
- manual/S10：HUD 像素、两段重叠效果、不同分辨率/GUI scale、Film preview/播放/录制和 addon-disabled baseline。
