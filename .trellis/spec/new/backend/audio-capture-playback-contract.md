# Audio Capture and Playback Contract

> Source-backed executable contract for microphone capture and film-audio preview in `new/`.
> Normative terms (`MUST`, `MUST NOT`, `SHOULD`) apply to production changes and their
> deterministic fake-backend tests. The implementation evidence is under
> `new/src/client/java/mchorse/bbs_mod/`, and the focused executable specification is
> `new/src/audioLifecycleTest/java/mchorse/bbs_mod/audio/AudioLifecycleTest.java`.

## 1. Scope / Trigger

Read this contract before changing any of the following:

- microphone format, polling, streaming, finalization, cancellation, temporary-file commit,
  recorder overlay, or film clip insertion;
- `CaptureBackend`, `CaptureSpec`, `CaptureState`, `CaptureResult`, `CaptureFailure`,
  `OpenALRecorder`, `StreamingWavSink`, or `UIAudioRecorder`;
- film preview identity, reconciliation, pause/resume/seek, relink/removal, asset invalidation,
  OpenAL source/buffer teardown, resource reload, disconnect, or client shutdown;
- `ClipContext` playback ownership, `AudioClientClip`, `SoundManager`, `SoundPlayer`,
  `SoundBuffer`, or a controller that owns a `ClipContext`.

This contract covers mono and stereo interleaved PCM capture/playback only. Microphone capture
defaults to mono. Stereo is valid only when explicitly requested and supported. Layouts above
stereo, HRTF, binaural processing, OS loopback, offline mixing, muxing, and export are outside
this contract.

The authoritative behavior is the current FSR implementation plus the archived task
`.trellis/tasks/archive/2026-07/07-20-audio-capture-playback/`. Preserve legacy public/protected
JVM descriptors through additive APIs or delegating adapters; do not move film preview back to
link-global playback to preserve a legacy shortcut.

## 2. Signatures

The lifecycle boundary MUST retain these shapes or source/binary-compatible adapters:

```java
record CaptureSpec(int sampleRate, int channels, int ringSamples, long maxFrames) {}

enum CaptureState {
    ARMED, OPENING, RECORDING, STOPPING, READY, SUCCEEDED, CANCELLED, FAILED
}

final class CaptureResult {
    enum Status { READY, SUCCESS, CANCELLED, FAILED }

    static CaptureResult ready(Path ownedTemp, long frames, int sampleRate, int channels);
    static CaptureResult success(Path formerOwnedTemp, long frames, int sampleRate, int channels);
    static CaptureResult cancelled(int sampleRate, int channels);
    static CaptureResult failed(CaptureFailure failure, Throwable cause,
                                int sampleRate, int channels);
}

interface CaptureBackend {
    String defaultDevice() throws Exception;
    long open(String deviceName, CaptureSpec spec) throws Exception;
    void start(long device) throws Exception;
    int availableFrames(long device) throws Exception;
    void read(long device, ByteBuffer destination, int frames) throws Exception;
    void stop(long device) throws Exception;
    void close(long device) throws Exception;
}

class OpenALRecorder implements Runnable {
    Thread startAsync();
    void stop();
    void cancel();
    void cleanup();
    void discardResultFile();
    boolean beginCommit(CaptureResult candidate);
    boolean ownsTemporaryFile(Path candidate);
    boolean completeCommit(CaptureResult candidate);
    CaptureResult markCommitFailed(Throwable cause);
}

class UIAudioRecorder {
    static void cancelActive(UIFilmPanel panel);
    static int calculateDurationTicks(long frames, int sampleRate);
    static Path commitOwnedFile(OpenALRecorder recorder, CaptureResult result,
                                Path audioRoot, String name) throws IOException;
}
```

`CaptureFailure` MUST remain a typed terminal vocabulary, not a message parser:

```text
NO_DEVICE, DEVICE_ENUMERATION_FAILED, UNSUPPORTED_MODE,
DEVICE_OPEN_FAILED, DEVICE_START_FAILED, DEVICE_READ_FAILED,
DEVICE_STOP_FAILED, DEVICE_CLOSE_FAILED, STORAGE_FAILED,
CAPTURE_OVERFLOW, DURATION_LIMIT, CALLBACK_FAILED, COMMIT_FAILED
```

Playback identity and cleanup MUST retain these semantics:

```java
Object ClipContext.getPlaybackOwner();
Object ClipContext.resetPlaybackOwner();

record SoundManager.VoiceRequest(Link link, float seconds, float gain) {}

void SoundManager.reconcile(Object owner, Map<?, VoiceRequest> desired, boolean playing);
void SoundManager.releaseOwner(Object owner);
void SoundManager.deleteSound(Link link);
void SoundManager.deleteSounds();

void SoundPlayer.delete();       // stop source -> detach buffer -> delete source
void SoundBuffer.delete();       // legal only after no source references the buffer

void AudioClientClip.manageSounds(ClipContext context);
void AudioClientClip.releaseSounds(ClipContext context);
```

The erased legacy descriptor `Map getPlayback(ClipContext)` may remain, but its map MUST be
identity-keyed by `AudioClientClip`, not keyed by `Link`. Legacy `play(Link)` and
`playUnique(Link)` may remain for unrelated one-shot UI sounds; film clips MUST use
`reconcile(owner, desired, playing)`.

## 3. Contracts

The exactly-once ownership ledger is:

| Resource | Owner while live | Transfer / terminal release |
|---|---|---|
| Capture device handle | One `OpenALRecorder` session | Recorder stops/closes it once; only an actually acquired non-zero handle is released |
| Capture worker thread | One `OpenALRecorder` session | `startAsync()` returns the one worker; terminal cleanup interrupts/joins it without creating a replacement |
| Capture chunk and sink | One `OpenALRecorder` session | Recorder frees the direct chunk and closes/finalizes or aborts the sink once |
| Temporary WAV path | Recorder through `READY` | Client commit transaction moves it and transfers ownership to the committed asset, or cancel/failure deletes it |
| Playback context identity | One `ClipContext` controller generation | Controller calls `releaseOwner(oldIdentity)` before reset, clear, replacement, or shutdown |
| OpenAL voice/source | `SoundManager` key `(owner identity, clip identity)` | Manager stops, detaches, and successfully deletes the source once; failures remain pending for retry |
| Decoded OpenAL buffer | `SoundManager` cache entry keyed by `Link` | Manager deletes it only after every referencing source has detached/deleted |

### Capture state and final drain

1. One `OpenALRecorder` session owns one backend, one non-zero device handle, one worker
   thread, one fixed-size direct chunk buffer, one streaming WAV sink, and at most one
   session-created temporary path. `startAsync()` MUST create at most one worker and repeated
   calls MUST return that same worker. Terminal commands and cleanup MUST be idempotent.
2. Capture data is interleaved signed PCM16. A chunk contains complete frames; sample access is
   `(frame * channels + channel) * 2`, normalized by `32768F`. Meter/waveform reads MUST use
   absolute reads or a duplicate and MUST NOT change the PCM delivered to the sink.
3. Live storage MUST remain bounded: the current implementation reuses a 4,096-frame direct
   chunk and a 64 KiB sink scratch array, while `CaptureSpec.maxFrames()` bounds file growth.
   A live poll reaching `ringSamples`, a final drain exceeding `ringSamples`, an incomplete
   backend chunk, or a duration limit is a failure; samples MUST NOT be silently dropped and the
   shorter take MUST NOT be reported as successful. A final drain of exactly `ringSamples` is
   valid and bounded.
4. A saving stop MUST execute in this order:

   ```text
   stop capture device
   -> query and read every available complete tail frame until zero
   -> write the tail to the same sink in order
   -> finalize WAV header, fsync, and close sink
   -> close capture device
   -> free direct chunk buffer
   -> publish READY once
   ```

   `READY.frames` is the exact live-plus-tail frame count. Zero captured frames is
   `FAILED(DEVICE_READ_FAILED)`, never an empty `READY` or `SUCCESS`.
5. Any acquisition, read, stop, drain, sink, finalize, or close failure MUST retain the first
   typed failure, continue safe cleanup, attach later cleanup diagnostics where possible, delete
   partial session-owned data, publish one terminal state, and invoke each registered observer at
   most once.

### READY, commit fence, cancellation, and UI dispatch

6. `READY` is a finalized-file transfer offer, not user-visible success. The recorder remains
   cancellable until the client transaction calls `completeCommit` with the exact same
   `CaptureResult` object. `beginCommit(candidate)` MUST require all of the following:

   ```text
   candidate == recorder.getResult()
   candidate.status == READY
   recorder.state == READY
   candidate path is the recorder-owned normalized path
   cancel was not requested
   commit is not already complete
   ```

7. The worker may publish only immutable result/progress snapshots. It MUST NOT move files,
   mutate clips or selection, start/stop film UI state, move the cursor, remove overlays, show
   errors, or mutate playback OpenAL objects. The production result callback MUST enqueue one
   task through `Minecraft.getInstance().execute(...)`; tests use an injected dispatcher and
   prove zero UI/backend calls before the queued task runs.
8. `UIAudioRecorder` MUST capture immutable panel identity, film identity, clip-list identity,
   generation, requested name, and origin cursor when armed. Count-in records no samples.
   Transport starts from the captured origin only after the recorder reaches `RECORDING`.
   Every terminal path stops only the session-owned transport and restores that origin.
9. The client task MUST revalidate the UI generation and commit fence before commit, after the
   move, before clip mutation, and when closing the transaction. `commitOwnedFile` MUST accept
   only the recorder-owned `READY` path, normalize beneath the audio root, reject blank or
   escaping names, and move without replacing an existing target.
10. Only after the owned file is moved may the UI create and select an `AudioClientClip` at the
    immutable origin. Its timeline duration is:

    ```text
    ceil(frames * 20 / sampleRate)
    == (frames * 20 + sampleRate - 1) / sampleRate
    ```

    `completeCommit(result)` is called after clip insertion and is the only production
    transition from `READY` to `SUCCESS`. The result's former temporary path is not the asset
    identity after the move; the clip's committed `Link` owns that identity.
11. Cancel has precedence over `READY`, a late callback, and an in-flight commit until
    `completeCommit` succeeds. A stale UI generation is non-committable: its callback MUST delete
    the owned result and perform no UI mutation. Cancel during count-in, opening, recording,
    stop, final drain, READY, or client commit MUST leave no final WAV, no clip, no new selection,
    and no retained temporary file. If cancellation wins after the move or clip insertion, the
    UI transaction MUST remove the clip, restore the previous selection, and delete only its
    moved asset. Repeated cancel/discard calls MUST be harmless.
12. Screen close/disappear, film replacement, disconnect, player-scope replacement, world
    unload, and client stop MUST call the same panel-owned cancel path while the panel/session is
    still reachable. A dashboard or panel reference MUST NOT be cleared first.

### Playback identity, context, and resource ownership

13. A decoded `SoundBuffer` is cached by asset `Link`. A film voice is keyed by reference
    identity `(playbackOwner, AudioClientClip instance)`. Therefore two overlapping clips of the
    same link MUST share one decoded buffer and own two independent `SoundPlayer` sources,
    offsets, gains, and pause states. Two owners that compare equal with `equals()` MUST still
    remain independent.
14. Every controller evaluation MUST clear and rebuild its desired identity map, then call
    `manageSounds`/`reconcile` even when the desired map is empty. Missing desired keys release
    expired, removed, disabled, out-of-range, or relinked voices. `AudioClientClip.shutdown`
    MUST NOT stop by `Link`; the controller owns terminal release through `releaseOwner`.
15. Reconciliation reuses a source only when owner, clip identity, and link still match. Relink
    releases that clip's old source before creating/reusing the new link's buffer. Pause retains
    an in-range voice and reconciles its offset without playing; resume and material seek set the
    expected offset before play. Scrubbing outside the clip interval omits the key and releases
    the voice.
16. All OpenAL buffer/source mutation MUST run on the client/OpenAL context thread. Off-context
    `reconcile`, `releaseOwner`, asset-watch invalidation, and global reset MUST enqueue work and
    perform no backend call before dispatch. `releaseOwner` MUST retire the owner identity before
    enqueueing cleanup so an older queued reconcile cannot resurrect it.
17. Each context has one stable playback owner for its active controller generation. Work
    replacement and controller shutdown MUST execute:

    ```text
    releaseOwner(old identity)
    -> context.shutdown() where applicable
    -> resetPlaybackOwner()
    -> clear clipData / drop controller references
    ```

    Resetting first loses the only key capable of releasing the old voices and is forbidden.
18. A voice source is released exactly once in native order: stop, detach buffer (`AL_BUFFER =
    0`), delete source, invalidate the Java handle. Buffer deletion is deferred until no live or
    pending player references it. Link invalidation releases every matching source, not merely
    the first; global reset releases/retries all sources before deleting any buffer. Repeated
    deletion MUST never call an already released native handle.
19. Cleanup continues after one failure. `SoundManager.lastCleanupFailure` retains the first
    failure and later failures are suppressed; failed source/buffer deletions stay pending for a
    context-thread retry. A buffer MUST NOT be deleted while a failed detach still leaves its
    source attached.
20. Disconnect and client shutdown MUST preserve owner order within each ownership domain and
    use the current cross-layer sequence:

    ```text
    cancel active capture while UI owner is reachable
    -> finish other UI/session teardown; dashboard reference may now be cleared
    -> reset Film controllers (each shutdown releases its playback owner before context reset)
    -> remove/reset camera controllers (release playback owners)
    -> SoundManager.deleteSounds() (remaining/legacy sources, then buffers)
    ```

    Thus `dashboard = null` is legal after capture cancellation but MUST NOT be used as a
    substitute for cancelling capture. Likewise, a controller/context identity MUST NOT be reset
    or dropped before `releaseOwner`. `BBSModClient.onClientDisconnect()` and `onClientStopping()`,
    `CameraWorkCameraController.shutdown()`, and `WorldFilmController.shutdown()` are the
    reference call sites. Lifecycle cleanup MUST continue after an individual step fails.

## 4. Validation & Error Matrix

### Capture validation and terminals

| Condition | Required typed result and ownership effect |
|---|---|
| `sampleRate <= 0`, channels not 1/2, undersized ring, or non-positive `maxFrames` | Reject `CaptureSpec`; a runtime request maps to `UNSUPPORTED_MODE`; acquire no device |
| Device enumeration throws | `FAILED(DEVICE_ENUMERATION_FAILED)`; free chunk/temp resources |
| Default device is null/blank | `FAILED(NO_DEVICE)`; no device, final asset, or clip |
| Explicit stereo/layout rejection | `FAILED(UNSUPPORTED_MODE)`; never silently fall back to mono |
| Open returns zero/throws | `FAILED(DEVICE_OPEN_FAILED)`; close only a handle actually acquired |
| Worker/backend start fails | `FAILED(DEVICE_START_FAILED)`; callback once, no stranded `STOPPING` |
| Available/read fails, count is negative, chunk is incomplete, or an unexpected worker interrupt occurs | `FAILED(DEVICE_READ_FAILED)`; discard partial WAV |
| Live available count reaches the ring capacity, or final-drain count exceeds it | `FAILED(CAPTURE_OVERFLOW)`; an exactly full stopped tail remains a valid bounded drain |
| Next complete chunk exceeds `maxFrames` | `FAILED(DURATION_LIMIT)`; no partial promotion |
| Sink creation/write/header finalize/fsync fails | `FAILED(STORAGE_FAILED)`; close and delete owned temp |
| Device stop fails | `FAILED(DEVICE_STOP_FAILED)`; still attempt close and temp cleanup |
| Device close fails after otherwise valid drain/finalize | `FAILED(DEVICE_CLOSE_FAILED)`; do not publish READY |
| Stop with tail frames | Drain all complete tail frames, finalize exact count, publish one `READY` |
| Stop with zero frames | `FAILED(DEVICE_READ_FAILED)`; never create an empty asset |
| Typed or bounded legacy consumer throws | `FAILED(CALLBACK_FAILED)`; callback is not retried; temp is discarded |
| Target exists, path escapes audio root, move/clip/selection transaction fails | `FAILED(COMMIT_FAILED)` unless cancel already won; roll back owned artifacts only |
| Cancel at any pre-commit point | `CANCELLED`; callback at most once; no final asset/clip/selection/temp |
| `completeCommit` succeeds with exact READY identity | `SUCCESS`; cancel becomes a no-op and asset ownership belongs to the clip/library |

### Playback and teardown validation

| Condition | Required response |
|---|---|
| Desired key/link/result is null, seconds/gain is non-finite, gain is negative, or offset is outside buffer duration | Do not create/retain a half-valid voice; absence releases any prior voice for that key |
| Missing, malformed, limited, or unsupported asset | No source; retain the typed `AudioDecodeException` subtype in playback diagnostics where decoding classified it |
| Same owner, two clip identities, same link | One buffer and two sources with independent gain/offset |
| Two owner identities use the same link | Shared buffer; releasing one owner leaves the other's source alive |
| Clip relinks | Delete only that key's old source; reuse/load new link buffer; leave other keys/owners unchanged |
| Paused in-range scrub | Retain paused source and update offset; do not issue play |
| Empty desired map / clip removed, disabled, expired, or scrubbed out of range | Stop/detach/delete that owner's stale voice |
| Off-context reconcile/release/watchdog event | Queue exactly once; zero native backend calls before context dispatch |
| Source detach/delete fails | Retain source mapping/pending player, keep buffer, record first cleanup failure, retry later |
| Link invalidation | Remove cache entry, release all matching sources, then delete buffer when unreferenced |
| Controller replacement/shutdown | Release old owner before reset/clear; queued old work is fenced |
| Global reset/disconnect/client stop | Release/retry every source before any buffer; repeated reset is idempotent |

## 5. Good / Base / Bad Cases

- **Good capture:** the fake supplies two stereo live frames and one tail frame. Stop occurs,
  the device is stopped once, the tail is read, the WAV is finalized to three frames, the device
  closes once, and the worker publishes `READY`. The client dispatcher validates the same result
  and generation, moves without replacement, adds a clip at the original cursor with ceiling
  duration, then calls `completeCommit` and observes `SUCCESS`.
- **Base capture boundary:** one frame at 44.1 kHz creates a one-tick clip. A stop before any
  frame is not a zero-duration success; it is a typed read failure. Cancel before worker start
  opens no device and saves nothing.
- **Good cancel race:** stop has produced `READY`, but panel teardown calls `cancelActive` before
  commit completes. The exact temp is deleted, stale `beginCommit`/`completeCommit` return false,
  no second callback is emitted, and no clip or selection survives.
- **Good playback:** clips A and B reference the same `Link`. Reconcile creates one buffer plus
  source A and source B. Removing A deletes only source A. Releasing owner 1 leaves owner 2's
  same-link source intact. Empty reconciliation eventually removes every stale source.
- **Good shutdown:** release each controller's old owner identity, retry/finish source
  stop-detach-delete, then delete unreferenced buffers. A cleanup exception is observable but
  does not prevent later owners and buffers from being processed.
- **Bad:** publish `SUCCESS` from the recorder worker, treat Escape as stop-and-save, commit from
  the worker callback, use `Files.move(..., REPLACE_EXISTING)`, calculate duration with floor
  division, or accept a callback solely because its path string looks plausible.
- **Bad:** key desired playback by `Link`, call `playUnique` from `AudioClientClip`, skip
  reconciliation when no clip is active, stop a clip by link, reset the context owner before
  release, mutate OpenAL from a watchdog thread, or delete a buffer before all sources detach.

## 6. Tests Required

The focused hermetic gate is `new`'s dependency-light `testAudioLifecycle` launcher. It requires
no microphone or OpenAL device. Production changes in this scope MUST keep these executable
cases passing in `AudioLifecycleTest`:

```text
captureStopDrainsTailAndPreservesStereo
captureCancelWinsDuringClose
captureImmediateStopAndPublishedCancel
captureRepeatedAndQueuedCancel
captureEmptyStartAndStorageFailures
captureFullRingTailIsBoundedAndDrained
captureCommitFenceProvenanceAndCeilingTicks
captureLegacyCallbackTerminalOrdering
captureWorkerInterruptUsesDispatcherOnce
captureRepeatedStartAndCleanupOwnResourcesOnce
captureFinalDrainAndConsumerFailuresOwnResourcesOnce
captureFailuresAreDistinctAndBounded
playbackIdentityAndGlobalCleanup
playbackCleanupRetriesAndRetiredOwners
playbackContextDispatchDefersBackend
cameraControllerRemovalShutsDown
```

The fake capture backend MUST assert operation counts for enumerate/open/start/read/stop/close,
distinguish live reads from final-drain reads, and reject duplicate device transitions. The temp
tracker MUST prove each created path is released once. The dispatcher MUST prove one submission,
zero direct worker/UI or off-context/OpenAL calls, and one execution. The fake sound backend MUST
identify every buffer/source handle, assert source stop-detach-delete ordering, assert one
successful delete per handle, and prove buffers are deleted only after source references vanish.

The cross-layer typed-status and terminal exactly-once checks in
`new/src/audioVerificationTest/java/mchorse/bbs_mod/audio/AudioVerificationContractTest.java`
MUST also pass. The focused commands for a code change are:

```powershell
cd new
.\gradlew.bat --no-daemon testAudioLifecycle testAudioVerification
.\gradlew.bat --no-daemon compileClientJava
git diff --check
```

Physical microphone/OpenAL smoke is separate evidence. It MUST NOT replace fake-backend tests or
be reported as passed when it was not run.

## 7. Wrong vs Correct

Wrong — the worker treats a finalized file as success and mutates UI directly:

```java
recorder = new OpenALRecorder(spec, temp, result -> {
    Files.move(result.temporaryFile(), target, REPLACE_EXISTING);
    clips.addClip(makeClip(result));
});
```

Correct — the worker publishes `READY`; the client dispatcher and identity fence own commit:

```java
recorder = new OpenALRecorder(spec, temp, result ->
    Minecraft.getInstance().execute(() -> {
        if (!isCurrentGeneration() || !recorder.beginCommit(result)
            || !recorder.ownsTemporaryFile(result.temporaryFile())) {
            recorder.cancel();
            return;
        }

        Path committed = UIAudioRecorder.commitOwnedFile(recorder, result, audioRoot, name);
        if (!isCurrentGeneration() || !recorder.beginCommit(result)) {
            rollbackOwnedCommit(committed);
            return;
        }

        AudioClientClip clip = insertAtOrigin(committed,
            UIAudioRecorder.calculateDurationTicks(result.frames(), result.sampleRate()));
        if (!recorder.completeCommit(result)) {
            rollbackOwnedClipAndFile(clip, committed);
        }
    }));
```

Wrong — asset identity collapses overlapping clips and teardown risks an attached buffer:

```java
Map<Link, Playback> desired = new HashMap<>();
SoundPlayer player = sounds.playUnique(clip.audio.get());
buffer.delete();
player.delete();
context.resetPlaybackOwner();
```

Correct — clip and owner use reference identity; owners release before buffers and owner reset:

```java
IdentityHashMap<Object, SoundManager.VoiceRequest> desired = new IdentityHashMap<>();
desired.put(clipA, new SoundManager.VoiceRequest(sharedLink, offsetA, gainA));
desired.put(clipB, new SoundManager.VoiceRequest(sharedLink, offsetB, gainB));
sounds.reconcile(context.getPlaybackOwner(), desired, context.playing);

Object oldOwner = context.getPlaybackOwner();
sounds.releaseOwner(oldOwner);       // stop -> detach -> delete every owned source
context.shutdown();
context.resetPlaybackOwner();
context.clipData.clear();
sounds.deleteSounds();               // remaining sources first, then unreferenced buffers
```
