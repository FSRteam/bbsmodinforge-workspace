# Design: FS 2.5 post-sync 对齐修复（5项）

## Boundaries

- 修改范围仅限 `new/` 中与五项问题直接相关的客户端渲染、原版粒子预览、设置注册/界面/语言资源、Form 编辑器和影片预览右键菜单。
- `new-fs25-sync/` 只作为经过迁移的实现依据；`temp/bbs-fs-2.3/` 用于核验上游语义与提交来源，不直接复制 Fabric API 或 Yarn 名称。
- 共享文件采用语义移植。任何修改都必须保留 `new/` 中已存在而迁移快照中没有的 FSR 能力，尤其是多视口渲染、远程输入、主题、音频、插件和附加兼容逻辑。

## 1. Translucent Command State

`FormTranslucentQueue` 继续拥有世界阶段的半透明延迟队列。问题根因不是 Color Pose 数据，而是延迟命令之间的 OpenGL 状态泄漏：`RenderType.clearRenderState()` 会关闭混合，下一条命令若不重新启用混合，就会忽略 alpha。

修复边界：

- 增加统一的混合状态恢复函数，设置 `enableBlend + defaultBlendFunc`。
- `flush()` 在每条命令绘制前调用；`GroupCommand` 在每个子命令前也调用，保证标签文字/背景等组内顺序不受前一层清理状态影响。
- `CustomVertexConsumerProvider` 在原版层仍处于自身 flush 时捕获 Iris 的 extended vertex layout 标记。
- `RenderLayerCommand` 保存该标记，绘制前临时应用，绘制后在 `finally` 恢复。
- 由 FSR 自己构建的普通顶点缓冲明确传 `false`，避免误用扩展步长。

这同时覆盖 Color Pose 半透明模型、粒子/方块/物品层、标签背景，以及 Iris 下的延迟材质读取。

## 2. Isolated Vanilla Particle Preview

新增 `VanillaParticleScene` 作为仅供模型编辑器预览使用的轻量场景：

- 通过最小 Mixin invoker/accessor 创建和读取原版粒子，而不写入客户端世界 `ParticleEngine` 的活动队列。
- 按原版 `ParticleRenderType` 分组绘制，复用 Minecraft 的材质图集和渲染类型。
- 在渲染前后保存并恢复 model-view、混合、深度、纹理等状态。
- 使用预览控件传入的 tick 驱动，限制追帧次数和最大粒子数量，切换/隐藏后释放场景。
- picking 通道不生成或绘制粒子。

`VanillaParticleFormRenderer` 在 `modelRenderer` 上下文走本地场景；世界上下文继续调用 `Level.addParticle`，不改变运行时伪装语义。

## 3. Settings Categories And Localization

设置注册从旧的综合分类拆为五个职责清晰的分类。迁移只搬运已知键，并遵循“新位置已有值优先”，从而避免覆盖用户已经保存的新配置。

- `camera`: 飞行、轨道和相机跟随。
- `viewport`: 参考线、预览尺寸、洋葱皮、运动路径、IK/物理调试。
- `timeline`: 时长、跳转、循环、时间显示、关键帧默认值和时间轴外观。
- `workspace`: 布局、面板尺寸、自动保存和退出保留画面。
- `misc`: 破坏控制、着色器曲线、多皮肤线程和实体选择器白名单。

设置 UI 采用迁移快照中的行布局扩展，仅为分辨率和导出路径提供组合行；现有 `UIValueMap` 与 FSR 专用值工厂继续保留。

语言资源处理分两层：

1. 对所有语言机械迁移受影响设置键的分类前缀，值原样保留。
2. 补全五个分类的标题/说明；中英文使用完整人工文案，其他语言优先使用已有词汇或可靠短译，无法可靠翻译的说明回退英文但不得为空。

## 4. Body Attachment Reveal

`BodyPartManager` 已保证追加顺序。刷新 `UIForms` 后通过对象身份找到新条目，调用 `setCurrentScroll` 和 `pickForm`，使列表滚到末尾并打开该附件。找不到条目时保持现有行为，避免空引用。

## 5. Camera Context Menu

保留 `populateCameraModeMenu()` 的六模式循环供左键模式选择入口使用。相机模式按钮的右键菜单不调用该循环，只追加轨道相机的传送、绑定和正交动作；动作本身继续由控制器处理当前状态。

## Compatibility And Rollback

- 保持 `mod_version=0.0.11` 与现有产物身份。
- 每项修改可按文件组独立回退；设置配置迁移为只复制不删除旧值，因此回退版本仍可读取旧分类。
- 不整文件覆盖 `BBSRendering`、`BBSSettings`、`UIFilmPreview` 或语言资源；仅应用经过差异审查的局部改动。
- 运行时视觉验证覆盖无 Iris、Iris、模型编辑器预览和影片编辑器右键菜单。无法自动验证的部分在交付报告中标为待手测。
