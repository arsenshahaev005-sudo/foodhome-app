import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

module_spec = importlib.util.spec_from_file_location(
    "store_screenshots", Path(__file__).with_name("export-store-screenshots.py")
)
screenshots = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(screenshots)


class StoreScreenshotTests(unittest.TestCase):
    def make_attachments(self, folder, size):
        attachments = []
        for name in screenshots.SCREENS:
            file = folder / f"{name}.png"
            file.write_bytes(b"\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR" + struct.pack(">II", *size))
            attachments.append({
                "suggestedHumanReadableName": f"foodhome-store-{name}_0.png",
                "exportedFileName": file.name,
            })
        (folder / "manifest.json").write_text(json.dumps([{"attachments": attachments}]), encoding="utf-8")

    def test_verifies_each_display_without_scaling_other_screens(self):
        for display, size, other in [("medium", (1206, 2622), "large"), ("large", (1320, 2868), "medium")]:
            with self.subTest(display=display), tempfile.TemporaryDirectory() as temp:
                folder = Path(temp)
                self.make_attachments(folder, size)
                self.assertEqual(set(screenshots.select_screens(folder, display)), set(screenshots.SCREENS))
                with self.assertRaisesRegex(ValueError, "iPhone portrait screenshot"):
                    screenshots.select_screens(folder, other)

    def test_rejects_wrong_deliverable_size_after_conversion(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            self.make_attachments(folder, (1206, 2622))
            with patch.object(screenshots.subprocess, "run"), patch.object(
                screenshots.subprocess, "check_output",
                return_value="format: jpeg\npixelWidth: 1320\npixelHeight: 2868\n",
            ), self.assertRaisesRegex(ValueError, "JPEG does not match"):
                screenshots.export(folder, folder / "out", "medium")


if __name__ == "__main__":
    unittest.main()
