# 圆锥声音 Form 与可视化 执行清单

> 2026-07-27 回归扩展：跟随兄弟任务 `07-26-fsr-sound-morph-sphere/implement.md` 的追加清单完成共享复合轨道、音频选择、重复试听、方块/实体反射及穿透；圆锥侧额外验证四个形状字段在一条 `发射形状` 轨道内往返与插值。

## 2026-07-27 阶段 2 用户冒烟回归与修复

用户实测确认声音 Form 的 `playing` 关键帧触发、循环和轨道中文名存在问题。本轮完成了球形/圆锥共用运行时修复:

- 在 Film 关键帧已应用后的渲染上下文中按映射 replay tick 调度,覆盖正播、暂停、scrub、倒放和循环回绕。
- `VoiceRequest` 应用 pitch、native looping、形状最大距离和条件 seek;直达/反射循环偏移统一回绕。
- 圆锥关键帧轨道接入中文文案,内部 `radius/inner_radius` 显示为「外锥角/内锥角」。
- 真实 Actor/玩家回放获得同一时间轴上下文;Film/editor 重建与退出统一释放 renderer-owned 音源,阻止残留与复活。

自动验证均以真实退出码 0 通过:`compileClientJava --rerun-tasks`、`testSoundAcoustics`、`testAudioLifecycle`、`testAudioVerification`、`testPlatformResourceConsistency`,以及完整 `check --max-workers=1`(74 个任务,`BUILD SUCCESSFUL`)。`ffmpeg/ffprobe` 当前不可用,严格媒体检查按既有契约跳过。首次 `check` 仅因外部 Minecraft 进程占用项目日志被严格日志门拦截;临时隔离客户端注册测试工作目录后通过,未修改项目构建配置。

待用户游戏内复测:圆锥 false→true→false、正播/暂停/拖动/倒放/Film 循环、looping、非 1 pitch、方向性听感及「外锥角/内锥角」轨道名。

前置阅读:本任务 `prd.md` → `design.md` → 父任务 `design.md` §3.2/§6 → `research/irlights-addon-reference.md` §2/§5(几何代码 + API 映射表)→ `research/irlights-drag-and-hookup.md`。

**红线**:验证一律真实 exit code(`echo REAL_EXIT=$?`),禁止管道结尾判断成败。

**前置条件**:子任务 1、2 已完成并归档。

## 阶段 1:几何真相源(纯函数,先立地基)

- [x] 新建 `new/src/main/java/mchorse/bbs_mod/forms/forms/sound/SoundConeGeometry.java`:`capDistance` / `coneRadius` / `cosHalfAngle`(design.md §2),纯函数、无渲染依赖。
- [x] 在子任务 1 的测试中补用例:锥体几何与 `coneGain` 的边界一致性(内锥 clamp、退化角度不除零)。
- [x] 验证:`cd new && ./gradlew testSoundAcoustics > /tmp/cone1.log 2>&1; echo REAL_EXIT=$?`

## 阶段 2:圆锥 Form 与注册

- [x] 新建 `SoundConeForm`(design.md §1),`shapeGain` 走 `SoundAcoustics.coneGain`,锥轴取自 `Form.transform` 旋转。
- [x] 确认 `range` 为锥形下 `maxDistance` 的唯一真相源(R1.3 硬语义)。
- [x] 三处注册:`BBSMod.java`(`sound_cone`)、`FormUtilsClient.java`、`UIFormEditor.java`。
- [x] 新建 `UISoundConeForm` + `UISoundConeFormPanel`(布局照子任务 2,追加锥体四参数)。
- [x] 验证:`cd new && ./gradlew compileClientJava > /tmp/cone2.log 2>&1; echo REAL_EXIT=$?`
- [ ] 冒烟:能创建 `sound_cone`,面板可调参,能发声,方向性听感正确(内锥/过渡/外锥三段有别)。

## 阶段 2.5:反射面收集(由子任务 2 移交)

子任务 2 完成时,反射换算与预算截断已实现且有测试覆盖,但**世界侧的反射面收集未做**,播放路径目前只调度直达声(`SoundFormPlayback.NO_SURFACES`)。它需要世界方块查询,且与可视化共用几何,故并入本任务。

- [x] 新建反射面探测:从声源出发,在最大距离内沿轴向采样,把命中的实心方块面转成 `SoundReflections.Surface`(平面上一点 + 单位法线)。
- [x] 复用池化的 `Surface[]`,每帧不分配(C3)。
- [x] 形状约束(PRD R5.3):球形接受各方向;锥形只接受落在锥体覆盖方向内的面。
- [x] 在两个 Form 的 renderer 里把收集到的面传进 `SoundFormPlayback.update`。
- [ ] 冒烟:开启反射后能听出回声,增大反射次数回声更密;关闭反射时与普通 3D 音源等价。

## 阶段 3:绘制原语(逐个编译验证,防 API 映射踩坑)

按 `research/irlights-addon-reference.md` §5 映射表改写为 1.21.1,**每个原语单独编译验证后再做下一个**:

- [x] `SoundGuideRenderer.renderRing`
- [x] `renderSphere`(三正交圆 + 三轴线)
- [x] `renderCone`(中轴 + 4 母线 + 外环 + 内环)
- [x] 圆顶点改用预计算 sin/cos 表(C3 零分配)
- [x] 线宽自适应 `clamp(r * 0.002, 0.002, 0.009)`
- [x] 渲染态 `disableCull` + `depthMask(false)` + `disableDepthTest`
- [x] 每步验证:`cd new && ./gradlew compileClientJava > /tmp/cone3.log 2>&1; echo REAL_EXIT=$?`

## 阶段 4:选中判定与挂钩

- [x] 实现选中标记(弱身份引用 + 300ms TTL + 回放编辑器判定,design.md §4)。
- [x] 在两个 Form 的 renderer 中接入绘制,条件 `globalShowGuides || isSelected(form)`。
- [x] 加全局开关(R5.2)。
- [x] **确认不需要任何 Mixin**(我们在主仓内,直接改对应类)。
- [ ] 冒烟:选中时显示、切到相机编辑器立即隐藏、最终渲染输出中不出现。

## 阶段 5:强度分层

- [x] 三种衰减模型各自实现 `-6dB` / `-12dB` 距离的解析反解。
- [x] 无解或非单调时跳过该档,不画错。
- [x] `renderIntensityBands`:参考距离 / -6dB / -12dB / 最大距离 四档,颜色或透明度区分。
- [ ] 验证一致性:分层显示的"听不见距离"与实际听感吻合(冒烟项)。

## 阶段 6:拖拽编辑(独立阶段,可单独回滚)

- [x] 绘制时捕获 local→view 矩阵。
- [x] stencil 拾取:锥高 / 外角 / 内角三个手柄(球形一个半径手柄)。
- [x] 鼠标射线反投影到局部坐标系,反算对应字段;**手柄位置与反算全部读阶段 1 的几何真相源**。
- [x] 拖拽期间面板数值同步刷新(R4.2);释放单次提交,取消/对象切换按身份回滚。
- [x] 验证:`cd new && ./gradlew compileClientJava > /tmp/cone6.log 2>&1; echo REAL_EXIT=$?`

## 阶段 7:收尾与父任务验收

- [ ] 全量:`cd new && ./gradlew check > /tmp/cone-check.log 2>&1; echo REAL_EXIT=$?` + 日志 BUILD SUCCESSFUL、0 FAILED。
- [ ] **跑父任务跨子任务验收**(父任务 `prd.md` Acceptance Criteria 全部条目)。
- [ ] 重点验:圆锥顶点在播放点、锥高 = 最远距离(可视化与听感双向验证)。
- [ ] 重点验:关闭反射时两个 Form 均等价于普通 3D 音源。
- [ ] 用户游戏内冒烟确认。

## 回滚点

| 阶段 | 回滚方式 |
|---|---|
| 1、2 | Form 三处注册注释掉即完全移除 |
| 3、4、5 | 全局开关关掉即隐身;绘制代码为新增文件 |
| 6 | 拖拽独立 revert,不影响可视化与面板数值编辑(「可编辑」需求在阶段 2 已由面板满足) |
