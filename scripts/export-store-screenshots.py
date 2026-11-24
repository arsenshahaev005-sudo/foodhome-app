"""Export the two original Simulator screens as full-size, opaque App Store JPEGs."""

import argparse
import json
import re
import struct
import subprocess
from pathlib import Path

SCREENS = ("01-catalog", "02-dish")
STORE_SIZES = {"medium": (1206, 2622), "large": (1320, 2868)}


def select_screens(source, display="large"):
    width, height = STORE_SIZES[display]
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
                if struct.unpack(">II", header[16:24]) != (width, height):
                    raise ValueError(f"{screen} is not a {width} x {height} iPhone portrait screenshot")
                matches[screen] = file
    if set(matches) != set(SCREENS):
        raise ValueError("the successful test must provide both the catalog and dish screenshots")
    return matches


def export(source, destination, display="large"):
    matches = select_screens(source, display)
    destination.mkdir(parents=True, exist_ok=True)
    for screen in SCREENS:
        target = destination / f"{screen}.jpg"
        subprocess.run(
            [
                "/usr/bin/sips", "-s", "format", "jpeg", "-s", "formatOptions", "best",
                str(matches[screen]), "--out", str(target),
            ],
            check=True,
        )
        info = subprocess.check_output(
            ["/usr/bin/sips", "-g", "format", "-g", "pixelWidth", "-g", "pixelHeight", str(target)],
            text=True,
        )
        width, height = STORE_SIZES[display]
        properties = {line.strip() for line in info.splitlines()}
        if not {f"pixelWidth: {width}", f"pixelHeight: {height}", "format: jpeg"}.issubset(properties):
            raise ValueError(f"{screen} JPEG does not match the selected display")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--display", choices=tuple(STORE_SIZES), default="large")
    args = parser.parse_args()
    export(args.source, args.destination, args.display)
