# Implementation plan

## 1. Establish reproducible inputs

- [x] Use the isolated `temp/irlite-connector-runtime` NeoForge 1.21.1 fixture and record
      its exact mod versions. The deleted launcher instance remains unavailable and was not
      modified or reconstructed.
- [ ] Preserve the original `irlite-1.1.2+mc1.21.1.jar` and record its SHA-256.
- [ ] Identify the exact IRLite source baseline and matching `irl-core 1.1.2` input used by the
      failing JAR.
- [ ] If the exact source baseline is unavailable, inspect the original JAR metadata, nested
      core, Mixin config, and `WorldBlockChangeMixin` bytecode before selecting a replacement
      baseline.
- [x] Confirm Java 21 and the Minecraft 1.21.1 Loom profile are used.

## 2. Apply the narrow IRLite patch

- [x] Port only the `WorldBlockChangeMixin` behavior from upstream commit `f7e08e7a`.
- [x] Target `ClientWorld.updateListeners(BlockPos, oldState, newState, int)` with the exact
      descriptor and `CallbackInfo`.
- [x] Preserve the identity guard and existing `BlockShadowCache.invalidateChange` contract.
- [ ] Keep the baseline `irl-core` coordinate unless a separate dependency check proves an
      upgrade is required and reproducible.
- [x] Give the compatibility artifact a distinct version or filename.

## 3. Build and inspect the artifact

- [x] Run the IRLite clean build for `-Pmc=1.21.1` with Java 21.
- [x] Confirm the output JAR contains the patched Mixin class and expected Mixin configs.
- [x] Inspect the Mixin target/handler descriptors in bytecode or remapped output.
- [x] Confirm exactly one expected `irl-core` nested JAR is present for the reproducible
      source build. It is `1.1.3`; the unavailable runtime baseline used `1.1.2`, so this
      remains a runtime-validation risk rather than evidence of an exact-baseline build.
- [x] Record the patched JAR SHA-256 and retain the build log.

## 4. Run the minimal Connector integration

- [x] Reduce the instance to BBS FSR, Connector, Forgified Fabric API, patched IRLite, Iris,
      and required dependencies.
- [x] Remove the old IRLite mapped artifacts from `mods/.connector/` before launch.
- [x] Launch once and retain complete `debug.log` and `latest.log`.
- [x] Verify Connector regenerated and loaded the patched mapped JAR.
- [x] Verify all IRLite Mixins apply and the previous descriptor error is absent.
- [x] Verify BBS common setup imports and registers the IRLite `bbs-addon` entrypoint.

## 5. Exercise runtime behavior

- [ ] Enter a world and open the BBS editor.
- [ ] Create or load point-light and spotlight Forms.
- [ ] Enable a supported Iris shaderpack and verify light rendering.
- [ ] Place, break, and modify blocks through relevant vanilla/BBS paths and verify shadow
      cache invalidation without crashes or stale shadows.
- [ ] Repeat the affected click, release, and scroll flows to cover the earlier BBS input fix.

## 6. Decide the next boundary

- [ ] If green, document the compatibility artifact and close this task without a native port.
- [ ] If another failure appears, capture the first failing Mixin/API boundary before changing
      code.
- [ ] Continue narrow compatibility patches only while failures remain isolated and testable.
- [ ] Create a separate native NeoForge migration task if multiple independent Minecraft/Iris
      core Mixins fail or Connector cannot provide stable runtime semantics.

## 7. Restore the BBS model-block item renderer

- [x] Replace the reflective block-entity load chain with direct parsing of
      `BLOCK_ENTITY_DATA.Properties` into the temporary entity properties.
- [x] Cache an item renderer entry only after the saved model data is restored successfully.
- [x] Synchronize the temporary entity's client world before rendering.
- [x] Add a focused regression for the no-world-first-load and later-world-render sequence.
- [x] Build the BBS 1.21.1 JAR and inspect the BEWLR/model/texture resources.

## 8. Fix IRLite beam strength and localization

- [x] Record the first evidence-backed beam-strength failure boundary from Form value through
      SSBO upload and generated Shader consumption.
- [x] Fix that boundary and add a regression that distinguishes default and non-default values.
- [x] Ensure stale marked Shader patches are re-patched when their contract/version is obsolete.
- [x] Add packaged `en_us` and `zh_cn` resources and register them with the four-argument
      addon source-pack lifecycle.
- [x] Replace user-visible constant labels in forms, settings, presets, Shader Patcher, debug,
      and option lists with IRLite translation keys.
- [x] Localize point-light and spotlight replay keyframe track titles, including nested Form paths.

## 9. Build and inspect fix3 artifacts

- [x] Run focused source tests and `compileClientJava` for BBS.
- [x] Build the BBS JAR with Java 21 and record its path/hash.
- [x] Build IRLite with `-Pmc=1.21.1`, version/file name `connector-fix3`, and record its hash.
- [x] Inspect IRLite for both language files, all declared Mixin configs, the patch templates,
      and exactly one expected nested `irl-core`.
- [x] Clear the disposable fixture's Connector mapping cache and retain a fresh launch log.
- [ ] Manually validate the model-block item, light Forms, beam-strength delta, Chinese panels,
      and replay keyframe tracks before closing the task.

## 10. Add the connector-fix4 beam texture protocol

- [x] Add a core-owned `R32F` texture with one marker texel and one encoded beam-strength texel
      per packed light, preserving `LightBuffer` ordering and lifecycle.
- [x] Register `irl_beamStrengths` through the existing Iris dynamic-sampler registry after all
      current core samplers.
- [x] Raise the patch contract to v2 and update all seven shader patches to use a sentinel-gated
      texture read with `vlParams.z` fallback.
- [x] Give the core and addon unique fix4 versions so Maven, nested-JAR, generated-pack, and
      Connector caches cannot substitute fix3 artifacts.
- [x] Extend resource verification to require the v4 marker, sampler uniform, sentinel,
      `texelFetch`, indexed helper call, and retained SSBO fallback in every patch.
- [x] Build and publish the core, build the addon for Minecraft 1.21.1 on Java 21, then inspect
      the outer and nested JAR contents and hashes.
- [x] Clear the disposable fixture's mapped IRLite cache, launch the fix4 artifact, and retain
      logs proving the new outer/core versions load without Mixin or shader-sampler errors.
- [x] Run a shader-enabled in-game matrix proving values `0`, `1`, and `10` for both point and
      spot lights from the selected freshly generated pack.
- [x] Exercise replay interpolation with dedicated changing-keyframe point/spot Films and require
      one stable packed index plus matching GPU readback and changing fixed-camera pixels.
- [ ] Keep the task open until the remaining manual editor/localization/model-block/shadow matrix
      is complete.

## 11. Fix the connector-fix5 texture upload state boundary

- [x] Separate the proven Iris sampler binding from the previously unverified R32F upload target.
- [x] Replace cached `GlStateManager._bindTexture` upload/restore calls with direct
      `GL11.glBindTexture` calls paired with the real queried binding.
- [x] Add opt-in GPU texture readback that compares marker and encoded light texels with CPU input.
- [x] Include both expected CPU bits and actual GPU bits in readback de-duplication so a stuck
      GPU value is still reported when the user changes `beam_strength`.
- [x] Build and publish core `1.1.3-connector-beamdiag2`, then build addon
      `1.1.2-connector-fix6` for Minecraft 1.21.1 on Java 21.
- [x] Inspect the remapped outer JAR, its single nested core, metadata, bytecode, and hashes.
- [x] Clear only the disposable fixture's prior IRLite mapping cache, launch Connector, enter the
      Photon world, verify zero-light readback and sampler binding, and shut down normally.
- [x] Run point and spot lights through `beam_strength = 0, 1, 10` and require matching
      form/registry/packed/texture-readback logs plus fixed-camera pixel differences.

## 12. Fix spotlight guide first-drag discontinuity

- [x] Compare the IRLite custom guide path with BBS FSR's generic film Gizmo and identify the
      first value-changing boundary.
- [x] Replace absolute range/angle writes with press-anchored relative deltas for both form and
      film keyframe edits.
- [x] Make the visible spotlight axis/cone use the same local-Z sign as its pick handles, drag
      math, and registered light direction.
- [x] Add a numeric regression proving zero mouse movement is identity and the first movement is
      continuous for range, outer angle, and inner angle.
- [x] Build a distinct fix8 IRLite artifact and inspect its Mixin/resources/single nested core.

## Validation references

- Crash evidence:
  `new/log/错误报告-2026-7-26_21.16.25/debug.log` around line 4068.
- Local pre-fix source:
  `temp/bbs-irlights-addon-1.21.1/bbs-irlights-addon-port-1.21.1/src/client/java/qualet/irlite/mixin/client/WorldBlockChangeMixin.java`.
- Upstream fix:
  `https://github.com/quaIett/bbs-irlights-addon/commit/f7e08e7a`.
- Prior BBS compatibility task:
  `.trellis/tasks/07-26-fix-bbs-connector-input-compatibility/`.

## Risk and rollback points

- A stale Connector mapped JAR can make a correct source patch appear ineffective.
- Upstream 1.1.4 currently references an unverified `irl-core 1.1.4`; do not turn a one-file
  Mixin fix into an unrelated dependency migration.
- Do not disable the required Mixin or swallow its injection failure as a final fix; that would
  leave shadow invalidation behavior missing.
- Preserve the original JAR and test in a disposable instance before touching the user's main
  instance.

## Build verification (2026-07-27)

- Java: Microsoft OpenJDK `21.0.11`.
- IRLite source baseline: current `build.gradle` is blob-identical to `f7e08e7a^`; the patched
  `WorldBlockChangeMixin.java` is blob-identical to `f7e08e7a`.
- Fabric BBS compile input: `bbs-fs@6a4d2fa19643a601981f18c22a2884983a08594b`, built with
  `-Pmod_version=2.2.1`; JAR SHA-256
  `BB1DCE152CE4A6064264F37E588737A6D33ABECF736A7B04BD115AEE2D70C4FE`.
- Public core input: `irl-core@3624676e80833ac8f6c8beb961adea339d261ceb`, version `1.1.3`;
  JAR SHA-256 `CBF7017F5E41C55B8798B58633E8EAC42FF8244F49C24DA1791C368DBC0B062B`.
- Output: `temp/bbs-irlights-addon-1.21.1/bbs-irlights-addon-port-1.21.1/build/libs/`
  `irlite-1.1.2-connector-fix1+mc1.21.1.jar`, 620,580 bytes, SHA-256
  `68A75F380ACECB8E98CEC6602E5F9D7EE94C64154E00AE467DA4DD42F3E8F311`.
- Bytecode: Java 21 class version 65; Mixin target `class_638` (`ClientWorld` / Connector
  `ClientLevel`) and injection target `method_8413` (`updateListeners` / Mojmap
  `sendBlockUpdated`); handler uses `(BlockPos, oldState, newState, int, CallbackInfo)`.
- Packaging: one nested JAR, `META-INF/jars/irl-core-1.1.3.jar`; its embedded bytes match the
  published local core hash above. No `irl-core-1.1.2` or duplicate nested core is packaged.
- Verification: clean online build passed, then an offline repeat build passed with the same
  output hash. Retained log: `research/build-2026-07-27.log`.
- Build-stage limitation: the crash report's original instance path no longer exists and the
  original outer JAR/core `1.1.2` artifact was not recoverable from the workspace or public
  Git history. The runtime section below resolves the Connector cache/startup gate; the
  in-game acceptance matrix remains pending.

## Runtime launch verification (2026-07-27)

- Fixture: `temp/irlite-connector-runtime`, NeoForge `21.1.235`, Connector
  `2.0.0-beta.15+1.21.1`, Forgified Fabric API `0.116.14+2.3.0+1.21.1`, BBS FSR
  `0.0.10`, patched IRLite, Iris `1.8.12`, and Sodium `0.6.13`.
- Connector clean input:
  `ECC41A0972FCB2234C78EE36E442CC34109C4B9D94ED0CE3D54265E8E37B988D`.
- The cache-cleared launch retained in
  `research/runtime-mixin-applied-fixture-invalid-2026-07-27.log` regenerated the mapped
  IRLite JAR before exposing the invalid empty-fixture mod state.
- After adding the low-code fixture descriptor, a second launch loaded the cached mapped JAR
  with SHA-256 `4B7E5E7BEF74D358434EA85EDCA3937A09062C9F815D5DD9ECC032FA0AB1F99D`.
- `WorldBlockChangeMixin` mixed into `ClientLevel` with the five-argument handler. No
  `InvalidInjectionException`, Mixin apply failure, broken mod state, or fatal error occurred.
- `BBSModMixin` applied to `BBSMod`; common setup imported `qualet.irlite.IrlightsAddon`,
  bridged it to the internal event bus, and posted `RegisterSettingsEvent` to both subscribers.
- The client completed sound, texture, and resource loading and reached a responsive normal
  Minecraft window. It was then closed normally; the log ends with `Stopping!`.
- Retained evidence: `research/runtime-validation-2026-07-27.md`,
  `research/runtime-success-debug-2026-07-27.log`, and
  `research/runtime-success-latest-2026-07-27.log`.
- Remaining gate: enter a world and manually validate the editor Forms, shaderpack path,
  block-shadow invalidation, and click/release/scroll flows. Do not close the task before that
  matrix passes.

## Fix4 runtime launch verification (2026-07-27)

- Connector regenerated and loaded mapped `1.1.2-connector-fix4` and nested
  `1.1.3-connector-beamtex1` artifacts from a cleared cache.
- Mapped outer SHA-256:
  `65C6ADF73113FFD03896A1201F1C58374D74CE54634B581F54FA23E893CEC348`.
- Mapped core SHA-256:
  `98D6D305ADA08B28C48565B7C6AABC90A6801BEB18EB87C4059B37C0BDA040C9`.
- `WorldBlockChangeMixin` applied to `ClientLevel`; BBS imported and bridged
  `qualet.irlite.IrlightsAddon`; no Connector, Mixin, IRLite, or sampler fatal error occurred.
- The client entered a singleplayer world, completed the BBS handshake, created the Iris
  overworld pipeline, and shut down normally. Gradle reported `BUILD SUCCESSFUL in 5m 47s`.
- Retained evidence: `research/runtime-fix4-validation.md`,
  `research/runtime-fix4-success-debug-2026-07-27.log`, and
  `research/runtime-fix4-success-latest-2026-07-27.log`.
- The fixture's Iris configuration has no selected shaderpack. This proves startup/publication,
  not beam-strength pixels; the `0/1/10` visual matrix remains the final gate.

## Fix6 build and runtime verification (2026-07-29)

- Core build: `clean build publishToMavenLocal` passed on Java 21. Output
  `irl-core-1.1.3-connector-beamdiag2.jar`, 177,807 bytes, SHA-256
  `4BD4808E4361BAC4ECA03563AD0584E6272FE2E0C760D83C3B2AF3B26B3CD0A5`.
- Addon build: `clean build -Pmc=1.21.1` passed, including `verifyIrliteResources` and remap.
  Output `irlite-1.1.2-connector-fix6+mc1.21.1.jar`, 655,143 bytes, SHA-256
  `3F3713272B455C94405076842209D06EC1F256A173BB5300BC8945EB76973948`.
- The outer JAR contains exactly one nested core named
  `META-INF/jars/irl-core-1.1.3-connector-beamdiag2.jar`; its embedded SHA-256 equals the
  published core hash. `javap` confirms the remapped core uses direct `GL11.glBindTexture`,
  `glGetTexImage`, and `textureReadBack` calls rather than `_bindTexture` for upload state.
- Connector regenerated and loaded mapped fix6/core beamdiag2 artifacts, entered `New World`,
  created Photon program 23 (`composite`), and logged
  `texture-readback texture=96 lights=0 marker=1937.25 encoded=[] matches=true`, followed by
  correct unit-12 binding and program execution. The client stopped normally and Gradle reported
  `BUILD SUCCESSFUL in 6m 32s`.
- This closes the texture-allocation/upload-target uncertainty only. The fixture Film still contains
  `bbs:sound_sphere`, so no IRLite light reached the registry; the user-facing `0/1/10` matrix is
  still required before declaring beam strength fixed.

## Fix7 final diagnostic artifact (2026-07-29)

- Quality review found that fix6 readback de-duplication hashed only actual GPU values. If an upload
  remained stuck while the expected CPU value changed, later mismatches could be suppressed. The
  hash now includes both expected and actual bits; the production GL upload path is unchanged.
- Final core: `irl-core-1.1.3-connector-beamdiag3.jar`, 177,829 bytes, SHA-256
  `376BE64834300404544175CCBAAA405DEDE0608471F4F75B7D6FE390C4D6FF74`.
- Final addon: `irlite-1.1.2-connector-fix7+mc1.21.1.jar`, 655,166 bytes, SHA-256
  `1EBD4F6117B69440651319ABD2A292950589A454A9BCD39E71EEAE54CB6543AB`.
- Both clean builds passed. The final outer JAR contains exactly one beamdiag3 core and its embedded
  SHA-256 matches the published core. The disposable fixture now has fix7 installed and all fix6
  mapped artifacts moved to `.irlite-backups/fix6-before-fix7-20260729-1418`.
- Fix7 was not relaunched because its only change from the successfully launched fix6 is opt-in log
  de-duplication. The next meaningful runtime gate is a real point/spot light matrix, not another
  zero-light startup.

## Fix8 build and beam-matrix verification (2026-07-29)

- The spotlight guide now uses press-anchored relative deltas and one `+Z` geometry convention.
  `SpotGuideDragContinuityTest` covers identity on zero movement, continuous first movement, and
  range/outer/inner clamps. The complete addon build passed on Java 21.
- Final addon: `irlite-1.1.2-connector-fix8+mc1.21.1.jar`, SHA-256
  `4E1AF04E286C1F8712B3505AB1C1A17C9958FFFBC18388BBD7FB59BA4384239E`.
- Connector loaded mapped outer/core artifacts with SHA-256
  `154E11B1C1085B345593FB621597CC94FC4C3DAD5C743CD3425B91C31108C232` and
  `4E0A6642E37865BB9C3AA47E23382DA9A7AC0E2A0E8119469F0349D7EA9C1D75`.
- The isolated fixture generated and round-tripped point/spot films for strengths `0`, `1`, and
  `10`. Every form/registry/packed/texture-write/readback stage matched the expected encoded
  values `1`, `2`, and `11`; Photon program `composite` registered, bound, and executed the sampler.
- The first spotlight screenshots were invalid because the cone pointed away behind the fixed
  camera. After placing the light one block behind the camera and matching its `180` degree yaw,
  the fixed ROI mean luminance progressed `95.890 -> 149.923 -> 220.699`. Point lights progressed
  `80.215 -> 180.930 -> 238.275`.
- The corrected spotlight run stopped once, saved the world, and Gradle reported
  `BUILD SUCCESSFUL in 2m 57s`. Full evidence and screenshot hashes are in
  `research/runtime-fix8-beam-matrix-validation.md`.
- Dedicated point/spot replay Films interpolated `beam_strength` from `0` through approximately
  `1` to `10`. Across 456 point samples and 534 spot samples the packed index remained `0`, every
  readback matched, and ROI luminance increased monotonically. Gradle reported
  `BUILD SUCCESSFUL in 2m 33s`.
- A final Java 21 `clean build -Pmc=1.21.1` passed in 51 seconds, including
  `testSpotGuideDragContinuity` and `verifyIrliteResources`. The rebuilt outer JAR retained the
  exact fix8 hash above and contains one nested beamdiag3 core with SHA-256
  `376BE64834300404544175CCBAAA405DEDE0608471F4F75B7D6FE390C4D6FF74`, both language files,
  the drag math class, the client Mixin config, and all seven `.irlights` patches.
- Result: Connector and NeoForge Iris do not lose `beam_strength` for point or spot Forms. Keep
  the compatibility route; do not start a native NeoForge IRLite migration for this defect.
