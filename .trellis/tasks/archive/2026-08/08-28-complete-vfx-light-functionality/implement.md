# 执行计划

1. [x] 激活任务并读取规范。
2. [x] 对比并修复 fallback 光照形态、颜色/强度和 Ambient 范围。
3. [x] 补齐模型编辑器辅助线和四种灯的预览几何。
4. [x] 修复模型方块光源清理和有限值滑条控件。
5. [x] 修复 Air/Style 运行时关键帧绑定并完成构建审计。

## Final verification

- `bfx-light/gradlew.bat --no-daemon --console=plain check` passed.
- `bfx-light/gradlew.bat --no-daemon --console=plain build` passed after a clean rebuild.
- `build/libs/VFX_Light_fsr.jar` contains the NeoForge metadata, VFX icon atlas, English/Chinese
  strings, and fallback shader resources; it contains no `fabric.mod.json`, `mchorse/bbs_mod/**`, or
  `net/fabricmc/**` entries.
- Runtime visual acceptance still requires launching the FSR client and checking fixed-camera pixels;
  the current environment only provides source, resource, and build verification.
