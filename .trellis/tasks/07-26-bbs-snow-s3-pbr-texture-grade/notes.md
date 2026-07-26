# S3 execution record

## Baseline and scope

- S2 was archived and the addon baseline contained `1d73747`.
- S3 edited only `bbs-snow-addon/` plus its Trellis task/spec records. It did not edit,
  clean, or restore `new/` or the Fabric source snapshot.
- The final BBS core jar was newer than every file under `new/src`; strict
  `verifyBbsCoreJar` passed before the addon build and client run.

## Implementation

- Registered `pbr` and `bone_pbr` common factories idempotently and installed both UI
  factories during client construction.
- Ported the PBR material/bone editors, replay sheets, localized labels, texture modifier,
  nested render contexts, texture-grade shader, opaque/deferred render Mixins, screen-effect
  queue/renderer, and the Iris-only wrapper Mixin.
- Kept the current FSR model shader contract, including `PassMode`, and did not import any
  S4 animation, S7 replay utility, S8 clip, or S9 form/UI implementation.
- Fixed two editor regressions found during review: opening a PBR panel no longer writes a
  keyframe, and bone edits copy the previous value before writing so notification/undo can
  observe a new object.

## UI operation evidence

- User manual feedback: the client starts and most settings are visible; detailed control
  operation had not yet been manually exercised.
- The runtime verifier now creates real `UIKeyframes` selections and drives the visible
  `UITrackpad`/bone-list callbacks. It verifies all five material channels, all six bone
  channels, 254/255 range clamping, bone selection, emission multiplier, and that the old
  `BonePBRData` object is not mutated in place.
- The final real client run executed these checks and reached `S3 runtime verification passed`.

## Runtime and GL gate

- Initial strict retries at 05:36 and 05:41 stopped before Mixin config selection because
  NeoForge's early splash reported `glfwGetPrimaryMonitor failed`. No S3 code had loaded.
- For diagnosis, local ignored `runs/client/config/fml.toml` was temporarily changed to
  `earlyWindowControl = false`. The actual Minecraft window then initialized normally; the
  value was restored to `true` after verification.
- Final strict `runClient` exited 0 at approximately 2026-07-27 05:53. All 9 regular S3
  Mixins applied, both shaders initialized, the runtime verifier passed once, and the client
  exited through `bbs_snow.exitAfterVerification`.
- The verifier exercised separate normal/specular GL cache entries, deletion of both cached
  textures, screen color-grade draw/queue consumption, and restoration of framebuffer,
  viewport, program, VAO/VBO, texture, depth, blend, and cull state.
- The development machine had no usable OpenAL device; Minecraft disabled sound and continued.
  This is outside S3 and did not affect rendering verification.

## Static and artifact gate

- `gradlew build --console=plain` and `git diff --check` exited 0.
- Regular/Iris configs contain 9/1 Mixins, `defaultRequire: 1`, and `JAVA_21`.
- Source/config search found no `bbs_fslovecml`, Fabric API, refmap, `require=0`, `getGlId`,
  or `IViewRotMat` residue.
- en_us/zh_cn each contain 51 keys with identical key sets.
- The addon jar contains 117 entries and all 14 required S3 classes/configs/shaders/strings;
  it contains no `mchorse/bbs_mod/**` classes and no S8 clip classes.
- `git status --short -- new` was empty at the final gate.

## Deferred integration gate

- Iris was absent, so the no-Iris isolation path passed. Positive Iris `_s.png`/`_n.png`
  visual smoke remains an explicit S10 integration check.
