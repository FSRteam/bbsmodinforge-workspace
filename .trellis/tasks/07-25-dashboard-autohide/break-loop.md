# Bug Analysis: 自动隐藏错误移动整个活动面板

## 1. Root Cause Category

- **Category**: E - Implicit Assumption
- **Specific Cause**: “上方的任务栏”被错误映射为 `UIDashboardPanels.panel` 整个活动面板,而真实目标是 `UIDataDashboardPanel.tabBar` 及其 Film 顶部操作按钮。这个错误心智模型随后被写入 design、spec 和源码字符串回归测试。

## 2. Why Fixes Failed

1. 初始移植只复现 Refreshed 原版底部 `taskBar` 的 20px 自动隐藏,没有覆盖项目新增的顶部数据页栏需求。
2. 第一次反馈修订把“上方项目”理解为活动面板全部内容,使用 `hiddenAmount * rootHeight` 修改面板根 `y`,修掉了“只隐藏底栏”的表面现象,却把编辑器主体整体推出屏幕。
3. 回归测试断言了 `panel.y = slide` 和 root-height 位移,将错误行为当成契约,使常规测试无法发现视觉语义已经偏离用户要求。

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | 在 `UIDashboardPanel` 暴露顶部 chrome 隐藏/hover 钩子,由 `UIDataDashboardPanel` 只移动自己拥有的 `tabBar`;容器不得移动 panel root | DONE |
| P0 | Test Coverage | 回归测试要求底栏按 20px、顶部栏按自身高度移动,并显式拒绝 `panel.y = slide` | DONE |
| P0 | Documentation | 更新 dashboard-autohide PRD/design/implement 和 frontend theming spec,明确“上下界面栏退出、编辑主体锚定” | DONE |
| P1 | Runtime QA | 游戏内验证顶部/底部唤回、隐藏中命中和 Film 顶部编辑器切换按钮 | TODO |

## 4. Systematic Expansion

- **Similar Issues**: overlay、侧栏或停靠栏若需要自动隐藏,也必须移动其自身 chrome,不能通过移动宿主根容器模拟隐藏。
- **Design Improvement**: 布局容器只协调归一化隐藏进度;具体 chrome 的方向、尺寸和内容 inset 由拥有该元素的 panel 实现。
- **Process Improvement**: 用户使用位置词描述 UI 时,先用现有组件层级确认具体元素;源码测试必须验证用户可见结果,不能只验证当前实现字符串。

## 5. Knowledge Capture

- [x] 更新 `.trellis/spec/frontend/bbs-ui-theming.md`
- [x] 更新自动隐藏子任务 PRD/design/implement
- [x] 更新 UI 生命周期回归测试
- [ ] 完成游戏内冒烟验证
