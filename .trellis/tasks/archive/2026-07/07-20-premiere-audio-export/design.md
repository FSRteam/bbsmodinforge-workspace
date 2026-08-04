# Premiere export design

## Current failure points

The dirty `bbs-premiere-export` tree currently has four independent sources of
truth: `IndividualAudioExporter` reads `AudioClip` values and calls
`Wave.excerptMono`, `PremiereExportHandler` polls `VideoRecorder.isRecording`
and finds the newest `.mp4`, `XmemlGenerator` hardcodes two audio tracks and
48 kHz/16-bit metadata, and `TickToFrameConverter` marks integer 24/30/60 rates
as NTSC without knowing the encoded rate. The generated WAV is already trimmed,
but XMEML still writes the original `clip.offset` as `<in>`, so an offset is
applied twice. The parallel `audioFiles` and `audioClips` lists also carry no
measured media metadata and make their association an unchecked convention.

The design below makes one immutable manifest the boundary between recording,
audio rendering, media probing, and XML generation.

## Data flow

```text
export request (Film + [rangeStart, rangeEnd) + layout)
        |
        v
typed VideoExportCompletion { sessionId, result, artifact }
        |
        +-- failure/cancel --> typed UI/log result; no XML package
        |
        v
PremiereExportManifest (immutable snapshot)
        |
        +-- clip plans --> channel-preserving WAV staging files
        |                    + probe/validate descriptors
        |
        +-- video artifact -> probe/validate descriptor
        |
        v
XmemlGenerator(manifest) -> temp XML -> validate -> atomic publish
```

The manifest owns the association between a clip, its WAV, its source channel
layout, its timeline placement, and its media descriptor. No consumer rebuilds
parallel lists or rereads mutable UI values.

## Contracts and ownership

### Completion boundary

The core export lifecycle should expose a versioned, immutable completion value
alongside its existing compatibility listener. The minimum shape is:

```text
VideoExportCompletion
  sessionId: UUID/string
  result: SUCCESS | CANCELLED | FAILED (optionally DEGRADED)
  artifact: VideoArtifact?       // only a validated produced video
  failure: Throwable?
  requestedRange: TickRange
  frameRate: Rational             // actual encoded rate, not just a setting
  width, height, encodedFrames
```

`VideoArtifact` contains the canonical path selected by the recorder/session,
not a path discovered by scanning a directory. The recorder must publish this
path only after FFmpeg exits successfully and the file exists; a nonzero exit,
cancel, or missing file is failure. Existing boolean `FinishedListener` callers
can be adapted from this value so the change is source compatible. The
Premiere handler subscribes to this value once per request and fences the
`sessionId` before doing any work.

If the core API cannot yet carry the full value, add a narrow adapter that
returns the session's planned movie name plus a validated artifact descriptor;
do not reintroduce `findLatestMp4`.

### Layout contract

Use the shared audio-export layout enum (`MONO`, `STEREO`, reserved `SURROUND_5_1`)
and its migration/default resolver. Premiere accepts only the first two and
fails closed for the reserved value. The layout is captured in the manifest,
so changing settings while an export is running cannot change its XML.

### Clip plan and exact timing

All calculations use integer ticks, source sample frames, and output frames;
floating-point seconds are used only at an API edge. For clip `C` and half-open
range `[R0, R1)`:

```text
C0 = C.tick
C1 = C.tick + C.duration
P0 = max(C0, R0)
P1 = min(C1, R1)
if P0 >= P1: omit

sourceOffsetTicks = C.offset + (P0 - C0)
placementTicks    = P0 - R0
excerptDuration   = P1 - P0
```

The audio renderer converts both half-open source boundaries with exact rational
arithmetic. For sample frames whose timestamp is `n / sampleRate`, use the first
frame at or after each boundary (`ceil(boundaryTicks * sampleRate / 20)`) and
then clamp the interval to the source length. This avoids including a sample
before the requested start and gives exact boundaries for 44.1/48 kHz integer
ticks. The generated WAV contains only that excerpt. Therefore the XMEML
clipitem always has `in=0` and never repeats `C.offset`.

Project the rebased sequence boundaries independently with one overflow-safe
rational converter:

```text
timelineStartFrame = round((P0 - R0) * rateNumerator / (20 * rateDenominator))
timelineEndFrame   = round((P1 - R0) * rateNumerator / (20 * rateDenominator))
```

Use `timelineEndFrame - timelineStartFrame` for XMEML `out` and clip duration;
do not add a separately rounded duration to a rounded start. Store the exact
source-frame and sequence-frame intervals in the descriptor for assertions and
diagnostics.

For a looped panel export, `R0/R1` come from the export session's captured loop
range. For F6, the session supplies the film's full range. F4 has no film/audio
manifest and therefore produces no Premiere audio clips. A loop's `R0` is not
subtracted in the audio writer and again in XML; the manifest is the sole place
where rebasing occurs.

### Channel-preserving audio projection

`IndividualAudioExporter` becomes a renderer of `ClipAudioDescriptor` values:

```text
ClipAudioDescriptor
  clipIdentity/sessionId
  wavPath (staging-owned)
  sourceChannels, outputChannels
  sampleRate, bitsPerSample, sampleFrames
  sourceStartFrame, sourceEndFrame
  timelineStartFrame, timelineEndFrame
  volume/appliedTransform
```

Read the source with the shared format contract, preserve interleaved L/R order,
and apply the selected layout explicitly. Stereo-to-mono uses the documented
downmix/limiter once; mono-to-stereo duplicates the mono sample to L and R.
Do not call `excerptMono` in the Premiere path. Use a shared frame-based excerpt
helper so 8/16/24/32-bit integer and float support, clipping, and conversion are
consistent with the mixer task. Volume is applied before the final master
conversion and is never silently skipped because a wave is not 16-bit.

Each output is written to a UUID/session-named temporary file, closed, probed,
and atomically renamed within the staging directory. The descriptor is appended
only after every write and probe succeeds. A failed clip aborts the requested
individual-audio package by default; an explicit degraded result may retain the
video but must identify the omitted clip.

### Media probing

Add a small `MediaDescriptor`/`MediaProbe` owner in the exporter (or consume the
shared probe from the lifecycle task). WAV descriptors can use the safe WAV
reader; MP4 descriptors should use `ffprobe` when available and verify:

- canonical regular-file path is inside the current session's output/staging
  roots;
- frame count/duration, width, height, and rational frame rate;
- audio sample rate, depth, and channel count when an audio stream exists.

The descriptor records the probe command/version or a bounded diagnostic, but
never embeds unbounded process output in logs. Probe failures are terminal for
XML generation.

### XMEML projection

`XmemlGenerator` takes a complete manifest and emits XML from descriptors only.
Use one file id per WAV and stable ids derived from the manifest index/session,
with all paths XML-escaped. Sequence duration is the video descriptor's frame
count. Video `<rate>`, `<duration>`, dimensions, and audio characteristics come
from probes.

For `MONO`, emit `<numOutputChannels>1</numOutputChannels>`, one `<track>` and
one `<clipitem>` per clip, `channelcount=1`, and a `<sourcetrack>` index of 1.
For `STEREO`, emit two paired tracks per clip, both referencing the same file
id, with `<sourcetrack><trackindex>1</trackindex>` and `2`; set
`channelcount=2` and `numOutputChannels=2`. Add the required `<link>` pairs if
Premiere's importer requires them, and test that left/right impulses remain
on their intended tracks. Never emit the current duplicated file placeholder
with contradictory metadata.

Every audio clipitem uses:

```text
start = timelineStartFrame
end   = timelineEndFrame
in    = 0
out   = sample/excerpt frame count converted to sequence frames
```

The descriptor's source interval is diagnostic only; it is not serialized as a
second offset.

### NTSC and rational rates

Replace the current boolean-only converter with a rational rate value carried by
the actual video descriptor. XMEML uses the nominal integer `timebase` (24/30/60)
and sets `<ntsc>TRUE</ntsc>` only when the actual rate is 24000/1001,
30000/1001, or 60000/1001 (or an explicitly supported equivalent). Exact
24/30/60 is `<ntsc>FALSE</ntsc>`. If a setting requests NTSC but the produced
file is integer-rate, report a metadata mismatch rather than lying in XML; the
encoding profile must be fixed by the lifecycle owner if conversion is desired.

### Atomic publication and errors

Create a session-owned staging directory such as
`<video>.premiere.<sessionId>.tmp`. Reject paths that escape it when writing.
Write and validate WAVs, then XML to temporary names, flush/close them, and
atomically move the completed package to a unique final directory. If atomic
move is unavailable, use a same-volume replace only for a newly reserved name;
never replace an existing user file. Cleanup is idempotent and deletes only
paths recorded as owned by this session. The handler reports one bounded outcome
(`SUCCESS`, `DEGRADED`, `CANCELLED`, or `FAILED`) and includes the session id,
video path, and first failure cause.

### Registration and lifecycle fencing

The dirty tree contains both `PremiereExportButton` and
`PremiereExportButtonInstaller` wiring. Keep one installation path and make
`initialize`/listener registration idempotent. The button captures an immutable
request (film, range, layout) and arms the completion listener before starting
recording. Completion is consumed on the client thread; audio/file work may be
off-thread only if the asset API permits it, with final UI mutation marshalled
back to the client thread.

## Test seams

- Pure clip-plan tests for no loop, loop-mid-clip, negative/large offsets,
  zero/empty intersections, and exact frame rounding.
- WAV fixture tests for mono/stereo impulses, 44.1/48 kHz, all supported sample
  formats, volume, and one-time layout conversion.
- XML parser assertions for counts, ids, source-track routing, escaped URIs,
  `in/out`, duration, and metadata.
- Fake completion/recorder tests for success, cancellation, early FFmpeg exit,
  missing artifact, duplicate callbacks, and stale session ids.
- Atomic filesystem tests inject write/probe/move failures and verify no
  published partial files and no deletion of pre-existing files.
- Optional real-media tests run FFmpeg/ffprobe against generated stereo and
  mono WAVs and the recorded MP4, checking stream metadata and A/V duration
  within one output frame. They skip with a clear reason when tools are absent.
