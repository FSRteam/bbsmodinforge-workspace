# S3 implementation plan

## 0. Baseline

- [x] Confirm S2 is archived and addon HEAD contains `1d73747`.
- [x] Record addon, source, root and `new/` dirty state; treat source/`new/` as read-only.
- [x] Run strict BBS jar freshness/API validation before compiling.

## 1. Registration and PBR UI

- [x] Add idempotent `pbr` / `bone_pbr` common factory registration.
- [x] Port the two client UI factories and register them from `BBSSnowModClient`.
- [x] Add S3-owned `PBRUIKeys`; add identical en_us/zh_cn key sets.
- [x] Port PBR replay-sheet and texture-grade label Mixins with current target descriptors.

## 2. Core render helpers

- [x] Port `PBRTextureModifier`, add explicit cache release on reload.
- [x] Replace flat context setters with nest-safe PBR/bone render-state capture/restore.
- [x] Split `ModelFormRendererPBRMixin` from the source multi-feature Mixin.
- [x] Split/port `CubicVAORendererPBRMixin`; keep only group grade, current bone and texture override.

## 3. Texture-grade shader

- [x] Implement `ModelTextureGradeShader` with 1.21.1 `ShaderInstance` APIs.
- [x] Build shader JSON/VSH/FSH from current FSR `model` shader plus tint/whiten operations.
- [x] Preserve `PassMode` and validate required uniforms at load.
- [x] Port `BBSShadersTextureGradeMixin` and `ModelInstanceTextureGradeMixin`.

## 4. Deferred transparency

- [x] Add render-state snapshot for tint/whiten + PBR/bone context.
- [x] Capture/restore it around `FormTranslucentQueue.ModelVAOCommand.draw`.
- [x] Capture/restore it around `FormTranslucentQueue.BOBJCommand.draw`.
- [x] Verify nested/queued state never leaks into a later model/group.

## 5. Screen color-grade engine

- [x] Port the effect DTO and namespaced `ClipContext` queue without any S8 clip classes.
- [x] Port `ColorGradeRenderer` framebuffer calls to `Minecraft` / `RenderTarget`.
- [x] Port `ScreenEffectRenderer` and split screen-only BBSRendering hook.
- [x] Verify empty queue is a no-op, populated queue is consumed once, and GL state is restored.

## 6. Iris isolation

- [x] Port wrapper Mixin under `mixin/client/iris/` and inject `getId()`.
- [x] Put it only in `bbs_snow.client.iris.mixins.json`; keep regular config free of Iris entries.
- [x] Verify no-Iris startup has no missing-target/class-load error.

## 7. Runtime verification

- [x] Extend the client verifier for factory/UI registration, l10n and S3 target class loading.
- [x] Verify shader readiness and `TextureTint` / `TextureWhiten` / `PassMode` uniforms.
- [x] Exercise PBR modifier output/cache cleanup with a small GL texture.
- [x] Exercise render-state snapshot/capture/restore and screen-effect queue lifecycle.

## 8. Quality gate

```powershell
.\gradlew.bat build --console=plain
git diff --check
rg -n "bbs_fslovecml|net\.fabricmc|refmap|require\s*=\s*0" src
.\gradlew.bat runClient
```

- [x] Build exits 0 and jar contains expected S3 classes/configs/shaders/strings, no BBS core classes.
- [x] All regular S3 Mixins apply; Iris config skips cleanly when Iris is absent.
- [x] Runtime verifier passes; logs contain no critical Mixin, shader, GL or missing-asset errors.
- [x] en_us/zh_cn JSON parses and key sets match.
- [x] `git diff --stat new/` is empty for S3-owned changes.
- [x] Run `trellis-check` and record actual signature drift/remaining Iris visual gate in `notes.md`.

## Rollback points

- Keep registration/UI, shader/opaque rendering, deferred state, screen engine and Iris as separable batches.
- If a split Mixin conflicts with later source-owned logic, rename/narrow the S3 Mixin; do not import the whole
  S4/S7/S8 source Mixin.
- If current BBS cannot expose a required target from an addon, stop under parent R5.3 rather than editing `new/`.
