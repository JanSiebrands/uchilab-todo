#!/usr/bin/env python3
# Fails (exit 1) if a node whose resource-id is one of the test tags is missing from the UI dump ("missing:") or
# it or a node inside it has focused="true" ("focused:"). Plan UCH-99 section 2: the buttons of the discard dialog must not show a focus
# frame in the picture, as for a user who opens the dialog with a finger.
# Usage: python3 -I ui-node-unfocused.py <window.xml> <tag>...
import sys
import xml.etree.ElementTree as ET

path, tags = sys.argv[1], sys.argv[2:]
if not tags:
    sys.exit("usage: ui-node-unfocused.py <window.xml> <tag>...")
nodes = list(ET.parse(path).getroot().iter("node"))
failed = False
for tag in tags:
    hits = [n for n in nodes if n.get("resource-id", "") == tag or n.get("resource-id", "").endswith(":id/" + tag)]
    if not hits:
        print(f"missing: {tag}")
        failed = True
    elif any(d.get("focused") == "true" for n in hits for d in n.iter("node")):
        print(f"focused: {tag}")
        failed = True
    else:
        print(f"no focus: {tag}")
sys.exit(1 if failed else 0)
