# Verification Implementation Plan

## Ordered Work

This child owns verification source/resources and required Gradle entry points.
Production contracts remain owned by sibling tasks.

1. [x] Freeze Mono/Stereo-positive format/layout/range/result/block/cancellation
   contracts in independent and procedural fixtures.
2. [x] Add `audioFormatTest` and `AudioFormatContractTest` for PCM/WAV/Vorbis,
   padding/short reads, metadata, duration, channels, limits, and cleanup.
3. [x] Extend `testVideoExportUtils` with focused renderer/mixer/import/export
   regressions for routing, conversion, gain/overlap, limiting, ordering,
   lifecycle outcomes, and resource ownership.
4. [x] Add `audioLifecycleTest` and `AudioLifecycleTest` for capture/playback
   final drain, cancel/error/interrupt, dispatcher ownership, independent
   voices, relink/removal/reload/shutdown, and exactly-once cleanup.
5. [x] Add `audioVerificationTest` with
   `AudioVerificationContractTest`, `AudioLayoutAvailabilityTest`, and
   `AudioProductionPerformanceTest`; wire hermetic tasks into `new` `check`.
6. [x] Add explicit `testAudioMediaIntegration` for strict FFmpeg/ffprobe media,
   three-path marker alignment, typed failures/timeouts, cleanup, and the
   opt-in procedural performance gate.
7. [x] Add `PremiereExportContractTest` under the add-on `test` source set and
   wire `testPremiereExportContract` into its `check` lifecycle.
8. [x] Rebuild the exact core jar, then rerun the add-on gate after the latest
   `PremiereExportHandler` and source-set fixes. The final execution used the
   fresh exact jar and did not reuse the earlier add-on PASS.

## Planned Verification Files

- `new/src/audioFormatTest/java/mchorse/bbs_mod/audio/AudioFormatContractTest.java`
- `new/src/audioLifecycleTest/java/mchorse/bbs_mod/audio/AudioLifecycleTest.java`
- `new/src/audioVerificationTest/java/mchorse/bbs_mod/audio/AudioVerificationContractTest.java`
- `new/src/audioVerificationTest/java/mchorse/bbs_mod/audio/AudioLayoutAvailabilityTest.java`
- `new/src/audioVerificationTest/java/mchorse/bbs_mod/audio/AudioProductionPerformanceTest.java`
- `new/src/audioVerificationTest/java/mchorse/bbs_mod/audio/AudioMediaIntegrationTest.java`
- `new/src/videoExportTest/java/mchorse/bbs_mod/utils/VideoExportUtilsTest.java`
- `new/src/videoExportTest/java/mchorse/bbs_mod/utils/VideoMuxerTeardownContractTest.java`
- `new/src/videoExportTest/java/mchorse/bbs_mod/audio/AudioRendererMixerFocusedTest.java`
- `new/src/videoExportTest/java/mchorse/bbs_mod/audio/MinecraftSoundCaptureContractTest.java`
- `new/src/videoExportTest/java/mchorse/bbs_mod/importers/types/AudioImporterSupportTest.java`
- `new/src/videoExportTest/java/mchorse/bbs_mod/film/AudioExportLifecycleTest.java`
- `new/src/videoExportTest/java/mchorse/bbs_mod/film/VideoExportLifecycleContractTest.java`
- `new/src/videoExportTest/java/mchorse/bbs_mod/film/VideoExportArtifactIdentityTest.java`
- `new/src/videoExportTest/java/mchorse/bbs_mod/film/VideoExportSessionTest.java`
- `bbs-premiere-export/src/test/java/mchorse/bbs_premiere_export/PremiereExportContractTest.java`
- `new/build.gradle` and `bbs-premiere-export/build.gradle` for actual source-set,
  JavaExec, exact-core dependency, and `check` wiring

Fixtures are intentionally embedded or generated independently into temporary
directories; there is no checked-in `audioVerificationTest/resources` manifest
in the implemented surface.

Do not edit generated `build/`, `bin/`, `runs/`, logs, or sibling task files.

## Validation

```powershell
cd new
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon testAudioFormat testAudioLifecycle `
  testAudioVerification testVideoExportUtils
.\gradlew.bat --no-daemon check
git diff --check
```

Strict optional media verification:

```powershell
.\gradlew.bat --no-daemon testAudioMediaIntegration `
  -PffmpegPath="C:\path\to\ffmpeg.exe" `
  -PffprobePath="C:\path\to\ffprobe.exe" `
  -PrequireMediaTools=true `
  -PaudioVerificationPerformance=true
```

Addon verification requires the exact configured core dependency. Runtime Core
is version `2.3.2`, the addon declares the required range `[2.3.2,)`, and it
resolves `../new/build/libs/BBS_FSR_beta_v0.0.10.jar` (beta filename build `10`
at this task's verification baseline). Rebuild that artifact from the frozen
source snapshot before compiling the addon; no older jar name, compatibility
alias, alternate copy, or stale jar may satisfy the gate:

```powershell
cd ..\new
.\gradlew.bat --no-daemon jar
cd ..\bbs-premiere-export
.\gradlew.bat --no-daemon compileJava testClasses
.\gradlew.bat --no-daemon testPremiereExportContract
.\gradlew.bat --no-daemon check
git diff --check
```

If the jar is absent/mismatched, report the resolved path/version as `BLOCKED`.
Normal missing media tools may only be `SKIPPED`; strict mode must fail.

Current evidence and the completed serial order are recorded in
`verification-record.md`. Core `check`, strict media, the ten-minute
performance gates, exact-jar rebuild, and the repaired Premiere
handler/source-set gate all have passing evidence.

## Review Gates

- **Numeric:** independent expected samples, frame counts, channel order,
  tolerances, resampling, limiting, and quantization are correct.
- **Ownership:** exact close/delete counts, cancel creates no clip, and recovery
  never overwrites/deletes user files.
- **Media:** ffprobe metadata and markers agree with layout/rate/range; no hidden
  `-shortest` loss is accepted.
- **Observability:** every degraded/failure path has a typed result and bounded
  diagnostic; warning plus success callback is not a pass.
- **Performance:** at most 8,192 frames/block, one post-cancel block, <=64 MiB
  growth, a 750 ms strict process-teardown deadline, a 1-second return/cancel
  target, and a 5-second tool-version diagnostic timeout.
- **Scope:** no positive 5.1/HRTF case, no fake claimed as hardware evidence, and
  no unrelated dirty edit reverted.

If a sibling API is not ready, retain the exact test prerequisite and blocker;
do not permanently skip the assertion or change production behavior here.
