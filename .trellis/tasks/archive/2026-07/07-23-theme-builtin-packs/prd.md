# PRD: 内置主题包与规范样例落地(收尾)

父任务:`.trellis/tasks/07-23-fsr-theme-system`。
依赖:theme-core、theme-widget-tokens、theme-motion-layer、theme-skin-settings-ui 全部完成。
轻量任务:设计已由父任务 design.md 覆盖,PRD + 执行清单即可。

## 目标

把规范样例正式化为随 jar 分发的资源,补一个"看得出换了肤"的演示主题,
并对 `new/docs/theme-spec/` 做实现↔规范逐 key 对齐校验(这是父任务验收 6 的落点)。

## 需求

1. **内置样例主题** `new/src/client/resources/assets/bbs/assets/themes/example/`:内容与
   `new/docs/theme-spec/example-theme/` 同源一致(theme.json 逐字节一致;贴图目录结构说明
   文件不进 jar)。skin-settings-ui 的"导出模板"切到从该资源复制(替换其兜底路径)。
2. **演示主题** `new/src/client/resources/assets/bbs/assets/themes/<id>/`(id 与配色由实现者提议,如高对比 OLED 黑
   或复古琥珀):必须同时展示 颜色全套 + style 开关差异(如关 bevel)+ motion 差异
   (如更弹的缓动),让用户一眼看出主题系统的能力边界。
3. **规范对齐校验清单**:逐 key 过一遍 `docs/theme-spec/README.md` ↔ `ThemeParser` 实际行为
   (键名、默认值、回退、缓动名表、贴图路径语义),清单落在本任务 implement.md,
   不一致处修文档或修实现并记录。
4. **贴图覆盖示例**:`docs/theme-spec/example-theme/textures/` 的说明文件按最终实现核实
   (icons.png 尺寸/网格、背景图格式),必要时补一张真实的示例 icons.png 变体
   (可以是内置 atlas 的色相偏移版)进 example-theme(文档侧,不进 jar)。
5. 三个内置主题(dark/light/example/演示)在主题选择器里来源标记为"内置"。

## 验收标准

- [ ] `./gradlew check` 通过;jar 构建产物含三个(+dark/light 共四个)主题资源。
- [ ] 主题选择器可见并可用全部内置主题;演示主题切换后颜色/样式/动画三层差异肉眼可辨。
- [ ] 导出模板产物 == docs 样例包(diff 校验)。
- [ ] 对齐校验清单全勾,遗留不一致为零。
- [ ] 父任务 prd"跨子任务验收标准"整表通过(本任务顺带执行父任务最终集成冒烟,
      结果记回父任务 implement.md)。

## 约束

- 演示主题不得改动 dark/light 的任何值;三包互不引用(base 各自指向 dark 即可)。
- docs 与 jar 内样例的同步以 docs 为源(docs 改了 → 拷进资源,方向不可反)。
