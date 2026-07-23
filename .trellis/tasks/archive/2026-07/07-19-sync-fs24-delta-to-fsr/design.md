# 技术设计：FS 最新增量迁移到 FSR

## Baselines and invariants

- Target source tree: `new@b004afd2`; initial boundary commit: `36a8e0dd`;
  NeoForge 1.21.1, `mod_version=2.3.1`.
- Intent source: FS master `07bcc918..f76cce04`.
- Same-Minecraft source: FS 1.21.1 `65bb8c93..42313660`.
- Preserve FSR Mojmap, render-space/simulation-space ownership, lifecycle teardown,
  payload broker and legacy particle policy.

## Migration clusters

1. **Correctness/UI**: atomic writes, model watcher refresh, uniform-scale mutation,
   recent colors, FFmpeg preset, export artifact naming.
2. **Physics**: scaled rotation extraction and remaining body-part world/collision semantics.
3. **Minecraft audio**: sound capture -> frame collection -> offline stereo mix -> WAV ->
   ffmpeg mux, with cancel/failure cleanup owned by the export session.
4. **Translucent forms**: collect translucent draws, preserve opaque depth writes, sort
   back-to-front, flush at a NeoForge/Iris-compatible world render boundary.
5. **Fractional UI scale**: float setting, exact Window scale application, keyboard steps,
   viewport/stencil/picking dimensions based on the effective scale.

Shared files (`BBSSettings`, `BBSModClient`, `UIKeys`, language JSON, export sessions,
render lifecycle/mixins) are integrated after cluster implementations. Agents must not
overwrite another cluster's shared edits; main integration owns conflicts.

## Compatibility decisions

- Use master diffs for behavior, 1.21.1 files for Minecraft API shape, and `new` for
  NeoForge lifecycle/mapping. A missing 1.21.1 equivalent is a design warning, not
  permission to paste 1.20.x code.
- `b7bd5f22` and `f76cce04` require target-specific adaptation because they are newer
  than the current 1.21.1 merged master tip.
- `e4d7f7d9` is partial: retain FSR's per-renderer `simulationOwner` isolation and add
  only missing world propagation/collision behavior.
- `32c9cfb6` is partial: retain `VideoExportUtils.resolveArguments`; port only missing
  export-artifact classification and related lifecycle corrections.
- `8979e27f` must be evaluated against FSR's `cameraRelativeWorld`, `semanticWorld`,
  and `localSimulation` contract; do not mechanically apply its matrix patch.

## Rollback and ownership

- Implementation occurs from the recorded initial commit; existing untracked files are
  excluded from all staging.
- Each cluster must compile before integration. High-risk rendering/UI clusters remain
  separable commits so they can be reverted without removing correctness fixes.
- The terminal commit in `new/` contains only migration-owned source/resource changes.
  Trellis/spec files live in the parent workspace outside that Git repository, so the
  terminal SHA is read after commit and then written into task metadata and the baseline.
