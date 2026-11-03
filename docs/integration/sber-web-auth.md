# Sber buyer login and isolated Android staging build

The website initiates a browser-bound Sber authorization attempt. Android advertises the additive `startSberAuth` method in bridge contract 1.12.0 only in the staging build. The method accepts an attempt UUID and a bounded HTTPS Sber authorize URL; the callback must match this build's trusted origin. It opens Custom Tabs, never bank authentication in the WebView.

The bank callback stays `/auth/sber/callback`. The server verifies the code/PKCE, signed ID token, nonce, subject and phone, then returns a separate first-party `/auth/sber/app-return` App Link with a short-lived ticket in its fragment. Android checks the saved attempt and exact origin, queues navigation until its original trusted WebView is ready, and the website exchanges the ticket using the original HttpOnly browser cookie. No provider token, site JWT or password crosses the bridge. The ticket alone cannot authenticate an external browser.

Only the attempt UUID and five-minute expiry are stored in private native preferences. UI checkout selection has a bounded web storage recovery hint for process restart. The server remains authoritative for ticket expiry, replay, browser ownership, role, consent and account matching. Cancel/failure does not create an order.

Build with:

```powershell
./android/gradlew.bat -p android '-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false' testDebugUnitTest testStagingUnitTest lintStaging assembleStaging --no-daemon
```

The staging APK is `market.foodhome.app.staging`, displays Food&Home Staging, uses only `https://staging.foodhome.market`, disables native push and direct APK updates, and has its own exact App Link host. Production signing, update publication and server compatibility tuples are unchanged. Production binaries keep Sber web auth disabled until a separately approved activation.

Validation for this candidate: 181 debug and 181 staging unit tests, staging lint and APK assembly passed; 33 canonical contract tests and secure-shell/phase 2-5 checks passed. These are local deterministic checks. Bank credentials are still awaited; real Sber authorization, installed-device App Link verification and physical Custom Tabs return remain acceptance checks after configuration. The website must expose `sber_available=false` while configuration is incomplete and retain email/Yandex alternatives.
