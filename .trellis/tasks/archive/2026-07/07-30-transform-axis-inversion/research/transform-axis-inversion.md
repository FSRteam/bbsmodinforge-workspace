# Research: 3D Preview G/S/R Transform Y/Z Axis Inversion

- **Query**: Analyze Y/Z axis inversion during G/S/R hotkey transforms in the 3D preview
- **Scope**: Internal (code analysis of UIPropTransform.java, GizmoDrag.java, Gizmo.java, related callers)
- **Date**: 2026-07-30

## Findings

### 1. Complete Code Flow: Hotkey G/S/R + Mouse Drag

The critical understanding is that there are **three separate code paths** for applying transforms during a hotkey drag, and only one shows the Y/Z inversion in the naive way described.

#### Path 1: Ray Drag (DEFAULT, `transformHotkeys3dRay=true`)

This is the default path because `BBSSettings.transformHotkeys3dRay` defaults to `true` (line 744 in `BBSSettings.java`).

**Entry**: `render()` line 1661-1670 (via `useRayDrag()` check at line 1661)

```
render() -> useRayDrag() == true -> applyRayDrag(mouseX, mouseY)
```

**`useRayDrag()` conditions** (line 1882-1898):
- Hotkey mode disabled AND `transformHotkeys3dRay=false` -> false
- `scaleAll` -> false
- Otherwise true when: editing, has transform, has drag, (not rotate with plane or is sphere rotate)

**Translate (mode 0) ray drag flow**:
1. `beginRayTranslate()` (line 1969) sets up:
   - `dragWorldBasis` = gizmoWorldAxes (world-space axes of the gizmo)
   - `dragTranslateBasis` = inverse(jacobian) * gizmoWorldAxes
   - `dragPlaneNormal` = plane containing the axis direction, perpendicular to view direction
2. `applyRayTranslate(Vector3d hit)` (line 2061):
   - `delta = hit - dragStartHit` (3D world-space delta from ray-plane intersection)
   - `accumulateAlongAxis(delta, axis, result)` (line 2091):
     - Projects world delta onto world axis: `t = worldAxis.dot(delta) / lenSq`
     - Converts to transform space: `result += translateAxis * t`
   - New value = start + result

**Scale (mode 1) ray drag flow**:
1. `beginRayScale()` (line 2107): Similar plane setup using `gizmoWorldAxes`
2. `applyRayScale(Vector3d hit)` (line 2125):
   - `applyRayScaleAxis()` projects hit onto axis direction and computes ratio of current/start projection
   - Multiplicative scaling: `scale *= ratio`

**Rotate (mode 2) ray drag flow with single axis**:
1. `beginRayRotate()` (line 2182): Computes screen-space angle and axis/view sign
2. `applyRayDrag()` -> `applyScreenRotate()` (line 2231):
   - Uses `atan2` screen angle around gizmo origin projection
   - `dragRotateSign = sign(axisDir.dot(cameraToGizmo))` — positive when axis points toward camera
   - Angle delta converted to rotation around the axis

**Verdict for Path 1**: The ray-plane intersection gives a proper 3D delta. The axis projection via `accumulateAlongAxis` is mathematically correct for an unskewed Jacobian. Y and Z should NOT be inverted in this path.

#### Path 2: Gizmo Projection (non-hotkey gizmo handle drag)

Used when gizmo handles are clicked directly (non-hotkey). Requires `gizmoDrag=true`.

**Key sign asymmetries in this path**:

**`applyTranslateDelta()` (line 2952-2971)** — used in `applyGizmoTranslate()`:
```java
else if (axis == Axis.X) { vector.x += units; }
else if (axis == Axis.Y) { vector.y += units; }
else if (axis == Axis.Z) { vector.z -= units; }  // Z NEGATED!
```
Z translation is explicitly negated in global mode, while X and Y are not. This inverts Z-axis gizmo translation relative to X and Y.

**`getAxisDragDirectionSign()` (line 3050-3095)** — used in `applyGizmoRotate()`:
```java
float sign = Math.signum(alignment);
if (axis == Axis.X || axis == Axis.Z) { sign = -sign; }  // X and Z flipped
```
Rotation sign for X and Z axes is explicitly flipped, while Y is not. This makes Y rotation respond opposite to X and Z for the same screen motion.

**Verdict for Path 2**: There are known sign asymmetries (Z inverted for translate, X/Z inverted for rotate). These affect direct gizmo handle dragging but NOT the hotkey path.

#### Path 3: Classic dx-only (fallback, `transformHotkeys3dRay=false` or no GizmoDrag)

Used when none of the above paths match — lines 1707-1743 of `render()`.

```java
// Lines 1716-1721
if (this.axis == Axis.X || all) vector3f.x += factor * dx;
if (this.axis == Axis.Y || all) vector3f.y += factor * dx;
if (this.axis == Axis.Z || all) vector3f.z += factor * dx;
if (!all && this.axis2 == Axis.X) vector3f.x += factor * dx;
if (!all && this.axis2 == Axis.Y) vector3f.y += factor * dx;
if (!all && this.axis2 == Axis.Z) vector3f.z += factor * dx;
```

ALL axes use `dx` (horizontal mouse delta). `dy` (vertical mouse delta) is never used.

- **X axis**: Using `dx` is correct (horizontal mouse = X movement)
- **Y axis**: Using `dx` is wrong. Should use `-dy` (screen Y inverted vs world Y)
- **Z axis**: Using `dx` is wrong. In a perspective view, vertical screen motion maps to Z-axis movement

This path also has the issue that moving the mouse vertically has NO effect on any axis.

**Verdict for Path 3**: Y and Z are both fundamentally wrong. However, this path is only a fallback when ray drag is unavailable.

### 2. `calculateLocalVector()` 180-degree X Rotation Hack

**Location**: Lines 454-474

```java
private Vector3f calculateLocalVector(double factor, Axis axis)
{
    Vector3f vector3f = new Vector3f(
        (float) (axis == Axis.X ? factor : 0D),
        (float) (axis == Axis.Y ? factor : 0D),
        (float) (axis == Axis.Z ? factor : 0D)
    );
    /* I have no fucking idea why I have to rotate it 180 degrees by X axis... but it works! */
    Matrix3f matrix = new Matrix3f()
        .rotateX(this.model ? MathUtils.PI : 0F)
        .mul(this.transform.createRotationMatrix());

    matrix.transform(vector3f);
    return vector3f;
}
```

**Analysis**:
- `rotateX(PI)` = rotation by 180 degrees around X axis = negates Y and Z components: `(x, y, z) -> (x, -y, -z)`
- Only applied when `this.model == true` (Pose Editor only — line 157 in UIPoseEditor: `this.transform.setModel()`)
- For non-model editors (UIFormEditor, UIBodyPartEditor, film editor, etc.), `model` is false and the rotation is skipped

**Used by**:
1. **Local mode translate** in `render()` line 1684-1706 (hotkey path, non-ray): uses `calculateLocalVector(factor * dx, axis)`
2. **Local mode translate** in `internalSetT()` line 1418 (trackpad input)
3. **Local mode numeric input** in `applyNumericTranslate()` line 1314
4. **Local mode gizmo translate** in `applyTranslateDelta()` line 2956-2957
5. **Local mode ray drag `beginRayTranslate()`** line 1980-1987: uses the same `rotateX(PI)` rotation for `dragWorldBasis` and `dragTranslateBasis`

**The double-negation cancellation**: In `beginRayTranslate()` local mode:
```java
Matrix3f rotation = new Matrix3f()
    .rotateX(this.model ? MathUtils.PI : 0F)
    .mul(this.transform.createRotationMatrix());
this.dragTranslateBasis.set(rotation);
this.dragWorldBasis.set(jacobian).mul(rotation);
```

Then in `accumulateAlongAxis`:
- `worldAxis = dragWorldBasis.getColumn(Y)` = `jacobian * rotation * (0,1,0)` = `jacobian * (0,-1,0)` (Y negated by PI rotation)
- `t = worldAxis.dot(delta) / lenSq` = negative value if delta has positive Y
- `translateAxis = rotation * (0,1,0) = (0,-1,0)`
- `result += translateAxis * t` = `(0,-1,0) * negative = (0, positive, 0)`

The double negation cancels out for the **ray drag** path, making it correct. But the **local mode non-ray path** (line 1684-1706) doesn't have this cancellation — it directly adds the result of `calculateLocalVector()` which has Y and Z negated. In that case, dragging right with Y axis selected would move the model DOWN (since `dx > 0` gives `factor * dx * rotateX(PI)` = positive input to Y -> `(0, -value, 0)` after rotation).

**Impact**: Only affects non-ray local mode. The `model=true` case (Pose Editor) adds an extra Y/Z flip via `rotateX(PI)` that is compensated for in the ray drag path but NOT in the local non-ray path.

### 3. The `applyTranslateDelta` Z Negation in Gizmo Path

**File**: `UIPropTransform.java` line 2952-2971

```java
private void applyTranslateDelta(Vector3f vector, Axis axis, float units)
{
    if (this.local)
    {
        float localUnits = axis == Axis.Z ? -units : units;
        vector.add(this.calculateLocalVector(localUnits, axis));
    }
    else if (axis == Axis.X) { vector.x += units; }
    else if (axis == Axis.Y) { vector.y += units; }
    else if (axis == Axis.Z) { vector.z -= units; }  // <-- Z negated globally
}
```

In global mode, Z is explicitly negated while X and Y are not. This means:
- Dragging with Z axis in gizmo translate moves in the opposite direction from X and Y for the same screen motion

In local mode, Z is also treated specially (`axis == Axis.Z ? -units : units`). The `calculateLocalVector` for model mode already flips Y and Z (due to `rotateX(PI)`), and then the local Z negation flips Z again.

**Impact**: Only affects the gizmo drag translate path (not hotkey ray drag).

### 4. The `getAxisDragDirectionSign` X/Z Rotation Sign Flip

**File**: `UIPropTransform.java` lines 3050-3095

```java
float sign = Math.signum(alignment);
if (axis == Axis.X || axis == Axis.Z) { sign = -sign; }
return sign;
```

In gizmo rotation, the sign of rotation around Y axis is the raw alignment sign, while X and Z axes have their sign flipped. This means rotating around Y via gizmo responds opposite to rotating around X or Z for the same drag direction.

**Impact**: Only affects gizmo rotation path (not hotkey ray drag).

### 5. Which Editors Share UIPropTransform

| Editor | Uses `setModel()`? | Has hotkey GizmoDrag? | Notes |
|---|---|---|---|
| **UIPoseEditor** (Pose Editor) | YES | YES (via `UIPoseForm`) | The main affected editor |
| **UIFormEditor** (general Form editor > general transform) | no | YES (via `UIForm`) | |
| **UIBodyPartEditor** (body part editor) | no | YES (via `UIFormEditor`) | |
| **UIModelBlockPanel** (model block) | no | YES | Has `enableHotkeys()` |
| **UIAnimationStateEditor** (animation state) | no | YES (via `UIFormEditor`) | |
| **UISubtitleClip** (film subtitle) | no | no `enableHotkeys()` called | Only trackpad input |
| **UIGeneralFormPanel** (glint/root transform) | no | no | Only trackpad input |
| **UIModelBlockEditorMenu** (projectile/zoom) | no | no | Only trackpad input |

Only UIPoseEditor sets `model=true`, which activates the `rotateX(PI)` hack in `calculateLocalVector`.

### 6. Related Specs

- `new/src/main/java/mchorse/bbs_mod/BBSSettings.java` line 744: `transformHotkeys3dRay` defaults to `true`
- `.trellis/spec/new/` — (check if any spec files exist for this package)

### 7. Hotkey Order Configuration

The hotkey cycle order is configurable per mode via settings:
- `translateHotkeyOrder` for mode 0
- `scaleHotkeyOrder` for mode 1
- `rotateHotkeyOrder` for mode 2

Default order includes X, Y, Z, SCREEN, VIEW (for rotate) etc. (line 609 in `UIPropTransform.java`)

When Ray drag is enabled, the cycle also includes SCREEN, VIEW, SPHERE options (via `needsRay` flag in `HotkeyTarget` enum lines 3168-3201).

## Caveats / Not Found

### The Y/Z inversion may NOT be in the ray drag path

The default `transformHotkeys3dRay=true` path goes through `applyRayDrag()`, which uses 3D ray-plane intersection and proper axis projection. Mathematically, this should not produce Y/Z inversion for translate or scale. The defect description mentions "G/S/R hotkey + mouse drag" which goes through this default path.

**Possible root causes for observed Y/Z inversion:**

1. **Jacobian issues**: If `computeTranslateJacobian()` produces a skewed matrix (due to pose-dependent bone sampling), `resolveTranslateJacobian` may fall back to an identity. But identity would give correct axes.

2. **Camera-relative axes**: `gizmoWorldAxes` from `computeWorldAxes()` uses the gizmo's rendered model-view matrix. If the gizmo's `lastRenderMatrix` captures a rotated frame (e.g., a rotated model form), the axes in `gizmoWorldAxes` rotate accordingly, making Y and Z axis behavior appear to invert relative to the screen.

3. **Form-level vs. bone-level editing**: In UIFormEditor, the general form transform has its own axes. On a rotated form, "global" mode uses the form's world axes, which are the model's rotation in world space. The form's up axis may not be world-space Y if the form is tilted.

4. **Classic path bug (non-ray mode)**: If `transformHotkeys3dRay` is false (user changed setting), Y and Z both use `dx` instead of `dy`, making their response entirely based on horizontal mouse movement. This causes visible inversion.

5. **Local mode with model=true**: The `rotateX(PI)` hack in `calculateLocalVector` inverts Y and Z for model-space transforms. The double-negation cancellation only works in the ray drag path, leaving the non-ray local path inverted.

### Missing information for complete diagnosis

- The exact camera angle and model orientation when the defect occurs were not specified
- Whether the user has `transformHotkeys3dRay` enabled (it defaults to true but may have been changed)
- Whether the editor was in "local" or "global" mode when the inversion was observed
- Whether it was a form-level transform (UIFormEditor) or bone-level transform (UIPoseEditor)
- The `computeWorldOrigin` and `computeWorldAxes` results depend on the runtime model-view matrix, which cannot be statically analyzed without knowing the exact rendering context

### Recommendation for further investigation

The most likely candidate for the defect is that **`applyTranslateDelta` Z negation** (line 2970) combined with the **`getAxisDragDirectionSign` X/Z rotation flip** (lines 3089-3091) creates asymmetric axis behavior that manifests in certain editor configurations. These are in the gizmo drag path, which may be reached when gizmos are enabled and the user interacts through gizmo handles rather than pure screen-space dragging.

For the hotkey-only (non-gizmo) case, check whether `transformHotkeys3dRay` is true; if not, the classic dx-only path (lines 1707-1743) lacks `dy` usage for Y and Z axes, which is the most obvious bug.
