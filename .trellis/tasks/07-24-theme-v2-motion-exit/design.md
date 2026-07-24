# motion-exit 技术设计

依赖阅读:本任务 `prd.md`、父任务 `../07-24-theme-v2/design.md` 的“层 2 退场对称”与 §3.2。

## 1. 目标边界

本任务是 v2 中唯一允许“碰生命周期”的动画任务，但边界严格锁死在两个封闭出口:

- `UIOverlayPanel`
- `UIContextMenu`

除此之外，其它 UIElement 生命周期、命中判定、事件分发模型保持 v1 语义不动。

## 2. 模型

### 2.1 退场态

关闭请求不再直接从 UI 树物理移除元素，而是进入“退场态”:

- 状态字段: `closing/exiting`
- 进入退场态的时间戳 / tween 起点重置
- 退场完成后才执行原本的 remove/closeItself 物理移除

退场态元素满足:

- 仍参与渲染
- 不再参与命中判定
- 不再接收键盘/鼠标输入
- backdrop 同步淡出

### 2.2 动画来源

默认复用该接入点现有的出现动画参数:

- overlay 走 `UIMotions.overlay()`
- context menu 走 `UIMotions.contextMenu()`

若 motion 总闸关闭、条目 disabled、或时长为 0:

- 立即走 v1 旧路径
- 不引入任何额外状态

### 2.3 反向播放

退场不新建一套独立参数，直接复用出现动画的同一组值并反向采样:

- alpha: `1 -> from`
- scale: `1 -> from`
- x/y offset: `0 -> from`

这样后续 motion-tracks 只需定义“静止态终点 + from 起点”，进入/退出天然对称。

## 3. 落点

### 3.1 UIOverlayPanel

在 `UIOverlayPanel` 中新增:

- 退场状态字段
- `beginCloseAnimation()` 或同等语义入口
- 渲染时读取“开/关统一 progress”

关闭入口统一收口:

- ESC
- 点击膜关闭
- 代码直接 close

若已在退场中，再次 close 为 no-op。

### 3.2 UIContextMenu

右键菜单采用同样模型，但保持更轻量:

- 退场中立即失去命中
- 动画结束后再从菜单栈 / parent 移除

### 3.3 UIBaseMenu / 根入口

如果现有菜单关闭是由 `UIBaseMenu` 统一调度，则只在这里加“退场元素仍渲染、但不再可交互”的桥接逻辑，不改变其它子类。

## 4. 命中与输入

### 4.1 命中短路

退场态元素在这些路径上必须直接返回不可交互:

- hover / mouseClicked / mouseReleased
- keyPressed / charTyped
- context replace / focus 获取

### 4.2 backdrop 语义

backdrop 在退场开始后不再拦截下层点击；观感仍可淡出，但语义上视为“已关闭”。

这条是为了满足“macOS 风格”: 退场只是视觉收尾，不应拖慢下层界面的重新可交互时间。

## 5. 连打与强制移除

- 退场中再次打开同类 overlay/context menu: 旧实例继续走退场并自行清理，新实例正常进入
- 父级强制移除: 若未走完退场，可直接硬移除，不要求补全动画
- 重复 beginClose: 幂等

## 6. 与 motion-engine / motion-tracks 的关系

- 依赖 `Batcher2D` 全局 alpha 栈来做整面板淡出
- 当前阶段只实现“反向复用现有开场参数”
- 后续 motion-tracks 只需把统一 track 采样接到这里

## 7. 测试策略

- `testUiMotions` 增加退场 progress / same-close no-op / motion-off immediate close 的纯逻辑测试
- `compileClientJava` 保证 UI 生命周期签名无误
- 游戏内冒烟重点看:
  - ESC 连按
  - 点击 backdrop 关闭
  - 退场中点击原区域应穿透
  - 快速开/关无残影
