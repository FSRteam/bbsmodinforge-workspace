# Editor layout and preview rendering

Source: `new/`, FS commits34–42 of the pinned migration range. These are source
contracts; compilation, existing executing suites and real Minecraft/GL acceptance
must be recorded separately. The latest task instruction forbids new or modified
test sources/fixtures.

## Scenario: Generic Dock storage and binding

### 1. Scope / Trigger

Changes to `ValueEditorLayout`, `ILayoutSource`, Film/Particle Dock adapters or
`UIDockSlot` must preserve storage and active layout selection together.

### 2. Signatures

- `getLayout(String, Supplier<EditorLayoutNode>)` returns the stored tree or the
  supplied default without adding a default as a read side effect.
- `setLayout(String, EditorLayoutNode)` writes through `BaseValue.edit`; null removes.
- `isBound(String)` / `setBound(String, boolean, EditorLayoutNode)` select ownership.
- `ILayoutSource.setRoot(EditorLayoutNode)` owns active Dock root writes.
- `EditorLayoutNode.copyWithSplitterRatios(root, identityRatios)` batches replacement.

### 3. Contracts

- VERSION1 payload fields are `version`, `layouts` (id to serialized tree) and
  optional `bound` (list of independent layout ids). Film selects
  `isBound(editorId) ? editorId : FILM`; Particle uses `PARTICLE`.
- Binding keeps an existing stored independent tree, otherwise seeds from the
  displayed tree. Explicit unbinding removes the independent id and its tree.
- Read pre-version `film_layout`, `particle_layout`, `film_editor_layouts`,
  `film_editor_layout_bindings` and old ratios. Retain camera/replay/action data
  even while its editor shares the main layout.
- `film_views`, hidden panels, unlocked docks and remembered split positions
  retain their FSR meaning. `splits` is canonical; `split_sizes` remains a read alias.
- Legacy Film per-editor public accessors intentionally keep inherited independent
  tree behavior. Active sources use the generic API, not those adapters.
- Dock splitter writes create one replacement root and call `setRoot` once.
  `UIDockSlot` owns its 20px header and content sizing; child panels must not add
  another title offset. Legacy offset methods may remain zero-valued adapters.

### 4. Validation & Error Matrix

- Bad tree -> discard it or salvage a valid subtree; owner supplies its own default.
- Unknown layout id -> preserve its tree using the generic map.
- Missing/non-string bound or hidden id -> ignore it.
- Nonnumeric split size -> ignore it; reload clears prior remembered values.
- Inspector remount/resize -> resize its actual target after assigning layout rules.

### 5. Good/Base/Bad Cases

- Good: unbound Film tabs share Dock edits; binding creates an independent layout.
- Base: legacy action layout remains serialized while dormant.
- Bad: active Film selects its editor id unconditionally or Dock mutates a detached
  splitter directly without writing the replacement root.

### 6. Tests Required

Use existing `testFilmEditorMigration`, `testUiInputMailbox` and `apiCheck` to
detect regressions. Manually verify bind/unbind, save/reload, hide/show, split drag,
20px header alignment, and Particle defaults. Do not edit tests for this task.

### 7. Wrong vs Correct

Wrong: `getFilmLayoutRoot(editor)` supplies the active layout and bypasses binding.
Correct: active Film reads and writes the generic id chosen by `isBound`.

## Scenario: UI pixel smoothing

### 1. Scope / Trigger

Changes to fractional UI scaling, `BBSShaders`, `PixelArt`, `UIScreen`,
`Batcher2D` or GameRenderer text hooks affect texture/program selection together.

### 2. Signatures

- `BBSSettings.pixelArtSmoothing` is `appearance.pixel_art_smoothing`, default true.
- `BBSShaders.getPixelArtProgram/getPixelArtTextProgram/getPixelArtTextIntensityProgram`.
- `PixelArt.setDrawingUI(boolean)` / `getTextProgram(boolean intensity)`.

### 3. Contracts

- Three optional programs use the ten `core/pixelart*` and `include/bbs_pixelart.glsl`
  resources. Include lookup prefers BBS resources and falls back to vanilla fog/light.
- Optional loading failure closes and clears partial programs; null selects vanilla.
- UIScreen brackets its existing mirror-aware render with the UI text flag and
  clears it in finally. Ordinary world text keeps vanilla programs.
- Textured primitives honor explicit linear/mipmap filtering. Null/raw native
  surfaces and native mirrored submissions retain their existing fallback path.
- The setting changes reconstruction, not theme colors or UI geometry.

### 4. Validation & Error Matrix

- Smoothing disabled or missing program -> vanilla shader.
- Resource reload fails midway -> no closed shader field remains available.
- Screen render throws -> text flag cleared.
- Vanilla include requested -> resolve it through the original resource manager.

### 5. Good/Base/Bad Cases

- Good: nearest-filter icons remain readable at fractional scale.
- Base: linear texture filtering stays user-controlled.
- Bad: global text getters always return the BBS shader, including world labels.

### 6. Tests Required

Run existing `compileClientJava`, `testThemeCore`, `testUiInputMailbox`,
`testPlatformResourceConsistency` and `apiCheck`. Inspect packaged shader bytes.
Manually verify scales, reload, world labels and native/mirrored UI. No new tests.

### 7. Wrong vs Correct

Wrong: replace every vanilla text program while the setting is enabled.
Correct: text replacement requires the scoped UI flag and a live optional program.

## Scenario: Explicit isolated form-editor translucent preview

### 1. Scope / Trigger

`UIPickableFormRenderer.renderUserModel` needs sorting before its preview camera
is restored. The default world queue flush boundary cannot serve this UI draw.

### 2. Signatures

- `FormTranslucentQueue.openPreview()` returns `PreviewScope implements AutoCloseable`.
- `PreviewScope.flush()` draws local commands while the preview camera is active.
- `FormRenderingContext.canDeferWorldTranslucency()` admits the narrow exception.

### 3. Contracts

- Only an explicitly open, active scope admits `PREVIEW && modelRenderer && !ui
  && !picking`. Picking, thumbnails and unscoped previews remain immediate.
- The scope parks outer commands, group, origin, active state, suspension depth
  and any outer scope. Close discards pending local commands and restores all
  outer state in finally; it never flushes or releases parked world commands.
- Normal form draw opens the scope and flushes before axes, picking or camera
  restoration. Nested framebuffer draws suspend the queue and cannot lazily restart it.
- Capture draw/read framebuffer bindings and cull/blend/depth flags, depth mask,
  depth function and separate blend factors. Restore on close.
- `flush()` reasserts the preview target before drawing. Fabulous world-main target
  rebinding only runs with no preview scope. `RenderLayerCommand` restores the
  preview target after vanilla layer setup and in layer-clear finally.
- Preserve FSR texture-alpha passes, per-command blend resets, camera-space sort,
  resource ownership, glint and overlay handling.

### 4. Validation & Error Matrix

- Setting disabled -> immediate draw, with scope cleanup still restoring outer state.
- Render/flush exception -> discard locals, restore outer queue and captured state.
- Nested suspension -> no queue restart until owner restores suspension.
- Vanilla output shard changes target -> reassert captured draw/read bindings.

### 5. Good/Base/Bad Cases

- Good: preview transparency sorts locally while the pending world queue survives.
- Base: world forms still flush at the world translucent stage.
- Bad: preview calls `begin()` on the world queue or Fabulous redirects it to main.

### 6. Tests Required

Run existing `compileClientJava`, `testClientStructuralRegistration`,
`testFilmEditorMigration`, `testReplayKeyframeConsistency`, `testUiInputMailbox`
and integrated `check/build/apiCheck`. Real acceptance must include Fabulous,
mirrored viewport, nested framebuffer forms, picking and Iris. No new tests.

### 7. Wrong vs Correct

Wrong: open the global frame queue for every non-picking model preview.
Correct:

```java
try (FormTranslucentQueue.PreviewScope preview = FormTranslucentQueue.openPreview())
{
    FormUtilsClient.render(this.form, formContext);
    preview.flush();
}
```

The caller owns its active preview camera across the complete block.
