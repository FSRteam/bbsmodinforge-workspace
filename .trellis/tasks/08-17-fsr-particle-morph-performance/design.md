# FSR Particle And Morph Replay Performance Design

## Boundaries

The implementation is split into exact hot-path optimizations and conservative culling. It does not change simulation frequency or move Minecraft world access off the client thread.

## Particle Runtime

### Stable linear compaction

`ParticleEmitter.updateParticles()` records the list size at the start of the update, updates that original range, writes survivors toward the front, then appends particles spawned during callbacks behind those survivors. One tail clear removes obsolete entries. This preserves stable order and avoids repeated `ArrayList` shifts.

Child emitters use the same compaction shape so event-heavy effects do not retain the iterator-removal cost.

### Variable and curve evaluation

Emitter variable assignment is separated from curve evaluation:

- logic update calls assignment plus curve evaluation where emitter components need current curves;
- render sampling restores emitter variables, assigns the current particle variables, then evaluates curves once;
- particle update expressions run only on logic creation/update paths, never from render-only sampling.

The public variable-sampling entry point keeps its current update behavior for compatibility; render code uses an internal no-side-effect path.

### Collision fast path

A particle with exactly zero displacement cannot collide in the existing swept-box call. It takes an allocation-free early return while preserving collision-event rearming and drag reset behavior.

## Morph Replay Runtime

### Actor replay preparation

Actor replays are drawn by Minecraft's entity renderer. The controller-side pass exists only to capture edit Gizmo matrices. When edit axes are disabled, the controller skips that hidden pass entirely.

### Conservative non-Actor culling

One Minecraft `Frustum` is built per controller render call. A direct replay may be rejected only when all of these are true:

- it is not an Actor replay;
- it is not camera-relative;
- its Form declares an explicit hitbox;
- its anchor has no target or transition.

All other cases render as before. The declared Form hitbox is converted to a Minecraft world AABB. This avoids guessing bounds for dynamic particles, nested BodyParts, anchors and transformed models.

### Mob equipment synchronization

Each slot compares item identity/components and count with the internal entity's current stack. `setItemSlot` runs only on a real change. The source stack remains the authoritative value.

## Compatibility

- Particle order, event order, blending order and alpha order remain stable.
- Export uses the same exact simulation path as preview.
- Culling affects draw submission only; it does not pause Form updates or particle simulation.
- Explicit Form hitboxes already define the authored spatial boundary used by picking and interaction, so they are the only accepted direct-replay culling source.

## Evidence

- Real-time crowd rendering survey: <https://www.cs.upc.edu/~npelechano/abstract_prePrintCGF2015Survey.pdf>
- Key-pose caching research (kept as a future option, not used for independent IK/physics): <https://onlinelibrary.wiley.com/doi/10.1111/j.1467-8659.2010.01715.x>
- EntityCulling implementation reference: <https://github.com/tr7zw/entityculling>
- NVIDIA high-speed screen particles: <https://developer.nvidia.com/gpugems/gpugems3/part-iv-image-effects/chapter-23-high-speed-screen-particles>

## Rollback

Particle compaction, render sampling, replay culling and Mob equipment synchronization are separate edits and can be reverted independently. If visual validation finds a hitbox-culling regression, disable only the non-Actor culling predicate while retaining the exact hot-path optimizations.
