"""Dump every KeyCombo declared in ui/Keys.java, grouped by category.

Source of truth for editor-internal shortcuts. These are registered into
config/bbs/keybinds.json via KeybindSettings.register() (reflection over
Keys.class), so users can rebind them -- tutorial text must say "default".
"""
import io
import os
import re
import json

ROOT = "D:/bbsinneoforge/bbsmodinforge/bbsmodinforge"
KEYS = os.path.join(ROOT, "new/src/client/java/mchorse/bbs_mod/ui/Keys.java")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "keybinds-verified.json")

GLFW_LABEL = {
    "GLFW_KEY_LEFT_CONTROL": "Ctrl",
    "GLFW_KEY_RIGHT_CONTROL": "右 Ctrl",
    "GLFW_KEY_LEFT_SHIFT": "Shift",
    "GLFW_KEY_RIGHT_SHIFT": "右 Shift",
    "GLFW_KEY_LEFT_ALT": "Alt",
    "GLFW_KEY_RIGHT_ALT": "右 Alt",
    "GLFW_KEY_SPACE": "空格",
    "GLFW_KEY_ENTER": "回车",
    "GLFW_KEY_DELETE": "Delete",
    "GLFW_KEY_BACKSPACE": "退格",
    "GLFW_KEY_ESCAPE": "Esc",
    "GLFW_KEY_TAB": "Tab",
    "GLFW_KEY_HOME": "Home",
    "GLFW_KEY_END": "End",
    "GLFW_KEY_PAGE_UP": "PageUp",
    "GLFW_KEY_PAGE_DOWN": "PageDown",
    "GLFW_KEY_LEFT": "←",
    "GLFW_KEY_RIGHT": "→",
    "GLFW_KEY_UP": "↑",
    "GLFW_KEY_DOWN": "↓",
    "GLFW_KEY_COMMA": "逗号 ,",
    "GLFW_KEY_PERIOD": "句号 .",
    "GLFW_KEY_SLASH": "斜杠 /",
    "GLFW_KEY_BACKSLASH": "反斜杠",
    "GLFW_KEY_SEMICOLON": "分号 ;",
    "GLFW_KEY_APOSTROPHE": "单引号 '",
    "GLFW_KEY_LEFT_BRACKET": "左方括号 [",
    "GLFW_KEY_RIGHT_BRACKET": "右方括号 ]",
    "GLFW_KEY_MINUS": "减号 -",
    "GLFW_KEY_EQUAL": "等号 =",
    "GLFW_KEY_GRAVE_ACCENT": "反引号 `",
}


def label(token):
    token = token.strip()
    if token in GLFW_LABEL:
        return GLFW_LABEL[token]
    m = re.match(r"GLFW_KEY_([A-Z0-9]+)$", token)
    if m:
        name = m.group(1)
        if re.match(r"^F\d+$", name):
            return name
        if len(name) == 1:
            return name
        if name.startswith("KP_"):
            return "小键盘 " + name[3:]
        return name
    return token


# KeyCombo(id, UIKeys.X, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT_CONTROL, ...)
PATTERN = re.compile(
    r'new\s+KeyCombo\(\s*"(?P<id>[^"]+)"\s*,\s*(?P<label>[A-Za-z0-9_.]+)\s*'
    r'(?P<keys>(?:,\s*GLFW\.GLFW_KEY_[A-Z0-9_]+\s*)*)\)'
    r'(?P<tail>[^;]*)'
)

src = io.open(KEYS, encoding="utf-8").read()

entries = []
for m in PATTERN.finditer(src):
    keys = re.findall(r"GLFW\.(GLFW_KEY_[A-Z0-9_]+)", m.group("keys"))
    if not keys:
        continue
    tail = m.group("tail")
    cat = re.search(r'\.categoryKey\("([^"]+)"\)', tail)
    # main key is listed first, modifiers after -> display modifiers first
    main, mods = keys[0], keys[1:]
    combo = " + ".join([label(k) for k in mods] + [label(main)])
    entries.append({
        "id": m.group("id"),
        "category": cat.group(1) if cat else "editor",
        "combo": combo,
        "glfw": keys,
        "repeatable": ".repeatable()" in tail,
        "labelKey": m.group("label"),
    })

by_cat = {}
for e in entries:
    by_cat.setdefault(e["category"], []).append(e)

doc = {
    "_meta": {
        "purpose": "BBS 编辑器内快捷键权威清单（教程 instruction 文案必须与此一致）",
        "source_file": "new/src/client/java/mchorse/bbs_mod/ui/Keys.java",
        "registry": "new/src/client/java/mchorse/bbs_mod/ui/utils/keys/KeybindSettings.java",
        "how_registered": "KeybindSettings.register() 反射扫描 Keys.class 的所有 KeyCombo 字段，按 categoryKey 分组注册为设置项",
        "user_config_file": "config/bbs/keybinds.json",
        "rebindable": True,
        "tutorial_rule": "这些键用户可在 设置 → 按键绑定 中修改，教程文案必须写「默认」二字，并提示可去按键绑定查看",
        "open_keybind_list_shortcut": "F9（Keys.KEYBINDS）",
        "total": len(entries),
        "categories": {k: len(v) for k, v in sorted(by_cat.items())},
    },
    "categories": {k: sorted(v, key=lambda x: x["id"]) for k, v in sorted(by_cat.items())},
}

with io.open(OUT, "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=2)

print("total combos:", len(entries))
for k, v in sorted(by_cat.items()):
    print("  %-12s %d" % (k, len(v)))
