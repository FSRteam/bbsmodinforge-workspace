# Forms Rendering Contract: Additive Blending

> Executable contracts for `additiveColor` on form renderers, and the RenderSystem
> blend-state rules that keep additive blending from leaking.

## 1. Scope / Trigger

- Trigger: `additiveColor` behavior differs per form renderer; an additive MobForm
  leaked its blend function into later non-additive forms (fixed 2026-07-31).

## 2. Contract: additiveColor per form type

| Form renderer | additiveColor effect | Mechanism |
|---------------|----------------------|-----------|
| `MobFormRenderer` | True additive glow | `applyAdditiveBlend()` switches blend function to `(SRC_ALPHA, ONE)` via the layer-preparation hooks (`CustomVertexConsumerProvider.hijackLayerPreparation`) |
| `ModelFormRenderer` | Vertex-color brighten only | `FormColorBlend.blend(..., additive)` = `BlendMode.BRIGHTEN` (`base *= 1 + r*a*8`); byte vertex formats clamp at 255, so the glow cannot exceed white |

**Design decision (2026-07-31)**: ModelForm is intentionally NOT aligned to
MobForm's true additive blend. If ModelForm glow parity is wanted later, add the
same `(SRC_ALPHA, ONE)` blend-function switch in its `renderModel`/`render3D`
draw where it already calls `RenderSystem.enableBlend()`.

## 3. Contract: blend-state lifecycle (gotcha)

RenderSystem blend function is process-wide and applied lazily. A layer's
`clearRenderState()` only restores what its `RenderStateShard` cleared:

- `TRANSLUCENT_TRANSPARENCY` clears with `disableBlend()` + `defaultBlendFunc()`.
- `NO_TRANSPARENCY` (opaque) clears with a **no-op** for blend.

Therefore `MobFormRenderer` must obey:

1. `applyAdditiveBlend()`: when `form.additiveColor` is **off**, call
   `RenderSystem.defaultBlendFunc()` (not just `enableBlend()`), so a leaked
   `(SRC_ALPHA, ONE)` from an earlier additive form cannot tint this form.
2. After the form's world render (the `finally` alongside
   `CustomVertexConsumerProvider.clearRunnables()`), call
   `RenderSystem.defaultBlendFunc()` to restore the default for later forms.
3. Deferred translucent queue commands re-apply their own captured preparation at
   draw time, so the `finally` restore must not be relied on for them.

## 4. Wrong vs Correct

### Wrong

```java
// applyAdditiveBlend(): additive off does nothing
if (this.form.additiveColor.get()) {
    RenderSystem.blendFuncSeparate(SRC_ALPHA, ONE, ONE, ONE_MINUS_SRC_ALPHA);
}
// opaque layer's clear is a no-op -> (SRC_ALPHA, ONE) leaks; the next
// non-additive MobForm re-enables blend with it and draws additive.
```

### Correct

```java
private void applyAdditiveBlend() {
    if (this.form.additiveColor.get()) {
        RenderSystem.blendFuncSeparate(SRC_ALPHA, ONE, ONE, ONE_MINUS_SRC_ALPHA);
    } else {
        RenderSystem.defaultBlendFunc();
    }
}

// finally after the world render:
RenderSystem.defaultBlendFunc();
```

## 5. Tests Required

- Source-level regression: `PoseFormRegressionSourceTest.additiveBlendDoesNotLeak`
  asserts `applyAdditiveBlend` contains `RenderSystem.defaultBlendFunc()` and the
  render `finally` restores it.
- Runtime smoke: morph into an additive MobForm in third person near a
  non-additive MobForm block; the block must not render additive.
