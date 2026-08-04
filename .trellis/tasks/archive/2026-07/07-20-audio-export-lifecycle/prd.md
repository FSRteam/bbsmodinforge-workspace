# Video audio export lifecycle and channel settings

## Goal

Make F4, F6, and film-panel video export produce an explicit, correctly timed
Mono or Stereo audio result without weakening the current FSR recorder/process
lifecycle. A requested audio track must either reach the published media or be
reported as a typed degraded/failure outcome; it must never be silently omitted
from an otherwise unqualified success.

This child owns the persisted export-layout setting, export timing contract,
session/postprocess state machine, FFmpeg audio delivery profile, final-artifact
publication, and user/caller completion result. PCM decoding, rendering and
mixing algorithms are supplied by the format and mixer sibling tasks. Premiere
XMEML generation is implemented by the Premiere sibling task against the final
artifact contract defined here.

## Confirmed Context

- The target is the clean `new/` NeoForge 1.21.1 repository. The current
  `VideoExportSession` already owns recorder reservation, warm-up, FFmpeg video
  completion, teardown, and a one-shot callback; this lifecycle is extended,
  not replaced.
- `PanelVideoExportSession` currently calculates its video range separately
  from the range passed to `AudioRenderer`. `WorldVideoExportSession` renders
  the F6 film track but treats render failure as best-effort silence. F4 is
  stopped through the cancellation path and therefore has no normal manual
  completion path.
- Minecraft-sound mixing and `VideoMuxer` currently run synchronously after
  video completion, infer the output video by scanning the folder, use a
  predictable `.tmp` basename, may replace/delete an existing destination, and
  do not affect the session's success result.
- The default direct-audio encoder template uses AAC at 128 kb/s while the
  post-mux template uses AAC at 192 kb/s. These paths need one delivery profile
  and one final lossy encode.
- Existing `bbs-premiere-export/` changes belong to the user and must be
  preserved. That module must consume the resolved final media/audio artifact
  rather than infer a filename independently.

## Requirements

### Channel setting and compatibility

1. Add one persisted video-export channel-layout setting backed by the shared
   audio layout contract. The stable identifiers are `mono`, `stereo`, and a
   reserved `5.1` identifier.
2. Only `Mono` and `Stereo` are selectable in the video settings UI. `5.1` is
   retained solely for forward-compatible parsing and cannot be selected,
   rendered, passed to FFmpeg, published in metadata, or advertised as
   supported.
3. Normalize the persisted value once at the settings boundary. A missing,
   legacy, malformed, unknown, or reserved `5.1` value resolves to `Mono`, the
   compatibility default. An explicit `stereo` value remains Stereo. Migration
   diagnostics must be bounded and must not repeat every frame/export.
4. The resolved layout must be snapshotted at export start and carried through
   film rendering, Minecraft-sound mixing, the WAV intermediate, FFmpeg
   arguments, final media metadata, completion details, and Premiere export.
   Changing settings while an export runs must not change that session.
5. Preserve source/binary compatibility where current settings bridges or
   public recorder methods are already used. Add overloads/adapters where a
   signature change would remove an existing JVM descriptor.

### Timing and normal completion

1. Each export must own one immutable timing request containing the half-open
   source interval `[start, end)`, output frame rate, sample rate, requested
   layout, and audio/capture flags. Panel video and audio must use the same
   normalized loop bounds. F6 must use the same film origin/end for playback,
   capture, and audio. F4 has an open end until the user completes it.
2. The final captured video-frame count is the authoritative delivered
   duration. Audio rendering/mixing must trim or pad to that duration so the
   final audio and video endpoints differ by no more than one output video
   frame. FFmpeg `-shortest` is not a substitute for this contract.
3. Pressing F4 while its F4 recording is active must request normal completion
   (`stop`) and retain/publish the finished recording. Cancellation remains a
   distinct action for abort, disconnect, world unload, startup failure, or an
   explicit cancel control. F6 toggle-off remains cancellation unless the film
   reaches its natural end.
4. A panel loop export must preserve the exact requested loop interval, even
   when loop endpoints are reversed in the editor or the interval is shorter
   than one source tick. Invalid/empty ranges fail before FFmpeg starts.
5. Natural F6/panel completion must drain the recorder's final frame before
   using its frame count to finalize audio.

### Session, async postprocess, and outcomes

1. Extend the session state machine with explicit finalizing/postprocessing
   ownership. Video completion, optional audio render/mix, mux, publication,
   cleanup, UI restoration, and callback delivery form one terminal lifecycle.
   The session cannot report success or release ownership while required
   postprocessing is still running.
2. Long audio rendering/mixing and FFmpeg post-mux work must run off the
   client/render thread. Progress and the terminal callback are marshaled to
   the owning client/UI thread and generation-fenced so a stale callback cannot
   mutate a newer export.
3. Cancellation must be checked before and during async work, stop/terminate
   owned work promptly, and complete exactly once. Repeated stop, cancel,
   disconnect, teardown, or late worker callbacks must be idempotent.
4. Replace the boolean-only completion contract with a typed result that
   distinguishes at least:
   - successful requested output;
   - explicit degraded video-only output when requested audio could not be
     rendered, mixed, or muxed but a valid video was deliberately retained;
   - cancellation;
   - preparation/start failure;
   - missing-resource or audio-render/mix failure;
   - FFmpeg encode/mux failure;
   - final publish/rename failure;
   - cleanup failure.
5. A degraded result is never mapped to success. It includes the exact final
   video path, any retained recovery WAV, failed stage/reason, requested and
   delivered layout, and whether audio is present. A failed result retains the
   primary cause and suppressed cleanup causes. Legacy boolean listeners may
   remain as adapters where both degraded and failed/cancelled map to aborted.
6. Success-only effects (completion sound, open-folder action, Premiere handoff
   or other successful-completion hooks) run only after postprocess,
   publication, and required cleanup have completed successfully. Degraded
   output receives an explicit warning/status instead.
7. Audio and FFmpeg failures must be logged once per terminal stage with
   bounded context (session id, stage, outcome, artifact names) and exposed to
   the user/caller. Do not swallow exceptions or log per sample/event.

### Artifact ownership and publication

1. Allocate a unique export-session id and session-owned work area before
   creating any WAV, mux, log, or partial-output artifact. Pass exact paths
   between recorder, renderer, mixer, muxer, and Premiere integration; do not
   rediscover output by basename/time-based directory scanning.
2. Every cleanup operation must prove session ownership. It may delete only
   artifacts created by that session and must never target a pre-existing user
   WAV, video, log, `.tmp`, or similarly named file.
3. Final output names are collision-free and reserved/published without
   `REPLACE_EXISTING`. Muxing writes to a unique owned temporary path and then
   publishes atomically when supported. A failed move must preserve the
   original video and recovery artifacts and return a publish failure.
4. No cancellation/failure path may delete an older user export. Owned partial
   files are cleaned idempotently; a recovery WAV retained for a degraded
   result is promoted to a documented user-visible path and removed from the
   cleanup set.

### FFmpeg delivery profile

1. Define one shared default final-audio delivery profile used by direct audio
   export and post-mux: 48 kHz AAC-LC at 192 kb/s, with explicit one- or
   two-channel output derived from the resolved layout and explicit stream
   mapping. Defaults and generated placeholders must not disagree between
   `ValueVideoSettings`, `VideoRecorder`, and `VideoMuxer`.
2. Lossless WAV/PCM remains the intermediate/master format. If Minecraft-sound
   postprocessing is required, capture video without a final AAC track, mix all
   film/Minecraft sources in PCM, and encode AAC exactly once during the final
   mux. Copy the video stream during that mux.
3. User-authored FFmpeg templates remain supported, but required input/output,
   channel, layout, sample-rate, and artifact placeholders are validated before
   launch. Unsupported/reserved layouts or templates that cannot preserve the
   requested contract fail explicitly instead of silently producing different
   metadata.
4. The final result must expose probeable channel count/layout, sample rate,
   codec/profile, duration, and exact artifact path for verification and
   Premiere consumption.

## Acceptance Criteria

- [ ] Missing, legacy, invalid, unknown, and persisted `5.1` layout values
      resolve to Mono once; explicit Stereo round-trips and remains Stereo.
- [ ] The settings UI exposes only Mono and Stereo, and the session snapshots
      the resolved value so a mid-export settings change has no effect.
- [ ] Mono/Stereo panel and F6 exports produce WAV intermediates and final media
      whose probed channels, layout, sample rate, AAC-LC profile, and 192 kb/s
      target match the request.
- [ ] F4's second key press completes normally, reaches `SUCCESS` after any
      required postprocess, and triggers success-only effects; explicit cancel
      reaches `CANCELLED` and never does so.
- [ ] F6 and normal/reversed panel loop ranges use one half-open timing request
      and finish with audio/video start and end alignment within one output
      frame at representative 24, 30, and 60 FPS rates.
- [ ] Empty/invalid loop ranges fail before recorder startup and leave editor,
      film playback, window size, render target, and recorder reservation
      restored.
- [ ] Audio missing-resource, render, mix, mux, publish, cleanup, and
      cancellation cases produce distinct typed outcomes and exactly one
      terminal callback. Requested audio loss is never plain success.
- [ ] Postprocessing does not block the client/render thread, reports bounded
      monotonic progress, is cancellable, and ignores generation-stale worker
      callbacks.
- [ ] Concurrent/repeated export names plus pre-created matching `.wav`,
      `.tmp`, `.log`, and video files prove that no pre-existing artifact is
      overwritten or deleted on success, failure, or cancellation.
- [ ] Direct-audio and post-mux argument tests derive the same AAC profile and
      layout metadata, while the post-mix path performs only one lossy audio
      encode.
- [ ] The exact published artifact/result is handed to
      `bbs-premiere-export/`; its existing worktree changes are preserved and
      no filename re-scan or duplicate audio encode is introduced.
- [ ] Focused unit/integration tests, real FFmpeg/ffprobe tests where available,
      relevant Gradle compile/check tasks, and `git diff --check` pass.

## Out of Scope

- PCM/WAV decoder implementation, resampler/limiter algorithms, microphone
  ownership, preview/OpenAL voice ownership, and Premiere XMEML internals owned
  by sibling tasks.
- Actual 5.1/7.1 mixing, encoding, UI selection, or metadata claims.
- HRTF/binaural, HRIR, Ambisonics, Atmos, or headphone spatialization. HRTF is
  deferred to a future optional spatializer that consumes the same explicit
  source/event and layout contracts.
- Operating-system loopback capture or replacement of Minecraft/OpenAL.

## Dependencies

- `07-20-audio-format-contract` supplies stable channel-layout ids and validated
  WAV/PCM metadata. This task is authoritative for the persisted video-export
  compatibility default: Mono for missing/invalid/reserved values.
- `07-20-audio-mixer-render` supplies cancellable, exact-duration async PCM
  render/mix results for the request defined here.
- `07-20-premiere-audio-export` consumes this task's final artifact and channel
  metadata contract.
- `07-20-audio-verification` owns the cross-tree real-media and A/V timing gate.
