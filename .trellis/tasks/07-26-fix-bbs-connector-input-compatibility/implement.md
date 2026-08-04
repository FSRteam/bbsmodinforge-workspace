# Implementation plan

## 1. Restore Connector-facing BBS compatibility

- [x] Add an optional reflective Fabric `bbs-addon` discovery adapter under the FSR loader/addon
      boundary.
- [x] Import discovered provider ids and addon instances into `BBSAddonCollector` before bridge
      closure.
- [x] Restore `BBSMod.onInitialize()` as a public compatibility hook and invoke it exactly once
      before `RegisterSettingsEvent`.
- [x] Add lifecycle/order tests covering Connector absent, empty entrypoints, successful import,
      duplicate ids, broken entrypoints, and the legacy hook symbol/order.

## 2. Repair pointer viewport ownership

- [x] Add an event-frame API to `UIContext` that isolates viewport and pointer state.
- [x] Refactor click, release, scroll, and cancel paths in `UIBaseMenu` to use the frame in
      `try/finally` and balance the menu viewport.
- [x] Replace the corruption-tolerating cleanup test with nested dispatch, pointer restoration,
      exception restoration, and top-level cleanup tests.
- [x] Keep resize/render behavior and existing gesture ownership contracts unchanged.

## 3. Verification

- [x] Run the addon lifecycle/loader test task selected from `new/build.gradle`.
- [x] Run `new\\gradlew.bat --no-daemon --console=plain testUiInputMailbox`.
- [x] Run the relevant broader compile/test tasks required by the `new` package specs.
- [x] Build the FSR jar and inspect `BBSMod` to confirm the public `onInitialize()` symbol.
- [ ] If a runnable IRLite jar and its `irl-core` dependency are available, run the four-way
      control matrix and capture a combined Connector + IRLite log.

## Verification result (2026-07-26)

- Work commits: `0e2a9be7` (Connector addon compatibility) and `0d7e883c`
  (nested pointer viewport isolation).
- `testAddonLifecycle`, `testClientStructuralRegistration`, `testBinaryCompatibility`, and
  `testUiInputMailbox` passed.
- `compileJava`, `compileClientJava`, and the strict full `build` passed; the full build completed
  75 tasks with every configured regression gate green.
- `BBS_FSR_beta_v0.0.10.jar` contains the reflective importer and
  `UIContext$PointerEventFrame`; `BBSMod` exports public virtual `onInitialize()V`.
- `jdeps` reports no `net.fabricmc.loader` type dependency. Fabric class names occur only as
  reflection strings in `ReflectiveDiscoverySource`.
- No runnable IRLite/irl-core fixture JAR was found under the task source checkout, Downloads, or
  the default Minecraft mods folder. The real Connector + IRLite runtime matrix remains pending
  and must not be reported as passed.

## Risk and rollback points

- Loader discovery must never create a hard Fabric Loader linkage in a plain NeoForge run.
- Legacy-hook placement is order-sensitive because IRLite needs both initialized form registries
  and a later `RegisterSettingsEvent`.
- Pointer-frame restoration must include mouse button and wheel fields, not only the viewport.
- Stop and revise the design if target-combination logs show an earlier required Minecraft/Iris
  Mixin failure; external source changes remain out of scope.
