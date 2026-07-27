# S5 source-to-target drift

## Source inventory

The source S5 surface has four particle classes, four UI classes, and seven particle-related
Mixins. Current BBS FSR has absorbed most of the former Particle+ extension.

| Source behavior | Current FSR evidence | S5 action |
|---|---|---|
| Unsafe injection of `particles_add` enum | `ParticleMaterial.ADD`, batching, parser and icon exist | Drop source code |
| Collision intersected state | `Particle.intersected` and collision update own it | Delegate duck interface |
| Collision appearance/tinting classes | Core components and parser entries exist | Drop source classes |
| Collision base-billboard replacement | `ParticleEmitter.render` owns separate collision batch | Preserve core path |
| Collision editor sections | Current appearance tab already contains combined collision UI | Drop source sections |
| `blockbuster:particle_morph` | No target component or parser key | Port and register |
| Per-particle `StubEntity` | No target storage | Port only dummy state |
| 3D form particle rendering | No target path | Port against PoseStack/Mojmap |
| Morph editor section | No target section; editor now uses dock tab pages | Port with new page injection |
| Favorites/recent/global search | No target particle-browser behavior | Port with FML config path |

## Signature drift

- `ParticleEmitter.render` now takes Mojmap
  `(VertexFormat, Supplier<ShaderInstance>, PoseStack, int, float)` and performs native additive and
  collision batches. The addon must inject at HEAD only for morph forms and must not redirect blend
  calls.
- `ParticleComponentAppearanceBillboard` now uses `CameraFacing.isLookAt/isDirection`, string
  `directionMode`, `speedThreshold`, and `customDirection`; it also supports emitter-transform
  planes. The source Mixin's obsolete `BillboardDirection`/`facingDirection` fields cannot compile.
- `ParticleComponentMotionCollision.update` directly maintains `Particle.intersected`; the source
  field injection would duplicate and reset core state.
- `UIParticleSchemePanel` replaced `appearanceView` with `panelById["appearance"]` containing a
  `UIParticleTabPage` and a shared `sections` list.
- `UIDataPathList` method shapes remain compatible, but Fabric config lookup becomes
  `FMLPaths.CONFIGDIR.get()`.
- `BufferBuilder`, `VertexFormat`, `PoseStack`, `ShaderInstance`, `LightTexture`, `LevelRenderer`,
  `BlockPos`, `Axis`, and `Minecraft` use Mojmap 1.21.1 types.

## Parser drift

The source registered the full key `blockbuster:particle_morph`. FSR now strips known prefixes
before component lookup and serializes Blockbuster keys through a private set. The compatible target
shape is normalized map key `particle_morph` plus membership in that set. Registering only the full
source key would make parse lookup fail; registering only the normalized key would serialize under
the wrong namespace.

## Target contracts to preserve

- FSR camera parity across world, editor and GUI perspective.
- `FormRenderingContext` separation between camera-relative render stack and semantic world stack.
- Stable `simulationOwner` for stateful model IK/physics.
- FSR collision appearance stays visible independently of ordinary texture suppression.
- Helpers remain outside `mchorse.bbs_snow.mixin.client`.
- `defaultRequire: 1`, no refmap, and a real client execution gate remain mandatory.
