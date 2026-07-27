# S5 design: Particle extensions

## 1. Delivery boundary

```text
bbs-snow-addon/src/client/java/mchorse/bbs_snow/
|-- particles/
|   |-- ParticleMorphTransformations.java
|   `-- components/ParticleComponentParticleMorph.java
|-- ui/particles/
|   |-- ParticlePlusUIKeys.java
|   `-- UIParticleSchemeMorphSection.java
|-- mixin/client/
|   |-- ParticleParserMixin.java
|   |-- ParticlePlusParticleMixin.java
|   |-- ParticleEmitterMixin.java
|   |-- ParticleComponentAppearanceBillboardMixin.java
|   |-- UIParticleSchemePanelMixin.java
|   `-- UIDataPathListParticleMixin.java
`-- verification/BBSSnowRuntimeVerification.java
```

The existing S2 `ParticlePlusParticle` interface remains the duck contract. Resources add only the
nine morph/browser translation keys; FSR supplies additive and collision strings/icons.

## 2. Ownership and deduplication

```text
FSR-owned
  ParticleMaterial.ADD + render batching + material icon
  Particle.intersected + collision update/events
  collision appearance/tinting models, rendering, parser entries, and editor

addon-owned
  blockbuster:particle_morph model + parser registration
  per-particle StubEntity and form rendering
  base-billboard suppression for morph-only output
  morph editor section
  particle browser favorites/recent/global search
```

The source `ParticlePlusClient` Unsafe enum mutation is deleted rather than translated. FSR already
has an `ADD` enum constant, so repeating that mutation on Java 21 would be both redundant and unsafe.

## 3. Parser contract

FSR normalizes JSON keys by stripping `minecraft:` and `blockbuster:` before lookup, then consults
its `blockbusterKeys` set while serializing. `ParticleParserMixin` runs at constructor return and:

1. maps normalized id `particle_morph` to `ParticleComponentParticleMorph`;
2. adds `particle_morph` to the Blockbuster-key set.

This makes parse and write symmetric and avoids the source plugin's now-stale registration of the
full `blockbuster:particle_morph` string.

## 4. Morph render data flow

```text
ParticleEmitter.render HEAD
  -> find ParticleComponentParticleMorph and base billboard appearance
  -> for each live particle
       set emitter/particle MoLang variables
       resolve interpolated absolute particle position
       update the particle's cached StubEntity
       set FormRenderingContext camera/light/simulation contract
       apply position -> facing -> spin -> scale on PoseStack
       FormUtilsClient.render(component.form, context)
  -> normal FSR particle batch continues
       ParticleComponentAppearanceBillboardMixin cancels only exact base billboard
       when morph is enabled and renderTexture is false
       FSR collision batch remains unchanged
```

`ParticleMorphTransformations` owns the target-version facing math outside the Mixin package. It
mirrors current `ParticleComponentAppearanceBillboard` semantics for all `CameraFacing` values and
is directly testable by the runtime verifier.

World morphs use a stable per-particle dummy as `simulationOwner`, an absolute semantic position,
and the camera-relative render stack. GUI/editor morphs use local simulation so physics, IK, and
the translucent world queue cannot inherit a live client world accidentally.

## 5. Editor and browser adaptation

`UIParticleSchemePanelMixin` reads the target's `panelById["appearance"]` at constructor return,
casts it to `UIParticleTabPage`, and adds one `UIParticleSchemeMorphSection` to both the tab and the
panel-wide section list. It does not restore the obsolete `appearanceView` field or duplicate FSR's
collision section.

`UIDataPathListParticleMixin` keeps the source behavior but replaces FabricLoader with
`FMLPaths.CONFIGDIR`. Every override first checks `fileIcon == Icons.PARTICLE`; other lists delegate
to FSR unchanged. Existing file names `bbs_particle_addon_favorites.txt` and
`bbs_particle_addon_recent.txt` are retained for user-data compatibility.

## 6. Verification and rollback

- Required Mixins and explicit target descriptors remain the startup signature gate.
- Deterministic runtime checks cover parser round-trip, state ducking, visibility decisions, and
  representative facing matrices without requiring a world.
- The client run additionally forces all S5 target classes to load and records the S5 Mixin count.
- Manual editor/world rendering remains an explicit acceptance item because a headless assertion
  cannot prove GL state, camera parity, or form-picker usability.
- Rollback removes only the files above, translation additions, Mixin config entries, and verifier
  additions. No BBS FSR source change is involved.
