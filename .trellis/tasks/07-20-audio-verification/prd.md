# Audio Regression and Media Verification

## Goal

Create an executable verification gate for the FSR audio pipeline across `new/`
and `bbs-premiere-export/`. It must catch channel corruption, sample-rate and
duration drift, lifecycle leaks, false-success outcomes, and Premiere XML/media
mismatches before real-client smoke testing is considered complete.

## Scope And Constraints

- Verify only `MONO` and `STEREO`. A reserved 5.1 identifier must be rejected
  and never advertised as selectable or exportable. HRTF/binaural output is
  unavailable and out of scope.
- Keep the default automated gate deterministic and independent of microphone,
  OpenAL device, GPU, Minecraft client, Premiere Pro, FFmpeg, and `ffprobe`.
- Run real-media checks as an explicit strict-capable tier. Missing tools must
  be `SKIPPED` by default and fail when strict mode is requested.
- Preserve all existing uncommitted `bbs-premiere-export` changes. Do not weaken
  assertions merely because a sibling implementation has not landed yet.
- Follow the repository's dependency-light `JavaExec` test convention rather
  than adding a test framework solely for this child.

## Requirements

### Numeric Audio Contracts

- Golden fixtures cover mono/stereo PCM8, PCM16, signed PCM24, signed PCM32, and
  float32. Each supported input decodes with correct sign, frame boundaries,
  channel order, metadata, and duration, or returns a typed unsupported result.
- Left/right impulse and ramp fixtures retain channel identity through import,
  film mix, Minecraft mix, WAV output, and selected layout. Mono/stereo mapping
  is asserted against the documented matrix.
- 44.1 kHz and 48 kHz fixtures produce the exact contract frame count with no
  half-frame channel shift. Gain, limiting/clipping, and final quantization use
  declared tolerances and one master-boundary policy.
- Malformed, truncated, odd-sized, short-read WAV and truncated Vorbis inputs
  fail in a bounded, observable way without partial-success audio.

### Capture And Playback

- Fake capture covers open/start, polls, final drain, stop, cancel, interrupt,
  device/consumer failure, and repeated cleanup. Each device, buffer, thread,
  temporary file, and UI callback has exactly-once ownership.
- Cancel creates no clip or asset. Stop places the take at the captured timeline
  position and includes the final partial tick.
- Fake playback proves overlapping same-asset clips own independent voices while
  decoded buffers stay shared. Pause/resume/seek, remove/disable/relink, reload,
  disconnect, shutdown, and repeated deletion leave no stale resources.

### Export Lifecycle

- F4, F6, and panel-loop exports share one start/end range and align audio/video
  within one output frame. Cover F4 silence and F6 film/Minecraft sound paths.
- Mix, mux, rename, missing-resource, startup, nonzero exit, timeout, partial
  write, and cancel remain distinct outcomes. Requested audio failure cannot be
  unqualified `SUCCESS`; retained degraded video/WAV is explicit and recoverable.
- Strict media timeout cleanup uses a 750 ms process-teardown deadline inside
  the separate one-second return budget.
- Long/high-event mixes are block-based and cancellable with bounded work after
  cancellation and bounded working-set growth.

### Premiere And Real Media

- Individual WAV and XMEML agree on offset, clamped duration, sample rate, bit
  depth, channels, frame rate, and NTSC. Offset is applied exactly once.
- Cover mono/stereo, zero/positive/negative/out-of-range offsets, source-end
  clamp, disabled/missing/corrupt clips, volume, loop ranges, XML escaping,
  stale MP4s, and output failures.
- Media is bound to the current session, not directory mtime. XML/partial WAV
  failure is observable and never logged as unconditional success.
- Optional `ffmpeg`/`ffprobe` fixtures verify codec, 48 kHz rate, channels,
  duration, stream presence, and start/middle/end marker alignment within one
  video frame. Runs record tool versions and `PASS`/`FAIL`/`SKIPPED`.

## Acceptance Criteria

- [x] Hermetic verification passes without external tools and is wired into
      `new`'s `check` lifecycle through `testAudioFormat`, `testAudioLifecycle`,
      `testAudioVerification`, and the existing `testVideoExportUtils` suite.
- [x] `AudioFormatContractTest` and `AudioRendererMixerFocusedTest` prove
      supported PCM behavior, Mono/Stereo identity, and exact 44.1/48 kHz frame
      contracts or typed rejection.
- [x] `AudioLifecycleTest` proves final drain, cancel-without-save, independent
      voices, deferred UI dispatch, and exactly-once teardown.
- [x] `AudioExportLifecycleTest`, `VideoExportLifecycleContractTest`, and the
      strict real-media fixtures cover F4/F6/panel-loop range and outcome
      contracts. The strict three routed paths measured start/middle/end marker
      deltas of `0.000` frame.
- [x] `PremiereExportContractTest` structurally checks WAV/XMEML metadata,
      offsets, channels, duration, NTSC, loop, binding, duplicate completion,
      failure publication, and atomic package publication; the final add-on
      `check build` passed against the freshly rebuilt exact Core jar.
- [x] Strict `testAudioMediaIntegration` passes with FFmpeg/ffprobe 8.1.2;
      normal missing-tool mode reports `SKIPPED`, while strict mode is designed
      to fail with `TOOLS_UNAVAILABLE`.
- [x] Hermetic cancellation checks enforce the one-block bound and one-second
      terminal budget; both procedural and production ten-minute stereo gates
      enforce 8,192-frame blocks and less than 64 MiB working growth.
- [x] Core layout/media tests prove 5.1/HRTF unavailable, and the repaired
      Premiere add-on gate reconfirmed the generated Mono/Stereo WAV/XMEML
      contract without advertising a spatial layout.
- [x] Core compilation/focused tests/`check`, strict media verification, exact
      `BBS_FSR_beta_v0.0.10.jar` rebuild, Premiere `check build`, and final
      `git diff --check` all pass on the current cross-project snapshot.

The checkboxes record the latest evidenced state, not source inspection alone.
See `verification-record.md` for the three-state matrix and the exact pending
boundary.

## Out Of Scope

- Production fixes owned by sibling audio tasks.
- 5.1/7.1, HRTF/binaural, browser audio, OS loopback, or automated Premiere UI.
- Claiming real GPU/OpenAL/FFmpeg/Premiere success from mocks or source scans.

## Dependencies

- Consume sibling format/layout, range/result, mixer, capture/playback, export,
  and Premiere contracts after they stabilize.
- The runtime Core version is `2.3.2`; `bbs-premiere-export` declares the
  required Core range `[2.3.2,)` and resolves exactly
  `../new/build/libs/BBS_FSR_beta_v0.0.10.jar` at this task's verification
  baseline. Rebuild that exact artifact from the frozen source snapshot before
  running the addon gate. No older jar filename or compatibility alias is an
  active dependency, and no alternate/stale jar may satisfy the gate.
