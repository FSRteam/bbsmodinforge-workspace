# BBS Together 1.6.14 -> BBSFSR Implementation Plan

## 1. Recover And Scaffold

- [x] Create `bbs-together-addon/` from the proven standalone NeoGradle addon skeleton without copying build outputs.
- [x] Copy the Yarn-remapped CFR sources into the correct main/client source sets and preserve the original MIT license/authorship.
- [x] Copy all 19 language resources; replace Fabric metadata with `neoforge.mods.toml` and Java 21 Mixin configs.
- [x] Add README provenance, target versions, build instructions, behavior differences, and known limitations.

Validation:

```powershell
rg -n "class_[0-9]+|method_[0-9]+|net\.fabricmc" bbs-together-addon/src
```

Expected: no matches after platform migration is complete.

## 2. Bootstrap, Settings, And Resources

- [x] Add common/client NeoForge entrypoints and the API 2.0 `BBSAddonDescriptor`.
- [x] Register settings and source pack during the addon registration window.
- [x] Activate translations after source-pack registration and repair the corrupted legacy Chinese strings.
- [x] Port default keyframe shape/interpolation/color behavior and the utility toggles using current FSR UI/value APIs.

Validation: focused compile plus packaged-resource inspection.

## 3. Broker Transport And Safety

- [x] Implement broker message ids and small-payload send/receive adapters.
- [x] Implement bounded chunk transfer for Film snapshots and assets.
- [x] Add normalized asset path containment, file/manifest limits, checksums, concurrency limits, timeouts, and cleanup.
- [x] Register server handlers through API 2.0 and client handlers through `BBSClientApi`.
- [x] Replace Fabric connection/lifecycle callbacks with NeoForge/BBS client lifecycle callbacks.

Validation: codec, malformed packet, chunk reorder/duplicate/timeout, traversal, oversized input, and disconnect cleanup tests.

## 4. Native Film Collaboration

- [x] Register the FSR Film collaboration listener.
- [x] Translate local mutations, checkpoints, presence, session open/close, and server sequence into Together messages.
- [x] Apply remote mutation/snapshot/presence through the typed FSR API.
- [x] Add resync on server-sequence gaps and prevent remote-apply echo loops.
- [x] Remove superseded broad UI/cursor/keyframe Mixins from the port.

Validation: two simulated peers for local commit -> server order -> remote apply, presence projection, gap recovery, and session close.

## 5. Remaining Collaboration Features

- [x] Port Film list create/duplicate/rename/delete notifications with focused current-method hooks.
- [x] Port replay reorder/category and form lock ownership.
- [x] Port synchronized recording command and recorder merge/baseline behavior against current FSR APIs.
- [x] Port asset manifest/download/upload/push settings and progress state.
- [x] Port damage-control activation and HUD indication.

Validation: targeted state tests and source-level Mixin target checks for every retained injection.

## 6. Compile-Driven Migration

- [x] Replace all Yarn names with Mojang 1.21.1 names and update changed signatures.
- [x] Resolve BBS 2.3 -> current FSR API differences using `new/src` as the source of truth.
- [x] Repair CFR artifacts, including the unstructured `handleFilmDataSyncPacket` lambda, from bytecode/control flow rather than deleting behavior.
- [x] Keep common code free of client-only class references.

Validation:

```powershell
cd bbs-together-addon
.\gradlew.bat compileJava compileClientJava
```

## 7. Build And Runtime Verification

- [x] Run unit tests and clean build.
- [x] Inspect the JAR for mod metadata, both Mixin configs, translations, and absence of BBS/Fabric classes.
- [x] Start the development client with BBSFSR and scan the complete log for addon registration, missing assets, and Mixin failures.
- [ ] When feasible, run a dedicated server plus two clients and smoke-test shared presence, one Film mutation, one replay lock, synchronized record start/stop, and one asset round trip.
- [x] Record any environment-limited runtime check precisely in README/task notes rather than treating compilation as runtime proof.

Commands:

```powershell
cd bbs-together-addon
.\gradlew.bat clean test build
.\gradlew.bat runClient
```

## Risk And Rollback Points

- First rollback point: scaffold + clean decompiled source before behavior changes.
- Second rollback point: broker transport and safety tests before UI integration.
- Third rollback point: native Film collaboration before remaining focused Mixins.
- Do not edit BBSFSR core to bypass an addon compile/runtime defect. Any newly discovered missing public capability requires a separate reviewed decision.
- If a retained Mixin target is unstable, stop that feature at its last passing checkpoint, document the gap, and keep the rest of the addon buildable.

## Verification Notes

- `2026-08-05`: `clean test build` completed successfully in 7m 58s; a later
  post-fix `test build` completed successfully in 1m 33s.
- Test report: 22 tests, 0 failures, 0 errors, 1 skipped Windows-only symbolic-link
  capability case.
- Release JAR: 80 entries, 19 translations, both Mixin configs, no
  `mchorse/bbs_mod/**`, and no Fabric/Connector entries.
- All 19 translation JSON files parse successfully. Source scan found no Yarn
  placeholder names or Fabric/Connector runtime references.
- Development client reached the main menu with BBSFSR 0.0.11. Addon API 2.0
  accepted `bbs_together`, both broker directions registered, and the strict
  client Mixin config prepared all 5 Mixins without apply/injection errors.
- The first startup exposed a pre-login handshake attempt. It was moved to
  `ClientPlayerNetworkEvent.LoggingIn` and the transport now rejects sends with
  no active connection; the second startup had no addon ERROR/FATAL, missing
  translation, or Mixin failure.
- The development client was intentionally stopped after startup validation,
  so Gradle reports the externally terminated `runClient` process as non-zero.
- Dedicated-server plus two-client collaboration and asset round-trip smoke
  testing remains pending because this session has only one interactive client.
