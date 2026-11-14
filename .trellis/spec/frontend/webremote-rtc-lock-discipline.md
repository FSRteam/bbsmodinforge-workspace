# WebRemote RTC Native-Call Lock Discipline

> Executable contract for `bbs-web-remote` Java client code that touches libwebrtc.
> Born from the 2026-08-13 film no-frame incident (jstack evidence:
> `.trellis/tasks/08-13-wr-film-no-frame-rootcause/research/jstack-hang-2026-08-13.txt`).

---

## 1. Scope / Trigger

Applies to any code in `bbs-web-remote/src/client/java/mchorse/bbs_web_remote/client/`
that calls a `WebRtcBackend` method or holds the `BBSWebRemoteClient` monitor or
`WebRtcCoordinator.lock`.

**Incident**: native `RTCPeerConnection.addIceCandidate` hung for 12819 s and never
returned. The then-current code invoked it inside `synchronized (WebRtcCoordinator.lock)`;
`BBSWebRemoteClient.onClose` (a `synchronized` method) called into the coordinator and
waited on that dead lock. Result: main-monitor avalanche — `onOpen` never ran
`setUiMirrorDemand(true)`, so the UI frame pipeline stalled permanently and did not
recover on screen switch or reconnect.

## 2. Signatures

```java
// BBSWebRemoteClient — the only sanctioned way to reach native RTC methods:
private void submitRtcWork(String operation, Runnable work)
// Executes on the single-threaded "bbs-web-remote-rtc-signaling" executor.
// Falls back to inline execution only after RejectedExecutionException (executor shutdown).

private void closeWebRtcWithinTimeout()
// coordinator.close() on a throwaway daemon thread + timed join(WEBRTC_CLOSE_TIMEOUT_MILLIS).
// A hung native close leaks that daemon thread instead of wedging game shutdown.
```

`WebRtcCoordinator` internal rule: every public method mutates peer/session state inside
`synchronized (this.lock)`, then invokes `backend.*` **after** releasing it.

## 3. Contracts

| Rule | Detail |
|---|---|
| Native-blocking set | `connectPeer`, `acceptOffer`, `addIceCandidate`, `endIceCandidates`, `resetSession`, `disconnect`, `close` on `WebRtcBackend` may block forever in native code. |
| Lock-free native | None of the above may execute while holding `WebRtcCoordinator.lock`. State transitions happen in-lock; the native call happens out-of-lock on the same thread. |
| Monitor-free native | None of the above may execute on a thread that holds the `BBSWebRemoteClient` monitor (`synchronized` methods such as `onOpen`/`onClose`/`startRuntimeServer`). Route through `submitRtcWork`. |
| Memory-only exceptions | `available()`, `unavailableReason()`, `mediaBridgeStatistics()`, and the `publishSurface` epoch check are memory reads / bridge handoffs; they may stay inline. |
| Ordering | All mutating RTC calls for one client flow through the single `rtcSignaling` thread, preserving native call order. |
| Degradation | A hung signaling thread must never affect the JPEG-over-WS UI frame path. The UI pipeline has no dependency on the RTC lock or the signaling thread. |

## 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Native call hangs mid-operation | Only the signaling thread is lost; coordinator lock and client monitor stay acquirable; UI frames keep flowing. |
| `submitRtcWork` after executor shutdown | Work runs inline (teardown must still happen); `RuntimeException`/`LinkageError` logged at debug, never propagated. |
| `close()` exceeds timeout | WARN log, shutdown continues, daemon thread leaked deliberately. |
| Stale generation callback | `GenerationCallbacks.current()` fences delegate invocation; a removed peer silences its callbacks. |

## 5. Good/Base/Bad Cases

- **Good**: `disconnectRtcPeer` removes the peer from `rtcPeers` (memory), then
  `submitRtcWork("disconnect", () -> webRtc.disconnect(peerId, generation))`.
- **Base**: RTC text handling (`handleRtcText`) already runs on the signaling thread via
  `onText` dispatch; inline `webRtc.*` calls there are acceptable because no monitor is held.
- **Bad**: any new `synchronized` method on `BBSWebRemoteClient` that calls `webRtc.*`
  directly, or any new coordinator method that calls `backend.*` inside `synchronized (lock)`.

## 6. Tests Required

- `webrtcTest`: with a deliberately blocking fake backend, assert that while a native call
  is blocked, another thread can still complete coordinator methods (`publishSurface` /
  `isCurrent` path) within a bounded time. (Regression fence for the incident.)

## 7. Wrong vs Correct

```java
// WRONG — the incident shape: native call under the coordinator lock
synchronized (this.lock) {
    this.requirePeer(peerId, generation);
    this.backend.addIceCandidate(peerId, generation, mid, mLineIndex, candidate); // may never return
}

// CORRECT — state in-lock, native out-of-lock
synchronized (this.lock) {
    this.requirePeer(peerId, generation);
    // counters / state transitions only
}
this.backend.addIceCandidate(peerId, generation, mid, mLineIndex, candidate);
```

```java
// WRONG — main monitor crossing into RTC
public synchronized void onClose(Socket socket) {
    this.webRtc.disconnect(peerId, generation); // wedges every synchronized method forever
}

// CORRECT — hand off to the signaling thread
public synchronized void onClose(Socket socket) {
    this.disconnectRtcPeer(removed); // internally: submitRtcWork("disconnect", ...)
}
```
