# Implementation Plan

## Checklist

1. Load pre-development guidance with `trellis-before-dev`.
2. Re-check the current `new` diffs for the particle files before editing.
3. Update `CameraFacing` with `isLookAt` and `isDirection` metadata matching
   Blockbuster.
4. Refactor `ParticleComponentAppearanceBillboard` to use shared
   Blockbuster-style direction helpers.
5. Replace `rotate_xyz` and `rotate_y` branches with the same yaw/pitch branch
   structure used by Blockbuster.
6. Replace `direction_x/y/z` branch math with Blockbuster branch order.
7. Implement `lookat_direction` using the Blockbuster constrained projection
   algorithm.
8. Review camera-space logic in `ParticleFormRenderer`, `UIParticleSchemeRenderer`,
   `ParticleEmitter`, and `Particle` for `test1`/`test2` implications. Patch only
   if evidence shows the current contract is wrong.
9. Build or compile-check `new`.
10. Run targeted searches to confirm no stale direction-mode condition lists
    remain where enum metadata should be used.
11. Manually inspect the final diff and document any verification limitation.

## Validation Commands

- `git -C new diff -- src/client/java/mchorse/bbs_mod/particles/components/appearance/CameraFacing.java src/client/java/mchorse/bbs_mod/particles/components/appearance/ParticleComponentAppearanceBillboard.java src/client/java/mchorse/bbs_mod/forms/renderers/ParticleFormRenderer.java src/client/java/mchorse/bbs_mod/ui/particles/UIParticleSchemeRenderer.java`
- `git -C new status --short`
- `./gradlew compileJava` or the nearest available compile task in `new`
- If available and practical, run the client against `docs/test1` and
  `docs/test2` for visual verification.

## Risky Files

- `ParticleComponentAppearanceBillboard.java`: shared render logic for all
  billboard particles and collision appearance.
- `ParticleEmitter.java`: camera and local-space behavior affects spawning,
  rendering, events, and child emitters.
- `ParticleFormRenderer.java`: interacts with form transforms and world camera
  matrices.
- `UIParticleSchemeRenderer.java`: editor preview path differs from in-world
  rendering but must stay visually consistent.

## Review Gates

- Do not start implementation until the user approves proceeding from the
  planning artifacts.
- Do not commit until the user explicitly confirms commit after verification.
