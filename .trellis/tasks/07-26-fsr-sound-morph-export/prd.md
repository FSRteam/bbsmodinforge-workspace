# 声音 Form 视频导出与声道处理

父任务:`07-26-fsr-sound-morph-parent` · 依赖:子任务 1、2、3

## Goal

让声音伪装目标的声音**出现在导出的视频里**,并正确处理导出的**单声道 / 立体声**两种声道设置。

## 背景:为什么需要这个任务

实现子任务 1 期间确认的架构缺口(父任务 `design.md` §9):

- 导出走 `VideoExportSession` → `MinecraftSoundCapture`(挂在 **Minecraft 原生 SoundEngine** 上捕获游戏音效)+ `MinecraftSoundMixer` 离线混音 + film 音频轨道,全路径**没有** `reconcile` 调用。
- 声音 Form 走 **BBS 自有 OpenAL 通道**(`SoundManager`),不经 Minecraft SoundEngine,因此捕获不到。
- 结果:**实时预览听得见,导出的视频里静音**。

BBS 导出本就有「Minecraft 场景声音」开关与声道选择,声音 Form 应当同等对待。

## Requirements

### R1 离线渲染通路

- R1.1 导出时把每帧的虚拟声源状态(位置 / 增益 / 播放偏移,**含反射声源**)记录为一条时间线。
- R1.2 时间线送入离线混音,按各声源的偏移与增益重采样叠加,产出与实时预览**听感一致**的音频。
- R1.3 复用既有离线混音基础设施(`MinecraftSoundMixer` / `AudioRenderer` / `Wave.add(...)` 重采样),不另起一套混音器。
- R1.4 与既有的「Minecraft 场景声音」「film 音频轨道」在同一条导出音频总线上混合,三者音量关系合理。

### R2 声道处理(用户明确要求,不得遗漏)

`ChannelLayout`(`new/src/main/java/mchorse/bbs_mod/audio/ChannelLayout.java`)定义 `MONO(1)` / `STEREO(2)` / `SURROUND_5_1(6, 不支持)`;导出声道数由 `VideoExportAudioProfile.channels(layout)` 决定。声音 Form 是**带方向的 3D 声源**,两种布局语义不同:

- R2.1 **立体声**:按相机(监听者)朝向做**声像定位** —— 声源在左侧则左声道更强。反射声源各自独立定位。采用**等功率声像**,与既有 render bus 的 equal-power 约定一致。
- R2.2 **单声道**:不做声像,直达声与全部反射声按各自增益求和进单声道;距离衰减 / 锥体方向性 / 反射衰减**仍全部生效**。
- R2.3 声道矩阵接入既有契约 `.trellis/spec/new/backend/audio-format-render-contract.md`(mono→stereo duplication vs. equal-power centering),不得另立一套规则。
- R2.4 `SURROUND_5_1` 当前标记为不支持,遇到时按既有降级路径处理,不崩。

### R3 一致性

- R3.1 同一场景、同一参数下,导出音频与实时预览的**听感一致**(音量包络、回声疏密、方向感)。
- R3.2 圆锥的方向性在导出中生效:相机处于内锥 / 过渡区 / 外锥时,导出音频呈现三段差异。
- R3.3 超出最大距离的声源在导出中静音,与实时行为一致。

## 全局约束

- C1 不破坏既有导出行为:关闭声音 Form 或场景中没有声音 Form 时,导出音频与本任务实施前**逐位一致**。
- C2 单声道 / 立体声两条路径都要有测试覆盖。
- C3 构建验证以真实 exit code + 日志双确认。

## Acceptance Criteria

- [ ] 场景中放置圆形与圆锥声音 Form,导出视频,音频中**能听到**它们。
- [ ] 立体声导出:声源在相机左侧时左声道更强,右侧反之;转动相机时声像跟随。
- [ ] 单声道导出:能听到声音且距离衰减正确,无声像差异。
- [ ] 圆锥方向性在导出中生效(内锥 / 过渡 / 外锥三段有别)。
- [ ] 开启反射后,导出音频能听出回声,且疏密与实时预览一致。
- [ ] 场景中无声音 Form 时,导出音频与实施前逐位一致(C1)。
- [ ] `cd new && ./gradlew check` 真实 exit code 为 0,日志确认 BUILD SUCCESSFUL 且 0 FAILED。
- [ ] 用户导出成片确认。

## Notes

- 本任务依赖前三个子任务的交付物(声学换算、两个 Form、几何真相源),不应重复实现声学逻辑 —— 离线渲染必须调用与实时**同一套**换算函数,否则两条路径会漂移。
- 规模与实时播放通路相当,故单独立项而非并入子任务 3。
