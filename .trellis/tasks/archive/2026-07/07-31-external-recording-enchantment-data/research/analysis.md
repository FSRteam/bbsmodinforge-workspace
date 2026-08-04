# Analysis: External recording loses enchantment data on equipped/held items

Date: 2026-07-31

## Confirmed root cause (NBT clearing hypothesis verified)

- Replay keyframes and the film inventory serialize `ItemStack` through
  `ItemStackKeyframeFactory`, which used `ItemStack.CODEC` with plain `NbtOps.INSTANCE`.
- In Minecraft 1.21.1, `ItemStack.CODEC` encodes item components, and the enchantment
  component uses `Enchantment.CODEC = RegistryFixedCodec.create(Registries.ENCHANTMENT)`.
- `RegistryFixedCodec.encode/decode` both fail unless the `DynamicOps` is a
  `RegistryOps` (checked against the remapped vanilla/NeoForge sources).
- Therefore an enchanted item failed `encodeStart` and was stored as an empty map
  (the user-visible "NBT cleared"); unenchanted items have no registry-backed
  component, so they round-tripped normally.
- The reference `bbs-fs-1.21.1` uses the same `ItemStack.CODEC` + `NbtOps` pattern,
  so it shares the same latent defect.

## First fix attempt (uncommitted, causes a runtime exception)

- `ItemStackKeyframeFactory` gained a registry-aware `ops()` plus a static
  `registryAccessProvider`; `BBSModClient` installs the current client level
  registry access as the provider.
- Automated round-trip test passes, but real recording/replay now throws:

  java.lang.IllegalArgumentException: Can't find id for 'Reference{ResourceKey[minecraft:enchantment / minecraft:efficiency]=Enchantment ...}' in map net.minecraft.core.Registry$1

## Why the first fix attempt fails at runtime

- `Registry$1` is `Registry.asHolderIdMap()`; the message is thrown by
  `IdMap.getIdOrThrow` through `ByteBufCodecs.holderRegistry(...)`, i.e. the
  network/stream encode path (`ByteBufCodecs.java:458-511`).
- The static provider is client-global. Server-side playback therefore decodes
  the recorded keyframes with the CLIENT level registry access, producing
  enchantment holders bound to the client registry instance.
- When `actor.setItemSlot(...)` later streams the stack with the SERVER registry
  access (`RegistryFriendlyByteBuf`), the client-bound holder is not in the server
  registry's id map, so encoding throws "Can't find id".
- In an integrated server the client and server keep separate registry instances,
  so one global provider cannot serve both sides.

## Required direction for the next fix

The registry context must follow the item's origin/destination, not one global:

1. Recording (client): encode with the recorded entity/player level registry access
   (client registries, because the stacks and their holders come from client entities).
2. Server playback / server film load: decode with the server registry access
   (`ServerLevel.registryAccess()`), so holders are server-bound and stream encoding works.
3. Film editor preview (client): decode with the client level registry access.
4. Keep the NBT format registry-independent (it already stores enchantment names as
   ResourceLocations via RegistryFixedCodec), so cross-instance decode by name works.

Suggested shape: thread `HolderLookup.Provider` (or registry access) explicitly into
`ReplayKeyframes.record/apply/fromData`, `Inventory.fromPlayer/toData/fromData/stageForPlayer`,
and the factory's `toData/fromData/tryFromData` overloads; do not rely on a static
process-wide provider. `Inventory.stageForPlayer(player, list)` can use
`player.registryAccess()`; `ActionPlayer.applySafely` decode can use the actor/server
level; `Recorder.update` and the film editor can use the client level.

## Status

- Per-context registry fix implemented (uncommitted, pending user runtime confirmation):
  - `src/main/java/mchorse/bbs_mod/utils/keyframes/factories/ItemStackKeyframeFactory.java` — replaced the global static provider with per-call context resolution: `currentRegistry()` uses the server registry when running on a server thread (`SidedThreadGroups.SERVER` thread-group check; `ServerLifecycleHooks.getCurrentServer()` is a plain static field, NOT thread-isolated, so the thread group is the reliable signal) and otherwise the client level registry installed via `setClientRegistryAccess`. Added explicit `toData/fromData/tryFromData(value, HolderLookup.Provider)` overloads for callers that hold a provider.
  - `src/main/java/mchorse/bbs_mod/film/replays/Inventory.java` — `stageForPlayer` passes `player.registryAccess()` explicitly to `tryFromData`.
  - `src/client/java/mchorse/bbs_mod/BBSModClient.java` — `setClientRegistryAccess` installs `Minecraft.getInstance().level.registryAccess()` as the client fallback.
  - `src/replayKeyframeTest/.../ReplayIndexRemappingTest.java` — round-trip test uses `setClientRegistryAccess(() -> provider)`.
- Design note: `RegistryFixedCodec.encode` only writes the holder's ResourceLocation (its `canSerializeIn` is an identity check against the holder's owner registry), so encode works with either registry as long as it matches the holder's bound registry; `RegistryFixedCodec.decode` binds holders to the ops registry, so server-side film decode MUST use the server registry for `RegistryFriendlyByteBuf` stream encoding to succeed during `setItemSlot`.
- Compile (`compileJava`/`compileClientJava`/`compileReplayKeyframeTestJava`) and `testReplayKeyframeConsistency` pass.
- Do not commit until the user verifies both recording and server-side replay of enchanted equipment at runtime.