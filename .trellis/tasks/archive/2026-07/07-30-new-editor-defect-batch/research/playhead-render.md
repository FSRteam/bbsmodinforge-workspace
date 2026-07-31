# Research: Animation State Editor Playhead Render Defect

- **Query**: Animation state editor timeline playhead rendering fails; playhead line not visible in graph area
- **Scope**: Internal code analysis
- **Date**: 2026-07-30

## Findings

### Root Cause

**UIAnimationStateKeyframes renders the playhead in `renderBackground()` (step 4 of the render pipeline), but the dope sheet's `renderGraph()` (step 5) draws opaque row background rectangles that overwrite the 1px playhead vertical line.**

### Rendering Pipeline (UIKeyframes.render(), line 1672-1699)

The render order in `UIKeyframes.render()` is:

```
Step 1: super.render(context)                       — standard UIElement rendering
Step 2: handleMouse(context)
Step 3: context.batcher.clip(this.area, context)    — clip to whole control area (label + graph)
Step 4: this.renderBackground(context)              — calls overridden subclass method
Step 5: this.currentGraph.render(context)           — dope sheet / curve editor
Step 6: this.currentGraph.postRender(context)       — label rendering
Step 7: this.renderOverlay(context)                 — topmost keyframe shapes
Step 8: context.batcher.unclip(context)
```

### What Goes Wrong

**UIAnimationStateKeyframes.renderBackground()** (file: `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/states/keyframes/UIAnimationStateKeyframes.java`, lines 62-73) draws the playhead at **Step 4**:

```java
@Override
protected void renderBackground(UIContext context)
{
    super.renderBackground(context);          // Step 4a: draws area + graphArea backgrounds

    if (this.editor != null)
    {
        int cx = this.toGraphX(this.getOffset());
        String label = TimeUtils.formatTime(this.getOffset()) + "/" + TimeUtils.formatTime(this.getDuration());
        UIClips.renderCursor(context, label, this.area, cx - 1);   // Step 4b: playhead line + label
    }
}
```

Then at **Step 5**, `UIKeyframeDopeSheet.renderGraph()` (file: `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/graphs/UIKeyframeDopeSheet.java`, lines 918-938) calls `renderElements()` which calls `renderSheet()` for each keyframe channel row. **Line 1166** draws a fully opaque background rectangle across the entire graph area width for each row:

```java
// UIKeyframeDopeSheet.java line 1166
context.batcher.box(area.x, by, area.ex(), by + bh, surface);
```

Where `area` is `graphArea` and `surface` is either `BBSSettings.deepSurface()` or `BBSSettings.baseSurface()` — **fully opaque colors**. These rectangles overwrite any 1px playhead vertical line at the same position.

Even the `renderGroup()` method at line 1129 does the same:

```java
context.batcher.box(area.x, by, area.ex(), by + bh, surface);
```

**Result**: The playhead vertical line is drawn but immediately covered by the dope sheet row backgrounds. The playhead label (text card) may still be partially visible if it extends above the row content area (at the ruler/timeline header), but the vertical cursor line spanning the full graph height is invisible.

### Correct Implementation (UIFilmKeyframes)

**UIFilmKeyframes** (file: `new/src/client/java/mchorse/bbs_mod/ui/film/utils/keyframes/UIFilmKeyframes.java`, lines 108-121) renders the playhead in `renderOverlay()` at **Step 7** — after all graph content:

```java
@Override
protected void renderOverlay(UIContext context)
{
    if (this.editor != null)
    {
        int cx = this.toGraphX(this.getOffset());
        String label = TimeUtils.formatTime(this.getOffset()) + "/" + TimeUtils.formatTime(this.getDuration());

        context.batcher.clip(this.graphArea, context);    // extra: restrict to graph area
        UIClips.renderCursor(context, label, this.area, cx - 1);
        context.batcher.unclip(context);
    }

    super.renderOverlay(context);                          // renders topmost keyframes on top of playhead
}
```

Key differences:
1. **Render timing**: `renderOverlay()` (Step 7) vs `renderBackground()` (Step 4) — the playhead is drawn AFTER `currentGraph.render()` (Step 5), so row backgrounds cannot cover it.
2. **Extra clip**: Wraps `renderCursor` with `context.batcher.clip(this.graphArea, context)` / `unclip` — prevents the playhead from bleeding into the label (channel name) column when offset is near 0.

### Additional Observations

#### a) `clip(this.area)` vs `clip(graphArea)`
- `UIKeyframes.render()` sets `clip(this.area)` at line 1678 — this clips to the **entire** control area, including the label column.
- The playhead's x coordinate `toGraphX(getOffset())` is calculated using `xAxis` (defined as `new Scale(this.graphArea, ...)` at line 88), so the result is an absolute screen coordinate within `graphArea`.
- Without an additional `clip(graphArea)`, when the cursor/offset is 0, `cx = graphArea.x` and the line at `cx - 1` would be drawn **into the label column** (1 pixel left of graphArea). UIFilmKeyframes prevents this with `clip(graphArea)`.

#### b) UIKeyframes.renderBackground() (lines 1780-1812)
- Draws `baseSurface` over the entire `area`, then `deepSurface` over `graphArea` (lines 1782-1783).
- Draws `chromeSurface` on the "before tick 0" area (lines 1789-1796).
- These backgrounds are drawn in `super.renderBackground(context)` BEFORE `UIClips.renderCursor()` in UIAnimationStateKeyframes, so the playhead is on top of the initial backgrounds. However, it is NOT on top of the row backgrounds drawn in Step 5.

#### c) graphArea setup in resize() (UIKeyframes.java lines 1205-1238)
- `labelWidth` is subtracted from `area` to create `graphArea`: `graphArea.x = area.x + labelWidth; graphArea.w -= labelWidth` (lines 1223-1225).
- `toGraphX()` uses the `xAxis` Scale which is based on `graphArea`, so coordinates are correct.
- The problem is not coordinate calculation — it's render ordering.

#### d) Duration handling
- `UIAnimationStateKeyframes.getOffset()` returns `this.editor.getCursor()` directly (the raw cursor tick), without subtracting a clip offset like UIFilmKeyframes does. This is domain-appropriate (state editor vs clip editor) and does not contribute to the render defect.

### Summary

| Aspect | UIAnimationStateKeyframes (broken) | UIFilmKeyframes (correct) |
|---|---|---|
| **Method** | `renderBackground()` | `renderOverlay()` |
| **Render Step** | Step 4 | Step 7 |
| **Extra clip** | None | `clip(graphArea)` / `unclip` |
| **Keyframe order** | Playhead BEFORE graph rows | Playhead AFTER graph rows |

### Not Found / Not Verified

- Whether the curve editor (non-dope-sheet) graph type has the same row background drawing behavior was not fully checked, but the render ordering problem would apply regardless of graph type since `currentGraph.render()` always runs after `renderBackground()`.

## Fix Recommendation

### File to modify
`C:\Users\McAxolotl\Documents\FSRteam\bbsmodinforge-workspace\new\src\client\java\mchorse\bbs_mod\ui\forms\editors\states\keyframes\UIAnimationStateKeyframes.java`

### What to change
Move the playhead rendering logic from `renderBackground()` to `renderOverlay()`, following the same pattern as `UIFilmKeyframes.renderOverlay()`.

### Specific changes

1. **Remove** the playhead rendering block from `renderBackground()` (current lines 66-72). Keep the `super.renderBackground(context)` call.

2. **Add** a `renderOverlay()` override:

```java
@Override
protected void renderOverlay(UIContext context)
{
    if (this.editor != null)
    {
        int cx = this.toGraphX(this.getOffset());
        String label = TimeUtils.formatTime(this.getOffset()) + "/" + TimeUtils.formatTime(this.getDuration());

        context.batcher.clip(this.graphArea, context);
        UIClips.renderCursor(context, label, this.area, cx - 1);
        context.batcher.unclip(context);
    }

    super.renderOverlay(context);
}
```

### Reference implementation
See `UIFilmKeyframes.renderOverlay()` at:
`new/src/client/java/mchorse/bbs_mod/ui/film/utils/keyframes/UIFilmKeyframes.java` lines 108-121.
