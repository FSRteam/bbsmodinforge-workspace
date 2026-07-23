# Audio Capture and Playback Lifecycle Design

## Verified Baseline

The current implementation has two different ownership problems.

- `OpenALRecorder` owns raw handles and an ever-growing direct buffer, but has
  only a non-volatile `running` flag. Initialization failure bypasses cleanup,
  normal stop does not drain the device tail, and its consumer runs on the
  recorder thread.
- `UIAudioRecorder` starts the thread directly, treats Escape as a successful
  stop, mutates clips/selection from the worker callback, silently swallows save
  errors, and does not place the clip at the recording-origin cursor.
- The upstream FS commits `d2c186b7`, `a9b6a105`, `bbe94274`, `04f718b9`, and
  `a447d075` provide useful waveform, naming, count-in, transport, cancel, cursor,
  client-thread callback, and larger capture-ring behavior. They are behavioral
  references, not a direct port: they still retain unbounded PCM accumulation,
  weak error handling, and incomplete final-drain/teardown semantics.
- `AudioClientClip` records desired playback in `Map<Link, Playback>` and calls
  `playUnique(Link)`, so same-link clips collapse into one source and overwrite
  each other's position/gain.
- `WorldFilmController.render` returns when no clips are active, skipping the
  empty reconciliation needed to stop prior audio. Its `paused` flag is also
  separate from `CameraClipContext.playing`.
- `AudioClientClip.shutdown` stops by `Link`, so one clip/controller can stop
  every same-link user. `SoundManager.deleteSound` examines only the first
  player before deleting a buffer that other sources may still reference.

## Capture Architecture

### Session Owner

Introduce one microphone capture session above `OpenALRecorder`. It owns:

- immutable session/generation id, requested mono/stereo `CaptureSpec`, asset
  target reservation, origin tick, and owning panel/transport identity;
- the recorder worker, capture backend/device handle, streaming sink, bounded
  chunk queue, temporary file, and terminal callback;
- state transitions and one terminal `CaptureResult`.

Conceptual states:

```text
ARMED -> OPENING -> RECORDING -> STOPPING -> SUCCEEDED
   |        |           |           |
   +--------+-----------+-----------+-> CANCELLED
            +-----------+-----------+-> FAILED
```

`cancel` has precedence over success until the client-thread commit completes.
Every command uses compare-and-set/idempotent transitions. Repeated stop,
cancel, overlay teardown, disconnect, and client-stop calls are harmless.

Use an additive result API while retaining the legacy `Runnable`/consumer
descriptor as a compatibility adapter. The new terminal result distinguishes:

```text
SUCCESS(frames, sampleRate, channels, ownedTempFile)
CANCELLED
NO_DEVICE
UNSUPPORTED_MODE
DEVICE_OPEN_FAILED
DEVICE_IO_FAILED
STORAGE_FAILED
COMMIT_FAILED
```

Failures carry one cause for logs but do not expose raw OpenAL details as UI
text. The legacy consumer runs only for `SUCCESS` after final drain.
Because `Wave` owns a byte array, that compatibility adapter enforces an
explicit maximum take size before materializing the finalized file. The normal
UI/result path transfers the owned file descriptor and exact metadata instead.

### Backend and Storage Boundary

Put direct ALC calls behind a narrow package-private capture backend. The real
backend owns OpenAL calls; a fake backend supplies deterministic chunks, a
post-stop tail, and injected failures for tests.

Do not grow one in-memory `ByteBuffer`. Stream captured interleaved PCM16 frames
to a uniquely named temporary WAV/PCM sink through fixed-size reusable chunks
and a bounded queue. The sink tracks frame count and finalizes the WAV header.
If the queue cannot keep up, the storage limit is reached, or the disk write
fails, stop capture and return `STORAGE_FAILED`; never drop samples and pretend
the shorter take succeeded.

The live meter/waveform is a separate fixed-size snapshot ring. For each frame,
read `getShort(frameIndex * channels * 2 + channelIndex * 2)`, normalize by
`32768F`, and derive peak/mean without advancing or rewriting the delivered PCM
buffer. UI reads a copied immutable/bounded snapshot.

### Stop, Drain, and Cleanup

The successful worker sequence is:

1. Wake the polling loop and stop accepting a new terminal command.
2. Call `alcCaptureStop` on the owned non-zero handle.
3. Query `ALC_CAPTURE_SAMPLES` and drain complete frames until none remain.
4. Flush the bounded queue, finalize the sink/header, and capture exact frames.
5. Close the capture device in `finally`.
6. Publish an immutable success result; do not touch UI or clips.

Cancellation stops/closes the device, closes the sink, and deletes its owned
temporary file without publishing audio. Failure follows the same cleanup and
retains the first error with later cleanup errors suppressed. Partial data from
a failed device/sink is not promoted to a successful take.

### UI and Timeline Boundary

`UIAudioRecorder` is the UI facade for the session rather than the resource
owner. At arm time it captures the panel identity, UI generation, origin cursor,
and proposed asset path. Count-in runs with the film transport stationary.
Only after the device reports `RECORDING` does the client thread set the cursor
to the origin and start the owning transport, aligning tick zero with the first
captured frames.

Terminal data flow:

```text
capture worker -> immutable CaptureResult -> Minecraft client executor
 -> verify session + panel + UI generation
 -> stop transport and restore origin cursor
 -> atomically commit owned temp file without overwrite
 -> add/select AudioClientClip at origin tick
 -> remove overlay or show localized failure
```

Duration is `ceil(capturedFrames * 20 / sampleRate)` for a non-empty take. The
WAV keeps the exact frame duration; the ceiling affects only the integer-tick
timeline extent. If generation validation fails, the callback deletes its temp
file and performs no UI mutation.

The client lifecycle must cancel the microphone session before clearing the
dashboard/panel. Add the same ordered step to disconnect and client stopping;
screen/film replacement and world unload call the identical session cancel.

## Playback Architecture

### Identity and Desired State

Give every `CameraClipContext`/film transport a stable playback-owner token and
generation. Define voice identity by reference identity, not clip equality or
mutable list index:

```text
VoiceKey = (owner token identity, AudioClientClip object identity)
DesiredVoice = (asset Link, seconds, gain, transportPlaying)
```

`AudioClientClip.applyClip` emits desired state keyed by its own clip identity.
It does not create a source and does not use link-global `playUnique`. Disabled,
out-of-range, invalid, or missing-link clips emit nothing.

At the end of every controller evaluation, including a frame with zero active
clips, call one `SoundManager.reconcile(owner, desired, transportPlaying)` on
the client/OpenAL thread. Remove the early return in `WorldFilmController` that
skips this empty pass, and explicitly feed `!WorldFilmController.paused` rather
than relying on the context's unrelated default `playing` value.

### Reconciliation

For each desired key:

1. Reuse its current voice when the asset link is unchanged.
2. If the link changed, stop/detach/delete that key's old source first.
3. Load/reuse the `SoundBuffer` keyed by link and create one source for the key.
4. Set gain and clamp/validate expected position against buffer duration.
5. On creation, seek, or material drift, set offset before issuing play.
6. Play when the owning transport is running; pause and retain when paused.

After applying desired keys, stop/release every actual voice for that owner not
seen in the current generation. Thus clip expiry, removal, disable, relink, and
an empty timeline all converge on the same behavior. A paused scrub inside a
clip retains a paused voice at the new offset; scrubbing outside releases it.

`releaseOwner(owner)` is the controller terminal. It replaces per-clip
link-global shutdown and is called exactly once by editor/world controller
replacement, film reload, UI teardown, disconnect, and world/client shutdown.

### Buffers and OpenAL Resource Order

`SoundManager` remains the authority for decoded buffers and live players.
Film voices and compatible one-shot/legacy voices share buffer entries but not
source identity. A buffer tracks or can derive all attached sources.

Make `SoundPlayer.close/delete` idempotent and perform:

```text
alSourceStop(source)
alSourcei(source, AL_BUFFER, 0)
alDeleteSources(source)
invalidate source handle
```

Only then may `SoundBuffer.delete` run. Link invalidation iterates all film and
legacy voices using the buffer, releases every source, removes the buffer from
the cache, and deletes it. Global reset first releases every source, then every
buffer, and clears both maps even when a later cleanup action fails.

Watchdog/resource callbacks that may run off-thread enqueue an invalidation for
the client/OpenAL thread. Failed load/source creation returns an explicit
missing/device result for that desired voice and retains no half-created handle.

### Channel Scope

Capture and playback descriptors accept one or two interleaved PCM channels.
The default microphone mode remains mono; requested stereo either opens as
stereo or fails clearly, without a silent downmix/fallback. `SoundBuffer` uploads
mono as `AL_FORMAT_MONO16` and stereo as `AL_FORMAT_STEREO16`, preserving stereo
left/right identity. Layouts above stereo are rejected.

HRTF is not implicit OpenAL configuration and is not represented as a channel
layout. A future spatializer may consume the same owner/voice descriptors, but
this implementation exposes no HRTF or 5.1 option.

## Error and Lifecycle Matrix

| Event | Capture behavior | Playback behavior |
|---|---|---|
| Device absent/open fails | Fail before transport starts; show error; no asset | Leave desired voice unavailable; no invalid handle |
| Stop | Drain tail, finalize, publish one success | Not applicable |
| Cancel/Escape | Discard temp and callback; restore cursor | Release affected owner when controller closes |
| Clip expires/is removed/disabled | Not applicable | Missing desired key stops only that clip voice |
| Clip relinks | Not applicable | Replace only that key's source/buffer reference |
| Transport pauses/resumes | Capture mode owns and restores its transport | Pause/resume owner voices and reconcile offset |
| Asset reload/delete | Not applicable | Release all sources for link, then buffer; reload lazily |
| Disconnect/world unload | Cancel while panel/session reachable | Release controllers/owners, then global sources/buffers |
| Client stopping | Cancel, join/bound worker teardown, delete temp | Release sources before buffers on OpenAL thread |

## Compatibility

- Preserve existing public method descriptors, including `startRecording`
  style APIs if encountered; add result-returning methods rather than replacing
  `void` descriptors.
- Keep `play(Link)` and `playUnique(Link)` for existing non-film callers.
  `AudioClientClip` moves to the owner/key API.
- Keep the legacy recorder consumer behind a bounded compatibility adapter;
  `UIAudioRecorder` uses the streaming file/result API.
- Keep the decoded asset cache keyed by `Link`; do not duplicate PCM per clip.
- Integrate the upstream microphone UX behavior through current NeoForge and UI
  APIs rather than copying Yarn-era imports or thread assumptions.
