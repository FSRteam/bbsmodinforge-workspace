# Research: MobForm Additive Blending

- **Query**: MobForm is missing additive blending adaptation support -- need to verify and document how additive blending works across Form types, and identify what MobForm is missing
- **Scope**: Internal (new/bbs-FSR codebase)
- **Date**: 2026-07-30

## Findings

### 1. Data Model: `additiveColor` in Form base class

**File**: `new/src/main/java/mchorse/bbs_mod/forms/forms/Form.java`

- Line 49: `public final ValueBoolean additiveColor = new ValueBoolean("additive_color", false);`
- Line 111: `this.additiveColor.invisible();` -- hidden from automatic UI layout
- Line 136: `this.add(additiveColor);` -- registered in value group

**MobForm.java** (`new/src/main/java/mchorse/bbs_mod/forms/forms/MobForm.java`) extends `Form` directly (line 15). It inherits `additiveColor` with no override -- the field is present and accessible.

Other forms that inherit `additiveColor` from `Form`:
- `BillboardForm` 
- `ModelForm`
- `LabelForm`
- `ParticleForm`
- `PoseForm` (not a standalone renderable, is an interface)
- `VanillaParticleForm`

All of these have the `additiveColor` field via inheritance.

### 2. Color Blend Utility: `FormColorBlend`

**File**: `new/src/client/java/mchorse/bbs_mod/forms/renderers/utils/FormColorBlend.java`

This is a VERTEX-COLOR operation, NOT an OpenGL blend mode change.

```java
public enum BlendMode { MULTIPLY, BRIGHTEN }

public static void blend(Color base, Color overlay, boolean additive)
{
    blend(base, overlay, additive ? BlendMode.BRIGHTEN : BlendMode.MULTIPLY);
}
```

- **BlendMode.MULTIPLY** (additive=false): `base.r *= r; base.g *= g; base.b *= b;` -- standard texture multiply, darkens toward overlay color
- **BlendMode.BRIGHTEN** (additive=true): `base.r *= 1F + r * a * 8F;` -- brightens by overlay, allows vertex color components above 1.0 to simulate additive glow

**Critical**: This modifies vertex color values in CPU-side color structs. Whether those values survive to the GPU depends on the vertex format (byte-based colors clamp at 255) and the shader pipeline.

### 3. Renderers that USE `FormColorBlend` with `additiveColor`

| Renderer | File | Lines | Color Source |
|---|---|---|---|
| **BillboardFormRenderer** | `.../renderers/BillboardFormRenderer.java` | 184 | `renderColor.set(overlayColor, true)` |
| **ModelFormRenderer** | `.../renderers/ModelFormRenderer.java` | 327, 775, 885 | Various (UI, arm, world 3D) |
| **LabelFormRenderer** | `.../renderers/LabelFormRenderer.java` | 151, 258 | `textColor.set(context.color, true)` |
| **MobFormRenderer** | `.../renderers/MobFormRenderer.java` | 477 | `new Color().set(contextColor, true)` |

Each calls: `FormColorBlend.blend(color, form.color.get(), form.additiveColor.get())`

### 4. MobFormRenderer additive color handling (EXISTS)

**File**: `new/src/client/java/mchorse/bbs_mod/forms/renderers/MobFormRenderer.java`

**Method `getColor()` (lines 472-479):**
```java
private Color getColor(int contextColor)
{
    Color color = new Color().set(contextColor, true);
    FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());
    return color;
}
```

**Called from UI path (line 286):**
```java
this.getColor(0xffffffff)
```

**Called from world 3D path (line 378):**
```java
this.getColor(context.color)
```

The resulting `Color` object is passed into `MobRenderContext.push()` (line 286, 374-382), where it gets stored and used by `ModelPartMixin` to multiply vertex colors.

**Mixin application at `ModelPartMixin.bbs$applyMobAppearance()` (lines 124-179):**
- Retrieves `Color color = MobRenderContext.getColor(part);` (line 135)
- Multiplies vertex color components: `redMultiplier *= color.r` etc. (lines 159-170)
- Applies to packed ARGB vertex color (lines 172-178)

### 5. OpenGL Blend Modes across renderers

None of the form renderers change the OpenGL blend function based on `additiveColor`. All use `RenderSystem.defaultBlendFunc()` (which is `SRC_ALPHA, ONE_MINUS_SRC_ALPHA` -- standard transparency):

| Renderer | Blend function | Where |
|---|---|---|
| **MobFormRenderer** | `enableBlend()` only, no explicit blendFunc | Lines 275 (UI), 698 (world) |
| **ModelFormRenderer** | `defaultBlendFunc()` | Line 390 |
| **BillboardFormRenderer** | `defaultBlendFunc()` | Line 205 |
| **LabelFormRenderer** | `enableBlend()` only, no explicit blendFunc | (implicit via RenderSystem) |

**True additive OpenGL blend** (for comparison) is used by `ParticleEmitter`:

**File**: `new/src/client/java/mchorse/bbs_mod/particles/emitter/ParticleEmitter.java` (lines 781-788)
```java
if (material == ParticleMaterial.ADD)
{
    RenderSystem.blendFuncSeparate(
        GlStateManager.SourceFactor.SRC_ALPHA,
        GlStateManager.DestFactor.ONE,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
    );
}
```

This is from the ParticleScheme system, NOT from `ParticleFormRenderer`. The `ParticleFormRenderer` does NOT use `additiveColor` at all -- it delegates rendering to the `ParticleEmitter`, which has its own material system (`ParticleMaterial.ADD`).

### 6. UI and Localization for `additiveColor`

**UI toggle**: `UIGeneralFormPanel.java` (line 86-87):
```java
this.additiveColor = new UIToggle(UIKeys.FORMS_EDITORS_ADDITIVE_COLOR, (b) -> this.form.additiveColor.set(b.getValue()));
```
Added to display at line 130:
```java
this.lighting, this.shaderShadow, this.additiveColor,
```
Loaded from form at line 196:
```java
this.additiveColor.setValue(form.additiveColor.get());
```

**UIKeys**: `UIKeys.java` line 461:
```java
public static final IKey FORMS_EDITORS_ADDITIVE_COLOR = L10n.lang("bbs.ui.forms.editors.additive_color");
```

**English string**: `en_us.json` line 805:
```json
"bbs.ui.forms.editors.additive_color": "Additive color",
```

The UI toggle is shared by ALL form types via `UIGeneralFormPanel`, so MobForm already shows the toggle.

### 7. ParticleForm additive support -- SEPARATE SYSTEM

`ParticleFormRenderer.java` does NOT use `FormColorBlend` and does NOT reference `additiveColor`. The particle system's "additive" support is through `ParticleMaterial.ADD` which sets the OpenGL blend mode to additive. This is configured at the particle scheme level, not the form level.

### 8. PoseForm -- NOT a standalone renderable form

`PoseForm` is an interface (implemented by `MobForm` and `ModelForm`), not a standalone renderable form. There is no `PoseFormRenderer.java`. The "additive support" claim for PoseForm likely refers to how the pose itself can carry color/alpha data, which is separate from `additiveColor`.

## Analysis Summary

**Current state**: MobFormRenderer ALREADY implements `additiveColor` support through the `FormColorBlend` mechanism at the vertex-color level, exactly matching the pattern used by `BillboardFormRenderer`, `ModelFormRenderer`, and `LabelFormRenderer`.

The `additiveColor` feature currently works as follows across ALL form renderers:
1. A boolean toggle `additive_color` stored in the data model
2. When enabled, `FormColorBlend.BRIGHTEN` multiplies vertex color RGB by `(1 + overlay * alpha * 8)` instead of `overlay`
3. The OpenGL blend function remains at default (`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`)

## Caveats / Possible Missing Pieces

1. **No form renderer switches OpenGL blend mode to additive (`GL_ONE, GL_ONE`)**. If the intent of "additive blending" is true GPU-level additive blending (like particles with `ParticleMaterial.ADD`), then ALL form renderers (not just MobForm) need this change.

2. **Vertex color brightening may be clamped**: Since vertex colors in most vertex formats are stored as unsigned bytes (0-255), values above 1.0 from `BRIGHTEN` mode get clamped at 255. The brightening only works if the shader/format preserves higher values.

3. **If the defect means MobForm has NO additive support at all** -- the current code contradicts this; MobForm already has `additiveColor` inherited and `FormColorBlend.blend()` called in `getColor()`. The defect may have been filed against an older version before this was implemented.

4. **If the defect means OpenGL blend mode** -- this would be missing from ALL form renderers, and would need an additional `RenderSystem.blendFunc()` call when `additiveColor` is true, similar to `ParticleEmitter`'s `ParticleMaterial.ADD` path.

## Related Specs

- None found in `.trellis/spec/` for additive blending

## Files Found

| File Path | Description |
|---|---|
| `new/src/main/java/mchorse/bbs_mod/forms/forms/Form.java` | Base Form class with `additiveColor` field (line 49) |
| `new/src/main/java/mchorse/bbs_mod/forms/forms/MobForm.java` | MobForm extends Form, inherits `additiveColor` |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/MobFormRenderer.java` | MobForm renderer, `getColor()` uses `FormColorBlend.blend()` at line 477 |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/utils/FormColorBlend.java` | Color blend utility with MULTIPLY/BRIGHTEN modes |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/ModelFormRenderer.java` | Reference: uses same pattern at lines 327, 775, 885 |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/BillboardFormRenderer.java` | Reference: uses same pattern at line 184 |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/LabelFormRenderer.java` | Reference: uses same pattern at lines 151, 258 |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/ParticleFormRenderer.java` | Does NOT use `additiveColor` -- separate particle material system |
| `new/src/client/java/mchorse/bbs_mod/particles/emitter/ParticleEmitter.java` | True OpenGL additive blend at lines 781-788 via `ParticleMaterial.ADD` |
| `new/src/client/java/mchorse/bbs_mod/particles/ParticleMaterial.java` | `ADD` enum for particle additive mode |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/MobRenderContext.java` | Holds per-form color; used by ModelPartMixin |
| `new/src/client/java/mchorse/bbs_mod/mixin/client/ModelPartMixin.java` | Applies MobForm color to vertex data (line 131-179) |
| `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/UIGeneralFormPanel.java` | UI toggle for `additiveColor` at lines 86-87, 130, 196 |
| `new/src/client/java/mchorse/bbs_mod/ui/UIKeys.java` | Translation key at line 461 |
