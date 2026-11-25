#!/usr/bin/env python3
"""Helpers to keep Android <string> resources sane after Weblate merges.

The fork repo has GitHub Issues disabled, so CI must never depend on the
Issues API. Duplicate-key problems are fixed and reported through stdout,
GitHub annotations and the step summary instead.

Usage:
    strings-tools.py dedupe <strings.xml> [more...]
    strings-tools.py verify <strings.xml> [more...]
"""

import re
import sys
import xml.etree.ElementTree as ET

STRING_RE = re.compile(r'^\s*<string\s+name="([^"]+)"')


def dedupe(paths):
    total = 0
    for path in paths:
        with open(path, encoding="utf-8") as fh:
            lines = fh.readlines()

        seen, kept, removed = set(), [], []
        for lineno, line in enumerate(lines, 1):
            match = STRING_RE.match(line)
            if match:
                key = match.group(1)
                if key in seen:
                    removed.append(f"{key}@{lineno}")
                    continue
                seen.add(key)
            kept.append(line)

        if removed:
            with open(path, "w", encoding="utf-8") as fh:
                fh.writelines(kept)
            total += len(removed)
            print(f"{path}: removed {len(removed)} duplicate key(s): {', '.join(removed)}")

    return 0 if total == 0 else 0


def verify(paths):
    failed = False
    for path in paths:
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            print(f"::error file={path}::malformed XML after merge: {exc}")
            failed = True
            continue

        seen, dupes = set(), []
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                match = STRING_RE.match(line)
                if match:
                    key = match.group(1)
                    if key in seen:
                        dupes.append(key)
                    seen.add(key)

        if dupes:
            unique = sorted(set(dupes))
            print(f"::error file={path}::duplicate string keys remain: {', '.join(unique)}")
            failed = True

    return 1 if failed else 0


def main(argv):
    if len(argv) < 3 or argv[1] not in ("dedupe", "verify"):
        print(__doc__, file=sys.stderr)
        return 2

    action, paths = argv[1], argv[2:]
    return dedupe(paths) if action == "dedupe" else verify(paths)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
