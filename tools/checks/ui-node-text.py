#!/usr/bin/env python3
# Language of the interface (plan UCH-81 section 3): the node whose resource-id is the test tag shows the value
# of the string resource <name> from the given strings.xml (read from the resource, never written into the
# script). The text of the node itself, its content-desc or the text of a descendant counts. Exit 1 otherwise.
# Usage: python3 -I ui-node-text.py <window.xml> <tag> <strings.xml> <name>
import sys
import xml.etree.ElementTree as ET

path, tag, strings, name = sys.argv[1:5]
values = [s for s in ET.parse(strings).getroot().iter("string") if s.get("name") == name]
if len(values) != 1:
    print(f"ui-node-text: string {name} not found exactly once in {strings}", file=sys.stderr)
    sys.exit(2)
# Android escapes in string resources: \' \" \\ and \n.
expected = "".join(values[0].itertext()).strip().replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
expected = expected.replace("\\\\", "\\")
for n in ET.parse(path).getroot().iter("node"):
    rid = n.get("resource-id", "")
    if rid == tag or rid.endswith(":id/" + tag):
        shown = [x for d in n.iter("node") for x in (d.get("text", ""), d.get("content-desc", "")) if x]
        if expected in shown:
            print(f"ui-node-text: {tag} shows {expected!r} ({name} from {strings})")
            sys.exit(0)
        print(f"ui-node-text: wrong language: {tag} shows {shown}, expected {expected!r} ({name} from {strings})",
              file=sys.stderr)
        sys.exit(1)
print(f"ui-node-text: {tag} missing in {path}", file=sys.stderr)
sys.exit(1)
