# 修复 BFX Light 表单预览选项与光照效果

## Goal

修复四种 BFX Light 表单在 BBSFSR 伪装/表单界面中的三类断链：预览贴图不显示、专用选项缺失、灯光数据未进入实际渲染效果。修复必须以最新 `new-fs25-sync` FSR API 为宿主基线，并保留上游 `TEMP/VFXLIGHTS` 作为可追溯参考。

## Confirmed facts

- 当前 `bfx-light/src` 只有彩色方块占位 `LightFormRenderer`、三个通用控件和 Extra 注册；没有上游的四种专用 UI 面板、LightKeys、LightIcons、LightGuide 或 FormLightCollector。
- 上游快照已包含完整客户端实现：`LightFormRenderer`、四种专用 renderer、`UILight*Panel`、`LightKeys`、`LightIcons`、`FormLightCollector`、`LightCompositor`、`VolumetricPass` 和相关资源。
- 上游客户端使用 Fabric/Yarn 1.20.x，不能原样放入 NeoForge/Mojmap 1.21.1；`new-fs25-sync` 的 `FormRenderer`、`FormRenderingContext`、`FormUtilsClient`、`BBSClientApi` 是迁移后的真实契约。
- 当前插件仍可编译，说明数据层没有问题；缺陷在客户端投影和消费链，而不是表单序列化本身。

## Requirements

- [ ] 用真实 BFX Light 图标/预览绘制替换彩色方块占位，使四种表单在 Extra 列表、伪装选择器和编辑器预览中都能看到可辨识图标/形状。
- [ ] 迁移四种表单的专用选项：点光源半径，聚光灯角度/内角/源半径，区域灯形状/尺寸/双面/扩散/遮光板，环境灯模式/体积/尺寸/边缘衰减/地面颜色/遮蔽；保留通用颜色、强度、范围、阴影、温度、影响对象和分组等选项。
- [ ] 所有控件修改都写回表单值，打开已有表单时能从值回填控件；分组关键帧值使用已有 `LightStructFactory`，不能改成一次性静态文本。
- [ ] 将表单渲染阶段接入 `LightRegistry`，至少让点/聚光/区域/环境四种灯在 BBSFSR 世界/影片渲染上下文中提交有效的世界坐标、颜色、强度、范围和形状数据。
- [ ] 接入一个经过当前 Mojmap/NeoForge API 验证的光照消费后端；若完整 Iris/阴影/体积管线无法安全迁移，至少提供 vanilla/Fallback 合成效果，并在状态文档中清楚区分已启用和未迁移功能。
- [ ] 不复制 `mchorse/bbs_mod/**`，不发布 `fabric.mod.json`，不降低 Mixin 注入要求来掩盖目标漂移。
- [ ] 增加回归检查，验证四种 UI 注册、关键选项绑定、图标资源存在和灯光提交路径存在。

## Acceptance Criteria

- [ ] 四种 Light 表单在 Extra/伪装界面显示非空、可区分的图标或形状预览，不再显示纯色占位方块。
- [ ] 四种 Light 的专用选项全部出现在对应编辑面板，并能读写真实表单值；编辑已有表单时控件值正确回填。
- [ ] 点/聚光/区域/环境灯在世界或影片渲染路径至少能产生可观察的 BFX Light 消费效果；若需要 Iris 才能显示的高级效果，明确记录并保留安全 fallback。
- [ ] `bfx-light` 的 `compileJava`、`check`、`build` 全部成功，依赖最新 `new-fs25-sync` FSR JAR 快照。
- [ ] JAR 审计通过：包含 NeoForge 清单和 BFX 自有资源，不包含 `mchorse/bbs_mod/**`、`fabric.mod.json` 或 `net.fabricmc` 类。
- [ ] `PORT_STATUS.md` 更新实际已修复能力、未迁移能力、映射依据和运行验证限制。

## Out of scope

- 不在本任务中把上游所有 Fabric/Iris 1.20.x Mixin 逐个无证据改写为 NeoForge 1.21.1；每个注入目标必须有当前宿主签名和运行验证。
- 不修改 `new-fs25-sync` 主工程；如果发现宿主 API 变化，只在 addon 中适配或记录阻塞。
