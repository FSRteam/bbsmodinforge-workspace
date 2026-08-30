# FSR FFmpeg Settings And Download Design

## Boundaries

- `FFMpegUtils` remains the single owner of FFmpeg path resolution and process health/capability checks.
- A new common-side `FFMpegDownloadManager` owns the immutable asset manifest, resumable HTTP transfer, hashing, extraction/publication, and managed-directory cleanup.
- A new client-side settings control owns file picking, drag/drop acceptance, progress projection, and committing a validated path to `BBSSettings.videoEncoderPath`.
- `UIScreen` remains the global GLFW drop entry point and gains only a generic consumed-first file-drop dispatch before the existing importer pipeline.

## Asset Manifest

The manifest is compiled into the mod and selected by `OS.CURRENT` plus normalized `os.arch` (`x86_64` or `aarch64`). Each entry owns:

- stable version id and platform id;
- original public GitHub Release URL;
- expected byte length and SHA-256;
- packaging type (`ZIP` or `EXECUTABLE`);
- executable file name and expected archive suffix.

Windows uses the fixed BtbN `autobuild-2026-08-16-13-00` FFmpeg 8.1 GPL ZIP assets. Linux and macOS use the fixed Shaka `n8.1.2-1` GPL executable assets to avoid adding an XZ/TAR dependency. Every installed candidate still has to expose `libx264` and AAC.

## Download Flow

```text
select manifest
  -> reuse ready + healthy managed executable when present
  -> inspect <asset>.part length
  -> GET gh-proxy.cn/<github-url> with Range when partial exists
  -> on transport/protocol failure, retry original GitHub URL
  -> stream to .part and publish throttled progress snapshots
  -> verify exact length and embedded SHA-256
  -> extract/copy into <platform>.installing
  -> mark executable on Unix
  -> run -version and -encoders capability checks
  -> write .ready inside installing directory
  -> atomically move installing directory to final platform directory
  -> remove .part
  -> client thread commits the returned executable path
```

When a resumed request returns `206`, the `Content-Range` start must equal the local size. When it returns `200`, the manager truncates and writes the full response from byte zero. `416` is accepted only when the local size already equals the manifest length and the final hash succeeds.

## Crash And Failure Semantics

- `.part` is durable and remains resumable across UI close, game exit, or JVM crash.
- `<platform>.installing` is never considered usable. A later run removes only this exact managed staging directory before retrying.
- `.ready` is written only after process and encoder checks pass.
- The configured setting changes only after the manager returns a successful result.
- A failed new install never removes a healthy final version or a user-selected binary.
- One daemon executor serializes downloads; an atomic in-flight reference rejects duplicate starts.

## UI Projection

The encoder setting becomes two compact rows:

```text
Encoder path   [text field                    ] [folder] [download]
               Status text / progress / speed / ETA
```

Buttons use existing icons and tooltips. Status colors use `BBSSettings.mutedTextColor()`, `positiveColor()`, `warningColor()`, and `negativeColor()`; no new hard-coded semantic colors are introduced. Worker callbacks are marshalled through `Minecraft.getInstance().execute(...)` before touching UI or settings state.

The generic drop consumer returns `true` only for a single existing candidate that resolves to the platform FFmpeg filename and passes asynchronous validation. Invalid names are not consumed, preserving the existing import pipeline. Validation failure is reported in the settings control without changing the value.

## Compatibility And Rollback

- Existing manually typed paths and directory resolution keep working.
- The old Windows disk scan context action is removed because it blocks the UI and is superseded by the local picker.
- Dedicated-server class loading remains safe: the manager uses JDK/common BBS classes only and performs no work until called by the client control.
- Rollback consists of removing the new client control/drop interface and manager; the persisted string setting remains compatible.

## Security

- Only hard-coded public GitHub Release URLs can pass through the third-party proxy.
- No runtime manifest, token, Cookie, user URL, or redirect-selected arbitrary host is accepted.
- Redirects are allowed for GitHub Release delivery, while final bytes remain bound by length and SHA-256.
- ZIP entries are normalized under the staging directory and rejected on path escape.
