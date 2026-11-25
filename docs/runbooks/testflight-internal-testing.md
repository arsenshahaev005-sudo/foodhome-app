# Internal iOS testing

Build and sign only from an organization-controlled protected workflow or Mac. Upload the exact reviewed commit, keep credentials outside pull-request jobs, distribute to an internal group, record installation and launch results, and revoke obsolete builds when testing is complete.

In GitHub Actions, select **iOS distribution → Run workflow → main**. Set `operation=upload` and choose the target and audience.

| Installed app | Target | Audience | Website |
| --- | --- | --- | --- |
| Food&Home | `production` (default) | `internal` (default) or `app-store` | `foodhome.market` |
| Food&Home Staging | `staging` | `internal` | `staging.foodhome.market` |

For the separate staging app, run `gh workflow run testflight.yml --ref main -f target=staging -f audience=internal -f operation=upload`. Apple uses the App Store Connect record for `market.foodhome.app.staging` and the widget identifier `market.foodhome.app.staging.OrderStatus`. Create the staging app record before uploading and assign its build through that record's internal testing group. Both apps can be installed on the same phone. The staging export sets `testFlightInternalTestingOnly=true`.

The archive includes FoodHomeApp and the FoodHomeOrderStatus widget. The job validates the distribution certificate, bundle IDs, production APNs entitlement, Live Activities setting, widget extension point and matching version numbers. Record the commit shown on the run. The build number consists of the workflow run number and attempt.

The confirmation job checks the app, iOS marketing version, build number and audience in App Store Connect. Its summary links to the processed build and reports the internal testing status. If Apple is still processing an accepted upload, re-run the confirmation job or start `operation=confirm` with the original `target`, `audience`, `version` and `build`. This operation only queries Apple. In App Store Connect, open the build under **TestFlight → iOS** and assign it to the intended internal group.

Both targets use the existing distribution identity from `BUILD_CERTIFICATE_BASE64` and `P12_PASSWORD` inside the GitHub `testflight` environment. Production reads `BUILD_PROVISION_PROFILE_BASE64` and `WIDGET_PROVISION_PROFILE_BASE64`. Staging reads the active App Store profiles `FoodHome Staging AppStore` and `FoodHome Staging OrderStatus AppStore` directly from Apple using the existing `APP_STORE_CONNECT_PRIVATE_KEY`, `APP_STORE_CONNECT_KEY_ID` and `APP_STORE_CONNECT_ISSUER_ID`. The job verifies the certificate against both profiles, checks the entitlements of the signed products and checks the built app's host and callback scheme before upload.
