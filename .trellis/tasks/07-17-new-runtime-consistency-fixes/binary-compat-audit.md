# Binary Compatibility Audit

Scope: compare `new/` working tree with `HEAD` for erased public/protected JVM
descriptors reachable by Addon v1, Addon/API 2.0, and the public compatibility
facades. The Addon API version remains exactly `2.0`.

## Retained Legacy Descriptors

- `NetworkCompatClient.ClientReceiver.receive(FriendlyByteBuf)V` remains the
  one-method legacy SAM. Its register method and two-argument dispatch method
  remain present; new transport-scoped receiver APIs are additive.
- `BBSApi` and `BBSMod` retain both API 2.0 `registerAddon` overloads;
  `BBSMod.registerAddon(String, Supplier)V` and both v1 event overloads remain.
- `BBSClientApi` and `ClientApiCompat` retain legacy key mapping, entity
  renderer, and block-entity renderer descriptors. Descriptor-aware overloads
  are additive.
- `ActionPlayer` retains its seven-argument constructor,
  `updateReplayEntities()V`, and `stop()V`.
- `ActionManager.startRecording(Film, ServerPlayer, int, int, int)V` delegates
  to additive `tryStartRecording(...)Z`.
- `ActionClip.applyPositionRotation(SuperFakePlayer, Replay, int)V` remains a
  protected virtual hook. Built-in guarded playback uses additive
  `tryApplyPositionRotation(...)Z`, which validates then invokes the old hook.
- `PacketCrusher` retains public `()V` and
  `receive(FriendlyByteBuf, IBufferReceiver)V`; the unscoped receive entry
  fails closed. Scoped receive overloads are additive.
- `UIFilmPanel.receiveActions(String, int, int, BaseType)V` delegates to the
  extended terminal-aware action-only path.
- `UIFilmRecorder` retains both public `startRecording(... )V` overloads and
  exposes additive `tryStartRecording(... )Z` overloads.
- `VideoRecorder` retains `startRecording(String, File, int, int, int)V` and
  `stopRecording()V`; guarded start and terminal results use additive
  `tryStartRecording(...)Z` and `completeRecording()`.
- `VideoExportUtils.deleteTemporaryFile(File)V` remains; cleanup result users
  call additive `tryDeleteTemporaryFile(File)Z`.
- `Scroll` retains both `mouseReleased(... )V` overloads and exposes additive
  `tryMouseReleased(... )Z`. `UICanvas.zoom(UIContext, int)V` remains alongside
  the double-precision overload.
- `IKControl.DEFAULT_SOFTNESS` remains the API 2.0 compile-time value `0.05F`.
  Core runtime defaults use additive `HARD_REACH_DEFAULT_SOFTNESS = 0F` so
  omitted softness still means hard reach without changing old inlined code.

## Additive Types

- New public API 2.0 UI mirror, render surface, Film collaboration, and client
  registration facade types do not replace old descriptors.
- `NetworkCompatClient.ScopedClientReceiver`, `PlayerType.TARGETED_COMMAND`,
  recording terminal/result enums, and new constructors/methods are additive.
  Existing `PlayerType` constants retain ordinals `0..2`.

## Resolved Exclusions

- `GunProjectileEntity.getPermissionLevel()I` is no longer declared on the
  subclass, but JVM method resolution finds the same protected descriptor on
  `Entity`; this is not a linkage deletion.
- Deleted `BlockItemMixin` and `ItemStackMixin` implementation classes were
  never Addon/API surface. They were removed from `bbs.mixins.json` together
  with their source and replaced by the unified interaction hook, so no mixin
  configuration references a missing class.

## Regression Gate

`testBinaryCompatibility` runs
`LegacyPublicDescriptorCompatibilityTest` and is a dependency of `check`. It
locks exact parameter/return types, visibility, constructors, SAM cardinality,
legacy enum ordinals, inherited resolution, and the dual IK softness constants.
`NetworkCompatClientDescriptorTest` additionally verifies that legacy unscoped
client registration/dispatch fails closed while scoped transport APIs remain
available.
