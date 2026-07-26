# Implementation plan

## 1. Establish reproducible inputs

- [ ] Locate the current NeoForge 1.21.1 test instance and record exact mod versions.
- [ ] Preserve the original `irlite-1.1.2+mc1.21.1.jar` and record its SHA-256.
- [ ] Identify the exact IRLite source baseline and matching `irl-core 1.1.2` input used by the
      failing JAR.
- [ ] If the exact source baseline is unavailable, inspect the original JAR metadata, nested
      core, Mixin config, and `WorldBlockChangeMixin` bytecode before selecting a replacement
      baseline.
- [ ] Confirm Java 21 and the Minecraft 1.21.1 Loom profile are used.

## 2. Apply the narrow IRLite patch

- [ ] Port only the `WorldBlockChangeMixin` behavior from upstream commit `f7e08e7a`.
- [ ] Target `ClientWorld.updateListeners(BlockPos, oldState, newState, int)` with the exact
      descriptor and `CallbackInfo`.
- [ ] Preserve the identity guard and existing `BlockShadowCache.invalidateChange` contract.
- [ ] Keep the baseline `irl-core` coordinate unless a separate dependency check proves an
      upgrade is required and reproducible.
- [ ] Give the compatibility artifact a distinct version or filename.

## 3. Build and inspect the artifact

- [ ] Run the IRLite clean build for `-Pmc=1.21.1` with Java 21.
- [ ] Confirm the output JAR contains the patched Mixin class and expected Mixin configs.
- [ ] Inspect the Mixin target/handler descriptors in bytecode or remapped output.
- [ ] Confirm exactly one expected `irl-core` nested JAR is present.
- [ ] Record the patched JAR SHA-256 and retain the build log.

## 4. Run the minimal Connector integration

- [ ] Reduce the instance to BBS FSR, Connector, Forgified Fabric API, patched IRLite, Iris,
      and required dependencies.
- [ ] Remove the old IRLite mapped artifacts from `mods/.connector/` before launch.
- [ ] Launch once and retain complete `debug.log` and `latest.log`.
- [ ] Verify Connector regenerated and loaded the patched mapped JAR.
- [ ] Verify all IRLite Mixins apply and the previous descriptor error is absent.
- [ ] Verify BBS common setup imports and registers the IRLite `bbs-addon` entrypoint.

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

