#!/usr/bin/env python3
# Readable seed of the screenshots (plan UCH-81 section 4, Z4): titles.txt from ShowcaseSeedTest holds at least
# one title, and no title looks technical (Seed-, UI-, Draft- or a token of 8 hex characters, the run ids of
# the emulator checks). If tasks.json lies next to it: same titles in the same order, at least one task with a
# note over more than one line and at least one task without a note. Exit 1 otherwise.
# Usage: python3 -I showcase-seed-check.py <titles.txt>
import json
import os
import re
import sys

TECHNICAL = re.compile(r"Seed-|UI-|Draft-|(?<![0-9A-Za-z])[0-9a-fA-F]{8}(?![0-9A-Za-z])")


def fail(message):
    print(f"showcase-seed-check: {sys.argv[1]}: {message}", file=sys.stderr)
    sys.exit(1)


titles = [t for t in open(sys.argv[1], encoding="utf-8").read().splitlines() if t]
if not titles:
    fail("no titles")
technical = [t for t in titles if TECHNICAL.search(t)]
if technical:
    fail(f"technical title(s), not readable: {technical}")
tasks_file = os.path.join(os.path.dirname(sys.argv[1]), "tasks.json")
if os.path.exists(tasks_file):
    tasks = json.load(open(tasks_file, encoding="utf-8"))
    if [t["title"] for t in tasks] != titles:
        fail(f"tasks.json and titles.txt differ")
    if not any(t["note"] and "\n" in t["note"] for t in tasks):
        fail("no task with a note over more than one line")
    if all(t["note"] for t in tasks):
        fail("no task without a note")
print(f"showcase-seed-check: {sys.argv[1]}: OK, {len(titles)} readable titles")
