# BBS Together 1.6.14 -> BBSFSR Design

## Boundaries

- New standalone project: `bbs-together-addon/`.
- Upstream evidence: the supplied JAR plus the readable Yarn-remapped CFR output under `temp/`.
- Target runtime: Minecraft 1.21.1, NeoForge 21.1.222, Java 21, BBSFSR 0.0.11.
- Preserve `mchorse.bbs_together` packages, `bbs_together` mod id/namespace, original authors, and MIT license.
- Do not modify BBSFSR core to make this addon compile. If a public FSR extension point exists, consume it from the addon.

## Project Layout

Mirror the proven standalone addon layout:

```text
bbs-together-addon/
  build.gradle
  gradle.properties
  settings.gradle
  gradlew / gradlew.bat / gradle/wrapper/
  README.md / LICENSE
  src/main/java/...
  src/main/resources/META-INF/neoforge.mods.toml
  src/main/resources/bbs_together.mixins.json
  src/client/java/...
  src/client/resources/bbs_together.client.mixins.json
  src/client/resources/assets/bbs_together/assets/strings/*.json
  src/test/java/...
```

The Gradle build uses `net.neoforged.gradle.userdev` 7.1.21, Java 21, and `compileOnly files(bbs_fsr_jar)`. Resource processing expands the mod metadata. The final JAR must not contain `mchorse/bbs_mod/**`.

## Bootstrap And Addon Registration

- Common `@Mod("bbs_together")` constructor registers one API 2.0 addon descriptor immediately through `BBSMod.registerAddon`.
- Descriptor capabilities: `SOURCE_PACKS`, `SETTINGS`, `NETWORK`, `CLIENT_UI`, and `EVENTS`; namespace `bbs_together`; required mod `bbs`.
- API 2.0 `register` installs settings, the internal source pack, and all server-side broker receivers.
- A `Dist.CLIENT` bootstrap registers client broker receivers, the Film collaboration listener, tick/disconnect/HUD hooks, and client-only settings UI.
- NeoForge events replace Fabric command, connection, server lifecycle, client tick, disconnect, and HUD callbacks.

## Network Architecture

Use BBSFSR's addon broker rather than raw NeoForge payload registration:

```text
client feature -> TogetherClientTransport -> BBS addon broker C2S
               -> TogetherServerHandlers -> authoritative state / storage
               -> BBS addon broker S2C -> TogetherClientHandlers -> UI/API apply
```

- Preserve meaningful `bbs_together:*` message ids so code and diagnostics remain understandable.
- Use `FriendlyByteBuf` and Mojang-mapped 1.21.1 types.
- Keep server state authoritative: editing Film id, colors, locks, recording membership, transfer sessions, and server sequence.
- Dispatch world/UI mutation to the appropriate game thread. Background executors are limited to hashing and bounded file I/O.
- Every receiver validates string length, collection count, index range, encoded BBS data size, permission, active Film id, and sender state before mutation.
- Disconnect and server stop share idempotent cleanup paths.

### Large Payloads

The broker allows about 28 KiB C2S and 1 MiB S2C bodies. Film snapshots and assets can exceed that, so the addon owns a bounded chunk envelope:

- transfer id, logical message id, chunk index/count, declared total bytes, and optional path metadata;
- chunks below the C2S/S2C broker limits;
- maximum file/snapshot size, maximum chunks, per-player concurrent transfer cap, global memory cap, idle timeout, and duplicate/out-of-order rejection;
- checksum before commit and cleanup on disconnect, failure, timeout, or completion.

No partial file is published as a valid asset. Asset paths are normalized and resolved under `BBSMod.getAssetsFolder()`; containment is checked with normalized absolute paths instead of substring checks.

## Film Collaboration

FSR already captures committed mutations, snapshots, semantic cursor position, selection, replay focus, and revision changes. Register a `BBSFilmCollaborationListener` and translate between its typed records and Together messages:

- `onSessionOpened/Closing/Closed`: join, final checkpoint, leave, and state cleanup;
- `onLocalMutations`: send one operation with local operation id and base revision;
- `onCheckpointRequired`: request and send a bounded snapshot;
- `onPresence`: send native semantic presence;
- remote mutation/snapshot/presence: apply through `BBSClientApi`, using the server sequence to detect gaps and trigger resync.

This replaces the broad legacy `UIFilmPanelMixin`, `UIFilmUndoHandlerMixin`, cursor render injection, and most FS2 keyframe collaboration code. A small Mixin is retained only where FSR has no public callback, such as Film list CRUD, replay form lock open/close, synchronized recording boundaries, or utility settings placement.

Remote applies must not echo back as new local operations. Use FSR's remote-apply boundary where available; otherwise keep a scoped depth guard with `try/finally`.

## Feature Mapping

| Upstream feature | FSR implementation |
|---|---|
| Player colors and editor presence | addon broker + native Film collaboration presence |
| Shared cursor, playhead, selections | native semantic presence and built-in rendering |
| Film value/keyframe changes | native local mutation listener and remote apply API |
| Whole Film resync | native bounded snapshot API over chunk transport |
| Film create/duplicate/rename/delete | focused dashboard/Film-list hooks + authoritative server broadcast |
| Replay reorder/category/form lock | focused replay editor hooks + validated server messages |
| Synchronized recording | NeoForge command/event hooks + current FSR recorder APIs |
| Asset manifest/download/upload/push | hashed manifest + bounded chunk transport + path containment |
| Default keyframe shape/interpolation/color | retained settings values and minimal keyframe creation hook |
| Damage-control HUD indicator | NeoForge `RenderGuiEvent.Post` client hook |

## Resources And Localization

- Register an `InternalAssetsSourcePack` with owning mod id, source prefix `bbs_together`, and root `assets/bbs_together/assets`.
- Defer L10n provider activation until the client source pack is registered, then reload.
- Preserve all 19 translation files. The supplied `zh_cn.json` contains mojibake in its first five legacy strings; repair those strings from their English keys/context while preserving the newer valid Chinese entries.
- Remove `fabric.mod.json`, the Fabric refmap, and Fabric entrypoints from the final project.

## Compatibility And Failure Behavior

- Both sides must run the NeoForge 1.21.1 port; cross-version Fabric protocol compatibility is intentionally unsupported.
- A missing server addon leaves client collaboration inactive without crashing BBSFSR.
- Invalid or unauthorized packets are rejected and logged with bounded detail; they never partially mutate Film or filesystem state.
- If an optional UI injection no longer has a stable target, omit that cosmetic integration and document it. Core sync behavior must not silently disappear.
- Rollback is removal of the standalone addon JAR/directory; BBSFSR core remains untouched.

## Verification Strategy

- Static: no Fabric/intermediary symbols, source/resource completeness, Mixin target audit, dependency/JAR-content inspection.
- Unit: codec bounds, chunk assembler, path containment, permission/session gates, cleanup, server-sequence gaps, and remote echo suppression.
- Build: `gradlew clean build` against `new/build/libs/BBS_FSR_beta_v0.0.11.jar`.
- Runtime: dedicated/common initialization, client startup, two-client collaboration smoke where available, Mixin apply log scan, and asset round trip using a temporary fixture.
