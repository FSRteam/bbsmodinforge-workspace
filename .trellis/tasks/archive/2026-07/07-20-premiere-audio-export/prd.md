# Premiere audio and XMEML correctness

## Goal

Make the `bbs-premiere-export` integration a deterministic consumer of a
completed video export. It must emit individual WAV excerpts and one XMEML v5
sequence whose timing, channel routing, media metadata, and referenced video
artifact describe the files that were actually produced.

## Scope

This task owns the Premiere integration and its cross-layer completion contract.
It includes the existing dirty worktree in `bbs-premiere-export/`; those edits
must be preserved and brought into the final implementation. The implementation
may add the smallest compatible API surface in the core `new/` export session so
the integration receives a typed result and the real output artifact.

Mono and stereo are the only selectable delivery layouts. A reserved 5.1
identifier may exist in shared models for forward compatibility, but this task
must reject it and must not mix, route, encode, or advertise 5.1. HRTF and other
spatializers remain deferred.

## Requirements

1. **Typed completion and artifact binding**
   - Premiere generation starts only from the owning export session's terminal
     result, not from a `recording -> idle` poll or a directory timestamp scan.
   - The result distinguishes success, cancellation, and failure and carries a
     session-owned video path plus the encoded frame-rate/size metadata (or a
     probeable artifact descriptor).
   - A callback/result is delivered at most once. A failed or cancelled video
     export never creates an apparently successful XML package.

2. **Stereo-safe excerpts**
   - Source channel order and channel count survive import and excerpting.
   - The selected layout is applied explicitly: mono output is a documented
     downmix, stereo output preserves L/R and explicitly upmixes mono sources.
   - Excerpt boundaries are frame based and deterministic for 44.1 kHz and
     48 kHz sources. Source offset is applied exactly once, including when a
     loop begins inside an audio clip.
   - Volume and any conversion operate on all supported samples; unsupported
     encodings fail with a typed error instead of silently producing an empty or
     unscaled file.

3. **Loop and timeline semantics**
   - The requested half-open export range `[rangeStart, rangeEnd)` is captured
     with the export request and is not reconstructed later from mutable UI
     state.
   - Each clip is intersected with that range. Its output placement is relative
     to `rangeStart`; its WAV is already trimmed to the intersected source
     region, so XMEML uses `in=0` and never applies the source offset again.
   - Clips outside the range are omitted. Empty intersections are not emitted.

4. **Real media descriptors and XMEML**
   - Every generated WAV and the completed video is described from the actual
     file (sample rate, depth, channel count, frame/duration count, dimensions,
     and frame-rate rational where applicable).
   - XMEML v5 contains metadata consistent with those descriptors, escaped file
     URIs, stable ids, and no references to missing or pre-existing files.
   - Mono emits one source track per clip and one sequence output channel.
     Stereo emits paired left/right source tracks for each clip, both pointing
     at the same stereo media file with explicit source track indices, and two
     sequence output channels. File descriptors are not duplicated with
     contradictory channel counts.

5. **Frame-rate and NTSC correctness**
   - The XML rate is derived from the encoded artifact's actual rational rate.
     `<ntsc>TRUE</ntsc>` is used only for a genuine 1001-denominator NTSC rate;
     exact integer 24/30/60 media is marked `FALSE`. A UI flag cannot make
     metadata lie about the file.
   - Tick/frame conversion uses one shared rational conversion policy and
     half-open boundaries. Video and audio placement must differ by no more
     than one output frame.

6. **Atomic package and observable errors**
   - WAVs, XML, and any manifest are written into a uniquely owned staging
     directory/file set, validated, and published atomically. Existing user
     WAV/video/XML files are never overwritten or deleted.
   - A missing asset, decode/probe failure, rename/move failure, or XML failure
     produces a typed failed/degraded result and a bounded log entry with the
     session id and path. Partial staging output is cleaned only when owned by
     this session.
   - If a caller elects to retain a usable video when audio/XML fails, the
     result must say `DEGRADED` and identify the missing artifact; it must not be
     reported as an unqualified success.

7. **Compatibility and ownership**
   - Consume the shared Mono/Stereo setting and its documented legacy default;
     do not introduce a second channel-layout setting in the Premiere plugin.
   - Keep the current settings/button registration changes compatible, but make
     registration and completion wiring idempotent so the button or listener
     cannot be installed twice.

## Acceptance Criteria

- [ ] A mono impulse and a stereo L/R impulse export with exact channel identity
      through WAV and XMEML; no call path uses an unconditional mono excerpt.
- [ ] A clip with a non-zero source offset and a loop beginning mid-clip has the
      expected first sample and has `in=0` in XMEML (offset applied once).
- [ ] 44.1 kHz and 48 kHz fixtures produce expected sample/frame counts and
      placement with no half-frame channel shift.
- [ ] Mono XML has `numOutputChannels=1`, one track per clip, and channelcount 1;
      stereo XML has two paired tracks per clip, source track indices 1 and 2,
      and channelcount/output count 2.
- [ ] XML metadata matches probes of the actual WAV and MP4, including URI,
      duration, dimensions, sample rate, and channel count.
- [ ] Exact 24/30/60 media is NTSC FALSE; 24000/1001, 30000/1001, and
      60000/1001 media are NTSC TRUE with the expected nominal timebase.
- [ ] F4, F6, and panel completion paths use the same typed result; cancellation,
      FFmpeg failure, missing video, and missing audio each avoid false success.
- [ ] Repeated completion events produce one package; a pre-existing file with
      the same human name is preserved and a unique package is chosen.
- [ ] An injected write/probe/rename failure leaves no published partial XML or
      WAVs and reports the failure with a session-owned path.
- [ ] Unit, integration, and (when FFmpeg/ffprobe are installed) real-media
      tests pass, along with compilation and `git diff --check`.

## Out of Scope

- 5.1/7.1 mixing, speaker routing, or UI selection.
- HRTF/binaural rendering, HRIR data, Atmos, Ambisonics, or live streaming.
- Replacing the core audio mixer or recorder lifecycle owned by sibling tasks.

## Dependencies

- The audio format/mixer tasks provide supported PCM conversion, explicit
  channel-layout semantics, and a safe WAV writer.
- The audio export lifecycle task provides the shared Mono/Stereo setting,
  migration default, and typed video-session completion/artifact contract.
- This task adds the Premiere projection and tests against those contracts; it
  must not fork their format or outcome definitions.
