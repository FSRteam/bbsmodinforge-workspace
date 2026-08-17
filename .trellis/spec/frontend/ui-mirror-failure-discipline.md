# FSR UI Mirror Failure Discipline

> Executable contract for the FSR capture layer
> (`new/src/client/java/mchorse/bbs_mod/client/ui/mirror/`). Any failure in this layer
> must be accounted, recoverable, and observable — never a silent permanent frame stall.

---

## 1. Scope / Trigger

Applies to `BBSUiMirrorRegistry` (listener delivery worker) and `BBSUiFrameRecorder`
(frame capture gate). Trigger: the 08-13 film no-frame task audit found three
unrecoverable silent-latch defects here (bare `Error` escaping `deliver()`,
`drainScheduled` wedged after worker death, `ACTIVE_FRAME` ThreadLocal leak).

## 2. Signatures

```java
// Read-only, lock-free diagnostics snapshots (add-only records):
public static BBSUiMirrorRegistry.Diagnostics BBSUiMirrorRegistry.diagnostics()
public record Diagnostics(long framesSkippedNoDemand, long framesSkippedQuarantined,
                          long callbackErrors, long workerRestarts)

public static BBSUiFrameRecorder.Diagnostics BBSUiFrameRecorder.diagnostics()
public record Diagnostics(long noDemand, long frameAlreadyActive, long sessionMissing,
                          boolean activeFrameLeaked)
```

Both are safe to poll from any thread at any frequency (AtomicLong reads). Package-private
`resetDiagnosticsForTests()` exists for test isolation only.

## 3. Contracts

| Rule | Detail |
|---|---|
| Callback exceptions | `ListenerEntry.deliver()` catches `Throwable`. `callbackStartedNanos`/`callbackPhase` reset happens in `finally` — the capture gate (`captureEnabled()`) must never stay latched by an escaped throwable. |
| Fatal errors | `OutOfMemoryError`/`StackOverflowError`: account (`callbackErrors`++), reset state, **then** rethrow. Other `Throwable`s enter the existing quarantine/backoff path. |
| Worker revival | `drain()` wraps the loop; on abnormal exit it resets `drainScheduled` under the lock, `notifyAll()`, increments `workerRestarts`, and reschedules if `pending` is non-empty. A dead worker thread must never leave the queue unserviced. |
| Frame lifecycle | Any `beginFrame` success must be paired via `try/finally` with `endFrame` or `abortFrame()`. `ACTIVE_FRAME` must not survive an exception (`publishStandaloneWorldReplayFrame` is the reference implementation). |
| Rejection accounting | Every `beginFrame` false return increments exactly one reason counter (`noDemand` / `frameAlreadyActive` / `sessionMissing`). `frameAlreadyActive` additionally logs a throttled WARN (>=5 s apart, logger `bbs-client-ui-mirror`) because it means a leak happened. |
| API stability | Diagnostics records are add-only; `BBSUiMirrorListener` contract unchanged. |

## 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Listener throws bare `Error` (e.g. `AssertionError`) | Accounted as callback error, listener quarantined with WARN, gate recovers, later frames deliver. |
| Listener throws OOM/SOE | Accounted + state reset first, then rethrown (worker dies), replacement worker revives delivery. |
| Frame body throws mid-frame | Exception propagates, `abortFrame()` discards the half frame, next `beginFrame` returns true. |
| `frameAlreadyActive` observed | Counter > 0, `activeFrameLeaked` true, throttled WARN — this state is a bug signal, never expected in healthy runs. |

## 5. Good/Base/Bad Cases

- **Good**: worker dies from a rethrown fatal error → `workerRestarts` increments, new
  thread (different name) drains the pending queue.
- **Base**: no-demand periods increment `framesSkippedNoDemand` only — cheap, no locks.
- **Bad**: adding a new delivery/capture path that swallows `Throwable` without `finally`
  state reset, or that returns early between `beginFrame` and `endFrame`.

## 6. Tests Required

`new/` source set `uiMirrorDemandTest`, Gradle task **`testUiMirrorDemand`**
(see guides/index.md gotcha: the bare source-set name abbreviation-matches to a
compile-only task). Reference tests: `BBSUiMirrorFailureRecoveryTest`
(`bareErrorDoesNotLatchCaptureGate`, `fatalErrorRethrowsAndWorkerRevives`,
`standaloneReplayFailureDoesNotLeakActiveFrame`) — each was red before the fix and
asserts gate recovery, restart accounting, and post-failure delivery. Bare-JVM rule:
tests must not trigger `BBSMod.<clinit>`.

## 7. Wrong vs Correct

```java
// WRONG — the defect shape: reset skipped when a bare Error escapes
this.callbackStartedNanos = started;
try {
    delivery.invoke(this.listener);
} catch (Exception | LinkageError e) {  // AssertionError escapes; gate latches forever
    failure = e;
}
this.callbackStartedNanos = 0L;

// CORRECT — Throwable + finally; fatal errors rethrow only after reset
this.callbackStartedNanos = started;
try {
    delivery.invoke(this.listener);
} catch (Throwable e) {
    failure = e;
} finally {
    this.callbackStartedNanos = 0L;
    this.callbackPhase = null;
}
```
