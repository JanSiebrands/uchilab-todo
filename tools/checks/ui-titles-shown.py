"""Emulator list rule (UCH-147): every expected title is shown in the UI dump, in text or content-desc.

Usage: python3 -I ui-titles-shown.py <window.xml> <titles.txt>. Exit 0 if all are shown, 1 otherwise.
Order and grouping are not checked here (TaskListIntegrationTest in the app module).
Unit test: tools/checks/src/test/kotlin/de/uchilab/checks/UiTitlesShownTest.kt.
"""
import sys
import xml.etree.ElementTree as ET

shown = [n.get("text", "") + "\n" + n.get("content-desc", "") for n in ET.parse(sys.argv[1]).getroot().iter("node")]
titles = [t for t in open(sys.argv[2], encoding="utf-8").read().splitlines() if t]
missing = [t for t in titles if not any(t in text for text in shown)]
if missing:
    print("missing: " + ", ".join(missing))
    sys.exit(1)
print("all titles shown: " + ", ".join(titles))
