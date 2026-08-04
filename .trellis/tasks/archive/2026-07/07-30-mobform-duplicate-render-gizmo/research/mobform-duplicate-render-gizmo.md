# Research: MobForm Duplicate Render and Gizmo Follow Defect

- **Query**: Entity selector MobForm duplicate rendering with same ID, gizmo not following
- **Scope**: internal
- **Date**: 2026-07-30

## Findings

### 1. Rendering Pipeline Architecture

#### Class Hierarchy
```
UIModelRenderer (base: sets up viewport/camera, calls abstract renderUserModel)
  -> UIFormRenderer (simple: just renders form via FormUtilsClient)
    -> UIPickableFormRenderer (adds: stencil picking pass + gizmo + highlight overlay)
```

#### UIModelRenderer.render() (new/.../ui/framework/elements/utils/UIModelRenderer.java:203)
```
render()
  -> updateLogic()
  -> clip area
  -> renderModel() [private]
      -> setupPosition()                    // compute camera position from pos+rotation
      -> setupViewport()                    // glViewport + camera.projection + camera.view
      -> cacheMatrices()
      -> RenderSystem.setProjectionMatrix(camera.projection)
      -> pushPose(), apply camera.view + camera.position translation
      -> renderGrid()
      -> renderUserModel(context)           // POLYMORPHIC dispatch
      -> popPose()
      -> restore viewport to full window
      -> restoreMatrices()
  -> BBSFormPreviewCapture.include(...)
  -> unclip
  -> super.render(context)                  // render children
```

#### UIPickableFormRenderer.renderUserModel() (new/.../UIPickableFormRenderer.java:203)
```
  [Visual pass]
  -> preFormRender()
  -> FormRenderingContext with camera, PREVIEW type, modelRenderer flag
  -> FormUtilsClient.render(this.form, formContext)   // renders form visually

  [Gizmo axes]
  -> renderAxes()                            // gizmo visual (via Gizmo.INSTANCE)

  [Stencil/picking pass - only when mouse is inside area]
  -> batcher.flush()
  -> disableScissorTest
  -> stencilMap.setup()                      // reset stencil index map
  -> stencil.apply()                         // bind + clear stencil framebuffer
  -> FormUtilsClient.render(form, formContext.stencilMap(stencilMap))  // render with picking IDs
  -> render gizmo stencil                    // Gizmo.INSTANCE.renderStencil()
  -> stencil.pickGUI(...)                    // read back pixel under mouse
  -> stencil.unbind(stencilMap)              // unbind stencil, copy index map
  -> Minecraft.getInstance().getMainRenderTarget().bindWrite(true)  // restore main FB
  -> enableScissorTest
```

#### UIPickableFormRenderer.render() (new/.../UIPickableFormRenderer.java:357)
```
  -> super.render(context)          // UIModelRenderer.render() -> visual + stencil
  -> SoundGuideInteraction.update()
  -> gizmoInteraction.update()
  -> gizmoInteraction.renderSphereHighlight()
  -> gizmoInteraction.renderReadout()
                                     // overlay: draw stencil FB texture
  -> if (stencil.hasPicked()):
       -> texturedBox(pickerPreviewProgram, stencilFB.texture,
                       area.x, area.y, area.w, area.h,
                       0, texH, texW, 0, texW, texH)
       -> draw bone label text
```

### 2. CRITICAL: Shared Stencil Framebuffer

**File:** `new/.../graphics/FramebufferManager.java`

```java
public Framebuffer getFramebuffer(Link key, Consumer<Framebuffer> setup) {
    Framebuffer framebuffer = this.framebuffers.get(key);
    if (framebuffer == null) {
        framebuffer = new Framebuffer();
        setup.accept(framebuffer);
        this.framebuffers.put(key, framebuffer);
    }
    return framebuffer;
}
```

**All `StencilFormFramebuffer` instances using the same `Link` key share the same underlying GL framebuffer object.**

In `UIPickableFormRenderer.ensureFramebuffer()` (line 131-135):
```java
this.stencil.setup(Link.bbs("stencil_form"));
```

The `StencilFormFramebuffer.setup()` method only creates the Framebuffer once:
```java
public void setup(Link id) {
    if (this.framebuffer != null) return;
    this.framebuffer = BBSModClient.getFramebuffers().getFramebuffer(id, ...);
}
```

Therefore, **every `UIPickableFormRenderer` instance in the application shares the same GL framebuffer for its stencil/picking pass.**

#### Other Stencil Framebuffers (different Link keys, no conflict)
| Location | Link key |
|---|---|
| UIPickableFormRenderer | `Link.bbs("stencil_form")` |
| UIFilmController | `Link.bbs("stencil_film")` |
| UIModelBlockPanel | `Link.bbs("stencil_model_block")` |
| Gizmo sphereHighlight | `Link.bbs("gizmo_sphere_highlight")` |

### 3. Stencil Framebuffer Detail

**File:** `new/.../ui/utils/StencilFormFramebuffer.java`

#### apply() (line 90-93)
```java
public void apply() {
    Texture texture = this.getMainTexture();
    GlStateManager._viewport(0, 0, texture.width, texture.height);
    this.bind();
}
```
Sets the GL viewport to `(0, 0, texW, texH)` - the stencil framebuffer's own coordinate space. This REPLACES the viewport set by `UIModelRenderer.setupViewport()`, which was positioned at the area's screen location.

#### applyClear() (line 90)
Calls `apply()` then `clear()` (GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT).

#### pickGUI() (line 100-107)
```java
public void pickGUI(UIContext context, Area area, int radius, int handleMax) {
    float scale = BBSModClient.getGUIScale();
    int x = Math.round((context.mouseX - area.x) * scale);
    int y = Math.round((area.h - context.mouseY + area.y) * scale);
    this.pick(x, y, Math.round(radius * scale), handleMax);
}
```
Y-flip: `(area.h - context.mouseY + area.y)` converts from UI coordinates (origin top-left) to OpenGL coordinates (origin bottom-left) within the framebuffer.

#### unbind() (line 228-230)
```java
public void unbind() {
    this.framebuffer.unbind();
}
```
Simply unbinds the framebuffer (binds framebuffer 0).

#### unbind(StencilMap) (line 219-225)
```java
public void unbind(StencilMap map) {
    this.unbind();
    this.indexMap.clear();
    this.indexMap.putAll(map.indexMap);
}
```
Copies the stencil index map from the StencilMap into the StencilFormFramebuffer for later lookup via `getPicked()`.

### 4. OpenGL Viewport Lifecycle

The viewport changes multiple times during a single frame:

1. **UIModelRenderer.setupViewport()**: `UIUtils.viewportArea(this.area)` sets viewport to `(areaX*scale, windowH - (areaY+areaH)*scale, areaW*scale, areaH*scale)` - the area position in framebuffer pixels
2. **StencilFormFramebuffer.apply()**: Overwrites to `(0, 0, texW, texH)` - the stencil FB's own coordinates
3. **UIModelRenderer.renderModel()** (after renderUserModel): Restores to `(0, 0, windowW, windowH)` - full window

**CRITICAL OBSERVATION:** The stencil framebuffer's viewport starts at (0,0) while the main viewport is positioned at the area's screen location. The PROJECTION MATRIX is set ONCE by `setupViewport()` before `renderUserModel()` and is NOT restored between the visual pass and the stencil pass. The projection matrix uses perspective(fov, aspect, near, far) based on the viewport dimensions (which happen to be the same), but does NOT include any viewport offset - so the rendered content should be identical in both passes.

### 5. Overlay Texture Coordinates and Y-Flip

**File:** `new/.../UIPickableFormRenderer.java:394`

```java
Texture texture = this.stencil.getFramebuffer().getMainTexture();
int w = texture.width;   // = Math.round(this.area.w * guiScale)
int h = texture.height;  // = Math.round(this.area.h * guiScale)

context.batcher.texturedBox(BBSShaders::getPickerPreviewProgram, texture.id, Colors.WHITE,
    this.area.x, this.area.y, this.area.w, this.area.h,    // destination: UI coords
    0, h, w, 0, w, h);                                      // source UV: (0, texH) -> (texW, 0)
```

**Destination-to-source mapping** (from `Batcher2D.fillTexturedBox()`):

| Destination vertex | Screen coord | Source UV |
|---|---|---|
| Top-left | (area.x, area.y) | (0/texW, texH/texH) = (0, 1) = bottom-left of texture |
| Top-right | (area.x+area.w, area.y) | (texW/texW, texH/texH) = (1, 1) |
| Bottom-left | (area.x, area.y+area.h) | (0/texW, 0/texH) = (0, 0) = top-left of texture |
| Bottom-right | (area.x+area.w, area.y+area.h) | (1, 0) |

This is a correct Y-flip converting from OpenGL (origin bottom-left) to UI (origin top-left) coordinates. The stencil framebuffer content should display correctly in the viewport area.

### 6. "Same ID" Trigger Analysis

#### Form Cache/Registry
- `Form.getFormId()` returns `BBSMod.getForms().getType(this).toString()` - the form's type registry identifier (e.g., "mob_form", "model_form")
- `FormArchitect` uses `"id"` as type key in serialized data
- `FormUtils.copy(form)` serializes→deserializes, creating a new Form instance

#### Entity Selector Form Storage
- `UISelectorsOverlayPanel.setSelector()` stores the form reference
- When editing, `FormUtils.copy(form)` is created and stored in `this.current.form`
- Each selector gets its own Form instance

#### "Same ID" Meaning
Most likely interpretations ranked by likelihood:

1. **Two entity selectors with MobForms pointing to the same `mobID`** (e.g., both have mobID="minecraft:zombie"). When the form editor's 3D preview renders one and the selector list preview renders another, or when two selector elements both render small previews simultaneously.

2. **Two EntitySelector entries with the same form (identical form data)** due to copy/paste or adding from an existing selector.

3. **Rare case: the form editor is open editing a MobForm while the selector list also renders a small preview** of the same mob type.

### 7. Selector List Small Previews

**File:** `new/.../ui/selectors/UISelectorList.java:46-64`

```java
protected void renderElementPart(UIContext context, EntitySelector element, int i,
    int x, int y, boolean hover, boolean selected) {
    super.renderElementPart(context, element, i, x, y, hover, selected);

    Form form = element.form;
    if (form != null && BBSSettings.listModelPreview.get()) {
        x += this.area.w - 30;
        context.batcher.clip(x, y, 40, 20, context);
        y -= 10;
        FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);
        context.batcher.unclip(context);
    }
}
```

Each visible selector list item renders a **40x40 small preview** of its form via `FormUtilsClient.renderUI()`. This calls `FormRenderer.renderUI()` -> `beginRenderUI()` -> `renderInUI()` -> `finishRenderUI()`.

For ModelForm: `ModelFormRenderer.renderInUI()` uses `getUIMatrix()` (line 134-153):
```java
uiMatrix.identity();
uiMatrix.translate(x, y, 40);    // position at center of preview
uiMatrix.scale(scale, -scale, scale);  // Y-flip
uiMatrix.rotateX(MathUtils.PI / 8);    // tilt 22.5 degrees
uiMatrix.rotateY(angle);               // mouse-X-dependent rotation
```

For MobForm: `MobFormRenderer.renderInUI()` also uses `ModelFormRenderer.getUIMatrix()` (line 244).

**These small previews use a DIFFERENT camera/view matrix from the main editor's 3D preview.** They do NOT go through `UIModelRenderer.renderModel()` and do NOT use the model renderer's camera. They directly manipulate the GL state (depth func, viewport) and restore it afterwards.

However, `beginRenderUI()` calls `RenderSystem.setupLevelDiffuseLighting()` which changes GL state that might affect subsequent rendering. And `finishRenderUI()` calls `BBSFormPreviewCapture.include()`.

### 8. MobFormRenderer Entity Management

**File:** `new/.../forms/renderers/MobFormRenderer.java:167-229`

```java
private void ensureEntity() {
    String id = this.form.mobID.get();
    String nbt = this.form.mobNBT.get();
    boolean slim = this.form.slim.get();

    if (!this.lastId.equals(id) || !this.lastNBT.equals(nbt) || slim != this.lastSlim) {
        this.lastId = id;
        this.lastNBT = nbt;
        this.lastSlim = slim;
        this.entity = null;   // Reset entity when identity changes
        // Reset all animation state...
    }

    if (this.entity != null) return;   // Use cached entity

    // Create new entity from mobID
    if (this.form.isPlayer()) {
        this.entity = new MobPlayer(Minecraft.getInstance().level, slim ? SLIM : WIDE);
    } else {
        this.entity = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id))
            .create(Minecraft.getInstance().level);
    }
    // Load NBT, set properties
}
```

Each MobFormRenderer manages its own Minecraft `Entity` instance. The entity is created once and cached until `mobID`, `mobNBT`, or `slim` changes.

**When two entity selectors have MobForms with the same mobID, two separate MobFormRenderer instances create two separate Entity instances** - no direct sharing issue here.

### 9. Gizmo Interaction Pipeline

**GizmoViewport interface** (new/.../GizmoViewport.java):
```java
interface GizmoViewport {
    StencilFormFramebuffer getGizmoStencil();  // returns this.stencil
    Matrix4f getGizmoProjection();             // returns this.camera.projection
    Area getGizmoArea();                       // returns this.area
    boolean startGizmo(UIContext, int);        // delegates to formEditor.startGizmo()
    void pickGizmoForm(UIContext, Form, String); // delegates to formEditor.pickGizmoFormFromRenderer()
}
```

**GizmoInteraction** (new/.../GizmoInteraction.java):
- `mouseClicked()`: checks `stencil.hasPicked()` to detect gizmo handle clicks
- `updateSphereHover()`: checks stencil and screen-center distance
- `mouseReleased()`: if pendingPickForm is set, calls `viewport.pickGizmoForm()` -> `formEditor.pickGizmoFormFromRenderer()`

**Gizmo.INSTANCE** (new/.../Gizmo.java):
- `renderStencil()`: draws gizmo handles with stencil IDs, DOES NOT capture `lastRenderMatrix`
- `captureVisual()`: captures `lastRenderMatrix` for later visual rendering
- `renderStencilInterface()`: alternative stencil rendering path for UI-pass picking
- `captureRenderMatrix()` (line 1452-1468): stores `modelView` and `projection` in `dragContext`

**Critical note from Gizmo.java:1200-1211:**
```java
/**
 * Deliberately does NOT capture lastRenderMatrix: the picking pass only runs while
 * the cursor is inside the preview viewport ... capturing here would make the gizmo's
 * world placement depend on where the cursor happens to be.
 */
public void renderStencil(PoseStack stack, StencilMap map)
```

The gizmo's visual placement is determined by `captureVisual()` / `captureRenderMatrix()` (called during the visual/axes render), NOT by the stencil pass. The stencil pass only draws the hit-test IDs.

### 10. Critical Finding: Gizmo Stencil vs Visual Mismatch Potential

In `UIPickableFormRenderer.renderUserModel()`:

1. **Visual axes** (line 234): `renderAxes()` calls `Gizmo.INSTANCE.render()` which internally calls `captureRenderMatrix()` to capture the model-view matrix
2. **Gizmo stencil** (line 271): `Gizmo.INSTANCE.renderStencil()` draws the handle IDs into the picking framebuffer

The visual and stencil gizmo passes both use `Gizmo.INSTANCE` which is a SINGLETON. The `captureRenderMatrix()` is called during the visual pass. The stencil pass reads the matrix that was captured during the visual pass.

**If only one renderUserModel() runs per frame, this is fine. But if two renderers share the same Gizmo singleton and both render in the same frame, the second renderer's captureRenderMatrix() would overwrite the first's, breaking the gizmo placement for the first renderer.**

However, this would only manifest if two `UIPickableFormRenderer` instances render on the same frame, which should not happen because `UIFormPalette.open()` prevents multiple palettes from opening simultaneously.

### 11. Animation Pausing and PreviewPoseSnapshot

**File:** `new/.../forms/renderers/ModelFormRenderer.java:923-1081`

`ModelFormRenderer` has a `PreviewPoseSnapshot` inner class that:
- Captures model state (bone transforms, colors, orientations) after the VISUAL pass
- Reuses this state in the PICKING pass to avoid re-computing animations
- Consumes the snapshot when `context.modelRenderer && context.isPicking() && ...`

```java
// In render3D(), after visual render:
if (context.modelRenderer && !context.isPicking()) {
    this.previewPoseSnapshot.capture(context, model);
}
```

The snapshot is keyed by `(context, simulationOwner, entity, model, age, transition)`. If the context is identical between visual and picking passes, the picking pass reuses the visual pass's captured pose without re-applying animations.

This is important for performance but **not related to the duplicate rendering defect**.

## Potential Root Causes (Ranked)

### 1. MOST LIKELY: Stencil framebuffer overlay drawn with incorrect viewport/projection

The overlay in `UIPickableFormRenderer.render()` draws the stencil framebuffer texture over the viewport area. The stencil framebuffer was rendered with:
- Viewport at (0, 0, texW, texH)
- Projection matrix set for the main viewport area dimensions

But between the stencil pass (inside `renderUserModel()`) and the overlay draw (in `render()`), the GL viewport is restored to full window by `UIModelRenderer.renderModel()`. The overlay thus draws with a DIFFERENT viewport than the stencil pass used.

The batcher's `texturedBox` method draws in UI coordinates (handled by the batcher's matrix). It should map correctly. But the picker preview shader (`BBSShaders.getPickerPreviewProgram()`) might read GL state (like viewport or projection) that was changed between the stencil pass and the overlay draw.

**Fix**: Investigate whether `getPickerPreviewProgram()` needs the correct GL viewport/projection to be set when the overlay is drawn.

### 2. VERY LIKELY: Shared GL framebuffer causes race between renderers

While `UIFormPalette.open()` prevents two form editors from opening simultaneously, the shared `FramebufferManager` with key `"stencil_form"` means:
- If the selector list's small previews somehow trigger a stencil pass (they don't directly, but `SoundGuideInteraction` or other systems might)
- If a renderer is garbage collected and a new one created, they share the same GL framebuffer object

**Fix**: Use unique keys per StencilFormFramebuffer instance, or remove from FramebufferManager when the renderer is released.

### 3. LIKELY: Selector list small preview OpenGL state leak

The small 40x40 form previews in `UISelectorList.renderElementPart()` call `FormUtilsClient.renderUI()` which:
- Changes GL depth func
- Sets diffuse lighting (`RenderSystem.setupLevelDiffuseLighting()`)
- Modifies the viewport
- Renders UI matrices

If any of these state changes is not properly restored, the subsequent rendering (including the form editor's stencil pass) could use incorrect GL state, causing the overlay or stencil to appear displaced.

**Check**: Look at whether `finishRenderUI()` or any intermediate step restores all GL state that was changed during the preview render.

### 4. POSSIBLE: Gizmo not following due to incorrect stencil index on same-ID forms

When two selectors have MobForms with the same mobID, and both render small previews in the selector list, each preview is drawn at a different position but uses a separate `FormRenderer` instance. These renders don't go through `UIPickableFormRenderer.renderUserModel()`, so they don't use the stencil framebuffer.

But if the form is ALSO open in the editor's `UIPickableFormRenderer`, the editor's stencil pass would render the form into the stencil framebuffer. If the stencil framebuffer was previously used by another rendering (or its content was overwritten), the pick result could be incorrect, leading to the gizmo not finding the correct bone.

### 5. POSSIBLE: MobForm animation pause state leak

`MobFormRenderer` has an elaborate animation pause state machine (`previewPoseSnapshot`, `pausedTransition`, etc.). If two `MobFormRenderer` instances share the same entity type and their animation pause states interact, the rendered pose could be different between the visual pass and the stencil pass.

**Check**: Whether `ensureEntity()` on one renderer can affect another renderer's entity when they have the same mobID and NBT.

## Related Specs

- `.trellis/spec/framebuffer.md` (if exists)

## Caveats / Not Found

- **Exact root cause not confirmed**: The analysis above identifies the `FramebufferManager`'s shared framebuffer and potential GL state leaks as the most likely causes, but the exact reproduction path is not fully verified without runtime debugging.
- **"Same ID" definition**: Further clarification needed on whether "same ID" means same `mobID` property, same form registry ID, or same form instance.
- **Area coordinate mismatch**: The `StencilFormFramebuffer.pickGUI()` Y-flip formula `area.h - context.mouseY + area.y` was noted but not fully verified against all edge cases.
- **Picker preview shader implementation**: The `BBSShaders.getPickerPreviewProgram()` shader source was not inspected - it might have viewport-dependent behavior that causes the overlay to appear with a different perspective.
