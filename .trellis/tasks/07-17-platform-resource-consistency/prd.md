# NeoForge平台与资源一致性修复

## Goal

修复 NeoForge 实体跟踪单位、资源 provider 路径边界和默认英语键集漂移。

## Requirements

- Fabric block range 转为 `(blocks+15)/16` chunks；chunk range 原样传 NeoForge builder。
- ExternalAssetsSourcePack 用规范路径 containment 判定 owner，安全处理 root 本身和同名前缀兄弟目录。
- `en_us` 补齐 `chroma_sky_color` canonical key，并检查语言键一致性。

## Acceptance Criteria

- [ ] Actor 为 16 chunks/256 blocks，gun 为 24 chunks/384 blocks。
- [ ] `assets_backup` 不被识别为 `assets` provider，root 输入不越界。
- [ ] 英语界面不显示原始 chroma sky key。
- [ ] 资源/单位测试、编译和 build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
