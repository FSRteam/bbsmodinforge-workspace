# BBS Together for BBSFSR

This is a NeoForge 1.21.1 port of BBS Together 1.6.14 for BBSFSR 0.0.11.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.222 or newer compatible 21.1 release
- Java 21
- BBSFSR 0.0.11 or newer compatible release
- The same BBS Together port on the server and collaborating clients

Install `bbs-together-addon-1.6.14-fsr.jar` in the `mods` directory on the
server and every collaborating client. Do not install the sources JAR.

## Included Features

- Player colors and native BBSFSR Film collaboration for mutations, snapshots,
  playheads, cursors, selections, replay focus, and remote presence.
- Film create, duplicate, rename, and delete notifications backed by BBSFSR's
  own Film repository.
- Replay ordering/category synchronization through native Film mutations, plus
  server-authoritative replay form locks.
- Permission-checked synchronized recording and the damage-control lock HUD.
- Checksummed, chunked asset upload, download, and server push with bounded
  paths, sizes, concurrency, memory, and timeouts.
- Configurable default shape, interpolation, and color for manually created
  keyframes.
- The 19 language resources recovered from the upstream JAR, including repaired
  Simplified Chinese strings.

## Commands

Both commands require Minecraft permission level 2:

```text
/bbstogether upload
/bbstogether record <targets>
```

`upload` asks connected clients to compare and synchronize their enabled BBS
assets. `record` toggles synchronized recording for the selected players; each
player must have an active Film editor session before recording can start.

Asset downloads and uploads are enabled by default. Receiving unsolicited
server asset pushes is disabled by default and can be enabled in the BBS
Together settings.

## Build

Build BBSFSR first so `../new/build/libs/BBS_FSR_beta_v0.0.11.jar` exists, then run:

```powershell
cd bbs-together-addon
.\gradlew.bat clean test build
```

The standalone addon JAR is written to `build/libs/` and does not bundle BBSFSR.

## Port Notes

- Fabric entrypoints and raw Fabric networking were replaced with native NeoForge lifecycle hooks and BBSFSR Addon API 2.0.
- Film changes, snapshots, cursors, playheads, and selections use BBSFSR's native Film collaboration API.
- Large snapshots and assets use a bounded, checksummed chunk protocol.
- Asset paths are normalized and restricted to the BBS assets directory.
- The unused upstream CDN URL and token settings were intentionally removed.
- This port is not wire-compatible with the Minecraft 1.20.1 Fabric release.

## Known Limitations

- Both the server and participating clients must use this NeoForge port; mixed
  Fabric/NeoForge or Minecraft 1.20.1/1.21.1 sessions are unsupported.
- BBSFSR remains the owner of Film and replay files. Together synchronizes
  collaboration state and CRUD results instead of replacing that repository.
- Automated tests cover the transport, paths, transfer lifecycle, sequence
  tracking, replay locks, and asset storage. Development-client startup has
  been verified, but a dedicated server plus two-client end-to-end session must
  still be smoke-tested in the intended multiplayer environment.

## Provenance

The port was recovered from the supplied `bbs-together-1.6.14-bbs2.3-wenxu-1.20.1.jar` (SHA-256 `7802CBB9DDCDB52C80261560E625459DC3D165786113CB7F8A123FA27726DA61`). The original mod metadata identifies McHorse and ChillinWithDylan as authors and declares the MIT license. Decompiled output was used as behavioral reference and was rewritten against public BBSFSR and NeoForge APIs.
