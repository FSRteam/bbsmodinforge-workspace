# PRD: 附魔 UI 文本精简与 alpha 光效修复

## Goal

修正附魔（enchantment glint / 光效）编辑器中的 UI 文案与光效强弱控制体验。

## Requirements

1. section 标题「附魔层变换」过长拗口 → 精简为「附魔」。
2. 「点击切换」模式选项文本过长 → 精简为「无 / 整体 / 边缘 / 原版」。**保留全部 4 个选项**（含原版），只精简文本；`glintMode` 索引 0-3 不变，已存形态读回安全。
3. 「附魔速度」→「速度」。附魔 section 内无歧义（「移动」section 的 movement_speed 不影响本 section）。
4. 颜色滑块不透明度（alpha）应平滑控制光效强弱，但当前几乎看不出变化 → 修复 alpha 渲染链，使 alpha 在 0~1 范围真正生效。

## Acceptance Criteria

- [ ] 附魔 section 标题显示「附魔」，不再显示「附魔层变换」。
- [ ] 切换按钮依次显示「无 / 整体 / 边缘 / 原版」，点击循环正常；已存「原版」形态读回仍是原版。
- [ ] 「附魔速度」标签显示「速度」。
- [ ] 不透明度从 1.0 降到 0.5、0.1 时光效明显平滑变弱；到 0 时不再可见。
- [ ] 打开光影（Iris）时透明度仍按可预期方式影响光效（不回归）。
- [ ] 相关回归测试（UIInputMailbox 等）通过。

## Notes

- 作用域：`new/` 项目（FSR），`src/client/` + `src/main/`。
- **不改 glintMode 数据索引**、**不新增数据字段**、**不扩 alpha 范围**（维持 0~1）。
- 中文本地化在 `src/client/resources/assets/bbs/assets/strings/zh_cn.json`；同步改 `en_us.json`。
