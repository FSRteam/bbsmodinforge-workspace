# Film Cameras and Multiple Viewports

## 1. Scope / Trigger

Applies to Film camera persistence, editor view navigation, output camera selection,
and additional world passes in `new/` (Minecraft 1.21.1, NeoForge, Java 21).
The baseline shader integration targets Iris 1.8.8 and Sodium 0.6.13. The additional
runtime contract covers Iris 1.8.14-beta.1 with Sodium 0.8.13-beta.2 on 1.21.1.

## 2. Signatures

- `Film.resolveCameraId(int tick)`, `getCameraClips(String cameraId)`,
  `getCameraBasePosition(String cameraId)`, `calculateDuration()`.
- `Film.addCamera(String name, Position position)`, `renameCamera(String id, String name)`,
  `setActiveCamera(String id)`, `removeCamera(String id)`, `setCameraCut(int tick, String id)`.
- `CameraPoseEvaluator.beginFrame(long frameId, Film film, int ticks, float transition,
  boolean playing, IntObjectMap<IEntity> entities)` and `evaluate(String id, Position fallback)`.
- `BBSRendering.renderWorldFrame(GameRenderer renderer, DeltaTracker timer, Runnable primaryPass)`
  and `releaseViewResources()`.
- `IrisViewBackend.open(String id, boolean shaders, long epoch)` and `openPrimary(...)`
  return `IrisViewState.Scope`. `close()` restores state; `commit()` publishes its sample.
- `ViewSampleClock.begin(long now, float firstInterval)` measures realtime samples;
  `beginFixedStep(long now, float elapsedSeconds)` supplies deterministic export samples.
- `ViewFramebuffer.finishForPresentation()` finalizes auxiliary image alpha inside
  the owning render-state scope, after world/post rendering and before publication.
- `UIFilmPanel.getAuxiliaryBudgetFraction()` supplies the shared render-time budget;
  `ViewDescriptor.getResolutionWidth()` supplies the per-view preferred width limit.
- `ViewportPickIntent<T>` retains a click's target, input, owner generation and result
  until both release and a matching stencil sample are available.
- `UIContext.withPointerState(int mouseX, int mouseY, int mouseButton, Runnable action)`
  temporarily supplies captured click coordinates without dispatching another input event.
- `UIMotionPathContextMenu(UIFilmController controller, ValueMotionPath motionPath)`
  binds pin actions to that preview. The existing `(UIFilmPanel, ValueMotionPath)`
  constructor remains an adapter for the primary controller.

## 3. Contracts

### Film data and evaluation

`camera` remains the legacy clip collection with reserved ID `legacy`.
`camera_tracks` records contain stable `id`, `name`, `position`, and `clips` fields;
list indexes are not camera identities. `active_camera` names the fallback output.
`camera_cuts` records contain `tick` and `camera`.

Resolve the last valid cut at or before the playhead. If every valid cut is in the
future, resolve the earliest one. With no valid cuts, use the valid active camera
or `legacy`. Loading corrupt references retains their source data and produces
diagnostics. Deleting a camera repairs its output/cut references within one undo
transaction; the panel includes view bindings in the same undo snapshot.

The shared Film controller owns actors, actions, audio, recording and time.
Pose evaluators cache by stable ID for a scene frame and return defensive copies.
Pose-only clip evaluation must filter nested audio and effect callbacks, including
endpoint and shutdown paths; `AudioClip` is a `CameraClip` and must not slip through
an `instanceof CameraClip` whitelist. Editor focus never selects the export camera.

### Views, rendering and publication

`ViewDescriptor` and `ViewNavigationState` own each preview's binding, free view,
camera lock, frame pan/zoom, guides and quality requests. Persist these under
editor `film_views` settings, not in the Film camera track. An unlocked camera
frame pan/zoom does not change camera position or FOV. A locked gesture edits
the bound Film camera through the existing keyframe/undo path.

`UIFilmPreview.openMotionPath()` supplies `getViewController()` to the menu.
The pin icon and pin toggle use this same controller's `MotionPathPin`; a menu
opened in an auxiliary view must not read or mutate the primary view's pin.
Retain the panel constructor for addon compatibility, but do not use it for
view-specific toolbar callbacks.

Prepare the scene once, render requested auxiliary views, then render the primary.
Each auxiliary owns a Minecraft Camera, render targets, last successful camera
matrices and timing. Picking must use the matrices of the displayed image.
Publish a back target only after the pass and every restoration succeed; failed
or skipped passes retain the previous image and do not advance its sample counter.

Auxiliary previews display a borrowed RGBA world attachment. Vanilla celestial
rendering can leave non-opaque alpha even in an otherwise opaque world image,
which produces black sun/moon rectangles and star dots when the UI blends it.
Normalize only the completed auxiliary image's alpha to one after world/post/Iris
rendering. An alpha-only clear must preserve RGB/depth and execute before the
owning scope restores the framebuffer, scissor, color mask and clear color. Do
not apply this presentation step to transparent export targets.

Camera markers use the last successfully displayed camera matrices. Clip frustum
segments against the homogeneous frustum and positive W before perspective
division, then draw constant-width GUI lines within the preview's existing clip.
World-space prisms near the camera plane become broad bands and are not suitable
for these editor guide lines.

Film FREE mode (POV 1) retains the original FS mouse-look convention: right/down
left-button drags turn the camera right/down, so stationary scenery moves left/up.
The `fsr-film-home` baseline routes this mode through dashboard `UIOrbitCamera`
and `OrbitCamera`, not `OrbitFilmCameraController`. `UIFilmPanel.setFlight(true)`
seeds that actual controller before enabling input, without a Film-specific
rotation inversion. Keyboard flight, roll and FOV retain their existing direction.

Flight captures the owning Film, preview, stable camera ID, clip and original tick.
Starting flight in camera mode edits that camera while retaining camera mode,
including when the ordinary camera-to-view drag lock is off. Starting in free
mode only navigates that preview. The explicit flight action must not depend on
or change the drag-lock preference. Seed the dashboard controller from the preview;
`RunnerCameraController.setManual` must never overwrite that supplied pose with
the shared runner's previous sample. The previous sample may still be the origin
or belong to a different camera. Copy the latest orbit input before disabling it.
Explicit flight toggles, timeline seeks, clip selection and playback commit a
camera flight edit before replacing the original tick or clip editor. Lifecycle,
view-binding and undo cancellation still discard the transient edit. A stale or
deleted camera/clip cannot redirect a pending write to another camera.

Resolve editable static/keyframe clips at the captured tick; a future clip on a
higher layer must not force a one-tick overlay instead of updating the static clip.
Preserve the selected clip's editable exclusive end. `CameraPoseEditing` evaluates
the target camera's pose-only layer snapshots when removing modifier offsets;
snapshots from the legacy soundtrack runner are not valid for other cameras.
For a clip inside a compound timeline, endpoint evaluation carries the root clip
stack, the film-wide sample tick, and the selected clip's local tick separately;
root-list membership must not prevent `applyLast()` from running on that local
exclusive end. When dissolving a compound, an enabled parent envelope multiplies
the child's existing envelope factor; it must not overwrite the child's envelope
data unconditionally.
Keep flight writes as one undo transaction and invalidate cached poses afterward.
Event-level `[FilmFlight]` logs record start, finish reason/ownership and requested
versus evaluated write pose. Diagnostics must not interrupt undo completion or
emit per-frame position logs.

Film orbit keeps the FSR baseline's negative target-angle deltas, which make
the near side of its subject follow the pointer while retaining the pivot and
radius. Test Film flight and orbit through actual press/move/update/apply/release
routes from the same camera pose and project the same visible subject feature.
Also test default flight separately and reuse the controller after leaving Film
to detect changed input modes. Equal raw camera-angle deltas are not a visual
oracle: orbit translation makes near-side and far-side parallax opposite.

A first click on an inactive or low-rate preview may precede its valid stencil.
Do not interpret unavailable picking data as an empty pixel. Retain the original
button, coordinates and Alt/Control/Shift modifiers; a matching displayed-sample
stencil and release may arrive in either order and complete the click once.
Right clicks must preserve bone keyframe/context actions; only a confirmed miss
may open the world-placement menu. Keep the same-scene stencil guard: never pick
current actor geometry using matrices from an older displayed frame.

Fence delayed clicks by Film, view, replay, mode, playhead, history, layout and
gesture/menu ownership. Dragging, another press/scroll, a popup, hiding the view
or replacing its target cancels the old intent. Mouse movement or modifier release
after a short click must not silently change that click's captured coordinates or
bone mode. Scoped pointer overrides restore the outer event state even on error.

Preview toolbar rows need measured width before right/bottom anchoring. At narrow
dock widths, wrap full-size buttons through the existing Row/Grid resizers. At the
legal 80 by 80 minimum, both toolbars must fit without overlapping button regions;
resizing back must restore the normal arrangement and original button parents.
Keep playback, flight, camera mode, onion skin, motion path and recording on their
existing toolbar/shortcut routes rather than duplicating them in view options.

A clip selected programmatically in the property editor may have no timeline
selection yet, for example after flight creates a keyframe clip. Before starting
a timeline drag, add the actual hit clip to the selection and build both grabbed
clip and bounds lists from that same snapshot. Do not assume `delegate.getClip()`
implies a nonempty selection. Recheck gesture ownership and the clip collection
after selection callbacks; a cancelled or retargeted press must not start a drag.
Left/right edges, clip moves and cancellation must work from this initial state.

Camera menus, tooltips, dialogs, status messages and generated default names use
`UIKeys` and matching English/Chinese translation resources. Format complete
sentences with placeholders rather than concatenating English labels. Preserve
user-authored camera names and technical exception details as data. Every declared
`UIKeys` language key must resolve in both locales.

Iris records validate actual dimension, shader pack identity, target size, shader
mode and history epoch. Shader-off rendering preserves the shared chunk vertex
format; separate ambient occlusion stored in vertex alpha must be converted for
the unshaded terrain program. Do not create fake dimensions or rebuild the shared
world for each auxiliary. Initialize main block materials before auxiliary passes.

Read BBS-owned terrain shader sources through a BBS class resource anchor. NeoForge
isolates mod resources, so Sodium's `ShaderLoader` cannot read a shader in the BBS
namespace even when it is present in the packaged JAR. Sodium still owns its shader
includes, variant preprocessing and fragment source. Test resource loading with
separate owners; a flat test classpath alone cannot reproduce this failure.

Sodium compatibility must inspect actual method descriptors, including return
types. Unconditional mixin shadows cannot target the 0.6-only
`submitSectionTasks(ChunkJobCollector, ChunkUpdateType, boolean)`: 0.8 replaces it
with duration/upload budgets, integer update flags and mixed-priority queues.
The mixin only shadows fields common to both versions and delegates scheduling
to `SodiumChunkScheduling`. Keep native section submission, share one meshing and
upload allowance per outer frame, and finish each view's sorting before drawing.
An exhausted mesh allowance must not starve sort entries later in a mixed queue.
Additional views must not distort the native frame-duration estimate. Do not make
a version mismatch disappear by disabling required injections or silently skipping
all chunk updates.

Read the options accessor without binding its return descriptor to
`SodiumGameOptions`; 0.8 returns `SodiumOptions`. Point-camera and multiview culling
use the same `SodiumUtils.CullingState` capture/restore bridge and retain the exact
original boolean settings across nested view scopes.

Timeline seeks and pose edits invalidate temporal samples, not compiled pipeline
resources. A changed history epoch resets the view clock and requests Iris' existing
full color/shadow clear passes while retaining the same pipeline, shader programs,
framebuffers and resource owner. Consume the reset after a successful
`beginLevelRendering`; ordinary playback and a click on the current tick must not
restart accumulation again. Do not drive Iris' resize or shadow-target creation
branches merely to clear old pixels. Pack, dimension, shader mode or target-size
changes, and failed/unpublished passes still follow resource invalidation.

The FS seek contract is to update the shared playhead and send `ActionState.SEEK`,
with action/entity restart governed by the existing `editorRestartOnSeek` setting.
Ordinary seeks must not rewrite Film camera/replay data. A pending camera flight
still commits once at its original tick before the cursor changes. Regression tests
must cover repeated seeks retaining primary, auxiliary and unshaded pipelines,
one-frame local history clears, and unchanged Film data without an active edit.

Snapshot global Iris camera/material/shadow state and image/SSBO bindings around
auxiliaries. Bind each view's own SSBO holder at every `beginLevelRendering`.
The primary pipeline stays selected through Iris' outer `finalizeGameRendering`.
Capture resource allocations before fallible setup, including unfinished
constructors. Global samplers and shared shader caches are not view-owned.

Iris 1.8.8 tracks the main depth attachment by a RenderTarget-local buffer
version, without comparing its texture ID. Alternating preview back buffers can
have equal versions and different depth textures. The view adapter must detect
the identity change and trigger Iris' native depth reattachment branch. Do not
force a resize or clear temporal history for an ordinary back-buffer swap.
Iris' final color pass already checks both the color texture ID and its version.

Export shader intervals use seconds (`1 / captureFrameRate`), including motion
blur samples. Held/rate-limited frames use zero animation interval. Realtime
preview intervals measure time since the previous published image.

GPU query results are read only after availability; CPU submission time is kept
separate. All auxiliaries share one budget. Primary timing uses the actual target
dimensions. The minimum useful preview is 320 by 180 at 15 Hz; an unmet budget,
failed view or stale image must stay visible as such. A scheduler estimate is not
proof of the requested maximum 20 percent main FPS loss.

The September 12 runtime feedback rejects the former two-second budget probe.
An active view must retain the minimum live cadence when the budget cannot be
met, with an explicit budget-failure status. Small UI panes display a downscaled
image rendered at least at the minimum dimensions. Pipeline/FBO preparation is
tracked separately from steady rendering cost; changing only refresh rate must
not invalidate shader history. Use full-frame measurements, excluding auxiliary
work, for the FPS budget rather than treating a world-only pass as a whole frame.

Editor performance settings store `auxiliary_budget` as a shared fraction in
the range 0.05 to 2.0, with default 0.20. Each descriptor stores `resolution_width`:
zero means automatic sizing; a positive value is a preferred maximum width,
normalized to 320 through 1920 and fitted to the Film output aspect and minimum
useful dimensions. These are editor settings, not Film camera or export data.
The budget expresses additional render time, not a guaranteed FPS-drop percentage.
Changing either preference retries quality selection without discarding the
displayed image or measured cost. Only a real target-size/history discontinuity
invalidates shader history. The UI must label the resolution as an upper limit,
show actual dimensions, and preserve explicit budget-failure status after downgrade.

## 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Old Film with only `camera` | Legacy output and duration remain usable |
| Missing/duplicate camera ID | Retain raw data, diagnose, resolve valid fallback |
| Camera delete then undo | Restore track, cuts, output and panel bindings together |
| Popup, view hide or Film switch during gesture | End ownership without writing to a different camera |
| Auxiliary exception | Restore all registered state, retain last published image, expose failure |
| Interrupted pipeline construction | Release allocations from this construction only |
| Multiple cleanup failures | Attempt all releases; retain diagnostics and propagate fatal errors |
| Shader/size/camera discontinuity | Invalidate that view's temporal history |
| Equal buffer versions with different depth texture IDs | Let Iris reattach the new depth target without resizing |
| Pending GPU result | Do not block or manufacture a zero-cost sample |
| Completed sky image with transparent pixels | Present opaque alpha while preserving RGB/depth |
| Camera guide crosses the near plane | Clip before division and retain constant GUI line width |
| Increased budget or resolution limit | Retry quality selection; retain old image until successful publication |
| Resolution reduced but budget still unmet | Show both actual dimensions and explicit budget-failure status |
| First left/right click before a preview stencil exists | Resolve the original click after a matching sample, not as an empty hit |
| Alt released or cursor moved after a short bone click | Preserve its captured bone mode and coordinates |
| Pending click followed by another gesture or popup | Cancel the old click before another target can receive it |
| Film left-drag and default flight | Both retain FS camera look; right/down drags move stationary scenery left/up, including after control reuse |
| Camera mode + flight, with either drag-lock state | Keep camera mode, preview the movement, and commit to the captured camera/clip/tick |
| Free mode + flight | Move only the preview; leave Film camera data unchanged |
| Auxiliary motion-path menu pin toggle | Read and mutate only the controller that opened the menu |
| Narrow/short dock resized back to normal | Keep every toolbar button complete, clickable and non-overlapping |
| Disconnect/reload/shutdown | Release view resources on the render thread after an active frame finishes |

## 5. Good / Base / Bad Cases

- **Base:** an old Film opens with the legacy camera and one shared playback owner.
- **Good:** two previews bind the same camera with different frame zoom and shader
  modes, while a cut chooses another camera for video output.
- **Bad:** each preview creates a complete playback controller, advances audio,
  samples the main view's matrices, or publishes a failed framebuffer.
- **Good:** pinning a path from an auxiliary preview retains its own target and
  leaves the primary pin unchanged. **Bad:** pair an auxiliary configuration
  with `panel.getController()` for its pin icon or callback.

## 6. Tests Required

Run `gradlew.bat testFilmEditorMigration` and the full `check` / `build` gates.
The launcher includes camera serialization/resolution, endpoint/effect filtering,
navigation/settings/gesture ownership, render order/restoration, shared budget,
nonblocking GPU query, publication and Iris binary-contract regressions.
Settings regressions cover defaults, persistence, invalid values, aspect fitting,
shared-budget scaling and quality retry. Geometry regressions cover near/far/side
clipping, large world coordinates and constant screen width. Real UI verification
must confirm the performance menu is visible and settings reach the scheduler.
`FilmViewNavigationTest` covers actual orbit and dashboard flight gesture routes,
projected feature direction and `ViewportPickIntentTest` menu event lifecycles.
`FilmFlightEditingTest` uses the normal unlocked camera view by default. Cover both
lock states on primary/secondary views, real flight mouse input, live preview,
seek/selection commits, save/load, one-step undo and explicit free navigation.
`FilmViewToolbarLayoutTest` uses real UI layout and parent-dispatched button clicks,
including the legal 80 by 80 dock boundary and later expansion.

Motion-path ownership review traces both the icon supplier and toggle callback
to the opening preview's controller. The existing Film suite remains the build
regression check; the 2026-11-07 user instruction prohibits writing tests. Live
primary/auxiliary pin interaction still requires manual client verification.

The Iris binary-contract test checks fields, methods and injection anchors in the
pinned dependencies, including the Sodium mixins. Its separate entrypoint accepts
`bbs.compat.iris.jar` and `bbs.compat.sodium.jar` to inspect exact additional
dependency binaries without changing the baseline compile dependencies. The
Sodium fixture is the nested implementation JAR when the distribution uses JarJar.
`SodiumCompatibilityTest` additionally runs with the actual alternative runtime
classes, covering settings restoration, scheduler selection, deferred meshing,
native sorting and frame timing. These checks do not apply mixins. A real development client launch
is required to catch constructor injection constraints and interactions with
other mixins; exercise world rendering as well as reaching the title screen.
Shader image correctness and FPS claims require their own measured scene tests.

## 7. Wrong vs Correct

Wrong: publish the texture before `close()`, advance a global TAA counter for
every window frame, or construct an auxiliary through Iris' failure factory
that can delete all live storage buffers.

Correct:

```java
IrisViewState.Scope iris;
try (RenderPassScope outer = RenderPassScope.capture(minecraft))
{
    outer.use(backTarget, worldCamera);
    iris = IrisViewBackend.open(viewId, shaders, historyEpoch);
    try (IrisViewState.Scope active = iris)
    {
        renderIntoBackTarget();
    }
}
publishBackTargetAndMatrices();
iris.commit();
```

Keep the failed image unpublished if rendering or restoration throws. The
complete production scope also owns the framebuffer, model-view stack,
projection, cameras, world post chains and GL bindings.
