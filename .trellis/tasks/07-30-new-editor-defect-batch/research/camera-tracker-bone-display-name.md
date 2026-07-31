# Research: Camera Tracker Bone Display Name Inconsistency

- **Query**: Camera tracker bone display name shows persistent ID instead of friendly display name
- **Scope**: Internal
- **Date**: 2026-07-30

## Findings

### Root Cause

`UIAnchorKeyframeFactory.displayAttachments()` (line 69-112) resolves attachment labels using only `Form.getTrackName()`, which only produces a display name when:
1. The attachment ID corresponds to a sub-form (embedded form within the main form), AND
2. That sub-form has a `trackName` configured

For **regular bones** (the majority of cases -- "leftArm", "head", "rightLeg", etc.), the attachment key is just a persistent internal ID (e.g., `"minecraft:player#main/%root/left_arm"` or `"leftArm"`), and no display-name resolution is performed. The raw ID is shown directly.

In contrast, both the **Pose track editor** and the **filter track overlay** correctly use `BoneHierarchy.getLabels()` to resolve display names.

### Files Found

| File Path | Description | Role |
|---|---|---|
| `new/src/main/java/mchorse/bbs_mod/camera/clips/modifiers/TrackerClip.java` | Data model for the tracker clip | Holds `group` (ValueString) which stores the selected attachment ID |
| `new/src/main/java/mchorse/bbs_mod/camera/clips/modifiers/EntityClip.java` | Base class for entity-modifying clips | Holds `selector` (ValueInt) and `offset` (ValuePoint) |
| `new/src/client/java/mchorse/bbs_mod/ui/film/clips/UITrackerClip.java` | UI for the tracker clip | Contains `selector` (actor pick) and `group` (attachment/bone pick) buttons |
| `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIAnchorKeyframeFactory.java` | Keyframe factory for anchor values | **BUG LOCATION**: `displayAttachments()` method at line 69-112 |
| `new/src/client/java/mchorse/bbs_mod/ui/film/replays/UIReplaysEditorUtils.java` | Utility for replay editor sheets | `addBoneTrackSheets()` at line 126-165 -- shows how pose tracks correctly use `BoneHierarchy.getLabels()` |
| `new/src/client/java/mchorse/bbs_mod/ui/utils/pose/UIPoseEditor.java` | Pose editor UI | `fillInGroups()` at line 299-303 -- uses `hierarchy.getLabels(hierarchicalLabels)` to display bone names |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/BoneHierarchy.java` | Bone hierarchy abstraction | `getLabels()` at line 88-119 -- generates display names with proper formatting |
| `new/src/client/java/mchorse/bbs_mod/ui/utils/pose/UIBoneHierarchyList.java` | String list for bone selection | `elementToString()` at line 27-30 -- uses label map for display, falls back to ID |
| `new/src/client/java/mchorse/bbs_mod/ui/utils/pose/UIBoneList.java` | Bone list widget | `setSource()` at line 90-98 -- accepts labels map from `BoneHierarchy` |
| `new/src/client/java/mchorse/bbs_mod/forms/FormUtilsClient.java` | Client-side form utilities | `getBoneLabel()` at line 230-241 -- **existing utility method** that resolves bone IDs to labels via `BoneHierarchy` |
| `new/src/client/java/mchorse/bbs_mod/camera/clips/misc/TrackerClientClip.java` | Client-side tracker clip logic | Uses `this.group.get()` (the stored attachment ID) to look up matrix in `MatrixCache` |
| `new/src/main/java/mchorse/bbs_mod/forms/forms/Form.java` | Form data model | `getTrackName()` at line 357-375 -- only produces a display name for sub-forms with a configured `trackName` |
| `new/src/client/java/mchorse/bbs_mod/forms/renderers/utils/MatrixCache.java` | Matrix cache for bone transforms | `keySet()` returns raw bone/attachment path IDs |
| `new/src/client/java/mchorse/bbs_mod/ui/film/replays/overlays/UIKeyframeSheetFilterOverlayPanel.java` | Filter overlay for track sheets | Uses `keyToLabel` map (populated from `sheet.title`) -- correct |
| `new/src/client/java/mchorse/bbs_mod/ui/film/clips/UIOrbitClip.java` | Orbit clip UI (sibling clip, same pattern) | Also uses `displayAttachments()` -- affected by same bug |
| `new/src/client/java/mchorse/bbs_mod/ui/film/clips/UILookClip.java` | Look clip UI (sibling clip, same pattern) | Also uses `displayActors()` -- affected by same bug |

### Code Patterns

#### Bug location: `UIAnchorKeyframeFactory.displayAttachments()` (line 69-112)

```java
public static void displayAttachments(UIFilmPanel panel, int index, String value, Consumer<String> consumer)
{
    // ...
    Form form = entity.getForm();
    List<String> attachments = new ArrayList<>(FormUtilsClient.getRenderer(form).collectMatrices(entity, 0F).keySet());
    attachments.sort(String::compareToIgnoreCase);

    /* Collect labels (substitute track names) */
    List<String> labels = new ArrayList<>(attachments);
    for (int i = 0; i < labels.size(); i++)
    {
        String label = labels.get(i);
        Form path = FormUtils.getForm(form, label);     // <-- only finds sub-forms
        if (path != null)
        {
            labels.set(i, path.getTrackName(label));    // <-- only resolves sub-form track names
        }
        // ELSE: regular bone -- label stays as raw persistent ID!
    }

    // Display the labels in the context menu
    panel.getContext().replaceContextMenu((menu) ->
    {
        for (int i = 0; i < attachments.size(); i++)
        {
            String attachment = attachments.get(i);
            String label = labels.get(i);               // <-- raw ID for regular bones
            menu.action(Icons.LIMB, IKey.constant(label), ...);
        }
    });
}
```

#### Correct pattern: `UIReplaysEditorUtils.addBoneTrackSheets()` (line 126-165)

```java
BoneHierarchy hierarchy = FormUtilsClient.getBoneHierarchy(form);
// ...
Map<String, String> labels = hierarchy.getLabels(false);   // <-- proper display names
for (BoneHierarchy.Bone bone : hierarchy.getBones())
{
    String label = labels.getOrDefault(bone.id(), bone.name());  // <-- display name
    String title = path.isEmpty() ? label : path + "/" + label;
    out.add(new UIKeyframeSheet(..., IKey.constant(title), ...));  // <-- uses display name
}
```

#### Correct pattern: `UIPoseEditor.fillInGroups()` (line 299-303)

```java
private void fillInGroups(BoneHierarchy hierarchy, boolean reset, boolean hierarchicalLabels)
{
    this.groups.setSource(hierarchy.getBoneIds(), hierarchy.getLabels(hierarchicalLabels), false);
    this.groups.filter(reset);
}
```

#### Existing utility that does exactly what's needed: `FormUtilsClient.getBoneLabel()` (line 230-241)

```java
public static String getBoneLabel(Form form, String bone)
{
    if (bone == null || bone.isEmpty()) return "";
    BoneHierarchy hierarchy = getBoneHierarchy(form);
    String resolved = hierarchy.resolveId(bone);
    return hierarchy.getLabels(false).getOrDefault(resolved, bone);
}
```

This method was already added (presumably for the same class of issue) and can directly resolve a bone ID to its display label using the same `BoneHierarchy` mechanism as the Pose editor.

### Related Specs

- `.trellis/spec/` -- no spec exists yet for this pattern

## Fix Recommendation

Modify `UIAnchorKeyframeFactory.displayAttachments()` to also use `BoneHierarchy` for label resolution.

**Option A (simpler):** Use `FormUtilsClient.getBoneLabel()` for each attachment that doesn't match a sub-form:

```java
// In the label loop, after the existing sub-form check:
Form path = FormUtils.getForm(form, label);
if (path != null)
{
    labels.set(i, path.getTrackName(label));
}
else
{
    // NEW: resolve regular bone IDs to display names
    labels.set(i, FormUtilsClient.getBoneLabel(form, label));
}
```

**Option B (more thorough):** Pre-compute the full label map from `BoneHierarchy` and use it as an overlay before falling back to `getTrackName()`:

```java
BoneHierarchy hierarchy = FormUtilsClient.getBoneHierarchy(form);
Map<String, String> hierarchyLabels = hierarchy.getLabels(false);

for (int i = 0; i < labels.size(); i++)
{
    String label = labels.get(i);
    Form path = FormUtils.getForm(form, label);
    if (path != null)
    {
        labels.set(i, path.getTrackName(label));
    }
    else if (hierarchyLabels.containsKey(label) || hierarchy.resolveId(label) != null)
    {
        // NEW: use BoneHierarchy labels for regular bones
        String resolved = hierarchy.resolveId(label);
        labels.set(i, hierarchyLabels.getOrDefault(resolved, label));
    }
}
```

**Option A is recommended** because `FormUtilsClient.getBoneLabel()` already encapsulates the exact logic needed -- it calls `BoneHierarchy.resolveId()` (handling legacy aliases) and `getLabels()` to get the display name. It's a one-line addition.

## Caveats / Not Found

- The `selector` button (entity/actor picker) in `UITrackerClip` and `UIOrbitClip` also has a fixed label (`UIKeys.CAMERA_PANELS_TARGET_TITLE`) that never updates to reflect the selected entity. This is a separate visual issue not covered by this analysis.
- The same `displayAttachments()` label issue affects `UIOrbitClip` (for picking attachments if any) and potentially other callers.
- `collectMatrices()` may return keys that don't correspond to bones in the hierarchy (sub-form attachments, etc.), so using `hierarchy.getLabels()` as a fallback (rather than primary) is correct.
