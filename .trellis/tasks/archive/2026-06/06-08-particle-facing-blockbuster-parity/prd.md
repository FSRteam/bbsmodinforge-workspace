# Particle facing Blockbuster parity

## Goal

Restore migrated BBS particle billboard behavior in `new` so camera-facing,
look-at, and direction-facing modes match Blockbuster/Snowstorm behavior while
preserving the provided `docs/test1` and `docs/test2` effects.

## User Value

Effects authored for Blockbuster and the old Fabric BBS project should render
the same after the NeoForge 1.21.1 migration. Camera motion must not change a
particle effect's world position, and the particle editor preview must remain a
reliable preview of in-world rendering.

## Confirmed Facts

- The task is limited to the migrated `new` project. Local commit is only
  allowed after explicit user confirmation, and only `new` content may be
  committed.
- `docs/test1/黑洞1.json` uses `minecraft:particle_appearance_billboard` with
  `facing_camera_mode: "lookat_xyz"` plus a custom `direction` vector from
  `variable.particle_speed`.
- `docs/test2/护盾5-0-stay.json` uses `facing_camera_mode: "rotate_xyz"` and
  `minecraft:emitter_local_space` with `position: true` and `rotation: true`.
- Local reference sources exist under `old`, `new`, `docs/test1`,
  `docs/test2`, `docs/plan`, `docs/temp/blockbuster`, and
  `docs/temp/snowstorm`.
- Blockbuster's `BedrockComponentAppearanceBillboard` treats `rotate_xyz` and
  `lookat_xyz` as the same transform branch after choosing camera yaw/pitch,
  and treats `rotate_y` and `lookat_y` as the same yaw-only transform branch.
  The difference is that look-at modes recompute yaw/pitch from particle
  position to camera position.
- The current `new` implementation diverges from Blockbuster by using
  `emitter.cameraRotation` for `rotate_xyz` / `rotate_y` and by using custom
  direction-axis math for `direction_x/y/z`.
- `new` already exposes UI labels and direction controls for
  `lookat_direction`, `direction_x/y/z`, and `emitter_transform_*` modes.

## Requirements

- Match Blockbuster behavior for `rotate_xyz`, `rotate_y`, `lookat_xyz`,
  `lookat_y`, `direction_x`, `direction_y`, `direction_z`, and
  `lookat_direction`.
- Keep `lookat_y` yaw-only. It must not visually collapse to `lookat_xyz`.
- `rotate_xyz` and `rotate_y` must orient to the camera like Blockbuster
  rather than using a NeoForge-specific matrix branch that changes the effect.
- `direction_x`, `direction_y`, and `direction_z` must use the normalized
  custom or velocity-derived direction vector with Blockbuster's yaw/pitch
  model.
- `lookat_direction` must be implemented with the Blockbuster-style direction
  constraint plus camera-facing projection around the local axis.
- The in-world particle form rendering path must keep particle positions stable
  when the camera moves.
- The particle editor preview must display the provided `docs/test2` effect.
- Preserve JSON compatibility for existing particle files, including old files
  that do not contain a `direction` object.
- Avoid unrelated refactors, asset moves, or cleanup outside the defect scope.

## Acceptance Criteria

- [ ] `docs/test1/黑洞1.json` renders outside the particle editor without
      changing world position when the camera moves.
- [ ] `docs/test2/护盾5-0-stay.json` renders in the particle editor preview.
- [ ] `rotate_xyz` and `rotate_y` visually face the camera according to the
      Blockbuster reference behavior.
- [ ] `lookat_y` remains yaw-only and is distinguishable from `lookat_xyz`.
- [ ] `direction_x`, `direction_y`, and `direction_z` align billboards using
      the same yaw/pitch formulas as Blockbuster.
- [ ] `lookat_direction` is parsed, configurable through the existing UI, and
      renders using the Blockbuster-style constrained look-at behavior.
- [ ] Existing particle files that use `rotate_xyz`, `lookat_xyz`, or no
      `direction` field continue to load.
- [ ] Verification commands complete or any environment limitation is recorded
      in the final report.

## Out of Scope

- Reworking unrelated particle editor interactions.
- Changing particle JSON assets in `docs/test1` or `docs/test2`.
- Moving existing historical clone folders from `docs/temp` to root `temp`.
- Committing changes before the user explicitly confirms commit.

## Open Questions

- No product-intent question blocks planning. The remaining work is technical
  implementation and verification against the provided fixtures and references.
