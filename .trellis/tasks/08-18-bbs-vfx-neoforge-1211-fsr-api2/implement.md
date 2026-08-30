# Implementation Plan

## Phase 0: Baseline And Workspace

- [x] Confirm the gh-proxy archive hash and clean source snapshot.
- [x] Record the untouched Fabric 1.21.1 compile results, including the six known client errors.
- [x] Verify BBSFSR 0.0.11 jar, source APIs, NeoForge version and Java 21 toolchain.
- [x] Keep all edits inside the independent BBSVFX project.

## Phase 1: Fabric Mapping Intermediate

- [x] Copy the clean source to a disposable mapping workspace.
- [x] Fix only the six upstream Fabric 1.21.1 client baseline errors in that workspace.
- [ ] Run Loom `migrateMappings` to official 1.21.1 Mojmap for main and client source sets.
- [x] Compile the mapped intermediate and manually repair imports, overloads and Mixin descriptors.
- [x] Audit all Fabric-only imports before using the result as NeoForge input.

> The final port started from the official 1.21.1 snapshot and was repaired directly against
> NeoForge/Mojmap. The disposable Loom mapping task was not carried into the final NeoForge project.

## Phase 2: NeoForge Project Skeleton

- [x] Replace Fabric Loom/build metadata with NeoGradle 7.1.21 and NeoForge 21.1.222.
- [x] Add `neoforge.mods.toml`, remove `fabric.mod.json`, and configure BBSFSR 0.0.11 as a local dependency.
- [x] Add common/client `@Mod` bootstrap and preserve Java 21 split source sets.
- [x] Register the destruction wand using NeoForge-native registration/events.
- [x] Reach a clean `compileJava` and `compileClientJava` with the smallest source slice.

## Phase 3: BBS API 2.0 Common Port

- [x] Convert `BbsVfxAddon` to `BBSAddon` with an API 2.0 descriptor.
- [x] Port forms and camera/action clips to V2 facades and check every `BBSRegistrationResult`.
- [x] Port settings to an independent V2 settings module and remove ordinal-based settings injection.
- [x] Port source pack override with `registerSourcePackFirst` and preserve asset namespace behavior.
- [x] Register keyframe factories or other missing structural hooks through the narrowest documented
      static extension and record why no V2 facade exists.
- [x] Compile common code and verify dedicated-server classloading boundaries.

## Phase 4: Client API And Mojmap Rendering

- [x] Convert client bootstrap to NeoForge/Mojmap types.
- [x] Replace Fabric client lifecycle/world callbacks with `BBSClientApi` where available.
- [x] Register custom shaders with `RegisterShadersEvent` and audit the bundled shader JSON/attribute contracts.
- [x] Rewrite `BbsVfxRenderCompat` as direct 1.21.1 Mojmap code.
- [x] Port buffer, render target, texture, matrix and vertex consumer calls.
- [x] Compile client code after each renderer family rather than batching all failures.

## Phase 5: Mixin Recovery

- [x] Remove registration Mixins already covered by V2.
- [x] Port BBS renderer, UI, form, smear and blend Mixins in small groups.
- [x] Port Minecraft rendering Mixins and verify refmap/target descriptors.
- [x] Isolate Iris/Sodium and Physics Mod integrations into optional configurations.
- [x] Launch a real NeoForge client after each Mixin group and require the expected startup marker.

## Phase 6: Functional Verification

- [x] Verify all six forms and both camera clips register and serialize.
- [x] Verify settings, language/source-pack registration and form picker/editor registration.
- [x] Guard Follow curve keyframe editing against stale graph state and empty sheets; reopening the
      editor clears the previous sheet before binding the live `progress` channel.
- [x] Guard navigation-ball axis release from failures in `snapToAxis()` and click-sound playback so a
      bad camera/audio state cannot crash the film editor.
- [x] Route VFX UI labels, section headers, dropdown choices, dynamic preset/actor/status labels through
      `BbsVfxUI.key(...)`; apply the complete Chinese map on `L10nReloadEvent` using BBS's resolved language
      key so Minecraft `zh_cn` is honored when BBS's language setting is blank. UI keys now retain their
      English fallback and reset stale Chinese content when switching back to `en_us`/`ru_ru`.
- [x] Add a final local failure boundary around Follow curve editor graph rebuilding; a stale UI hierarchy
      is logged and returned to the dope sheet instead of propagating through the film editor input path.
- [x] Remove the obsolete global `BBSShaders.getModel()` replacement. Normal model-form previews and
      world renders now retain BBSFSR's current model shader; smear dissolve keeps the shared texture-alpha
      path that already supports vanilla and Iris instead of an unused, stale `model_dissolve` copy.
- [ ] Smoke test smear, blend, labels, trails, destruction/PhysX and volume effects.
- [ ] Verify shader/depth/OIT/framebuffer passes with vanilla and Iris/Sodium profiles.
- [ ] Verify camera clips, AE/GLB export and video-related hooks.
- [x] Run release JAR inspection, `git diff --check`, compile, check and targeted tests.

## Validation Commands

```powershell
./gradlew.bat --no-daemon compileJava compileClientJava --console=plain
./gradlew.bat --no-daemon check --console=plain
./gradlew.bat --no-daemon build --console=plain
./gradlew.bat --no-daemon runClient -Pverify=1 --console=plain
git diff --check
```

The final gate must include a real client launch through resource reload. A successful Java
compile alone is insufficient evidence for Mixin or render correctness.

## Verification Record

- `compileJava compileClientJava`: passed with Java 21 and NeoForge 21.1.222.
- `check`: passed; project has no test sources (`test NO-SOURCE`).
- `build`: passed; release artifact is `build/libs/BBS-VFX-neoforge-1.21.1-1.2.0-all.jar`.
- Client launch: reached resource reload, `BBS VFX API 2.0 READY`, and
  `BbsVfxClient.onClientStarted`; no MixinApplyError, invalid injection, client-started failure,
  or missing addon shader uniform. Repeated the launch with `runs/client/options.txt` set to
  `lang:zh_cn`; both `assets:strings/zh_cn.json` and the BBS VFX runtime language subscriber loaded.
- Post-fix validation: `compileJava compileClientJava`, `check build`, and a fresh `runClient` all passed
  after the language fallback and Follow curve failure-boundary changes. The fresh run reached API READY
  and client initialization with no new crash report, injection error, or Follow curve open failure log.
- Model-form visibility fix: removed `BBSShadersDissolveMixin`, `SmearDissolveShader`, and all
  `model_dissolve` resources after confirming no Java path ever set its `Dissolve` uniform and the active
  texture-alpha implementation already owns dissolve for all forms. The current BBSFSR shader added
  object-space UI fog distance and `PassMode`; the stale addon copy used transformed UI distance and omitted
  the pass control, while globally intercepting every normal model preview. `compileJava compileClientJava`,
  `check build`, release-JAR inspection, and a fresh client launch passed. The client reached API 2.0 READY
  and `onClientStarted` with no shader/Mixin error. Final visual acceptance still needs opening the model-form
  picker and checking a normal model plus one dissolving smear in-world.
- BOBJ model visibility fix: BBSFSR 0.0.11 moved BOBJ vertex uploads from the old `updateMesh`
  wrapper into cached `updateGeometry(Matrix4f[])`. The old `BOBJSmearHideMixin` injection therefore
  matched zero targets and terminated the asynchronous BOBJ loader when `emoticons` was requested.
  The mixin now injects at the final `GL15.glBufferData` upload, forces a geometry refresh while
  hidden bones are active, and refreshes once more after the smear pass restores the original mesh.
  After rebuilding and replacing the run JAR, a default full-Mixin client reached API READY and
  loaded both `player/steve` and `emoticons/steve` without an injection error; manual verification
  confirmed both categories and the in-world actor render normally.
- Final artifact confirmation: the user repeated the model-picker and in-world check against the final
  all-JAR and reported normal behavior. The run-copy and `build/libs` artifact both have SHA-256
  `62356465C99BAACEB78C90C5EC93F84A136E02A63FBED382DA0DF78A54F9F9F5`; the subsequent client log
  reached API READY/client initialization without Mixin or injection errors, and created no new crash report.
- Final quality gate rerun: `check`, `build`, `git diff --check`, diagnostic-string scanning and
  release-JAR inspection passed. The all-JAR contains NeoForge metadata and both Mixin configs, and
  contains no Fabric metadata/namespace, embedded BBS core classes, stale `model_dissolve` resources,
  or temporary model diagnostics.
- Dedicated server launch: reached `DedicatedServer Done`; common-side API registration completed
  without loading client classes after moving the common-safe Mixin plugin into `src/main`.
- Release JAR audit: contains NeoForge metadata, both Mixin configs, API classes, PhysX Java
  Jar-in-Jar plus Windows loader/DLL resources; contains no Fabric metadata, Fabric namespace,
  or embedded `mchorse/bbs_mod/**` classes.
- PhysX release smoke: loaded the final all JAR plus its nested binding and successfully called
  `PxTopLevelFunctions.getPHYSICS_VERSION()` (`5.6.1`).
- Residual manual coverage: visual form/effect interaction, Iris/Sodium shaderpack behavior, and
  camera/export workflows still require a human smoke test. The client run also prints a recoverable
  BBSFSR first-run `categories.json` FileNotFoundException from its own settings initializer; it is
  outside this addon JAR and does not prevent API READY or client startup.
- User-reported click paths have not been driven by an automated mouse session in this environment;
  source-level guards and clean client launches are verified, but final acceptance still needs a human
  click on the lower-left X/Y/Z navigation axis and `Follow curve -> Edit offset keyframes`, followed by
  checking that no new crash report is created.
- Ground-crack camera anchoring fix: the migrated form stack is camera-relative while camera rotation
  remains in `RenderSystem.getModelViewMatrix()`. `CracksVolume.queue` now snapshots
  `modelView * formMatrix` before deriving its VIEW-space origin/up/tangent uniforms, matching the
  shader's inverse-projection depth reconstruction. `check` and `build` passed, the refreshed run JAR
  has SHA-256 `9F5C786AEB44095A710652517E25B64F806FBA7D427195540200EAEB689FBD03`, and a fresh client launch
  reached API READY/client initialization without a new crash report, Mixin failure, or crack-shader
  uniform error. Final visual acceptance still requires rotating and translating the camera around a
  fixed in-world crack effect.

## Rollback Points

- After mapped intermediate compiles.
- After NeoForge skeleton compiles.
- After common API 2.0 port.
- After each client renderer/Mixin group.
- Before any release artifact or shared repository staging.
