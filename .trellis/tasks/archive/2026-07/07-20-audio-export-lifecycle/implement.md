# Video Audio Export Lifecycle Implementation Plan

## 1. Settings And Request Contract

- [ ] Add the persisted layout setting and UI values `Mono`/`Stereo` only.
- [ ] Normalize missing/invalid/reserved values to Mono at one boundary.
- [ ] Add immutable export timing/layout/artifact request and snapshot it before
      recorder reservation.
- [ ] Add settings serialization, migration, range-normalization, and snapshot
      tests.

Gate: legacy settings produce Mono; explicit Stereo round-trips; `5.1` cannot
be selected or carried into a request.

## 2. Typed Completion And State Machine

- [ ] Introduce typed export result/stage/artifact data while preserving a
      legacy boolean-listener adapter.
- [ ] Add finalizing/postprocessing states to the current session ownership.
- [ ] Keep reservation, UI restoration, teardown, callback, and failure
      aggregation idempotent and generation-fenced.
- [ ] Route requested audio loss to degraded/failure, never plain success.

Gate: focused session tests cover every result kind and one terminal callback.

## 3. Timing And Hotkey Semantics

- [ ] Share one normalized half-open range between panel video and audio.
- [ ] Make delivered video frames authoritative and trim/pad PCM duration using
      rational conversion.
- [ ] Drain the recorder's final frame before freezing duration.
- [ ] Change the second F4 press to normal stop; retain explicit cancel for
      abort/unload/disconnect. Preserve F6 cancellation/natural-end behavior.

Gate: 24/30/60 FPS range fixtures align endpoints within one output frame and
F4 stop/cancel tests produce distinct results.

## 4. Session-Owned Artifacts

- [ ] Allocate a unique work directory and exact raw/WAV/mux/final paths.
- [ ] Remove newest-file scans and predictable `.tmp`/WAV names.
- [ ] Track cleanup ownership, publish without replacement, preserve recovery
      artifacts on publish/degraded failures, and aggregate cleanup failures.
- [ ] Pass the exact terminal artifact descriptor to completion consumers.

Gate: collision and pre-existing-file tests prove no user artifact is replaced
or deleted on success, failure, or cancellation.

## 5. Async Render And Mux

- [ ] Run block PCM render/mix and post-mux on a bounded worker executor.
- [ ] Thread cancellation/progress through each stage and marshal callbacks/UI
      changes to the client thread.
- [ ] Ignore stale callbacks by session generation and keep ownership until
      postprocessing completes.
- [ ] Preserve existing FFmpeg timeout, diagnostic, and teardown behavior.

Gate: fake slow render/mux tests prove prompt cancellation, monotonic bounded
progress, client-thread callbacks, and no premature success.

## 6. Unified Delivery Profile

- [ ] Define one AAC-LC 48 kHz 192 kb/s delivery profile for direct and mux
      paths with explicit maps and `-ac` from Mono/Stereo.
- [ ] Keep intermediates lossless and perform one AAC encode after Minecraft
      mixing while copying video.
- [ ] Validate structured custom-template placeholders before process launch.
- [ ] Expose final codec/rate/channels/duration/artifact metadata in the result.

Gate: argument tests for both paths match; optional ffprobe fixtures confirm the
published stream contract.

## 7. Integration

- [ ] Update F4, F6, panel export, completion effects, and Premiere handoff to
      consume the typed result and exact artifact.
- [ ] Add bounded stage logs and a user-visible degraded/failure message.
- [ ] Run session, export-utils, audio, compile, integration, and diff checks.

## Validation Commands

```powershell
cd new
.\gradlew.bat --no-daemon testVideoExportSession
.\gradlew.bat --no-daemon testVideoExportUtils
.\gradlew.bat --no-daemon testAudioFormat
.\gradlew.bat --no-daemon testAudioMixer
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon check
git diff --check
```

Run strict optional FFmpeg/ffprobe verification when the tools are available.

## Rollback Points

- The setting/request layer can land before session state changes.
- Typed results retain legacy adapters until all callers migrate.
- Async postprocess and owned publication stay behind current recorder
  boundaries and can be reverted without weakening existing process cleanup.
- Never roll back by deleting pre-existing outputs or reverting unrelated
  `bbs-premiere-export/` changes.
