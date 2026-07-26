# S2 source inventory and compatibility audit

## Source baseline

- Source repository: `bbs_FSloveCML_1.21.1/`
- Target addon: `bbs-snow-addon/`
- BBS target: `new/` (NeoForge 1.21.1, BBS FSR 0.0.10)
- S1 archive: `.trellis/tasks/archive/2026-07/07-26-bbs-snow-s1-skeleton-mixin/`

## File inventory

### Common API (10)

`ActionsOverlayProvider`, `ActionTimelineConfig`, `BoneTextureHolder`,
`GroupTextureGradeHolder`, `GroupTextureHolder`, `MolangSharedProvider`,
`PBRModelFormAccess`, `PivotHolder`, `TextureGradeHolder`, `TextureGradeProvider`.

### Client API (13)

`ActionsTimelineEditorContext`, `BoneCollapseHandler`, `FilmsControllerAccessor`,
`FloatLayerAccess`, `ParticlePlusParticle`, `PickTextureButtonHolder`,
`PoseTabStateProvider`, `PoseTextureGradeEditorHolder`, `TransformPivotEditor`,
`UIClipsAccessor`, `UIClipsPanelAccessor`, `UIDockLayoutAccessor`,
`UIFilmPanelLayoutAccessor`.

### Common Mixin (12)

`ActionConfigMixin`, `ActionPlaybackMixin`, `ActionsConfigKeyframeFactoryMixin`,
`BBSSettingsMixin`, `DataManagerAccessor`, `FormPropertiesPBRMixin`,
`KeyframeSegmentMixin`, `ModelFormMixin`, `ModelGroupMixin`, `ModelMixin`,
`PoseTransformMixin`, `TransformMixin`.

### Client accessor Mixin (5)

`FilmsAccessor`, `UIClipsInvoker`, `UIClipsPanelAccess`, `UIDockLayoutAccess`,
`UIFilmPanelLayoutAccess`.

### Required supporting classes

- PBR common data/factories: `PBRData`, `BonePBRData`, `PBRChannel`,
  `PBRKeyframeFactory`, `BonePBRKeyframeFactory`.
- Settings: `CMLSettings`, `ValueSectionHeader`.
- Common utilities: `ActionTimelineEvaluator`, `PresetDataOperations`.

## Target audit

The 17 target classes exist in `new/`. The following members were checked in the current
source tree:

- `ActionConfig`: `toData`, `fromData`, `copy`, `equals`, `isDefault`.
- `ActionPlayback`: `getTick(float)`.
- `ActionsConfigKeyframeFactory`: six-argument `interpolate`.
- `BBSSettings`: static `register(SettingsBuilder)`.
- `DataManager`: private `data`, protected abstract `getFile(String)`.
- `FormProperties`: `getOrCreate`, private `applyProperty`.
- `KeyframeSegment`: `createInterpolated`.
- `ModelForm`: zero-argument constructor and the `actions` add call used by the ordinal
  injection point.
- `ModelGroup.reset`, `Model.applyPose(Pose)`.
- `Transform` / `PoseTransform`: source injection targets remain except `autoLerp`, which is
  absent from both FSR and the `old/` baseline. Those two injections must be removed.
- Accessor targets: `Films.controllers`; `UIClips.factory/addClip`; `UIClipsPanel.panel/target`;
  `UIDockLayout.panelById/iconById/layoutRoot/setLayoutRoot`;
  `UIFilmPanel.panelById/getCurrentFilmLayoutRoot/setCurrentFilmLayoutRoot/setupEditorFlex`.

## Settings discrepancy resolved by evidence

The current source's `CMLSettings.appendToMain` calls `builder.category("bbs_snow")` once,
registers 20 `builder.get*` values, and adds two `ValueSectionHeader` instances. Earlier commits
used several `cml_*` categories, but the migration target is current source behavior. The parent
task's prior "8 categories" statement was not supported by the source and was corrected during
S2 planning.

## S1 constraints carried forward

- External addon registration uses `BBSMod.registerAddon` during mod construction; an external
  mod cannot receive BBS's internal `BBSAddonRegisterEvent`.
- Common and client addon objects need distinct ids because collection de-duplicates by id.
- Mixin packages contain Mixins only; helpers outside them.
- NeoGradle build success does not validate injection targets. `runClient` is mandatory.
- All configs keep `defaultRequire: 1`, `JAVA_21`, and no refmap.
