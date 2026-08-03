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

## Bone List Display Contract (2.5 hierarchy tree)

Every bone list backed by `UIBoneTreeList` renders flat unless the host feeds hierarchy
metadata. The 2.5 migration wired this for `IModel` paths (model form panel, IK/physics/
constraints panels, bone pickers, film model keyframes); `BoneHierarchy`-backed lists
(mob forms' vanilla model parts, pose form bones) must feed it too:

```java
Map<String, String> labels = hierarchy.getLabels();

this.groups.list.setHierarchy(hierarchy, null, labels);   // tree metadata
this.groups.setSource(hierarchy.getBoneIds(), labels, false); // hierarchy order, no sort
this.groups.filter(reset);
```

Signatures:

- `setHierarchy(IModel model, Predicate<String> hidden)` — tree from a model's root/child keys.
- `setHierarchy(BoneHierarchy hierarchy, Predicate<String> hidden, Map<String, String> labels)`
  — tree from `BoneHierarchy.Bone` (`id`/`parentId`/`depth`). Layer roots and orphans
  (parentId absent from the id set) become separate roots; a hidden bone dissolves into
  its children in place; ids missing from the label map fall back to the raw id.
- `getLabels()` — stable display names (e.g. `inner_armor_rightArm`); **no indent
  parameter** — the tree list draws indentation/connectors itself.

Invariants:

- `BoneHierarchy.getBoneIds()` is pre-order: per-layer recursion with children sorted by
  natural name order, layers ordered primary-first then by name. The parent-grouping
  rebuild (`boneHierarchyRoots`) reproduces the exact list order — do not re-sort.
- `treeLabel == fullLabel == labels[id]` — flat search mode (host sets
  `list.flat(!query.isEmpty())`) and tree mode agree on labels.
- Search flattening is the host's job (`UIBoneList.filter`), not the tree list's.

### Gotcha: flat MobForm bone lists are a legacy style

Before 2.5 the pose editor's bone list was `UIBoneHierarchyList` (flat label rows) and
`fillGroups(BoneHierarchy)` cleared tree metadata (`setHierarchy(null, null)`). Since 2.5,
new BoneHierarchy-backed lists must NOT clear metadata — pass the hierarchy so rows render
as a tree. `setHierarchy(null, null)` remains correct only for plain string lists
(`fillGroups(Collection)`, e.g. film anchor bones), which are intentionally flat + sorted.

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
