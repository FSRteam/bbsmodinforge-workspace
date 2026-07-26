# 技术设计:声学核心

前置阅读:父任务 `design.md`(§1 四个事实、§2 两个架构决策、§3 声学公式、§4 兼容性策略)。本文只写本任务的落地细节,不重复父任务结论。

## 1. 新增与改动清单

### 改动(3 个文件,全部向后兼容)

| 文件 | 改动 | 兼容性保证 |
|---|---|---|
| `new/src/client/java/mchorse/bbs_mod/audio/SoundManager.java` | `VoiceRequest` 加 `spatial/x/y/z` + 保留 3 参构造器;`:304`、`:413` 的 `setRelative(true)` 改为 `setRelative(!spatial)`;`spatial` 时应用位置 | 3 参构造器 `spatial=false` → 既有调用点行为逐位不变 |
| `new/src/client/java/mchorse/bbs_mod/audio/SoundPlayer.java` | `:41` 的 `setSourceMaxDistance(source, 60F)` 改为可配置,缺省 `60F` | 不传参即原值 |
| `new/build.gradle` | 新增 `soundAcousticsTest` source set(§4 区)、`testSoundAcoustics` 任务、加入 `bbsVerificationTaskNames`(`:541-564`) | 纯增量 |

### 新增(main,纯逻辑)

```
new/src/main/java/mchorse/bbs_mod/forms/forms/sound/
├── SoundFalloff.java        枚举 + 三种衰减模型的纯函数
├── SoundAcoustics.java      距离衰减 / 空气吸收 / 锥体方向增益,全静态纯函数
├── SoundReflections.java    image-source 反射源生成与预算截断
├── SoundVoice.java          单个虚拟声源的可复用载体(位置 + 延迟 + 增益)
└── AbstractSoundForm.java   共享字段 + 播放调度骨架
```

放 `main` 而非 `client`:纯数学不依赖客户端,且可被裸 JVM 测试直接加载(R4.3)。`AbstractSoundForm` 同样在 `main`,与既有 Form 一致(`forms/forms/` 下)。

### 新增(client,调度)

```
new/src/client/java/mchorse/bbs_mod/forms/renderers/sound/
└── SoundFormPlayback.java   每帧构建 desired map 并调用 reconcile
```

## 2. 声学换算的纯函数边界(R4.3 红线)

这几个类**不得** import 任何 `net.minecraft.*`、`BBSMod`、`BBSSettings`,也不得读取任何静态注册表。输入全部由调用方传入:

```java
public static float distanceGain(SoundFalloff model, float distance,
                                 float refDistance, float maxDistance, float rolloff)

public static float airAbsorptionGain(float distance, float coefficient)

/** cosAngle = 锥轴单位向量 · 指向监听者的单位向量 */
public static float coneGain(float cosAngle, float innerAngleDeg,
                             float outerAngleDeg, float outerGain)
```

这样测试进程只加载这几个类,天然不触发 `BBSMod.<clinit>`。

> 这条红线来自既有事故记录:裸 JVM 测试一旦触碰 BBSMod 静态初始化就会炸。`ThemeManager` 用 `catch (Exception | LinkageError)` 兜底,但更好的做法是从一开始就让被测逻辑无静态依赖 —— 本任务采用后者。

## 3. 播放调度(R3.3)

```
SoundFormPlayback.update(form, formWorldPos, listenerPos, currentTick):
    if (!form.playing.get())          → desired.clear(); reconcile(owner, desired, playing)
    else:
        seconds = startOffset + (currentTick - clipStartTick) / 20
        直达声: gain = volume · distanceGain(...) · airAbsorption(...) · shapeGain(...)
                position = formWorldPos
        反射声: SoundReflections.collect(...) 产出 ≤ maxReflectionVoices 个 SoundVoice
                每个: seconds - Δt, gain = 路径衰减 · α^n
        reconcile(owner, desired, playing)
```

其中 `shapeGain` 是 `AbstractSoundForm` 的抽象方法:球形返回 `1`(各向同性),锥形返回 `coneGain(...)`。这是子任务 2、3 唯一需要实现的声学部分(R3.4)。

**无状态机**:每帧全量重算 `desired`,`reconcile` 负责 diff 与启停。倒放/scrub/暂停/循环因此不需要任何特殊分支(父任务 D1)。

**零分配(C3)**:`desired` map 与 `SoundVoice` 数组在 `SoundFormPlayback` 实例上复用,每帧只覆写字段,不 new。`SoundVoice` 设计为可变载体而非 record。

## 4. 反射的 MVP 范围

`SoundReflections` 用**几何镜像法**:对声源周围有限范围内的方块面做镜像,生成一阶到 N 阶镜像声源,按路径长度算延迟与衰减。不做逐条射线追踪(热路径开销不可控)。

发射形状约束(R5.3):球形接受各方向镜像;锥形只接受落在锥体覆盖方向内的镜像。

预算截断(R2.5/R5.4):候选镜像按增益降序,保留前 `maxReflectionVoices` 个。截断发生时不静默 —— 记一次可观测计数,便于后续调优。

## 5. 测试设计

新增 `new/src/soundAcousticsTest/java/mchorse/bbs_mod/audio/SoundAcousticsTest.java`,`main` 方法自跑(与既有 23 个测试同模式,如 `AudioFormatContractTest`)。

| 用例 | 断言要点 |
|---|---|
| 三种衰减模型 × 三段距离 | `d < refDistance` 增益为 1;区间内单调递减;`d > maxDistance` 为 0 |
| 衰减模型相对关系 | 同参数下指数衰减 ≤ 反比 ≤ 线性(在中段) |
| 锥体三段 | 内锥内 = 1;外锥外 = outerGain;过渡段单调且端点连续 |
| 锥体退化 | `innerAngle == outerAngle` 时不产生除零 |
| 反射换算 | 延迟 = 额外路程 / 声速;n 次反射增益 = 路径衰减 × α^n |
| 反射预算 | 候选数 > 上限时,保留的是增益最大的那批,且数量等于上限 |
| 无静态依赖 | 测试类不 import BBS/Minecraft 任何类(人工走查 + import 检查) |

`build.gradle` 接入(照 `:498-503` 的 `testAudioFormat` 样板):

```gradle
soundAcousticsTest {
    compileClasspath += sourceSets.main.output
    runtimeClasspath += output + compileClasspath + sourceSets.main.output
}

tasks.register('testSoundAcoustics', JavaExec) {
    dependsOn tasks.named('compileJava'), tasks.named('soundAcousticsTestClasses')
    classpath = sourceSets.soundAcousticsTest.runtimeClasspath
    mainClass = 'mchorse.bbs_mod.audio.SoundAcousticsTest'
}
```

并把 `'testSoundAcoustics'` 加入 `bbsVerificationTaskNames`(`build.gradle:541-564`)。

## 6. R3.2 的验证方式

父任务断定"字段声明即自动获得关键帧轨道"依据是 `ValueXxx` 均继承 `BaseKeyframeFactoryValue`(已核验)。但 `FormProperties.getOrCreate()` 还要求属性 `isVisible()`,因此:

- 需要 K 帧的字段**不要**调 `.invisible()`;
- 纯配置型字段(如 `maxReflectionVoices`)可以 `.invisible()` 以免污染时间轴。

验证手段:在测试中构造 `AbstractSoundForm` 的最小具体子类,逐字段断言 `instanceof BaseKeyframeFactoryValue` 且 `isVisible()` 符合预期。这不需要启动 BBS,满足 R4.3。

## 7. 回滚点

- `VoiceRequest` 扩展与 `SoundPlayer` 参数化互相独立,可分别回滚。
- 声学纯函数类是新增文件,无消费者时删除即回滚。
- `AbstractSoundForm` 未注册进 `FormArchitect`(注册在子任务 2、3),因此本任务全程**不影响运行时行为**,回滚风险最低。
