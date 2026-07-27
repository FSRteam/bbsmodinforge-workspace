# S6 source-to-target drift

## Source inventory and ownership

| Source behavior | Current FSR evidence | S6 action |
|---|---|---|
| Five mini-window classes | No `MiniWindow` implementation in `new/src` | Port |
| Generic `UIDockLayout` detach/restore | Target methods and maps still exist | Port against accessors |
| Film detach/restore and preview forwarding | Target Film fields/methods still exist | Port |
| Base-menu float layer | Target root still owns `main`, then `overlay` | Insert layer between them |
| Overlay eight-way resize | Target overlay supports drag only | Port handles additively |
| Particle dock cleanup on disappear | `UIParticleSchemePanel.dock` and dashboard disappear exist | Port |
| Clip/keyframe inspector re-embed hooks | Depend on S7 replay helper | Defer hooks to S7 |
| `UIScreenMixin` async audio drop | Current FSR audio importer contract was rewritten | Defer/adapt later |

## Signature audit

- `UIDockLayout` retains private `panelById`, `iconById`, `layoutRoot()`, `setLayoutRoot(...)`,
  `createPanelDragHandle(String)`, `ensureRegisteredPanels(EditorLayoutNode)`, and public
  `setupFlex(boolean)` / `resetLayout()`.
- `UIFilmPanel` retains `panelById`, private layout getters/setters, `setupEditorFlex(boolean)`,
  `ensureFilmLayoutPanels(EditorLayoutNode)`, `getEditPanelTopOffsetPx()`,
  `createPanelDragHandle(String)`, `renderPanelSurfaces(UIContext)`, `resetFilmLayout()`,
  `showPanel(UIElement)`, and `disappear()`.
- Current Film still exposes camera/replay/action editors, preview, edit area, dashboard orbit UI,
  and the public editor-switch buttons used by the source integration.
- The source fork's `UIReplaysEditor.isActionsMode()` no longer exists. Current FSR owns actions as
  a separate `UIFilmPanel.actionEditor`, so inspector ownership uses that editor directly instead
  of mapping an internal replay mode.
- Current FSR keeps `selectedMainEditorPanel` as a stable private field. S6 extends the existing
  `UIFilmPanelLayoutAccessor` contract instead of retaining the source reflection-plus-silent-
  camera-fallback path; Mixin startup now validates the field explicitly.
- `UIBaseMenu` construction still appends `main` and `overlay` to one root in that order.
- `UIOverlayPanel(IKey)` remains the single constructor and keeps content/title/icon ownership.

## Audio screen drift

The source `UIScreenMixin` delegates to `AudioDropImporter`, which copies WAV/OGG directly and
invokes ffmpeg itself for other formats. Current FSR instead routes audio through
`AudioImporterSupport`, which owns temporary files, validates WAV output, checks channel layout,
publishes without overwrite, and returns structured `ImportOutcome` failures. Porting the old
screen Mixin in S6 would bypass those guarantees and duplicate file-publication logic. Any later
timeline-drop feature must wrap current importers asynchronously and preserve their outcome and
publication contracts.

## Target contracts to preserve

- Modal overlays remain above mini windows; generic mini layers do not become invisible blockers.
- Persisted layouts never retain floating panel ids or lose registered panels.
- Ordinary non-floating dock panels keep their target-defined layout, visibility, order, and input.
- Film preview pointer events are forwarded once and releases always clear orbit ownership.
- Helpers stay outside `mchorse.bbs_snow.mixin.client`.
- `defaultRequire: 1`, no refmap, strict target descriptors, and real client execution remain
  mandatory.
