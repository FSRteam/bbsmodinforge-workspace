# Design: Block Audio Mixer And Channel Rendering

## 1. Current Baseline And Constraints

The current FSR code has two independent render paths:

- `AudioRenderer.renderAudio` builds a mono, 16-bit `Wave`, calls
  `Wave.add` once per clip, and writes it through `WaveWriter`.
- `MinecraftSoundMixer.mixToFile` writes a fixed stereo WAV and, for every
  output sample, scans all captured sounds, looks up the current loop frame,
  samples a source frame, and clamps each channel before writing.

The latter shape is both expensive and lossy for this task: it resets/quantizes
state at event boundaries, cannot preserve an authored stereo source through a
mono/stereo choice, and has no cancellation/progress result. The upstream
`eef239e5` implementation establishes the capture/event model. The later
`f76cce04` mixer is the behavior reference for the parts worth retaining:
resource caching, fixed streaming windows, per-event cursors, linear
interpolation, continuous `srcPos`/pitch stepping, listener-frame gain updates,
constant-power mono panning, and stereo assets bypassing positional panning.
Those behaviors are adapted behind the contract below rather than copied as a
second renderer.

## 2. Canonical Model

Create one client audio-rendering core (names are descriptive; follow existing
package naming when implementing) under `mchorse.bbs_mod.audio`:

```text
AudioLayout          MONO, STEREO, SURROUND_5_1 (reserved/rejected)
AudioRenderRequest   sources, minecraft events, film source, timing,
                     output rate, frame rate, layout, output/temp file,
                     cancellation token, progress sink
AudioRenderResult    status, output path, layout, frame count, error code,
                     recoverable diagnostics
AudioSource          immutable decoded samples + channels/rate/metadata
SourceCursor         mutable fractional phase, pitch, loop, gain/pan state
BlockRenderer        prepare -> render blocks -> master finalize -> write
```

`AudioRenderRequest` is the sole owner of timing and output layout. Callers do
not independently derive channel count, range, or sample count. The request
normalizes the half-open interval `[startSeconds, endSeconds)` and computes:

```text
outputFrames = ceil((endSeconds - startSeconds) * outputSampleRate)
```

The normalized request rejects non-finite/negative rates, inverted ranges,
unsupported layouts, and overflow before opening a file.

`AudioRenderResult` is a typed terminal value, for example
`SUCCESS`, `EMPTY`, `CANCELLED`, `UNSUPPORTED_FORMAT`, `MISSING_RESOURCE`,
`IO_FAILURE`, or `MIX_FAILURE`. The old boolean methods become adapters that
map only `SUCCESS` to `true` and log/return `false` for every other status.

## 3. Channel And Spatial Mapping

Keep source channels in their decoded order until the output matrix is applied.
Use one explicit matrix per source/output combination:

| Source | Output | Mapping |
|---|---|---|
| mono | mono | `M = [1]` |
| mono | stereo | `L = x * sqrt(0.5)`, `R = x * sqrt(0.5)` |
| stereo | stereo | `L = xL`, `R = xR` |
| stereo | mono | `M = 0.5 * xL + 0.5 * xR` |

The equal-power center gain avoids a centered mono source gaining 3 dB when
expanded to stereo. The equal-weight downmix keeps a correlated L/R pair at
unity and is deliberately documented rather than hidden in a helper. Inputs
with more than two channels fail with `UNSUPPORTED_FORMAT`; they are not
silently truncated.

For a captured Minecraft event, a mono source is first assigned the positional
gain computed from the listener frame (range attenuation plus constant-power
pan), then passed through the selected output matrix. A stereo source keeps its
left/right image and receives only its event volume, matching the upstream
OpenAL-compatible behavior. Relative mono sounds use centered gain. Film audio
uses the same matrix, with no special mono-only path.

The matrix and spatial policy live in the core, so preview/intermediate/final
paths cannot grow subtly different channel rules.

## 4. Decode, Resample, And Cursor State

Preparation decodes each distinct resource once into an immutable `AudioSource`
and validates channel count, sample format, frame alignment, sample rate, and
duration. Resource-manager access happens in the owning client preparation
phase or through a thread-safe immutable byte snapshot; the worker never calls
Minecraft client APIs while rendering.

Each clip/event gets an independent `SourceCursor` containing:

- output start/end frame and loop bounds;
- `double sourcePosition` and current `double step`;
- source-rate/output-rate conversion and continuous pitch;
- current/target channel gains and the last listener/loop frame index;
- stable source ordering key for deterministic accumulation.

For each output frame, the cursor reads `floor(sourcePosition)` and the next
source frame, linearly interpolates each retained channel, applies the channel
matrix, advances `sourcePosition += step`, and wraps only after a loop boundary.
The fractional position survives block boundaries and pitch updates. Gain and
pitch automation is sampled at the relevant captured frame and interpolated or
smoothed according to the existing upstream policy; it is never recomputed by
integer event scaling on the client thread. One-shots terminate at source end;
loops terminate at their captured end frame or request end.

## 5. Block Renderer And Master Boundary

Use a fixed block size (for example 8,192 output frames; implementation may
tune it while keeping the bound documented). Allocate/reuse only:

```text
float[channelCount][blockFrames] accumulation
byte[channelCount * 2 * blockFrames] packed output
```

Preparation creates and stably orders cursors by timeline start, source
identity, and capture sequence. Rendering then:

1. clears the block accumulation buffers;
2. mixes each cursor into the block using double/float accumulation and
   continuous phase;
3. mixes the film source through the same resampler/matrix;
4. checks cancellation and reports one bounded progress update;
5. applies the master limiter across the completed output channels;
6. quantizes once to signed 16-bit little-endian PCM and writes the block.

No cursor or track clamps before step 5. The master limiter is one hard peak
clamp to the representable normalized range `[-1, 1]`, matching the current
upstream behavior while making its placement explicit. All sources and both
channel paths are accumulated first, then limited, then quantized. This prevents
per-track clipping seams and keeps source iteration order differences below the
acceptance tolerance.

The writer emits the header only after request validation and writes exactly
`outputFrames * outputChannels * 2` data bytes. A failed/cancelled write closes
the stream, deletes only the session-owned temporary path, and never replaces a
pre-existing user file.

## 6. Async, Cancellation, And Progress

Expose a worker-oriented `renderAsync`/`render` pair. The synchronous core is
usable in tests and by an already-owned worker; export UI code always submits
the request to the existing export executor/session. The cancellation token is
checked:

- before resource preparation and after each decode;
- before opening the temporary output;
- between every block and during long cursor-preparation loops;
- before promotion/rename of the completed temporary file.

The progress sink receives `(completedFrames, totalFrames)` only at block
boundaries and must be monotonic and clamped to `[0,1]`. Terminal callbacks are
marshaled to the owning client/UI thread. Disconnect, world unload, panel
close, and explicit cancel all use the same idempotent session teardown owner;
the UI owner is cleared only after the worker, stream, and temp file have been
released.

## 7. Compatibility And Integration Boundaries

`BBSSettings` owns one persisted `AudioLayout` value. Loading accepts existing
settings and maps missing/invalid/legacy values to `MONO`; the reserved
`SURROUND_5_1` token is retained only for round-trip diagnostics and rejected
by request validation. UI exposes only Mono and Stereo.

Update the following call paths to construct one request and consume one result:

- `AudioRenderer.renderAudio` and `UIFilmPreview`/panel audio export;
- `MinecraftSoundMixer.mixToFile` and `VideoExportSession` Minecraft-sound
  combination;
- `WorldVideoExportSession`, `PanelVideoExportSession`, and F4/F6 timing/range
  adapters;
- `WaveWriter` header/metadata arguments and `VideoMuxer`/FFmpeg channel
  arguments and final metadata probing.

The compatibility methods preserve existing signatures while delegating to the
core. New callers use the typed result and explicit layout. Film and Minecraft
audio are rendered into one lossless PCM intermediate before the final mux;
there is no repeated lossy encode between source, mix, and final media.

## 8. Error And Observability Contract

Every recoverable source/resource failure carries its source id and stage in a
bounded diagnostic list and a warning/error log. A missing Minecraft resource
does not silently turn the entire requested mix into a success. Callers decide
whether an explicitly degraded result may be retained, and must surface that
choice to the user. Exceptions are never swallowed in the worker without a
terminal result.

## 9. Test Design

Keep pure core tests independent of Minecraft, then add integration tests at
the export boundaries:

- routing matrix tests with left/right impulses and correlated signals;
- integer/floating source fixture validation and frame-alignment rejection;
- 44.1/48 kHz resampling, exact duration, fractional pitch, and loop phase;
- block-size boundary tests (event starts/ends exactly at a block edge);
- master limiter/quantizer tests proving no intermediate clip and one final
  clamp, plus order-independence tolerance;
- cancellation/progress tests with a long synthetic event list and bounded
  memory/write behavior;
- layout-setting migration and reserved-5.1 rejection;
- `VideoExportSession`/F4/F6 result propagation, temp-file preservation, and
  real FFmpeg channel/duration metadata where the tool is available.

## 10. Deferred Spatializer Seam

Do not add HRTF code or UI. Keep spatial positioning as a source-stage policy
that produces channel gains before the output matrix; a future HRTF stage can
replace that policy while consuming the same immutable source/cursor/event
model and master boundary.
