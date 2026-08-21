# Mapping Baseline

## Source

- Repository: `xavineditor/BBSVFX` master archive.
- Download path: `https://gh-proxy.cn/https://github.com/xavineditor/BBSVFX/archive/refs/heads/master.zip`.
- Archive SHA-256: `ED3022E9E3590C72FF5CA2FD5A30F23234A369B8117ECC1BB024AED9D52402B7`.
- Untouched snapshot: `bbs-vfx-neoforge-1.21.1-codex-ghproxy/BBSVFX-master`.
- Task-owned mapping workspace: `bbs-vfx-neoforge-1.21.1-codex-ghproxy/BBSVFX-port-work`.

## Fabric 1.21.1 Baseline

The untouched snapshot passed `compileJava` and failed `compileClientJava` with six errors:

1. Three uses of removed `VertexFormats.POSITION_COLOR_TEXTURE`.
2. Two `VertexConsumer.quad(...)` calls missing the 1.21 alpha argument.
3. One removed `MinecraftClient.getTickDelta()` call.

The task workspace fixes only those six compatibility errors:

- Use the existing `BbsVfxRenderCompat.positionColorTexture()` fallback.
- Pass alpha `1F` to both quad calls.
- Read partial tick from `getRenderTickCounter().getTickDelta(false)`.

Validation command:

```powershell
.\gradlew.bat compileJava compileClientJava --console=plain --offline '-Pmc=1.21.1'
```

Result: `BUILD SUCCESSFUL` with all three compile tasks executed.

## Mapping Tool

Reference: <https://docs.fabricmc.net/zh_cn/develop/migrating-mappings/>

The local Fabric Loom 1.15.5 `migrateMappings` task exposes:

- `--input`: Java source directory.
- `--mappings`: target mappings coordinate.
- `--output`: remapped source output directory.
- `--overrideInputsIHaveABackup`: destructive in-place option; this migration does not use it.

Main and client source sets must be converted separately because Loom split environment source
sets do not implicitly migrate both directories.

Target mapping coordinate: `net.minecraft:mappings:1.21.1`.

## Interpretation Rules

- Loom/Ravel migration is a mechanical naming aid, not a loader port.
- Fabric events, metadata, entrypoints and callbacks remain for later manual conversion.
- Every Mixin descriptor and string target is manually checked against NeoForge/BBSFSR sources.
- Final correctness is decided by NeoForge compilation and runtime launch, not by the mapping task.
