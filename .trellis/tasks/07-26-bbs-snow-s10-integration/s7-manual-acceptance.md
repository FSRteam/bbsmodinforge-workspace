# S7 Manual Acceptance Queue

These checks require real mouse input, representative Film projects, visual review,
or optional third-party mods. S7 automation and strict-client startup do not claim
them as passed.

- Actions and numbered overlay tracks: select, replace, clear, edit loop/speed/fade/tick,
  delete either endpoint, undo/redo, and preserve unrelated actions on shared markers.
- Three-level bone hierarchy: expand/collapse hit targets, selection cleanup, replay/channel
  rebuild persistence, and unchanged IK/physics/sound/PBR ordering.
- Bone priority: default, expanded-limb, explicit pose/shape/bone-PBR choices, hidden or
  missing track fallback, multi-selection, and nested Form ownership.
- Docked and floating clip/replay/keyframe inspectors: switch selections and rebuild replay
  channels without losing or mis-parenting the active inspector.
- Body-part animation-to-pose conversion: linear, constant, catmull-rom and Bezier
  interpolation inheritance where the target editor can represent it.
- Bone texture/tint/whiten in normal Form and pose-keyframe hosts: multi-bone selection,
  undo/redo, save/reload, and disabled-setting layout.
- Pivot: edit, drag gesture boundaries, multi-selection, undo/redo, save/reload, null target,
  and disabled-setting layout in every supported transform host.
- Pose, per-bone and IK export: invalid input, unwritable path, name conflicts, target/pole
  tracks, non-target JSON preservation, and BBS/Blockbench readback.
- Preset rename/remove alongside BBS-owned copy/paste/reset/save behavior.
- Addon-disabled BBS FSR baseline plus current Together, Iris/PBR, audio import and the
  dedicated editing-export addon integration.
