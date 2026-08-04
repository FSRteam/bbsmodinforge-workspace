# S7 source-to-target drift

## 1. Source inventory

### Port in S7

- 5 utility owners: `AnimationFileOperations`, `AnimationInterpInheritance`, `BonePriority`, `IKAnimationExporter`, `PoseAnimationExporter`.
- 5 UI/helper classes: `UIReplayKeyframeEmbedHelper`, `ExportUIKeys`, `ReplayUIKeys`, `FilmIKSpatialTargetSampler`, `UIExportAnimationOverlayPanel`.
- 22 Mixins/Accessors: Actions (5), inspector/embed (3), replay/keyframe (6), pose/transform (6), preset/Film preview (2).
- Existing S2 API interfaces and common Mixins are dependencies, not duplicate S7 copies.

### Deliberately excluded

| Source item | Evidence | Decision |
|---|---|---|
| `UIReplaysEditorExtended`, `BoneTextureUtils` | No production caller | Dead source code; exclude |
| `UIQuickReplayOverlayPanel`, `ClientEventHandler` | Handler registration has no caller; handler only logs and never opens the panel | Do not invent activation behavior |
| `TogetherClientNetworkReplaySelectionMixin` | Optional third-party target plus synthetic lambda and `require=0` | Defer current Together contract to S10 |
| `UIScreenMixin` | Direct copy/ffmpeg path bypasses current FSR audio importer ownership and safe publication | Exclude |
| S8/S9/Premiere classes | Owned by other child tasks | Exclude |

## 2. Current target signature audit

Audited against `new/build/libs/BBS_FSR_beta_v0.0.10.jar` with private descriptors, 2026-07-27.

| Mixin group | Current FSR target evidence | Drift/action |
|---|---|---|
| `ModelFormTrackNameMixin` | `Form.getTrackName(String):String` exists | Descriptor stable; append fallback-only naming |
| Actions Form/factory | `UIActionsFormPanel(UIForm)`, `startEdit(ModelForm)` and `UIActionsConfigKeyframeFactory(Keyframe,UIKeyframes)` exist | Constructor/Tail hooks stable |
| Actions editor | `pickAction(String,boolean)` exists; lambdas are `$0..$5`, with `$2(UIToggle)` and `$3..$5(Double)` | Source lambda assumptions require bytecode audit; use full descriptors/field anchors |
| Clip/keyframe inspector | `UIClipsPanel.pickClip(Clip)` and private `UIKeyframeEditor.pickKeyframe(Keyframe)` exist | Exact descriptors stable |
| Replay editor | `UIReplaysEditor.updateChannelsList()`, `getColor(String)`, `getIcon(String)`, fields `filmPanel`, `replay`, `keyframeEditor` exist | Target now owns pose tabs, IK/physics, replacement editor and many new lambdas; append only at named method boundaries |
| Replay utils | 5-arg `pickForm`, private `resolveBoneSheet(UIKeyframeEditor,String,String)`, private `fillAnimationPose(...,int)` exist | Exact descriptors stable; preserve current shortcut/IK logic unless addon priority applies |
| Dope sheet | `configurePoseTabs(Map,Map,Set)`, private `isVisible`, private `getSheetIndent`, private render path and pose-tab maps exist | FSR already owns first-level pose tabs; add second-level bone collapse only |
| Pose editor | `UIPoseEditor` constructor and `pickBones(List)` exist; `UIModelPoseEditor.valuePose`, nested factory `editor/keyframe` fields exist | Accessor path remains valid; adapt current UI layout APIs |
| Pose factory | `UIPoseKeyframeFactory.resize()` and nested editor `add(IUIElement)` overloads exist | Redirect descriptors must be checked against current bytecode |
| Transform | `UITransform()` and `UIPropTransform.setTransform(Transform)` exist | Abstract UI fills pivot; concrete prop editor owns writeback |
| Preset menu | `UIDataContextMenu(DataManager,String,Supplier,Consumer)` and required private state exist | S2 `PresetDataOperations` can be wired without duplicate data logic |
| Film preview | `UIFilmPreview(UIFilmPanel)`, private `panel`, public `icons`, `subMouseScrolled(UIContext)` exist | Add BonePriority control without replacing current preview controls |

## 3. Important semantic drift

### Current FSR is not the old base UI

- `UIKeyframeDopeSheet` already models `poseTabRoots`, `poseTabDepths`, `poseTabParents` and `expandedPoseTabs`; source collapse code must operate beneath that layer.
- `UIReplaysEditor` already curates form, IK and physics sheets, persists expanded pose tabs and replaces editors during rebuild. S7 context menus and embed refreshes run after these owners finish.
- `UIReplaysEditorUtils` already has adjacent/hierarchy offers, Film gizmo paths, sound sheets and pose selection helpers. BonePriority is a conditional override, not a replacement.
- `UIFilmPreview` has substantially more controls and export/session ownership than the source baseline. S7 inserts one icon and scroll behavior only when the BonePriority menu owns the event.
- Current FSR resolves IK target/pole anchors in world space and applies them in model-local space. S7 therefore samples all Film replays at the export tick, includes target/pole timing keys, applies the target model's semantic base transform (including its Y-axis flip), and passes only model-local values to `ModelIKRuntime`.

### Source contains mixed mapping/API assumptions

- `UIKeyframeDopeSheetMixin` imports Yarn `net.minecraft.client.render.BufferBuilder`; target descriptor uses Mojmap `com.mojang.blaze3d.vertex.BufferBuilder`.
- Resource links and texture namespace still use `bbs_fslovecml`; all become `bbs_snow`.
- Some source members already use `bbs_snow$` while most use `bbs_fslovecml$`; the port normalizes every injected member.

## 4. Contracts to preserve

- `defaultRequire: 1`, no refmap, exact target descriptors, no ordinary helper under the Mixin package.
- Addon UI must not replace current FSR factory/registry entries or swallow fallback behavior.
- Animation JSON mutation preserves unknown root/animation fields and unrelated animations.
- Mini-window inspector refresh delegates to S6 host APIs; S7 does not manipulate window internals.
- Missing target sheet/model/form or unwritable export destination produces a safe fallback or explicit error, not stale selection or partial publication.
- Real client execution is required because compilation cannot validate BBS-targeted Mixin anchors.
