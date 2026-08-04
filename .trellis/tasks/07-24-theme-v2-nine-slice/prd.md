# nine-slice 控件贴图皮肤

## Goal

v2 视觉线的封顶增强:控件可选 nine-slice(九宫格)贴图替代纯色填充,让主题作者用美术贴图实现圆角原语表达不了的质感(花纹边框、渐变面板、烘焙软阴影)。参考图的"草莓花边卡片"级效果由此达成。排在 rounded-corners 之后——多数主题用圆角+配色即可,贴图皮肤面向高投入作者。

## Requirements

- R1 theme.json 新增可选 `skins` 对象,按控件类声明贴图:第一批 `panel`(弹层/卡片)、`button`(normal/hover/pressed 三态)、`text_field`;值为 `{texture, border: [top,right,bottom,left] 或单值, scale}`。
- R2 nine-slice 渲染:四角原尺寸、四边单轴拉伸/平铺、中心拉伸/平铺(`fill_mode: stretch|tile`,缺省 stretch);基于现有 texturedBox 组合实现,不写 shader。
- R3 未声明 skins 的控件走既有渲染路径(纯色或圆角),逐位不变;声明后贴图**取代**该控件的背景填充与边框绘制,语义色仍用于文字/图标。
- R4 贴图缺失时解析期丢弃该项并警告,回退默认渲染(validateTextures 模式)。
- R5 与动画兼容:hover 三态切换经过 hover tween(贴图间淡变或即切,设计期定死并文档化)。

## Out of Scope

- 每实例控件皮肤(仅控件类粒度)。
- 图集打包(每皮肤独立贴图文件,主题作者友好优先)。
- 列表/滚动条/复杂编辑器控件的皮肤(后续批次)。

## Constraints

- 依赖 rounded-corners(接管背景绘制的控件接缝处已按批次整理)。
- 热路径:nine-slice 顶点数固定,零分配;贴图句柄解析期解析。
- `docs/theme-spec/` 同步 schema 与贴图规格说明(含 border 语义示意)。

## Acceptance Criteria

- [ ] `testThemeCore` 扩充 skins 解析用例(合法/border 单值展开/坏值回退/缺贴图丢弃)。
- [ ] 无 skins 主题(全部既有内置)渲染逐位不变。
- [ ] 演示:strawberry 主题至少给 panel+button 挂贴图皮肤,三态正确。
- [ ] 全量 check 真实 exit code 0;用户游戏内冒烟确认。
