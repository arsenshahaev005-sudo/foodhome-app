import plistlib
import unittest
from datetime import datetime, timedelta, timezone
from uuid import uuid4

from ios_export_options import export_options


def profile(bundle, *, push=False):
    return {
        "TeamIdentifier": ["TEAM123456"],
        "DeveloperCertificates": [b"fixture certificate"],
        "Entitlements": {
            "application-identifier": f"TEAM123456.{bundle}",
            "beta-reports-active": True,
            **({"aps-environment": "production"} if push else {}),
        },
        "ExpirationDate": datetime.now(timezone.utc) + timedelta(days=2),
        "UUID": str(uuid4()).upper(),
    }


class ExportOptionsTests(unittest.TestCase):
    def setUp(self):
        self.app = "market.foodhome.app"
        self.widget = self.app + ".OrderStatus"
        self.main = profile(self.app, push=True)
        self.extension = profile(self.widget)

    def options(self, audience="app-store"):
        return export_options(self.main, self.extension, app_bundle=self.app,
                              widget_bundle=self.widget, audience=audience)

    def test_both_profiles_assigned_with_expected_audience(self):
        public = self.options()
        self.assertFalse(public["testFlightInternalTestingOnly"])
        self.assertEqual(public["provisioningProfiles"], {
            self.app: self.main["UUID"], self.widget: self.extension["UUID"],
        })
        self.assertTrue(self.options("internal")["testFlightInternalTestingOnly"])
        self.assertEqual(plistlib.loads(plistlib.dumps(public)), public)

    def test_widget_must_share_certificate_and_match_identifier(self):
        self.extension["DeveloperCertificates"] = [b"a different certificate"]
        with self.assertRaises(ValueError):
            self.options()
        self.extension = profile(self.app, push=True)
        with self.assertRaises(ValueError):
            self.options()

    def test_expired_or_missing_production_push_never_exports(self):
        self.main["Entitlements"]["aps-environment"] = "development"
        with self.assertRaises(ValueError):
            self.options()
        self.main = profile(self.app, push=True)
        self.extension["ExpirationDate"] = datetime(2020, 1, 1)
        with self.assertRaises(ValueError):
            self.options()

    def test_staging_requires_the_named_profiles_and_internal_export(self):
        self.app = "market.foodhome.app.staging"
        self.widget = self.app + ".OrderStatus"
        self.main = {**profile(self.app, push=True), "Name": "FoodHome Staging AppStore"}
        self.extension = {**profile(self.widget), "Name": "FoodHome Staging OrderStatus AppStore"}
        options = self.options("internal")
        self.assertIs(options["testFlightInternalTestingOnly"], True)
        self.assertEqual(set(options["provisioningProfiles"]), {self.app, self.widget})
        with self.assertRaises(ValueError):
            self.options("app-store")
        self.extension["Name"] = "FoodHome OrderStatus AppStore"
        with self.assertRaises(ValueError):
            self.options("internal")

    def test_mixing_staging_and_production_components_fails_before_export(self):
        self.app = "market.foodhome.app.staging"
        self.main = profile(self.app, push=True)
        with self.assertRaises(ValueError):
            self.options("internal")


if __name__ == "__main__":
    unittest.main()
