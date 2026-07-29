# S8 source/addon overlap decision

## Decision

Use `bbs_FSloveCML_1.21.1/src/` as the only behavior source. Keep `addon-project/` as historical comparison evidence; do not merge or port it.

## Evidence

| Capability | Main source tree | `addon-project/` | S8 decision |
|---|---|---|---|
| Hotbar data | 25 channels, clamps, item copies, legacy layout/hardcore migration | Same/older data shape | Port main source |
| Hotbar HUD | Complete renderer and BBSRendering consumption chain | Missing the complete current chain | Port/adapt main source to 1.21.1 sprites |
| Cinematic | 8 channels and current UI generated from `channels[]` | Older duplicated UI | Port main source |
| Color | Data implementation exists but registration/UI are incomplete | No superior implementation | Finish main-source behavior required by parent R3.6 |
| Video | Absent after upstream deletion | `applyClip()` is empty | Exclude |

Git history records removal of Video in source commit `e02659f`; an empty parameter container is not a runtime capability.

## Target drift

- Current FSR has no Hotbar/Cinematic/Color clip factory to reuse.
- S3 already owns `ColorGradeRenderer`, `ScreenEffectRenderer`, context discovery and the screen-effect consumption injection. S8 publishes into this contract.
- S3 `ColorEffect` and `ScreenEffectQueue` are pure data/queue classes but currently live under the client source set. They must move to a common-safe owner before common clips can reference them.
- BBS `UIClip.createPanel()` returns no inspector when a UI factory is absent. Parent R3.6 therefore requires a new Color inspector; registering only the data factory is insufficient.
- Source Hotbar rendering uses 1.20.1 texture atlases. Target 1.21.1 uses HUD sprites and changed draw APIs, so this is a semantic port rather than a mapping-only rename.

## Source inventory

Common candidates:

- `clips/HotbarClip.java`
- `clips/HotbarState.java`
- `clips/screen/ColorClip.java`
- `clips/screen/CinematicClip.java`
- `clips/screen/ColorEffect.java` (deduplicate with S3 owner)

Client candidates:

- `ui/film/UIHotbarRenderer.java`
- `ui/film/clips/UIHotbarClip.java`
- `ui/film/clips/UICinematicClip.java`
- `ui/film/clips/ClipTrackTitles.java`
- Clips accessors/invoker and the Hotbar branch of source `BBSRenderingMixin`

Target-only addition:

- `UIColorClip`, because Color has no complete source inspector but is explicitly required by parent R3.6.

## Exclusions

- `addon-project/` source and build files
- `VideoClip`
- duplicate shader/render queue/context discovery
- S9 fluid and Premiere paths
