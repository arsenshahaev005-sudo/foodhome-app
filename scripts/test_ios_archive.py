import plistlib
import tempfile
import unittest
from pathlib import Path

from verify_ios_archive import verify_archive


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.app = self.root / "FoodHomeApp.app"
        self.widget = self.app / "PlugIns" / "FoodHomeOrderStatus.appex"
        self.widget.mkdir(parents=True)
        self.signing = self.root / "signing"
        self.signing.mkdir()

    def fixture(self, staging=True):
        bundle = "market.foodhome.app.staging" if staging else "market.foodhome.app"
        shared = {"CFBundleVersion": "7.1", "CFBundleShortVersionString": "1.0",
                  "FoodHomeWebHost": "staging.foodhome.market" if staging else "foodhome.market"}
        self.write(self.app / "Info.plist", {
            **shared, "CFBundleIdentifier": bundle,
            "CFBundleDisplayName": "Food&Home Staging" if staging else "Food&Home",
            "FoodHomeAPSEnvironment": "production", "NSSupportsLiveActivities": True,
            "ITSAppUsesNonExemptEncryption": False, "CFBundleURLTypes": [{"CFBundleURLSchemes": [bundle + ".auth"]}],
        })
        self.write(self.widget / "Info.plist", {**shared, "CFBundleIdentifier": bundle + ".OrderStatus",
                   "NSExtension": {"NSExtensionPointIdentifier": "com.apple.widgetkit-extension"}})
        self.write(self.signing / "ExportOptions.plist", {"teamID": "TESTTEAM", "testFlightInternalTestingOnly": staging,
                   "provisioningProfiles": {bundle: "main-fixture", bundle + ".OrderStatus": "widget-fixture"}})
        for kind, suffix, uuid in (("profile", "", "main-fixture"), ("widget", ".OrderStatus", "widget-fixture")):
            self.write(self.signing / (kind + "-entitlements.plist"), {
                "application-identifier": "TESTTEAM." + bundle + suffix, "com.apple.developer.team-identifier": "TESTTEAM",
                **({"aps-environment": "production"} if not suffix else {}),
            })
            self.write(self.signing / (kind + "-embedded.plist"), {"UUID": uuid})

    @staticmethod
    def write(path, value):
        path.write_bytes(plistlib.dumps(value))

    def test_both_compiled_and_signed_variants(self):
        for kind, audience in (("production", "app-store"), ("staging", "internal")):
            with self.subTest(kind=kind):
                self.fixture(kind == "staging")
                self.assertEqual(verify_archive(self.app, kind, audience), "1.0")
                self.assertEqual(verify_archive(self.app, kind, audience, build="7.1", signing_dir=self.signing), "1.0")

    def test_rejects_wrong_host_callbacks_and_signed_entitlements(self):
        for path, field, value in [
            (self.app / "Info.plist", "CFBundleDisplayName", "Food&Home"),
            (self.widget / "Info.plist", "FoodHomeWebHost", "foodhome.market"),
            (self.app / "Info.plist", "CFBundleURLTypes", [{"CFBundleURLSchemes": ["market.foodhome.app.auth"]}]),
            (self.widget / "Info.plist", "CFBundleIdentifier", "market.foodhome.app.OrderStatus"),
            (self.app / "Info.plist", "FoodHomeAPSEnvironment", "development"),
            (self.widget / "Info.plist", "CFBundleVersion", "7.2"),
            (self.signing / "profile-entitlements.plist", "aps-environment", "development"),
            (self.signing / "widget-entitlements.plist", "application-identifier", "TESTTEAM.market.foodhome.app.OrderStatus"),
            (self.signing / "profile-entitlements.plist", "get-task-allow", True),
            (self.signing / "widget-embedded.plist", "UUID", "other"),
            (self.signing / "ExportOptions.plist", "testFlightInternalTestingOnly", False),
        ]:
            with self.subTest(path=path.name, field=field):
                self.fixture()
                data = plistlib.loads(path.read_bytes())
                data[field] = value
                self.write(path, data)
                with self.assertRaises(ValueError):
                    verify_archive(self.app, "staging", "internal", build="7.1", signing_dir=self.signing)


if __name__ == "__main__":
    unittest.main()
