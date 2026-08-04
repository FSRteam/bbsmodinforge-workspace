# Design: sync FS 27a6866c Dock delta to FSR

## Boundaries

The source behavior boundary is FS `5848363c..4220b453`, confirmed by the
`1.20.1` merge `813be205..27a6866c`. The target starts from clean FSR
`origin/master@156d0bff` in a dedicated worktree.

This is a semantic port. FSR has a newer `UIDockLayout`, animated layout changes,
per-editor layout binding, Refreshed theming, extra settings, and deferred UI
hierarchy mutation. Upstream files must not replace those implementations.

## Commit Disposition

| Upstream | Design disposition |
|---|---|
| `6f4f9ec9` | Port all user-visible Dock behavior onto the FSR component and immutable layout tree. Preserve FSR animation and per-editor layout binding. |
| `0cecc8c9` | Audit all seven calls. FSR already passes width/height at the affected sites; add only regression coverage or an unambiguous helper where it reduces future misuse. |
| `4220b453` | Port detached-property-panel teardown and generalize the existing splitter callback to a layout-settled callback. |

## Contracts

### Layout state

- `EditorLayoutNode` remains the immutable structural tree.
- Hidden panel IDs and the layout lock are persisted by `ValueEditorLayout` and
  remain compatible with older data that lacks those fields.
- A maximized panel is transient and does not replace the saved tree.
- Layout history stores immutable tree snapshots plus hidden-panel state. It is
  bounded and records only completed layout mutations.
- Layout changes continue through `ILayoutSource`; FSR per-editor bound layouts
  remain the active source of truth.

### Interaction

- A tab click remains a click until pointer movement crosses the drag threshold.
- Escape cancels active panel drag or transient maximization.
- Shift plus center drop swaps panels instead of stacking them.
- Hovering another tab during drag activates it after the upstream delay.
- Splitter minimum size is computed in pixels so nested panels remain usable.
- Layout settled fires once after a completed structural/visibility/tab mutation
  and after splitter drag end, never on every frame of splitter dragging.

### Ownership

- `UIClipsPanel.removeFromParent()` removes its externally parented `UIClip`.
- `UIKeyframeEditor.removeFromParent()` removes its externally parented
  `UIKeyframeFactory`.
- FSR deferred hierarchy-mutation generation checks remain intact.

### Localization and rendering

- Dock rendering continues through `UIDockStyleRenderer`; no raw upstream colors
  replace Refreshed/theme tokens.
- Add English, Russian, and Simplified Chinese labels for panel names and commands.
- Drop previews use the theme renderer and preserve FSR layout animations.

## Compatibility and rollback

- Old saved layouts deserialize with `hidden=[]` and the current lock default.
- Reset clears hidden state and is undoable.
- A failed Dock cluster can be reverted without touching the lifecycle cluster.
- No change may touch particle or translucent Form ownership.

## Validation

Add focused source/logic tests for layout tree operations, hidden/maximized state,
layout-settled callback wiring, strict undo bindings, detached panel cleanup, and
the seven clip calls. Then run the existing film/UI tests, both Java compilations,
full `check`, full `build`, and version/diff invariants.
