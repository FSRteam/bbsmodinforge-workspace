# Audio Verification Record

Updated: 2026-07-21 (Asia/Shanghai)

## Current Release Baseline

- Runtime Core is version `2.3.2` and its final artifact identity is exactly
  `new/build/libs/BBS_FSR_beta_v0.0.10.jar`.
- Premiere declares the required Core range `[2.3.2,)` and resolves only that
  exact artifact. An older jar filename or compatibility alias is not an
  active dependency and cannot satisfy `verifyBbsCoreJar`.
- Strict process teardown has a 750 ms deadline inside the separate one-second
  return budget.

The automated matrix below records the final executions for this source
snapshot. Manual application/device rows remain separate and unchanged.

## State Rules

- **已通过**: an executable gate has passing output for the relevant snapshot,
  or the row explicitly records an independently reusable result.
- **待最终复跑**: the gate passed earlier or its source was reviewed, but files
  inside that gate changed afterward; it is not completion evidence yet.
- **实体未执行**: a real external application/device smoke was not run. Source
  inspection and fakes never upgrade this state.

## Verification Matrix

| State | Gate / executable | Evidence and boundary |
|---|---|---|
| 已通过 | Core hermetic gate: `new` `check` | A forced `--rerun-tasks build` completed with `BUILD SUCCESSFUL`; the wired audio entries are `testAudioFormat`, `testAudioLifecycle`, `testAudioVerification`, and `testVideoExportUtils`. All expected-failure launchers now capture declared diagnostics, and the run plus its generated log archives contained zero `/ERROR]` records. |
| 已通过 | `AudioFormatContractTest` via `testAudioFormat` | Actual format executable covers PCM8/16/24/32/float32, WAV/Vorbis, Mono/Stereo, malformed/partial input, limits, writer cleanup, and ownership. Included in the passing core `check`. |
| 已通过 | `AudioLifecycleTest` via `testAudioLifecycle` | Actual fake capture/playback executable covers final drain, cancel without commit, failure/interrupt, dispatcher ordering, independent voices, and exactly-once cleanup. It captures and verifies exactly 25 expected recorder/playback failure diagnostics instead of leaking unqualified `ERROR` lines. Included in the passing core `check`. |
| 已通过 | `AudioVerificationContractTest` via `testAudioVerification` | Actual launcher includes `AudioLayoutAvailabilityTest` and `AudioProductionPerformanceTest`; typed results, one-second fake terminal budget, no-replace ownership, Mono/Stereo-only output, production cancellation, and production performance are in the passing core `check`. |
| 已通过 | Export/mix regression graph via `testVideoExportUtils` | `VideoExportUtilsTest` invokes the actual mixer/import/mux/export/session suites, including `AudioRendererMixerFocusedTest`, `AudioExportLifecycleTest`, `VideoExportLifecycleContractTest`, and `MinecraftSoundCaptureContractTest`. It captures and verifies exactly 16 expected export failure diagnostics without polluting a passing build log. Included in the passing core `check`. |
| 已通过 | Strict real media: `testAudioMediaIntegration` | FFmpeg `8.1.2-essentials_build-www.gyan.dev` and ffprobe 8.1.2 were resolved through explicit paths; Mono/Stereo WAV/MP4 contracts and the strict task passed. |
| 已通过 | Strict marker alignment, three routed paths | `direct-film` / Mono / full `0..12`, `post-minecraft-sound` / Stereo / full `0..12`, and `direct-film` / Stereo / nonzero `panel-loop` `6..18` each reported start, middle, and end delta `0.000` frame. |
| 已通过 | Ten-minute performance | Procedural output: 600 s, 48 kHz Stereo, 8,192-frame blocks, 3,516 blocks, `peak_bytes=98304`, below 64 MiB. The production ten-minute/high-event mix and cancellation gates also run through `AudioProductionPerformanceTest` in core `check`. |
| 已通过 | Exact core artifact for add-on | `new/build/libs/BBS_FSR_beta_v0.0.10.jar` was rewritten by the forced full build at `2026-07-22 01:31:26`; SHA-256 is `298CA4C859FC9DB614F63B4FD420E4D21818B9C4AF6BF60DAC26C8B50DCD12B8`, embedded NeoForge mod version is `2.3.2`, all five required typed API entries exist, and the artifact is newer than all Core Java sources. No older runnable JAR alias is retained; the separately named sources archive is non-runtime build output. |
| 已通过 | Premiere add-on: `testPremiereExportContract` and add-on `check build` | The final repaired snapshot passed `testPremiereExportContract`, including `aac_low`/AAC-LC normalization, serialized one-frame start tolerance, direct exactly-once external add-on registration, handler duplicate-completion, and publication-failure regressions. The handler gate captures and validates exactly nine expected failed-package diagnostics without leaking `ERROR`; the broader add-on `check build` then passed against the exact fresh Core jar. |
| 已通过 | Final cross-project whitespace gate | `git diff --check` returned zero in both `new` and `bbs-premiere-export` after the final add-on gate; output contained only line-ending conversion warnings. |
| 实体未执行 | Real Minecraft/OpenAL/GPU/client smoke | No real device/client run is claimed. Hermetic fakes and process tests are the automated evidence. |
| 实体未执行 | Premiere Pro import smoke | No actual Premiere Pro import/UI smoke is claimed. DOM/XMEML/WAV contract tests do not substitute for opening the package in Premiere. |

## Final Serial Rerun

1. `new`: focused audio/export tasks and full `check` passed.
2. `new`: strict `testAudioMediaIntegration` passed with FFmpeg/ffprobe 8.1.2
   and the performance flag enabled.
3. `new`: the exact Core jar was force-rebuilt and inspected as recorded above.
4. `bbs-premiere-export`: forced compile/contract execution reconfirmed the
   media boundary after AAC profile, start-time epsilon, and direct add-on
   registration fixes; the rerun and final `check build` passed.
5. Final `git diff --check` passed in both Git packages.

Do not downgrade a missing/stale core jar to a skip, and do not mark either
entity smoke as passed without actually running the named application/device.
