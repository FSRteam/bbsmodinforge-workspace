# IK / Physics / Selection Manual Matrix

## Simulation ownership and space

1. Place two world entities that use the same model id and enable physics on both.
2. Keep both entities still while orbiting and translating the player camera.
3. Expected: neither chain receives camera acceleration and the two histories do not kick or reset each other.
4. Open one of those forms in the model editor while the world placement remains visible.
5. Expected: editor preview motion is independent from the world placement; closing the editor does not reset the world chain.
6. Add the same IK/physics model as a transformed nested body part and repeat camera orbit plus parent motion.
7. Expected: the nested solver follows the body-part transform but retains its own stable history.

## Film anchors and world targets

1. Create replay A with an IK/physics model and anchor it to a moving, rotating bone on replay B; add a non-default anchor offset.
2. Place an absolute IK target, pole target, and physics pin on replay A, then play, pause, scrub, and orbit the editor camera.
3. Expected: the rendered form, IK target conversion, physics gravity/collision, and pin remain in the same anchored world position; camera motion adds no impulse.
4. Change the anchor attachment, animate the anchor blend, and repeat with a nested anchor chain.
5. Expected: simulation follows the exact blended anchor transform without one frame using A's unanchored entity coordinates.
6. Drive replay B's attachment bone with absolute IK, physics, and an animation-state pose/transform; enable shadow-follow and use the same bone for Tracker and orbit pivot.
7. Expected: child anchor, axes/Gizmo, shadow, Tracker, and orbit pivot all follow the exact rendered procedural bone, including the first frame after loading.
8. Give the replays different looping periods, then reorder the replays and reorder IK/pole/physics target channels that reference each other's ordinary animated bones.
9. Expected: each replay keeps the same local time and the same frame produces the same targets and physics; no referenced replay advances with the previous frame's controls because it appeared later in iteration order.
10. Enable replay-relative mode and repeat the camera motion with world collision enabled in the model config.
11. Expected: the relative replay uses stable local simulation, ignores absolute world target/pin overrides, and does not collide with blocks near world origin.
12. Try to use that relative replay as an absolute anchor parent, IK/pole target source, physics pin source, Tracker target, and orbit target.
13. Expected: cross-policy world consumers fail closed (child remains unanchored and absolute targets are skipped) rather than interpreting replay-local coordinates as world coordinates.

## First-person arms

1. Configure the same physics-capable model for both first-person hand slots.
2. Hold visible items in both hands, then look and turn rapidly without advancing the player position.
3. Expected: camera motion does not create gravity/inertia impulses; main- and off-hand histories remain independent.
4. Move, stop, and resume movement.
5. Expected: both hands advance from the player tick clock without sharing state or sampling world collision at camera-relative coordinates.
6. Add absolute film IK target/pole and physics pin overrides to the same form, then render it in first person.
7. Expected: the hand-local solve ignores those absolute overrides while ordinary local IK controllers and physics continue to run.

## Model-block live preview

1. Place a physics-enabled model block far from world origin with collision enabled, leave it visible, and open that exact block in the model editor.
2. Orbit/pan the preview while blocks exist near world origin but not around the placed block.
3. Expected: the editor preview uses identity-local simulation with no Level collision; it neither reacts to origin blocks nor resets/kicks the world placement.
4. Move and rotate the live model block while the preview stays open, then close and reopen the editor.
5. Expected: preview and world histories remain separate; the world renderer alone uses the block's absolute semantic transform and collisions.
6. Select a physics-driven preview bone and manipulate its Gizmo and animation-state editor origin.
7. Expected: preview mesh, picker, Gizmo and state editor use the same preview owner and identity-local physics pose.

## Item display modes

1. Use one gun item and one model-block item whose forms have visible physics and IK controllers.
2. Render each item in first-person left/right, third-person left/right, GUI, ground, fixed/item-frame, head, and NONE contexts.
3. Alternate rapidly between GUI and world views, drop/pick up the item, switch hands, and open/close the inventory.
4. Expected: every display context keeps a stable independent history; no mode inherits a jump, velocity, pole calibration, or pin from another mode.
5. Put solid blocks at world origin and around the player while the GUI/hand/ground/fixed previews run.
6. Expected: all item displays remain model-local and do not sample Level collisions or absolute world target/pin overrides.
7. Expected: ground, fixed, head, and NONE are classified as item-local, never as ordinary world entities.

## IK binding and limits

1. Enable IK on a visibly pre-bent animated two-bone limb with target and pole left at bind, `Pole Angle=0`.
2. Toggle pole false then true, disable/re-enable the chain, and move weight through `0 -> 1`.
3. Expected: every effective re-entry recalibrates without an enable-frame jump; FK animation continues while controller deltas are zero.
4. Move target and pole, then rotate the tip controller beyond narrow root/tip limits.
5. Repeat with cubic and BOBJ models, including a one-bone/short chain.
6. Expected: the exact rendered root, descendants, and tip stay inside limits; no debug-line/rendered-mesh disagreement appears.
7. Enable physics with a large authored twist plus narrow limits.
8. Expected: final swing+twist is clamped and descendants inherit the clamped parent frame.

## Viewport ownership

1. Open IK, physics, and constraints panels in turn; ordinary-click and Ctrl-click bound and unbound bones.
2. Expected: the visible panel selects and scrolls to the clicked bone; the hidden pose list is unchanged.
3. Select a nested form, return to the root form so the body-part editor is hidden, then Ctrl-click a root bone.
4. Expected: stale body-part state does not consume the click.
5. Ctrl-click a bone belonging to a different nested form.
6. Expected: the current form editor does not mutate its own same-named bone; selection changes to the actual picked Form owner.
