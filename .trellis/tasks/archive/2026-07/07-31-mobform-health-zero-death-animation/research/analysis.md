# Analysis: MobForm `{Health:0}` death animation

Date: 2026-07-31

## Reproduction path

- `UIMobFormPanel` writes the NBT text to `MobForm.mobNBT`.
- `MobFormRenderer.ensureEntity()` recreates the preview entity when the NBT string changes and calls `entity.load(compound)`.
- `LivingEntity.readAdditionalSaveData()` applies `Health`, but without `DeathTime` the death clocks stay at zero.
- `EnderDragon` has a renderer-specific `dragonDeathTime`; its override of `tickDeath()` advances that clock, while ordinary living entities advance the inherited 20-tick `deathTime`.

## Reference implementation (bbs-fs-1.21.1)

- `bbs-fs-1.21.1` `MobFormRenderer.tick()` calls `entity.tick()` every frame and never special-cases entity types.
- Java virtual dispatch reaches each entity's own death lifecycle, including the dragon's overridden `tickDeath()`.
- No `LivingEntityInvoker`, no access transformer, and no entity-type check exist in the reference.
- Vanilla Mojang 1.21.1 (shared by Fabric and NeoForge) gates `tickDeath()` behind `ClientLevel.shouldTickDeath()`, so the reference inherits that distance-dependent behavior too.

## Selected fix (reference parity, generic)

- Keep `MobFormRenderer.tick()` calling `entity.tick()` for alive and dead previews, exactly like the reference; the entity's own virtual `tickDeath()` advances the correct clock.
- Add one generic guard `refreshDeathState()`: a dead `LivingEntity` keeps `Pose.DYING` after `synchronizeEntity()` would otherwise reset it to standing/crouching.
- The guard is applied after NBT load and after per-frame source synchronization; it contains no entity-type checks and adds no new file or mixin.

Rejected approaches:

- Hard-coding `EnderDragon.dragonDeathTime` (worked but was entity-type-specific).
- `LivingEntityInvoker` Mixin `@Invoker` (worked and was deterministic, but deviated from the reference, which introduces no such file).
- Access transformer making `tickDeath` public (broke `neoFormRecompile`: the protected overrides in `LocalPlayer` and `EnderDragon` would lower the inherited visibility).

## Verification

Automated:

- `compileClientJava`
- `testUiInputMailbox`, including a source-level contract that the tick path calls `entity.tick()` and never references `EnderDragon` or a removed invoker
- `testReplayKeyframeConsistency`
- `git diff --check`

Required runtime gate before commit:

1. Open an Ender Dragon MobForm and apply `{Health:0}`; the exploding texture and rays must begin immediately and progress through the dissolve.
2. Apply positive health and confirm the newly created preview is normal again.
3. Smoke-test a normal living MobForm with `{Health:0}` so the generic path is exercised beyond the dragon.