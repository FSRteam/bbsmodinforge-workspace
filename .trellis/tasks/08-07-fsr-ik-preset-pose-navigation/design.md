# FSR 1.21.1 IK, Preset And Pose Navigation Design

## Boundaries

- Modify Java sources and focused tests only under `new/`.
- Read FS behavior from `temp/bbs-fs-2.3` `origin/1.21.1` and port semantics,
  not files.
- Avoid unrelated dirty files where possible; if a required file is dirty,
  patch only the local method and review the final diff against its baseline.

## Design

### Stateless FS IK evaluation

Keep undo data ownership and history navigation on the ordinary FS path. The
solver must not require an undo-specific refresh or rebind: `ModelIKRuntime` is
a static, stateless entry point and production rendering passes no persistent
`ChainWorkspace` into the DLS solver. Every frame resolves the current target
position, target rotation, pole position, and control weights from the restored
Form/keyframe data. FSR keeps its bone-constraint input, but that input does not
change the FS target-resolution semantics.

### IK-owned rotation

Add the FS `ModelIKRuntime.isRotationConstrained` ownership query, adapted to
FSR's cached runtime. It checks compiled enabled chains, configured and film
weight overrides, chain membership, and `tipRotation`. Wire it into model-form
and film pose transforms. FSR's transform implementation blocks every rotation
gesture entry point and numeric input reached by an active rotation gesture;
the gizmo renders its rotation handles dimmed.

### Preset recovery

Add one reusable recursive `BaseType.equals` comparison that treats numeric
types by value. `UIDataContextMenu` uses it to select/scroll the structurally
matching preset both after list fill and on the first laid-out render. Reset
continues to call the existing callback with an empty `MapType`; storage is not
modified.

### Pose-sheet priority

Adapt the FS selection helpers in `UIReplaysEditorUtils` to FSR's existing
generic hierarchy support. Empty per-bone sheets fall back to preferred pose
sheets. Pose overlays use prefix matching so numbered overlays participate.
Insertion first reuses a keyframe at the current tick and only creates one in
the preferred pose sheet when none exists.

## Compatibility And Rollback

- No serialized format changes.
- No new settings or migrations.
- The old FSR maintain-offset workspace path remains available only as an
  internal compatibility overload; production `ModelIKRuntime` never creates
  or supplies that state.
- No Film history batching, forced preview invalidation, gesture-specific undo
  commit, or post-undo IK refresh is part of this fix.
