# Audio Export and Premiere Package Contract

## 1. Scope / Trigger

Apply this contract when changing any of the following cross-layer path:

```text
F4 / F6 / Film panel
    -> immutable export request and owner
    -> recorder finalization
    -> optional Film render / Minecraft capture and mix
    -> FFmpeg mux
    -> owned artifact publication
    -> typed terminal result
    -> Premiere WAV manifest and XMEML package
```

The contract covers `VideoExportRequest`, `VideoExportResult`,
`VideoExportArtifact`, `VideoExportArtifacts`, `VideoExportSession`,
`WorldVideoExportSnapshot`, `VideoMuxer`, and the corresponding types under
`bbs-premiere-export`. It applies equally to success, cancellation, startup or
encode failure, requested-audio failure, publication failure, disconnect, and
world/UI teardown.

The source-backed baselines are:

- `new/src/client/java/mchorse/bbs_mod/film/VideoExport*.java` and
  `WorldVideoExportSnapshot.java`;
- `new/src/client/java/mchorse/bbs_mod/utils/VideoMuxer.java`;
- `bbs-premiere-export/src/client/java/mchorse/bbs_premiere_export/{model,audio,media,xml}/`;
- archived tasks `07-20-audio-export-lifecycle` and
  `07-20-premiere-audio-export`.

## 2. Signatures

- One export is identified by a non-null `sessionId`, positive `generation`,
  immutable `sourceId`, requested layout, and one half-open source range
  `[sourceStart, sourceEnd)`. F4 is explicitly `openEnd=true`; closed ranges
  are non-empty. Values consumed by Premiere must be exact integer BBS ticks.
- `VideoExportRequest` is the immutable owner snapshot. It carries identity,
  range, capture and output rates, dimensions, motion-blur/cadence settings,
  48 kHz `MONO` or `STEREO`, requested Film/Minecraft audio, encoder arguments,
  and the exact `VideoExportArtifacts` paths. Mutable settings must not be read
  again after this value is constructed.
- `VideoExportResult` carries a `Kind`, `Stage`, `FailureKind`, artifact,
  primary cause, cleanup failures, and bounded message. Its terminal kinds are
  `SUCCESS`, `DEGRADED`, `CANCELLED`, `PREPARATION_FAILED`, `START_FAILED`,
  `ENCODE_FAILED`, `AUDIO_FAILED`, `MUX_FAILED`, `PUBLISH_FAILED`, and
  `CLEANUP_FAILED`. Only `SUCCESS` maps to legacy success.
- `VideoExportArtifact` carries the exact final/raw/work/recovery paths and the
  request identity plus expected or observed dimensions, capture/output rate,
  delivered frame counts, duration, sample rate, layout, codec/profile/bitrate,
  audio presence, and `deliveryVerified`.
- `CompletionFence` validates the complete armed identity tuple and consumes
  at most one completion. A second matching completion is `DUPLICATE`; another
  session/generation is `STALE`; different source/range/layout metadata is
  `MISMATCH`; a retired fence cannot be reopened.
- Premiere projection uses a single immutable `PremiereExportManifest` of one
  probed `MediaDescriptor.Video` and typed `ClipAudioDescriptor` entries. Do
  not restore parallel audio-file/audio-clip lists.
- The external add-on entry point is
  `PremiereExportMod -> PremiereExportBootstrap.register() ->
  BBSMod.registerAddon(MOD_ID, PremiereExportAddon::new)`. Registration is
  process-idempotent and occurs during add-on mod construction.
- `PremierePackageResult.Status` is `SUCCESS`, `DEGRADED`, `CANCELLED`, or
  `FAILED`. Package failure defaults to `FAILED`; only the caller-owned
  `PackageFailurePolicy.RETAIN_VIDEO` may convert a failure with a real usable
  video into `DEGRADED`.

## 3. Contracts

### Identity, completion, and ownership

- Snapshot identity and range before recorder reservation/startup. Publish the
  start snapshot before synchronous preparation can complete. Every async
  callback, terminal callback, and Premiere handoff must match the armed
  `(sessionId, generation, sourceId, range, openEnd, layout)` tuple.
- Keep one owner through `FINALIZING`, audio work, mux, publication, cleanup,
  and exactly-one terminal delivery. A stale worker may clean only its own
  generation; it must not mutate or complete the active generation.
- Final captured frames become authoritative only after the recorder drains its
  last frame. Derive output-video and PCM durations from that delivered count;
  do not hide drift with `-shortest` or an independently recomputed end.
- On disconnect, world unload, screen retirement, or owner replacement, call
  the normal session cancellation path while the UI/controller owner is still
  reachable. Only after cancellation/teardown has been requested may the
  dashboard, panel, controller, or recorder reference be cleared/released.
  Cancellation is not a synthetic normal stop.

### Artifact provenance and collision safety

- Allocate a unique session work directory and exact raw-video, WAV, mux
  partial, log, final-video, and recovery paths before producers start. Never
  discover a result through directory mtime, basename polling, or
  `findLatestMp4`.
- `claim(path)` succeeds only while an exact known intermediate is absent.
  After the producer creates it, `markProduced(path)` must record stable
  regular-file identity using `BasicFileAttributes.fileKey()` with
  `NOFOLLOW_LINKS`. If the provider has no `fileKey`, the production fallback
  is a private hard-link anchor whose `Files.isSameFile` relationship proves
  identity.
- Revalidate the captured key/anchor before publish, recovery promotion, or
  deletion. A same-path replacement is foreign even when its name, size, or
  timestamp matches. If identity cannot be established or revalidated, retain
  the path and any surviving anchor, report the publish/cleanup failure, and do
  not move, delete, re-mark, or adopt the file.
- Producers write into a unique private/session staging area using `CREATE_NEW`.
  FFmpeg writes to its private mux directory, staged WAV/XML files are closed
  and probed, and only then may the owner publish them. Publication uses a
  same-volume move without `REPLACE_EXISTING`; a target collision preserves
  both the foreign target and the owned source/staging recovery material.
- Cleanup is idempotent and limited to registered, identity-proven artifacts.
  Never recursively delete an output root or a target which appeared during a
  failed move.

### Entry-point and audio-failure behavior

- An external NeoForge mod bus does not receive `BBSAddonRegisterEvent`
  instances posted on BBS' own mod bus. External add-ons must call the public
  `BBSMod.registerAddon(...)` API directly during mod construction and guard
  the call against duplicate construction. Client-only listener/UI startup
  remains a separate distribution-gated step. If direct registration throws
  before Core accepts or queues it, the idempotence guard must allow a retry.

- When the Premiere implementation is embedded in an existing multi-feature
  addon such as `bbs_snow`, do not keep the standalone Premiere mod/bootstrap
  or register a second settings file. The addon's client constructor must
  initialize one button installer, and the settings adapter must read and write
  the addon's existing `CMLSettings` values directly.
- A registered setting is not a feature entry point. The installer must poll
  the current dashboard instance, re-read the enabled value until installation
  succeeds, and install at most one button per dashboard. Enabling the setting
  after the dashboard was created must expose the button without a restart.
  Do not mark a disabled dashboard as installed.

- F4 owns an open-ended live-world range. A second F4 press normally stops only
  its active recording; during warm-up it cancels. It must not truncate an F6
  session. F4 has no Film clip manifest and must not fabricate Premiere audio
  clips.
- F6 snapshots the selected Film, full closed range, layout, and enabled audio
  clips. Natural Film completion drains/finalizes; toggling off the same Film,
  disconnect, or unload cancels. It must not act on an unrelated export.
- A Film-panel export normalizes its loop once to a non-empty half-open range
  and gives that exact range to video, Film audio, Premiere snapshotting, and
  XMEML rebasing. Reversed bounds are normalized once; an empty range fails
  before recorder/FFmpeg startup.
- When Minecraft audio was requested, unavailable capture, callback failure,
  loop/listener-frame failure, final snapshot failure, or partial capture is an
  observable typed audio failure. Preserve the first cause and suppress later
  cleanup causes. Partial captured events must not be mixed into an
  unqualified `SUCCESS`.
- Requested audio loss is `AUDIO_FAILED`, `MUX_FAILED`, or another explicit
  failure by default. `DEGRADED` is permitted only when an explicit retention
  policy deliberately keeps a valid video and the result identifies missing
  audio/recovery artifacts. A warning followed by a success callback is
  forbidden.

### Process teardown and delivery verification

- All FFmpeg mux terminal paths share one global five-second teardown budget,
  including stream close, liveness probes, graceful destroy/wait, forced
  destroy/wait, and confirmation. Do not restart the five-second clock for each
  phase or retry an exceptional `isAlive()` indefinitely. Preserve interrupt
  status, retain the primary mux failure, append teardown diagnostics, and
  release the terminal owner even when death cannot be confirmed by deadline.
- `deliveryVerified=false` means codec, layout, rate, duration, frame count,
  and audio-presence fields are expectations, not observed facts. A consumer
  may use them for validation but must probe the exact canonical final MP4
  before XMEML generation. Setting the flag to true is allowed only at the
  boundary that actually measured and cross-checked the delivered file.
- `MediaProbe` supplies real MP4 metadata from the exact session artifact:
  canonical path, regular-file containment, width/height, rational frame rate,
  exact frame count (including a bounded count-frames fallback), duration, and
  encoded audio stream rate/depth/channels/codec. `WaveReader` supplies real
  staged-WAV sample rate, bit depth, channel count, and sample-frame count.
  Probe failure or typed-versus-probed mismatch fails package generation.

### Premiere timing and channel projection

- For each clip, intersect its timeline interval with the captured export range.
  Convert source boundaries with exact rational arithmetic, clamp them to the
  real source length, and write a WAV containing only that excerpt. Rebase its
  placement once relative to the export range.
- Because the WAV is already trimmed, XMEML must serialize `in=0`. Its `start`
  and `end` are the descriptor's independently projected timeline boundaries,
  and `out` is the excerpt's measured sample duration converted to sequence
  frames. Never add `AudioClip.offset` in both WAV slicing and XMEML.
- XMEML rate comes from the probed MP4. Exact 24/30/60 fps emits
  `<ntsc>FALSE</ntsc>`; only 24000/1001, 30000/1001, and 60000/1001 emit
  `<ntsc>TRUE</ntsc>` with timebases 24/30/60. A UI NTSC preference cannot
  relabel an integer-rate file.
- Mono emits one output channel/track and source track index 1. Stereo emits two
  paired tracks referencing the same WAV file id, source track indices 1 and 2,
  `channelcount=2`, `numOutputChannels=2`, and the required link records. L/R
  sample order is preserved; no duplicated mono placeholder may masquerade as
  stereo. XML media fields come from the real MP4/WAV descriptors.

## 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Duplicate or stale terminal callback | Consume once; classify duplicate/stale and perform no package or active-owner mutation. |
| Session/generation matches but source, range, open-end marker, or layout differs | Reject as identity mismatch; do not scan for another MP4. |
| F4 pressed during its recording / warm-up | Normal stop during recording; cancel during warm-up. |
| F4 pressed during F6, or F6 toggled for another Film | Leave the unrelated export untouched. |
| Invalid/empty panel range | Typed preparation failure before recorder or FFmpeg startup. |
| Minecraft capture requested but unavailable or failed | Typed audio failure; partial data is diagnostic only, never plain success. |
| Mux cancel, timeout, interrupt, destroy failure, or liveness-probe failure | Finish teardown inside the single five-second budget, preserve the first failure, append cleanup diagnostics, deliver once. |
| Claimed file is replaced at the same path | Preserve replacement; refuse publish, promotion, cleanup, and repeated ownership marking. |
| `fileKey` absent and hard-link anchor cannot be created or checked | Fail closed and preserve the artifact/path; never infer ownership from pathname. |
| Final destination or Premiere package directory already exists | Fail publication without replacement; preserve destination and owned staging. |
| Typed artifact says `deliveryVerified=false` | Probe exact MP4 and compare all required metadata before generating XML. |
| MP4/WAV probe is missing, malformed, outside the owned root, or contradicts typed metadata | `FAILED`; publish no partial Premiere package. |
| WAV already contains the requested excerpt | XMEML `in=0`; apply source offset nowhere else. |
| Exact 30 fps MP4 with NTSC selected in UI | Emit 30/`FALSE`, or fail an actual-rate mismatch; never emit 30/`TRUE`. |
| Stereo impulse fixture | Left remains source track 1 and right remains source track 2 in the WAV and parsed XMEML. |
| Package audio/XML generation fails with a usable video | `FAILED` by default; `DEGRADED` only when explicit `RETAIN_VIDEO` policy is present and named. |
| Disconnect/world unload while a panel export owns resources | Fence startup, cancel panel/world sessions while owners are reachable, then clear UI/controller ownership and release lower-level resources. |
| External add-on listens for `BBSAddonRegisterEvent` on its own mod bus | Registration never arrives; replace it with one idempotent construction-time `BBSMod.registerAddon(...)` call. |
| Embedded addon exposes Premiere settings but never initializes the client installer | Reject the integration; settings visibility alone cannot create the monitor button or consume typed completion results. |
| Premiere is enabled after the dashboard was opened | Install once on the active dashboard during a later client tick; no restart and no duplicate button. |

## 5. Good / Base / Bad Cases

- Good: F6 delivers one fenced typed result; Premiere probes that result's exact
  MP4, writes and probes session-staged stereo WAVs, emits L/R XMEML with
  `in=0`, and publishes a unique package without replacement.
- Good: a foreign file replaces `mux-partial.mp4` after production. The stored
  `fileKey` or hard-link anchor no longer matches, so publication and cleanup
  report failure while preserving the replacement.
- Good: an FFmpeg process ignores graceful/forced termination and throws from
  liveness checks. The mux retains its original error, appends the unconfirmed
  five-second-deadline diagnostic, and still releases exactly one terminal
  completion.
- Base: F4 records video with an open end, the second F4 drains it normally,
  and Premiere emits a video-only manifest with no invented Film audio clips.
- Bad: poll `isRecording`, choose the newest `.mp4`, trust expected codec/rate
  values as measured facts, and announce success before probing/publication.
- Bad: trim by `sourceOffset` in the WAV and serialize the same offset in
  XMEML `<in>`, shifting the clip twice.
- Bad: catch Minecraft capture failure, mix the partial event list, or retain a
  video implicitly and report `SUCCESS`/`DEGRADED` without an explicit policy.
- Bad: clear `dashboard` or replace a panel/controller first, then attempt to
  locate and cancel the session whose resources were owned by that UI.
- Bad: subscribe the Premiere mod bus to BBS' add-on registration event and
  assume events cross between separate mod buses.

## 6. Tests Required

- From `new/`, `testVideoExportUtils` must exercise
  `VideoExportLifecycleContractTest`, `AudioExportLifecycleTest`,
  `VideoExportArtifactIdentityTest`, `MinecraftSoundCaptureContractTest`, and
  `VideoMuxerTeardownContractTest`. The gate covers typed request/result/artifact
  metadata, F4/F6 fences, panel range alignment, Minecraft capture failure,
  same-path replacement, unavailable identity, no-replace publication,
  cancellation, and bounded teardown.
- From `bbs-premiere-export/`, `testPremiereExportContract` must structurally
  parse XMEML and cover exact/NTSC rational rates, positive/negative/out-of-range
  offsets and source-end clamp, offset exactly once, mono/stereo track counts,
  stereo L/R source-track indices and links, XML escaping, probed WAV metadata,
  probed MP4 metadata, stale/duplicate completion fences, atomic publication
  collision, and `FAILED` versus explicit-retention `DEGRADED`.
- Hermetic tests may inject processes, probes, clocks, and filesystems, but they
  must assert typed values and actual file identity/contents rather than source
  text alone where a runtime seam exists.
- The Premiere contract gate must prove the production mod constructor calls
  the direct Core registration bridge exactly once and that the bridge does not
  reference `BBSAddonRegisterEvent` or install an event-bus listener.
- Optional real-media verification must use real `ffmpeg`/`ffprobe` output and
  compare stream presence, codec/profile, 48 kHz rate, one/two channels,
  dimensions, rational rate, frame/sample duration, and start/middle/end A/V
  markers within one output frame. Missing tools are `SKIPPED` normally and a
  failure in strict mode; mocks never count as real-media evidence.
- Also run the affected client compilation and `git diff --check` in the owning
  repository. Never weaken a contract assertion because the optional media
  tier or the exact configured addon core JAR is unavailable.
- For an embedded integration, inspect the release JAR for the button,
  installer, handler, completion fence, media probe, atomic publisher and XMEML
  classes. Assert the client entry point calls the installer, the handler uses
  `setFinishedResultListener`, and no source or bytecode contains
  `findLatestMp4`, recording-state polling, Fabric/Yarn client names, or the
  standalone Premiere mod/bootstrap package.

## 7. Wrong vs Correct

Wrong:

```java
modBus.addListener(PremiereExportBootstrap::onAddonRegister); // wrong mod bus
Path video = findLatestMp4(outputDirectory);
Files.move(partial, video, StandardCopyOption.REPLACE_EXISTING);
generateXmeml(video, clip.offset.get(), settings.isNtsc());
listener.onFinished(false); // capture/probe/package warnings were ignored
dashboard = null;
session.cancel();
```

Correct:

```java
PremiereExportBootstrap.register();
BBSMod.registerAddon(PremiereExportMod.MOD_ID, PremiereExportAddon::new);

CompletionFence.Acceptance accepted = fence.accept(
    artifact.sessionId(), artifact.generation(), artifact.sourceId(),
    artifact.sourceStart(), artifact.sourceEnd(), artifact.openEnd(),
    artifact.requestedLayout());

if (accepted.decision() != CompletionFence.Decision.ACCEPTED) return;

MediaDescriptor.Video video = mediaProbe.probeVideo(
    artifact.finalVideo(), artifact.finalVideo().getParent());
validateArtifactMetadata(artifact, video); // expectations versus real MP4

// The staged WAV already starts at the selected source frame.
xmemlClip.setIn(0);
xmemlClip.setStart(descriptor.timelineStartFrame());
xmemlClip.setEnd(descriptor.timelineEndFrame());

Path published = publisher.publish(staging, uniqueTarget); // no replacement

session.cancel();       // owner is still reachable
dashboard = null;       // release UI ownership afterward
```

Embedded wrong versus correct:

```java
// Wrong: the option is visible, but nothing projects it into the monitor UI.
premiereExportEnabled = builder.getBoolean("premiere_export_enabled", false);

// Correct: initialization is idempotent and the installer keeps checking a
// disabled dashboard until the existing setting becomes enabled.
PremiereExportButtonInstaller.initialize();
if (PremiereExportSettings.isEnabled() && !installed(dashboard))
{
    installButton(dashboard);
}
```
