# 装饰贴花层与背景扩面

## Goal

补齐参考图的"装饰感":主题可声明锚定装饰贴图(草莓/蝴蝶结类角落贴花)与全界面背景铺底。两者同属"叠加装饰性贴图"机制,合并交付。

## Requirements

### 贴花层

- R1 theme.json 新增可选 `decorations` 数组:`{texture, anchor, offset: [x,y], scale, opacity, layer}`;anchor 九宫位(top_left…bottom_right),layer 至少支持 `screen`(全屏级,最上层 UI 之下/之上二选一,设计时定死一种并文档化)。
- R2 贴花**永不参与命中判定**,纯渲染叠加;缺失贴图解析期丢弃并警告(validateTextures 模式延续)。
- R3 数量上限(建议 16)防滥用;解析期定死为不可变列表,渲染零分配。

### 背景扩面

- R4 `background` 从仅 UIDashboard 扩为根面板通用铺底(dashboard、独立 UI 屏);新增可选 `background_mode: cover|tile|stretch`(缺省 stretch 保持 v1 行为)与 `background_dim`(0-1 暗化系数,缺省 0)。
- R5 未声明 background 的主题(v1 四内置)渲染逐位不变。

## Out of Scope

- 贴花动画/飘动(v3 候补)。
- 每面板独立背景、面板内嵌海报位(布局内容,超出换肤范畴)。
- 毛玻璃(父任务已裁决不做局部 blur)。

## Constraints

- 建议在 rounded-corners 之后实施(演示主题联动);无硬依赖。
- example 主题与 `docs/theme-spec/` 同步新 schema;strawberry 演示主题使用贴花+背景。

## Acceptance Criteria

- [ ] `testThemeCore` 扩充:decorations 解析(合法/坏 anchor 回退/超限截断警告/缺贴图丢弃)、background_mode/dim 解析。
- [ ] 贴花区域点击穿透正确(不挡任何交互)。
- [ ] v1 四内置主题渲染逐位不变;strawberry 演示贴花+cover 背景+暗化。
- [ ] 全量 check 真实 exit code 0;用户游戏内冒烟确认。
