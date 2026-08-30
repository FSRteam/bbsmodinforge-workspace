# FSR FFmpeg Settings And Download Implementation

## Checklist

- [x] Extend `FFMpegUtils` with candidate-path health and required-encoder checks while preserving existing configured-path behavior.
- [x] Add immutable cross-platform manifest selection and normalized architecture handling.
- [x] Implement resumable proxy-first/direct-fallback transfer with strict Range response validation.
- [x] Implement exact size/SHA-256 validation, safe ZIP extraction or raw executable publication, Unix executable permission, `.ready`, staging cleanup, and atomic final move.
- [x] Add a generic consumed-first UI file-drop interface and preserve importer fallback.
- [x] Add the FFmpeg settings control with native picker, download button, status/progress projection, and client-thread state commits.
- [x] Add English and Simplified Chinese UI strings and generated `UIKeys` constants; other locales fall back to English.
- [x] Add focused executable regression tests for manifest selection, resume semantics, hash failure, ZIP traversal, publication, and crash recovery.
- [x] Run focused tests, `compileJava`, `compileClientJava`, resource parsing, and `git diff --check`.
- [x] Run `trellis-check`, inspect dirty-file overlap, and record any residual platform smoke gap.

## Risky Files

- `new/src/client/java/mchorse/bbs_mod/ui/framework/UIScreen.java`: file drops must still reach existing importers when no FFmpeg control consumes them.
- `new/src/client/java/mchorse/bbs_mod/settings/ui/UIValueMap.java`: the general `ValueString` factory must special-case only `videoEncoderPath`.
- `new/src/main/java/mchorse/bbs_mod/utils/FFMpegUtils.java`: existing video export and importer checks must retain their behavior and timeouts.
- language JSON files: preserve valid JSON and existing formatting style.

## Validation

From `new/`:

```powershell
./gradlew.bat testFFMpegDownload
./gradlew.bat testVideoExportUtils
./gradlew.bat compileJava compileClientJava
./gradlew.bat processResources
```

Repository checks:

```powershell
git diff --check
git diff -- <task-owned-paths>
```

Manual smoke remains required on at least one Windows client for native file dialog, drag/drop, visible progress, resume after forced exit, and successful video export using the installed path. Linux/macOS execution is covered structurally and by fixtures unless those hosts are available.
