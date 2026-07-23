# Particle Facing Reference

## Sources

- Local Blockbuster source:
  `docs/temp/blockbuster/src/main/java/mchorse/blockbuster/client/particles/components/appearance/BedrockComponentAppearanceBillboard.java`
- Local Blockbuster enum:
  `docs/temp/blockbuster/src/main/java/mchorse/blockbuster/client/particles/components/appearance/CameraFacing.java`
- Local Blockbuster emitter:
  `docs/temp/blockbuster/src/main/java/mchorse/blockbuster/client/particles/emitter/BedrockEmitter.java`
- Old BBS source:
  `old/src/client/java/mchorse/bbs_mod/particles/components/appearance/ParticleComponentAppearanceBillboard.java`
- Current migrated source:
  `new/src/client/java/mchorse/bbs_mod/particles/components/appearance/ParticleComponentAppearanceBillboard.java`
- Microsoft Bedrock documentation:
  `https://learn.microsoft.com/en-us/minecraft/creator/reference/content/particlesreference/particlecomponents/minecraftparticle_appearance_billboard`

## Fixture Facts

- `docs/test1/黑洞1.json`
  - Main billboard mode: `lookat_xyz`.
  - Direction object: `mode: custom`, using `variable.particle_speed.x/y/z`.
  - Reported defect: outside the editor, the effect changes position when the
    camera moves.
- `docs/test2/护盾5-0-stay.json`
  - Main billboard mode: `rotate_xyz`.
  - Local space: `position: true`, `rotation: true`.
  - Reported defect: not displayed correctly in the particle editor.

## Blockbuster Behavior

Blockbuster stores facing metadata in `CameraFacing`:

- `LOOKAT_XYZ`, `LOOKAT_Y`, and `LOOKAT_DIRECTION` are look-at modes.
- `LOOKAT_DIRECTION`, `DIRECTION_X`, `DIRECTION_Y`, and `DIRECTION_Z` are
  direction modes.
- `ROTATE_XYZ` and `ROTATE_Y` are not direction modes and do not recompute
  yaw/pitch from particle position.

Blockbuster transform branches:

- `ROTATE_XYZ` and `LOOKAT_XYZ`: apply Y rotation by camera yaw, then X
  rotation by camera pitch.
- `ROTATE_Y` and `LOOKAT_Y`: apply only Y rotation by camera yaw.
- For `LOOKAT_XYZ` and `LOOKAT_Y`, camera yaw/pitch are first recomputed from
  camera position minus particle position.
- `DIRECTION_X`: yaw from direction, pitch from direction, then rotate Y by
  90 degrees.
- `DIRECTION_Y`: yaw from direction, then pitch plus 90 degrees.
- `DIRECTION_Z`: yaw from direction, then pitch.
- `LOOKAT_DIRECTION`: start from the direction yaw/pitch plus 90-degree pitch,
  then project the camera vector onto the plane normal to the direction vector
  and rotate around local Y so the particle faces the camera under that
  direction constraint.

Blockbuster direction vector fallback:

- Custom direction mode evaluates the three Molang expressions.
- Velocity-derived mode uses normalized particle speed only above the speed
  threshold.
- If the direction vector is too small, Blockbuster falls back to `(1, 0, 0)`.
- Non-unit direction vectors are normalized.

## Current `new` Divergence

- `new` uses `emitter.cameraRotation` for `ROTATE_XYZ`.
- `new` derives `ROTATE_Y` yaw by transforming a forward vector through
  `emitter.cameraRotation`.
- `new` implements `DIRECTION_X/Y/Z` with branch-specific yaw/pitch formulas
  that do not match Blockbuster's shared `getYaw()` / `getPitch()` model.
- `new` has a `LOOKAT_DIRECTION` enum and UI controls, but the render math does
  not match Blockbuster's constrained camera projection.

## Migration Notes

- Old BBS used the same simplified yaw/pitch branch as Blockbuster for
  `rotate_*` and `lookat_*`.
- NeoForge migration changed rendering APIs from fixed OpenGL/buffer
  translation to `PoseStack` and MojMap vertex builders. This means the fix
  must preserve the current camera-relative rendering contract while restoring
  Blockbuster orientation math.
