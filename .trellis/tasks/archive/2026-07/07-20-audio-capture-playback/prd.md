# Audio Capture and Playback Lifecycle

## Goal

Make microphone recording and film-audio preview safe across normal completion,
cancellation, device failure, transport changes, asset edits, reload, disconnect,
and client shutdown. Restore the useful upstream microphone workflow while
replacing link-global playback and unbounded capture ownership with explicit
session-owned lifecycles.

## Scope

- Harden `OpenALRecorder` and `UIAudioRecorder` in the current `new/` FSR
  client.
- Restore the applicable upstream microphone behavior: bounded waveform
  history, count-in, film playback during capture, finish versus cancel,
  film-based name suggestions, cursor restoration, and client-thread clip
  insertion.
- Rework `AudioClientClip`, `SoundManager`, `SoundPlayer`, and controller
  integration so each playback session and clip identity owns an independent
  OpenAL voice.
- Preserve mono and stereo source channels in capture/playback. Microphone
  capture defaults to mono; an explicit stereo capture mode may be used only
  when supported by the selected device.
- Keep existing public/protected JVM descriptors source- and binary-compatible
  through additive APIs or delegating adapters.

## Requirements

### Microphone Capture

- One capture session must own its device handle, capture thread, sample sink,
  temporary artifact, transport snapshot, UI generation, and terminal result.
  Every resource is acquired once and released once by that owner.
- Session state and terminal commands must be thread-safe and idempotent.
  `stop` means drain and save; `cancel` means discard; a late success callback
  must never override cancellation, failure, disconnect, or a replaced UI.
- Successful stop must stop the capture device, drain all still-available
  samples, finalize the exact captured frame count, close the device, and only
  then publish success. The drained tail must be present in the WAV.
- Capture storage must be streaming or explicitly bounded. Recording duration
  must not cause a geometrically growing direct buffer or unbounded client heap
  use. Queue/sink overflow, disk exhaustion, and duration limits are failures,
  not silent sample loss.
- The OpenAL capture ring must remain large enough for the polling cadence, and
  every available sample must be read with the correct sample index and signed
  16-bit normalization. Meter/waveform sampling must not alter PCM delivery.
- Enumerate/open/start/read/stop/close and sink/finalization failures must
  produce distinct observable failure details. No-device and unsupported
  mono/stereo mode errors must reach the UI; no empty asset or clip is created.
- Count-in must not record samples. Capture and the owning film transport begin
  from the same immutable origin cursor. On every terminal path, the transport
  is stopped and the cursor returns to that origin without an unrelated late
  callback moving it again.
- A successful take is first written to a uniquely session-owned temporary
  path. Commit must not overwrite an existing asset. Only after durable commit
  may a clip be inserted at the captured origin tick on the client thread.
- Clip duration is derived from captured audio frames and sample rate using a
  ceiling tick conversion so a final partial tick is not truncated.
- Cancel during count-in or active capture creates no final WAV, no clip, no
  selection change, and no retained temporary file. Disconnect, film/screen
  replacement, world unload, and client shutdown use the same cancel path while
  the owning panel/session is still reachable.
- Worker threads may publish immutable progress/result snapshots only. File
  commit, clip-list mutation, selection, cursor/transport changes, overlay
  removal, and user-facing errors are dispatched to and generation-fenced on
  the Minecraft client thread.

### Preview Playback

- Decoded buffer cache identity remains the asset `Link`; OpenAL voice identity
  is `(playback owner, clip object identity)`. Two overlapping clips using the
  same asset must share decoded data but use independent sources, offsets, and
  gains.
- Each transport frame builds a desired voice set and reconciles it against the
  actual voices owned by that controller. Reconciliation must run even when no
  clip is active; absence from the desired set stops and releases a stale
  voice.
- Disabled, removed, expired, or relinked clips must stop their old voices.
  Relinking recreates or rebinds only that clip's voice and must not disturb
  another clip or controller using the old asset.
- Pause and resume follow the owning transport, including `WorldFilmController`
  pause and editor `RunnerCameraController` state. Pausing preserves the voice
  and expected timeline offset; resume/seek reconciles offset before playback.
- Scrubbing while paused updates the paused source position. Leaving the clip's
  active interval releases the voice rather than retaining a paused stale
  source.
- Controller shutdown, film reload/replacement, screen teardown, disconnect,
  resource reload, and client stop release every voice owned by the affected
  session. One owner must never stop another owner's same-link voice.
- OpenAL playback mutations occur on the client/OpenAL context thread. Watchdog
  and worker callbacks enqueue invalidation rather than deleting live OpenAL
  objects directly.
- Source cleanup order is stop, detach buffer, delete source. A buffer is
  deleted only after every source that references it is detached/deleted.
  Cleanup is idempotent and continues after one cleanup failure while retaining
  the first failure for diagnostics.
- Asset invalidation/deletion stops every voice using that buffer, not only the
  first list entry. A global sound reset releases all sources before buffers
  and leaves no invalid source or buffer handles in manager collections.
- Mono and stereo assets retain their channel layout through OpenAL upload and
  preview. Unsupported layouts fail explicitly. This task does not add HRTF,
  binaural processing, or surround playback.

### Observability and Compatibility

- Do not swallow capture, file, or OpenAL failures. Log each terminal failure
  once with bounded context and present a localized, actionable capture error
  to the user.
- Retain legacy `OpenALRecorder`, `SoundManager`, and `SoundPlayer` entry-point
  descriptors where exposed. Film playback must use the new owner/clip voice
  API; unrelated one-shot/unique UI playback remains compatible. A legacy
  `Consumer<Wave>` capture adapter may materialize PCM only under an explicit
  maximum byte/duration bound; the production recorder UI consumes the
  finalized-file result and does not reload the complete take into memory.
- Tests must use injectable capture/playback backends so lifecycle correctness
  is verified without requiring physical microphone or OpenAL hardware.

## Acceptance Criteria

- [ ] Stop drains a fake device tail, finalizes the exact frame count, and
      closes/frees every owned device, sink, thread, and temporary resource once.
- [ ] Cancel wins over queued/late success and produces no final WAV or clip
      before start, during count-in, during capture, and during finalization.
- [ ] Missing device, open/start/read failure, unsupported stereo mode, sink
      overflow, disk failure, and callback failure are distinct tested outcomes
      with no resource leak or false success.
- [ ] A long capture uses bounded memory/queue capacity and never silently drops
      samples; overload ends with an explicit failure.
- [ ] Captured PCM, meter, and waveform fixtures read every signed sample at
      the correct offset; meter output does not mutate the captured bytes.
- [ ] The saved clip starts at the immutable recording-origin cursor, uses
      `ceil(capturedFrames * 20 / sampleRate)` ticks, and contains the final
      partial tick/tail samples.
- [ ] All clip/UI/transport mutations are proven to run on the client thread,
      and a stale UI generation cannot insert a take after teardown.
- [ ] Two overlapping same-asset clips produce two independent OpenAL sources
      backed by one decoded buffer, with independent gain and offset.
- [ ] Pausing/resuming either world-film or editor transport pauses/resumes only
      its owned voices and reconciles timeline position before play.
- [ ] Empty desired state, clip removal/disable/expiry/relink, controller
      shutdown, reload, disconnect, and client stop leave no stale voice.
- [ ] Two playback owners using the same asset can shut down independently.
- [ ] Asset invalidation and global reset stop/detach/delete all affected
      sources before deleting buffers; cleanup calls and handle deletion are
      idempotent in failure-injection tests.
- [ ] Mono and stereo capture/playback fixtures retain channel count and left/
      right identity; unsupported layouts are rejected and HRTF is not exposed.
- [ ] Relevant focused tests, `compileJava`, `compileClientJava`, `check`, and
      `git diff --check` pass; physical microphone/OpenAL smoke status is
      recorded separately.

## Out of Scope

- HRTF, binaural, HRIR, Ambisonics, 5.1/7.1, or vertical spatial audio.
- Operating-system loopback, live network audio, or replacing OpenAL.
- Offline film/Minecraft mixing, FFmpeg muxing, and export channel settings,
  which are owned by sibling audio tasks.
- A broad redesign of non-film UI sound playback beyond compatibility and
  resource-safety changes required by shared `SoundManager` cleanup.

## Deferred Follow-up

Add an optional HRTF spatializer behind the parent pipeline's future
spatializer boundary only after mono/stereo preview and export parity is proven.
