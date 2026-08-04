# Block Audio Mixer And Channel Rendering

## Goal

Provide one deterministic, block-based renderer for film clips and captured
Minecraft sounds. The renderer must preserve source channel identity until an
explicit output-layout mapping, support mono and stereo delivery, and expose
bounded asynchronous progress and cancellation for long exports.

## Scope

- Render supported `Wave` sources and `MinecraftSoundCapture` events into an
  exact-duration PCM stream in the requested `Mono` or `Stereo` layout.
- Use explicit mono/stereo routing, source-rate resampling, continuous source
  phase for pitch changes, and one master limiter/quantization boundary.
- Share the rendering contract between `AudioRenderer` (film audio) and
  `MinecraftSoundMixer` (Minecraft sounds plus film audio), while preserving
  the existing public entry points through compatibility adapters.
- Carry layout, sample rate, frame rate, start/end range, and cancellation from
  panel, F4, and F6 export callers through the WAV intermediate and final mux.
- Define a reserved `5.1` layout identifier for serialization/migration, but
  reject it as unsupported and never expose it as selectable output.
- Keep HRTF/binaural rendering out of this change; the source/event model must
  leave a future spatializer insertion point.

## Requirements

### Rendering contract

- Calculate output frame count from an explicit half-open time interval and
  sample rate. 44.1 kHz and 48 kHz inputs/outputs must not lose or duplicate a
  frame at a clip boundary.
- Decode each source once per render session and retain channel count, sample
  rate, bit depth, and frame boundaries. Unsupported encodings fail with a
  typed render error instead of being silently treated as silence.
- Resample with a continuous fractional source position. A source cursor owns
  its phase and pitch step across blocks and across loop wraps; it must not
  restart from an integer sample for each output event or block.
- Apply an explicit channel matrix:
  - mono -> mono copies the sample;
  - mono -> stereo sends equal-power center gain to left and right;
  - stereo -> stereo preserves left/right order;
  - stereo -> mono uses the documented equal-weight left/right downmix;
  - more than two channels and the reserved 5.1 layout are rejected.
- Positioned mono Minecraft sounds use the captured listener/pan/attenuation
  state; stereo assets retain their authored left/right image. Film audio uses
  the same final layout matrix and is mixed before master processing.
- Accumulate all active sources in floating-point block buffers. No source,
  clip, or intermediate track may clamp or quantize independently.
- Apply exactly one master limiter (at the output-layout boundary), then one
  signed PCM quantization/byte-packing step. Limiter and quantizer behavior is
  identical regardless of source iteration order within the stated tolerance.

### Async lifecycle

- Long mixes run off the client/render thread in bounded blocks. Cancellation is
  checked during source preparation and between blocks, and stops further file
  writes promptly.
- Report progress as bounded work completed/total work, with an optional
  callback that is marshaled to the owning client/UI thread. Progress must not
  require one callback per sample or event.
- Return distinct outcomes for success, empty input, cancellation, unsupported
  format, missing resource, and I/O/mix failure. A cancelled or failed request
  is never reported as a successful export and leaves no partial final file.
- Temporary output is session-owned and uniquely named; cleanup is idempotent
  and cannot delete a pre-existing user file.

### Compatibility and integration

- Existing `AudioRenderer.renderAudio(...)` and
  `MinecraftSoundMixer.mixToFile(...)` callers remain source-compatible via
  adapters that resolve the selected layout and use the new result contract.
- Add one persisted channel-layout setting with `Mono` and `Stereo` values.
  Missing, malformed, or legacy values migrate to documented `Mono` default;
  `5.1` may be read for forward compatibility but is rejected for rendering.
- Panel, F4, and F6 paths pass the same explicit layout and `[from,to)` timing
  into the renderer, WAV writer, FFmpeg arguments, and final metadata.
- Preserve the existing FSR video-process lifecycle and existing user files;
  renderer work must not reintroduce the upstream F4/F6 completion regression.

## Acceptance Criteria

- [ ] Mono and stereo impulse fixtures retain channel identity through film
      mixing, Minecraft mixing, WAV output, and final mux.
- [ ] Mono/stereo routing matches the mapping matrix, including explicit
      stereo-to-mono behavior and rejection of unsupported channel counts.
- [ ] 44.1 kHz and 48 kHz fixtures produce the expected exact frame count and
      no half-frame channel skew.
- [ ] A pitch-changing loop remains phase-continuous across block boundaries
      and loop wraps; no per-event integer reset is observable in the fixture.
- [ ] Source peaks are not clipped before the master boundary; the final
      limiter/quantizer runs once and produces valid signed little-endian PCM.
- [ ] Reordering clips/events changes output only within the documented numeric
      tolerance.
- [ ] A long/high-event-count mix uses bounded memory, leaves the client thread
      responsive, reports monotonic bounded progress, and cancels without a
      successful result or stale partial output.
- [ ] Missing/legacy layout settings resolve to `Mono`; selected `Mono` or
      `Stereo` is reflected in WAV channel count, FFmpeg metadata, and final
      media probes; `5.1` cannot be selected or exported.
- [ ] Existing public renderer/mixer entry points continue to compile and their
      callers receive explicit failure outcomes rather than swallowed errors.
- [ ] `git diff --check` and the relevant compile/unit/integration/media tests
      pass; manual client-thread and F4/F6 smoke results are recorded.

## Out Of Scope

- Actual 5.1/7.1 speaker routing or encoding.
- HRTF, binaural, HRIR, Ambisonics, or other spatializer implementation.
- Replacing Minecraft/OpenAL or capturing operating-system loopback audio.
- A broad decoder rewrite beyond the renderer's supported-format validation
  boundary.

## Deferred Follow-up

Add an optional HRTF spatializer that consumes the same source/event cursors and
layout boundary, then prove preview/export parity before exposing it as a user
setting.
