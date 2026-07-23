# Audio Verification Contract

> Executable evidence contract for the FSR audio pipeline in `new/` and the
> Premiere addon in `bbs-premiere-export/`. A green launcher proves only the
> tier that actually ran; capability detection, skips, fakes, and source scans
> are never evidence for a real-media or real-runtime pass.

## 1. Scope / Trigger

Apply this contract whenever a change affects any of the following:

- PCM/WAV/Vorbis decoding, sample conversion, resampling, channel routing,
  limiting, or WAV publication;
- capture/playback ownership, cancellation, final drain, dispatch, or native
  resource teardown;
- F4, F6, panel-loop, film-audio, Minecraft-sound, mux, or export-result flow;
- FFmpeg/`ffprobe` discovery, invocation, probing, marker alignment, timeout,
  cleanup, or Gradle verification wiring;
- Premiere WAV/XMEML timing, session binding, package publication, or the local
  BBS core dependency;
- block sizing, cancellation latency, progress, or working-set behavior.

The positive delivery layouts are exactly `ChannelLayout.MONO` and
`ChannelLayout.STEREO`. `SURROUND_5_1` is a reserved negative case;
5.1, HRTF, binaural, and other spatial modes must not become selectable,
serializable, or encodable through verification changes.

This specification defines four independent evidence tiers:

1. hermetic numeric/lifecycle/export verification;
2. explicit real FFmpeg/`ffprobe` media verification;
3. hermetic Premiere addon contract verification against the exact rebuilt
   core jar;
4. manual real NeoForge/OpenAL/GPU/Premiere smoke verification.

Passing a lower tier never promotes a higher tier to `PASS`.

## 2. Signatures

### Gradle entry points

```text
new/check
  -> testAudioFormat
  -> testAudioLifecycle
  -> testAudioVerification
  -> testVideoExportUtils
  -> other existing project checks

new/testAudioVerification : JavaExec
  mainClass = mchorse.bbs_mod.audio.AudioVerificationContractTest
  requireMediaTools = false

new/testAudioMediaIntegration : JavaExec
  mainClass = mchorse.bbs_mod.audio.AudioMediaIntegrationTest
  properties = ffmpegPath?, ffprobePath?, requireMediaTools?,
               audioVerificationPerformance?

new runtime/release identity
  mod_version = 2.3.2
  final jar = new/build/libs/BBS_FSR_beta_v0.0.10.jar

bbs-premiere-export/verifyBbsCoreJar
  input = ../new/build/libs/BBS_FSR_beta_v0.0.10.jar
  required Core range = [2.3.2,)

bbs-premiere-export/testPremiereExportContract : JavaExec
  mainClass = mchorse.bbs_premiere_export.PremiereExportContractTest

bbs-premiere-export/check
  -> testPremiereExportContract
```

`testAudioMediaIntegration` is strict-capable, not implicitly strict. The
strict signature is `-PrequireMediaTools=true` (or `--strict` when invoking the
Java main directly). Omitting both allows a missing-tool `SKIPPED` result.

### Media launcher inputs

```text
AudioMediaIntegrationTest [--strict] [--performance]

Java system properties:
  ffmpegPath
  ffprobePath
  requireMediaTools = true | false
  audioVerificationPerformance = true | false

Environment fallback:
  ffmpeg:  FFMPEG_PATH, then FFMPEG, then PATH
  ffprobe: FFPROBE_PATH, then FFPROBE, then PATH
```

An explicit property has precedence over its environment fallbacks. A supplied
but invalid explicit path is unavailable; it must not silently become a
different executable from `PATH`.

```text
PROCESS_TEARDOWN_TIMEOUT = 750 ms
PROCESS_RETURN_TIMEOUT   = 1 second
TOOL_VERSION_TIMEOUT     = 5 seconds
```

The teardown deadline is nested inside the return budget; it is not a second,
additional wait.

### Production performance entry points

```java
static AudioRenderResult AudioRenderer.renderPreparedAudio(
    File file,
    List<AudioRenderer.PreparedFilmSource> preparedSources,
    int sampleRate,
    double start,
    double end,
    ChannelLayout layout,
    BooleanSupplier cancelled,
    BiConsumer<Long, Long> progress);

public static AudioRenderResult MinecraftSoundMixer.mixToFileResult(
    File file,
    List<MinecraftSoundCapture.CapturedSound> sounds,
    List<MinecraftSoundCapture.ListenerFrame> frames,
    Wave filmAudio,
    Map<ResourceLocation, Wave> resourceSnapshot,
    int sampleRate,
    double frameRate,
    int totalFrames,
    ChannelLayout layout,
    BooleanSupplier cancelled,
    BiConsumer<Long, Long> progress);
```

The performance acceptance gate must exercise these production renderer/mixer
paths through `AudioProductionPerformanceTest`; a standalone procedural loop
is supplemental evidence only.

### Observable terminal values

```text
AudioRenderResult.Status =
  SUCCESS | EMPTY | CANCELLED | UNSUPPORTED_FORMAT |
  MISSING_RESOURCE | IO_FAILURE | MIX_FAILURE

VideoExportResult.Kind includes =
  SUCCESS | DEGRADED | CANCELLED | AUDIO_FAILED |
  MUX_FAILED | PUBLISH_FAILED

AudioMediaIntegrationTest.MediaFailureKind =
  TOOLS_UNAVAILABLE | ARGUMENT_FAILURE | START_FAILURE | INTERRUPTED |
  TIMEOUT | NON_ZERO_EXIT | TERMINATION_FAILURE | INVALID_JSON |
  INVALID_CSV | CONTRACT_MISMATCH | OUTPUT_EXISTS | IO_FAILURE |
  CLEANUP_FAILURE
```

Do not collapse a typed non-success value into `SUCCESS` or a pass log.

## 3. Contracts

### Hermetic default gate

- `new/check` must remain deterministic without a microphone, OpenAL device,
  GPU, running Minecraft client, FFmpeg, `ffprobe`, or Premiere Pro.
- `testAudioVerification` must keep `requireMediaTools=false`. It executes
  cross-layer status/ownership checks, unavailable-layout checks, and the
  production performance/cancellation gate. Its media capability probe may
  print `MEDIA SKIPPED` or `MEDIA CAPABLE`, but it must not start either media
  tool.
- `MEDIA SKIPPED` is not `PASS`. `MEDIA CAPABLE` means only that executable
  files were found; it is not proof that a real-media fixture ran.
- A report may say `new/check: PASS` and `real media: SKIPPED` in the same run.
  It must not summarize that state as an unqualified media pass.
- Hermetic fixtures must independently assert PCM8, PCM16, signed PCM24,
  signed PCM32, and float32 behavior; mono/stereo channel identity; exact
  44.1/48 kHz frame boundaries; malformed/truncated/odd/short-read failures;
  and rejection of unsupported layouts before encoder arguments or media are
  produced.
- Fake capture/playback tests must prove final drain, cancel-without-commit,
  independent voices for overlapping same-asset clips, dispatcher ownership,
  and exactly-once cleanup. They prove lifecycle logic only, not a real OpenAL
  device.
- The audio/Premiere negative-path launchers owned by this contract must
  capture and assert their expected `ERROR` events by logger, count, message,
  and cause. A passing owned launcher must not emit or write unqualified
  expected-failure `ERROR` lines; global logger muting is forbidden because it
  would also hide unexpected production diagnostics.
- F4 silence, F6 direct-film and post-Minecraft-sound paths, and a nonzero
  panel loop must share one immutable half-open range. Requested audio failure
  must produce an explicit typed failure/degraded result; warning plus
  `SUCCESS` is forbidden.

### Production performance and cancellation

The default hermetic gate must run the following production-path assertions:

```text
sample_rate                  = 48000
layout                       = stereo
duration                     = 600 seconds
timeline_rate                = 20 frames/second
timeline_frames              = 12000
expected_audio_frames        = 28,800,000
high_event_count             = 4096 plus one continuous event
max_block_frames             = 8192
max_post_cancel_blocks       = 1
max_post_cancel_frames       = 8192
max_heap_growth_after_load   = 64 * 1024 * 1024 bytes
```

- Establish the heap baseline after procedural fixtures/events and resource
  snapshots are loaded and the production paths are warmed.
- Measure peak used heap at production progress callbacks. The inclusive
  contract is `peakUsedHeap - baselineUsedHeap <= 64 MiB`.
- Progress must be strictly monotonic, retain one positive total, finish at
  exactly 28,800,000 frames, and commit exactly
  `ceil(28,800,000 / 8192)` blocks.
- Every committed progress delta and
  `AudioRenderer.PcmBlockRenderer.BLOCK_FRAMES` must be `<= 8192` frames.
- Renderer and mixer cancellation probes request cancellation after the first
  committed block. From the instant the token becomes true, each operation may
  commit at most one additional block and at most 8,192 additional frames.
- A cancelled operation returns `CANCELLED`, publishes no destination WAV, and
  leaves no owned temporary WAV.
- The ten-minute output must have the exact PCM16 stereo WAV size
  `44 + 28,800,000 * 2 * 2` bytes.
- `AudioMediaIntegrationTest --performance` exercises a procedural marker and
  cannot replace the production-entry assertions above.

### Explicit real-media gate

- Resolve both tools, execute `<tool> -version`, and record the normalized
  executable source and recognizable version line before accepting media
  evidence.
- In non-strict mode, either missing tool yields `SKIPPED` and a zero launcher
  exit. The media tier remains `SKIPPED`, never `PASS`.
- In strict mode, either missing/invalid tool yields authoritative task
  `FAIL` with `TOOLS_UNAVAILABLE`. The launcher currently prints the missing
  diagnostic containing `SKIPPED` before throwing; reporters must use the
  nonzero task result as the final `FAIL` state.
- A real-media pass requires real processes and structurally parsed `ffprobe`
  JSON. It must prove exactly one requested audio stream and one video stream,
  AAC-LC audio at 48 kHz, the requested one/two channels and layout, bounded
  durations/start-time drift, and retained stereo identity.
- Real decoded start/middle/end markers are mandatory for all three fixtures:

| Fixture | Routed path | Layout/range | Source markers | Required output evidence |
|---|---|---|---|---|
| Direct film | `direct-film` | mono, full range | frames `0`, `6`, `11` | video luma and audio RMS peaks at start/middle/end |
| Film plus Minecraft sound | `post-minecraft-sound` | stereo, full range | frames `0`, `6`, `11` | film markers remain in the left channel and the Minecraft bed remains in the right channel |
| Nonzero panel loop | `direct-film` | stereo, `panel-loop`, source range `6..18` | source frames `6`, `12`, `17`, rebased to output `0`, `6`, `11` | trimmed audio/video retain the same rebased markers |

  Each decoded audio/video marker delta must be at most one 24 fps video frame.
  Source construction, filenames, argument inspection, or fake probes do not
  satisfy this requirement. A valid run emits three `MARKERS PASS` records and
  the final media `PASS` record.
- Nonzero process exit, timeout, invalid probe output, contract mismatch,
  output collision, or cleanup failure remains a typed `FAIL`. Partial files
  must not be published as successful artifacts.

### Premiere addon and exact core jar

The addon dependency is exact and local:

```text
bbs-premiere-export/gradle.properties: bbs_version=2.3.2
bbs-premiere-export metadata: versionRange=[2.3.2,)
resolved core jar: ../new/build/libs/BBS_FSR_beta_v0.0.10.jar
```

- Rebuild `new/build/libs/BBS_FSR_beta_v0.0.10.jar` from the current/frozen `new` source
  before any addon compile or test. Do not copy, rename, substitute, or silently
  reuse another core artifact.
- This exact filename is the only active final artifact identity. An older jar
  filename or compatibility alias must not be retained or resolved as an
  active fallback, even if such a stale build output exists on disk.
- `verifyBbsCoreJar` must fail when the exact path is missing, older than the
  newest `new/src/main/java` or `new/src/client/java` source, or lacks any of:
  `ChannelLayout.class`, `VideoExportArtifact.class`,
  `VideoExportResult.class`, `WorldVideoExportListener.class`, or
  `WorldVideoExportSnapshot.class` at their declared package paths.
- Missing, stale, or API-incompatible core input is `BLOCKED` for addon
  verification until `new:jar` is rebuilt successfully. It is not `SKIPPED`
  and cannot be repaired by weakening addon assertions.
- `PremiereExportContractTest` must use real generated WAV bytes plus securely
  parsed XMEML to compare half-open range, offset exactly once, source clamp,
  sample rate, bit depth, channel count/routing, duration, rational frame rate,
  NTSC, XML escaping, loop rebasing, session fencing, and atomic publication.
- `PremiereExportContractTest passed` proves the addon contract only. It does
  not prove that Adobe Premiere Pro imported the package.

### Real runtime evidence boundary

The following remain separate manual smoke states:

```text
real NeoForge client startup and export flow
real OpenAL capture/playback device lifecycle
real GPU-backed video rendering/export
real Adobe Premiere Pro WAV/XMEML import and timeline inspection
```

They may be marked `PASS` only when the named real component was exercised and
the run record identifies the environment/version, scenario, produced
artifact, and observed result. Fakes, mocks, source scans, a headless JVM,
FFmpeg success, or addon XMEML parsing must leave the corresponding real smoke
as `NOT RUN`/`PENDING`, never `PASS`.

## 4. Validation & Error Matrix

| Condition | Authoritative result | Required behavior |
|---|---|---|
| `new/check` runs with no external media tools | Hermetic `PASS`; media `SKIPPED` | Complete numeric/lifecycle/export/performance assertions and report media separately |
| Default capability check finds both tool files | Hermetic result only | `MEDIA CAPABLE` may be logged; do not claim a real-media pass |
| Explicit media task, non-strict, one or both tools missing | `SKIPPED` | Name each resolved/missing tool; exit normally; never relabel as `PASS` |
| Strict media task, one or both tools missing/invalid | `FAIL` / `TOOLS_UNAVAILABLE` | Preserve diagnostic and nonzero task failure even if a preceding line says `SKIPPED` |
| Tool version command fails or version is unrecognizable | `FAIL` | Retain typed process/contract diagnostic; no media pass |
| FFmpeg start, nonzero exit, timeout, or forced termination fails | `FAIL` with matching failure kind | Bound captured output, clean descendants/partials, and retain the first failure |
| An owned process remains alive at the 750 ms teardown deadline | `FAIL` / `TERMINATION_FAILURE`, suppressed under the initiating `TIMEOUT` when applicable | Stop publication, retain bounded diagnostics, and clean descendants/partials |
| A timeout probe returns after its separate one-second budget | Gate `FAIL` | Report measured return latency; do not publish partial output |
| `ffprobe` JSON/CSV is malformed or required fields/streams differ | `FAIL` | Parse structurally; never accept text-presence or source-scan evidence |
| Any direct-film, post-Minecraft, or panel-loop marker is absent or over one frame apart | `FAIL` / `CONTRACT_MISMATCH` | Identify fixture and marker; do not print final media `PASS` |
| Production block exceeds 8,192 frames | Hermetic `FAIL` | Fail the production performance gate |
| More than one block or 8,192 frames commit after cancellation | Hermetic `FAIL` | Return cancellation without publishing output or temporary files |
| Ten-minute production mix grows more than 64 MiB after fixture load | Hermetic `FAIL` | Report measured growth; do not substitute the procedural marker |
| Ten-minute frame count, progress total, block count, or WAV size differs | Hermetic `FAIL` | Report the exact observed counter/size |
| Requested audio fails but result is unqualified `SUCCESS` | Hermetic `FAIL` | Use the appropriate typed degraded/failure result and recovery ownership |
| An owned audio/Premiere negative-path launcher leaks an unqualified `ERROR` line | Hermetic/add-on `FAIL` | Capture the expected logger events, assert the exact known diagnostics, and fail on any extra or unrecognized error |
| 5.1/HRTF is selectable, serialized, or reaches encoder arguments | Hermetic/addon `FAIL` | Reject before production and publish no media |
| Exact `BBS_FSR_beta_v0.0.10.jar` is missing, stale, or API-incompatible | Addon `BLOCKED` (Gradle failure) | Rebuild `new:jar`, then rerun addon verification |
| Addon contract passes but Premiere Pro was not opened | Addon `PASS`; Premiere smoke `NOT RUN` | Keep the two evidence states separate |
| A fake is offered as NeoForge/OpenAL/GPU/Premiere evidence | Invalid report | Reject the claim and retain the real smoke as `NOT RUN`/`PENDING` |
| A real manual smoke violates the scenario | Manual `FAIL` | Record component/version, artifact, and observed failure |

## 5. Good / Base / Bad Cases

- Good: `new/check` passes hermetically, including the ten-minute production
  mixer; strict media resolves and versions both tools, emits all three real
  marker records, and passes; the exact core jar is rebuilt before the addon
  contract passes; each real runtime smoke is reported from its own evidence.
- Base: only the default gate is run on a host without media tools. Report
  `hermetic: PASS`, `real media: SKIPPED`, `addon: NOT RUN`, and all real
  runtime smokes as `NOT RUN`. This is a valid partial verification report, not
  a full-stack pass.
- Bad: treat a zero exit from non-strict `testAudioMediaIntegration` with
  missing tools as media `PASS`.
- Bad: use the supplemental procedural ten-minute loop to claim the production
  renderer/mixer stayed within 64 MiB.
- Bad: accept marker fixture names or generated source values without decoding
  the actual muxed media and comparing audio/video peaks.
- Bad: compile the addon against a previously existing or renamed jar, then
  describe `PremiereExportContractTest passed` as a real Premiere import pass.
- Bad: weaken an F4/F6/panel, layout, cancellation, or XMEML assertion because
  a sibling implementation is incomplete. Preserve the exact blocker instead.

## 6. Tests Required

### Hermetic core gate

```powershell
cd new
.\gradlew.bat --no-daemon testAudioFormat testAudioLifecycle `
  testAudioVerification testVideoExportUtils
.\gradlew.bat --no-daemon check
```

Required assertion points:

- `AudioFormatContractTest` completes supported PCM/WAV/Vorbis, exact frame,
  channel, malformed-input, allocation-limit, and writer-ownership assertions.
- `AudioLifecycleTest` completes fake capture/playback tail, cancellation,
  dispatcher, independent voice, and exactly-once teardown assertions.
- `AudioLifecycleTest` captures exactly 25 expected recorder/playback failure
  diagnostics; `testVideoExportUtils` captures exactly 16 expected export
  lifecycle diagnostics; neither launcher writes those events to the normal
  console/file appenders.
- `AudioVerificationContractTest` completes typed result/layout/artifact gates
  and invokes `AudioProductionPerformanceTest` through the production entry
  points.
- The production run reaches 28,800,000 frames, uses deltas no larger than
  8,192, stays at or below 64 MiB heap growth after load, and proves both
  renderer and mixer post-cancel work is at most one block.
- `testVideoExportUtils` runs the F4/F6/panel-loop and export lifecycle suites;
  ranges, branches, terminal kinds, retained recovery artifacts, and
  no-replace ownership remain explicit.
- Missing media tools appear only as `MEDIA SKIPPED`; they do not fail this
  hermetic gate and are not recorded as a media pass.

### Strict real-media gate

```powershell
cd new
.\gradlew.bat --no-daemon testAudioMediaIntegration `
  -PffmpegPath="C:\path\to\ffmpeg.exe" `
  -PffprobePath="C:\path\to\ffprobe.exe" `
  -PrequireMediaTools=true
```

Required assertion points:

- both `TOOL` records include executable source and recognizable versions;
- WAV and MP4 stream metadata, codec/profile, 48 kHz delivery rate,
  mono/stereo layout, duration, start time, and stereo identity pass;
- decoded `MARKERS PASS` evidence exists for `direct-film/full`,
  `post-minecraft-sound/full`, and `direct-film/panel-loop` with start, middle,
  and end deltas each `<= 1.000` frame;
- typed rejected-output, nonzero-exit, and timeout probes pass without
  publishing partial output;
- timeout/termination probes enforce the 750 ms process-teardown deadline
  inside the one-second return budget;
- the launcher emits its final media `PASS` only after every assertion and
  workspace cleanup succeeds.

Also run a strict negative prerequisite test on a controlled host/path: an
invalid or missing tool must make the Gradle task fail with
`TOOLS_UNAVAILABLE`. Seeing only the preceding `SKIPPED` line is insufficient.

### Exact-jar addon gate

Run in this order; never begin with the addon task:

```powershell
cd new
.\gradlew.bat --no-daemon jar
Test-Path .\build\libs\BBS_FSR_beta_v0.0.10.jar

cd ..\bbs-premiere-export
.\gradlew.bat --no-daemon verifyBbsCoreJar testPremiereExportContract
.\gradlew.bat --no-daemon check
```

Required assertion points:

- the resolved dependency is exactly `../new/build/libs/BBS_FSR_beta_v0.0.10.jar`, is
  newer than current core source, and contains every required export API class;
- Core reports runtime version `2.3.2`, Premiere declares `[2.3.2,)`, and no
  older jar filename or compatibility alias is used as an active dependency;
- real generated mono/stereo WAV metadata equals its descriptors and securely
  parsed XMEML;
- the handler contract captures exactly nine expected failed-package result
  diagnostics and rejects any additional/unrecognized handler error without
  leaking the known failures into a passing build log;
- offsets/clamps are applied once, loop ranges are rebased, session/stale-media
  fencing holds, failures do not announce success, and package publication is
  atomic/no-replace;
- the result is recorded as an addon contract result, not a Premiere Pro smoke.

### Manual smoke record

For each real NeoForge, OpenAL, GPU, and Premiere scenario, record at minimum:

```text
component and version/device
host/runtime configuration
exact scenario and requested Mono/Stereo layout
input session/range and produced artifact identity
observed channel/timing/lifecycle result
PASS | FAIL | NOT RUN, with evidence location
```

No automated launcher in this contract supplies those four manual `PASS`
records.

## 7. Wrong vs Correct

### Media status

Wrong:

```text
AudioMediaIntegrationTest: SKIPPED missing media tools
Overall audio/media verification: PASS
```

Correct:

```text
Hermetic audio verification: PASS
Real FFmpeg/ffprobe media verification: SKIPPED (tools unavailable)
```

In strict mode, the correct final state for the same missing tools is `FAIL /
TOOLS_UNAVAILABLE`, regardless of the diagnostic line printed before the
exception.

### Performance evidence

Wrong:

```java
runProceduralTenMinuteLoop(); // claim production mixer <= 64 MiB
```

Correct:

```java
AudioRenderer.renderPreparedAudio(/* production prepared sources, cancellation/progress */);
MinecraftSoundMixer.mixToFileResult(/* production resource snapshot, cancellation/progress */);
// Assert <= 8192 frames/block, <= one post-cancel block, and <= 64 MiB growth.
```

### Addon ordering and smoke claims

Wrong:

```powershell
cd bbs-premiere-export
.\gradlew.bat testPremiereExportContract  # silently reuse an old core jar
```

```text
Fake OpenAL lifecycle + parsed XMEML passed, therefore OpenAL/GPU/Premiere passed.
```

Correct:

```powershell
cd new
.\gradlew.bat --no-daemon jar
cd ..\bbs-premiere-export
.\gradlew.bat --no-daemon verifyBbsCoreJar testPremiereExportContract
```

```text
Fake lifecycle: PASS; addon WAV/XMEML contract: PASS;
real OpenAL: NOT RUN; real GPU: NOT RUN; real Premiere import: NOT RUN.
```

### Negative-path logs

Wrong:

```text
[main/ERROR] fake FFmpeg startup failure
BUILD SUCCESSFUL
```

Correct:

```text
VideoExportUtilsTest passed; captured 16 expected failure diagnostics
```

The capture must fail the test when the count, logger, message, or cause is not
part of the explicit expected set; it is not a blanket logging suppression.
