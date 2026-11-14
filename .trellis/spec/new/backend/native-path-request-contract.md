# Native path dialogs and asynchronous setting requests

## 1. Scope / Trigger

Apply to native folder/file pickers and asynchronous encoder search, validation
or managed-download results that update a settings field. The owners are
`UIFileDialogs`, `WindowsFolderDialog`, `UIPathRow`, `UIFFMpegPath` and
`UIExportPathRow` in `new/src/client`.

## 2. Signatures

- `WindowsFolderDialog.pick(String title, String folder)` returns a path or null
  for cancellation, and throws on required COM failures.
- `UIPathRow.captureRequest(UIElement owner, ValueString path, long generation)`
  captures the value, UIContext and actual Minecraft Screen identity.
- `PathRequest.isCurrent(UIElement owner, ValueString path, long generation)`
  checks the captured generation/value and current active ownership.
- `UIFFMpegPath.isCurrentRequest(PathRequest request)` additionally clears an
  obsolete same-generation CHECKING state without disturbing a newer request.

## 3. Contracts

All native results return to Minecraft's main thread before touching settings,
UI state, context menus or notifications. An async result may be adopted only
when the request generation and setting value match, its captured Screen is
still current, UIScreen.getMenu matches the captured context, its owner remains
under that menu root, and every ancestor is enabled/visible. Closing overlays
disable input before they detach. UIScreen.removed closes the menu and input
state but does not remove all root children, so ancestry alone is insufficient.

Typing and tree removal invalidate the control's generation. Search-result
menu actions recheck ownership before starting validation. A superseded
completion must leave the newer request's state untouched. A same-generation
completion rejected because the value or owner changed clears its obsolete
CHECKING state. Managed installation belongs to FFMpegDownloadManager and is
not cancelled by closing the UI; its late completion cannot overwrite a newer
user setting or notify a closed screen.

COM initializes and uninitializes on the worker that owns the dialog. S_OK and
S_FALSE require CoUninitialize; failed initialization does not. Only exact
HRESULT_FROM_WIN32(ERROR_CANCELLED), signed32 `0x800704C7`, returns null without
fallback. Required setup/show/get-result/get-display-name failures throw through
COMUtils.checkRC so UIFileDialogs can invoke TinyFD. Optional initial-folder
failure may open the dialog elsewhere. Every acquired dialog/ShellItem reference
and shell-allocated display-name string is released in finally.

## 4. Validation / Error Matrix

| Condition | Required result |
| --- | --- |
| User cancels native folder dialog | Null; no second picker |
| Required COM setup or show fails | Release owned resources, invoke existing fallback |
| Optional starting folder fails | Continue picking with default folder, release acquired ShellItem |
| Setting is edited while work runs | Reject stale adoption and clear old CHECKING when generation unchanged |
| A newer request is running | Older completion cannot clear the new CHECKING state |
| Screen closes and menu is reused | Actual Screen identity rejects the old callback |
| Overlay is in exit animation | Disabled ancestry rejects adoption before removal |
| Managed download completes after close | Keep manager result; do not overwrite path or notify old UI |

## 5. Good / Base / Bad Cases

- Good: a folder request started by the current export control returns to the
  same Screen and unchanged value, then updates both setting and textbox.
- Base: a COM cancel releases resources and leaves the existing path alone.
- Bad: a callback checks only root ancestry, then updates a menu reused by a
  different Screen; or silently treats every negative HRESULT as cancellation.

## 6. Verification Required

Reuse existing `testUiInputMailbox`, `testFFMpegDownload`, `apiCheck`, client
compilation and JAR packaging. Confirm named JavaExec tasks executed and capture
input hashes before and after. Gate26 passed these checks with zero input/test
drift; this does not execute native COM or prove the callback UI scenarios.
The current user instruction prohibits adding/changing tests or fixtures.

Manual acceptance must pick and cancel Unicode Windows paths, exercise real
COM-to-TinyFD failure fallback, close/reopen a reused menu during search and
validation, change the setting during work, and let managed installation finish
after closing the UI. These scenarios remain unperformed at this checkpoint.

## 7. Wrong vs Correct

Wrong:

```java
if (COMUtils.FAILED(dialog.show(null)))
{
    return null;
}
```

Correct:

```java
WinNT.HRESULT shown = dialog.show(null);

if (shown.intValue() == HRESULT_CANCELLED)
{
    return null;
}

COMUtils.checkRC(shown);
```
