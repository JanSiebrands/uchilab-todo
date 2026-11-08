#!/usr/bin/env python3
# Fails (exit 1) unless the node whose resource-id is the test tag is in the UI dump, has an area above 0 and
# lies completely inside the display (plan UCH-81 section 5: "Save" visible at 200 % font and in landscape).
# Usage: python3 -I ui-node-visible.py <window.xml> <tag> <display width> <display height>
import re
import sys
import xml.etree.ElementTree as ET

path, tag, width, height = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
for n in ET.parse(path).getroot().iter("node"):
    rid = n.get("resource-id", "")
    if rid == tag or rid.endswith(":id/" + tag):
        left, top, right, bottom = map(int, re.findall(r"-?\d+", n.get("bounds", "")))
        if right <= left or bottom <= top:
            print(f"ui-node-visible: {tag} has zero area: bounds {n.get('bounds')}", file=sys.stderr)
            sys.exit(1)
        if left < 0 or top < 0 or right > width or bottom > height:
            print(f"ui-node-visible: {tag} outside the display {width}x{height}: bounds {n.get('bounds')}",
                  file=sys.stderr)
            sys.exit(1)
        print(f"ui-node-visible: {tag} visible: bounds {n.get('bounds')} inside {width}x{height}")
        sys.exit(0)
print(f"ui-node-visible: {tag} missing in {path}", file=sys.stderr)
sys.exit(1)
