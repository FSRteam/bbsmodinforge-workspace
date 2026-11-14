# Component Guidelines

> How components are built in this project.

---

## Overview

<!--
Document your project's component conventions here.

Questions to answer:
- What component patterns do you use?
- How are props defined?
- How do you handle composition?
- What accessibility standards apply?
-->

(To be filled by the team)

---

## Component Structure

<!-- Standard structure of a component file -->

(To be filled by the team)

---

## Props Conventions

<!-- How props should be defined and typed -->

(To be filled by the team)

---

## Styling Patterns

<!-- How styles are applied (CSS modules, styled-components, Tailwind, etc.) -->

(To be filled by the team)

---

## Accessibility

<!-- A11y requirements and patterns -->

(To be filled by the team)

---

## Common Mistakes

### Dashboard document ownership and lifecycle

`UIEditorDashboardPanel.documentTabs` (`UITabList`) owns document identity and
selection. Data panels keep their public `tabs` and `currentTab` compatibility
views synchronized with that owner; audio panels use the same owner. Route a tab
request through `IUITabsHost.openTab(...)` and the panel's `showTab(...)` callback.
For asynchronous loads, compare against the identity of the data actually loaded,
not only the requested tab, before treating a selection as complete. A previous
load may still be visible when the next tab is requested.

Dashboard subclasses register lifecycle work in their constructors with `onOpen`,
`onClose`, `onAppear`, and `onDisappear`. Entering callbacks run parent first;
leaving callbacks run child first so a child flushes widgets into its data before
the parent saves. `open`/`close` apply to every registered panel. World and playback
effects belong to `appear`/`disappear`, which apply to the visible panel. Public
lifecycle methods remain virtual for existing FSR addon descriptors; core panels
use callbacks to avoid bypassing inherited saves.

The real document strip is `UIDataTabs extends UITabStrip`. Selection, closing,
and tab geometry must use its shared owner; the top bar reserves the action width
through `setRightInsetPx(...)`. Preserve legacy public tab/scroll adapters without
creating a second selection or scroll owner.

`UIPanelBase.buttonTabs` is the actual `UITabStrip` owner for panel selectors.
Register and select panels through that strip; its highlight follows the rail's
content edge. The public `buttons` field retains its `UIScrollView` descriptor as
an alias to the same instance for existing FSR addons. Keep `changeDirection` and
deferred panel replacement during captured input; do not reintroduce a second
active-fill renderer or a separate tab list.

### Curve editor coordinate transforms

`UICurve` must keep graph-space and screen-space conversion in one owner. Rendering,
hit testing, adding nodes, dragging nodes, and viewport pan/zoom should all use the
same `toGraph(...)` / `fromGraph...(...)` helpers.

Wrong:

```java
double value = range.x + (1 - (mouseY - graph.y) / (double) graph.h) * (range.y - range.x);
int x = graph.x + (int) (time * graph.w);
```

Correct:

```java
double value = this.fromGraphY(context.mouseY);
Vector2d point = this.toGraph(time, value);
```

This prevents panned or zoomed views from rendering in one coordinate system while
dragging or hit testing uses another.

### Collapsible sections inside scroll views

When a `UISection` adds or removes its body, the layout owner is the nearest
`UIScrollView`, not only the section's direct parent. The scroll view must resize
immediately so three values stay in sync: following-child positions, `scrollSize`,
and the clamped current scroll offset. A window resize must never be the event that
repairs a stale collapsed layout.

For responsive editors that rebuild their children in `resize()`, every width
branch must re-add the same logical section. Keep a feature's mode, color, speed,
and transform controls under one reusable `UISection` instead of rebuilding an
unfolded copy in one branch. Do not stretch a list to consume height released by a
fold; that converts a successful collapse into a large empty list area.

Regression coverage should assert the behavior through the real scroll container:

- the next sibling moves upward immediately;
- `scrollSize` shrinks after collapse and grows after expansion;
- an old bottom scroll offset is clamped to the new range;
- every responsive layout branch still contains the section.

### Keyframe property panels across channel rebuilds

`KeyframeChannel.fromData(...)` replaces the channel's `Keyframe` objects and their
mutable value objects. A mounted property panel can therefore outlive both the
`Keyframe` and `Transform` instances that it was constructed with. Object identity is
only a fast path; it is not a stable key after a submit, undo, or channel reload.

When an editor keeps a panel mounted across a rebuild:

- resolve the live keyframe in the same channel by object identity first, then by the
  stable keyframe id and tick;
- use the current selection index only as a same-sheet fallback when stable lookup fails;
- replace the panel's keyframe reference with the live object before the next edit;
- refresh every child control that caches the value object, including transform, pose
  metadata, color, and lighting controls;
- keep per-axis selection and drag snapshots indexed by sheet and keyframe index instead
  of by `Keyframe` object.

Wrong:

```java
int index = sheet.channel.getKeyframes().indexOf(panelKeyframe);
Transform base = panelTransform.getTransform();
```

Correct:

```java
int index = PoseCurveKeyframeResolver.indexOf(sheet.channel.getKeyframes(), panelKeyframe);
Keyframe<?> live = sheet.channel.get(index);
panel.setKeyframe(live);
panelTransform.setTransform((Transform) live.getValue());
```

Regression coverage must use distinct old/live keyframe objects with the same id and
tick, a changed value object, and a same-tick keyframe with a different id. A runtime
check must still load the strict Mixin targets because compilation does not validate the
injected panel synchronization path.

### Optional GPU batches may contain zero vertices

UI overlays whose geometry depends on selection, interpolation mode, hidden channels,
or available keyframes can legitimately emit no vertices in a frame. Finalizing those
batches with `BufferBuilder.buildOrThrow()` turns a normal empty state into a screen
render crash.

Use `build()` and skip the upload when it returns `null`. Reserve `buildOrThrow()` for a
path that emits vertices unconditionally before finalization.

Wrong:

```java
BufferUploader.drawWithShader(builder.buildOrThrow());
```

Correct:

```java
MeshData mesh = builder.build();

if (mesh != null)
{
    BufferUploader.drawWithShader(mesh);
}
```

For Mixin-provided UI rendering, regression verification must cover both halves:

- run an empty-buffer probe on the render thread and require it to complete without a
  draw or exception;
- inspect or exercise the transformed target so the injected method is proven to call
  the guarded submission path, not merely compile beside it.
