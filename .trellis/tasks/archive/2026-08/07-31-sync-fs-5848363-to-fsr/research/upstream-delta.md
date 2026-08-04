# Upstream delta report and disposition plan

## Exact boundaries

| Line | Previous | Current | Role |
|---|---|---|---|
| FS master | `116ed397` | `5848363c` | behavior source |
| FS 1.20.1 | `c3d1a477` | `813be205` | requested 1.20.1 reference |
| FS 1.21.1 | `31bd255e` | `5ed64583` | Minecraft 1.21.1 API reference |
| FSR | `4143499b` migrated through | `0544eada` task start | NeoForge target |

The 1.20.1 range changes 186 files (`+18653/-8319`) across 153 commits. The
master range contains 154 commits: 49 features, 52 fixes, 19 refactors, 18 UI
changes, 9 test/debug changes, 6 merges and 1 cleanup.

## Already equivalent or stronger in FSR

- Fractional GUI scale and shortcuts.
- Tick-stepped interpolation.
- `FormTranslucentQueue` and invalid-property-channel continuation.
- Model-editor creation flow, bevel removal, pose recording and framebuffer restore.
- Multi-selection traversal protection.
- Replay track and IK/physics/constraints panel bone ownership consistency from
  `0544eada`.

## Applicable standalone fixes

- `2774a26a`: clear secondary preview axis state during film context reset.
- `46693a61`: place newly introduced `ValueOrder` tokens at their expected position.
- `d6a8c847`: replay complete ground probes and sprint state for vanilla step and
  sprint particles.
- `c4b7e6f2`: retain expanded section state across form-editor rebuilds; keep the
  existing FSR equivalent for selection ownership.

## Applicable missing feature groups

- Quaternion editing, transform spaces and split drag strategies.
- DLS IK tree, multiple targets, per-bone degrees of freedom and classic two-bone mode.
- Searchable tree bone picker and stencil eyedropper.
- Orbit navigation sphere, orthographic preview and tracker flight/orbit authoring.
- Freeze the final film frame after leaving the editor.
- Weld parent share, twist, limits and diagnostics.
- Keyframe hover content preview.
- Single Dock implementation and whole-editor edge docking.
- `FormFrameCache` integrated behind FSR rendering ownership.

## Exclusions

- The shader option parser fix is excluded because upstream later reverted it.
- The inactive-icon brightness change is excluded because FSR theme rules own it.
- FS particle-engine replacement is excluded; only replay state required for vanilla
  footsteps/sprint particles is applicable.
- Transparency rollback/replacement is excluded; FSR keeps its stronger queue.

## Disposition legend

- Status `M`: migrated into the NeoForge target.
- Status `E`: target was already equivalent/stronger, the commit is merge-only, or later
  upstream history superseded the intermediate behavior.
- Status `X`: deliberately excluded or not applicable to the target boundary.
- Evidence `T`: transform/quaternion/gizmo implementation and migration tests.
- Evidence `C`: evaluated-channel/constraint implementation and IK consistency tests.
- Evidence `I`: DLS/classic IK implementation, configuration, UI, and IK consistency tests.
- Evidence `R`: FSR render queue, VAO/immediate renderers, frame cache, and render source tests.
- Evidence `F`: film/camera/replay implementation and film/replay tests.
- Evidence `U`: editor/pose/slider/settings implementation and film/UI source tests.
- Evidence `W`: weld data/runtime/diagnostics implementation.
- Evidence `B`: searchable bone tree/picker/stencil implementation.
- Evidence `D`: shared `UIDockLayout` and root-edge insertion implementation.
- Evidence `P`: pre-existing equivalent or stronger FSR behavior at `0544eada`.
- Evidence `S`: intermediate upstream behavior later superseded within this same range.
- Evidence `G`: merge bookkeeping with no additional terminal behavior to port.
- Evidence `N`: repository-only change not applicable to the target.
- Evidence `X`: approved product-boundary exclusion.

## Per-commit disposition (`116ed397..5848363c`)

| # | Commit | Status | Evidence |
|---:|---|:---:|:---:|
| 1 | `e5eb8eff` | M | T |
| 2 | `acd8e797` | M | T |
| 3 | `80b8e1a2` | M | T |
| 4 | `16567de3` | M | T |
| 5 | `fc1f6ccc` | M | T |
| 6 | `ca779207` | E | S |
| 7 | `9d62bc35` | M | U |
| 8 | `10451a18` | E | S |
| 9 | `1233fc86` | E | S |
| 10 | `cdb6c6de` | E | S |
| 11 | `36cde7a0` | M | T |
| 12 | `592fd34e` | M | T |
| 13 | `f7dfedf9` | M | T |
| 14 | `716bfc86` | M | T |
| 15 | `afa31b2c` | E | S |
| 16 | `b66a9a6d` | M | T |
| 17 | `670d7565` | M | T |
| 18 | `bc4787dd` | M | T |
| 19 | `33ef93b3` | M | T |
| 20 | `603ead0e` | M | T |
| 21 | `fe991729` | M | T |
| 22 | `9c28f94d` | M | T |
| 23 | `f2a4eb59` | M | T |
| 24 | `dd54e927` | M | T |
| 25 | `fb959029` | X | N |
| 26 | `5672d4ac` | M | T |
| 27 | `d37f5d61` | M | T |
| 28 | `e777ca69` | M | T |
| 29 | `ce089861` | M | T |
| 30 | `1ecded0d` | M | T |
| 31 | `b7b1ec40` | M | T |
| 32 | `85cd13ed` | M | T |
| 33 | `5eff5c65` | M | T |
| 34 | `607db6ca` | M | T |
| 35 | `01c41c37` | M | T |
| 36 | `7fa1d8a8` | M | T |
| 37 | `6b565206` | M | C |
| 38 | `bce39354` | M | C |
| 39 | `6b90af85` | M | C |
| 40 | `4c02944b` | M | C |
| 41 | `381db60e` | E | S |
| 42 | `60595526` | M | T |
| 43 | `b0adc941` | M | T |
| 44 | `4650902f` | M | T |
| 45 | `4198e3e7` | M | T |
| 46 | `8894d2fb` | M | T |
| 47 | `adda2160` | M | T |
| 48 | `0069ae2a` | E | G |
| 49 | `0f405a66` | M | T |
| 50 | `11c43e02` | M | T |
| 51 | `7defa513` | M | T |
| 52 | `e09742d4` | M | T |
| 53 | `c8766867` | M | T |
| 54 | `7877f381` | M | C |
| 55 | `c4524f2d` | M | I |
| 56 | `4877579b` | M | T |
| 57 | `56d8236a` | M | T |
| 58 | `3a635fee` | M | T |
| 59 | `9dda2852` | M | T |
| 60 | `089e06cb` | M | T |
| 61 | `0881163b` | M | U |
| 62 | `c71cda9c` | M | U |
| 63 | `4e33c810` | M | T |
| 64 | `e62e4091` | M | T |
| 65 | `acd2f367` | M | T |
| 66 | `46693a61` | M | U |
| 67 | `51f0b908` | M | T |
| 68 | `10156e24` | M | U |
| 69 | `3e45206c` | M | U |
| 70 | `df6131bb` | M | U |
| 71 | `d1e51fb3` | M | U |
| 72 | `d0e722df` | E | G |
| 73 | `c73a4d8f` | M | T |
| 74 | `16ed9e3d` | M | T |
| 75 | `3959188f` | M | T |
| 76 | `913f10df` | E | S |
| 77 | `1ed86edf` | M | I |
| 78 | `93f856b5` | M | I |
| 79 | `68389614` | M | I |
| 80 | `f5cab182` | M | I |
| 81 | `db76eabb` | M | I |
| 82 | `904284f3` | M | I |
| 83 | `09a9ac1c` | M | I |
| 84 | `dc87eae4` | M | I |
| 85 | `8700f0e6` | M | I |
| 86 | `3310ec02` | M | I |
| 87 | `78ef06dd` | M | I |
| 88 | `eee3b10b` | M | I |
| 89 | `92a5cfa0` | M | I |
| 90 | `60b11e4e` | M | I |
| 91 | `d407e6fb` | M | I |
| 92 | `3f4d87f2` | M | I |
| 93 | `95c29b7e` | M | I |
| 94 | `a73df786` | M | I |
| 95 | `2ed753f0` | M | I |
| 96 | `8a53fe71` | M | I |
| 97 | `008dd3c0` | M | I |
| 98 | `ab3fb3b4` | M | I |
| 99 | `5d87cf7d` | M | I |
| 100 | `b7bd5f22` | M | R |
| 101 | `2015333f` | E | G |
| 102 | `f76cce04` | E | P |
| 103 | `252a3f3f` | M | I |
| 104 | `31792371` | M | I |
| 105 | `c3cc6722` | M | I |
| 106 | `dc65e87f` | M | R |
| 107 | `ca6ca463` | E | P |
| 108 | `c0824918` | E | P |
| 109 | `17b08070` | E | P |
| 110 | `e56ad6fb` | E | P |
| 111 | `c0c10a08` | E | P |
| 112 | `9884eae4` | E | G |
| 113 | `42cb4d5e` | M | I |
| 114 | `2dd85c4c` | X | S |
| 115 | `38ed5d1e` | M | I |
| 116 | `20149149` | M | R |
| 117 | `4b37abf3` | E | G |
| 118 | `cbd16c14` | M | T |
| 119 | `5aef4df8` | M | F |
| 120 | `1c809ebe` | M | F |
| 121 | `47ef44e1` | E | G |
| 122 | `c0e997bb` | M | W |
| 123 | `e5763ce9` | M | W |
| 124 | `1ee14e3b` | X | X |
| 125 | `07db2337` | M | F |
| 126 | `0e7de511` | M | F |
| 127 | `cc7d423a` | M | F |
| 128 | `9496cbc3` | M | U |
| 129 | `a85ce7a2` | M | U |
| 130 | `7a4c5a04` | M | U |
| 131 | `86cb04cb` | M | U |
| 132 | `875b4b96` | M | U |
| 133 | `e13dd08a` | M | U |
| 134 | `b4ecd8de` | X | S |
| 135 | `a7b7e672` | M | F |
| 136 | `f3e11331` | M | I |
| 137 | `836a801f` | M | I |
| 138 | `bf2c0239` | M | I |
| 139 | `afef412c` | M | I |
| 140 | `0e40c8e1` | M | I |
| 141 | `6530acbb` | M | I |
| 142 | `eb503a7f` | M | I |
| 143 | `eed2b45e` | M | I |
| 144 | `952e4da7` | M | I |
| 145 | `c4b7e6f2` | M | U |
| 146 | `f0478d46` | M | B |
| 147 | `21737add` | M | B |
| 148 | `e9b8d160` | M | B |
| 149 | `b26114dd` | M | F |
| 150 | `2774a26a` | M | F |
| 151 | `58c07747` | M | F |
| 152 | `d6a8c847` | M | F |
| 153 | `8adefc9c` | M | D |
| 154 | `5848363c` | M | D |

Count: 154 commits; 131 migrated, 19 equivalent/superseded/merge-only, and
4 excluded/not applicable. No commit is blocked or left without a disposition.

## Fixed boundaries

- `fsr_initial_commit`: `0544eadacbd0d2d60fd27d2a317e9e73483fa468`
- `fsr_terminal_commit`: `94d2349f43b8d71dcf162e30f5f4294e3d9cc2e3`
- FS master source: `5848363c3e7c63ef124bad0c3b08809abd311f91`
- FS 1.20.1 source: `813be205e83b571823d58736b9213f8309811186`
- FS 1.21.1 source: `5ed645834b46f7fd8c81e6c40dbc3e453949ac0b`

The terminal commit is a direct child of the recorded initial commit. It contains
171 files (`+19466/-7147`): 139 modified and 32 added. No migration file remains
unstaged or uncommitted in the dedicated worktree.

## Automated verification

- Passed: `testUiInputMailbox`, `testFilmEditorMigration`,
  `testIkPhysicsConsistency`, `testReplayKeyframeConsistency`, and
  `testAudioVerification`.
- Passed: `compileJava`, `compileClientJava`, full `check`, full `build`, and
  `git diff --check`.
- Audio hermetic contracts passed. Strict media probing was skipped because this
  machine has no `ffmpeg` or `ffprobe`.
- Built artifacts remain `BBS_FSR_beta_v0.0.11.jar` and
  `BBS_FSR_beta_v0.0.11-0.0.11-sources.jar`.
- Minecraft `1.21.1`, NeoForge `21.1.222`, `mod_version=0.0.11`, and
  `archives_base_name=BBS_FSR_beta_v0.0.11` are unchanged.
- Particle paths and `FormTranslucentQueue.java` are unchanged from the initial
  commit. All 77 reviewed `.rej` files are absent.

## Manual runtime checks still required

- In-game transform-space, quaternion, multi-select, bone picker, orbit/ortho, tracker,
  freeze-frame, Dock, IK/classic/stretch, and weld interaction checks.
- Shader-off plus Iris/Photon visual checks for model, extruded, billboard, label,
  framebuffer, armor, item, glint, and translucent sort behavior.
- These checks remain explicitly pending; build success is not reported as visual proof.
