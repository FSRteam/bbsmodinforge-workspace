# PRD: 控件颜色全量 token 化

父任务:`.trellis/tasks/07-23-fsr-theme-system`。
依赖:`07-23-theme-core` 必须已完成(本任务消费它新增的语义函数)。

## 目标

把客户端 UI 中**语义性的**硬编码 `Colors.*` 用法(约 880 处中的语义子集)替换为
`BBSSettings` 语义函数,使第三方主题改 `colors.text/state/*` token 能作用到整个 UI,
而不是只有表面色。**dark 主题下视觉逐像素不变**(替换是恒等变换:函数在 dark 主题下
返回的值 == 原常量值)。

## 需求

1. 按 design.md 的**映射表**执行替换;映射表是唯一裁决标准,扫描中遇到表外情况
   先补表(记录理由)再改代码。
2. 覆盖范围(优先级从高到低,全部完成):
   - `ui/framework/elements/**`(buttons/input/list/overlay/context/tooltips/notifications/utils);
   - `ui/framework/`(UIBaseMenu/UIContext/UIRenderingContext/UIScreen 中的 2D 绘制);
   - `ui/utils/`(Scroll、Area 渲染、Label、renderers);
   - `ui/dashboard/**`、`ui/settings ui`;
   - 各编辑器面板(film/forms/model_editor/particles/utility 等)中匹配映射表的用法。
3. 明确**不替换**的(保留常量):纯白/灰系用作图标 tint 或永远固定的装饰、gizmo 轴色
   (PLANE_XZ/XY/ZY)、剪辑分类色、代码里做颜色数学的中间量——详见映射表"保留"节。
4. `text_shadow` 样式:`context.batcher.text(..., shadow)` 显式传 shadow 布尔的调用点里,
   属于"普通 UI 文字"的改读 `BBSSettings.textShadow()`;特殊效果文字(标题装饰等)保留。
5. 替换后在 `.trellis/spec/frontend/`(若无则建)登记规约:新 UI 代码禁止硬编码语义色。

## 验收标准

- [ ] `compileClientJava` 通过;`./gradlew check` 全量通过。
- [ ] dark 主题下与 theme-core 完成时的基线截图逐像素一致(dashboard、影片编辑器时间轴、
      关键帧编辑器、右键菜单、tooltip、通知、设置面板,共 7 张)。
- [ ] 用一个"故意夸张"的测试主题(text.primary=红、state.positive=紫 等)确认:
      正文文字、按钮文字、列表文字、正/负状态色、active 高亮、光标色全部跟随主题。
- [ ] light 主题下的视觉变化仅限"修复类"(原本 light 下硬编码白字不可读的地方),
      逐条记录在 implement.md 的清单里。
- [ ] 替换统计:framework 目录内语义色硬编码清零(`grep` 白名单核查,白名单=映射表"保留"节)。

## 约束

- 只改取色表达式,不改布局、不改事件、不改绘制顺序。
- 不新增 token(发现缺 token 的语义 → 记录到父任务,v2 再扩);用现有 9+N 个语义函数硬凑
  不合适时宁可保留常量并登记。
- 大扫描分目录提交(见 implement.md),每步可独立回滚。
