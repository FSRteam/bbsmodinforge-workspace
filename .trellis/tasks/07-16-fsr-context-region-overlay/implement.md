# FSR Context Region Overlay — Implementation Plan

## Ordered Checklist

1. Scaffold `bbs-context-hints/` from the smallest proven NeoForge addon pattern in `bbs-structure-addon/`, while reusing the explicit local-FSR-jar verification from `bbs-web-remote/`.
2. Configure project metadata:
   - mod id `bbscontexthints`;
   - archive name `bbs-context-hints`;
   - Minecraft 1.21.1, NeoForge 21.1.222, Java 21;
   - required client dependency on FSR/BBS 2.3.1+.
3. Add the NeoForge entrypoint and client-only bootstrap with safe distribution guards.
4. Register a rebindable held-state key mapping, proposed default F7, with English and Simplified Chinese localization.
5. Implement an immutable `ContextRegion` value object and pure rectangle clipping/de-duplication helpers.
6. Implement `FsrContextRegionScanner`:
   - obtain the current `UIBaseMenu` from `UIScreen`;
   - traverse all `UIElement` descendants;
   - filter visibility/enabled state;
   - probe `createContextMenu(menu.context)` and require a non-empty menu;
   - transform and clip areas with `UIViewportStack.fromElement`;
   - isolate per-element failures and rate-limit logs.
7. Implement a short-lived scan cache keyed by screen/menu/root identity, dimensions, and refresh tick.
8. Implement the screen render-post overlay:
   - poll held key state;
   - scan only on FSR screens;
   - draw translucent interiors and crisp borders;
   - consume no input and retain no state after release.
9. Add dependency-free regression tests for clipping and de-duplication, and wire them into Gradle `check`.
10. Build and run the addon with the adjacent FSR jar.
11. Execute the manual UI matrix and tune color/alpha/line thickness only if readability requires it.
12. Run the Trellis quality gate before implementation handoff or commit.

## Validation Commands

From `new/`:

```powershell
.\gradlew.bat build
```

From the future `bbs-context-hints/` directory:

```powershell
.\gradlew.bat clean build
.\gradlew.bat check
```

If a client run configuration is added:

```powershell
.\gradlew.bat runClient
```

## Manual Verification Matrix

- Hold F7 on a non-FSR Minecraft screen: no overlay.
- Hold F7 on each representative FSR editor: all active context-menu regions are framed.
- Release F7: every frame disappears immediately.
- Right-click a highlighted region while holding F7: the original context menu still opens.
- Test scroll views at top, middle, and bottom positions: rectangles remain clipped and aligned.
- Open and close nested overlays/context menus: stale regions disappear on refresh.
- Change GUI scale and resize the window: rectangles follow the controls.
- Switch between FSR panels without releasing the key: scan cache invalidates correctly.
- Verify empty/dynamic menus are not highlighted.

## Risk and Rollback Points

- If `createContextMenu` probing has side effects or excessive cost, stop before shipping and replace candidate discovery with an accessor Mixin plus explicit handling for the `UIReplayList` override.
- If `KeyMapping.isDown()` does not update while an FSR screen owns keyboard input, retain the registered mapping for rebinding but poll its bound physical key through Minecraft/GLFW during render.
- If render-post coordinates differ from FSR coordinates under GUI scaling, move the renderer into a non-interactive FSR overlay element and reuse the same scanner output.
- Do not modify `new/` unless the user separately approves an upstream API change.

## Review Gate Before `task.py start`

- User approves the directory/name or supplies a replacement.
- User approves the proposed default key F7, or accepts it as freely rebindable.
- PRD, design, and implementation plan remain consistent with context-menu-only scope.

## Implementation Result — 2026-07-16

- Created the standalone `bbs-context-hints/` NeoForge addon.
- Implemented a rebindable F7 held-state key, public FSR context-menu probing,
  viewport-aware rectangle clipping, near-duplicate reduction, and a
  non-interactive screen render-post overlay.
- Added English and Simplified Chinese key/category resources.
- Added pure Java regression coverage for intersection, invalid filtering,
  exact duplicates, near duplicates, and distinct nested regions.
- Verified with `gradlew.bat clean check build --no-daemon --console=plain`.
- Verified the final JAR contains the common/client entrypoints, scanner,
  overlay, expanded NeoForge metadata, and both language files with no duplicate
  ZIP entries.
- Verified a hidden `runClient` smoke launch accepts the API 2.0 addon and logs
  `[bbscontexthints] client overlay initialized`.
- Manual interaction inside representative FSR editors remains a user-facing
  visual check because the automated smoke run does not navigate the UI.
