# Particle Camera-Facing Parity

## Goal

Particles using camera-facing modes should look the same in the 3D particle editor preview and outside the particle editor.

## Requirements

- Treat the particle editor 3D preview as the target visual behavior.
- Align camera yaw/pitch/position semantics between BBS `Camera` and Minecraft `Camera` setup paths.
- Verify common facing modes: `lookat_xyz`, `lookat_y`, `lookat_direction`, `rotate_xyz`, `rotate_y`, and fixed direction modes.
- Do not change JSON field names or saved particle format.

## Acceptance Criteria

- [ ] Representative camera-facing particles match between editor preview and outside-editor rendering.
- [ ] `lookat_xyz` and `lookat_y` no longer appear rotated/flipped between contexts.
- [ ] Direction-based modes remain stable and do not regress.
- [ ] Existing particle preview rendering still works in the editor.
