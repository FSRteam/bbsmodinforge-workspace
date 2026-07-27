# S5 Particle extensions

## Goal

Port the remaining `bbs_snow` particle extensions to the standalone NeoForge 1.21.1 addon without
duplicating particle behavior that BBS FSR already owns. Existing Blockbuster particle JSON must
retain its 3D morph component, particle-browser favorites/recent entries, and the corresponding
editor controls.

## Confirmed facts

- BBS FSR already owns `ParticleMaterial.ADD`, additive batching and UI, collision state,
  collision appearance/tinting components, collision rendering, and the combined collision editor.
- The source plugin's Unsafe enum injection, duplicate collision component classes,
  `ParticleComponentMotionCollisionMixin`, additive material UI Mixin, and collision UI sections
  must not be ported.
- BBS FSR does not contain the source `blockbuster:particle_morph` component, its 3D form render
  path, or the particle browser's favorites/recent/search behavior.
- S2 already ported the `ParticlePlusParticle` duck interface. Its S5 implementation can delegate
  collision state to FSR's public `Particle.intersected` field and add only the per-particle dummy
  entity needed by morph rendering.
- The FSR particle editor now uses `UIParticleTabPage` and a private `panelById` map instead of the
  source plugin's `appearanceView` field.
- `ParticleParser` strips the `blockbuster:` prefix before component lookup and maintains a private
  set for writing Blockbuster-prefixed keys. Morph registration must update both contracts.

## Requirements

### R1 Morph component and JSON compatibility

- R1.1 Port `ParticleComponentParticleMorph` with `enabled`, `render_texture`, `billboard`, MoLang
  `scale`, and nested BBS `Form` serialization.
- R1.2 Parse and write the component as exactly `blockbuster:particle_morph`; do not emit an invalid
  double namespace or silently rewrite it to `minecraft:particle_morph`.
- R1.3 Component registration must be idempotent and occur before particle schemes are parsed.

### R2 Particle morph rendering

- R2.1 Render one configured BBS form for every live particle, using interpolated particle position,
  particle age, world/light context, emitter rotation, particle rotation, and MoLang scale.
- R2.2 Match FSR's current camera-facing modes, including look-at, direction, custom direction,
  emitter-transform planes, GUI perspective, and texture-scale behavior.
- R2.3 When morph rendering is enabled and `renderTexture=false`, suppress only the ordinary base
  billboard. Collision appearance remains independently owned by FSR.
- R2.4 Reuse one `StubEntity` per particle, update its level/age/position, and provide stable
  simulation ownership. World rendering uses semantic world position without camera transforms;
  particle-editor preview uses local simulation and must not enter the world translucent queue.

### R3 Particle editor and browser

- R3.1 Add one morph section to the current appearance tab with enable, form picker, scale,
  billboard, and render-texture controls.
- R3.2 Particle-only data lists provide global search, Favorites and Recent virtual folders,
  context-menu add/remove, deterministic natural sorting, and persistent UTF-8 config files.
- R3.3 Non-particle `UIDataPathList` users retain their original filtering, selection, rendering,
  and sorting behavior.
- R3.4 Add English and Simplified Chinese strings under the `bbs_snow` namespace.

### R4 Strict addon compatibility

- R4.1 Keep all injections required and bind current FSR descriptors; do not add refmaps or
  `require=0`.
- R4.2 Keep helpers and components outside the configured Mixin package.
- R4.3 Do not modify or bundle BBS FSR classes.

## Acceptance criteria

- [x] AC1 `verifyBbsCoreJar` and `gradlew build --console=plain` finish with exit code 0.
- [x] AC2 All S5 Mixins apply with no injection/class-load error; runtime verification records the
  S5 pass and the exact S5 Mixin count.
- [x] AC3 A `blockbuster:particle_morph` sample round-trips with its flags, scale, and form data.
- [x] AC4 Runtime verification covers per-particle dummy identity/current-emitter world ownership,
  core collision delegation, morph visibility rules, and representative camera-facing matrices.
- [ ] AC5 The particle editor exposes the morph section; a configured model-form particle is visible
  in editor preview and world rendering, while `renderTexture` controls the ordinary billboard.
- [ ] AC6 Particle search, Favorites, and Recent work without affecting non-particle data lists.
- [x] AC7 The packaged jar contains all S5 classes/resources, no `mchorse/bbs_mod/**`, Fabric,
  old namespace, refmap, or unexpected optional injection.
- [x] AC8 `new/` and `bbs_FSloveCML_1.21.1/` source trees remain untouched by S5.

AC5 is automated through section construction and direct enable/scale/billboard/render-texture
control mutation; form-picker/save-reopen and editor/world visual output remain S10 manual checks.
AC6 is automated for global search, natural sorting, Favorites/Recent virtual-folder navigation,
and ordinary-list isolation; context-menu mutation and persistence remain S10 manual checks.

## Out of scope

- Reimplementing FSR's additive material, collision runtime/components, or collision editor.
- General particle editor redesign, event/collision fixes, or new Snowstorm features.
- S6 mini-window infrastructure and S7 replay particle settings behavior.
- Whole-suite visual acceptance, which remains part of S10.

## Open questions

None. The parent task requires source-compatible behavior, and current FSR code determines the
deduplication and signature choices.
