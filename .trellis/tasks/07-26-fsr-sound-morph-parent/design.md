# 技术设计:FSR 声音伪装目标

> 本文所有代码坐标均**经过人工核验**(haiku 调研 agent 的行号有偏差,已逐条复核)。原始调研材料见 `research/`。

## 1. 结论先行:四个决定架构的事实

| # | 事实 | 核验坐标 | 对设计的影响 |
|---|---|---|---|
| F1 | 伪装目标的正确抽象是 **Form**(不是老的 `morphing/` Morph),注册在 `FormArchitect` | `BBSMod.java:553-566` | 新增 2 个 `Form` 子类,共 7 处接入点 |
| F2 | `Form extends ValueGroup`,字段用 `ValueXxx` 声明并 `add()`,而 `ValueBoolean/ValueLink/ValueFloat/ValueInt` **全部继承 `BaseKeyframeFactoryValue`** | `Form.java:32`、`numeric/ValueBoolean.java:6`、`base/BaseValueNumber.java:5`、`core/ValueLink.java:7` | **声明字段 = 免费获得关键帧轨道 + 免费序列化**。R2.2 几乎零成本 |
| F3 | BBS 有**完全独立于 Minecraft SoundEngine** 的 OpenAL 栈;`reconcile()` 可用一次调用同步 N 个共享 buffer、各自 offset/gain 的声源 | `SoundManager.java:871`(VoiceRequest)、`OpenALSoundBackend.java` | image-source 反射法**可行**,且反射源复用同一 buffer,内存零增长 |
| F4 | 全项目 `alDistanceModel` / `AL_CONE_*` / `AL_DIRECTION` **零匹配**;`maxDistance` 硬编码 `60F` | `SoundPlayer.java:41`;全项目搜索无命中 | 距离衰减与锥体方向性**都必须自己算**(见 §3) |

## 2. 两个关键架构决策

### D1 播放调度:期望状态驱动,而非事件触发

`reconcile(owner, desired, playing)` 的语义是「告诉我现在应该响哪些声音,我来处理启停」——它是**声明式**的,不是命令式的。

这一点直接消解了 PRD R2.1 最难的部分。事件触发式实现(检测 `false→true` 边沿then播放)在倒放、scrub、暂停、循环下要维护状态机,是 bug 温床;而声明式实现里**根本没有状态机**:

```
每帧:
  playing = form.playing.interpolate(currentTick)   // 关键帧求值,阶跃语义
  if (!playing) → desired 为空 → reconcile 自动停掉所有声源
  else → 算出直达声 + N 个反射声的 (offset, gain, position) → reconcile 自动创建/复用
```

倒放、跳转、暂停在这个模型下**不需要任何特殊处理**:每帧重新求值即可。`AudioClientClip.manageSounds()` 已经用同一模式在 film 音频轨道上验证过。

> 调研 agent 建议「复用 Clips 区间机制」实现开关播放,**未采纳**:`Clip` 是 film 的相机/动作轨道,而用户要的是挂在伪装目标上的关键帧。但它的 `envelope`(淡入淡出)字段值得借鉴,列入后续增强。

### D2 声学计算全部在我们侧,OpenAL 只当发声器

不碰 `alDistanceModel`——它是**全局状态**,一改会影响游戏内所有音效,直接违反 C1。锥体同理:OpenAL 原生锥体沿方向**无限延伸**,无法表达 PRD R1.3 的「锥高 = 最远距离」。

因此:每帧自己算出最终增益,写进 `VoiceRequest.gain`,OpenAL 侧只负责按位置发声。代价是失去 OpenAL 的自动距离衰减(需把 rolloff 置零或让 maxDistance 足够大),收益是三种衰减模型、锥体截断、反射衰减全部可控且零全局副作用。

## 3. 声学模型

### 3.1 距离衰减

设 `d` = 声源到监听者距离,`d_ref` 参考距离,`d_max` 最大距离,`f` 衰减系数(rolloff)。

| 模型 | 公式 | 说明 |
|---|---|---|
| 反比 inverse | `g = d_ref / (d_ref + f·(d - d_ref))` | OpenAL 默认语义,最接近真实点声源 |
| 线性 linear | `g = 1 - f·(d - d_ref)/(d_max - d_ref)` | 可控性最好,可视化最直观 |
| 指数 exponential | `g = (d / d_ref)^(-f)` | 衰减最快 |

三者均先把 `d` clamp 到 `[d_ref, d_max]`,超出 `d_max` 后 `g = 0`(R4.2)。

可选空气吸收(R4.3):`g *= exp(-k·d)`,`k` 为吸收系数。参数化档位下只做整体增益衰减,不做高频滤波。

### 3.2 锥体方向性

`θ` = 监听者方向与锥轴夹角,`θ_in` 内锥半角,`θ_out` 外锥半角,`g_out` 外锥增益:

```
θ ≤ θ_in           → g_dir = 1
θ_in < θ < θ_out   → g_dir = lerp(1, g_out, (θ - θ_in)/(θ_out - θ_in))
θ ≥ θ_out          → g_dir = g_out
```

沿用 OpenAL 锥体的语义(用户熟悉),但**距离截断由我们接管**:锥高 `h` 即 `d_max`,`d > h` 时静音。这就是「顶点是播放点、锥高是最远距离」的实现(R1.3)。

最终增益 `g = g_dist · g_dir · volume`。

### 3.3 反射(image-source)

每个反射声源 = 一条更长的传播路径:

```
额外路程 Δd = 路径总长 - 直达距离
延迟 Δt = Δd / c            (c = 声速,默认 343 m/s,1 方块 = 1 m)
增益 g_r = attenuate(路径总长) · α^n     (α = 每次反射保留系数,n = 反射次数)
```

映射到 `VoiceRequest`:延迟表现为**播放偏移提前量**(`seconds - Δt`),增益直接写 `gain`,位置放在镜像声源点。

反射源数量硬上限(C4/R5.4):超限时按 `g_r` 排序保留最强的若干个。MVP 阶段用**几何镜像法**(对周围方块面做镜像),而非逐条射线追踪,以保证热路径零分配(C3)。

## 4. 兼容性策略:`VoiceRequest` 如何加位置

现状 `record VoiceRequest(Link link, float seconds, float gain)`(`SoundManager.java:871`),`reconcile()` 在 `SoundManager.java:304` 与 `:413` 强制 `setRelative(true)`,即 film 音频走的是 **2D 配乐**路径。而 `SoundPlayer` 构造时本来就是 3D 的(`SoundPlayer.java:41-42`:`maxDistance=60F` + `setRelative(false)`)。

调研 agent 建议「扩展 VoiceRequest 并同步更新所有调用点」——**不采纳**,那会改动既有 film 音频行为(违反 C1)。改用 record 兼容构造器:

```java
public record VoiceRequest(Link link, float seconds, float gain,
                           boolean spatial, float x, float y, float z)
{
    /** 既有 2D 调用点保持原样,行为逐位不变 */
    public VoiceRequest(Link link, float seconds, float gain)
    {
        this(link, seconds, gain, false, 0F, 0F, 0F);
    }
}
```

`reconcile()` 里把无条件的 `setRelative(true)` 改为 `setRelative(!request.spatial())`,并在 `spatial` 时应用位置。**现有全部调用点一行不改,行为逐位等价**;`maxDistance` 从硬编码改为可选参数,缺省仍为 `60F`。

## 5. Form 字段设计

两个 Form 共享声学参数,仅发射形状不同。共享部分抽到 `AbstractSoundForm`:

```java
/* 音源 */
ValueLink    audio          // 音频资源,可 K 帧
ValueBoolean playing        // 播放开关 —— 关键帧主控字段(R2.1)
ValueFloat   volume, pitch
ValueBoolean looping
ValueFloat   startOffset    // 起始偏移秒数(R2.3)

/* 衰减 */
ValueString  falloffModel   // inverse | linear | exponential
ValueFloat   refDistance, maxDistance, rolloff
ValueFloat   airAbsorption

/* 反射 */
ValueBoolean reflectionsEnabled
ValueInt     reflectionCount
ValueFloat   reflectionDecay      // 每次反射保留系数 α
ValueInt     maxReflectionVoices  // 音源预算上限

/* 可视化 */
ValueBoolean showGuide
ValueColor   guideColor
```

球形追加 `ValueFloat radius`;锥形追加 `ValueFloat range`(锥高 = 最远距离)、`ValueFloat innerAngle`、`ValueFloat outerAngle`、`ValueFloat outerGain`。

**朝向不设独立字段**:复用 `Form` 基类已有的 `ValueTransform transform` 的旋转分量。这是参考实现 `SpotlightForm` 的做法(它同样没有 direction 字段),好处是免费获得既有 gizmo 拖拽,且与所有其他 Form 的朝向语义一致。

字段命名与取值范围对齐参考实现 `SpotlightForm`(见 `research/irlights-addon-reference.md`),便于用过该 addon 的用户迁移:

```java
ValueFloat range      = new ValueFloat("range", 12F, 0.1F, 128F);       // 锥高
ValueFloat outerAngle = new ValueFloat("radius", 35F, 1F, 179F);        // 外锥角(度)
ValueFloat innerAngle = new ValueFloat("inner_radius", 25F, 1F, 179F);  // 内锥角(度)
```

> 该参考实现的源码注释原文:*"All four are BBS Value\* so they keyframe in the film editor"* —— 独立印证了 F2。

因 F2,以上字段**全部自动可 K 帧、自动序列化、自动向后兼容**(新字段有默认值,旧存档读取走 `ValueGroup` 缺省路径)。

## 6. 可视化

世界渲染入口已确认:`BBSClientNeoEvents.java:151`(`RenderLevelStageEvent.Stage.AFTER_ENTITIES`)→ `BBSModClient.onRenderAfterEntities(BbsWorldRenderContext)`(`:160`),上下文提供 `camera()` / `matrixStack()` / `consumers()` / `tickDelta()`。选中态从 `UIFormEditor.editor` 读取。

**球/锥线框本仓库没有现成实现**:`Draw.java` 只有 `renderBox`,`Batcher2D` 的圆弧是 2D UI 专用,粒子系统无形状预览。但参考实现 `LightGuideRenderer` 提供了可直接照搬的几何算法(见 `research/irlights-addon-reference.md`):

| 图形 | 画法 | 用于 |
|---|---|---|
| 球体线框 | 三个正交平面的圆 + 三条轴线 | 球形发射范围(比经纬线球更轻、视觉更清晰) |
| 锥体线框 | 中轴线 + 4 条顶点到底沿的母线 + 底面圆环 | 锥形发射范围 |
| 内锥 | 在同一底面 Z 上再画一个半透明小环 | 内锥角提示 |

两个可直接借鉴的做法:

1. **单一真相源**:参考实现用 `spotRingZ(range)` 统一供给可视化、拾取手柄、拖拽换算三处,"改一处符号全部跟随"。我们的锥体同样让可视化与声学换算共用同一份几何函数,避免"看到的"与"听到的"不一致(这正是 PRD 跨子任务验收要验的点)。
2. **渲染态**:`disableCull` + `depthMask(false)` + `disableDepthTest`,使辅助图形穿墙可见 —— 编辑器辅助图形的正确做法。

**注意 API 差异**:参考实现是 Fabric / 1.20.1,我们是 NeoForge / 1.21.1,几何算法可照搬但渲染 API 须逐一映射(`MatrixStack`→`PoseStack`、`Tessellator.getInstance().getBuffer()`→`Tesselator.getInstance().begin(mode, format)`、`VertexFormats`→`DefaultVertexFormat`、`BufferRenderer.drawWithGlobalProgram`→`BufferUploader.drawWithShader`、`getPositionColorProgram`→`getPositionColorShader`)。映射后的 1.21 写法以本仓库 `Draw.java` 为准。

圆的顶点用预计算 sin/cos 表(参照 `Batcher2D` 的 `ROUNDED_RECT_ARC_SIN/COS` 做法)以满足 C3。

强度可视化(R6.2)按 §3.1 的衰减公式分层取等值面:在 `d_ref`、`-6dB`、`-12dB`、`d_max` 处画不同颜色的环/截面,用户能一眼看出「衰减到哪儿基本听不见」。

## 7. 风险与取舍

| 风险 | 应对 |
|---|---|
| 音源耗尽:`alGenSources` 失败时现有代码 fail-hard 只记日志(`SoundManager.reconcile` catch 块) | 每 Form 反射源数设硬上限;MVP 先监控日志,LRU 回收列为后续增强 |
| 自算衰减后失去 OpenAL 自动衰减,若某处漏算会导致音量异常 | 反射关闭时行为必须与普通 3D 音源等价——列为跨子任务验收项 |
| 几何镜像法在复杂地形下不准 | MVP 接受近似;PRD 未要求物理精确,可视化会如实反映我们算出的结果 |
| **视频导出听不到声音 Form(C7,已确认)** | 见下方 §9,需单独立任务解决 |

## 8. 子任务边界

| 子任务 | 负责 | 不负责 |
|---|---|---|
| `fsr-sound-acoustic-core` | `AbstractSoundForm`、衰减/锥体/反射换算、`VoiceRequest` 兼容扩展、播放调度器 | 具体两个 Form 类、UI、可视化 |
| `fsr-sound-morph-sphere` | `SoundSphereForm` + 注册 + 编辑面板 | 锥体、可视化渲染 |
| `fsr-sound-morph-cone` | `SoundConeForm` + 注册 + 编辑面板 + **两者的世界可视化** | — |

各子任务的详细实现计划见各自 `design.md` / `implement.md`。

## 9. C7 结论:视频导出听不到声音 Form(已确认,需单独立任务)

子任务 1 执行期间对导出路径做了调查,结论明确且需要用户知晓。

**事实**:导出走的是 `VideoExportSession` → `MinecraftSoundCapture`(挂在 **Minecraft 原生 SoundEngine** 上捕获游戏音效)+ `MinecraftSoundMixer`(离线混音)+ film 音频轨道。全路径**没有** `reconcile` 调用。

**后果**:声音 Form 用的是 BBS 自有 OpenAL 通道(`SoundManager`),**完全不经过 Minecraft SoundEngine**,因此 `MinecraftSoundCapture` 捕获不到它。**声音 Form 在实时预览里能听见,导出的视频里则是静音。**

**用户决定(2026-07-26)**:导出必须支持,**并且要正确处理单声道与立体声两种导出声道设置**。BBS 导出本就有「Minecraft 场景声音」开关与声道选择,声音 Form 理应同等对待。

**声道处理要求**(用户明确提出,不得遗漏):

`ChannelLayout`(`new/src/main/java/mchorse/bbs_mod/audio/ChannelLayout.java`)定义 `MONO(1)` / `STEREO(2)` / `SURROUND_5_1(6, 不支持)`,导出声道数由 `VideoExportAudioProfile.channels(layout)` 决定。声音 Form 是**带方向的 3D 声源**,因此两种布局下的离线渲染语义不同:

| 布局 | 渲染语义 |
|---|---|
| `STEREO` | 需按相机(监听者)朝向做**声像定位**:声源在左侧则左声道更强。反射声源各自独立定位。用等功率声像,与既有 render bus 的 equal-power 约定一致 |
| `MONO` | 不做声像,所有声源(直达 + 反射)按各自增益求和进单声道。距离/锥体/反射衰减仍全部生效 |

既有契约 `.trellis/spec/new/backend/audio-format-render-contract.md` 已规定声道矩阵(mono→stereo duplication vs. equal-power centering),离线渲染须接入该 render bus,不得另起一套。

**为什么单独立项**:导出是离线渲染,需要把每帧的虚拟声源状态(位置/增益/偏移,含反射)录成一条时间线,再送进离线混音器重采样叠加并做声道映射 —— 规模与实时播放相当,塞进任何现有子任务都会失控。已列为子任务 4。

> 缺口本身是既有架构约束,不是本次改动引入的缺陷;但补上它是本需求的一部分。
