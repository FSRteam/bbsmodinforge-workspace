# 执行计划：按骨骼附魔效果

## 步骤

### S1 数据层
- `main/.../utils/pose/PoseTransform.java`：新增 `glintMode` / `glintColor` / `glintSpeed` + `GLINT_*` 常量；同步 `identity` / 两个 `lerp`（模式走阈值阶跃）/ `equals` / `copy` / `add` / `toData` / `fromData`
- `main/.../cubic/data/model/ModelGroup.java`：新增运行时字段，`reset()` 同步
- `main/.../cubic/data/model/Model.java` `applyPose()`：搬运三个字段

**验证**：`compileJava` + `compileClientJava` 通过。

### S2 Shader
- 新增 `main/resources/assets/bbs/shaders/core/model_glint.{json,vsh,fsh}`
- `client/BBSShaders.java`：字段 + `setup()` 构造 + `close()` + `getModelGlint()`

**验证**：编译通过（shader 编译错误只在运行时暴露，需入游戏验收）。

### S3 VAO 路径
- `cubic/render/vao/ModelVAORenderer.java`：`render()` 增加可选 `Consumer<ShaderInstance>` 重载
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
