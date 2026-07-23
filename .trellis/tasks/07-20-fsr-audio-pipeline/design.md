# FSR Audio Pipeline Design

## Boundaries

The work spans two repositories without merging their ownership:

- `new/` owns audio formats, decoding, preview, recording, film/Minecraft
  rendering, video settings, FFmpeg process lifecycle, and final media results.
- `bbs-premiere-export/` consumes stable FSR audio/export descriptors and owns
  editable WAV/XMEML packaging. Its existing uncommitted changes are inputs.

The parent task owns contracts and integration. Child tasks own implementation.

## Canonical Contracts

### Format

- Address audio by frames, never raw byte offsets across layers.
- Carry explicit sample encoding, sample rate, channel layout, and frame count.
- Supported delivery layouts are `MONO` and `STEREO`.
- `SURROUND_5_1` may exist as an unsupported reserved identifier; public
  validation must reject it until a future task implements every boundary.
- HRTF is not a channel layout and is deferred behind a future spatializer.

Decoded/mixed samples use an interleaved floating-point buffer with headroom.
Legacy `Wave` callers cross one adapter that validates PCM/WAV metadata and
decodes supported PCM to that buffer. Playback-specific PCM16 conversion is a
derived cache, not a mutation of the canonical data.

### Channel Policy

- Preserve mono and stereo assets on import.
- Mono master output uses an explicit stereo-to-mono matrix.
- Stereo master output centers mono non-positional material at equal gain and
  uses equal-power pan/attenuation for positional mono events.
- Stereo source material keeps its left/right image and is not point-panned.
- Inputs beyond stereo are rejected with a clear unsupported-layout result in
  this release rather than silently discarding channels.

### Time

- Export range is one immutable `[startTick, endTick)` descriptor shared by
  video, film audio, Minecraft capture, and Premiere.
- Frame/sample conversion uses rational arithmetic and a documented rounding
  rule; the same descriptor produces expected video frames and audio samples.
- Pitch owns an accumulated fractional source phase. Automation changes the
  step from that phase and never recomputes position from elapsed time.

### Results and Ownership

- Recording, render, mix, mux, rename, cancellation, and degraded fallback use
  typed outcomes. Requested audio failure cannot collapse into plain success.
- Every temporary file has a unique session owner and is deleted only by that
  owner after the next durable artifact exists.
- Long audio rendering is block-based, asynchronous, cancellable, and reports
  bounded progress. UI mutation is dispatched to the client thread.
- Strict process timeout cleanup has a 750 ms teardown deadline inside the
  separate one-second return budget.
- OpenAL source ownership is `(playback session, clip identity)`; decoded asset
  caches remain keyed by `Link`.

## Data Flow

```text
WAV/OGG/import/capture
  -> validated source descriptor
  -> decoded float frames (source layout/rate)
  -> channel map + resampler + per-source gain/spatial state
  -> block float master bus (Mono or Stereo, 48 kHz)
  -> one master limiter/clip report
  -> one quantization boundary
  -> session-owned WAV
  -> FFmpeg delivery profile / Premiere media descriptor
```

Minecraft sound capture remains event-based for deterministic offline export.
It restores the useful upstream `eef239e5` cursor/window behavior while keeping
the current NeoForge APIs and stronger video-process lifecycle.

## Compatibility

- Existing settings files without a layout value default to `MONO` for
  compatibility; `STEREO` is an explicit opt-in.
- Existing mono assets and microphone takes remain valid.
- Public legacy `Wave` methods remain during migration but delegate to validated
  frame-aware operations; unsafe byte-oriented methods are deprecated/removed
  only after all in-tree consumers migrate.
- Default MP4 audio becomes one named 48 kHz AAC-LC profile, at least 192 kb/s
  for stereo. Mono may use a lower bitrate without changing codec/rate.
- A lossless WAV intermediate is retained only when recovery/export semantics
  require it; no intermediate is re-encoded lossily.
- The current release runs Core `2.3.2`. The Premiere add-on declares
  `[2.3.2,)` and compiles/tests only against
  `../new/build/libs/BBS_FSR_beta_v0.0.10.jar`; older jar names and compatibility
  aliases are not active dependency identities.

## Rollback

Each child lands behind existing call boundaries. If integration fails, revert
the active child while retaining earlier format/tests work. Never roll back by
discarding the dirty Premiere worktree. Keep old settings readable throughout.

## Deferred Spatial Audio

Future HRTF work should implement a `Spatializer` fed by the same listener and
source descriptors. It must add a distinct binaural delivery mode and prove
preview/export parity; it must not overload `STEREO` or `SURROUND_5_1`.
