# Audio Format Contract Implementation Plan

## 1. Establish Focused Tests

- [ ] Add an `audioFormatTest` source set and executable test task following the
      repository's existing plain-Java harnesses.
- [ ] Add byte-built RIFF fixtures for PCM U8/S16/S24/S32, IEEE float32,
      mono/stereo impulses, odd chunk sizes, partial reads, malformed sizes,
      and WAVE_FORMAT_EXTENSIBLE cases.
- [ ] Capture current compatibility expectations for legacy `Wave`
      constructors, `convertTo16()`, and `excerptMono()` before replacing logic.

Gate: focused fixtures compile and fail against the known broken behavior for
24/32-bit conversion, stereo excerpting, short reads, and padding.

## 2. Add Explicit Format Types

- [ ] Add `PcmEncoding`, `ChannelLayout`, and `PcmFormat` under the main audio
      package with stable persisted IDs.
- [ ] Implement Mono/Stereo as supported layouts and recognize 5.1 only as an
      unsupported reserved ID. Do not expose HRTF or 5.1 options.
- [ ] Add the single export-layout normalizer: missing/blank/invalid/5.1 falls
      back to Mono with a bounded diagnostic; explicit Stereo remains Stereo.
- [ ] Add checked frame/byte-rate/size calculations and production/test decode
      limits.

Gate: format/normalizer/overflow unit tests pass.

## 3. Make `Wave` Frame-Aware

- [ ] Add validated `PcmFormat` construction/projection and frame-count access.
- [ ] Add explicit little-endian `PcmSamples` frame/channel read/write helpers.
- [ ] Replace `convertTo16()` internals with encoding-aware conversion for all
      supported integer/float formats while preserving source layout.
- [ ] Add channel-preserving `excerpt()` and explicit `convertLayout()`.
- [ ] Deprecate compatibility methods only after they delegate safely; keep
      current public constructors/fields source-compatible during this task.
- [ ] Rebase retained cue metadata and preserve supported LIST metadata.

Gate: exact sample vectors, channel impulses, excerpts, and legacy API tests pass.

## 4. Harden Container Readers

- [ ] Replace binary reads/skips with exact, progress-guaranteed little-endian
      primitives and US-ASCII FourCC handling.
- [ ] Parse RIFF only inside its declared unsigned boundary, honor word padding,
      validate fmt/data together, and reject duplicate/missing/unsupported data.
- [ ] Support PCM, IEEE float32, and constrained mono/stereo extensible WAV;
      reject unsupported codecs/layouts without returning null or printing.
- [ ] Introduce typed decode exceptions carrying source/container context.
- [ ] Make Vorbis allocation/decode counts checked; close decoder and free all
      native buffers on every exit; copy only returned decoded frames.
- [ ] Update every `AudioReader` caller to handle typed success/failure.

Gate: malformed/limit/short-read/Vorbis tests pass with no leaks or stack-trace
side effects.

## 5. Harden WAV Writing

- [ ] Validate format and frame alignment before opening/publishing output.
- [ ] Calculate RIFF/chunk sizes in `long`, enforce RIFF limits, and write pads.
- [ ] Emit the correct PCM or IEEE-float format tag and supported metadata.
- [ ] Make stream ownership explicit: file overload closes its stream and
      cleans partial output; stream overload never closes caller-owned streams.
- [ ] Add typed `writeHeader(PcmFormat, length)` while retaining a validating
      legacy overload for current mixer callers.

Gate: writer-reader round trips and non-closing/overflow/odd-padding tests pass.

## 6. Preserve Source Channels on Import

- [ ] Add SOURCE/MONO/STEREO import policy with SOURCE as the importer default.
- [ ] Remove unconditional `-ac 1`; generate pure, testable FFmpeg arguments.
- [ ] Strict-validate WAV input/output and publish through a unique owned temp
      file so failed conversions never replace a user asset.
- [ ] Preserve validated WAV bytes/metadata for SOURCE when no conversion is
      required; transcode other supported inputs to lossless PCM WAV without
      forcing channel count.
- [ ] Surface conversion/process/validation failure to the importer caller.

Gate: argument-policy and fake publication/cleanup tests pass; Mono/Stereo
impulses retain their source layout under SOURCE.

## 7. Integration and Handoff

- [ ] Migrate waveform/OpenAL preparation paths to validated format accessors
      or explicit derived PCM16 buffers.
- [ ] Search for raw `Wave.data` 16-bit assumptions and either migrate them in
      this child or list them as required consumers in the mixer child.
- [ ] Run focused tests, `testVideoExportUtils`, compile tasks, `check`, and
      `git diff --check`.
- [ ] Record the stable type/method contract consumed by mixer, export, and
      Premiere child tasks.

## Validation Commands

```powershell
cd new
.\gradlew.bat --no-daemon testAudioFormat
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon testVideoExportUtils
.\gradlew.bat --no-daemon check
git diff --check
```

## Rollback Points

- Format types/tests can land independently before caller migration.
- Keep legacy `Wave` constructors and delegating methods until all consumers
  compile; do not combine their removal with codec fixes.
- Import policy changes are isolated from reader/writer publication and can be
  reverted without reverting safe decoding.
- Never remove or rewrite pre-existing files outside `new/` in this child.
