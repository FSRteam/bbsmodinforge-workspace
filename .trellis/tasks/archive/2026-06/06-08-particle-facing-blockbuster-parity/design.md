# Design

## Scope

Implementation is limited to the `new` project, primarily the particle
billboard appearance component, facing enum metadata, and any narrow render
path adjustment required to keep camera-relative particle positions stable.

## Architecture and Boundaries

Particle facing behavior is owned by:

- `new/src/client/java/mchorse/bbs_mod/particles/components/appearance/CameraFacing.java`
- `new/src/client/java/mchorse/bbs_mod/particles/components/appearance/ParticleComponentAppearanceBillboard.java`

World and editor render space behavior is owned by:

- `new/src/client/java/mchorse/bbs_mod/forms/renderers/ParticleFormRenderer.java`
- `new/src/client/java/mchorse/bbs_mod/ui/particles/UIParticleSchemeRenderer.java`
- `new/src/client/java/mchorse/bbs_mod/particles/emitter/ParticleEmitter.java`
- `new/src/client/java/mchorse/bbs_mod/particles/emitter/Particle.java`

The UI already enumerates `CameraFacing.values()` and exposes direction fields
when needed, so no new panel or control system is planned.

## Facing Contract

`CameraFacing` should carry the same semantic flags as Blockbuster:

- `isLookAt`: true for `lookat_xyz`, `lookat_y`, and `lookat_direction`.
- `isDirection`: true for `lookat_direction`, `direction_x`, `direction_y`,
  and `direction_z`.

These flags avoid duplicated conditional lists in render and UI code.

## Billboard Data Flow

1. Calculate billboard size and UVs.
2. Interpolate particle position and spin.
3. If local position and local rotation are enabled, transform particle local
   position by emitter rotation and add `emitter.lastGlobal`.
4. Resolve camera yaw/pitch from `emitter.cYaw` / `emitter.cPitch`.
5. For non-direction look-at modes, recompute yaw/pitch from camera position
   minus particle position.
6. For direction modes, resolve and normalize direction from custom Molang or
   particle speed.
7. Subtract camera position from particle position exactly once before writing
   vertices into the current camera-space pose.
8. Build the transform using Blockbuster branch order.
9. Apply particle spin around Z, scale, translation, and write vertices.

## Direction Math

Port Blockbuster helpers into JOML style:

```text
getYaw(direction):
  yaw = atan2(-direction.x, direction.z)
  normalize degrees to [-180, 180]
  return -yaw

getPitch(direction):
  pitch = atan2(direction.y, sqrt(direction.x^2 + direction.z^2))
  return -pitchDegrees
```

Branch transforms:

- `direction_x`: rotate Y(yaw), rotate X(pitch), rotate Y(90).
- `direction_y`: rotate Y(yaw), rotate X(pitch + 90).
- `direction_z`: rotate Y(yaw), rotate X(pitch).
- `lookat_direction`: rotate Y(yaw), rotate X(pitch + 90), then project the
  camera vector onto the plane normal to the direction and rotate around local
  Y by the signed angle to the current normal.

## Camera-Space Compatibility

The current NeoForge render path uses camera-space vertices and `PoseStack`
view matrices instead of Blockbuster's fixed OpenGL `BufferBuilder` translation.
The implementation must not introduce a second camera subtraction or switch
particle global positions into the wrong space.

`test1` is the guard for this: if camera movement changes the world location of
the effect, the camera-space contract is still wrong.

## Compatibility

- Keep all existing enum IDs unchanged.
- `fromString()` continues to default unknown modes to `ROTATE_XYZ`.
- Direction fields remain optional. Direction modes with missing or zero
  direction fall back to `(1, 0, 0)`, matching Blockbuster.
- Collision appearance reuses the same base class behavior and must inherit the
  same facing fixes.

## Rollback

Rollback is local to the touched `new` files. Because the working tree already
contains unrelated uncommitted changes, rollback should be by reviewing and
reversing only the hunks introduced by this task, not by resetting the repo.
