# 圆形声音 Form 执行清单

## 执行记录

### 2026-07-27 阶段 2 用户冒烟回归与修复

用户实测发现:`playing` 关键帧未能可靠触发播放、`looping` 无效果、回放编辑器轨道仍显示内部英文 ID。

根因与最终修复:

- Film 的 Form 属性在 `startRenderFrame()` 才应用,而旧声音 tick 更早执行并读取上一帧值。现改为把 `FormProperties`、映射后的 replay tick 和 transport 状态传入渲染上下文,在关键帧应用完成后同步声音。
- renderer 本地 `elapsed` 不能表达暂停、scrub、倒放和 Film 循环回绕。现按 `playing=true` 的当前连续区间计算激活 tick;新触发、暂停、跳转、倒放、回绕时 seek,连续正播交给 OpenAL 自然推进。
- 空间 `VoiceRequest` 现实际携带并应用 pitch、native looping、最大传播距离与条件 seek;循环时直达声和反射声偏移均安全回绕。
- `UIKeyframeSheet` 为声音 Form 复用现有中英文文案;圆锥内部 `radius/inner_radius` 正确显示为「外锥角/内锥角」。
- 真实 Actor/玩家回放通过 owner-scoped 弱引用桥接获得同一时间轴上下文;控制器重建、关闭、隐藏、Film 结束时递归释放声音 renderer owner,并防止 Actor 最后一次 tick 复活音源。

自动验证(真实退出码均为 0):

- `compileClientJava --rerun-tasks`: `BUILD SUCCESSFUL`
- `testSoundAcoustics`: all tests passed
- `testAudioLifecycle`: all tests passed; captured 25 expected failure diagnostics
- `testAudioVerification`: all hermetic contracts passed(`ffmpeg/ffprobe` 缺失,严格媒体检查按既有规则跳过)
- `testPlatformResourceConsistency`: all tests passed
- `check --max-workers=1`: 74 个任务通过,`BUILD SUCCESSFUL`。首次运行仅因外部 Minecraft 进程占用 `new/logs/latest.log`/`debug.log` 被严格日志门拦截;将客户端注册测试工作目录临时隔离后完整门通过,未修改项目构建配置。

仍需用户游戏内复测:球形/圆锥的 false→true→false、正播、暂停、拖动、倒放、Film 循环、非 1 pitch、looping,以及中文轨道名。

### 首轮实现后自查发现的两个运行时 bug(本地 elapsed 修法已被上述时间轴同步替代)

| # | 症状 | 根因 | 修法 |
|---|---|---|---|
| 1 | **声音卡在开头反复重播** | 每帧传固定的 `seconds = startOffset`,而 `SoundManager.applyVoice` 在「请求位置与实际播放位置漂移超阈值」时会 seek。播放位置推进而请求值不动 → 每帧被拽回起点 | `SoundSphereFormRenderer` 加 `elapsed` tick 计数,`seconds = startOffset + elapsed/20`;`playing` 关闭时归零(故关键帧重新打开是**重播**而非续播) |
| 2 | **`looping` 开关完全无效** | `reconcile` 会丢弃 `seconds >= 音频时长` 的 voice,循环播到头就静音 | `SoundFormPlayback` 按 `SoundBuffer.getDuration()` 对 `seconds` 取模回绕 |

### 与规划的偏差

| # | 规划 | 实际 | 原因 |
|---|---|---|---|
| 1 | 试听按钮「调用与运行时同一个调度器」 | **改为平播**(无距离/方向衰减) | 编辑器预览没有世界坐标也没有监听者,没有可衰减的对象。它回答「选对片段了吗」,不回答「场景里听起来如何」。已写入代码注释,空间行为需放进世界验证 |
| 2 | 面板控件限幅写死 min/max | 改用 `UITrackpad.limit(ValueFloat)` 直接读字段范围 | 面板不可能与 Form 声明的范围漂移 |
| 3 | — | 追加 **30 条中英文案**(`assets/bbs/assets/strings/{en_us,zh_cn}.json`)+ 30 个 `UIKeys` 常量 | 规划未提 i18n,但主仓内新增 UI 必须走 L10n 而非硬编码英文;项目有 en/zh key parity 要求,已校验通过(各 1940 键) |

### 未完成项(移交子任务 3)

**反射目前只走直达声**:`SoundFormPlayback` 已留 `NO_SURFACES` 入口,反射换算与预算截断逻辑完整且有测试覆盖,但**世界侧的反射面收集未实现**。它需要世界方块查询,且与可视化共用几何,故与子任务 3 一并做。


前置阅读:本任务 `prd.md` → `design.md` → 子任务 1 `design.md`(基类接口)→ `research/irlights-addon-reference.md` §1/§3(真实 Form 与面板代码)。

**红线**:验证一律用真实 exit code(`echo REAL_EXIT=$?`),禁止管道结尾判断成败。

**前置条件**:子任务 1 `07-26-fsr-sound-acoustic-core` 已完成并归档。若 `AbstractSoundForm` / 调度器尚不存在,停止并回到子任务 1。

## 阶段 1:Form 与注册

- [x] 新建 `new/src/main/java/mchorse/bbs_mod/forms/forms/sound/SoundSphereForm.java`(design.md §2)。
- [x] `BBSMod.java:566` 后追加 `.register(Link.bbs("sound_sphere"), SoundSphereForm.class, null)`。
- [x] 验证:`cd new && ./gradlew compileJava > /tmp/sph1.log 2>&1; echo REAL_EXIT=$?`

## 阶段 2:渲染器与编辑器注册(先让它能被选中)

- [x] 新建 `SoundSphereFormRenderer`(UI 预览占位即可,世界可视化归子任务 3)。
- [x] `FormUtilsClient.java:89` 后追加渲染器注册。
- [x] 新建 `UISoundSphereForm extends UIForm<SoundSphereForm>`,注册默认面板。
- [x] `UIFormEditor.java:144` 后追加编辑器注册。
- [x] 验证:`cd new && ./gradlew compileClientJava > /tmp/sph2.log 2>&1; echo REAL_EXIT=$?`
- [ ] 冒烟(**待用户**):游戏内能在伪装选择器里找到并创建 `sound_sphere`。

## 阶段 3:编辑面板

- [ ] ~~新建 `SoundTrackpads` 工厂~~ **未做**:改用 `UITrackpad.limit(ValueFloat)` 直接读字段范围,无需再包一层工厂。
- [x] 新建 `UISoundSphereFormPanel`,按 design.md §3 四组布局。
- [x] 音频选择接 `UISoundOverlayPanel`,用法照 `UIAudioClip` 既有模式。
- [x] 实现 `startEdit(form)` 同步(照 `UIMobFormPanel:272-279`)。
- [x] 试听按钮:**改为平播**(见执行记录偏差 1)——编辑器无世界坐标与监听者,无可衰减对象。
- [x] 验证:`cd new && ./gradlew compileClientJava > /tmp/sph3.log 2>&1; echo REAL_EXIT=$?`

## 阶段 4:播放贯通

- [x] 接通 Form 的每帧调度(基类调度器 + 球形 `shapeGain` 恒 1)。
- [x] 确认 `radius` 作为 `maxDistance` 的唯一真相源(design.md §2),面板不重复暴露。
- [x] 验证:`cd new && ./gradlew check > /tmp/sph4.log 2>&1; echo REAL_EXIT=$?` + 日志确认 BUILD SUCCESSFUL、0 FAILED。

## 阶段 5:冒烟清单(交用户验证)

- [ ] 创建 → 选音频 → 调半径/音量 → 试听有声。
- [ ] 保存退出重进,参数不丢(序列化往返)。
- [ ] 时间轴 K 帧 `playing`:**正播**按时起停 / **倒播**行为正确 / **scrub 拖动**不残留音源 / **循环**不重复叠加。
- [ ] 关闭反射:听感与普通 3D 音源一致(走远变小、绕背后方位正确)。
- [ ] 开启反射:能听出回声,增大反射次数回声更密。
- [ ] 旧存档读取无异常。

## 回滚点

| 阶段 | 回滚方式 |
|---|---|
| 1 | 注释 `BBSMod.java` 那一行 → Form 不再注册 |
| 2 | 注释另两处注册 → 不出现在编辑器 |
| 3、4 | 新增文件删除即可;三处注册行是唯一侵入点 |

## 2026-07-27 用户回归追加执行清单

- [x] 添加五组 `SoundKeyframeValue` factory、稳定 channel id、序列化/插值/复制测试。
- [x] 在 `FormProperties` 中应用并重置复合声音 channel，旧独立 channel 先应用、新复合 channel 后应用；空复合轨道不覆盖旧轨道，删除最后一帧立即回退。
- [x] 回放编辑器为声音 Form 注入五条中文复合 sheet，并注册专用关键帧编辑面板。
- [x] 声音关键帧音频控件接 `UISoundOverlayPanel`；隐藏声音原始字段的自动独立轨道。
- [x] `SoundManager` 增加只替换 unique 预览源的重启入口，Form 试听按钮改用该入口。
- [x] 增加方块反射、实体反射、穿透方块、穿透实体字段和面板控件。
- [x] 扩展 `SoundSurfaceProbe`：精确方块近侧面、实体 AABB 最近面、直达声遮挡、排除 owner、范围与数量预算。
- [x] 扩展纯声学过滤和 `SoundFormPlayback`，覆盖方块/实体反射与穿透矩阵；循环反射到达前保持静音，到达后再回绕。
- [x] 补中英文案和 `UIKeys`，验证 en/zh key parity。
- [ ] 运行 `compileClientJava`、`testSoundAcoustics`、`testAudioLifecycle`、`testAudioVerification`、`testPlatformResourceConsistency` 和完整 `check`。
