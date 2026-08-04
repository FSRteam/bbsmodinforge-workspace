# Premiere export implementation plan

## Guardrails

- Work in the existing dirty tree. Before each edit, inspect `git diff` for the
  target file and preserve unrelated changes; do not reset, checkout, or clean
  generated files that belong to the user.
- Keep the shared audio-layout enum, migration default, PCM codec, and recorder
  outcome definitions owned by their sibling tasks. Premiere consumes those
  contracts rather than adding local copies.
- Keep all public changes source-compatible where feasible. Retain a deprecated
  adapter for the current boolean completion listener while migrating the
  Premiere handler to the typed result.

## Ordered work

### 1. Establish the cross-layer completion contract

- [ ] In the core export lifecycle, expose an immutable completion/artifact
      value with session id, terminal result, canonical video path, actual
      rational frame rate, dimensions, encoded frame count, requested range,
      and failure cause.
- [ ] Make `VideoRecorder`/`VideoExportSession` publish the artifact only after
      FFmpeg has a successful terminal outcome and a regular file is present;
      cancellation and early process exit must not publish it.
- [ ] Keep the existing `FinishedListener(boolean)` adapter and add tests for
      exactly-once delivery, re-entrant completion, and stale session fencing.
- [ ] Coordinate the API names with `07-20-audio-export-lifecycle` before
      touching callers. If that task already supplies the contract, consume it
      instead of introducing a second result type.

### 2. Capture an immutable Premiere request and remove polling heuristics

- [ ] Refactor `PremiereExportHandler` to arm one listener per request before
      recording starts and retain an immutable request/manifest seed (Film id or
      snapshot, loop range, layout, frame-rate policy, session id).
- [ ] Replace `wasRecording` and `findLatestMp4` with the typed completion's
      artifact. Ignore callbacks for another session and fence duplicate
      callbacks.
- [ ] Consolidate `PremiereExportButton` and
      `PremiereExportButtonInstaller` so only one button/listener path is
      installed; make initialization idempotent. Preserve the current dirty
      settings registration behavior while avoiding duplicate UI actions.
- [ ] Route all user-visible completion/failure messages through the client
      thread and include a bounded reason (cancelled, video failed, audio failed,
      XML failed, degraded).

### 3. Implement a shared clip-plan/timing projection

- [ ] Add a pure clip-plan helper near the exporter. Inputs are clip tick,
      duration, source offset, requested half-open range, source frame count,
      source sample rate, and output frame rate; output contains source frame
      start/end and rebased timeline frame start/end.
- [ ] Use the intersection equations in `design.md`; clamp to source length and
      reject non-positive durations. Use overflow-safe rational arithmetic,
      ceil half-open source sample boundaries, and independently rounded
      sequence start/end boundaries. Keep source offset in exactly one field in
      the plan and set serialized XMEML `in=0`.
- [ ] Add tests for full-range, loop-mid-clip, clip-before/after-loop,
      non-zero offset, negative offset, and 44.1/48 kHz rounding.

### 4. Make `IndividualAudioExporter` channel preserving and descriptor based

- [ ] Change the return value from parallel `ExportedClip` fields to a typed
      descriptor that binds clip identity, staging WAV path, source/output
      channels, sample rate/depth, sample frames, and timeline/source ranges.
- [ ] Replace `Wave.excerptMono` with the shared frame-based excerpt/conversion
      API. Preserve stereo interleaving; explicitly downmix mono and upmix
      mono-to-stereo according to the shared layout contract. Reject 5.1.
- [ ] Apply volume through the supported sample conversion path and fail loudly
      for unsupported formats; do not silently skip non-16-bit samples.
- [ ] Write to a unique session-owned temporary file, close and probe it, then
      atomically publish it. Append a descriptor only after validation; clean
      only files created by this session.
- [ ] Add fixture tests that assert exact first/last samples and L/R impulse
      identity after each layout choice.

### 5. Add actual media probing and XMEML projection

- [ ] Add/reuse a bounded `MediaProbe` for WAV and MP4. Verify regular-file
      ownership, URI, duration/frame count, dimensions, frame-rate rational,
      sample rate, depth, and channels before XML generation.
- [ ] Refactor `XmemlGenerator` to accept one complete manifest, use video
      descriptor duration/rate, and XML-escape every name/path/id value.
- [ ] Implement mono routing (one track, one source index, one output channel)
      and stereo routing (paired tracks with source indices 1/2, shared file id,
      two output channels). Keep `in=0`, `out=excerpt frames`, and placement
      relative to the captured loop start.
- [ ] Update `TickToFrameConverter` or replace it with the shared rational
      converter. Emit NTSC TRUE only for actual 1001-denominator rates; exact
      integer rates are FALSE. Add XML tests for 24, 30, 60, 24000/1001,
      30000/1001, and 60000/1001.

### 6. Publish the package atomically

- [ ] Build a session-owned staging directory and unique final directory. Write
      WAVs and XML under temporary names, flush/close, probe, and atomically move
      only after all requested artifacts validate.
- [ ] Define the failure policy: default requested individual-audio failures
      fail the package; an explicitly retained video is marked `DEGRADED` and
      names the missing artifact. Never log a degraded result as success.
- [ ] Ensure duplicate requests cannot overwrite a pre-existing user file and
      cancellation/failure cleanup is idempotent.
- [ ] Add injected filesystem/probe/move failure tests and a pre-existing-file
      preservation test.

### 7. Verification and handoff

- [ ] Run focused exporter tests and XML/descriptor tests.
- [ ] Run core compile/tests covering the completion contract and existing video
      lifecycle regressions.
- [ ] Run the exporter Gradle build against the current sibling `new` jar.
- [ ] When FFmpeg and ffprobe are available, run real-media tests and compare
      probed metadata and A/V duration; otherwise record the explicit skip.
- [ ] Run `git diff --check` in both repositories and inspect the final diff for
      accidental changes to the pre-existing dirty worktree.
- [ ] Update the parent task with the exact contract names, tests run, and any
      remaining dependency on the lifecycle/mixer tasks.

## Suggested file ownership

| Area | Primary files | Notes |
| --- | --- | --- |
| Completion/artifact API | `new/src/client/java/mchorse/bbs_mod/film/VideoExportSession.java`, `new/src/client/java/mchorse/bbs_mod/utils/VideoRecorder.java` | Coordinate with export-lifecycle owner. |
| Clip projection/WAVs | `bbs-premiere-export/src/client/java/mchorse/bbs_premiere_export/audio/IndividualAudioExporter.java` plus new timing/descriptor helpers | Keep layout/codec logic shared. |
| Handler/session fencing | `bbs-premiere-export/src/client/java/mchorse/bbs_premiere_export/client/PremiereExportHandler.java`, button installer | Preserve existing UI changes; one registration path. |
| XML/rate metadata | `bbs-premiere-export/src/client/java/mchorse/bbs_premiere_export/xml/XmemlGenerator.java`, `.../util/TickToFrameConverter.java` | Generate only from descriptors. |
| Tests | exporter test source set plus `new` video-export tests | Include pure, integration, and optional real-media layers. |

## Risks and decisions

- If FFmpeg does not expose a stable output path, make the recorder choose a
  unique movie basename before launch and return that exact path; directory
  scanning by mtime is not an acceptable fallback.
- If Premiere requires a particular XMEML `<link>` shape for stereo, encode it
  from the same paired-track descriptor and lock it with an importer fixture;
  do not duplicate channel metadata in ad hoc branches.
- If a loop range is changed after recording starts, the captured request wins;
  mutable editor state must never alter a completed package.
- If a core API change cannot land in this task's dependency window, stop at the
  adapter boundary and report the missing typed artifact contract rather than
  silently restoring `isRecording` polling.
