# S4 Cubic render and animation

## Goal

Port the `bbs_snow` Cubic rendering and animation runtime to the standalone NeoForge 1.21.1
addon. Bone pivots, additive action overlays, procedural action overlays, Molang variable isolation,
unwrapped full-blend rotations, and Blockbench step keyframes must behave like the source plugin
without changing BBS FSR itself.

## Confirmed facts

- S1-S3 are archived and the addon starts successfully. The user confirmed that most settings are
  visible; detailed interaction coverage is still incomplete.
- S2 already owns the required common data contracts: `PivotHolder`, `BoneTextureHolder`,
  `TextureGradeHolder`, `MolangSharedProvider`, `ActionsOverlayProvider`, `ActionTimelineConfig`,
  and timeline evaluation.
- S3 already owns all PBR/texture-grade behavior from the source `CubicVAORendererMixin` and the
  PBR HEAD/RETURN portion of `ModelFormRendererMixin`.
- BBS FSR 1.21.1 has three, not four, `ModelFormRenderer` action application sites:
  private `renderInUI`, `render3D`, and `collectMatricesWithAppliedStates`. The old
  `sampleBoneOrigin` application site no longer exists.

## Requirements

### R1 Additive animation runtime

- R1.1 Port `AdditiveAnimator` and `AdditiveProceduralAnimator` with `bbs_snow$` names and the S2
  action-timeline contract.
- R1.2 Action overlays must apply after the primary animator without resetting untouched bones.
- R1.3 Overlay animators must be rebuilt when model, procedural mode, count, or action config changes;
  they must update on tick and clear on `resetAnimator`.
- R1.4 Timeline weight is clamped to `[0, 1]`, transition actions are applied once, and normal BBS
  action slots keep their existing fade and priority behavior.

### R2 Molang isolation

- R2.1 Port the per-model `variable.*` scope around normal and procedural action application.
- R2.2 A `ModelForm` with `molangShared=true` retains BBS's global shared behavior; the default false
  isolates variables by model instance.
- R2.3 Scope state must be restored after application and must not leak into later models.

### R3 Cubic pivot rendering

- R3.1 Port the pose-pivot transform helper from Yarn `MatrixStack` to Mojmap `PoseStack`.
- R3.2 `CubicCubeRenderer`, its `CubicVAORenderer` subclass, and `CubicAxisRenderer` apply
  `+pivot` before rotate/scale and `-pivot` afterward while preserving FSR's leading bone offset.
- R3.3 `CubicMatrixRenderer` records its origin at the same point as FSR and applies the same pivot
  pair, keeping rendered geometry, gizmos, attachments, and collected matrices aligned.

### R4 Animation parser and rotation compatibility

- R4.1 At full blend, `CubicModelAnimator` preserves the source Euler angle instead of normalizing it
  through `lerpYaw`; partial blends keep the original shortest-path interpolation.
- R4.2 `GeoAnimationParser` recognizes Blockbench's `{pre, post}` step encoding and changes only the
  preceding keyframe to constant interpolation when `pre` matches that keyframe.
- R4.3 Catmull-Rom (`lerp_mode`) and nonnumeric expressions are not reclassified as steps.

### R5 ModelForm composition

- R5.1 The S4 `ModelFormRenderer` Mixin propagates overlay bone texture, texture grade, and pivot data
  in `applyPose` after BBS combines the ordinary transform fields.
- R5.2 It wraps exactly the three current action application sites and keeps the preview-pose reuse
  path intact.
- R5.3 It contains no PBR context ownership already delivered by S3 and no editor/UI logic owned by
  S7.

## Acceptance criteria

- [x] AC1 `verifyBbsCoreJar` and `gradlew build --console=plain` finish with real exit code 0.
- [x] AC2 Eight S4 client Mixins apply under `defaultRequire: 1`; the client log contains no Mixin
  injection/class-load error.
- [x] AC3 Runtime verification covers Cubic pivot matrices, full-blend angle preservation, Geo step
  parsing, Molang scope isolation/restoration, and additive animator behavior.
- [x] AC4 The three `ModelFormRenderer` action redirects bind to their exact 1.21.1 descriptors;
  overlay animators update and reset without changing the primary animator.
- [x] AC5 The addon reaches the BBS dashboard and exits through the strict runtime verifier; the log
  records the S4 verification pass.
- [x] AC6 The packaged jar contains the S4 classes and updated client Mixin config, contains no
  `mchorse/bbs_mod/**`, and does not introduce Fabric/refmap/optional-injection drift.
- [x] AC7 `new/` and `bbs_FSloveCML_1.21.1/` remain unmodified. Existing unrelated Trellis changes
  remain untouched.

## Out of scope

- PBR context, texture grading, per-bone texture resolution, and Iris behavior already owned by S3.
- Actions editor, replay timeline, keyframe sheet, pose editor, and other S7 UI behavior.
- Particle, mini-window, clip, fluid, Premiere, and whole-suite visual acceptance work from S5-S10.
- New animation features not present in the source plugin.

## Open questions

None. The parent task requires behavior-equivalent migration, and the remaining implementation
choices are determined by the current source and BBS FSR signatures.
