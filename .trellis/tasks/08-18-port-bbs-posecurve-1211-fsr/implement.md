# Implementation Plan

## Ordered Checklist

- [x] Create `BBS-PoseCurve-fsr-1.21.1/` from the repository's proven standalone
  NeoForge addon build pattern.
- [x] Add `gradle.properties`, `settings.gradle`, wrapper files, source sets,
  stale-core verification, client run setup and resource expansion.
- [x] Add NeoForge `@Mod` entrypoint, `neoforge.mods.toml`, common/client Mixin
  configs and development verification bootstrap.
- [x] Copy the functional 1.20.1 helper and Mixin sources without changing the
  reference directory; omit the empty Fabric client/datagen entrypoints.
- [x] Convert Mixin metadata to Java 21/no-refmap/non-remapped BBS targets.
- [x] Replace all Fabric and intermediary Minecraft references with NeoForge
  and 1.21.1 Mojmap equivalents.
- [x] Compile against the current BBSFSR jar and repair API, field, descriptor
  and generic-signature differences in small batches.
- [x] Verify every old functional source is migrated or explicitly accounted
  for; do not leave disabled or empty substitutes.
- [x] Add focused pure-logic tests and the opt-in client Mixin target verifier.
- [x] Build the addon, run logic checks, audit jar contents and run the strict
  client verifier when the local graphical environment permits.
- [x] Perform a final search for Fabric/intermediary names, stale BBS version
  paths, weakened Mixin requirements and accidental edits to `new/` or the old
  source tree.

## Validation Commands

Run from `BBS-PoseCurve-fsr-1.21.1/` unless stated otherwise:

```powershell
.\gradlew.bat clean testPoseCurveLogic build
.\gradlew.bat runClient -Pverify=1
jar tf build\libs\bbs-posecurve-addon-2.8.3-1.21.1.jar
rg -n "net\.fabricmc|net\.minecraft\.class_|method_[0-9]+|field_[0-9]+" src
```

Repository-level checks:

```powershell
git status --short -- BBS-PoseCurve-fsr BBS-PoseCurve-fsr-1.21.1 new
```

## Review Gates

- Gate 1: Gradle scaffold resolves the same Java, Minecraft and NeoForge
  versions as `new/`, and the core-jar check points to 0.0.11.
- Gate 2: all migrated sources compile with no Fabric/intermediary references.
- Gate 3: strict target loading applies every required Mixin config without a
  critical injection error.
- Gate 4: final jar contains only addon classes/resources and does not contain
  BBSFSR implementation classes.

## Risky Files and Rollback Points

- `UIPoseTransformKeyframeGraph.java` and `MixinUIKeyframeGraph.java` contain
  most rendering and interaction behavior; keep changes limited to required API
  adaptation and validate after each compiles.
- `MixinUIReplaysEditor.java` owns per-replay/category state; verify state is
  cleared only on a real Film switch.
- Lambda injection targets are compiler-generated and must be rechecked after
  any BBSFSR jar rebuild.
- Before the first source copy, the task can be rolled back by deleting only the
  newly created addon directory. Afterward, rollback remains isolated because
  no `new/` source modification is planned.
