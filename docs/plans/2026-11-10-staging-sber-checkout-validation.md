# Staging Sber checkout validation

The cart selection, checkout totals, neutral Continue label and address sheet are delivered by the existing web product. Keep those rules in the website. Android needs the Sber Custom Tabs/App Link integration already in this branch; extend that PR rather than creating a duplicate or publishing a production APK.

1. Make the Android PR workflow test, lint and assemble the isolated staging variant alongside the existing production-variant checks. Keep the existing release checks intact.
2. Rebuild locally, run the canonical bridge and shell checks, and compare the APK identity/signing fingerprint with the public staging App Link association. Retain owner-local Gradle/debug files outside the commit.
3. Update the integration instructions with the current staging acceptance path. Record physical Sber login/return and process restart as manual checks until observed on a device.
4. Commit only these changes to the existing PR, check its CI, and keep merging, production activation and publication outside this task.

No pricing, delivery-compensation or production push-compatibility changes are required.
