"""Dump the real BBS UI panel inventory + verified Chinese labels.

Two authoritative sources:
  1. strings/zh_cn.json  -> the exact on-screen Chinese text (1987 keys)
  2. registerPanel(...) call sites -> which panels actually exist

Tutorial `instruction` text MUST quote labels from source 1, otherwise a
beginner looks for a button that does not exist under that name.
"""
import io
import json
import os
import re

ROOT = "D:/bbsinneoforge/bbsmodinforge/bbsmodinforge"
UI = os.path.join(ROOT, "new/src/client/java/mchorse/bbs_mod/ui")
STRINGS = os.path.join(
    ROOT, "new/src/client/resources/assets/bbs/assets/strings/zh_cn.json"
)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ui-inventory.json")


def flatten(obj, prefix=""):
    for k, v in obj.items():
        name = prefix + "." + k if prefix else k
        if isinstance(v, dict):
            for pair in flatten(v, name):
                yield pair
        else:
            yield name, v


labels = dict(flatten(json.load(io.open(STRINGS, encoding="utf-8"))))

# UIKeys constant -> lang key, so registerPanel(UIKeys.X) can be resolved to text
uikeys = {}
src_uikeys = io.open(os.path.join(UI, "UIKeys.java"), encoding="utf-8").read()
for m in re.finditer(
    r'IKey\s+([A-Z0-9_]+)\s*=\s*L10n\.lang\("([^"]+)"\)', src_uikeys
):
    uikeys[m.group(1)] = m.group(2)


def text_of(token):
    """UIKeys.FOO -> real Chinese label."""
    token = token.strip()
    if token.startswith("UIKeys."):
        key = uikeys.get(token[7:])
        if key:
            return labels.get(key, "(未翻译:" + key + ")")
        return "(未知常量:" + token + ")"
    m = re.match(r'IKey\.raw\("([^"]+)"\)', token)
    if m:
        return m.group(1)
    return token


# every registerPanel(<content>, <UIKeys.X>, <Icons.Y>) across the UI tree
panels = []
for base, _dirs, files in os.walk(UI):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        path = os.path.join(base, fn)
        src = io.open(path, encoding="utf-8", errors="replace").read()
        rel = os.path.relpath(path, UI).replace(os.sep, "/")
        for m in re.finditer(
            r"registerPanel\(\s*(?P<content>[^,]+?)\s*,\s*"
            r"(?P<label>UIKeys\.[A-Z0-9_]+|IKey\.raw\([^)]*\))\s*,\s*"
            r"(?P<icon>Icons\.[A-Z0-9_]+)",
            src,
        ):
            panels.append({
                "declaredIn": rel,
                "content": m.group("content").strip(),
                "labelConst": m.group("label"),
                "label": text_of(m.group("label")),
                "icon": m.group("icon"),
            })

groups = {}
for p in panels:
    groups.setdefault(p["declaredIn"], []).append(p)

# label groups worth quoting verbatim in tutorial steps
PREFIXES = {
    "morphing": "bbs.ui.morphing",
    "film": "bbs.ui.film",
    "forms_model": "bbs.ui.forms.editors.model",
    "forms_actions": "bbs.ui.forms.editors.actions",
    "particles": "bbs.ui.particle",
    "model_editor": "bbs.ui.model_editor",
    "model_blocks": "bbs.ui.model_blocks",
    "textures": "bbs.ui.textures",
    "audio": "bbs.ui.audio_editor",
    "plugins": "bbs.ui.plugins",
    "dashboard": "bbs.ui.dashboard",
    "keyframes": "bbs.ui.keyframes",
    "camera_editor": "bbs.ui.camera_editor",
}
vocab = {}
for name, pref in PREFIXES.items():
    hit = {k: v for k, v in labels.items() if k.startswith(pref)}
    vocab[name] = {"prefix": pref, "count": len(hit), "labels": dict(sorted(hit.items()))}

doc = {
    "_meta": {
        "purpose": "BBS 真实面板清单 + 真实中文 UI 文案，教程 instruction/userWillSee 必须与此一致",
        "label_source": "new/src/client/resources/assets/bbs/assets/strings/zh_cn.json",
        "label_total": len(labels),
        "panel_call_sites": len(panels),
        "note": "L10n.lang() 从 assets/strings/<lang>.json 读取，不是 assets/bbs/lang/（后者只有按键名）",
    },
    "registered_panels": groups,
    "label_vocabulary": vocab,
}

with io.open(OUT, "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=2)

print("labels:", len(labels), " registerPanel sites:", len(panels))
print()
for f in sorted(groups):
    print(f)
    for p in groups[f]:
        print("   %-34s %s" % (p["label"], p["icon"]))
print()
for name in sorted(vocab):
    print("vocab %-14s %4d labels" % (name, vocab[name]["count"]))
