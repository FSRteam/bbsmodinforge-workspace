# FSR Audio Pipeline Implementation Plan

## Ordering

1. Complete and validate all child planning artifacts.
2. Implement `07-20-audio-format-contract` first; every later child consumes it.
3. Implement `07-20-audio-mixer-render` and its numeric golden tests.
4. Implement capture/playback and export-lifecycle children against the shared
   contracts; they may proceed in parallel once the format API is stable.
5. Implement Premiere descriptors/XMEML after the export result/range API lands.
6. Run the verification child across both repositories and perform parent-level
   integration review.

## Cross-Child Gates

- [x] No child exposes `SURROUND_5_1` or HRTF as supported.
- [x] Mono/Stereo setting, layout enum, FFmpeg metadata, WAV header, and XMEML
      all derive from one shared layout contract.
- [x] Export range and result types have one owner and no consumer reconstructs
      them from raw settings/filenames.
- [x] All readers/mixers address frames and handle exact short reads/padding.
- [x] No audio worker mutates UI/controller state directly.
- [x] Existing dirty Premiere changes remain present and are intentionally
      integrated rather than overwritten.

## Validation

Run focused child tests after every implementation step, then:

```powershell
cd new
.\gradlew.bat --no-daemon compileJava compileClientJava
.\gradlew.bat --no-daemon testVideoExportUtils
.\gradlew.bat --no-daemon check
.\gradlew.bat --no-daemon jar
Test-Path .\build\libs\BBS_FSR_beta_v0.0.10.jar
git diff --check

cd ..\bbs-premiere-export
.\gradlew.bat --no-daemon verifyBbsCoreJar compileJava compileClientJava `
  testPremiereExportContract check
git diff --check
```

This release runs Core `2.3.2`; Premiere declares `[2.3.2,)` and must resolve
the exact `../new/build/libs/BBS_FSR_beta_v0.0.10.jar`. Do not retain or resolve
an older jar filename/alias as an active fallback.

Run sample/media fixtures from the verification child, including `ffprobe` when
FFmpeg is available. Record actual hardware microphone/OpenAL and Premiere
import smoke tests separately; never report them passed from unit tests.

## Review Gates

- Numeric review: formats, resampling, channel identity, gain, limiting, dither.
- Lifecycle review: all terminal events reach one idempotent owner; strict
  process teardown observes the 750 ms deadline inside the one-second return
  budget.
- Media review: probed layout/rate/duration matches settings and XML.
- Performance review: allocation after warm-up is bounded per block; long mixes
  can be cancelled without client-thread stalls.
- Compatibility review: old settings and mono assets load; normal video exports
  remain usable when optional Minecraft audio is disabled or degraded.
