# Research: Orbit Camera Regression - Black Screen After Smoothing Fix

- **Query**: Locate root cause of orbit camera mode failing to render (black screen) after recent black screen fix in NeoForge port
- **Scope**: internal (code comparison + git diff analysis)
- **Date**: 2026-06-20

## Findings

### 1. Keybinding Conflict Analysis

#### Files Compared
- **new**: `new/src/client/java/mchorse/bbs_mod/ui/Keys.java` (lines 65, 134-135)
- **reference 1.21.1**: `temp/bbs-fs-1.21.1-ui/src/client/java/mchorse/bbs_mod/ui/Keys.java` (lines 131 only)

#### Key Differences

**NeoForge NEW (lines 65, 134-135)**:
```java
public static final KeyCombo FLIGHT_ORBIT = new KeyCombo("flight_orbit", UIKeys.CAMERA_FLIGHT_FLIGHT_ORBIT, GLFW.GLFW_KEY_Z).categoryKey("flight");
...
public static final KeyCombo FILM_CONTROLLER_TELEPORT_ORBIT = new KeyCombo("teleport_orbit_record", UIKeys.FILM_CONTROLLER_KEYS_TELEPORT_ORBIT, GLFW.GLFW_KEY_Z).categoryKey("film_controller");
public static final KeyCombo FILM_CONTROLLER_ATTACH_ORBIT = new KeyCombo("attach_orbit_record", UIKeys.FILM_CONTROLLER_KEYS_ATTACH_ORBIT, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_LEFT_SHIFT).categoryKey("film_controller");
```

**FS2 1.21.1 Reference (line 131 only)**:
```java
public static final KeyCombo FILM_CONTROLLER_TELEPORT_ORBIT = new KeyCombo("teleport_orbit_record", UIKeys.FILM_CONTROLLER_KEYS_TELEPORT_ORBIT, GLFW.GLFW_KEY_Z).categoryKey("film_controller");
```

#### Usage Verification
- **`FLIGHT_ORBIT`**: Defined on line 65 but **never referenced** anywhere in the codebase (`grep -r FLIGHT_ORBIT` returns only definition and UIKeys localization)
- **`FILM_CONTROLLER_ATTACH_ORBIT`**: **New addition** in NeoForge port, registered in `UIFilmController.java:175-179`
- **`FILM_CONTROLLER_TELEPORT_ORBIT`**: Used in `UIFilmController.java:174` with `.strict().active(() -> this.getPovMode() == CAMERA_MODE_ORBIT)`

#### Keybinding Registration
**new/src/client/java/mchorse/bbs_mod/ui/film/controller/UIFilmController.java:174-179**:
```java
this.keys().register(Keys.FILM_CONTROLLER_TELEPORT_ORBIT, this::teleportOrbitPivotToReplay).strict().active(() -> this.getPovMode() == CAMERA_MODE_ORBIT).category(category);
this.keys().register(Keys.FILM_CONTROLLER_ATTACH_ORBIT, () ->
{
    this.toggleOrbitAttachment();
    UIUtils.playClick();
}).strict().active(() -> this.getPovMode() == CAMERA_MODE_ORBIT).category(category);
```

**Reference 1.21.1 UIFilmController.java:159** (NO ATTACH_ORBIT):
```java
this.keys().register(Keys.FILM_CONTROLLER_TELEPORT_ORBIT, this::teleportOrbitPivotToReplay).strict().active(() -> this.getPovMode() == CAMERA_MODE_ORBIT).category(category);
```

**Verdict**: No Z-key conflict detected between active keybindings (both use `.strict()` and context-active conditions). However, `FLIGHT_ORBIT` is **orphaned code** (defined but never used).

---

### 2. Orbit Camera Initialization & Smoothing Logic

#### Black Screen Root Cause Location

**Camera smoothing implementation in OrbitCamera.java (lines 377-396)**: 
```java
protected void applySmoothing()
{
    float smoothness = BBSSettings.editorCameraSmoothness.get();

    if (smoothness <= 0F)
    {
        this.position.set(this.targetPosition);
        this.rotation.set(this.targetRotation);
        this.fov = this.targetFov;
        return;
    }

    float dt = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
    float factor = MathUtils.clamp(1F - (float) Math.pow(Math.min(smoothness, 0.99F), dt), 0F, 1F);

    this.position.lerp(this.targetPosition, factor);
    this.rotation.lerp(this.targetRotation, factor);
    this.fov = Lerps.lerp(this.fov, this.targetFov, factor);
}
```

#### Problem in Orbit Mode Entry

**new/src/client/java/mchorse/bbs_mod/camera/OrbitCamera.java:70-77**:
```java
public void setup(Camera camera)
{
    this.position.set(camera.position);
    this.rotation.set(camera.rotation);
    this.fov = camera.fov;

    this.syncTarget();
}
```

This `setup()` is called in **UIFilmPanel.java:3111** when `!this.dashboard.orbitUI.canControl()`:
```java
else
{
    this.dashboard.orbit.setup(this.getCamera());
}
```

#### Critical Issue: Camera not positioned before orbit apply

When switching to orbit mode:
1. `UIFilmPanel.updateLogic()` line 3111 calls `this.dashboard.orbit.setup(this.getCamera())`
2. But **if orbit camera's target position is uninitialized or misaligned**, subsequent calls to `this.dashboard.orbit.apply()` (line 3091) will apply incorrect camera position
3. The smoothing code at line 3091 applies via `this.dashboard.orbit.apply(this.position)` **before** full orbit state is initialized

#### Orbit Film Camera Controller Changes (git diff evidence)

**new/src/client/java/mchorse/bbs_mod/ui/film/controller/OrbitFilmCameraController.java** has major refactoring:
- Old: simple `position` + `rotation` + `distance` + `offsetY` + `center` boolean
- **New**: `targetRotation`, `targetPivot`, `targetDistance`, `attached`, `anchorReplay`, `anchorPosition`, `anchorYaw`, `panState`

**Initialization gap**: The new `positioned` flag (line 44 diff) is `false` initially, meaning orbit state tracking is incomplete on first entry.

---

### 3. Teleport to Recording Logic

#### Reference FS2 1.21.1 Implementation
Has commit message "телепортация орбиты к записи теперь вновь на Z" (orbit teleportation to recording now back on Z).

**Keys.java line 131**: Single Z-key binding for teleport-only orbit.

#### New NeoForge Port
**UIFilmController.java:174-179**: Splits into:
- `FILM_CONTROLLER_TELEPORT_ORBIT` (Z alone) 
- `FILM_CONTROLLER_ATTACH_ORBIT` (Z+Shift)

Both call separate methods but both have `.active(() -> this.getPovMode() == CAMERA_MODE_ORBIT)`.

---

### 4. Uncommitted Changes in Camera & Film Modules

#### Git diff statistics
```
.../camera/OrbitCamera.java      | 53 ++++-
.../film/BaseFilmController.java | 230 ++++++++++++++++++++-
src/client/java/mchorse/bbs_mod/film/Films.java    |   8 +
src/client/java/mchorse/bbs_mod/film/Recorder.java |  59 ++++++
```

#### Key Diff Lines in OrbitCamera.java
- **Lines 39-43**: Added target position/rotation caching (`targetPosition`, `targetRotation`, `targetFov`)
- **Lines 76, 134**: Added `this.syncTarget()` calls
- **Lines 234-236, 248, 262**: Changed raw `this.rotation` → `this.targetRotation` mutations
- **Lines 357, 368**: Changed `this.position` → `this.targetPosition` and `this.rotation` → `this.targetRotation`
- **Lines 377-396**: NEW `applySmoothing()` method (causes position lag on first frame)

#### Root Cause: Uninitialized Target State on Orbit Entry

When switching to orbit mode via `setPov(CAMERA_MODE_ORBIT)`:
1. `UIFilmController.setPov()` sets `this.orbit.enabled = true`
2. Next frame, `UIFilmPanel.updateLogic()` calls `this.dashboard.orbit.setup(camera)` (line 3111)
3. **BUT** if orbit camera's `targetPosition`/`targetRotation` differ from current `position`/`rotation`, the smoothing lerp will take multiple frames to converge
4. During convergence, camera moves to unintended positions → black screen if it goes inside geometry or far away
5. The new `OrbitFilmCameraController` has `positioned = false` initially, meaning replay pivot not yet calculated

---

## Caveats & Not Found

1. **Why black screen specifically?** The exact rendered black pixel condition requires:
   - Orbit pivot calculation failing or returning invalid coordinates
   - OR camera positioned inside a block or too far from entities
   - Need to check if `getReplayPivot()` or anchor matrix calculation returns (0,0,0)

2. **`FLIGHT_ORBIT` orphaned**: Defined but never used; should be removed or mapped to flight mode orbit toggle if intended

3. **No commit evidence**: Changes are uncommitted; cannot see exact timing of when smoothing was added vs. when regression appeared

---

## Fix Direction

1. **Ensure orbit state initialization on mode entry**: 
   - In `UIFilmController.setPov(CAMERA_MODE_ORBIT)`, explicitly initialize `orbit.setupFromCurrent()` to sync target state immediately
   - OR call `this.dashboard.orbit.setup(this.getCamera())` before setting `orbit.enabled = true`

2. **Validate pivot calculation in OrbitFilmCameraController**:
   - Check `getReplayPivot()` returns valid entity position before applying
   - Ensure `anchorPosition` is not (0,0,0) on entry

3. **Remove orphaned FLIGHT_ORBIT**:
   - Delete line 65 in `Keys.java`
   - Or properly map it if intended for flight mode orbit control

4. **Test black screen fix interaction**:
   - Smoothing code path (`applySmoothing()` at line 344) is called BEFORE input processing
   - Ensure target vectors are properly initialized before first smooth lerp
