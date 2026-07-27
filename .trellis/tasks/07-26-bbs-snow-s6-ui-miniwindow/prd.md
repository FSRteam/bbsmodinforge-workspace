# S6 UI framework extensions and mini windows

## Goal

Port the `bbs_snow` floating mini-window system to the standalone NeoForge 1.21.1 addon and bind
it to the current BBS FSR dock, Film editor, base-menu layering, and overlay-panel contracts. Users
must be able to detach, move, resize, merge, focus, collapse, and restore supported panels without
breaking the ordinary dock layout or viewport input.

## Confirmed facts

- Current BBS FSR has no mini-window implementation, but still exposes the source target classes
  and private method shapes used by the Dock and Film accessors ported in S2.
- The source feature consists of five `ui/miniwindow` classes and five mini-window-specific Mixins:
  `UIDockLayoutMiniWindowMixin`, `UIFilmPanelMiniWindowMixin`,
  `UIDashboardPanelMiniWindowMixin`, `UIBaseMenuFloatLayerMixin`, and
  `UIOverlayPanelResizeMixin`.
- `UIClipInspectorEmbedMixin` calls the S7 `UIReplayKeyframeEmbedHelper`; it is an S7 re-embed hook,
  not part of the S6 window lifecycle.
- The source `UIScreenMixin` is an asynchronous audio-drop/timeline feature. Current FSR replaced
  its audio import internals with validated `AudioImporterSupport` publication and `ImportOutcome`
  contracts, so copying the old direct ffmpeg path would regress current behavior. It is deferred
  to integration work against the current audio contract.
- S2 already supplies `FloatLayerAccess`, `UIDockLayoutAccessor`, `UIFilmPanelLayoutAccessor`, and
  `UIClipsPanelAccessor`, plus their configured accessor Mixins.

## Requirements

### R1 Mini-window shell

- R1.1 A mini window hosts one or more panels with icon tabs and one active panel.
- R1.2 It supports title dragging, eight-direction resizing, collapse/expand, restore, soft viewport
  clamping, and session-state capture/restoration.
- R1.3 Clicking a background window or one of its children brings it to the front. Empty window
  content must not click or scroll through to unrelated dock controls.
- R1.4 Dropping one mini window over another merges its panels into tabs while preserving the
  active panel, viewport-input policy, and restore behavior.

### R2 Generic dock integration

- R2.1 Unlocked `UIDockLayout` panel drag handles expose detach/restore actions. Locked layouts do
  not expose detach.
- R2.2 Floating panels are removed from the persisted layout tree without losing their registered
  panel or icon identity; restoring them inserts a valid layout node and panel parent.
- R2.3 Layout rebuild, reset, and particle-dashboard exit preserve or safely clear floating state.
- R2.4 The mini-window layer stays above dock contents without becoming a full-size input blocker.
  Non-floating panels retain their original visibility, ordering, sorting, and input behavior.

### R3 Film editor integration

- R3.1 Camera, replay, preview, edit-area, replay-list, and replay-properties panels can float while
  the Film layout and its shared `main` container remain structurally valid.
- R3.2 Independent camera/replay timelines remain visible and enabled when floated; switching the
  selected editor updates only the appropriate legacy `main` window.
- R3.3 Preview-window click, scroll, and release are forwarded to the Film/Dashboard orbit controls
  exactly once. Release forwarding must clear orbit/pan state.
- R3.4 Inspector ownership and parent synchronization remain coherent when the main timeline or
  edit area is floating. S7 owns the later selection/rebuild re-embed hooks.
- R3.5 Leaving Film or resetting the Film layout restores/clears all floating panels and offsets.

### R4 Framework layering and overlay resizing

- R4.1 `UIBaseMenu` exposes a non-modal float layer between `main` and modal `overlay`; the layer is
  reattached in that order if another UI rebuild detaches it.
- R4.2 Every `UIOverlayPanel` receives eight resize handles with minimum dimensions. Its existing
  title drag, close control, modal propagation, and content controls remain usable.

### R5 Strict addon compatibility

- R5.1 Bind current FSR method descriptors with required Mixins, `defaultRequire: 1`, no refmap,
  and no optional injection weakening.
- R5.2 Keep helper/UI classes outside the configured Mixin package and do not modify or bundle BBS
  FSR classes.
- R5.3 Add English and Simplified Chinese mini-window labels under the `bbs_snow` namespace.

## Acceptance criteria

- [x] AC1 `verifyBbsCoreJar` and `gradlew build --console=plain` finish with exit code 0.
- [x] AC2 All five S6 Mixins apply and the strict client verifier records exactly five S6 Mixins.
- [x] AC3 Runtime verification exercises mini-window add/remove/active/collapse/state behavior,
  manager undock/redock and layout stripping, focus/z-order, base-menu float-layer order, and
  overlay resize-handle attachment, merge ownership, and minimum-width enforcement.
- [x] AC4 Runtime verification covers ordinary dock/layout isolation and preview release forwarding
  semantics without weakening any injection.
- [ ] AC5 Manual S10 smoke: detach, drag, resize, merge, collapse, focus, and restore particle and
  Film panels; operate Film preview orbit/pan and inspector controls after detaching.
- [x] AC6 The packaged jar contains every S6 class/resource and no `mchorse/bbs_mod/**`, Fabric
  metadata, old namespace, refmap, or unexpected optional injection.
- [x] AC7 `new/` and `bbs_FSloveCML_1.21.1/` source trees remain untouched by S6.

Manual status on 2026-07-27: the client launches and most settings/features are visible. Specific
detach, drag, resize, merge, Film orbit/pan, and inspector interactions have not been confirmed and
remain explicitly assigned to S10 rather than being inferred from visibility.

## Out of scope

- S7 clip/keyframe/replay selection hooks that re-embed newly rebuilt inspectors.
- Replacing current FSR audio import publication with the source `UIScreenMixin` direct-ffmpeg
  implementation; any asynchronous timeline drop work must preserve the current audio contract.
- Persisting mini-window geometry across game restarts; the source behavior is session-only.
- Redesigning the dock, Film editor, or overlay appearance.

## Open questions

None. The parent task requires source-compatible behavior and the current FSR code determines the
deduplication and signature choices.
