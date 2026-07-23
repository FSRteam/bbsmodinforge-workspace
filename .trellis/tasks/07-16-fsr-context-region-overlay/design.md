# FSR Context Region Overlay — Technical Design

## Architecture and Boundaries

Create a standalone client-side NeoForge addon in a new root directory named `bbs-context-hints/`.

The addon will:

- target Minecraft 1.21.1, NeoForge 21.1.222, and Java 21;
- compile against the adjacent local FSR artifact `../new/build/libs/bbs-2.3.1.jar`;
- declare a required client-side dependency on the `bbs` mod;
- use public FSR UI APIs first and avoid changes to `new/` for the MVP;
- own only discovery and rendering of hints, never context-menu actions or input dispatch.

Suggested packages:

- `mchorse.bbs_context_hints` — NeoForge entrypoint and bootstrap;
- `mchorse.bbs_context_hints.client` — client lifecycle and key mapping;
- `mchorse.bbs_context_hints.client.discovery` — context-region scanning;
- `mchorse.bbs_context_hints.client.render` — rectangle normalization and drawing.

## Runtime Data Flow

1. Register a rebindable `KeyMapping`, with F7 as the proposed default because the inspected FSR bindings already use F1, F3–F6, F8, and F10 for major UI features.
2. Listen to the client screen render-post event.
3. Return immediately unless:
   - the key mapping is currently held;
   - the current screen is an FSR `UIScreen`;
   - the current FSR menu and root element are available.
4. Obtain the active `UIBaseMenu` through `UIScreen.getCurrentMenu()` and traverse its public `UIElement` tree.
5. For each visible and enabled element:
   - call the public `createContextMenu(UIContext)` method;
   - treat only a non-null, non-empty result as an active right-click menu region;
   - never install, open, or execute the returned menu.
6. Convert the element's `Area` into screen coordinates using `UIViewportStack.fromElement(element)`, applying the same scroll shifts and viewport clipping model used by FSR.
7. Normalize and de-duplicate the resulting rectangles.
8. Draw a translucent fill and a high-contrast border in the render-post phase, above the FSR UI and without adding an input-handling element.
9. Stop drawing immediately when the key is released or the user leaves an FSR screen.

## Discovery Strategy

The MVP will intentionally use `UIElement.createContextMenu(UIContext)` instead of reflection or a Mixin accessor:

- it is public and already represents the framework's authoritative menu creation path;
- it also detects `UIReplayList`, the only inspected subclass that overrides the method;
- null and empty menus can be filtered, reducing false positives;
- no private FSR field names become part of the addon compatibility contract.

To control allocation cost, discovery results should be cached for a short interval while the key is held and invalidated when:

- the current `UIScreen` or `UIBaseMenu` changes;
- the root element identity changes;
- the screen size changes;
- the refresh interval expires (proposed: every 5 client ticks).

If verification shows that any existing menu supplier has side effects or scanning is measurably expensive, the fallback is a small accessor Mixin that exposes whether `contextSupplier` or `contextOptions` is registered. That fallback should not be implemented pre-emptively.

## Visibility, Clipping, and De-duplication

A candidate is excluded when:

- `isVisible()`, `isEnabled()`, or `canBeSeen()` is false;
- width or height is zero/negative;
- it has no intersection with its effective viewport or the screen bounds;
- `createContextMenu(...)` returns null or an empty menu.

Rectangle processing:

- intersect the transformed element rectangle with the current viewport and screen bounds;
- merge exact duplicates;
- collapse near-identical rectangles when overlap is at least 95% and their edges differ by at most two pixels;
- retain distinct nested regions when their boundaries materially differ, because parent and child may expose different menus.

## Visual Contract

- Render all discoverable regions simultaneously while the key is held.
- Use a translucent cyan/blue fill and an opaque border as the initial theme-compatible default.
- Keep the fill light enough that labels, icons, and timelines remain readable.
- Do not render text labels, menu contents, or an additional panel in the MVP.
- Do not consume mouse, keyboard, wheel, focus, or context-menu events.

## Compatibility and Failure Handling

- The addon is built against FSR 2.3.1 and declares `[2.3.1,)` initially.
- Discovery must catch an exception from an individual menu supplier, log it once per element class, skip that element, and continue rendering other hints.
- If the local FSR jar is missing, compilation must fail with an actionable message, following the existing `bbs-web-remote` build pattern.
- No files under `new/` are changed for the MVP, preserving its current uncommitted work.

## Validation Strategy

Automated checks should cover pure rectangle logic:

- clipping to viewport and screen bounds;
- exact and near-identical de-duplication;
- preservation of materially different nested rectangles;
- empty and invalid rectangle filtering.

Manual integration coverage must include:

- film editor clip/timeline menus;
- replay list/editor menus;
- keyframe editor menus;
- model/form editor menus;
- data/path list menus;
- scrolling, nested overlays, GUI scaling, resizing, screen switching, and key release.

## Rollback

The addon is isolated in its own directory. Rollback consists of disabling/removing the addon jar; it does not migrate data or modify FSR state.
