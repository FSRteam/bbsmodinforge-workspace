# 优化 CNB Gradle 构建资源与保活监控

## Goal

Reduce CNB build cost from the current 4-core/8-GiB runner to a 2-core/4-GiB
runner while keeping the NeoForge Gradle build reliable and observable during
long, quiet asset downloads.

## Requirements

- Apply the same configuration to both `main.push` and `main.api_trigger`.
- Set the CNB runner to 2 CPU cores (CNB assigns 2 GiB per core, therefore
  4 GiB total memory).
- Bound Gradle/JVM memory and worker concurrency so the Java process uses most
  of the runner without exhausting memory needed by the container and native
  processes.
- Preserve the existing Gradle wrapper cache and dependency cache mounts.
- During the Gradle build, emit a timestamped heartbeat often enough to avoid
  CNB's default 10-minute no-output termination.
- The heartbeat should report useful Linux network receive/transmit rates when
  available and degrade to a clear liveness message when network counters are
  unavailable.
- The monitoring process must terminate when Gradle exits and must not hide or
  replace Gradle's exit code.
- Do not expose credentials or proxy keys in monitor output.
- Give the build stage an explicit timeout longer than the observed asset
  download while keeping it bounded.

## Acceptance Criteria

- [ ] Both CNB entry points request `cpus: 2`.
- [ ] Gradle's heap/metaspace/worker limits fit a 4-GiB container with reserved
  headroom for Docker/container runtime and native JVM memory.
- [ ] The build stage prints network/liveness output at least once every
  60 seconds while Gradle is running.
- [ ] The monitor prints aggregate receive and transmit speed derived from
  `/proc/net/dev` when available.
- [ ] Gradle success and failure codes propagate unchanged after monitor
  cleanup.
- [ ] YAML and shell syntax are validated locally.
- [ ] Existing `new/` source and unrelated build behavior remain unchanged.

## Notes

- CNB documentation states that `runner.cpus: 2` corresponds to 4 GiB memory.
- CNB's default no-output timeout is 10 minutes. Declaring a task `timeout`
  also changes the no-output timeout to that value, but periodic diagnostics
  are still required for visibility and early detection of stalled transfers.
- The failing task was
  `neoFormJoined1.21.1-20240808.144430DownloadAssets`; the observed pipeline
  ended after 12m24s because it produced no output for 10 minutes.

## Open Question

- Exact explicit timeout for the Gradle build stage: `30m`.
