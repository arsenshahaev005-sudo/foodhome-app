# Seller-only new-order notification sound — food-home task

## Owner decision and native implementation

On 2026-09-14 the owner requested the attached `03_rising_excite.wav` only for
new orders received by sellers. Messages and ordinary order updates must keep
their existing sound. Android 0.2.5/build 7 implements a separate semantic channel
`foodhome_seller_new_orders`, selected ONLY for `seller.order.new`.

The unchanged bundled sound is `res/raw/seller_new_order.wav`: PCM 16-bit mono,
44,100 Hz, 4.01998 seconds, 354,606 bytes, SHA-256
`7fb0a3e173d9cb882b3a95aa55072f34b536bcb9842c3729c10c6a3829559710`.
Provenance: file supplied by product owner; no external licence has been independently
verified. Confirm distribution rights before public store release. No source audio
was uploaded to a third-party service or synthesized/edited.

Named resource URI and shrinker keep rule preserve the file across builds. There
is no web audio player, download, alarm usage, forced volume, loop or DND bypass.
The seller category is created lazily for the first eligible seller-new-order
presentation, not on buyer login or before the initial Android permission grant.
Existing channels are not changed. On first creation, the new category inherits
the old category's lower importance, silence, badge and vibration settings. Android
may further restrict notifications. Existing general permission/channel gate is
retained conservatively: disabling the old updates channel still blocks all native
push in this version. The new channel can additionally mute only new orders.
Do not claim independent category status in the current aggregate bridge result.
The web settings method retains its 1.6.0 behavior; a notification's own Settings
action targets that notification's category, using distinct PendingIntent identity.

## Contract 1.7.0, major 1

No new bridge method or binding field. Existing application payload version 2
admits an additive event type `seller.order.new` with the SAME six exact keys:

```json
{
  "version": "2",
  "eventType": "seller.order.new",
  "eventId": "<event UUID>",
  "bindingId": "<current binding generation, 64 lowercase hex>",
  "expiresAt": "<RFC3339, maximum 24 hours ahead>",
  "route": "<JSON-encoded existing order.detail logical route>"
}
```

See canonical schemas and `fixtures/valid/push-seller-new-order.json` (decoded
fixture form). FCM values remain strings. Never add a `notification` block, sound,
title, body, role flags, external URL or personal order details. Native supplies
generic private copy and audio, validates route/binding/expiry and uses existing
persistent dedupe. The event name is not proof of seller identity or order state:
server-side recipient authorization and order access remain authoritative.

Older APKs reject this event, so DO NOT broadcast it to all installations. Preserve
`order.updated`/`chat.message` for old clients. New APKs still accept both old types.
Historical 1.5.0/1.6.0 source checksum documents must remain unchanged. The initial
source commit and 1.7.0 checksums are in the
[source handoff](../releases/foodhome-bridge-contract-1.7.0.md). Production must pin
the actual accepted merge and verify its tree, not just an open PR head.

## Copyable food-home implementation task

Работай только в `C:\Users\Arsen\Desktop\food-home`, на текущей ветке, соблюдая
AGENTS.md и сохраняя чужие изменения. Сначала изучи фактические notification/outbox/
native delivery пути, контракт и текущие тесты. `foodhome-app` разрешён только для
чтения. Не создавай отдельный backend, frontend, БД или sender.

Задача: специальный звук только продавцу при новом заказе. Android реализует звук;
сервер должен корректно классифицировать событие и получателя.

1. Найди существующее авторитетное событие «новый заказ продавцу» и точку создания
   уведомления/outbox. Не делай вывод по тексту уведомления, отсутствию прочтения,
   первому увиденному orderId или любому изменению статуса. Не меняй бизнес-правила,
   момент оплаты/доступности заказа продавцу. Если событие неоднозначно, зафиксируй
   конкретное расхождение до изменения бизнес-семантики. Учти обычные и подарочные
   заказы в рамках уже существующих правил.
2. Сохрани серверный тип события в существующей цепочке доставки до worker.
   Только для фактического продавца этого заказа выбирай `seller.order.new`.
   Покупатели, сообщения, отмены, оплаты и остальные изменения статуса не должны
   получать этот eventType только потому, что связаны с заказом.
3. Отправляй новый тип исключительно совместимым Android installations: проверенный
   опубликованный контракт 1.7.0, согласованный APK 0.2.5/build 7 и последующие
   подтверждённо совместимые сборки. Используй уже сохраняемую native-метаинформацию
   установки, а не UA или роль из запроса браузера. Это только compatibility gate,
   не авторизация. Для старой/неизвестной метаинформации — прежний `order.updated`.
   После обновления APK может требоваться штатный rebind для обновления метаданных;
   не перезаписывай их вручную и не сбрасывай согласие пользователя.
4. На одну доставку выбирай один формат, не отправляй оба события. Сохрани стабильный
   eventId, dedupe, generation/expiry/revocation/recipient checks, bounded retries,
   transactional outbox и актуальную проверку перед отправкой. Нельзя повторно
   оповещать обо всех старых заказах после выката, обновления APK или rebind.
5. Не меняй browser/PWA/iOS payload и поведение, ключи Google, глобальные push-флаги,
   права IAM или production. Не передавай аудиофайл по сети: он уже внутри APK.
6. Сохрани существующий безопасный logical route и проверь, что переход открывает
   заказ продавца в его контексте с серверной проверкой доступа. Не добавляй новый
   маршрут или доверие клиентскому role-флагу без необходимости.
7. Добавь тесты: продавец vs покупатель, новый заказ vs обычный update/chat,
   совместимый vs старый/неизвестный APK, повтор worker/retry, revoke/account switch,
   просрочка, отсутствие лишних payload-полей и отсутствие двойного уведомления.
   Запусти профильные тесты, lint, typecheck и существующие contract gates.
8. Проверь опубликованный source commit и хеши 1.7.0. Если публикации пока нет,
   допускается честно обозначенный hashed local candidate для разработки, но
   релиз заблокирован до реального provenance. Не ослабляй CI и не выдумывай commit.
9. Дай отчёт с файлами, фактическими тестами, ограничениями и порядком публикации.
   Коммит/push/PR/deploy и реальные push без отдельной команды не выполняй.

## Rollout and acceptance

Publish reviewed native provenance, integrate server gate/fallback, install matching
APK, refresh installation metadata through ordinary authenticated flow, then test
one approved seller new order. Check unchanged message/update sounds, no special
buyer sound, foreground/background/process-dead delivery, duplicate suppression,
existing muted settings and Android 10/14. Real-device audio is not proven by unit
tests or a built APK. Roll back server event selection to the old event before
downgrading APKs; stale metadata must not cause old clients to receive a new enum.
