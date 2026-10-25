import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

const root = resolve(import.meta.dirname, "..");
const read = (file) => readFileSync(resolve(root, file), "utf8");
const notifications = "android/app/src/main/java/market/foodhome/app/notifications/";

test("data-only service rejects notification envelopes and delegates deleted messages without alerts", () => {
  const service = read(`${notifications}FoodHomeMessagingService.kt`);
  assert.ok(service.indexOf("message.notification != null") < service.indexOf(".receive(message.data)"));
  assert.ok(service.indexOf("PushEnvelopePolicy.acceptsKeys") < service.indexOf("super.handleIntent(intent)"));
  assert.match(service, /override fun onDeletedMessages\(\)/);
  assert.match(service, /\.messagesDeleted\(\)/);
  assert.doesNotMatch(service, /collapseKey|notification\.title|notification\.body/);
});

test("one notification per event and binding-specific immutable pending intents retain privacy", () => {
  const presenter = read(`${notifications}PushNotificationPresenter.kt`);
  assert.ok(presenter.includes('"market.foodhome.app.PUSH.${push.bindingId}.${push.eventId}"'));
  assert.ok(presenter.includes("manager.notify(push.eventId, 0, notification)"));
  for (const marker of ["FLAG_IMMUTABLE", "VISIBILITY_PRIVATE", ".setPublicVersion(publicVersion)", ".setOnlyAlertOnce(true)"]) {
    assert.ok(presenter.includes(marker), marker);
  }
  assert.doesNotMatch(presenter, /setSound|data\["(?:title|body|message|sound)"\]/);
  const strings = read("android/app/src/main/res/values/strings.xml");
  assert.ok(strings.includes("Новое уведомление от Food&amp;Home"));
  assert.ok(strings.includes("Откройте приложение, чтобы посмотреть подробности"));
});

test("persisted receipt precedes display and first tap is persisted before routing", () => {
  const runtime = read(`${notifications}AndroidPushRuntime.kt`);
  assert.ok(runtime.includes("if (store.set(recorded)) presenter.show(push, data)"));
  assert.ok(runtime.includes("store.get().opened(push, now) ?: return null"));
  assert.ok(runtime.includes("push.destination.takeIf { store.set(opened) }"));
  assert.ok(runtime.includes("notifications.authorizationStatus() != NotificationAuthorizationStatus.Authorized"));
  assert.ok(runtime.includes("presenter.cancelAll()"));
});

test("deleted-message recovery uses only the existing visible trusted lifecycle transport", () => {
  const runtime = read(`${notifications}AndroidPushRuntime.kt`);
  assert.ok(runtime.includes("copy(syncRequired = true)"));
  assert.ok(runtime.includes("syncRevision.value == revision"));
  const shell = read("android/app/src/main/java/market/foodhome/app/ui/FoodHomeAppShell.kt");
  assert.match(shell, /LaunchedEffect\(pushSyncRevision\)/);
  assert.ok(shell.includes("notificationSignals.record(NotificationLifecycleSignals.Kind.Resumed)"));
  assert.ok(shell.includes("pushRuntime.acknowledgeSync(pushSyncRevision)"));
  const web = read("android/app/src/main/java/market/foodhome/app/web/FoodHomeWebView.kt");
  assert.match(web, /if \(dispatched\) \{\s+onNotificationSignalsDispatched\(\)/);
  const signals = read("android/app/src/main/java/market/foodhome/app/bridge/NotificationLifecycleSignals.kt");
  assert.ok(signals.includes("window !== window.top || location.origin !=="));
  assert.ok(signals.includes("document.visibilityState !== 'visible'"));
  assert.doesNotMatch(runtime, /\/api\/notifications|push_enabled|push_promotions|marketing_consent/);
});
