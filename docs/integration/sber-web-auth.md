# Sber buyer login and isolated Android staging build

The website initiates a browser-bound Sber authorization attempt. Android advertises the additive `startSberAuth` method in bridge contract 1.12.0 in staging and in the owner-approved production release0.2.16. The method accepts an attempt UUID and a bounded HTTPS Sber authorize URL; the callback must match this build's trusted origin. It opens Custom Tabs, never bank authentication in the WebView.

The bank callback stays `/auth/sber/callback`. The server verifies the code/PKCE, the explicitly configured ID-token trust source, all required token claims, nonce, subject and phone, then returns a separate first-party `/auth/sber/app-return` App Link with a short-lived ticket in its fragment. Android checks the saved attempt and exact origin, queues navigation until its original trusted WebView is ready, and the website exchanges the ticket using the original HttpOnly browser cookie. No provider token, site JWT or password crosses the bridge. The ticket alone cannot authenticate an external browser.

Only the attempt UUID and five-minute expiry are stored in private native preferences. UI checkout selection has a bounded web storage recovery hint for process restart. The server remains authoritative for ticket expiry, replay, browser ownership, role, consent and account matching. Cancel/failure does not create an order.

Build with:

```powershell
./android/gradlew.bat -p android '-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false' testDebugUnitTest testStagingUnitTest lintStaging assembleStaging --no-daemon
```

The staging APK is `market.foodhome.app.staging`, displays Food&Home Staging, uses only `https://staging.foodhome.market`, disables native push and direct APK updates, and has its own exact App Link host. Production signing, update publication and server compatibility tuples are unchanged. Production0.2.16/build18 enables Sber web auth under the owner's separate production activation approval; debug retains its default-disabled flag. Preserve older published APKs unchanged.

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


## Production0.2.16 activation

Approved by the owner on2026-11-10: use the exact production origin `https://foodhome.market`, package `market.foodhome.app` and existing owner signing certificate SHA-256 `B3:E8:82:3F:71:AD:07:36:53:B0:31:4F:40:F1:3F:D9:02:F9:B5:85:04:58:67:4C:1B:7D:B5:9D:9F:5E:26:45`. The website must publish that exact App Link association. Bridge1.12.0, account storage, direct-update policy and the existing seller-order sound remain unchanged. Enable server notification compatibility only for the exact tuple `(1.12.0,0.2.16,18)`, preserving existing releases.

The owner additionally requires a working real Sber login before the full production rollout. Complete the bank confirmation and first-party return on staging; record actual bank/token and installed-device return results separately from synthetic tests. Do not publish or deploy on the strength of a token-endpoint transport probe alone. Production uses the explicitly approved `mtls_backchannel_production` configuration, with verified server HTTPS and client mTLS, fixed bank endpoints/issuer/production callback and all claim, nonce, UserInfo and browser-binding checks. Credentials stay exclusively on the backend.
