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
