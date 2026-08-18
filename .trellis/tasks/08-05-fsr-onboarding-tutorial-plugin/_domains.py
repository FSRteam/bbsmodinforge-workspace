"""Slice all 1987 real UI labels into functional domains.

Purpose: a 300-step tutorial cannot be written from memory. Every step's
`instruction` / `userWillSee` must quote a label that actually exists in
strings/zh_cn.json. This produces the per-domain vocabulary + a step budget
so coverage can be verified mechanically instead of by eyeballing.
"""
import io
import json
import os
import re
from collections import OrderedDict

ROOT = "D:/bbsinneoforge/bbsmodinforge/bbsmodinforge"
STRINGS = os.path.join(
    ROOT, "new/src/client/resources/assets/bbs/assets/strings/zh_cn.json"
)
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "ui-domains.json")


def flatten(obj, prefix=""):
    for k, v in obj.items():
        name = prefix + "." + k if prefix else k
        if isinstance(v, dict):
            for pair in flatten(v, name):
                yield pair
        else:
            yield name, v


labels = OrderedDict(sorted(flatten(json.load(io.open(STRINGS, encoding="utf-8")))))

# Domain -> key prefixes. Ordered most-specific-first so each label lands once.
DOMAINS = OrderedDict([
    # --- 影片系统 ---
    # motion_path / onion_skin 嵌在 film.controller 下，必须先于 film_replays 匹配
    ("film_motion_path",  ["bbs.ui.film.controller.motion_path",
                           "bbs.ui.film.controller.onion_skin",
                           "bbs.ui.film.motion_path", "bbs.ui.film.onion_skin"]),
    ("film_replays",      ["bbs.ui.film.replays", "bbs.ui.film.controller",
                           "bbs.ui.film.groups", "bbs.ui.film.record",
                           "bbs.ui.scene.replays", "bbs.ui.scene"]),
    ("film_clips",        ["bbs.ui.film.clips", "bbs.ui.camera"]),
    ("film_core",         ["bbs.ui.film"]),
    # 42 种插值类型是独立命名空间 interpolations.*，不在 bbs.ui.* 下
    ("interpolations",    ["interpolations."]),
    ("keyframes",         ["bbs.ui.keyframes", "bbs.ui.generic_keyframes"]),
    # --- 伪装 / 形态 ---
    ("forms_model",       ["bbs.ui.forms.editors.model"]),
    ("forms_actions",     ["bbs.ui.forms.editors.actions",
                           "bbs.ui.forms.editors.states", "bbs.ui.actions"]),
    ("forms_pose",        ["bbs.ui.pose", "bbs.ui.shape_keys"]),
    ("forms_other",       ["bbs.ui.forms"]),
    ("morphing",          ["bbs.ui.morphing"]),
    # --- 纹理 / 像素画 ---
    ("textures_pixels",   ["bbs.ui.textures.pixels", "bbs.ui.textures.layers",
                           "bbs.ui.textures.tools"]),
    ("textures",          ["bbs.ui.textures", "bbs.ui.texture", "bbs.ui.color"]),
    ("skins_themes",      ["bbs.ui.skins", "bbs.config.skins",
                           "bbs.config.multiskin", "bbs.config.refreshed"]),
    # --- 粒子（真名 snowstorm）---
    ("particles",         ["bbs.ui.snowstorm", "bbs.ui.particle"]),
    # --- 建模 ---
    ("model_editor",      ["bbs.ui.model_editor", "bbs.ui.model_debug"]),
    ("model_blocks",      ["bbs.ui.model_blocks", "bbs.config.model_blocks"]),
    # --- 枪械 / 场景物件 ---
    ("gun",               ["bbs.ui.gun"]),
    # --- 导出 / 编码 ---
    ("video_export",      ["bbs.ui.video_settings", "bbs.ui.ffmpeg",
                           "bbs.config.video", "bbs.config.recording"]),
    ("importer",          ["bbs.ui.importer", "bbs.ui.cdn", "bbs.config.cdn"]),
    # --- 设置总集 ---
    ("settings",          ["bbs.config", "bbs.ui.config", "bbs.ui.settings",
                           "bbs.ui.theme", "bbs.ui.overlays",
                           "bbs.ui.scrollbars"]),
    # --- 其它面板 ---
    ("plugins",           ["bbs.ui.plugins"]),
    ("audio",             ["bbs.ui.audio"]),
    ("graph",             ["bbs.ui.graph"]),
    ("selectors",         ["bbs.ui.selectors", "bbs.config.entity_selectors"]),
    ("dashboard",         ["bbs.ui.dashboard", "bbs.ui.panels"]),
    ("utility",           ["bbs.ui.utility", "bbs.ui.language",
                           "studio.ui.utility", "studio.ui.language"]),
    ("presets",           ["bbs.ui.presets", "bbs.ui.item_stack"]),
    ("keys_labels",       ["bbs.ui.keys"]),
    ("credits",           ["bbs.ui.supporters"]),
    ("general",           ["bbs.ui.general", "bbs.ui.engine",
                           "bbs.ui.transforms"]),
])

assigned = {}
buckets = OrderedDict((d, OrderedDict()) for d in DOMAINS)
for key, val in labels.items():
    for dom, prefixes in DOMAINS.items():
        if any(key.startswith(p) for p in prefixes):
            buckets[dom][key] = val
            assigned[key] = dom
            break

leftover = OrderedDict((k, v) for k, v in labels.items() if k not in assigned)

# Step budget: proportional to vocabulary size, floor of 4, target ~300.
TARGET = 300
sizes = {d: len(b) for d, b in buckets.items() if b}
total = sum(sizes.values())
budget = {}
for d, n in sizes.items():
    budget[d] = max(4, round(TARGET * n / total))

doc = {
    "_meta": {
        "purpose": "把 1987 条真实 UI 文案切成功能域，为 300 步教程提供逐步文案依据与覆盖预算",
        "label_source": "new/src/client/resources/assets/bbs/assets/strings/zh_cn.json",
        "label_total": len(labels),
        "assigned": len(assigned),
        "unassigned": len(leftover),
        "step_target": TARGET,
        "rule": "每一步的 instruction/userWillSee 必须引用所属域 labels 中的真实文案，不得自造按钮名",
    },
    "step_budget": OrderedDict(sorted(budget.items(), key=lambda kv: -kv[1])),
    "domains": OrderedDict(
        (d, {"count": len(b), "labels": b}) for d, b in buckets.items() if b
    ),
    "unassigned_labels": leftover,
}

with io.open(OUT, "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=2)

print("labels total :", len(labels))
print("assigned     :", len(assigned))
print("unassigned   :", len(leftover))
print("budget sum   :", sum(budget.values()))
print()
for d, n in sorted(budget.items(), key=lambda kv: -kv[1]):
    print("  %-18s %4d labels -> %3d steps" % (d, sizes[d], n))
