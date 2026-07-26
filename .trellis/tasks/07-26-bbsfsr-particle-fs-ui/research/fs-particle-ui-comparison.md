# FS / BBSFSR 粒子界面对照

日期: 2026-07-26

## 证据来源

- 当前实现: `new/src/client/java/mchorse/bbs_mod/ui/particles/`
- FS 当前源码: `old2.0/bbs-fs/src/client/java/mchorse/bbs_mod/ui/particles/`
- 1.21.1 对照副本: `temp/bbs-fs-1.21.1-ui/`
- FS 模式按钮提交: `old2.0/bbs-fs` commit `93ba4a2c` (`Change UICirculate to UIIcons for much clearer representation`)
- FS 表达式表面提交: `old2.0/bbs-fs` commit `c7bf6544` (`Add layout system to particle editor, pt. 5`)
- 历史审计会话: Codex session `019ede32-8081-7391-bcaf-6933652c99cc`
- 主题规范: `.trellis/spec/frontend/bbs-ui-theming.md`

## 结论

### 1. 模式切换

`new/` 已有与 FS 同源的 `UIIcons`,但粒子界面仍有以下 `UICirculate`:

- 通用模式基类:发射率、寿命、形状、运动。
- 普通粒子:材质、纹理模式、相机朝向、方向来源、颜色模式、过期模式、曲线类型。
- 碰撞外观:材质、纹理模式、相机朝向、方向来源、染色模式。
- FSR 扩展:事件节点类型、事件粒子效果类型。

FS 已验证的图标映射可直接复用；FSR 新增项需用现有 atlas 中最接近的语义图标补齐。值索引必须保持不变。

### 2. 输入表面

FS 的 `UIMolangExpression` 是只读预览式可点击控件:

- 左侧 20px 图标槽。
- 图标槽右侧 1px 语义色条。
- 右侧显示表达式内容，后来增加语法高亮。
- 点击后通过 `editMoLang(...)` 打开独立编辑器。

BBSFSR 当前 `UITextbox` 直接编辑表达式并在输入回调中解析、记录撤销边界。用户明确要求保留这一交互，因此不能移植 `UIMolangExpression` 本身。

推荐在 `UITextbox` 增加默认关闭的 leading-icon 能力:图标、可选色条、内容左内边距。未设置图标时走原绘制和命中路径；设置后仍由同一个 `Textbox` 处理光标、选择、键盘输入与 focus。

### 3. FSR 独有范围

FS 对照不能覆盖以下现状:

- `ParticleComponentRateManual`
- `fullTexture`
- 第四种 `ParticleMaterial` additive
- `ParticleComponentShapeCustom`
- `CameraFacing` 的 `LOOKAT_DIRECTION` 与三种 emitter transform
- `ParticleComponentCollisionAppearance` 及其 tint
- 粒子事件节点与 effect type

这些项必须跟随同一图标化规则，否则一个界面中会混用图标分段和文字循环两套交互。

### 4. 明确不迁移

- FS `UIMolangExpression` 的弹窗编辑回调。
- FS 粒子数据模型和运行时组件。
- FS `UIDockLayout`/`PARTICLE_LAYOUTS` 对当前标签页系统的结构替换。
- 与本次控件视觉无关的 rotation/motion-axis 新组件迁移。

## 验证风险

- 11 个相机朝向在窄栏中单元格较小；必须保持固定控件高度并依赖 tooltip 表达完整名称。
- leading icon 不能通过缩小 `Textbox.area` 破坏鼠标索引换算；应使用内容 inset，让背景、focus 边框和点击区域仍覆盖整个输入框。
- 主题扩展 token 的值可能为 ARGB `0` 哨兵；控件必须回退到现有 `inputSurface/dividerColor` 路径。
- 模式回调按 ordinal/index 取值，图标加入顺序必须与现有 `addLabel` 顺序严格一致。
