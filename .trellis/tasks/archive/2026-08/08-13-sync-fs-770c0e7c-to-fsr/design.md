# Design: sync FS 770c0e7c delta to FSR

## Boundaries

源行为边界是 FS `master@4220b453..770c0e7c`(16 个线性提交)。目标从 `new/`
确认的干净基点(预期 `637d6f76`)在专用 worktree 起步。这是语义移植:FSR 有
更新的 `UIDockLayout`、独立主题系统(Refreshed/theme v2)、`FormTranslucentQueue`
延迟透明队列、NeoForge 网络层、远程输入保护等,上游文件不得整文件替换这些实现。

上游 `origin/1.21.1@a982f1dc` 已包含全部 16 个提交的 Fabric 1.21.1 适配
(yarn 命名),凡涉及 MC API 的组(B/E/G)先对照该分支的同文件形态,再换成
mojmap/NeoForge 写法。

## Commit Disposition(16 项,组内顺序即迁移顺序)

| 组 | 上游提交 | 设计处置 |
|---|---|---|
| E1 | `baafc5a4` 全半透明纹理模型自透 | 语义移植:延迟 pass 中实体几何恢复深度写,平面 form(billboard/framebuffer 屏/label)保持不写深度;落到 FSR 自己的 `FormTranslucentQueue`/`ModelInstance` 对应点 |
| E2 | `770c0e7c` 物品 form 乱面 | 与 E1 捆绑:`RenderLayerCommand` 恒写深度;FSR 中"按层名含 translucent 子串拦截"的对应逻辑按语义核对(mojmap `RenderType` 命名不同) |
| F | `861257a7` VIEW 空间 gizmo 正对相机 | 语义移植:绘制矩阵第三列换成 gizmo→眼睛单位射线(`applyViewShear`),仅作用绘制与拾取 stencil 两个 pass;`lastRenderMatrix` 在 shear 前捕获,拖拽正交基不动;约束辅助线留在 shear 外。JOML 数学与 MC 版本无关。与 FSR `514b2fe2`(锚点跟随)语义不同,确认互不覆盖 |
| A4 | `95a110e2` 编辑器 resize 同步参数面板(1 行) | 先核对 FSR `46613ff5`(dock 构建顺序修复)后此处是否仍缺;缺则移植,已等价则记 target-equivalent |
| C4 | `d0f3754d` 选择/编辑按钮间距(1 行) | 直接移植 `UINestedEdit` 间距 0→MARGIN |
| D1 | `fe552a58` 滑条绝对网格步进 | 语义移植 `UISliderTrackpad`+`BaseValueNumber.slider(step)` 体系;前置 `UISliderTrackpad`(基线 `9496cbc3` 一波)在 FSR 应已存在,开工时验证;保留 FSR 主题色/圆角 |
| D2 | `0773f9df` FOV 回 trackpad(1 行) | 与 D1 捆绑:FOV 设置去 `.slider()` |
| C1 | `459e9db7` 背景色调阶梯(OKLab 中性灰) | 常量替换,但必须落到 FSR 主题层对应位置(不得绕过 theme v2 的 token);明/暗主题镜像行为保留 |
| C2 | `52c5db8f` 阴影/高光拆开关 + 浮动面板压暗/光晕 | 语义移植 4 个新设置项;完成 `bevelBox`→`surfaceBox` 更名并全库清扫 FSR 调用点(含 FSR 独有 UI);默认值照上游(高光默认关) |
| C3 | `bb1ac00f` UIToggle 轻量化 | 依赖 C1 色阶与 C2 `surfaceBox` 签名,随其后;浅色主题行为核对 |
| B1 | `8a293161` 热键栏九轨道 + 电影 inventory 移除 | 本批最高风险。存档格式:删 `inventory` 组、增 `item_slot_0..8` 通道,主手 ≡ `hotbar[selected_slot]`;`FilmLegacy` 按 tick 重放旧逻辑生成九通道 key;播放期借用装备、停止归还;网络包 `SERVER_APPLY_FILM_PLAYER_SETTINGS` 按 NeoForge payload/StreamCodec 重写;ItemStack 序列化按 1.21.x DataComponent 适配;`IEntity`/`MCEntity`/`StubEntity` 增 hotbar API。同时核对 FSR 自身既有存档兼容路径不被破坏 |
| B2 | `c02aa6e6` 旧电影捡起物品消失 | 与 B1 不可分割:空通道不写值(`drivesHotbarSlot`),开播统一清空一次;旧 inventory 盔甲/副手 `migrateWorn` |
| B3 | `8c48a185` 物品轨道分家族配色 + HOTBAR 图标 | 依赖 B1 的九轨道结构;渐变色值按上游,若与 FSR 主题 token 冲突按主题层适配 |
| H | `378bac69` IK/物理专属图标 | 图集资源:`icons.png` 直接取 `8c48a185` 之后的合并状态;核对 FSR 图集 (224,160)/(240,160)/(0,176) 是否空闲,被占则重排坐标 |
| G | `7f99e757` 小数缩放 pixel art 平滑 | 单独评估项:① mixin 目标 `GameRenderer.getRenderTypeTextProgram` 为 yarn 名,NeoForge/mojmap 下注入点重找;② 9 个 core shader 文件按 1.21.1 shader 格式校验;③ 若 FSR 的 UI 缩放实现已不同则需重设计。评估结论(落地或拒绝/延期+理由)必须写入处置矩阵,不阻塞其他组 |
| I | `5b8d714f` UITrackpad ABI 协变覆写 | 预期 rejected:这是 Fabric 1.20.x addon 生态的二进制兼容修复;FSR 的 addon ABI 本就不同。在处置矩阵记录拒绝理由与"基类化 builder 破坏 ABI"的坑(供 FSR 自身 API 冻结时参考)。若 FSR 已做同样的 `UINumericInput` 基类化且承诺 ABI,则改为移植 |

## Contracts

### 渲染(E 组)

- 延迟透明 pass:实体几何写深度,form 间可见性由 far→near 排序保证;平面 form
  构造器显式声明不写深度。
- `RenderLayerCommand` 恒写深度,无按名分支。
- `FormTranslucentQueue` 与粒子主线的所有权不变(基线规格 translucency_policy /
  particle_mainline_policy);FS `31884112` 回滚仍被拒绝。

### 电影数据(B 组)

- 新格式:九个 `item_slot_*` 通道 + `selected_slot` 指针通道;`Film.inventory`
  字段删除;非玩家实体 selectedSlot 恒 0。
- `FilmLegacy` 迁移语义:按 tick 重放旧运行时逻辑,不做简单搬运;inventory
  背景仅给第一人称 replay;录制电影写满九槽,播放对空通道不动格子。
- 播放器装备借用/归还:玩家退出世界、服务器停止时必须停止 ActionPlayer 并归还。
- 网络载荷:`selectedSlot + packEquipment`,StreamCodec 编解码,两端版本一致。

### UI/主题(C/D/F 组)

- 主题值变更一律经 FSR 主题层(token/renderer),不得用上游原始色码替换
  Refreshed/theme v2 token。
- `surfaceBox` 第四参(shadow/border)语义与上游一致,更名后无残留 `bevelBox`。
- 滑条步进:绝对网格(1/2/5×10ⁿ 或显式 `.snap`),Shift÷5、Alt÷25;文本输入、
  方向键、滚轮不受限;trackpad/slider 保留 FSR 主题色与远程输入保护
  (`UIPropTransform` 契约见基线规格)。
- gizmo shear 只影响绘制/拾取,不影响拖拽数学与约束辅助线。

### 本地化

- 新增用户可见文本三语齐平(en_us/ru_ru/zh_cn);不删除 FSR 已有语言与主题词条。

## Compatibility & Rollback

- 旧电影文件经 `FilmLegacy` 读入后行为等价;FSR 既有用户数据(0.0.x 产生)
  不受格式变更破坏——回归用例必须覆盖"fs 旧格式电影"与"FSR 自身旧存档"两条路径。
- 各组独立可回退:E/F/C/D/B/H 每组单独提交,失败组可 revert 而不牵连他组。
- G/I 若拒绝,只留处置记录,零代码变更。
- 版本与产物身份不变(基线规格 Validation Matrix:改动产物身份即拒绝)。

## Validation

按基线规格第 6 节:开工 fetch+ancestry 校验;每组完成跑聚焦测试
(`testFilmEditorMigration`、`testUiInputMailbox`、`testReplayKeyframeConsistency`
等按触及面选择),收尾串行 `compileJava compileClientJava`、`check`、`build`、
`git diff --check`,并验证版本锁、粒子/半透明路径无 diff、终止提交祖先关系。
游戏内视觉/交互烟测(渲染修复、九轨道录制回放、主题观感)记为人工验收项。
