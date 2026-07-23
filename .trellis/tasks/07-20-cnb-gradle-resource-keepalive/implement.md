# Implementation Plan

1. [x] Add the shared POSIX shell wrapper for Gradle, network-rate sampling,
   trap cleanup, and exit-code propagation.
2. [x] Change both CNB runners to 2 CPUs, set the build-stage timeout to `30m`,
   and invoke the shared wrapper.
3. [x] Reduce Gradle JVM memory and worker settings for the 4-GiB runner.
4. [x] Validate shell syntax, YAML structure, and the resulting diff; run the
   narrowest available Gradle configuration/build validation.

## Validation

- `sh -n cnb/gradle-build.sh`
- Parse `.cnb.yml` with an available YAML parser.
- Search both pipeline branches for `cpus: 2`, `timeout: 30m`, and the shared
  wrapper invocation.
- Run `./gradlew help --no-daemon` when the local environment permits it.

## Verification Results

- Local `./gradlew help --no-daemon --console=plain`: passed.
- CNB invariant scan: two `cpus: 2`, two `timeout: 30m`, and two shared-script
  invocations; no `cpus: 4`: passed.
- `git diff --check`: passed.
- Local Docker and Linux shell simulation: unavailable on this workstation.
