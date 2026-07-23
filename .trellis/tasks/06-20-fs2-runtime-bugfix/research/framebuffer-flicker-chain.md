# Research: Framebuffer Flicker, Black Screen, and Film Loss Root Cause Analysis

- **Query**: Pinpoint code differences between new NeoForge build and reference Fabric 1.21.1 causing framebuffer flicker after placing replay target, black screen on exit, ESC hang, and film project loss
- **Scope**: Internal (code comparison) + architecture analysis
- **Date**: 2026-06-20

---

## Findings

### 1. setCustomSize / toggleFramebuffer Flow Differences

#### **Files Involved**
| File | Purpose |
|---|---|
| `new/src/client/java/mchorse/bbs_mod/client/BBSRendering.java` | NeoForge: Enhanced version with detailed diagnostics |
| `temp/bbs-fs-1.21.1-ui/src/client/java/mchorse/bbs_mod/client/BBSRendering.java` | Fabric 1.21.1: Minimal version |

#### **Critical Difference 1: setCustomSize Diagnostics (lines 167-206 new vs 164-177 ref)**

**NEW (NeoForge) - BBSRendering.java:167-206**:
```java
public static void setCustomSize(boolean customSize, int w, int h)
{
    if (BBSFlickerDiagnostics.ENABLED)
    {
        boolean previousCustomSize = BBSRendering.customSize;
        int previousWidth = width;
        int previousHeight = height;
        String trace = stackSummary(3, 5);
        String signature = previousCustomSize + "|" + previousWidth + "x" + previousHeight
            + ">" + customSize + "|" + (customSize ? w : 0) + "x" + (customSize ? h : 0)
            + "|" + toggleFramebuffer + "|" + renderingWorld + "|" + trace;

        if (!signature.equals(lastCustomSizeSignature))
        {
            LOGGER.info("[BBS-FLICKER] rendering.setCustomSize from={} to={} prevSize={}x{} nextSize={}x{} toggleFramebuffer={} renderingWorld={} caller={}",
                previousCustomSize, customSize, previousWidth, previousHeight, customSize ? w : 0, customSize ? h : 0, toggleFramebuffer, renderingWorld, trace);
            lastCustomSizeSignature = signature;
        }

        if (!trace.equals(lastCustomSizeTrace))
        {
            LOGGER.info("[BBS-FLICKER-STACK] rendering.setCustomSize caller={}", trace);
            lastCustomSizeTrace = trace;
        }
    }

    width = !customSize ? 0 : w;
    height = !customSize ? 0 : h;
    BBSRendering.customSize = customSize;

    if (!customSize)
    {
        resizeExtraFramebuffers();
    }
}
```

**REFERENCE (Fabric) - BBSRendering.java:164-177**:
```java
public static void setCustomSize(boolean customSize, int w, int h)
{
    LOGGER.info("[BBS film] setCustomSize customSize={} w={} h={} (stored width/height will be {})",
        customSize, w, h, customSize ? w + "/" + h : "0/0");
    BBSRendering.customSize = customSize;

    width = !customSize ? 0 : w;
    height = !customSize ? 0 : h;

    if (!customSize)
    {
        resizeExtraFramebuffers();
    }
}
```

**Analysis**: The NeoForge version added extensive diagnostics but they're debug-level logging only. The core logic is identical. **NOT the root cause.**

---

#### **Critical Difference 2: toggleFramebuffer Logging & State Management (lines 312-379 new vs 269-320 ref)**

**NEW (NeoForge) - BBSRendering.java:312-379** adds:
- `toggleFramebufferChangeCount` tracking (line 324)
- Detailed logging of framebuffer state transitions (lines 330-337)
- Diagnostic condition checks only in ENABLED blocks

**REFERENCE (Fabric) - BBSRendering.java:269-320** uses:
- No change counter
- Minimal logging
- Clean state management

**Analysis**: Again, diagnostics only. Core logic (`bindWrite(true)` vs `frameBufferId` binding) is structurally identical but uses NeoForge APIs (`RenderTarget` vs Fabric `Framebuffer`). **NOT the flicker cause.**

---

#### **Critical Difference 3: onRenderBeforeScreen Copy Logic - THIS IS THE BUG**

**NEW (NeoForge) - BBSRendering.java:457-552** - 95 LINES:
```java
public static void onRenderBeforeScreen()
{
    boolean wasFramebufferActive = toggleFramebuffer;

    if (BBSFlickerDiagnostics.ENABLED && (!customSize || !toggleFramebuffer))
    {
        inactivePreviewCopyCount += 1;
        // ... diagnostics ...
    }

    if (!customSize || !toggleFramebuffer)
    {
        return;  // ⚠️ EARLY RETURN - skips texture copy if not both flags true
    }

    if (framebuffer == null)
    {
        // ... handle null ...
        return;
    }

    Texture texture = getTexture();
    int targetWidth = getVideoWidth();
    int targetHeight = getVideoHeight();
    boolean resizedTexture = texture.width != targetWidth || texture.height != targetHeight;

    if (resizedTexture)
    {
        texture.bind();
        texture.setSize(targetWidth, targetHeight);
        texture.unbind();
    }

    // ⚠️ FRAMEBUFFER MANAGEMENT: Complex FBO bind/unbind pattern
    if (exportFramebuffer == null)
    {
        exportFramebuffer = new mchorse.bbs_mod.graphics.Framebuffer();
        exportFramebuffer.attach(texture, GL30.GL_COLOR_ATTACHMENT0);
        exportFramebuffer.unbind();
    }

    int previousReadFramebuffer = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    int previousDrawFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

    renderCopyCount += 1;
    // ... diagnostics ...

    // ⚠️ Manual FBO management with GL30 binds
    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.frameBufferId);
    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, exportFramebuffer.id);
    GL30.glBlitFramebuffer(
        0, 0, framebuffer.width, framebuffer.height,
        0, 0, targetWidth, targetHeight,
        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
    );

    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);

    if (wasFramebufferActive)
    {
        toggleFramebuffer(false);  // ⚠️ This calls toggleFramebuffer which does bindWrite
    }
    // ... handle pending action ...
}
```

**REFERENCE (Fabric) - BBSRendering.java:383-400** - 17 LINES:
```java
public static void onRenderBeforeScreen()
{
    Texture texture = getTexture();

    // ⚠️ Simple, direct texture copy - no FBO management
    texture.bind();
    texture.setSize(framebuffer.textureWidth, framebuffer.textureHeight);
    GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
    texture.unbind();

    // ⚠️ ALWAYS toggles off - no conditional check
    toggleFramebuffer(false);

    if (pendingExportResolutionAction != null)
    {
        Runnable action = pendingExportResolutionAction;
        pendingExportResolutionAction = null;
        MinecraftClient.getInstance().execute(action);
    }
}
```

**Root Cause Analysis**:
1. **New NeoForge logic**:
   - Creates an intermediate `exportFramebuffer` (custom mchorse Framebuffer, not RenderTarget)
   - Uses complex GL30 FBO binding: `GL_READ_FRAMEBUFFER` from main, `GL_DRAW_FRAMEBUFFER` to export
   - Uses `glBlitFramebuffer` (GPU-to-GPU copy)
   - Only toggles off if `wasFramebufferActive` was true
   - **Flicker cause**: Line 543 `toggleFramebuffer(false)` called AFTER blit completes, but:
     - If frame 1: `toggleFramebuffer=true` → blit → toggleFramebuffer(false) → OK
     - If frame 2: `toggleFramebuffer=false` (already off) → no blit → doesn't toggle (wasFramebufferActive=false)
     - If frame 3: `toggleFramebuffer=true` (re-enabled by applyPreviewSizeToBBS) → blit → toggleFramebuffer(false)
     - **Result**: Rapid on/off cycling = **FLICKER**

2. **Reference Fabric logic**:
   - Uses simple CPU-side texture copy: `glCopyTexSubImage2D`
   - **Always** calls `toggleFramebuffer(false)` unconditionally
   - No conditional guard means predictable state reset per frame

**Black Screen Root Cause**:
- When exiting film panel, `UIFilmPanel.close()` (line 2442) calls `BBSRendering.setCustomSize(false)`
- If the last `onRenderBeforeScreen()` didn't toggle framebuffer off because `wasFramebufferActive==false`:
  - Main render target is still pointing to custom framebuffer
  - Rendered world goes to non-existent/unbound FBO
  - Screen stays black

---

### 2. applyPreviewSizeToBBS Trigger Point Analysis

#### **NEW (NeoForge) - UIFilmPanel.java:1939-2029**

Method signature changed to accept `source` parameter (debug tracking):
```java
private void applyPreviewSizeToBBS(String source)
{
    if (!this.hasFilmInCurrentTab() || this.data == null)
    {
        if (BBSRendering.isCustomSize())
        {
            BBSRendering.flushCustomFramebuffer();  // ⚠️ NEW: calls onRenderBeforeScreen()
            BBSRendering.setCustomSize(false);
        }
        // ... diagnostics ...
        return;
    }
    
    if (this.recorder.isExporting())
    {
        // ... skip ...
        return;
    }

    // ... size calculation ...

    boolean apply = !BBSRendering.isCustomSize() || w != BBSRendering.getVideoWidth() || h != BBSRendering.getVideoHeight();
    
    if (apply)
    {
        BBSRendering.setCustomSize(true, w, h);
    }
}
```

Key calls:
- Line 520: `settingsCallback` (settings change)
- Line 1107: `splitterDragEnd` (UI resize)
- Line 1858: `resize` (preview area changed)
- Line 1916: `restorePreviewSize`
- Line 1925: `direct` (layout changes)
- Line 2106: `showPanel`
- Line 2416: `appear` (panel becomes visible)

#### **REFERENCE (Fabric) - UIFilmPanel.java:2005-2062**

No `source` parameter - simpler debug:
```java
private void applyPreviewSizeToBBS()
{
    if (this.recorder.isExporting())
    {
        return;
    }

    // ... size calculation, no early return on !hasFilm ...

    boolean applied = w != BBSRendering.getVideoWidth() || h != BBSRendering.getVideoHeight();
    LOGGER.info("[BBS film] applyPreviewSizeToBBS mode={} cameraEditor={} -> w={} h={} applied={}",
        previewMode, this.cameraEditor.isVisible(), w, h, applied);

    if (applied)
    {
        BBSRendering.setCustomSize(true, w, h);
    }
}
```

**Key Difference**: 
- **New**: Calls `flushCustomFramebuffer()` when no film is active → forces immediate blit + toggleFramebuffer(false)
- **Reference**: Silently skips if no film, no flush call

**Flicker Cascade**:
1. User places replay target (applyPreviewSizeToBBS called with "appear")
2. `setCustomSize(true, w, h)` sets customSize=true
3. Next frame: `onWorldRenderBegin()` sees customSize=true → calls `toggleFramebuffer(true)` (line 415)
4. Frame renders with custom framebuffer
5. `onRenderBeforeScreen()` called → saves texture from custom FB → toggleFramebuffer(false)
6. Another UI event triggers `applyPreviewSizeToBBS()` with different size
7. Calls `flushCustomFramebuffer()` → **toggleFramebuffer(false) called again while already false**
8. Then `setCustomSize(true, new_w, new_h)`
9. Next frame: toggleFramebuffer(true) again
10. **Result**: Rapid on/off cycles = **FLICKER**

---

### 3. Exit Path State Reset Issues

#### **NEW (NeoForge) - UIFilmPanel.java:2427-2477**

```java
@Override
public void close()
{
    if (this.queueExporter != null)
    {
        this.queueExporter.cancel();
    }

    if (BBSFlickerDiagnostics.ENABLED)
    {
        LOGGER.info("[BBS-FLICKER] film.close film={} selected={} customSize={} previewArea={}x{}",
            this.getFilmDebugId(), this.editorName(this.selectedMainEditorPanel), BBSRendering.isCustomSize(), this.preview.area.w, this.preview.area.h);
    }

    super.close();
    BBSRendering.flushCustomFramebuffer();  // ⚠️ Forces blit + toggleFramebuffer(false)
    BBSRendering.setCustomSize(false);
    MorphRenderer.hidePlayer = false;

    CameraController cameraController = this.getCameraController();

    this.cameraEditor.embedView(null);
    this.setFlight(false);
    cameraController.remove(this.runner);

    this.disableContext();
    this.replayEditor.close();

    this.notifyServer(ActionState.STOP);
}

@Override
public void disappear()
{
    super.disappear();

    if (BBSFlickerDiagnostics.ENABLED)
    {
        LOGGER.info("[BBS-FLICKER] film.disappear film={} selected={} customSize={} previewArea={}x{}",
            this.getFilmDebugId(), this.editorName(this.selectedMainEditorPanel), BBSRendering.isCustomSize(), this.preview.area.w, this.preview.area.h);
    }

    BBSRendering.flushCustomFramebuffer();  // ⚠️ Forces blit + toggleFramebuffer(false)
    BBSRendering.setCustomSize(false);
    MorphRenderer.hidePlayer = false;

    this.setFlight(false);
    this.getCameraController().remove(this.runner);

    this.disableContext();
    this.secretPlay.removeFromParent();
}
```

#### **REFERENCE (Fabric) - UIFilmPanel.java:2499-2536**

```java
@Override
public void close()
{
    if (this.queueExporter != null)
    {
        this.queueExporter.cancel();
    }

    super.close();

    BBSRendering.setCustomSize(false);  // ⚠️ Direct reset, no flush
    MorphRenderer.hidePlayer = false;

    CameraController cameraController = this.getCameraController();

    this.cameraEditor.embedView(null);
    this.setFlight(false);
    cameraController.remove(this.runner);

    this.disableContext();
    this.replayEditor.close();

    this.notifyServer(ActionState.STOP);
}

@Override
public void disappear()
{
    super.disappear();

    BBSRendering.setCustomSize(false);  // ⚠️ Direct reset, no flush
    MorphRenderer.hidePlayer = false;

    this.setFlight(false);
    this.getCameraController().remove(this.runner);

    this.disableContext();
    this.secretPlay.removeFromParent();
}
```

**Root Cause Chain**:
1. New version adds `flushCustomFramebuffer()` calls in close/disappear paths
2. Flush triggers `onRenderBeforeScreen()` early in exit sequence
3. If `toggleFramebuffer` was already false (due to flicker), flush does nothing
4. But render target might not be reassigned correctly
5. ESC key (client-side disconnect) may not restore framebuffer state synchronously
6. **Result**: Render target stays bound to custom/dead framebuffer → black screen or hang

---

### 4. Film Persistence / Data Loss Issue

#### **Film List Data Source**

Searched for film list initialization and persistence. The film data is managed by:
- `BBSModClient.getFilms()` → returns `Films` instance
- `UIFilmPanel extends UIDataDashboardPanel<Film>`
- Parent class handles data loading from disk

**No explicit difference found in:**
- Save paths (`Films.java`)
- Data serialization
- Load timing in UIFilmPanel

**However, the flicker + black screen sequence can cause**:
1. User opens film panel (loads film from disk)
2. Framebuffer flicker occurs (rendering instability)
3. User presses ESC to exit or film panel crashes
4. Exit sequence called but render target state is corrupted
5. Application state becomes unstable
6. If the exit sequence doesn't properly save film state before clearing context:
   - `this.disableContext()` (line 2451 new vs 2517 ref) shuts down context
   - Any pending saves might be lost if context shutdown flushes pending operations

**Suspected path**: Exit doesn't occur cleanly due to black screen hang → user force-closes → film.json never written back.

---

### 5. Key State Machine Issues

**New NeoForge Implementation Problems**:

```
State tracking variables added (no matching in Fabric):
- toggleFramebufferChangeCount (line 89)
- renderCopyCount (line 88)
- inactivePreviewCopyCount (line 91)
- lastCustomSizeTrace, lastCustomSizeSignature (lines 92-93)
- lastToggleTrace (line 94)
- exportFramebuffer (line 85) - separate from framebuffer/clientFramebuffer
```

**Problem**: More state = more opportunity for desync:

| Scenario | New NeoForge | Reference Fabric |
|----------|---|---|
| **Frame 1: Enter film** | toggleFramebuffer=false → appears → setCustomSize(true) | setCustomSize(true) |
| **Frame 2: worldRenderBegin** | toggleFramebuffer(true) called | toggleFramebuffer(true) called |
| **Frame 3: onRenderBeforeScreen** | IF wasFramebufferActive=true: blit then toggleFramebuffer(false) | glCopyTexSubImage2D then toggleFramebuffer(false) |
| **Frame 4: UI event (resize)** | flushCustomFramebuffer() → onRenderBeforeScreen() → wasFramebufferActive=false → NO blit → NO toggle | (method doesn't exist in Fabric - handled differently) |
| **Frame 5: UI event** | Same state (toggleFramebuffer=false) repeated | N/A |
| **Frame 6: worldRenderBegin again** | toggleFramebuffer(true) → renders → toggleFramebuffer(false) → toggleFramebuffer(true) again | Consistent behavior |

**The fundamental bug**: The `wasFramebufferActive` save-and-restore pattern (line 459) is **WRONG**:
- It assumes if framebuffer was active at entry, toggle it off at exit
- But if another caller (like `flushCustomFramebuffer`) already toggled it, the conditional skip causes desync
- **Fabric never had this problem**: Always toggles off regardless

---

## Caveats / Not Found

1. **Film loss confirmation**: Could not directly confirm in code that films are deleted. Likely scenario is:
   - Exit crash/hang → user force-closes Minecraft
   - Film.json never written from in-memory changes
   - On restart, old version loaded from disk (or blank list if file got corrupted)

2. **ESC hang mechanism**: Exact code path not found but suspected:
   - ESC triggers closeScreen()
   - which calls UIFilmPanel.close()
   - which calls flushCustomFramebuffer() → onRenderBeforeScreen()
   - which tries to access framebuffer while render thread is in different state
   - Dead-lock or race condition possible in toggleFramebuffer() bindWrite calls

3. **BBSFlickerDiagnostics class**: Exists in new version but not found in reference. Suggests this is NEW debugging code added to isolate the flicker problem, meaning flicker existed before diagnostics.

---

## Root Cause Summary

**Primary Bug**: `BBSRendering.onRenderBeforeScreen()` in NeoForge (new) uses conditional `toggleFramebuffer(false)` based on `wasFramebufferActive` flag, which can cause:

1. **Flicker**: Rapid on/off cycling when UI calls `flushCustomFramebuffer()` during resize/events
2. **Black Screen**: Framebuffer not reset to client when exiting, render target stays bound to dead FBO
3. **ESC Hang**: State corruption during exit sequence; toggleFramebuffer calls in wrong order
4. **Film Loss**: Indirect; exit crash prevents clean shutdown and file save

**Fix Direction**: 
- Remove conditional toggle logic
- **Always** call `toggleFramebuffer(false)` unconditionally in onRenderBeforeScreen (like Fabric reference)
- Remove `wasFramebufferActive` flag entirely
- Simplify exportFramebuffer blit to use Fabric-style glCopyTexSubImage2D if available, OR ensure GL state is properly saved/restored around manual FBO binds
- Ensure exit paths call `setCustomSize(false)` WITHOUT `flushCustomFramebuffer()` to prevent toggle during shutdown

**Files to Modify**:
- `new/src/client/java/mchorse/bbs_mod/client/BBSRendering.java` (lines 457-552: onRenderBeforeScreen)
- `new/src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java` (lines 2441-2469: close/disappear)
- Consider removing `exportFramebuffer` and `flushCustomFramebuffer()` method entirely if it's not critical to export pipeline

