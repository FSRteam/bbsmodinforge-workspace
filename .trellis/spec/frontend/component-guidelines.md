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
