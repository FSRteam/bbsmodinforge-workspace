# 技术设计:圆形声音 Form

前置阅读:父任务 `design.md`(架构决策与声学公式)、子任务 1 `design.md`(基类与调度接口)、`research/irlights-addon-reference.md` §1/§3(真实的 Form 与面板代码)。本文只写差异。

## 1. 改动清单

### 新增(3 个文件)

| 文件 | 内容 |
|---|---|
| `new/src/main/java/mchorse/bbs_mod/forms/forms/sound/SoundSphereForm.java` | `extends AbstractSoundForm`,加 `radius`,实现 `shapeGain` 返回 `1F` |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/sound/SoundSphereFormRenderer.java` | `extends FormRenderer<SoundSphereForm>`,UI 预览占位 |
| `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/forms/UISoundSphereForm.java` + `panels/UISoundSphereFormPanel.java` | 编辑器与面板 |

### 改动(3 处注册,各一行)

| 文件 | 位置 | 追加 |
|---|---|---|
| `BBSMod.java` | `:566` 后 | `.register(Link.bbs("sound_sphere"), SoundSphereForm.class, null)` |
| `FormUtilsClient.java` | `:89` 后 | `register(SoundSphereForm.class, SoundSphereFormRenderer::new);` |
| `UIFormEditor.java` | `:144` 后 | `register(SoundSphereForm.class, UISoundSphereForm::new);` |

## 2. Form 定义

```java
public class SoundSphereForm extends AbstractSoundForm
{
    public final ValueFloat radius = new ValueFloat("radius", 6F, 0.1F, 64F);

    public SoundSphereForm()
    {
        this.add(this.radius);
    }

    /** 球形各向同性,无方向衰减 */
    @Override
    public float shapeGain(float dx, float dy, float dz, float distance)
    {
        return 1F;
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return "Sound (sphere)";
    }
}
```

`radius` 的默认值与范围对齐参考实现 `PointLightForm.radius`(`6F, 0.1F, 64F`),减少用户心智负担。

**`radius` 与 `maxDistance` 的关系**:`radius` 是"发射半径"(声音铺到多远),语义上就是基类 `maxDistance` 的球形表达。为避免两个字段互相打架,`SoundSphereForm` 让 `radius` 作为**唯一真相源**,基类的 `maxDistance` 在球形下由 `radius` 供给,面板不单独暴露 `maxDistance`。(锥形下则由 `range` 供给,见子任务 3 —— 这保证了"可视化画出的边界"就是"声音消失的边界"。)

## 3. 面板布局

按 PRD R7.2 分四组,组间用分隔;控件类型照 `research/irlights-addon-reference.md` §3:

```
音源      audio(UIButton → UISoundOverlayPanel) / volume / pitch / looping / startOffset / [试听]
发射形状  radius
衰减      falloffModel(循环选择) / refDistance / rolloff / airAbsorption
反射      reflectionsEnabled / reflectionCount / reflectionDecay
```

`maxReflectionVoices` 等纯预算参数不进面板(基类已 `.invisible()`)。

建议同时新建 `SoundTrackpads.create(cb).limit(min, max)` 工厂(照参考实现的 `IrliteTrackpads`),声音参数多,统一配置能省掉大量重复。

**试听按钮**(R3.4):直接调用与运行时相同的调度器,传入编辑器预览坐标,确保"试听即所得"。不另写一条播放路径 —— 那会导致预览与实际不一致。

**`startEdit(form)`**:逐控件 `setValue(form.xxx.get())` 同步,照 `UIMobFormPanel:272-279` 既有模式。

## 4. 验收要点的实现对应

| 验收项 | 靠什么保证 |
|---|---|
| 序列化往返一致 | 字段全部 `add()` 进 `ValueGroup`,由基类自动处理 |
| 正播/倒播/scrub/循环正确 | 期望状态驱动,每帧全量重算(父任务 D1),无状态机可错 |
| 关闭反射 = 普通 3D 音源 | 反射关闭时 `desired` 只含直达声一项;需实际对比确认 |
| 旧存档兼容 | 新 Form 不影响旧存档;新字段有默认值 |

## 5. 回滚点

三处注册各一行,注释掉即完全回滚(Form 不再出现在选择器中);新增文件删除即可。基类与声学核心属子任务 1,本任务不动。

## 6. 2026-07-27 复合轨道与交互反射设计

### 6.1 复合轨道

声音 Form 的原始 `Value*` 字段继续作为存档与运行时真相源，但从自动轨道收集中隐藏。Film 使用五个属性外复合 channel，channel id 由 Form 路径和稳定后缀组成，支持嵌套 Form：

```text
<form-path>/$sound
<form-path>/$sound_shape
<form-path>/$sound_visualization
<form-path>/$sound_falloff
<form-path>/$sound_reflections
```

`SoundKeyframeValue` 保存一组完整快照，`SoundKeyframeFactory` 按组序列化并对数值/颜色插值，对 Link、布尔、枚举执行阶跃。`FormProperties` 先应用旧普通 channel，再应用复合声音 channel，因此旧影片兼容、同时存在时新轨道确定性优先。无 segment 时清理该组所有字段的 runtime value。

客户端为五个 factory 注册同一个专用 `UISoundKeyframeFactory`。编辑器从 sheet 的 `form` 和 factory group 构建相应控件；声音组音频按钮直接打开 `UISoundOverlayPanel`，不经过通用 `UILinkKeyframeFactory`。

### 6.2 试听所有权

`SoundManager.restartUnique(Link)` 只停止并删除相同 Link 的旧 unique 预览源，再创建一个从 0 秒开始的新预览源；不得调用 link-global `stop(Link)`，避免误停同资源的 Film/世界声音。

### 6.3 方块、实体与穿透

共享字段新增：

```text
block_reflections=true
entity_reflections=false
pass_through_blocks=false
pass_through_entities=true
```

默认值保留“房间方块可产生反射、普通实体不干扰声音”的基础体验。方块面以六轴首次实体方块命中的精确近侧面生成；实体在有界查询范围内取 AABB 面向声源的最近面，数量硬上限。直达声对 source→listener 做方块碰撞和实体 AABB 线段命中。

过滤矩阵是纯逻辑合同：`passThrough(category)` 优先；否则直达路径会被该类别遮挡，且只有 `reflections && reflect(category)` 时该类别产生反射。探测数组和 voice 数组池化，世界查询范围与候选数量都有上限。
