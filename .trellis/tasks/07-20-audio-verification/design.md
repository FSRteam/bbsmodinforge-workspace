# Verification Design

## Test Tiers

This child owns verification code, fixtures, and build entry points only.

1. **Hermetic numeric:** deterministic PCM/WAV fixtures test format adapters,
   mixers, resampling, channel matrices, limiting, and WAV output.
2. **Hermetic lifecycle:** injected capture, playback, clock, dispatcher,
   filesystem, process, and resource backends prove ownership/results.
3. **Optional media:** real FFmpeg mux plus structured `ffprobe` JSON checks.
   Missing tools skip normally and fail under `-PrequireMediaTools=true`.
4. **Manual runtime:** real NeoForge/OpenAL/GPU and Premiere import. Automated
   tests never mark these passed.

The first two tiers join `new`'s default `check`. Media remains explicit. The
Premiere tier is separate because it consumes a built BBS jar in another Gradle
project.

## Fixtures

Use independent expected vectors embedded in the dependency-light test sources,
plus procedural fixtures written only to unique temporary directories. Expected
samples must not be generated through the production reader/writer path being
asserted.

- Mono/stereo PCM8/16/24/32 and float32 ramps at boundary/half/zero values.
- Left-only, right-only, and alternating impulses that expose channel swaps and
  half-frame offsets.
- 44.1/48 kHz one-second and fractional clips.
- Odd/padded RIFF chunks, truncated headers/data, short-read streams,
  unsupported tags, and truncated Vorbis.
- Zero, positive, negative, and source-end-clamped clip offsets.
- Synthetic video markers at start/middle/end for optional A/V probing.

Each fixture case records encoding, rate, layout, frame count, and tolerance.
Byte preservation is exact; specified conversions allow at most one destination
quantization LSB unless the sibling contract states a stricter bound.

## `new` Test Surface

The implemented surface uses three source sets and four executable Gradle tasks:

| Source set / launcher | Actual executable coverage |
|---|---|
| `audioFormatTest` / `testAudioFormat` | `AudioFormatContractTest`: PCM8/16/24/32/float32 golden data, WAV/Vorbis decode and rejection, padding/short reads/truncation, metadata, duration, limits, writer policy, cleanup, and reader ownership |
| `audioLifecycleTest` / `testAudioLifecycle` | `AudioLifecycleTest`: fake capture final drain/cancel/failure/interrupt/dispatcher/commit fencing and fake playback voice/buffer identity, transport, relink/removal, retry, and shutdown ownership |
| `audioVerificationTest` / `testAudioVerification` | `AudioVerificationContractTest`, which invokes `AudioLayoutAvailabilityTest` and `AudioProductionPerformanceTest` for typed cross-layer results, exactly-once delivery, no-replace artifact ownership, Mono/Stereo availability, cancellation, and the production ten-minute mix |
| existing `videoExportTest` / `testVideoExportUtils` | `VideoExportUtilsTest` transitively invokes `AudioRendererMixerFocusedTest`, `AudioImporterSupportTest`, `VideoMuxerTeardownContractTest`, `AudioExportLifecycleTest`, `VideoExportLifecycleContractTest` (including `MinecraftSoundCaptureContractTest`), `VideoExportArtifactIdentityTest`, and `VideoExportSessionTest` |

`new`'s `check` depends on all four tasks above. Assertions target stable
adapters and typed results; source inspection is used only where an integration
boundary cannot safely be instantiated by the dependency-light launcher.

## Premiere Test Surface

The add-on uses its existing `test` source set and one dependency-light
`testPremiereExportContract` JavaExec launcher for
`PremiereExportContractTest`. That executable currently covers:

- exact core dependency range, unavailable layout rejection, integer and
  1000/1001 rates, half-open offsets/clamps, reversed loops, and completion
  fencing;
- typed package failure/degraded retention policy and client-thread PCM
  snapshot ownership;
- real generated WAV projection for every supported encoding, Mono/Stereo
  channel mapping, volume, cleanup, missing/corrupt/omitted clips, and
  no-replace writes;
- secure DOM validation of Mono/Stereo XMEML, WAV metadata, channel topology,
  escaping, loop rebasing, exactly-once offsets, video-only F4, and atomic
  package publication.

`bbs-premiere-export`'s `check` depends on `testPremiereExportContract`. Because
`PremiereExportHandler` and the add-on source-set layout changed after the last
passing add-on execution, this tier is pending a final rerun against a freshly
built exact `BBS_FSR_beta_v0.0.10.jar`. The runtime Core version is `2.3.2`,
the add-on declares the required range `[2.3.2,)`, and no older jar name or
compatibility alias is accepted as an active dependency. Actual Premiere
application import remains a separate manual smoke and is not represented by
this automated gate.

## Real Media

Resolve `ffmpeg`/`ffprobe` from explicit properties, then environment/PATH.
Record versions, invoke `ffprobe -of json`, and parse it structurally. Assert one
video stream, exactly one requested audio stream, codec/profile, 48 kHz, one/two
channels, frame/sample duration, and marker alignment within one video frame.
Run Mono/Stereo, direct-film and post-Minecraft-sound mux paths, plus a nonzero
panel loop. A `-shortest` tail loss is acceptable only in a fixture explicitly
classified as intentional truncation.

The executable implementation is `AudioMediaIntegrationTest`, launched by
`testAudioMediaIntegration`. It writes independent WAV fixtures, probes JSON and
CSV structurally, validates Mono/Stereo output and typed process failures, and
checks three routed alignment fixtures: `direct-film` Mono/full,
`post-minecraft-sound` Stereo/full, and `direct-film` Stereo/nonzero
`panel-loop`. The latest strict FFmpeg/ffprobe 8.1.2 evidence reports `0.000`
frame at start, middle, and end for all three paths.

## Performance And Cancellation

The implemented gates combine `AudioVerificationContractTest`,
`AudioProductionPerformanceTest`, and the `--performance` branch of
`AudioMediaIntegrationTest`:

- blocks contain at most 8,192 frames;
- cancellation is checked at each boundary and commits at most one later block;
- a procedural ten-minute stereo/high-event mix grows at most 64 MiB after
  fixture load and reports monotonic progress;
- fake terminal delivery is bounded to 1 second; strict process teardown uses a
  750 ms teardown deadline and a 1-second return budget, with a 5-second version
  diagnostic timeout;
- worker callbacks reach UI/controller state only through the fake dispatcher.

Counters, not wall time, gate deterministic checks. Only the opt-in smoke uses
host timing.

## Resource And Compatibility Rules

Every fake device, native buffer, source, stream, process, channel, temp WAV,
and recovery artifact has an identity and close/delete counter. Tests preserve
existing user files, retain the first failure with later suppressed diagnostics,
and reject print-only/swallowed/unconditional-success behavior.

Legacy settings without layout use the parent default and old Mono assets load.
5.1/HRTF fixtures are negative only. Missing media tools or the exact addon BBS
jar produce explicit skip/block diagnostics, never a copied dependency or pass.
