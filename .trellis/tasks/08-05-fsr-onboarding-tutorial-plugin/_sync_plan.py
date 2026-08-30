"""Sync chapter-plan.json with the revised ch46-52 skeleton: bump orders >=46 by +1,
insert the new ch46_export_basics row, update totals."""
import io
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
PLAN = os.path.join(HERE, "chapter-plan.json")
SKEL = os.path.join(HERE, "chapters-46-52-skeleton.json")

skel = json.load(io.open(SKEL, encoding="utf-8"))
new_ch = next(c for c in skel["chapters"] if c["id"] == "ch46_export_basics")
new_title = new_ch["titleZh"]
new_steps = len(new_ch["steps"])
print("new chapter from skeleton: order", new_ch["order"], "|", new_title, "|", new_steps, "steps")

plan = json.load(io.open(PLAN, encoding="utf-8"))
meta = plan["_meta"]

# bump orders >= 46 (delivery/system tail) to make room, then insert
for c in plan["chapters"]:
    if c["order"] >= 46:
        old = c.pop("order")
        c["order"] = old + 1
        c["id"] = "ch%02d" % (old + 1)

plan["chapters"].append({
    "order": 46,
    "id": "ch46",
    "stage": "导出",
    "titleZh": new_title,
    "labelDomains": ["video_export"],
    "availableLabels": 94,
    "plannedSteps": new_steps,
    "status": "skeleton",
})
plan["chapters"].sort(key=lambda c: c["order"])

meta["totalChapters"] = len(plan["chapters"])
meta["revisionNote"] = (
    "2026-08-23 content audit: inserted ch46_export_basics (video export basics was "
    "silently dropped from the original plan); orders of former ch46-ch52 shifted to "
    "ch47-ch53. Skeleton step ids keep their original chNN prefixes on purpose."
)

io.open(PLAN, "w", encoding="utf-8").write(
    json.dumps(plan, ensure_ascii=False, indent=2) + "\n"
)
print("chapter-plan.json updated:", meta["totalChapters"], "chapters")
for c in plan["chapters"][44:]:
    print(" ", c["order"], c["id"], c["titleZh"])
