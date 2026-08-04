# Rejected patch disposition

Boundary: FS `116ed397..5848363c` onto FSR `0544eada` in the dedicated
`codex/sync-fs-5848363-to-fsr` worktree. The 77 `.rej` files were migration
scratch artifacts, not accepted leftovers.

Status codes:

- `M`: migrated in the corresponding target source.
- `N`: migrated through the NeoForge/FSR integration owner instead of the rejected hunk shape.
- `S`: the target keeps an equivalent or stronger FSR implementation.
- `X`: intentionally excluded by the approved migration boundary.

| # | Rejected file | Status | Evidence |
|---:|---|:---:|---|
| 1 | `camera/clips/misc/TrackerClientClip.java` | M | `TrackerFrame`, flight/orbit authoring, and tracker UI wiring are present. |
| 2 | `client/BBSRendering.java` | M | Orthographic state and render-stage integration are present. |
| 3 | `client/renderer/ModelBlockEntityRenderer.java` | M | Migrated transform/render call is present. |
| 4 | `cubic/ModelInstance.java` | S | Quaternion capture and split/whole translucent VAO/immediate paths are present with FSR glint ownership. |
| 5 | `cubic/constraints/ModelConstraintsRuntime.java` | M | Evaluated-orientation constraint path is present. |
| 6 | `cubic/ik/ModelIKApplier.java` | S | Final upstream implementation lives in `ModelIKDlsApplier`; target adds FSR workspace/constraint ownership while retaining the stable simple-chain applier. |
| 7 | `cubic/ik/ModelIKCache.java` | S | Classic flag, joint DoF, controller sets, cycle guards, work chains, and cached metadata are present. |
| 8 | `cubic/ik/ModelIKRuntime.java` | S | DLS routing, classic solver, stretch, tip rotation, multi-chain selection, and FSR simple-chain routing are present. |
| 9 | `cubic/model/bobj/BOBJModel.java` | M | Evaluated quaternion/orientation and matrix capture are present. |
| 10 | `cubic/render/CubicCubeRenderer.java` | S | Multi-seam tessellation implementation is present; rejected difference is stale commentary/import context. |
| 11 | `cubic/render/CubicRenderer.java` | M | Pivot frames include accumulated scale. |
| 12 | `cubic/render/CubicVAORenderer.java` | S | Split/whole translucency plus deferred glint ordering is present. |
| 13 | `cubic/render/ICubicRenderer.java` | M | Parent-frame stretch offset and quaternion/orient rendering are present. |
| 14 | `cubic/render/ModelPivotFrames.java` | M | BOBJ pivot scale is captured into the four-field `PivotFrame`. |
| 15 | `cubic/render/ModelRotationBlender.java` | S | Renderer-local workspaces, final quaternion constraints, and read-only FK channels strengthen the upstream orientation path. |
| 16 | `cubic/render/vao/BOBJModelSimpleVAO.java` | M | Migrated VAO matrix/render overloads are present. |
| 17 | `cubic/render/vao/BOBJModelVAO.java` | S | Armature snapshot/upload generation and deferred draw support are present. |
| 18 | `cubic/render/vao/ModelVAORenderer.java` | M | Explicit model-view/normal-matrix overloads are present. |
| 19 | `film/BaseFilmController.java` | S | Shared `FormFrameCache`, gizmo/preview/anchor paths, transform spaces, and replay floor/sprint state are present with FSR semantic-world keys. |
| 20 | `film/FilmControllerContext.java` | M | Secondary preview axes reset and anchor gizmo state are present. |
| 21 | `forms/CustomVertexConsumerProvider.java` | N | NeoForge `BufferSource` plus `RenderLayerMixin` captures deferred `MeshData` after render-state setup; layer preparation is retained. |
| 22 | `forms/FormUtilsClient.java` | N | NeoForge 1.21.1 `ByteBufferBuilder`/sequenced fixed-buffer constructor is used. |
| 23 | `forms/renderers/BillboardFormRenderer.java` | S | Whole-quad sorted translucency includes texture alpha, filtering, and FSR glint. |
| 24 | `forms/renderers/BlockFormRenderer.java` | M | Sort origin, `endBatch`, and captured glint are present. |
| 25 | `forms/renderers/ExtrudedFormRenderer.java` | S | Split/whole queue, Iris immediate/cutout fallback, and FSR glint are present. |
| 26 | `forms/renderers/FramebufferFormRenderer.java` | S | Nested queue suspension, deferred output quad, and full borrowed-state restoration are present. |
| 27 | `forms/renderers/ItemFormRenderer.java` | M | Sort origin, `endBatch`, and captured glint are present. |
| 28 | `forms/renderers/LabelFormRenderer.java` | S | Text and background share one exception-safe translucent group. |
| 29 | `forms/renderers/MobFormRenderer.java` | S | Buffered layer sort origin and FSR model/picking state restoration are present. |
| 30 | `forms/renderers/ModelFormRenderer.java` | S | Iris fallback, armor/item sort origins, pose snapshot, semantic cache keys, IK/physics ownership, and evaluated rotations are present. |
| 31 | `forms/renderers/utils/MatrixCache.java` | S | Overloads preserve both FSR `rotationOffset` and migrated `evaluatedRotation`. |
| 32 | `forms/renderers/utils/MatrixCacheEntry.java` | S | Four-field record preserves both FSR and migrated rotation metadata. |
| 33 | `graphics/texture/Texture.java` | M | Texture translucency metadata is present. |
| 34 | `graphics/texture/TextureManager.java` | M | Texture translucency detection/loading path is present. |
| 35 | `mixin/client/GameRendererMixin.java` | S | Queue end-of-world fallback is present alongside FSR render ownership. |
| 36 | `mixin/client/WorldRendererMixin.java` | N | Queue starts lazily from world forms and flushes before translucent terrain; Iris shadow pass is guarded. |
| 37 | `ui/UIKeys.java` | M | Migrated transform, IK, weld, orbit, freeze, and bone-picker keys are present. |
| 38 | `ui/dashboard/textures/layers/UILayersPanel.java` | M | Slider control migration is present. |
| 39 | `ui/film/UIFilmPanel.java` | S | Dock ownership is centralized in `UIDockLayout`; film layouts, scroll memory, export queue, preview sizing, and freeze-frame lifecycle remain present. |
| 40 | `ui/film/UIFilmPreview.java` | M | Orbit gizmo click ownership and cursor suppression are present. |
| 41 | `ui/film/clips/UIDragClip.java` | M | Slider migration is present. |
| 42 | `ui/film/clips/UITrackerClip.java` | M | Flight/orbit tracker authoring UI is present. |
| 43 | `ui/film/controller/FilmEditorController.java` | M | Orbit gizmo/controller integration is present. |
| 44 | `ui/film/controller/OrbitFilmCameraController.java` | S | Ortho ray origin, auto-ortho, and strict `FormFrameCache` anchor reuse are present. |
| 45 | `ui/film/controller/UIFilmController.java` | M | Transform-space and orbit-gizmo integration is present. |
| 46 | `ui/film/replays/UIReplayList.java` | M | Bone-picker selection wiring is present. |
| 47 | `ui/film/replays/UIReplaysEditor.java` | S | Picker wiring preserves FSR replay-track/bone ownership. |
| 48 | `ui/film/replays/UIReplaysEditorUtils.java` | S | Recorded ground/sprint keyframes preserve FSR action ordering. |
| 49 | `ui/forms/editors/UIBodyPartEditor.java` | M | Bone tree/picker and transform-space wiring are present. |
| 50 | `ui/forms/editors/UIFormEditor.java` | S | Searchable picker, stencil selection, scene axes, and FSR selection ownership are present. |
| 51 | `ui/forms/editors/forms/UIForm.java` | M | Transform-space and evaluated-rotation hooks are present. |
| 52 | `ui/forms/editors/forms/UIModelForm.java` | S | IK-constrained rotation guard, gizmo space, and additive evaluated-rotation base are present. |
| 53 | `ui/forms/editors/panels/UIFormPanel.java` | M | Section-state persistence is present. |
| 54 | `ui/forms/editors/panels/UIGeneralFormPanel.java` | M | Slider migration is present. |
| 55 | `ui/forms/editors/panels/UIModelFormPanel.java` | M | Model pose panel integration is present. |
| 56 | `ui/forms/editors/panels/UIModelIKFormPanel.java` | S | Classic/DLS, DoF, cycle validation, preview, picker, and FSR ownership are present. |
| 57 | `ui/forms/editors/panels/UIModelPhysicsFormPanel.java` | M | Slider and bone-picker migration is present. |
| 58 | `ui/forms/editors/states/keyframes/UIAnimationStateEditor.java` | M | Scene/global axes and shared placement convention are present. |
| 59 | `ui/forms/editors/utils/UIDebugOverlayContextMenu.java` | M | Slider migration is present. |
| 60 | `ui/forms/editors/utils/UIPickableFormRenderer.java` | S | Visual/pick gizmos share transform-space reorientation and FSR queue remains suspended for previews. |
| 61 | `ui/framework/elements/buttons/UIIcons.java` | X | Inactive-icon always-bright behavior is intentionally excluded; FSR theme disabled/hover colors remain authoritative. |
| 62 | `ui/framework/elements/input/UIPropTransform.java` | M | Split drag strategies, numeric input, spaces, quaternion mode, and IK logging scope are present. |
| 63 | `ui/framework/elements/input/UISliderTrackpad.java` | M | Shared trackpad slider implementation is present. |
| 64 | `ui/framework/elements/input/UITrackpad.java` | M | Shared base behavior and drag lifecycle are present. |
| 65 | `ui/framework/elements/input/keyframes/UIKeyframeEditor.java` | M | Hover preview tooltip and transform-space controls are present. |
| 66 | `ui/framework/elements/input/keyframes/factories/UIAnchorKeyframeFactory.java` | M | Final upstream file has no rejected added behavior; target anchor transform editor remains present. |
| 67 | `ui/framework/elements/input/keyframes/factories/UIPoseTransformKeyframeFactory.java` | M | Slider migration is present. |
| 68 | `ui/framework/elements/layout/UIDockLayout.java` | S | Single shared implementation owns tabs, splitters, animation, locking, root-edge drop, fixed pixel edge bands, and `DROP_ROOT_RATIO`. |
| 69 | `ui/model_blocks/UIModelBlockEditorMenu.java` | M | Slider migration is present. |
| 70 | `ui/model_blocks/UIModelBlockPanel.java` | S | Global/view/parent reorientation is present with FSR viewport and exception-safe stack ownership. |
| 71 | `ui/particles/UIParticleSchemePanel.java` | X | FS particle panel replacement is intentionally excluded; FSR particle editor and its dock/layout ownership remain authoritative. |
| 72 | `ui/utils/Gizmo.java` | M | Transform-space render/pick capture is present. |
| 73 | `ui/utils/GizmoDrag.java` | M | Global/view/parent axes, quaternion sampling, and drag bases are present. |
| 74 | `ui/utils/pose/UIBoneList.java` | M | Bone hierarchy/picker integration is present. |
| 75 | `ui/utils/pose/UIPoseBoneStringList.java` | M | Search/tree selection integration is present. |
| 76 | `ui/utils/pose/UIPoseEditor.java` | S | Quaternion/space/slider changes preserve FSR multi-selection and ownership behavior. |
| 77 | `utils/MatrixStackUtils.java` | M | Scale stripping and transform helpers used by migrated gizmos are present. |

## Result

- Migrated/equivalent/stronger: 75 files.
- Intentionally excluded: 2 files (`UIIcons`, FS particle panel replacement).
- Unresolved or blocked: 0 files.
- All 77 `.rej` artifacts may be deleted after this disposition is recorded.
