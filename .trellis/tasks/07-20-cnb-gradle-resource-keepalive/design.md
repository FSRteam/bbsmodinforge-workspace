# Technical Design

## Boundaries

- `new/.cnb.yml` remains the CNB entry point for push and API-triggered builds.
- `new/cnb/gradle-build.sh` owns Gradle invocation and build-time diagnostics so
  the two CNB entry points cannot drift.
- `new/gradle.properties` owns default JVM and Gradle worker limits.

## Resource Budget

CNB `runner.cpus: 2` provides 4 GiB. Gradle uses one worker and a 1.5 GiB heap
with a 384 MiB metaspace cap. Parallel execution is disabled, leaving room for
JVM native memory, the shell/monitor, filesystem cache, and container overhead.
CNB also enables NeoGradle UserDev binary patch mode so the memory-heavy
`DecompilerExecute` child process is not used. Explicit 1 GiB/single-thread
decompiler limits remain as a fallback. The daemon remains disabled because
CNB jobs are ephemeral.

## Keepalive Flow

The shell script starts Gradle in the background, then samples `/proc/net/dev`
every 30 seconds. It aggregates receive/transmit byte counters across all
interfaces, computes the interval rate in MiB/s, and emits a timestamped line.
If `/proc/net/dev` is unavailable, it emits a liveness-only line. The monitor
is stopped after `wait` captures Gradle's status, and that status is returned.

## Timeout and Compatibility

The CNB `build` stage declares `timeout: 30m`; this raises the no-output limit
while keeping a hard upper bound. The script still emits output so operators
can distinguish a slow transfer from a stalled one. No Gradle task graph or
NeoForm behavior is changed.

## Rollback

Revert the `.cnb.yml`, `gradle.properties`, and `cnb/gradle-build.sh` changes
together. No cache layout or source files are modified.
