# Diagnosis evidence

## Runtime failure

The supplied runtime log proves Connector discovered and transformed IRLite 1.1.2:

```text
Found valid mod file irlite-1.1.2+mc1.21.1_mapped_moj_1.21.1.jar
Loading mod file .../mods/.connector/irlite-1.1.2+mc1.21.1_mapped_moj_1.21.1.jar
```

Mixin then aborts before BBS common setup:

```text
Mixin apply for mod irlite failed
irlite.client.mixins.json:WorldBlockChangeMixin -> net.minecraft.world.level.Level

Expected:
(BlockPos, BlockState, int, CallbackInfoReturnable)V

Found:
(BlockPos, BlockState, int, int, CallbackInfoReturnable)V
```

Primary evidence file:
`new/log/错误报告-2026-7-26_21.16.25/debug.log`, lines 607-609, 877, 891-893,
and 4065-4069.

## Static source comparison

The local pre-fix IRLite source explicitly injects:

```java
@Mixin(World.class)
@Inject(method = "setBlockState(...;II)Z", at = @At("HEAD"))
private void irlite$invalidateBlockShadows(
    BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
    CallbackInfoReturnable<Boolean> cir)
```

NeoForge 1.21.1 `Level` contains both:

```java
boolean setBlock(BlockPos, BlockState, int)
boolean setBlock(BlockPos, BlockState, int, int)
```

The error therefore matches overload ambiguity introduced when distinct Fabric intermediary
method names are converted to the shared Mojmap name `setBlock`.

## Upstream resolution

Upstream commit `f7e08e7a` is an exact-match fix. Its commit message states that Connector
remaps the bare intermediary target to ambiguous Mojmap `setBlock`. It retargets the Mixin to
the non-overloaded client notification method:

```java
@Mixin(ClientWorld.class)
@Inject(method = "updateListeners(...oldState...newState...I)V", at = @At("HEAD"))
```

This also improves semantics by receiving old/new state directly and only observing real
client-visible block changes.

## Connector version check

The failing runtime uses Connector beta.15 and Adapter core 2.0.36. The unreleased beta.16
source bumps Adapter to 2.0.37, but the only changed Adapter source between 2.0.36 and 2.0.37
is `ResolverSyntheticInstanceof.java`; it does not address this overload problem. Upgrading to
the current Connector source is therefore not a demonstrated fix.

## Migration assessment

IRLite has 46 Java source files, 17 Mixin/accessor classes, and only five source files with
direct Fabric imports. A native NeoForge port is feasible but materially larger than this
one-file compatibility fix. The current evidence supports patch-first, migrate-only-after-new-
evidence.

