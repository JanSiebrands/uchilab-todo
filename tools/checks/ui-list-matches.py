#!/usr/bin/env python3
# List check of the screenshots job (plan UCH-81 section 4, moved out of emulator-showcase.sh by plan UCH-99
# section 1 so counterprobe 16u can run it without an emulator): all titles of the seed in order (tops strictly
# increasing), every note of the seed shown in its entry and no entry without a note in the seed shows a note
# line. Prints "missing:", "note not shown:", "note shown for a task without a note:" or "wrong order:" and
# exits 1, otherwise prints "all N titles in order ..." and exits 0.
# Usage: python3 -I ui-list-matches.py <window.xml> <tasks.json> <item_tag>
import json, re, sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
tasks = json.load(open(sys.argv[2], encoding="utf-8"))
items = []
for n in root.iter("node"):
    rid = n.get("resource-id", "")
    if rid == sys.argv[3] or rid.endswith(":id/" + sys.argv[3]):
        text = "\n".join(x for d in n.iter("node") for x in (d.get("text", ""), d.get("content-desc", "")) if x)
        top = int(re.findall(r"-?\d+", n.get("bounds", ""))[1])
        items.append((text, top))
tops = []
for t in tasks:
    hits = [(text, top) for text, top in items if t["title"] in text]
    if not hits:
        print(f"missing: {t['title']}")
        sys.exit(1)
    text, top = hits[0]
    tops.append(top)
    lines = [l for l in (t["note"] or "").splitlines() if l]
    if lines and lines[0] not in text:
        print(f"note not shown: {t['title']}")
        sys.exit(1)
others = [l for o in tasks for l in (o["note"] or "").splitlines() if l]
for t, (text, _) in ((t, [h for h in items if t["title"] in h[0]][0]) for t in tasks if not t["note"]):
    if any(l in text for l in others):
        print(f"note shown for a task without a note: {t['title']}")
        sys.exit(1)
if any(a >= b for a, b in zip(tops, tops[1:])):
    print("wrong order: " + ", ".join(f"{t['title']}@{y}" for t, y in zip(tasks, tops)))
    sys.exit(1)
with_note = sum(1 for t in tasks if t["note"])
print(f"all {len(tasks)} titles in order, {with_note} with note shown, {len(tasks) - with_note} without")
