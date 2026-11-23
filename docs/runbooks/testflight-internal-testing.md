# Internal iOS testing

Build and sign only from an organization-controlled protected workflow or Mac. Upload the exact reviewed commit, keep credentials outside pull-request jobs, distribute to an internal group, record installation and launch results, and revoke obsolete builds when testing is complete.

In GitHub Actions, select **Internal TestFlight → Run workflow → main**. Record the commit shown on the run. The upload job archives the release for iPhone using the distribution identity and matching profile from the `testflight` environment. Xcode marks the upload for internal testing only. The build number consists of the workflow run number and attempt.

The confirmation job checks the app, version, build number and audience in App Store Connect. Its summary links to the processed build and reports the internal testing status. If Apple is still processing an accepted upload when this job times out, re-run only the failed confirmation job. In App Store Connect, open the build under **TestFlight → iOS** and assign it to the intended internal group.

The macOS job uses the environment secrets `BUILD_CERTIFICATE_BASE64`, `P12_PASSWORD` and `BUILD_PROVISION_PROFILE_BASE64` for signing. App Store Connect authentication uses the PEM secret `APP_STORE_CONNECT_PRIVATE_KEY` and the environment variables `APP_STORE_CONNECT_KEY_ID` and `APP_STORE_CONNECT_ISSUER_ID`.
