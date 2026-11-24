# Internal iOS testing

Build and sign only from an organization-controlled protected workflow or Mac. Upload the exact reviewed commit, keep credentials outside pull-request jobs, distribute to an internal group, record installation and launch results, and revoke obsolete builds when testing is complete.

In GitHub Actions, select **iOS distribution → Run workflow → main**. Choose `internal` for internal-only testing or `app-store` to prepare a build eligible for App Review. The latter can also be tested by the internal group. Both choices upload to App Store Connect. Submission for review is a separate action in the store.

The archive includes FoodHomeApp and the FoodHomeOrderStatus widget. The job validates the distribution certificate, bundle IDs, production APNs entitlement, Live Activities setting, widget extension point and matching version numbers. Record the commit shown on the run. The build number consists of the workflow run number and attempt.

The confirmation job checks the app, version, build number and audience in App Store Connect. Its summary links to the processed build and reports the internal testing status. If Apple is still processing an accepted upload when this job times out, re-run only the failed confirmation job. In App Store Connect, open the build under **TestFlight → iOS** and assign it to the intended internal group.

The macOS job uses the environment secrets `BUILD_CERTIFICATE_BASE64`, `P12_PASSWORD`, `BUILD_PROVISION_PROFILE_BASE64` and `WIDGET_PROVISION_PROFILE_BASE64` for signing. The two profiles belong to `market.foodhome.app` and `market.foodhome.app.OrderStatus` and share a distribution certificate. App Store Connect authentication uses the PEM secret `APP_STORE_CONNECT_PRIVATE_KEY` and the environment variables `APP_STORE_CONNECT_KEY_ID` and `APP_STORE_CONNECT_ISSUER_ID`.
