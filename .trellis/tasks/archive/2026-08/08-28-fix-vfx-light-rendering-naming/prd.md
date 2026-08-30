# 修复 VFX Light 分类、渲染与命名

## Goal

按上游 VFX Light 的实际用户界面和命名恢复移植版本，修复四种灯在 BBSFSR 模型编辑器中的表单分类、图标、选项和世界渲染链。

## Requirements

- 四种表单仍分别注册为 point、spot、area、ambient，不把四种灯合并成一个表单。
- 编辑器恢复上游的 Light、Air、Style 分类；Ambient 只显示有意义的 Light 分类。
- 恢复 Air/Style/Toon/Barn 分组关键帧的独立编辑器，并保持中英文翻译。
- 修复灯光表单 renderer 的世界坐标和实际消费路径；至少在 vanilla/Mojmap fallback 中产生可观察的灯光效果。
- addon 显示名统一为 `VFX Light`，产物文件名统一为 `VFX_Light_fsr.jar`，代码包和 mod id 保持兼容 id `vfxlights`。
- 中英文资源必须从 addon 自己的资源包加载，不能依赖 BBS 核心资源。
- 不修改 `new-fs25-sync` 主工程，不复制上游 Fabric/Iris Mixin 到发布源码。

## Acceptance Criteria

- [ ] 四种表单分别可在 Extra 列表中创建，并显示可区分的真实图标。
- [ ] 表单编辑器左侧显示 Light/Air/Style 分类，Ambient 不出现无效的 Air/Style 分类。
- [ ] 形状、Air、Style、Toon、Barn 和分组选项读写真实值，分组值继续支持关键帧。
- [ ] 世界渲染路径提交四种灯并由 fallback 消费；不再是静默 no-op。
- [ ] `VFX_Light_fsr.jar` 产物包含 NeoForge 清单、图标、shader、`en_us`、`zh_cn`，不包含 Fabric/BBS 核心类。
- [ ] `clean build`、`check` 和 JAR 审计成功。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
