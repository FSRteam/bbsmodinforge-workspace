# Verification

## Automated checks

- `gradlew.bat testPoseCurveLogic build --console=plain`
  - Result: `BUILD SUCCESSFUL`
  - Regression marker: `BBS PoseCurve logic tests passed`
- `gradlew.bat runClient -Pverify=1`
  - Result: client exited automatically
  - Log marker: `runtime verification passed: mixinTargets=24 resources=3`
  - No `client-started handler failed`, critical injection failure, or surviving
    PoseCurve client process was found.
- Final addon Jar audit
  - `META-INF/neoforge.mods.toml` and both declared Mixin configs are present.
  - `PoseCurveKeyframeResolver` and `MixinUIKeyframeFactorySync` are present.
  - `fabric.mod.json` and bundled `mchorse/bbs_mod/**` classes are absent.
- Source whitespace checks found no trailing-whitespace or conflict-marker errors.

## Regression coverage

The focused logic test verifies that a panel's old keyframe resolves to the rebuilt
keyframe by id and tick even when the value object changed, while rejecting a same-tick
decoy with a different id. Strict runtime verification proves the accessor and sync
Mixin targets load against the current BBSFSR Jar.

## Manual UI boundary

This session did not drive a real Film editor with a loaded actor and pose track. The
remaining smoke check is:

1. Open a pose or transform track whose target axis was not edited outside the curve
   editor.
2. Enter curve editing and change only that axis; confirm the actor updates immediately.
3. Leave and re-enter curve editing, then drag the same rotation axis; confirm one drag
   applies one delta and the actor does not continue spinning.
4. Repeat once for a normal `Transform` track and once for a `PoseTransform` track.

The automated checks cover the stale-object resolution and Mixin wiring, but not mouse
gesture delivery, actor rendering, or a user's existing Film data.

## 2026-08-20 FSR drawing regression

### Crash report evidence

- `错误报告-2026-8-19_23.53.52`: opening the pose edit-track UI failed at
  `UIPoseTransformKeyframeGraph.renderGraph`, line 583, with
  `IllegalStateException: BufferBuilder was empty`.
- `错误报告-2026-8-19_23.55.50`: with PoseCurve and BBSVFX installed, opening the
  follow-curve progress keyframes failed in the PoseCurve-injected
  `UIKeyframeGraph.bbsplus$renderAutoBezierHandles` with the same exception.
- BBSVFX reached API 2.0 READY before the second crash. The first project-owned failing
  frame in both reports belongs to PoseCurve.

### Fix verification

- All three optional PoseCurve batches now call `PoseCurveRenderUtils.drawIfNotEmpty`:
  pose/transform points, ordinary automatic handles, and vector automatic handles.
- `gradlew.bat testPoseCurveLogic build --console=plain` passed against the latest
  `new/build/libs/BBS_FSR_beta_v0.0.11.jar`.
- `gradlew.bat runClient -Pverify=1 --console=plain` passed after a transient Mojang
  metadata outage cleared. The render-thread empty-buffer probe returned no draw and the
  final log marker remained `runtime verification passed: mixinTargets=24 resources=3`.
- Exported Mixin bytecode for `UIKeyframeGraph` and `UIVector3KeyframeGraph` calls the
  guarded helper from the injected methods.
- Final source and runtime logs contain no PoseCurve `buildOrThrow()` path and no
  `BufferBuilder was empty` failure.

### Remaining environment boundary

The exact PCL modpack from the reports includes NeoForge 21.1.248, Iris, Sodium, and many
unrelated mods. It was not modified or launched from this workspace. The repaired
operation is independent of those render mods because an empty builder is finalized
before any mesh reaches their upload hooks, but the final user smoke check remains:

1. Replace only the PoseCurve Jar in the reported instance.
2. Open an empty/default pose edit track.
3. With BBSVFX enabled, open follow-curve progress keyframes containing ordinary linear
   points.
4. Confirm both screens stay open and no new `BufferBuilder was empty` report is created.
