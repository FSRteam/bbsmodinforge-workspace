# 圆锥声音 Form 与可视化 执行清单

前置阅读:本任务 `prd.md` → `design.md` → 父任务 `design.md` §3.2/§6 → `research/irlights-addon-reference.md` §2/§5(几何代码 + API 映射表)→ `research/irlights-drag-and-hookup.md`。

**红线**:验证一律真实 exit code(`echo REAL_EXIT=$?`),禁止管道结尾判断成败。

**前置条件**:子任务 1、2 已完成并归档。

## 阶段 1:几何真相源(纯函数,先立地基)

- [ ] 新建 `new/src/main/java/mchorse/bbs_mod/forms/forms/sound/SoundConeGeometry.java`:`capDistance` / `coneRadius` / `cosHalfAngle`(design.md §2),纯函数、无渲染依赖。
- [ ] 在子任务 1 的测试中补用例:锥体几何与 `coneGain` 的边界一致性(内锥 clamp、退化角度不除零)。
- [ ] 验证:`cd new && ./gradlew testSoundAcoustics > /tmp/cone1.log 2>&1; echo REAL_EXIT=$?`

## 阶段 2:圆锥 Form 与注册

- [ ] 新建 `SoundConeForm`(design.md §1),`shapeGain` 走 `SoundAcoustics.coneGain`,锥轴取自 `Form.transform` 旋转。
- [ ] 确认 `range` 为锥形下 `maxDistance` 的唯一真相源(R1.3 硬语义)。
- [ ] 三处注册:`BBSMod.java`(`sound_cone`)、`FormUtilsClient.java`、`UIFormEditor.java`。
- [ ] 新建 `UISoundConeForm` + `UISoundConeFormPanel`(布局照子任务 2,追加锥体四参数)。
- [ ] 验证:`cd new && ./gradlew compileClientJava > /tmp/cone2.log 2>&1; echo REAL_EXIT=$?`
- [ ] 冒烟:能创建 `sound_cone`,面板可调参,能发声,方向性听感正确(内锥/过渡/外锥三段有别)。

## 阶段 2.5:反射面收集(由子任务 2 移交)

子任务 2 完成时,反射换算与预算截断已实现且有测试覆盖,但**世界侧的反射面收集未做**,播放路径目前只调度直达声(`SoundFormPlayback.NO_SURFACES`)。它需要世界方块查询,且与可视化共用几何,故并入本任务。

- [ ] 新建反射面探测:从声源出发,在最大距离内沿轴向采样,把命中的实心方块面转成 `SoundReflections.Surface`(平面上一点 + 单位法线)。
- [ ] 复用池化的 `Surface[]`,每帧不分配(C3)。
- [ ] 形状约束(PRD R5.3):球形接受各方向;锥形只接受落在锥体覆盖方向内的面。
- [ ] 在两个 Form 的 renderer 里把收集到的面传进 `SoundFormPlayback.update`。
- [ ] 冒烟:开启反射后能听出回声,增大反射次数回声更密;关闭反射时与普通 3D 音源等价。

## 阶段 3:绘制原语(逐个编译验证,防 API 映射踩坑)

按 `research/irlights-addon-reference.md` §5 映射表改写为 1.21.1,**每个原语单独编译验证后再做下一个**:

- [ ] `SoundGuideRenderer.renderRing`
- [ ] `renderSphere`(三正交圆 + 三轴线)
- [ ] `renderCone`(中轴 + 4 母线 + 外环 + 内环)
- [ ] 圆顶点改用预计算 sin/cos 表(C3 零分配)
- [ ] 线宽自适应 `clamp(r * 0.002, 0.002, 0.009)`
- [ ] 渲染态 `disableCull` + `depthMask(false)` + `disableDepthTest`
- [ ] 每步验证:`cd new && ./gradlew compileClientJava > /tmp/cone3.log 2>&1; echo REAL_EXIT=$?`

## 阶段 4:选中判定与挂钩

- [ ] 实现选中标记(`WeakHashMap` + 300ms TTL + 回放编辑器判定,design.md §4)。
- [ ] 在两个 Form 的 renderer 中接入绘制,条件 `globalShowGuides || isSelected(form)`。
- [ ] 加全局开关(R5.2)。
- [ ] **确认不需要任何 Mixin**(我们在主仓内,直接改对应类)。
- [ ] 冒烟:选中时显示、切到相机编辑器立即隐藏、最终渲染输出中不出现。

## 阶段 5:强度分层

- [ ] 三种衰减模型各自实现 `-6dB` / `-12dB` 距离的解析反解。
- [ ] 无解或非单调时跳过该档,不画错。
- [ ] `renderIntensityBands`:参考距离 / -6dB / -12dB / 最大距离 四档,颜色或透明度区分。
- [ ] 验证一致性:分层显示的"听不见距离"与实际听感吻合(冒烟项)。

## 阶段 6:拖拽编辑(独立阶段,可单独回滚)

- [ ] 绘制时捕获 local→view 矩阵。
- [ ] stencil 拾取:锥高 / 外角 / 内角三个手柄(球形一个半径手柄)。
- [ ] 鼠标射线反投影到局部坐标系,反算对应字段;**手柄位置与反算全部读阶段 1 的几何真相源**。
- [ ] 拖拽期间面板数值同步刷新(R4.2)。
- [ ] 验证:`cd new && ./gradlew compileClientJava > /tmp/cone6.log 2>&1; echo REAL_EXIT=$?`

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
