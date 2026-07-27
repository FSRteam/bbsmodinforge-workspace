# S6 design: UI framework extensions and mini windows

## 1. Delivery boundary

```text
bbs-snow-addon/src/client/java/mchorse/bbs_snow/
|-- ui/miniwindow/
|   |-- IMiniWindowDockHost.java
|   |-- IMiniWindowDockHostRef.java
|   |-- MiniWindowUIKeys.java
|   |-- UIMiniWindow.java
|   |-- UIMiniWindowManager.java
|   `-- UIResizeHandles.java
|-- mixin/client/
|   |-- UIBaseMenuFloatLayerMixin.java
|   |-- UIDashboardPanelMiniWindowMixin.java
|   |-- UIDockLayoutMiniWindowMixin.java
|   |-- UIFilmPanelMiniWindowMixin.java
|   `-- UIOverlayPanelResizeMixin.java
`-- verification/BBSSnowRuntimeVerification.java
```

The S2 accessor contracts remain the bridge to private target fields and methods. Resources add
only the three mini-window labels. `UIClipInspectorEmbedMixin` and the replay/keyframe helper stay
in S7.

## 2. Window and manager ownership

`UIMiniWindow` owns presentation and direct interaction: tabs, active content, title dragging,
collapse, resize handles, input blocking/forwarding, chrome rendering, and state capture.

`UIMiniWindowManager` owns host mutations: detach/restore, layout-tree removal/insertion, shell
creation, remembered session geometry, focus order, merge, rebuild reassertion, and rollback.

```text
panel drag-handle context action
  -> manager.undock(panelId)
     -> host marks floating
     -> window adopts panel
     -> host layout removes only the persistent layout id
     -> host rebuilds layout
     -> manager reasserts the floating panel/window

window restore
  -> manager.redock(window)
     -> capture session state
     -> return every panel to host
     -> reinsert missing persistent layout ids
     -> close shell and rebuild
```

All mutations retain rollback snapshots. Camera/replay editor panels map to the Film `main` shell
with `hostLayoutPanelId == null`, so floating those child editors never creates fake persistent
layout nodes.

## 3. Layer and input contracts

Generic dock windows use a zero-size, non-culled sibling layer above the dock. Window children may
render outside that layer's bounds, while the layer itself cannot cover the dock's normal input.

Film windows use a full Film-root layer because their panels span several children of the editor.
`UIBaseMenuFloatLayerMixin` provides the broader non-modal layer between `main` and `overlay` for
callers that need it. Modal overlays remain last and therefore retain input/render priority.

Every mini window blocks empty content from reaching unrelated controls. The Film preview is the
only exception in routing, not propagation: click/scroll/release are explicitly forwarded to
`dashboard.orbitUI`, then the mini window still consumes the original pointer event. Release is
forwarded even if a child consumed it so orbit ownership cannot remain stuck.

## 4. Dock and Film rebuild isolation

FSR rebuild methods iterate registered panel maps and can otherwise reclaim a floating panel.
Before `setupFlex`/`setupEditorFlex`, the Mixins temporarily remove floating entries from those
maps. At method return they restore registrations, rebind floating panels to windows, and repair
ordinary detached panels. The persisted layout is filtered through `stripFloatingFromLayout`.

The Film host additionally preserves the shared `main` shell, normalizes its child draw/input
order, renders surfaces only for still-docked panels, and refreshes property-panel offsets.
Inspector owner selection stays in S6 because it is part of choosing which content occupies the
shared floating edit area. S7 later adds hooks at clip/keyframe rebuild points to call the exposed
`IMiniWindowDockHostRef` refresh methods.

## 5. Source/target adaptation

- Rename source package and injected member prefixes to `mchorse.bbs_snow` / `bbs_snow$`.
- Replace `SnowUIKeys` mini-window references with the S6-only `MiniWindowUIKeys`.
- Keep current FSR descriptors for `createPanelDragHandle`, `ensureRegisteredPanels`, `setupFlex`,
  `ensureFilmLayoutPanels`, `setupEditorFlex`, `showPanel(UIElement)`, `renderPanelSurfaces`, and
  reset/disappear methods.
- Use the already configured S2 accessor Mixins for panel/icon maps and private layout methods.
- Do not port `UIScreenMixin`: current FSR audio import now validates owned temporary output and
  publishes collision-safely; the source's raw copy/direct ffmpeg algorithm is stale.

## 6. Verification and rollback

- Required target descriptors provide the startup signature gate.
- Runtime checks instantiate transformed `UIBaseMenu` and `UIOverlayPanel`, plus standalone
  mini-window/manager hosts, to exercise constructor injections and deterministic lifecycle paths.
- The strict client run records S6 target count and exact Mixin names.
- Actual pointer dragging, orbit feel, visual stacking, and Film inspector usability remain S10
  manual checks because synthetic UI contexts cannot prove real window-system gesture ownership.
- Rollback removes only the S6 files/config entries/translations/verifier additions. No BBS FSR
  source change is involved.
