# Video Audio Export Lifecycle Design

## Ownership Boundaries

`ValueVideoSettings` owns persisted user intent. Export sessions normalize and
snapshot that intent once. `VideoExportSession` owns one immutable request, one
session id, one work directory, recorder lifetime, postprocessing, publication,
cleanup, and terminal result. Renderers and muxers receive exact request and
artifact paths; they never rediscover files by scanning output directories.

The format and mixer siblings provide `ChannelLayout`, validated WAV metadata,
and cancellable PCM rendering. This child adapts those contracts to the current
recorder without replacing its reservation, warm-up, failure aggregation, or
bounded FFmpeg teardown behavior.

## Export Request

Add an immutable request carrying:

- session id and generation;
- half-open source range `[startTick, endTick)` (or an open F4 end);
- FPS and final captured frame count;
- 48 kHz audio sample rate and resolved `MONO` or `STEREO` layout;
- film-audio and Minecraft-sound inclusion flags;
- exact work, raw-video, PCM-WAV, mux-partial, and final paths.

The settings boundary accepts stable ids `mono`, `stereo`, and reserved `5.1`.
Only the first two are selectable. Missing, blank, unknown, malformed, legacy,
and `5.1` values resolve to `MONO`; explicit `stereo` remains `STEREO`. The
resolved enum is captured before recorder startup, so later UI changes cannot
change an active export.

Panel ranges are normalized once and shared by video and audio. An empty range
fails before reservation. F6 uses the film origin/end. F4 captures its start and
gets its end from the delivered frame count when the user requests normal stop.

## State And Result Model

Extend the existing session lifecycle with `FINALIZING` and `POSTPROCESSING`.
Recorder completion first drains the last frame and freezes delivered duration.
Audio rendering, optional Minecraft mixing, muxing, publication, and cleanup
then run as one owned terminal pipeline. The recorder reservation and UI state
remain owned until that pipeline has produced exactly one result.

Use a typed result with at least these kinds:

- `SUCCESS`: requested audio and video are present at the final path;
- `DEGRADED`: a deliberate video-only artifact is retained and audio failure
  details/recovery WAV are present;
- `CANCELLED`;
- `PREPARATION_FAILED`, `START_FAILED`, `AUDIO_FAILED`, `MUX_FAILED`,
  `PUBLISH_FAILED`, and `CLEANUP_FAILED`.

Results carry final/recovery artifacts, requested/delivered layout, whether an
audio stream is present, the failing stage, primary cause, and cleanup causes.
Legacy boolean listeners adapt only `SUCCESS` to true. Success-only UI effects
run after successful publication and cleanup; degraded output gets a warning.

All terminal paths are generation-fenced and idempotent. Cancellation signals
the renderer/mixer and terminates owned FFmpeg work, while stale worker
callbacks cannot complete or mutate a newer session.

## Duration Contract

The delivered video frame count is authoritative. Convert it to target audio
frames with rational arithmetic and one documented rounding operation. The PCM
renderer pads or trims to that count. Start time is shared from the request;
endpoints must differ by no more than one delivered video frame. `-shortest` is
not used to conceal incorrect source durations.

F4's second hotkey requests normal completion. Abort, disconnect, unload,
startup failure, and an explicit cancel control request cancellation. F6 toggle
off remains cancellation unless film playback reaches its natural end.

## Async Processing

PCM rendering/mixing and FFmpeg mux work execute on a bounded worker executor.
Progress is stage-based, monotonic, bounded, and posted to the client thread.
Cancellation is checked at every block and between stages. UI restoration and
terminal callbacks are always posted to the owning client thread.

## Artifact Model

Create a collision-free session directory under the configured output root.
Only files registered in that session's artifact set can be cleaned. Never use
fixed `.tmp` names, directory-wide newest-file scans, or `REPLACE_EXISTING`.
Mux into an owned partial file, fsync/close it, and publish with an atomic move
when supported or a collision-safe move otherwise. A publish failure leaves the
original video and recovery WAV intact.

The terminal result exposes the exact final artifact to Premiere. A retained
recovery WAV is promoted out of the cleanup set and named explicitly.

## FFmpeg Profile

Both direct and post-mux paths use one delivery profile: AAC-LC, 48 kHz,
192 kb/s, explicit `-ac 1` or `-ac 2`, explicit stream maps, and one final lossy
encode. When Minecraft mixing is requested, video is captured without a final
AAC stream, sources are combined in lossless PCM, and the final mux copies the
video stream while encoding AAC exactly once.

Custom templates remain supported through structured argument construction.
Validate required input, output, sample-rate, channel, and artifact placeholders
before launching FFmpeg; an unsupported layout or incomplete template is a
typed preparation failure.

## Compatibility And Rollback

Keep current public recorder/session descriptors with adapters where required.
Existing settings without the new value retain mono output. The current FSR
process timeout, stderr diagnostics, teardown, and failure aggregation remain
in place. The layout UI, typed result adapter, timing request, and owned artifact
publication are separable rollback points; no rollback may delete user files or
discard the dirty Premiere worktree.

## Deferred Work

`5.1` remains a parse-only unsupported id. HRTF/binaural rendering is a future
spatializer and is neither a stereo alias nor part of this lifecycle.
