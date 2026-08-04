# Audio Capture and Playback Lifecycle Implementation Plan

## Preconditions

- Use the shared mono/stereo format contract from `07-20-audio-format-contract`
  when it lands. Until then, keep capture/playback adapters local and avoid
  creating a second public layout enum.
- Preserve legacy public/protected JVM descriptors. Run the binary compatibility
  gate after additive recorder/sound APIs are introduced.
- Do not implement HRTF or advertise 5.1 support.

## Phase 1: Capture Test Seams and Session Contract

- [ ] Add a narrow injectable capture backend around ALC enumerate/open/start/
      available/read/stop/close operations.
- [ ] Add immutable capture spec, progress snapshot, terminal result/error, and
      a thread-safe session state machine with cancel precedence.
- [ ] Retain the current `OpenALRecorder` constructor/`Runnable`/`stop` surface
      as a delegating compatibility path; add explicit cancel/result APIs and
      cap the byte/duration size materialized for its `Consumer<Wave>` callback.
- [ ] Add fake-backend tests for state transitions, repeated terminal calls,
      stop/cancel races, failure injection, and exact cleanup call counts.

Primary files:

- `new/src/client/java/mchorse/bbs_mod/ui/film/audio/OpenALRecorder.java`
- new package-private capture backend/session/result types beside it
- the focused client regression test source set used by `new/`

## Phase 2: Streaming Capture and Final Drain

- [ ] Replace the geometrically growing direct buffer with a streaming temp
      sink fed by reusable fixed-size chunks and a bounded queue.
- [ ] Keep the one-second OpenAL capture ring or an equivalent capacity derived
      from sample rate/poll cadence; fail explicitly on sink overload.
- [ ] Correct per-frame/per-channel signed sample indexing for meters and add a
      fixed-size waveform snapshot ring without modifying captured bytes.
- [ ] Implement stop order: stop device, drain all complete available frames,
      flush/finalize sink, close device, then publish exact frame count.
- [ ] Implement cancel/failure cleanup that deletes only the session-owned temp
      file and closes each resource once, retaining suppressed cleanup errors.
- [ ] Cover mono/stereo capture descriptors, unsupported mode, tail drain,
      empty input, partial final chunk, long bounded capture, disk failure, and
      interruption in deterministic tests.

## Phase 3: Recorder UI, Timeline, and Client Lifecycle

- [ ] Restore the applicable upstream UX: film-based unique name suggestion,
      bounded waveform, count-in, scene playback, explicit finish/cancel, and
      cursor restoration using current NeoForge/Minecraft APIs.
- [ ] Capture panel identity, UI/session generation, and origin tick when the
      session is armed. Keep transport stationary through count-in and start it
      only after the device reaches recording state.
- [ ] Dispatch all terminal handling to the Minecraft client thread. Fence the
      callback by session and panel generation before file commit or UI changes.
- [ ] Reserve a non-existing final asset path, write to a unique temp path, and
      commit without overwrite. Surface errors instead of empty catch blocks.
- [ ] Insert/select a clip only after durable success. Set `clip.tick` to the
      captured origin and duration to ceiling frame-to-tick conversion.
- [ ] Make overlay/screen/film replacement, disconnect, world unload, and
      client stopping call the same idempotent capture-session cancel before
      dropping the dashboard/panel owner.
- [ ] Test count-in cancel, active cancel, late callback fencing, origin
      placement, final partial tick, name collision, commit failure, client-
      thread mutation, and lifecycle teardown.

Primary files:

- `new/src/client/java/mchorse/bbs_mod/ui/film/audio/UIAudioRecorder.java`
- `new/src/client/java/mchorse/bbs_mod/BBSModClient.java`
- `new/src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java` as needed for
  an owned-session cancel hook
- current localization resources/UI keys for actionable failures and count-in

## Phase 4: Per-Clip Playback Identity and Reconciliation

- [ ] Add a stable playback-owner token/generation to each camera/film clip
      context or its controller lifecycle.
- [ ] Change desired audio state from `Map<Link, Playback>` to identity-keyed
      `(owner, AudioClientClip)` entries carrying link, offset, gain, and
      transport state.
- [ ] Make `AudioClientClip.applyClip` emit desired state only. Do not call
      `playUnique` and do not stop by link from per-clip shutdown.
- [ ] Add `SoundManager.reconcile(owner, desired, playing)` and
      `releaseOwner(owner)`. Reuse one decoded buffer per link and one source
      per voice key.
- [ ] Reconcile on every evaluation, including an empty desired map. Remove
      `WorldFilmController`'s early return that skips empty audio cleanup.
- [ ] Feed world-controller pause and editor-runner transport state explicitly;
      seek before play on creation/resume/material drift and update paused
      scrubs without starting playback.
- [ ] Test same-link overlap, independent gain/offset, two owners, pause/resume,
      seek, expiry, empty timeline, disable/remove/relink, missing asset, and
      idempotent owner shutdown.

Primary files:

- `new/src/client/java/mchorse/bbs_mod/camera/clips/misc/AudioClientClip.java`
- `new/src/main/java/mchorse/bbs_mod/utils/clips/ClipContext.java` and/or
  `new/src/main/java/mchorse/bbs_mod/camera/clips/CameraClipContext.java`
- `new/src/client/java/mchorse/bbs_mod/camera/controller/CameraWorkCameraController.java`
- `new/src/client/java/mchorse/bbs_mod/camera/controller/RunnerCameraController.java`
- `new/src/client/java/mchorse/bbs_mod/film/WorldFilmController.java`

## Phase 5: OpenAL Resource Ownership and Global Teardown

- [ ] Make `SoundPlayer` source teardown idempotent: stop, detach buffer, delete
      source, then invalidate the handle.
- [ ] Track all film and compatible legacy sources attached to each cached
      buffer. Delete a buffer only after every source is detached/deleted.
- [ ] Fix link invalidation/deletion to release every matching voice rather than
      the first iterator entry. Preserve other links and playback owners.
- [ ] Marshal watchdog invalidation onto the client/OpenAL thread and lazily
      rebuild a buffer on the next desired reconciliation.
- [ ] Update resource reload, controller replacement, disconnect/world unload,
      and client stop so owner/global teardown occurs before references are
      cleared. Continue cleanup after individual failures.
- [ ] Test exact OpenAL call order with a fake backend, repeated deletion,
      source-creation failure, buffer invalidation with multiple voices, global
      reset, and one failing cleanup step followed by successful later steps.

Primary files:

- `new/src/client/java/mchorse/bbs_mod/audio/SoundManager.java`
- `new/src/client/java/mchorse/bbs_mod/audio/SoundPlayer.java`
- `new/src/client/java/mchorse/bbs_mod/audio/SoundBuffer.java`
- `new/src/client/java/mchorse/bbs_mod/mixin/client/ResourceReloadLoggerMixin.java`
- controller/client lifecycle call sites identified in Phase 4

## Phase 6: Compatibility and Validation

- [ ] Keep legacy one-shot and `playUnique` behavior compiling while proving
      film playback no longer uses link-global identity.
- [ ] Verify mono/stereo buffer upload and stereo left/right impulse identity;
      reject layouts above stereo without fallback.
- [ ] Run focused capture/playback regressions under fake backends, then the
      existing client structural/registration and binary compatibility gates.
- [ ] Run from `new/`:

```powershell
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon testBinaryCompatibility
.\gradlew.bat --no-daemon check
git diff --check
```

- [ ] Run an actual client smoke with a mono microphone: finish after a partial
      tick, cancel once during count-in and once during capture, unplug/deny the
      device, pause/resume preview, overlap two same-asset clips, relink/remove
      one, reload resources, disconnect, and confirm no stale audio or temp file.
- [ ] If stereo capture hardware is available, record a left/right fixture and
      verify channel identity. Otherwise record hardware stereo validation as
      pending while keeping the fake-backend stereo test authoritative.
- [ ] Record physical-device/OpenAL smoke separately from automated results;
      never claim it passed based only on fake-backend tests.

## Review Gates

- Capture review: stop drains; cancel never commits; memory/queue capacity is
  bounded; all device/sink failure paths clean up once.
- Thread review: capture worker never mutates UI, clips, transport, or OpenAL
  playback objects; every late callback has a generation fence.
- Playback review: voice identity includes owner and clip identity; empty-state
  reconciliation and owner release cover every terminal controller path.
- Resource review: every source is stopped/detached/deleted before its buffer;
  link invalidation handles all matches and cleanup is idempotent.
- Scope review: mono/stereo only, no HRTF/5.1 claim, and sibling export/mixer
  responsibilities remain untouched.
