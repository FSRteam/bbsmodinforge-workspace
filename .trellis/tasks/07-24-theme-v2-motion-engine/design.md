# motion-engine 技术设计

依赖阅读:父任务 `design.md`(`../07-24-theme-v2/design.md`)层 1 与研究报告 3.5 节。本文给出可直接编码的完整规格。

## 1. 弹簧闭式解(数学规格,照写即可)

参数:`response` T(秒,自然周期)、`damping` ζ(阻尼比)。派生:`ω = 2π / T`。
状态:当前值 `x`、当前速度 `v`(单位 1/秒)、目标 `g`。令偏移 `d(t) = x(t) - g`,初值 `d0 = x - g`、`v0 = v`(均在 `to()` 时刻采样)。

**欠阻尼 ζ < 1**(常规路径):

```
ωd = ω·sqrt(1 - ζ²)
d(t) = e^(-ζωt) · (A·cos(ωd·t) + B·sin(ωd·t))
A = d0
B = (v0 + ζω·d0) / ωd
v(t) = e^(-ζωt) · ((B·ωd - A·ζω)·cos(ωd·t) - (A·ωd + B·ζω)·sin(ωd·t))
```

**临界 |ζ-1| < 1e-4**(并入同支):

```
d(t) = e^(-ωt) · (A + B·t),A = d0,B = v0 + ω·d0
v(t) = e^(-ωt) · (B - ω·(A + B·t))
```

**过阻尼 ζ > 1**:

```
ωr = ω·sqrt(ζ² - 1);r1 = -ζω + ωr;r2 = -ζω - ωr
C1 = (v0 - r2·d0) / (r1 - r2);C2 = d0 - C1
d(t) = C1·e^(r1·t) + C2·e^(r2·t)
v(t) = C1·r1·e^(r1·t) + C2·r2·e^(r2·t)
```

**settle 判定**:`|d| < 0.001 && |v| < 0.01` → 值钳到 `g`、速度清零、settled=true(值域约定 0..1,阈值即千分之一)。

**打断即速度继承**:动画中再次 `to(newTarget, spec)` → 先以当前时刻求 `(x, v)`,再以此为新 `d0/v0` 重启计时。这一条是本任务的灵魂,必须有测试覆盖。

**速度缩放**:`effectiveResponse = response / (themeSpeed × userSpeed)`(与 v1 duration 除法同构,UIMotions 现有组合点)。

## 2. UITween 改造

- 对外接口 `snap(float) / to(float, UIThemeMotion) / update(long nowMs) / update() / isSettled()` 签名与语义不变。
- 内部增加:`velocity` 字段、模式判别(spec.type)。ease 分支 = v1 现有代码原样;spring 分支 = 上节闭式解,`t = (nowMs - startMs) / 1000.0`。
- `snap()`:值跳变、`velocity = 0`、settled(语义不变)。
- 同目标 `to()` 幂等(v1 语义):spring 下同目标且未 settled 时**不重启**(继续当前轨迹)。
- disabled spec → snap 短路(v1 语义不变)。

## 3. UIThemeMotion 与解析

- 字段扩为:`{boolean enabled, int duration, IInterp easing, MotionType type, float response, float damping}`,`MotionType { EASE, SPRING }`。
- theme.json 键(全部可选):`"type": "spring"` 时读 `"response"`(缺省 0.3,钳 [0.05, 3])、`"damping"`(缺省 0.9,钳 [0.1, 2]);其余情况 type=EASE,v1 键照旧。坏值逐 key 回退缺省,不抛出(ThemeParser 惯例)。
- `UIMotions.duration(spec)`:spring 条目返回 `response×1000` 的缩放近似(Notification 的 ms 信封继续可用);文档标注 notification 接入点推荐 ease。

## 4. Batcher2D 全局 alpha 栈

- 字段 `private float globalAlpha = 1F` + `pushAlpha(float mul)` / `popAlpha()`(小数组栈或记录前值的简单实现,嵌套乘法)。
- 汇聚方式:每个 **public 绘制方法**入口对颜色参数做 `color = Colors.mulA(color, globalAlpha)`(globalAlpha==1 时直接跳过,逐位等价保证)。方法清单:`box×2 / gradientHBox / gradientVBox / dropShadow / outline 系 / icon 系 / fullTexturedBox / texturedBox 系 / text 系 / textCard / clip 外的所有着色绘制`。
- 文字保护:乘后 alpha < 0.03 → 跳过整个文字绘制(MC 字体低 alpha discard 跳变规避)。
- 本任务只建机制并保证恒等;真正的消费方是 motion-exit(整面板淡出),此处不接入任何调用点。

## 5. 测试规格(themeCoreTest source set,JavaExec main 惯例)

UIMotionsTest 扩充(裸 JVM,禁触 BBSMod.<clinit> 红线延续):

- 三分支性质断言:欠阻尼(ζ=0.5)出现过冲后收敛;临界(ζ=1)不过冲单调;过阻尼(ζ=1.5)不过冲收敛更慢。
- 打断速度连续:t0 打断,打断前 `[t0-ε, t0]` 与打断后 `[t0, t0+ε]` 的差分速度之差 < 容差(建议 ε=1ms 采样、容差按 v 幅值 5%)。
- settle 收敛:足够长 t 后 isSettled 且值==target 精确。
- ease 回归:v1 全部既有断言不动。
- ThemeCoreTest 增加 spring 键解析用例(合法/坏值回退/缺省)。

## 6. 风险与回滚

- ease 路径零改动(分支隔离),v1 主题(无 type 键)行为逐位不变 —— 回滚只需主题不写 spring。
- alpha 栈乘子恒 1 时静态可证等价;文字 skip 阈值只在 push 过非 1 乘子时可达。
