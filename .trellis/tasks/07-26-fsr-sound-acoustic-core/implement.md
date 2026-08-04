# 声学核心 执行清单

## 执行记录:与规划的偏差(实现中发现,均已落地)

| # | 规划 | 实际 | 原因 |
|---|---|---|---|
| 1 | 改 `SoundManager.java:304` 与 `:413` 两处 `setRelative(true)` | **只改 `:413`** | `:304` 属于 `playUnique()`,是独立的 2D 播放 API,与 `reconcile` 无关,不该动 |
| 2 | 基类含 `ValueFloat maxDistance` 字段 | **改为抽象方法 `getMaxDistance()`** | 球的 `radius` / 锥的 `range` 本身就是截断距离;两个独立字段会让"画出的边界"与"听到的边界"漂移,改成抽象方法后从类型层面写不错 |
| 3 | 热路径零分配 | **`VoiceRequest` record 每帧仍有分配**(每个可闻声源一个,受反射预算上界约束) | record 不可变是 `reconcile` 既有契约的一部分,改成可变会破坏 film 音频依赖的 API(C1)。map / voice 池 / identity key 均已复用。此偏差已写入代码注释,不假装做到了零分配 |

| 4 | R3.2 验证靠「构造最小具体子类 + 断言 `isVisible()`」 | **改用反射检查字段声明类型** | `Form` 构造器会读 `BBSSettings`(`Form.java:94`),裸 JVM 实例化有触发 BBS 静态初始化的风险(C5 红线)。反射只取 `getDeclaredField().getType()`,不初始化任何类,验证的又恰是我们关心的性质。代价:`isVisible()` 的正确性改由代码走查 + 子任务 2/3 的游戏内验证兜底 |

另发现并修复两处规划未预见的问题:
- `SoundFalloff.values()` 每次调用克隆数组,而 `getFalloff()` 每帧必经 —— 已改为静态缓存。
- `SoundReflections.collect` 中 `directDistance` 是循环不变量,却在双层循环内被反复求值 —— 已提到循环外。

**构建验证踩坑记录**:首轮把 `./gradlew check > log 2>&1; echo REAL_EXIT=$?` 丢进后台运行,**后台通知报 exit code 0,而日志里是 BUILD FAILED** —— 因为 `echo` 才是最后一条命令,退出码被它顶掉。这与项目既有的「管道吞退出码」红线是同一类陷阱,只是分号版。后台运行时必须让 `gradlew` 作为末条命令。另:`--offline` 不适用于全量 check(vanilla 资源下载任务会失败)。

**C7 调查结论(阻塞项,已上报父任务 `design.md` §9)**:视频导出走 `MinecraftSoundCapture` + 离线混音,全路径无 `reconcile`;声音 Form 走 BBS 自有 OpenAL 通道,不经 Minecraft SoundEngine,因此**导出视频里听不到声音 Form**。属既有架构约束,非本次引入,建议单独立项。


前置阅读顺序:本任务 `prd.md` → 本任务 `design.md` → 父任务 `design.md`(§1 事实表、§3 声学公式、§4 兼容性策略)→ 调研原文 `../07-26-fsr-sound-morph-parent/research/audio-pipeline.md` §10。

**红线提醒**:所有验证以真实 exit code 判定,禁止 `| tail` / `| grep` 之类管道结尾判断成败(管道会吞掉退出码);裸 JVM 测试不得触发 `BBSMod.<clinit>`。

## 阶段 1:声学纯函数(无消费者,零风险)

- [x] 新建 `new/src/main/java/mchorse/bbs_mod/forms/forms/sound/SoundFalloff.java`:枚举 `INVERSE / LINEAR / EXPONENTIAL`,含从字符串解析(坏值回退 `INVERSE`)。
- [x] 新建 `SoundAcoustics.java`:`distanceGain` / `airAbsorptionGain` / `coneGain`,公式照父任务 `design.md` §3.1、§3.2。**不得 import 任何 Minecraft / BBS 类**。
- [x] 新建 `SoundVoice.java`:可变载体(`x/y/z/seconds/gain/reflectionOrder`),提供 `set(...)` 复用接口,**不是 record**(C3 零分配)。
- [x] 新建 `SoundReflections.java`:几何镜像法生成候选 + 按增益降序截断到上限;截断计数可读出。
- [x] 验证:`cd new && ./gradlew compileJava > /tmp/sac1.log 2>&1; echo REAL_EXIT=$?`(REAL_EXIT=0)

## 阶段 2:测试接入(先立测试,后接管道)

- [x] `new/build.gradle` 的 sourceSets 区(`:144-280`)加 `soundAcousticsTest`(照 design.md §5)。
- [x] 注册 `testSoundAcoustics` 任务(照 `build.gradle:498-503` 的 `testAudioFormat` 样板)。
- [x] `'testSoundAcoustics'` 加入 `bbsVerificationTaskNames`(`:541-564`)。
- [x] 新建 `new/src/soundAcousticsTest/java/mchorse/bbs_mod/audio/SoundAcousticsTest.java`,覆盖 design.md §5 表格全部用例。
- [x] 走查测试类 import,确认零 BBS / Minecraft 依赖(R4.3)。
- [x] 验证:`cd new && ./gradlew testSoundAcoustics > /tmp/sac2.log 2>&1; echo REAL_EXIT=$?` + 日志确认用例全过。

## 阶段 3:空间化播放通道(触碰既有代码,最需谨慎)

- [x] `SoundManager.java:871` 扩展 `VoiceRequest` 为 `(link, seconds, gain, spatial, x, y, z)`,**保留 3 参构造器**委托到全参且 `spatial=false`。
- [x] `SoundManager.java:304`、`:413`:`setRelative(true)` → `setRelative(!request.spatial())`;`spatial=true` 时调 `setPosition`。
- [x] `SoundPlayer.java:41`:`maxDistance` 参数化,缺省 `60F`。
- [x] 验证既有音频行为逐位不变:
      `cd new && ./gradlew testAudioLifecycle testAudioFormat testAudioVerification > /tmp/sac3.log 2>&1; echo REAL_EXIT=$?`
- [x] 走查所有 `new VoiceRequest(` 调用点,确认均未被迫修改(R1.2)。

## 阶段 4:Form 抽象基类与调度

- [x] 新建 `AbstractSoundForm.java`:父任务 `design.md` §5 的共享字段;需 K 帧的字段**不调** `.invisible()`,纯配置字段调 `.invisible()`(design.md §6)。
- [x] 抽象方法 `shapeGain(...)` 与形状可视化参数暴露给子类(R3.4)。
- [x] 新建 `SoundFormPlayback.java`:期望状态驱动的每帧调度(design.md §3),`desired` map 与 `SoundVoice` 数组实例级复用。
- [x] 测试补充:最小具体子类 + 逐字段断言 `BaseKeyframeFactoryValue` 与 `isVisible()`(R3.2 验证)。
- [x] 验证:`cd new && ./gradlew compileClientJava testSoundAcoustics > /tmp/sac4.log 2>&1; echo REAL_EXIT=$?`

## 阶段 5:收尾

- [x] 全量:`cd new && ./gradlew check > /tmp/sac-check.log 2>&1; echo REAL_EXIT=$?` + 日志确认 BUILD SUCCESSFUL、0 FAILED。
- [x] 热路径走查:确认每帧调度路径无 `new` / 无装箱 / 无临时集合(C3)。
- [x] 调查父任务 C7:视频导出路径是否复用同一调度器;结论写入本任务执行记录并上报父任务,不在此解决。
- [x] 确认 `AbstractSoundForm` 未注册进 `FormArchitect`(注册属于子任务 2、3),本任务对运行时行为零影响。

## 回滚点

| 阶段 | 回滚方式 |
|---|---|
| 1、2 | 纯新增,删文件即回滚 |
| 3 | `VoiceRequest` 与 `SoundPlayer` 两处独立,可分别 revert;3 参构造器保证回滚不影响调用点 |
| 4 | 新增文件,且未注册进任何工厂,删除即回滚 |
