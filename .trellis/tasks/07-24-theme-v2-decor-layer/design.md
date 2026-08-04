# decor-layer 技术设计

依赖阅读:本任务 `prd.md`、父任务 `../07-24-theme-v2/design.md` 的“视觉架构”部分。

## 1. 统一装饰模型

本任务把两类“纯渲染装饰贴图”合并:

- decorations: 九宫锚定贴花
- background: 根界面铺底

共同约束:

- 解析期定死为不可变结构
- 渲染期零分配
- 永不参与命中

## 2. decorations

### 2.1 数据结构

`UITheme` 新增不可变装饰项列表，每项包含:

- `texture`
- `anchor`
- `offsetX/offsetY`
- `scale`
- `opacity`
- `layer`

### 2.2 anchor

固定九宫位:

- top_left / top / top_right
- left / center / right
- bottom_left / bottom / bottom_right

坏值丢弃并警告，不回退到“猜测位置”。

### 2.3 layer

首版只允许一套固定层语义:

- 装饰绘制在全屏背景之后
- 主 UI 之前

这样既能形成“贴花在界面背后”的质感，又天然点击穿透。

## 3. background 扩面

### 3.1 模式

`background_mode`:

- `stretch`
- `cover`
- `tile`

缺省值仍为 `stretch`，保证旧主题不变。

### 3.2 dim

`background_dim` 为 0..1 的暗化蒙层，绘制顺序:

1. 背景贴图
2. dim 蒙层
3. decorations
4. 主 UI

## 4. 落点

优先把背景/装饰接到根菜单或根 overlay 的公共绘制入口，而不是每个面板重复接线。

如果当前只有 dashboard 有背景消费点，则抽出一个共享 helper 供:

- dashboard
- 独立 UI screen / root menu

共同使用。

## 5. 验证

- `testThemeCore`
  - decorations 合法/坏 anchor/超限/缺贴图
  - background_mode/dim 解析
- `compileClientJava`
  - 验证根绘制路径无误
