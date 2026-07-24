# motion-coverage 技术设计

依赖阅读:本任务 `prd.md`、父任务 `../07-24-theme-v2/design.md` 的“层 3 覆盖面”与 §3.3/§3.4。

## 1. 四类覆盖面

本任务拆成四个彼此独立的微动画系统:

1. 平滑滚动
2. hover 缩放
3. 按下回弹
4. 面板布局过渡

所有条目都挂在 `UIMotions` 总闸下，关闭态必须逐位回到 v1。

## 2. 平滑滚动

### 2.1 双值模型

滚动区域维护两个值:

- `targetScroll`: 逻辑值，滚轮/拖拽即时修改
- `scroll`: 视觉值，每帧向 `targetScroll` 指数趋近

规则:

- 命中判定/边界裁剪使用 `targetScroll`
- 渲染偏移使用 `scroll`
- `motion.enabled=false` 或 `scroll_smooth.enabled=false` 时，`scroll == targetScroll`

### 2.2 落点

优先挂在共享 scroll 实现或 list/grid 基类，而不是每个列表各写一套。

## 3. hover 缩放 / 按下回弹

### 3.1 适用范围

首批只接到“明确可点击且以中心绘制”的控件:

- `UIButton`
- `UIIcon`
- 视现有结构可扩到 `UIToggle`

### 3.2 应用顺序

以控件中心为锚点:

1. translate 到中心
2. scale
3. translate 回左上
4. 原渲染逻辑

hover 与 press 可叠乘，但默认值都等于 1，不影响关闭态。

## 4. 布局过渡

### 4.1 模型

只对“面板级布局切换”生效，不对任意 element 普及。

流程:

- 布局变更前抓 `from`
- 应用新布局并抓 `to`
- 动画期间每帧把插值后的 bounds 写回布局对象并 `resize()`
- settle 后直接落到 `to`

### 4.2 打断

若动画中再次切换布局:

- 先把当前插值态冻结成新 `from`
- 再抓新 `to`

### 4.3 阈值

`from/to` 差异足够小时不动画，直接落位。

## 5. 主题键

本任务最少需要新增这些 motion 条目:

- `scroll_smooth`
- `hover_scale`
- `press`
- `layout`

先延续 v2 motion 条目结构:

- `enabled`
- `type`
- `duration/response/damping`

若需要额外的 `scale` 参数，优先局部解析在条目内，不提前引入完整 track 系统。

## 6. 测试策略

- `testUiMotions`
  - smooth scroll 追赶/关闭态等价
  - hover/press 默认值恒等
  - layout transition 打断连续
- `compileClientJava`
  - 验证真实控件接线

