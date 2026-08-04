# 声学核心模型与关键帧音频轨道

父任务:`07-26-fsr-sound-morph-parent`(需求全貌、调研结论与架构决策见父任务 `prd.md` / `design.md`)

## Goal

搭好两个声音伪装目标共用的地基:**声学换算**(距离衰减 / 锥体方向性 / 反射)、**空间化播放通道**(让 `reconcile` 支持 3D 定位)、**共享 Form 抽象基类**(含关键帧驱动的播放调度)。

本任务**不交付**任何可在编辑器里选中的 Form 类型,也不交付 UI 与可视化 —— 那是子任务 2、3 的事。本任务的交付物是"能被单元测试验证的纯逻辑 + 一条打通的播放通路"。

## Requirements

### R1 空间化播放通道(解除阻塞)

- R1.1 `SoundManager.VoiceRequest` 支持携带 3D 位置与空间化开关。
- R1.2 **既有调用点一行不改**:保留 3 参构造器,委托到全参版本且 `spatial=false`,使 film 音频轨道行为逐位不变。
- R1.3 `reconcile()` 中当前无条件的 `setRelative(true)`(`SoundManager.java:304`、`:413`)改为由请求的 `spatial` 决定;`spatial=true` 时应用位置。
- R1.4 `SoundPlayer` 硬编码的 `maxDistance=60F`(`SoundPlayer.java:41`)改为可配置,缺省仍为 `60F`。

### R2 声学换算(纯函数,可裸 JVM 测试)

- R2.1 三种距离衰减模型:反比 / 线性 / 指数,参数为参考距离、最大距离、衰减系数;超过最大距离静音。
- R2.2 可选空气吸收项(随距离的额外增益衰减)。
- R2.3 锥体方向增益:内锥全增益,内外锥之间平滑过渡到外锥增益,外锥之外为外锥增益;**距离截断由我们接管**(锥高即最远距离)。
- R2.4 反射换算:给定反射次数与每次反射保留系数,产出每个虚拟声源的延迟(额外路程 / 声速)与增益(路径衰减 × 多次反射吸收)。
- R2.5 反射声源数量受硬上限约束,超限时按增益保留最强的若干个。

### R3 共享 Form 抽象基类

- R3.1 `AbstractSoundForm extends Form`,声明父任务 `design.md` §5 的全部共享字段(音源 / 衰减 / 反射 / 可视化),形状参数由子类补充。
- R3.2 因所有 `ValueXxx` 均继承 `BaseKeyframeFactoryValue`,字段声明即自动获得关键帧轨道与序列化 —— 本任务需**验证**这一点确实成立,而非假定。
- R3.3 播放调度采用**期望状态驱动**:每帧按当前 tick 求值 `playing` 等字段,算出应发声的直达声 + 反射声集合,交给 `reconcile`。不维护播放状态机。
- R3.4 形状相关的换算(球 / 锥)以抽象方法暴露,由子类实现。

### R4 测试

- R4.1 新增独立测试 source set + `JavaExec` 任务,并加入 `bbsVerificationTaskNames` 使其纳入 `check`。
- R4.2 覆盖:三种衰减模型的边界(小于参考距离 / 区间内 / 超过最大距离)、锥体三段(内锥 / 过渡 / 外锥)、反射延迟与增益换算、反射预算截断。
- R4.3 **红线**:测试运行在裸 JVM,不得触发 `BBSMod.<clinit>`。声学换算须设计为不依赖任何 Minecraft / BBS 静态状态的纯函数。

## Acceptance Criteria

- [ ] `VoiceRequest` 扩展后,既有 film 音频轨道行为逐位不变(现有 `testAudioLifecycle` / `testAudioFormat` / `testAudioVerification` 全绿)。
- [ ] 新增测试任务纳入 `check` 并全绿,真实 exit code 为 0。
- [ ] 声学换算全部为纯函数,裸 JVM 可测,无 `BBSMod.<clinit>` 触发。
- [ ] `AbstractSoundForm` 的字段能被 `FormProperties` 识别并生成关键帧通道(以测试或最小验证证明 R3.2)。
- [ ] 热路径(每帧调度)无临时对象分配:声源列表与请求对象复用。
- [ ] `cd new && ./gradlew check` 真实 exit code 为 0,日志确认 BUILD SUCCESSFUL 且 0 FAILED。

## Notes

- 本任务完成后,子任务 2、3 只需实现形状参数、注册与 UI,不应再改动声学逻辑。
- 若发现视频导出路径未复用同一调度器(父任务 C7),在此记录差异并上报父任务,不在本任务内解决。
