"""Print the real zh_cn UI labels of one or more domains.

Chapter writers must quote only labels that exist, so this is the vocabulary
lookup: `python _labels.py video_export importer` dumps every key -> label pair
those chapters are allowed to reference. Pass `--grep <substr>` to filter.
"""
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DOM = os.path.join(HERE, "ui-domains.json")

args = [a for a in sys.argv[1:]]
grep = None
if "--grep" in args:
    i = args.index("--grep")
    grep = args[i + 1]
    args = args[:i] + args[i + 2:]

dom = json.load(io.open(DOM, encoding="utf-8"))

if not args:
    print("domains:")
    for name, d in dom["domains"].items():
        print("  %-18s %4d labels" % (name, d["count"]))
    print("\nusage: python _labels.py <domain> [<domain>...] [--grep substr]")
    sys.exit(0)

for name in args:
    if name == "unassigned":
        labels = dom["unassigned_labels"]
    elif name in dom["domains"]:
        labels = dom["domains"][name]["labels"]
    else:
        print("!! unknown domain: %s" % name)
        continue

    print("=== %s (%d labels)" % (name, len(labels)))
    for key, value in labels.items():
        if grep and grep not in key and grep not in value:
            continue
        print("  %-62s %s" % (key, value))
    print()
