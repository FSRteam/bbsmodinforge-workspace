# S5 implementation plan

## Implementation

- [x] Port `ParticleComponentParticleMorph` with Mojmap buffer types and BBS form serialization.
- [x] Add `ParticleParserMixin` for normalized lookup plus Blockbuster-prefix round-trip.
- [x] Implement `ParticlePlusParticleMixin` by delegating collision state to FSR and caching only
  the per-particle `StubEntity`.
- [x] Implement target-version `ParticleMorphTransformations` and the trimmed
  `ParticleEmitterMixin`; preserve FSR render batching and simulation-space contracts.
- [x] Add exact-base billboard suppression without intercepting FSR collision appearance.
- [x] Port the morph editor section and inject it into the current appearance `UIParticleTabPage`.
- [x] Port particle-only browser search/Favorites/Recent with `FMLPaths.CONFIGDIR` persistence.
- [x] Add the nine English/Chinese morph and browser strings and register exactly six S5 Mixins.
- [x] Extend `BBSSnowRuntimeVerification` with S5 target-load and deterministic behavior checks.

## Validation

- [x] Run `./gradlew verifyBbsCoreJar --console=plain` and `./gradlew build --console=plain` with
  real exit codes.
- [x] Inspect the jar for every S5 class/resource and no `mchorse/bbs_mod/**` entries.
- [x] Scan for Fabric, old `bbs_fslovecml`, refmap, and unexpected `require = 0` drift.
- [x] Run strict client verification with both runtime properties; require the S5 pass, six S5
  Mixins, translation keys, parser round-trip, and automatic exit 0.
- [ ] S10 manual: choose a form, save/reopen, and check editor plus world rendering and
  `renderTexture` output. Section construction and direct control mutations are automated here.
- [ ] S10 manual: exercise Favorites/Recent context-menu mutation and persistence. Global search,
  natural sorting, virtual-folder navigation, and non-particle isolation are automated here.
- [x] Restore any temporary `earlyWindowControl` override, then run `git diff --check` and source
  boundary checks.

## Verification record (2026-07-27)

- `verifyBbsCoreJar build`: exit 0; 145-entry addon jar.
- Strict `runClient`: exit 0; 82 total Mixins prepared; all six S5 Mixins applied; runtime marker
  reported `targets=39 settings=20 headers=2 pbrFactories=2 shaders=2 s4Mixins=8 s5Mixins=6`.
- Jar audit: all 13 required S5 classes/resources present; no BBS core classes, Fabric metadata,
  legacy namespace, or refmap.
- Runtime browser coverage exposed and fixed a non-particle sorting regression caused by a Mixin
  `super.sortElements()` fallback bypassing the target override.
- `earlyWindowControl = true` remained unchanged. Parallel changes already present under `new/`
  and `bbs_FSloveCML_1.21.1/` were not touched by S5.

## Risk and rollback points

- `ParticleEmitterMixin` is the highest-risk path because it bridges BBS forms, camera-facing math,
  render state, semantic world space, and GUI preview. Keep its renderer helper testable and outside
  the Mixin package.
- `ParticleParserMixin` must update lookup and serialization ownership together; changing only one
  direction causes load/save data loss.
- `UIParticleSchemePanel` was redesigned around dock tabs. Inject only through its current map/page
  ownership instead of recreating source fields.
- Do not port source collision/additive code merely to match file count. FSR is authoritative for
  those features and already has broader behavior than the source plugin.
- Stage only S5 addon files and S5 Trellis artifacts; leave all parallel `new/` and root work alone.
