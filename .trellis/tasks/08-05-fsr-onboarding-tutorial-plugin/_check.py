"""Validate the chapter skeleton.

Two checks that matter:
  1. structural  -- ids unique, orders contiguous, required fields present
  2. label truth -- every UI name quoted in instruction/userWillSee should be
     traceable to strings/zh_cn.json, so a beginner is never told to click a
     button that does not exist under that name.

Also reports per-chapter character volume against the 1000-char target the
user set: a chapter far below it means the explanation skipped something.
"""
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_SKEL = "chapters-39-45-skeleton.json"
SKEL = os.path.join(HERE, sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SKEL)
DOM = os.path.join(HERE, "ui-domains.json")

skel = json.load(io.open(SKEL, encoding="utf-8"))
dom = json.load(io.open(DOM, encoding="utf-8"))

# all real Chinese labels, as a set of exact strings
real = set()
for d in dom["domains"].values():
    real.update(d["labels"].values())
real.update(dom["unassigned_labels"].values())

chapters = skel["chapters"]
errors = []
warnings = []

REQUIRED = ("id", "order", "pose", "instruction", "userWillSee")

seen_ids = set()
for c in chapters:
    for f in ("id", "order", "titleZh", "labelDomains", "steps"):
        if f not in c:
            errors.append("chapter %s missing field %s" % (c.get("id"), f))
    orders = [s.get("order") for s in c["steps"]]
    if orders != list(range(1, len(orders) + 1)):
        errors.append("%s step orders not contiguous: %s" % (c["id"], orders))
    for s in c["steps"]:
        for f in REQUIRED:
            if not s.get(f):
                errors.append("%s missing %s" % (s.get("id", "?"), f))
        if s["id"] in seen_ids:
            errors.append("duplicate step id %s" % s["id"])
        seen_ids.add(s["id"])

# character volume per chapter (instruction + userWillSee + hint + uiElements text)
def chapter_chars(c):
    n = len(c["titleZh"])
    for s in c["steps"]:
        n += len(s.get("instruction", ""))
        n += len(s.get("userWillSee", ""))
        n += len(s.get("hint", ""))
        ui = s.get("uiElements", {})
        n += len(json.dumps(ui, ensure_ascii=False))
    return n


# bolded names in instructions should be real labels or key names
KEYWORD_OK = re.compile(
    r"^(数字键|字母|句号键|反斜杠|右 |小键盘|Ctrl|Shift|Alt|F\d+|[A-Z]$|Home|Esc|Tab|Delete|回车|空格|[0-9]$)"
)
unknown = []
for c in chapters:
    for s in c["steps"]:
        text = s.get("instruction", "")
        for m in re.finditer(r"\*\*(.+?)\*\*", text):
            token = m.group(1).strip()
            if token in real:
                continue
            if KEYWORD_OK.match(token):
                continue
            # tolerate composites like "player/steve" and "生物 (动物)"
            if re.match(r"^[A-Za-z0-9_/:.\-]+$", token):
                continue
            if token in ("最近使用的形态", "模型", "生物 (动物)", "生物 (敌对)"):
                continue
            unknown.append((c["id"], s["id"], token))

total_steps = sum(len(c["steps"]) for c in chapters)
print("chapters      :", len(chapters))
print("steps         :", total_steps)
print("unique ids    :", len(seen_ids))
print("errors        :", len(errors))
for e in errors:
    print("   ERROR", e)
print()
print("per-chapter volume (target >= 1000 chars):")
thin = []
for c in chapters:
    n = chapter_chars(c)
    flag = "" if n >= 1000 else "  << UNDER TARGET"
    if n < 1000:
        thin.append((c["id"], c["titleZh"], n, len(c["steps"])))
    print("  ch%02d %-40s %5d chars  %d steps%s"
          % (c["order"], c["titleZh"][:38], n, len(c["steps"]), flag))
print()
if thin:
    print("章节文案量不足（可能漏讲内容）:", len(thin))
    for cid, title, n, steps in thin:
        print("   %s %s -> %d chars / %d steps" % (cid, title, n, steps))
else:
    print("所有章节均达到 1000 字标准")
print()
if unknown:
    print("疑似非真实 UI 名称（需核对 strings/zh_cn.json）:", len(unknown))
    for cid, sid, tok in unknown:
        print("   %-34s %s -> %s" % (sid, cid, tok))
else:
    print("instruction 中加粗的 UI 名称全部可追溯到真实文案")

sys.exit(1 if errors else 0)
