# Implementation Plan: Block Audio Mixer And Channel Rendering

## Ownership And Boundaries

This plan owns the renderer/mixer contract and its planning artifacts. It does
not own the decoder/import rewrite, microphone capture lifecycle, playback
ownership, or FFmpeg process fixes except where those layers must pass the new
typed render request/result. Coordinate those changes through the parent
`07-20-fsr-audio-pipeline` task and preserve pre-existing
`bbs-premiere-export/` edits.

Primary implementation files are expected under `new/`:

- `src/client/java/mchorse/bbs_mod/audio/AudioRenderer.java`
- `src/client/java/mchorse/bbs_mod/audio/MinecraftSoundMixer.java`
- new shared renderer/request/result classes beside them, if needed;
- `src/main/java/mchorse/bbs_mod/audio/Wave.java` and
  `src/main/java/mchorse/bbs_mod/audio/wav/WaveWriter.java` only for the
  narrow shared PCM contract;
- `BBSSettings.java`, `VideoExportSession.java`,
  `PanelVideoExportSession.java`, `WorldVideoExportSession.java`,
  `UIFilmPreview.java`, and `VideoMuxer.java` only for request/result/layout
  plumbing.

Do not copy an entire upstream commit or edit `temp/bbs-fs-2.3`. Use
`eef239e5` as the capture/event origin and `f76cce04` as the behavior reference,
then adapt to the current NeoForge/FSR APIs.

## Phase 0: Contract And Baseline Audit

1. Validate the immutable history markers from
   `.trellis/spec/new/backend/fs-migration-baseline.md`; confirm
   `eef239e5` is an ancestor of `f76cce04` in `temp/bbs-fs-2.3` and that the
   current `new/` checkout is the target branch.
2. Record current callers and behavior before edits:
   `AudioRenderer.renderAudio`, `MinecraftSoundMixer.mixToFile`,
   `VideoExportSession.finishCapturedSounds`, panel/F4/F6 range handling, and
   `BBSSettings` audio keys.
3. Freeze the canonical terms and test vectors in code/test notes:
   `AudioLayout`, reserved `SURROUND_5_1`, half-open range, 44.1/48 kHz,
   8,192-frame block target, equal-power mono center, 0.5/0.5 stereo downmix,
   and one final limiter/quantizer.

Exit condition: no caller derives output channels or duration independently,
and any decoder assumptions are listed as explicit preconditions.

## Phase 1: Pure Block Core

1. Add the immutable request/layout/result types and validation. Keep them free
   of Minecraft client state so unit tests can run on synthetic sources.
2. Add a decoded-source view that exposes validated interleaved channels,
   source rate, frame count, and metadata without changing channel order.
3. Implement reusable `SourceCursor` logic for fractional resampling,
   continuous pitch phase, loop wrapping, timeline clipping, and per-frame
   gain updates. Ensure cursor state survives a block boundary.
4. Implement the explicit 1/2-channel matrix and a block accumulator. Stable
   ordering should use timeline start plus capture/clip sequence/source id, and
   accumulation should tolerate harmless floating-point order noise.
5. Implement the single master limiter and signed 16-bit little-endian packer;
   no `Wave.add`-style per-track normalization, clipping, or quantization may
   remain in the core path.
6. Add pure tests before wiring callers: impulses, correlated stereo,
   unsupported channels, resampling, pitch/loop continuity, exact frame count,
   block-edge events, limiter placement, and order tolerance.

Exit condition: a synthetic request renders deterministic mono/stereo bytes
without Minecraft classes, with bounded memory proportional to one block.

## Phase 2: Film Audio Adapter

1. Change `AudioRenderer.renderAudio` to build a request from enabled clips,
   explicit `[from,to)` timing, output rate, and resolved `AudioLayout`.
2. Decode each clip once during preparation; propagate unsupported/missing
   format errors instead of adding a null entry and later writing silence.
3. Preserve source channels until the core matrix. Remove the implicit
   `excerptMono`/mono-only post-processing from the new path; retain it only in
   a compatibility helper that is called when a caller explicitly requests
   Mono.
4. Keep the existing signature as a boolean adapter, but add an explicit typed
   result path for export sessions. Ensure `MemoryUtil`/temporary buffers are
   released in all terminal paths.
5. Add film-only tests for mono/stereo fixtures, range clipping, source-rate
   conversion, and exact WAV header/data lengths.

Exit condition: `UIFilmPreview`, `PanelVideoExportSession`, and
`WorldVideoExportSession` can request either layout without source-channel loss.

## Phase 3: Minecraft Event Adapter

1. Convert `MinecraftSoundMixer.mixToFile` to create immutable event cursors
   from `CapturedSound`, `LoopFrame`, and `ListenerFrame`. Cache each distinct
   resource once during preparation, using the client-owned resource snapshot
   before worker execution.
2. Port the useful upstream `f76cce04` semantics: per-event continuous
   `sourcePosition`, linear interpolation, frame-indexed pitch/gain updates,
   constant-power mono positioning, and stereo-source preservation. Do not
   restore the old per-output-sample scan/allocation loop.
3. Mix film audio through the same cursor/matrix into the same block before the
   master boundary. Keep event and film ordering stable and independent of
   which list was supplied first.
4. Return typed missing-resource/empty/failure outcomes. A recoverable missing
   sound may be retained only as an explicitly degraded result chosen by the
   caller; it must not silently become success.
5. Add integration tests with left/right Minecraft impulses, listener pan and
   attenuation, loop pitch automation, mixed film+Minecraft sources, and event
   order permutations.

Exit condition: long mixes are block-based, preserve stereo assets, and match
the film adapter's layout/rate/duration contract.

## Phase 4: Async Export And Settings Plumbing

1. Add the persisted layout setting in `BBSSettings`; expose only Mono/Stereo,
   migrate missing/invalid values to Mono, and reject the reserved 5.1 token
   at request validation. Cover load/save migration tests.
2. Update `VideoExportSession` and panel/world/F4/F6 callers to construct one
   request with explicit layout and range. Keep the existing lifecycle ordering:
   capture stops/drains before mix, and mux promotion happens only after a
   successful mix.
3. Run the renderer on the export worker/executor, with cancellation checks at
   preparation and block boundaries and monotonic bounded progress callbacks.
   Marshal UI state changes to the client thread. Disconnect/world-unload and
   panel-close paths must call the same idempotent teardown owner.
4. Use a uniquely named session temp file and atomic promotion. On cancel,
   missing resource, mix failure, or mux failure, preserve pre-existing user
   files and return a distinct result to the UI/caller.
5. Pass the selected channel count/layout to `WaveWriter`, FFmpeg arguments,
   and final metadata probes. Keep one lossless PCM intermediate before the
   final delivery encode.

Exit condition: F4, F6, panel, and world exports agree on channels, duration,
failure semantics, and cleanup; the client/render thread does no unbounded mix.

## Phase 5: Verification And Handoff

Run from `new/`:

```powershell
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon check
git diff --check
```

Run the focused audio tests first, then media integration tests when FFmpeg is
available. Probe generated WAV/final media for channel count, sample rate,
frame/duration, and A/V alignment. Record unavailable external-tool checks as
pending rather than passing them implicitly. Manually smoke-test a long mix,
cancel, panel close, disconnect, F4, and F6; verify no stale temp file or stale
UI progress owner remains.

Before handoff, review the diff for:

- any accidental source-channel collapse or hidden 5.1/HRTF claim;
- any limiter/quantizer call before the master boundary;
- any client-thread resource-manager or sample loop;
- boolean success returned for cancellation/failure;
- changes outside the assigned renderer/plumbing scope or discarded existing
  `bbs-premiere-export/` work.

The parent task should then run the broader decoder, recorder, playback, mux,
and verification suites and record the terminal commit SHA in Trellis metadata.
