# S10 Upstream-Sync Manual Acceptance (08-03 bbs_snow 1.0.0 port)

These checks need a live game, a real Film/model project, visual review, or
specific interaction. Strict-client startup and the runtime verifier do not
claim them as passed.

## F1 Animation-state editor dock layout
- Form editor → animation state: a 3-panel dock (preview / timeline / inspector)
  appears instead of the fixed two-panel layout; the dock's preview area is
  transparent (shared model renderer visible behind it).
- Dragging splitters re-lays out the three panels; the layout persists across
  closing/reopening the form editor and across worlds (animation_state_layout).
- Lock icon freezes the layout; presets icon saves/restores named layouts;
  reset icon restores the default split.
- The timeline panel hosts the keyframe timeline; the inspector panel hosts the
  pose keyframe factory. Selecting a keyframe shows the correct factory.
- Undo/redo on a pose keyframe rebuilds the factory and restores the selected
  bones (no stale gizmo / wrong-selection after undo).
- The preview panel's bone-priority button opens the BonePriority menu; right
  click on the preview passes through to viewport bone picking (no dock restore
  menu), and the preview panel cannot be floated to a mini window.

## F2 Actions additive overlay stacking
- With more than one action overlay layer, layers stack (addition) on top of
  the base action and each other, instead of lower layers overwriting.
- Toggling `snow_actions` off restores vanilla overwrite behavior immediately.
- Both cubic and BOBJ (skeletal mesh) forms behave the same.

## F3 Texture-grade UX
- Pose editor: the texture-grade section is a collapsible card (default
  collapsed); selecting a bone shows it; the tint color's first RGB change
  auto-enables alpha.
- Expanded pose limb keyframe panels have the same tint/whiten controls, and
  edits apply to the selected keyframe(s).
- Wide vs narrow pose keyframe factory layouts both keep the tint/whiten
  controls in the left parameter column (no blank gap after the bone list).

## F4 Mini-window robustness
- Docking a panel into a mini window, merging two windows, and restoring via the
  restore button or the dock right-click menu all leave the host layout correct.
- Restoring a mini window from its own restore button does not leave the input
  traversal pointing at detached elements.
- The animation-state preview panel (INonFloatingDockPanel) cannot be floated,
  and its content input passes through to the viewport.