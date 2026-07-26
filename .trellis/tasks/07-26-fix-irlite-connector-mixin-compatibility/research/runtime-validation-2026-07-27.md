# Corrected Connector runtime validation

Date: 2026-07-27

## Result

The isolated NeoForge development fixture reached the normal Minecraft client window without
a fatal error. The IRLite Connector patch passed its automated runtime boundary: Connector
loaded the mapped artifact, `WorldBlockChangeMixin` applied to `ClientLevel`, BBS common setup
imported and bridged the Fabric `bbs-addon`, and client resource loading completed.

This result does not complete the task. World entry, BBS editor Forms, an enabled Iris
shaderpack, block-shadow invalidation, and input flows still require manual verification.

## Fixture

Path: `temp/irlite-connector-runtime`

| Component | Version |
| --- | --- |
| NeoForge | `21.1.235` |
| Sinytra Connector | `2.0.0-beta.15+1.21.1` |
| Forgified Fabric API | `0.116.14+2.3.0+1.21.1` |
| BBS FSR | `0.0.10` |
| IRLite | `1.1.2-connector-fix1+mc1.21.1` |
| Nested IR core | `1.1.3` |
| Iris | `1.8.12` |
| Sodium | `0.6.13` |

The user launcher instance was not used. The fixture source set contains an empty low-code
`runtime_fixture` descriptor so NeoForge does not mark the global mod state broken.

## Artifact identity

- Clean Minecraft input SHA-256:
  `ECC41A0972FCB2234C78EE36E442CC34109C4B9D94ED0CE3D54265E8E37B988D`.
- Patched source JAR SHA-256:
  `68A75F380ACECB8E98CEC6602E5F9D7EE94C64154E00AE467DA4DD42F3E8F311`.
- Connector mapped JAR SHA-256:
  `4B7E5E7BEF74D358434EA85EDCA3937A09062C9F815D5DD9ECC032FA0AB1F99D`.
- The prior cache-cleared launch regenerated this mapped JAR. The corrected launch reused it
  from `runs/client/mods/.connector/` and loaded both IRLite and nested IR core.

## Runtime evidence

Evidence file: `runtime-success-debug-2026-07-27.log`.

- Lines 655-656 discover the mapped IR core and IRLite files; lines 899-911 load them from
  Connector's cache.
- Lines 4204-4214 apply `WorldBlockChangeMixin` to `ClientLevel` and accept the handler
  `(BlockPos, BlockState, BlockState, int, CallbackInfo)`.
- Lines 4422-4426 apply IRLite's legacy `BBSModMixin` to `BBSMod`.
- Lines 5600-5601 import one Fabric Loader `bbs-addon` candidate with no rejection or failure.
- Lines 5616-5627 show common setup collecting `irlite`, bridging
  `qualet.irlite.IrlightsAddon`, and subscribing its settings handler.
- Lines 5671-5672 post `RegisterSettingsEvent` to two subscribers without an exception.
- Lines 5835-5888 complete OpenAL, sound, texture, GUI atlas, and animation resource loading.
- Line 5890 is the requested normal shutdown marker, `Stopping!`.

Searches found no `InvalidInjectionException`, IRLite Mixin apply failure, invalid fixture mod,
broken mod state, refused lifecycle event, reported exception, or new crash report. The only
ERROR-level line is an early-display `glfwInit` duration warning; it is non-fatal and the
client continues through complete resource loading.

## Retained logs

| File | Size | SHA-256 |
| --- | ---: | --- |
| `runtime-success-debug-2026-07-27.log` | 1,293,878 bytes | `2A91FEAA208098D37F84923F09306AC141FE0AF3C6B849F034E79E0A292B4FAC` |
| `runtime-success-latest-2026-07-27.log` | 50,380 bytes | `26889980DFA6B8496E40CE4EE23DDC2812B371772605D91BABAA3A3949595135` |

## Remaining manual matrix

- Enter a world and open the BBS editor.
- Create or load point-light and spotlight Forms.
- Enable a supported Iris shaderpack and verify both light types render.
- Place, break, and modify blocks through vanilla and BBS paths; verify shadow refresh without
  stale data or crashes.
- Repeat the click, release, and scroll flows covered by the earlier BBS input patch.

Until this matrix passes, retain the `irl-core 1.1.2` to `1.1.3` difference as residual risk
and keep the task `in_progress`.
