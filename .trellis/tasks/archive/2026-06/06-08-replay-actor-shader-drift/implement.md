# Implement Plan: Replay Actor Shader Drift

## Ordered Checklist

1. Load package specs before editing with `trellis-before-dev` for `new`.
2. Confirm the active baseline:
   - `new` working tree is dirty and contains many render/matrix changes.
   - `82714440` is the known previous fix.
   - only `ebe75984` is a relevant committed change after `82714440`.
3. Reproduce or instrument the drift:
   - create or use a replay with actor mode disabled;
   - enable Iris/shaders;
   - move/orbit the camera while observing actor world position;
   - log or inspect actor entity world coordinates versus rendered matrix translation if visual reproduction is not practical.
4. Separate data drift from render drift:
   - verify replay keyframe/entity coordinates remain stable while the camera moves;
   - if coordinates move with the camera, inspect `BaseFilmController.update()`;
   - if coordinates are stable but visuals drift, continue in BBS world render matrix code.
5. Identify the regression source:
   - compare current working tree against `82714440` for form/model/VAO renderer changes;
   - if needed, temporarily test clean `82714440`, current `HEAD`, and current dirty tree to distinguish committed versus local regression;
   - do not discard local changes unless the user explicitly approves.
6. Fix the smallest responsible boundary:
   - keep actor rendering unchanged because user clarified it is the normal path;
   - keep BBS replay/world rendering camera-relative;
   - pass the current render callback's world matrix into `BBSRendering.onRenderChunkLayer()` instead of reading a possibly stale global `RenderSystem` matrix;
   - correct a specific stale/reused matrix in current uncommitted renderer optimization only if instrumentation shows accumulation or reuse.
7. Verify all affected paths:
   - non-actor replay preview with shaders enabled;
   - non-actor replay preview with shaders disabled;
   - actor replay with shaders enabled remains normal;
   - disabled replay tracks;
   - at least one model form and one billboard/particle form if available.
8. Run build validation for `new`.
9. If the fix establishes a reusable render-space rule, capture it in `.trellis/spec/new/backend/quality-guidelines.md` or a more appropriate spec file.
10. Add a short migration note in the task or spec explaining that this was a NeoForge/Iris render-space normalization issue, so future renderer migration should preserve explicit context contracts.

## Validation Commands

Run from `D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\new`:

```powershell
.\gradlew.bat compileJava compileClientJava
```

If compile tasks differ in this NeoForge setup, run the closest existing Gradle build task and record the actual command/result.

## Risky Files

- `src/client/java/mchorse/bbs_mod/client/renderer/entity/ActorEntityRenderer.java`
- `src/client/java/mchorse/bbs_mod/client/renderer/MorphRenderer.java`
- `src/client/java/mchorse/bbs_mod/forms/renderers/FormRenderingContext.java`
- `src/client/java/mchorse/bbs_mod/forms/renderers/ModelFormRenderer.java`
- `src/client/java/mchorse/bbs_mod/forms/renderers/ParticleFormRenderer.java`
- `src/client/java/mchorse/bbs_mod/cubic/render/vao/ModelVAORenderer.java`
- `src/client/java/mchorse/bbs_mod/client/BBSRendering.java`
- `src/client/java/mchorse/bbs_mod/film/BaseFilmController.java`

## Rollback Points

- If data coordinates are stable, avoid changing replay keyframe application in `BaseFilmController`.
- If non-actor replay preview is stable, avoid changing `BaseFilmController.renderEntity()` camera-relative math.
- If only current dirty tree reproduces, isolate and patch the dirty render change rather than reverting all local changes.
- If clean `HEAD` reproduces but `82714440` does not, inspect `ebe75984` first.
- If correcting the chunk-layer matrix source fixes non-actor mode, avoid broader form-context migration in this task.

## Pre-Start Review Notes

Planning currently points to a shader render-space mismatch, not a replay data bug. The desired solution is a small normalization of form render-space contracts, not another actor-only workaround. Implementation should start only after the user approves moving from planning to code changes.

## Implementation Notes

- Reverted the explicit `FormRenderingContext.RenderSpace` normalization attempt after runtime testing showed it broke previously working replay rendering and did not fix the reported drift.
- User clarified the failing path is actor mode disabled; actor mode is normal.
- Reverted the narrow actor-entry marker attempt and left `ActorEntityRenderer` behavior unchanged.
- Updated `WorldRendererMixin` so Iris chunk-layer BBS rendering uses the `renderSectionLayer(...)` matrix argument instead of `RenderSystem.getModelViewMatrix()`, which can point at a different render scope and make non-actor replay forms drift with camera movement.

## Validation Results

- Earlier `./gradlew.bat compileClientJava` and `./gradlew.bat build` passed for the reverted approach, but runtime validation failed with shaders and actor mode enabled.
- Current narrow matrix-source fix:
  - `./gradlew.bat compileClientJava` from `new/`: passed.
  - `./gradlew.bat build` from `new/`: passed.
  - In-game visual check still needed: Iris/shaders enabled, actor option disabled, move/orbit camera and confirm the replay form no longer drifts.
