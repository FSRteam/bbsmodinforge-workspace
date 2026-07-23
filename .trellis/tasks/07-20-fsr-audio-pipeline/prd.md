# FSR audio pipeline reliability and channel export

## Goal

Make FSR audio recording, preview, film rendering, Minecraft-sound capture,
video muxing, and editing-tool export reliable enough for production use while
letting users choose mono or stereo delivery without destroying source-channel
information.

## Confirmed Scope

- Target the current clean `new/` FSR/NeoForge 1.21.1 repository.
- Address all verified P1/P2 audio findings from the 2026-07-20 audit, with
  focused P3 fixes where they share the same code path.
- Support mono and stereo export in this implementation.
- Reserve a 5.1 layout identifier for forward compatibility, but do not expose,
  mix, encode, or claim support for 5.1 yet.
- Do not implement HRTF/binaural rendering in this task. Record it as a future
  optional spatializer phase and keep the audio model extensible for it.
- Preserve the stronger current FSR video-process lifecycle behavior while
  restoring missing upstream FS audio behavior where applicable.

## Requirements

- Audio import must preserve mono and stereo sources by default; conversion to
  mono must be explicit rather than an unconditional importer side effect.
- PCM and WAV decoding must distinguish supported integer and floating-point
  encodings, reject unsupported encodings, and preserve correct sample signs,
  frame boundaries, channel order, duration, and metadata.
- Film rendering must correctly mix supported source rates, bit depths, mono,
  and stereo inputs without per-track clipping discontinuities or order-dependent
  results.
- Export settings must offer `Mono` and `Stereo`. The selected layout must be
  applied consistently to rendered WAV intermediates, Minecraft-sound mixes,
  FFmpeg arguments, final media metadata, and user-visible settings.
- Existing settings must migrate compatibly. A missing/legacy channel setting
  must resolve to a documented default.
- Microphone recording must have explicit lifecycle ownership, device-failure
  reporting, bounded or streaming storage, final-sample draining, cancellation
  that does not save a take, correct timeline placement, and client-thread UI
  mutation.
- Preview playback must pause and resume with the owning film transport, support
  overlapping clips that use the same asset, stop removed/disabled/relinked
  voices, and release sources/buffers in a valid order.
- Minecraft-sound export must retain the film track on recoverable failures,
  preserve continuous pitch phase and stereo assets, avoid client-thread
  sample-by-event scaling, and provide progress/cancellation for long mixes.
- F4/F6 and panel export must share explicit start/end timing, preserve the
  requested loop range, and keep audio/video alignment within one output frame.
- Requested audio failures must not be reported as an unqualified successful
  export. A degraded output, if retained, must be explicit to the user/caller.
- Strict media timeout cleanup must use a 750 ms process-teardown deadline
  inside a separate one-second return budget.
- Temporary audio and mux artifacts must be uniquely session-owned and must
  never overwrite or delete a pre-existing user file.
- Default final encoding must use one documented audio delivery profile; a
  lossless intermediate/master path must avoid repeated lossy encoding.
- Audio failures must be observable through bounded logs and user-facing export
  results rather than swallowed exceptions.
- Automated tests must cover sample correctness, channel routing, resampling,
  clipping/limiting, exact duration, recorder teardown, playback ownership,
  export failure semantics, and real FFmpeg metadata/A-V timing where available.
- The current runtime Core version is `2.3.2`. Premiere must require
  `[2.3.2,)` and resolve only the final
  `new/build/libs/BBS_FSR_beta_v0.0.10.jar`; no older jar name or compatibility
  alias remains an active dependency.
- The existing `bbs-premiere-export/` audio/XML integration is included in this
  task; its pre-existing worktree changes must be preserved and incorporated,
  never discarded.

## Acceptance Criteria

- [x] Mono and stereo sources import without implicit channel destruction.
- [x] Golden PCM fixtures for 8/16/24/32-bit integer and 32-bit float either
      decode exactly or fail with a supported-format error.
- [x] Mono and stereo export settings produce files whose probed channel count,
      sample rate, duration, and declared metadata match the selection.
- [x] Stereo left/right impulse fixtures retain their channel identity through
      import, film mix, Minecraft mix, WAV output, and final mux.
- [x] 44.1 kHz and 48 kHz fixtures render with the expected frame count and no
      half-frame channel misalignment.
- [x] Mix output is independent of clip iteration order within numeric tolerance,
      and clipping/limiting is applied once at the master boundary.
- [x] Stopping or cancelling microphone capture closes/frees each owned resource
      exactly once; stop drains the tail, while cancel creates no clip or asset.
- [x] A recorded microphone clip begins at the timeline position captured when
      recording started and includes the final partial tick.
- [x] Same-asset overlapping clips preview as independent voices; pause, removal,
      relink, shutdown, disconnect, and reload leave no stale voice/resource.
- [x] F4 has a normal completion path; F6 and panel loop exports meet the
      one-output-frame A/V alignment requirement.
- [x] Mix, mux, rename, missing-resource, and cancellation outcomes are distinct,
      tested results; existing user WAV/video files are preserved.
- [x] A long/high-event-count mix is block-based, cancellable, and does not run
      unbounded work on the client thread.
- [x] The 5.1 identifier cannot be selected or exported and is never advertised
      as implemented.
- [x] Relevant compilation, unit/integration tests, and `git diff --check` pass.

## Out of Scope

- Actual 5.1/7.1 mixing, encoding, speaker routing, or UI selection.
- HRTF/binaural rendering, HRIR data, and headphone-specific spatial output.
- Atmos, Ambisonics, and vertical spatial audio.
- Live network audio streaming or browser-remote audio.
- Replacing Minecraft/OpenAL itself or capturing the operating-system loopback.

## Deferred Follow-up

- Add an optional HRTF/binaural spatializer for headphone delivery. It should
  share the same source/event model as the mono/stereo renderer and be tested
  for preview/export parity before being exposed as a user-selectable mode.

## Child Task Map

- `07-20-audio-format-contract`: explicit formats, channel layouts, safe
  readers/writers, and non-destructive import.
- `07-20-audio-mixer-render`: block renderer, resampling, spatial stereo,
  limiting, and Minecraft/film mix convergence.
- `07-20-audio-capture-playback`: microphone and OpenAL playback ownership.
- `07-20-audio-export-lifecycle`: Mono/Stereo setting, timing, FFmpeg, and
  typed completion outcomes.
- `07-20-premiere-audio-export`: media excerpts, channel metadata, XMEML, and
  output-artifact binding.
- `07-20-audio-verification`: sample-level, lifecycle, performance, and real
  media verification across the tree.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
