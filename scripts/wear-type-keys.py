#!/usr/bin/env python3
"""Type on the Wear OS keyboard by tapping its on-screen keys.

    scripts/wear-type-keys.py <device-serial> "<text>"

`adb shell input text` is no use on a watch: the Wear keyboard shows the
injected text but never returns it to the app. This reads the keyboard's keys
from the layout tree once and taps them, so it works at any screen size.

The keyboard must already be open on its letter layout. Only letters and
spaces are supported, and the keyboard capitalises the first letter itself.
Needs the `android` CLI and `adb` on the PATH.
"""

import json
import subprocess
import sys
import time


def keys(serial: str) -> dict[str, tuple[str, str]]:
    layout = subprocess.run(
        ["android", "layout", f"--device={serial}"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    found: dict[str, tuple[str, str]] = {}

    def walk(node: object) -> None:
        if isinstance(node, dict):
            label = node.get("content-desc") or ""
            center = node.get("center")
            if center:
                x, y = center.strip("[]").split(",")
                if len(label) == 1 and label.isalpha():
                    found.setdefault(label.lower(), (x, y))
                elif label.startswith("Space"):
                    found.setdefault(" ", (x, y))
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(json.loads(layout))
    return found


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    serial, text = sys.argv[1], sys.argv[2].lower()
    found = keys(serial)
    missing = sorted({ch for ch in text if ch not in found})
    if missing:
        print(f"No key on screen for {missing}. Is the keyboard open on its letter layout?", file=sys.stderr)
        return 1
    for ch in text:
        x, y = found[ch]
        subprocess.run(["adb", "-s", serial, "shell", "input", "tap", x, y], check=True)
        time.sleep(0.2)
    return 0


if __name__ == "__main__":
    sys.exit(main())
