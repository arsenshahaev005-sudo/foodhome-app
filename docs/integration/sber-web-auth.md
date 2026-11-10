# Sber buyer login and isolated Android staging build

The website initiates a browser-bound Sber authorization attempt. Android advertises the additive `startSberAuth` method in bridge contract 1.12.0 only in the staging build. The method accepts an attempt UUID and a bounded HTTPS Sber authorize URL; the callback must match this build's trusted origin. It opens Custom Tabs, never bank authentication in the WebView.

The bank callback stays `/auth/sber/callback`. The server verifies the code/PKCE, signed ID token, nonce, subject and phone, then returns a separate first-party `/auth/sber/app-return` App Link with a short-lived ticket in its fragment. Android checks the saved attempt and exact origin, queues navigation until its original trusted WebView is ready, and the website exchanges the ticket using the original HttpOnly browser cookie. No provider token, site JWT or password crosses the bridge. The ticket alone cannot authenticate an external browser.

Only the attempt UUID and five-minute expiry are stored in private native preferences. UI checkout selection has a bounded web storage recovery hint for process restart. The server remains authoritative for ticket expiry, replay, browser ownership, role, consent and account matching. Cancel/failure does not create an order.

Build with:

```powershell
./android/gradlew.bat -p android '-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false' testDebugUnitTest testStagingUnitTest lintStaging assembleStaging --no-daemon
```

The staging APK is `market.foodhome.app.staging`, displays Food&Home Staging, uses only `https://staging.foodhome.market`, disables native push and direct APK updates, and has its own exact App Link host. Production signing, update publication and server compatibility tuples are unchanged. Production binaries keep Sber web auth disabled until a separately approved activation.

## Web changes and native updates

Cart checkboxes, selected checkout totals, the neutral Continue button and the address sheet belong to the remote web product. They arrive when that environment's website is deployed; do not duplicate their state or pricing rules in the native shell. An installed production app continues using the production website. Use the isolated staging APK to test the staging website.

This Android update is needed for the `startSberAuth` Custom Tabs launch and App Link return. Email and Yandex remain web authentication alternatives. The iOS shell does not advertise `startSberAuth`; adding iOS Sber authorization requires separate implementation and device acceptance.

## Verification refreshed on 2026-11-10

- `testDebugUnitTest testStagingUnitTest lintStaging assembleStaging` succeeded with the command above. Current unit-test reports contain 181 tests per variant and no failures or skips; Gradle reused unchanged outputs on this verification run.
- All 33 canonical contract tests passed. Server push contract hashes, expanded-push tests, secure-shell and phase 2-5 checks also passed.
- The Android PR workflow now tests, lints and assembles staging alongside its existing debug/release and emulator checks. It explicitly enables tests for both build types.
- The APK is version `0.2.15-staging`, version code `17`, package `market.foodhome.app.staging`. Its SHA-256 is `5762614a12cbc2c79f6bd02e6dece1d73da265c0df295cab0e58387f954c8ed2`.
- APK signature verification succeeded, and its signing certificate matches the public association returned with HTTP 200 by `https://staging.foodhome.market/.well-known/assetlinks.json`. A build signed with another debug key needs its own approved staging association; never reuse the production signing key for this test.
- Generated staging build flags enable Sber, disable native push and APK updates, and preserve the staging-only origin.

Bank credentials and certificates remain backend-only. Build success and a matching public association do not prove a completed bank login or an installed device's domain verification. Keep `sber_available=false` while backend configuration is incomplete and retain email/Yandex alternatives.

## Installed-device acceptance

Install `android/app/build/outputs/apk/staging/app-staging.apk` as the separate Food&Home Staging app; it can coexist with the production app. Use staging test accounts and stop before any real payment.

1. Check the installed App Link association (Android 12 and newer):

   ```powershell
   adb shell pm verify-app-links --re-verify market.foodhome.app.staging
   adb shell pm get-app-links market.foodhome.app.staging
   ```

   Wait for the verifier result and confirm that `staging.foodhome.market` is verified. On older supported Android versions, use the OS supported-links settings and verify the real return. A matching server JSON alone is insufficient.
2. Keep old dish A in an existing account's cart, log out, add guest dishes B and C, and check only B. Sign in through Sber from checkout. Verify that only B is selected for checkout, its quantity is preserved, and old A and unchecked C do not join the order. Repeat the web flow through email and Yandex, including first account creation and required consent.
3. Toggle checked items before checkout and confirm the preview follows the authoritative quote for that selection. Existing fee and delivery-compensation rules still apply; no native price calculation is added.
4. Check the neutral Continue label, retained email/Yandex methods and seller password login. With no selected address, adding a dish must first show the address sheet; dismissing it adds nothing, confirming an address continues the original add once.
5. Complete a real Sber Custom Tabs login and verify return into the same staging WebView. Repeat after backgrounding the app and after process recreation. A cancelled or expired attempt must not create an order; a mismatched origin or attempt must not grant a session.

Record the device, Android/WebView versions, APK hash and observed outcomes. Real bank login, installed-device App Link verification and physical return/process-recreation checks remain pending until observed. PR merge readiness and production activation are separate from assembling this staging candidate.
