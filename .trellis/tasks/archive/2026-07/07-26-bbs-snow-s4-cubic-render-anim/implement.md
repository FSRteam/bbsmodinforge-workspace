# S4 implementation plan

## Implementation

- [x] Port `AdditiveAnimator`, `AdditiveProceduralAnimator`, `MolangVariableScopes`, and
  `CubicPivotTransformations` with Mojmap/PoseStack and `bbs_snow$` naming.
- [x] Add strict Animator, ProceduralAnimator, CubicModelAnimator, and GeoAnimationParser Mixins.
- [x] Add cube and axis transformation overrides preserving FSR `offsetGroup`; add the matrix
  overwrite preserving FSR origin capture and render-only-offset exclusion.
- [x] Split the source `ModelFormRendererMixin` into `ModelFormRendererAnimationMixin`: retain pose
  extras and overlay animator lifecycle, remove S3 PBR code, and bind the three current action sites
  by exact descriptors.
- [x] Register exactly the eight S4 Mixins in `bbs_snow.client.mixins.json`; do not duplicate the S3
  VAO/PBR Mixins.
- [x] Extend `BBSSnowRuntimeVerification` with S4 target-load and deterministic behavior checks.

## Validation

- [x] Run `./gradlew verifyBbsCoreJar --console=plain` from `bbs-snow-addon` and confirm exit 0.
- [x] Run `./gradlew build --console=plain` without a pipe and confirm exit 0.
- [x] Inspect the jar for every S4 class/config entry and confirm no `mchorse/bbs_mod/**` entries.
- [x] Search addon sources for Fabric imports, old `bbs_fslovecml` namespace, refmap, and unexpected
  `require = 0`.
- [x] Run strict `runClient`; if it fails before Mixin selection with the known primary-monitor error,
  retry with only local `earlyWindowControl=false` and restore it afterward.
- [x] Require all eight S4 Mixins, the S4 runtime behavior assertions, dashboard reachability, and
  automatic exit code 0 in the complete log.
- [x] Run `git diff --check` and confirm `git diff --stat -- new bbs_FSloveCML_1.21.1` is empty.

## Validation evidence

- `runClient` exited 0 with `bbs_snow.runtimeVerification=true` and
  `bbs_snow.exitAfterVerification=true`.
- The client prepared all 76 Mixins and logged
  `S4 runtime verification passed: ... s4Mixins=8`.
- The packaged addon jar contains 131 entries, including all 12 S4 implementation classes, and no
  `mchorse/bbs_mod/**` entries.
- Source scans found no Fabric imports, old namespace, refmap, or `require = 0`; `git diff --check`
  passed. The temporary `earlyWindowControl=false` workaround was restored to `true`.

## Risk and rollback points

- `ModelFormRendererAnimationMixin` is the highest-risk file because the target was recently changed
  by preview-pose, IK, and physics work. Its exact descriptors are the first runtime gate.
- `CubicMatrixRendererMixin` uses `@Overwrite` because origin capture leaves no stable injection seam.
  Keep its body byte-for-byte equivalent to current FSR except for the pivot pair.
- Runtime verifier edits are additive; a verification-only failure must not be hidden by deleting an
  assertion. Fix implementation or update an assertion only when target semantics prove it stale.
- Stage only S4 task files and `bbs-snow-addon` paths. Do not stage unrelated Trellis spec changes.
