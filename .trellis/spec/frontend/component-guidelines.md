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
- `fillAttachments(form, keys)` walks ModelForm subtrees (`formBoneNodes`) AND
  PoseForm bones (`formBoneHierarchyNodes` → `boneHierarchyRoots` + `remapFormKeys`);
  node ids are matrix keys (`combinePaths(formPath, bone.id())`), labels are display
  names. Without the PoseForm branch, mob/pose form bones fall flat as raw ids.

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
