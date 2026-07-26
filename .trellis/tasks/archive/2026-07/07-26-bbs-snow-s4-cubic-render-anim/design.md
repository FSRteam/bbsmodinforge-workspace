# S4 design: Cubic render and animation

## 1. Delivery boundary

```text
bbs-snow-addon/src/client/java/mchorse/bbs_snow/
├── cubic/animation/
│   ├── AdditiveAnimator.java
│   └── AdditiveProceduralAnimator.java
├── utils/
│   ├── CubicPivotTransformations.java
│   └── MolangVariableScopes.java
├── mixin/client/
│   ├── AnimatorMixin.java
│   ├── ProceduralAnimatorMixin.java
│   ├── CubicModelAnimatorMixin.java
│   ├── GeoAnimationParserMixin.java
│   ├── CubicCubeRendererMixin.java
│   ├── CubicAxisRendererMixin.java
│   ├── CubicMatrixRendererMixin.java
│   └── ModelFormRendererAnimationMixin.java
└── verification/BBSSnowRuntimeVerification.java
```

Helpers stay outside `mchorse.bbs_snow.mixin.client`, because the configured Mixin package may contain
Mixins only. No new resources or registrations are needed.

## 2. Animation data flow

```text
S2 ActionTimelineEvaluator / ModelForm values
    -> primary ModelFormRenderer animator
    -> exact applyActions redirect
         -> primary animator (normal BBS behavior)
         -> cached additive overlay animator(s)
              -> timeline transition actions
              -> base/action slots without resetting untouched bones
    -> Model.applyPose(composed ModelForm pose)
```

The overlay cache key is the model identity, primary procedural mode, overlay count, and copied
`ActionsConfig` values. A changed entry replaces only its cached animator because calling
`Animator.setup(..., true)` can retain an `active` playback removed by the new config.

Preview-pose reuse deliberately bypasses both primary and overlay application. The snapshot was
captured after the composed pose and is already the authoritative pose for that frame.

## 3. Molang scope

`MolangVariableScopes` stores a weak per-`IModelInstance` map. At action entry it snapshots global
`variable.*` values and installs the model's values; at return it saves the updated model values and
restores the global snapshot. `query.*` variables are untouched. `molangShared=true` bypasses this
behavior.

Normal `Animator` and `ProceduralAnimator` methods receive HEAD/RETURN hooks. Additive subclasses
override `applyActions`, so `ModelFormRendererAnimationMixin` explicitly scopes overlay application
around the overlay loop.

## 4. Pose composition and Cubic matrices

`ModelFormRenderer.applyPose` remains the single composition point for base pose, primary overlay,
and additional overlays. S4's TAIL hook applies the three extension fields after BBS handles ordinary
translation/rotation/scale:

- non-null bone texture replaces the target texture;
- tint with positive alpha replaces target tint and whiten accumulates with `[0,1]` clamp;
- pivot lerps for fixed overlays and adds for additive overlays.

After `Model.applyPose` copies the pivot onto `ModelGroup.current`, the cube/VAO, axis, and matrix
render paths consume it with the target-specific transformation order documented in research.

## 5. Parser and angle extensions

- `CubicModelAnimatorMixin` redirects only the existing `Lerps.lerpYaw(DDD)D` calls. For `t >= 1`,
  returning `b` preserves multi-turn Euler data used by animation-to-pose conversion. For `t < 1`,
  it delegates unchanged.
- `GeoAnimationParserMixin` runs at `parseChannel` TAIL. It scans timestamp objects with numeric
  `pre`, skips `lerp_mode`, finds the prior keyframe, and switches it to `CONST` only when values
  agree within `0.001`.

## 6. Compatibility and rollback

- All injections remain required; method drift is a hard startup failure.
- `CubicVAORendererPBRMixin` and `ModelFormRendererPBRMixin` remain untouched and independently own
  S3 state stacks.
- `new/` and the source snapshot stay read-only. Rollback is limited to the new S4 addon files plus
  the client Mixin config and verifier additions.
- If a strict runtime path exposes new target drift, correct the descriptor/body against current FSR;
  do not lower `require` or copy BBS classes into the addon.
