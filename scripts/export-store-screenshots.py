"""Export the two original Simulator screens as full-size, opaque App Store JPEGs."""

import json
import re
import struct
import subprocess
import sys
from pathlib import Path

SCREENS = ("01-catalog", "02-dish")
STORE_SIZE = (1320, 2868)


def select_screens(source):
    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    matches = {}
    for test in manifest:
        for attachment in test["attachments"]:
            label = attachment.get("suggestedHumanReadableName", "")
            for screen in SCREENS:
                if not re.match(r"^foodhome-store-" + re.escape(screen) + r"(?:[\s_.-]|$)", label):
                    continue
                if screen in matches:
                    raise ValueError(f"duplicate attachment for {screen}")
                file = (source / attachment["exportedFileName"]).resolve()
                if file.parent != source.resolve():
                    raise ValueError("unexpected exported attachment path")
                with file.open("rb") as image:
                    header = image.read(24)
                if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
                    raise ValueError(f"{screen} is not a Simulator PNG")
                if struct.unpack(">II", header[16:24]) != STORE_SIZE:
                    raise ValueError(f"{screen} is not a 1320 x 2868 iPhone portrait screenshot")
                matches[screen] = file
    if set(matches) != set(SCREENS):
        raise ValueError("the successful test must provide both the catalog and dish screenshots")
    return matches


def export(source, destination):
    matches = select_screens(source)
    destination.mkdir(parents=True, exist_ok=True)
    for screen in SCREENS:
        subprocess.run(
            [
                "/usr/bin/sips", "-s", "format", "jpeg", "-s", "formatOptions", "best",
                str(matches[screen]), "--out", str(destination / f"{screen}.jpg"),
            ],
            check=True,
        )


if __name__ == "__main__":
    export(Path(sys.argv[1]), Path(sys.argv[2]))
