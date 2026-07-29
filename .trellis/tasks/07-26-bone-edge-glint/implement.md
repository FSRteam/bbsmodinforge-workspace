# 执行计划：按骨骼附魔效果

## 步骤

### S1 数据层
- `main/.../utils/pose/PoseTransform.java`：新增 `glintMode` / `glintColor` / `glintSpeed` + `GLINT_*` 常量；同步 `identity` / 两个 `lerp`（模式走阈值阶跃）/ `equals` / `copy` / `add` / `toData` / `fromData`
- `main/.../cubic/data/model/ModelGroup.java`：新增运行时字段，`reset()` 同步
- `main/.../cubic/data/model/Model.java` `applyPose()`：搬运三个字段

**验证**：`compileJava` + `compileClientJava` 通过。

### S2 Shader / RenderType（已按光影兼容反馈重做）
- 删除 `main/resources/assets/bbs/shaders/core/model_glint.{json,vsh,fsh}` 及 `BBSShaders` 注册
- 新增 `GlintRenderState`，原版模式走 `armorEntityGlint()`，全表面/边缘走 `energySwirl(...)`
- 边缘 Fresnel 改为 CPU 顶点 alpha；二次 pass 关闭深度写入，XYZ 变换使用 `LEQUAL`

**验证**：编译通过；Iris/光影包 gbuffer 路由仍需入游戏验收。

### S3 VAO 路径
- `cubic/render/GlintRenderState.java`：在 vanilla RenderType 已设置当前 shader 后复用现有 VAO
- `cubic/render/CubicVAORenderer.java`：`renderGroup()` 末尾调 `renderGlint()`；`stencilMap != null` 或 `glintMode == 0` 时跳过

**验证**：编译通过。

### S4 CPU 即时路径
- 新增 `cubic/render/CubicGlintCubeRenderer.java`
- `cubic/ModelInstance.java`：新增 `renderGlintImmediate()`，在 `drawImmediate` 之后调用

**验证**：编译通过。

### S5 UI 层
- `ui/utils/pose/UIPoseEditor.java`：`UICirculate` + `UIColor` + `UITrackpad` 三控件、`pickBones` 回显、`afterFilter` 可见性、`applyGlint*ToSelection`、`setGlint*`
- `ui/forms/editors/panels/widgets/UIModelPoseEditor.java`：三个 override 做 `preNotify/postNotify`（模式用 `FLAG_UNMERGEABLE`，颜色与速度可合并）

**验证**：编译通过。

### S6 i18n
- `ui/UIKeys.java`：7 个 `IKey` 常量
- `client/resources/assets/bbs/assets/strings/en_us.json`（**Tab 缩进**）
- `client/resources/assets/bbs/assets/strings/zh_cn.json`（**4 空格缩进**）

⚠ 两文件缩进风格不同，勿混用。无自动 parity 测试兜底，需手动校验：
```bash
python -c "import json;en=json.load(open('en_us.json',encoding='utf-8'));zh=json.load(open('zh_cn.json',encoding='utf-8'));print(len(en),len(zh),sorted(set(en)^set(zh)))"
```

### S7 全量构建
```bash
cd new && ./gradlew build > build-glint.log 2>&1; echo "REAL_EXIT=$?"
```

### S8 覆膜透明与杂项 Form 复核
- [x] `GlintRenderState` 手动启用加性混合、关闭 depth write，并在绘制后恢复
- [x] 使用原版加性 RenderType，透明处不再被不透明黑色片元覆盖
- [x] Billboard / Extruded / Block / Item 声明并实现整体附魔，通用 Form 面板按 `supportsGlint()` 显示选项
- [x] 重新构建 jar；透明叠加与四类 Form 选项仍待入游戏观察确认

### S9 附魔层 XYZ 变换
- [x] Form 新增 `glintTransform: ValueTransform` 并注册到值树
- [x] `PoseTransform` / `ModelGroup` / `Model.applyPose()` 接入逐骨骼 `glintTransform`
- [x] shader 新增 `GlintTransform`，所有 glint 渲染路径统一设置；CPU 顶点路径在模型局部空间预应用
- [x] 通用 Form 面板与 Pose 面板加入 XYZ 位移/缩放/旋转控件
- [x] Pose 多选同步、应用到子骨骼、撤销/重做与中英文文案补齐
- [x] Form 颜色使用 Value setter，确保撤销与关键帧通知不被绕过
- [x] Billboard / Extruded / Block / Item 拾取路径跳过附魔二次绘制
- [x] 多材质 VAO 的附魔 pass 按材质 VAO 分批绘制
- [x] Item 捕获器丢弃 vanilla glint RenderType，避免自带附魔物品的几何重复写入

### S10 最终验证
- [x] `compileJava` / `compileClientJava`（初轮 `BUILD SUCCESSFUL in 2m 57s`）
- [x] 最终完整 `compileClientJava --rerun-tasks`（初次 `6m 16s`；本轮修复 NeoForm 缓存缺口后复核 `BUILD SUCCESSFUL in 12m 34s`）
- [x] `en_us.json` / `zh_cn.json` key 双向对等（1962/1962）
- [x] 初轮全量 `build` 真实退出码为 0 且日志含 `BUILD SUCCESSFUL`（9m16s，77 tasks）
- [x] 最终集成 `build -x testAudioLifecycle`（`BUILD SUCCESSFUL in 6m 41s`，75 tasks）；未排除时仅被无关音频用例 `AudioLifecycleTest.java:1336` 的 `firstPreview == null` 阻塞
- [x] 初轮 JAR：`BBS_FSR_beta_v0.0.10.jar`，8,051,101 bytes，2026-07-27 05:29:55 +08:00，SHA-256 `D5F53BEB54E3AEF3FE739EEF6549EDCA7F196FAEB70517727DCD31EF31ED0BAC`

### S11 单骨骼附魔状态与透明深度回归
- [x] `CubicVAORenderer` 改为只收集附魔，`ModelInstance` 在 VAO/CPU/hybrid 本体全部完成后统一提交
- [x] 新增延迟 VAO/Mesh 附魔命令，Billboard / Extruded / Block / Item 与模型附魔都排在场景半透明本体之后
- [x] 恒等附魔使用 `GL_EQUAL`，变换附魔使用 `GL_LEQUAL`；CPU 合批按深度策略拆分
- [x] `GlintRenderState` 保存并恢复嵌套 RenderType 的 shader、纹理、纹理矩阵、颜色、混合、深度与剔除状态
- [x] `compileClientJava` 成功；`compileClientJava jar sourcesJar --offline` 最终成功
- [x] 最终 JAR：`BBS_FSR_beta_v0.0.10.jar`，8,072,363 bytes，2026-07-27 13:11:14 +08:00，SHA-256 `6EE90992C093CEA66942730A0F80E548214046F1070B7980EB1688AC71792DBE`
- [ ] 入游戏验收：Iris 开/关、多材质透明区、前方方块/实体遮挡、VAO/CPU/hybrid
- [ ] 全量 `build`：代码/JAR阶段与多组测试已通过；当前分别被 `logs/latest.log` 外部占用以及缺失的 Mojang 资源索引/联网初始化阻塞

### S12 Photon / IterationRP 光影兼容与方向回归

- [x] 直接读取 `new/log/photon_v1.3b.zip` 与 `new/log/iterationRP Alpha 0.8.25.zip`，确认两者都提供独立 `gbuffers_armor_glint`，Photon glint 程序忽略 `gl_Color`，IterationRP glint 程序读取 `gl_Color`
- [x] Iris 世界光影启用时三种模式统一路由 `armorEntityGlint()`
- [x] ~~自定义两种模式在该通道覆盖纹理矩阵以保留速度~~（用户实测否定：导致所有光影下自定义两种模式消失，S13 已撤销）
- [x] ~~边缘 Fresnel 假定 PoseStack 烘焙空间的视点恒为原点~~（用户实测否定：半圆方向缺失仍存在，S13 已替换为逆 ModelView 视点与同矩阵链）
- [x] `compileClientJava --offline`：`BUILD SUCCESSFUL in 1m 52s`
- [x] 完整联网 `build`：`BUILD SUCCESSFUL in 6m 54s`，77 tasks；首次离线构建仅因缺少 Mojang `17.json` 资源索引失败，联网补齐后全部测试通过
- [x] 最终 JAR：`BBS_FSR_beta_v0.0.10.jar`，8,076,640 bytes，2026-07-27 15:29:39 +08:00，SHA-256 `923BA3C31AA4BB9CEDFFF7478A77F15DC36FED6EE5067990FEEEE0580C122A71`
- [x] 产物字节码核验：`GlintRenderState` 含 Iris/world 分流与 `setTextureMatrix`；两个 Fresnel 类均不含 `getModelViewMatrix`
- [ ] Photon / IterationRP 入游戏矩阵：东南西北四方向、上下观察、透明材质、前景遮挡、三种模式

### S13 光影自定义模式不可见与半圆方向二次修复

- [x] 对比可见的原版模式与不可见的自定义模式，确认光影路径唯一额外状态是 `setupArmorGlintTexturing()`；移除覆盖，三种模式均继承 `armorEntityGlint()` 的原版纹理矩阵
- [x] VAO 边缘网格改为骨骼局部生成，绘制时复用本体 `global * bone` ModelView，保证未变换覆层的 `GL_EQUAL` 深度计算顺序一致
- [x] Cubic 即时、VAO 边缘与 Block/Item 捕获路径统一从剩余 ModelView 的逆矩阵计算 emitted-vertex 空间视点
- [x] 普通 `compileClientJava --offline`：`BUILD SUCCESSFUL in 1m 21s`；产物时间晚于源文件
- [x] `compileClientJava --rerun-tasks --offline` 强制重编译：`BUILD SUCCESSFUL in 8m 44s`，26 tasks（7 executed）
- [x] 完整 `build`：`BUILD SUCCESSFUL in 4m 56s`，77 tasks（26 executed）；所有已配置测试通过
- [x] 新 JAR：`BBS_FSR_beta_v0.0.10.jar`，8,079,667 bytes，2026-07-27 21:22:50 +08:00，SHA-256 `125706165532907493E6EB23B9979414A25C4216646A9CBA2DF26404DBF750CA`
- [x] JAR 字节码核验：包含四个修改类；`GlintRenderState` 含 `getViewOrigin(Matrix4f)` 且已无 `setupArmorGlintTexturing`
- [ ] 游戏内验收：Photon / IterationRP，东南西北与上下观察，全表面/仅边缘/原版三模式

### S14 附魔分组折叠与 Pose 空白布局修复

- [x] `UISection.resizeParent()` 改为重排最近的 `UIScrollView`，折叠后同步后续控件位置、滚动范围与滚动位置钳制
- [x] 移除影片 Pose 的骨骼列表视口拉伸，保留固定列表高度，避免折叠释放高度变成长空白
- [x] `UIPoseKeyframeFactory` 的宽、窄两种布局都重新加入完整 `glintSection`，模式/颜色/速度/变换保持整体可折叠
- [x] 回归测试覆盖折叠后滚动范围缩短、后续控件立即上移、滚动位置不越界
- [x] `compileClientJava compileUiInputMailboxTestJava --offline`：真实退出码 0，`BUILD SUCCESSFUL in 2m 9s`
- [x] `testUiInputMailbox --offline`：真实退出码 0，`BBSUiInputDispatcherTest: all tests passed`，`BUILD SUCCESSFUL in 1m 41s`
- [x] 四项回归合并后的串行验证：`testUiInputMailbox testFilmEditorMigration testClientStructuralRegistration compileClientJava --offline`，真实退出码 0，`BUILD SUCCESSFUL in 3m 8s`
- [ ] 游戏内验收：模型效果/骨骼窗口与影片 Pose，在展开/折叠附魔层后均可直接滚到最底部，且无需缩放窗口刷新

## 验证红线

⚠ **绝不能**用 `./gradlew ... | tail` 判断成败 —— 管道退出码取自 `tail`（恒 0），会吞掉 `BUILD FAILED`。
**判绿标准**：真实 exit code 为 0 **且** 日志含 `BUILD SUCCESSFUL`。

## 回滚点

- S1–S2 纯新增，无风险
- S3/S4 若渲染异常：`renderGlint` / `renderGlintImmediate` 开头直接 `return` 即可全量退回
- S5/S6 若 UI 异常：从 `this.add(...)` 移除两行 row 即可

## 不做

- **BOBJ 路径**：该格式无 per-bone 颜色通路（原作者注释已声明不支持），补齐是独立的大工作量
- 不新增贴图资源（条纹由 shader 程序化生成）
- 不写自动化测试（裸 JVM 测试会触发 `BBSMod` 静态初始化崩溃；用户确认编译通过即可）
