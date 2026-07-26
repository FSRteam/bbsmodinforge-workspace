# S4 source inventory and target drift

## Ownership matrix

| Source artifact | 1.21.1 target/finding | S4 decision |
|---|---|---|
| `cubic/animation/AdditiveAnimator` | `Animator` fields, `setup`, `createAction`, and `applyActions` remain available | Port and rename namespace members |
| `cubic/animation/AdditiveProceduralAnimator` | `ProceduralAnimator` contract remains available | Port and rename namespace members |
| `utils/MolangVariableScopes` | `MolangParser.variables` remains public; S2 already supplies `MolangSharedProvider` | Port as non-Mixin helper |
| `utils/CubicPivotTransformations` | Yarn `MatrixStack` became Mojmap `PoseStack`; `peek().getPositionMatrix()` became `last().pose()` | Port helper outside Mixin package |
| `AnimatorMixin` | `applyActions(IEntity,IModelInstance,float)` unchanged | Strict HEAD/RETURN scope hooks |
| `ProceduralAnimatorMixin` | Same descriptor; early null-target return remains | Strict HEAD/RETURN hooks; RETURN covers early exit |
| `CubicModelAnimatorMixin` | `applyGroupAnimation` still calls `Lerps.lerpYaw(DDD)D` three times | Keep redirect; full blend returns destination |
| `GeoAnimationParserMixin` | private static `parseChannel(...,JsonElement)` still exists | Keep TAIL parser extension |
| `CubicCubeRendererMixin` | class still inherits `ICubicRenderer.applyGroupTransformations`; FSR added `offsetGroup` to the default sequence | Add concrete override and preserve `offsetGroup` first |
| `CubicAxisRendererMixin` | class still inherits the same default sequence | Same as cube renderer |
| `CubicMatrixRendererMixin` | concrete override and public `origins` list remain; current sequence deliberately omits `offsetGroup` | Overwrite exact FSR body and insert only the pivot pair |
| source `CubicVAORendererMixin` | every behavior in it is already in S3 `CubicVAORendererPBRMixin` | Do not port or register a duplicate S4 Mixin |
| source `ModelFormRendererMixin` | PBR part is already S3; `applyPose`, `tick`, and `resetAnimator` remain; action sites changed from four to three | Split a PBR-free S4 Mixin and retarget exact current descriptors |

## ModelFormRenderer descriptor drift

The source redirect named `renderInUI`, `render3D`, `collectMatrices`, and `sampleBoneOrigin` and
required four `IAnimator.applyActions` calls. The current FSR implementation contains exactly three:

1. private `renderInUI(UIContext,int,int,int,int,Object,IEntity)`;
2. `render3D(FormRenderingContext)`;
3. protected `collectMatricesWithAppliedStates(IEntity,Object,Matrix4f,boolean,boolean,PoseStack,MatrixCache,String,float)`.

The public `collectMatrices` now delegates and has no action call. `sampleBoneOrigin` no longer owns an
action call. S4 must target the exact descriptors above and require three matches. Weakening the
injector or retaining the stale method names would either fail startup or silently miss matrix paths.

## Pivot sequencing drift

FSR's default renderer sequence is:

```text
offset -> translate -> model pivot -> rotate -> scale -> undo model pivot
```

S4 inserts the pose pivot only around rotate/scale:

```text
offset -> translate -> model pivot -> +pose pivot -> rotate -> scale
       -> -pose pivot -> undo model pivot
```

The matrix renderer has its own target-specific sequence and origin capture:

```text
translate -> capture origin -> model pivot -> +pose pivot -> rotate -> scale
          -> -pose pivot -> undo model pivot
```

Adding FSR's render-only `offset` to the matrix collector would alter IK/attachment semantics, so S4
preserves the target implementation rather than normalizing all three Mixins to one copied body.

## Scope boundary

- S3 remains the only owner of `CubicVAORenderer` group PBR/grade/texture state and
  `ModelFormRenderer.render3D` PBR context.
- S4 owns runtime animation composition, Molang isolation, Geo parsing, angle preservation, and
  Cubic pose-pivot matrices.
- S7 owns controls and replay/editor surfaces that create or edit the action/pose data.

## Verification implications

- Compilation validates Java/Yarn-to-Mojmap translation but not injector matching.
- The runtime verifier should instantiate transformed target classes and check matrix/parser/angle/
  scope behavior before its automatic clean exit.
- The complete client log must show all eight new target classes transformed and the final S4 pass.
- Strict core-jar freshness remains a prerequisite because these injection descriptors bind to local
  BBS FSR implementation details.
