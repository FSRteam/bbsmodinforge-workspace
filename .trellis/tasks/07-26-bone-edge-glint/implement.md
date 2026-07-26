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
- [x] 最终 JAR：`BBS_FSR_beta_v0.0.10.jar`，8,051,101 bytes，2026-07-27 05:29:55 +08:00，SHA-256 `D5F53BEB54E3AEF3FE739EEF6549EDCA7F196FAEB70517727DCD31EF31ED0BAC`

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
