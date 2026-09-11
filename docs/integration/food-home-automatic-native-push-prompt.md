# Задача для food-home: уведомления Android без лишних кнопок и окон

Работай **в репозитории food-home, на текущей ветке**. Сначала прочитай его
AGENTS.md и обязательные skills, найди решения в Qdrant, проверь git status и
фактический код. Сохрани чужие изменения. Не переключай ветку и не переноси код
в sibling foodhome-app. Если обязательные правила репозитория конфликтуют с работой
на текущей ветке, укажи конфликт до изменений.

## Чего хочет владелец

В Android APK Food&Home не нужны отдельные рекламные карточки и кнопки
«Включить уведомления», «Проверить подключение», промежуточные окна согласия.
После подтверждённого входа покупателя или продавца служебные уведомления о чатах
и заказах должны подключаться автоматически, если настройки аккаунта и Android
это разрешают. Если разрешение Android ещё не запрашивалось — показать только
стандартное системное окно, один раз. Отказ/закрытие не должны вызывать запрос при
каждом входе. Это не подписка на маркетинг и не разрешение обходить прошлый opt-out.

Оставь неброское доступное управление уведомлениями в существующих настройках:
один понятный переключатель, фактический статус и помощь при системном запрете.
Убери дублирующую карточку с главной страницы профиля. Отключить и осознанно
повторно включить уведомления пользователь по-прежнему должен иметь возможность.
Это изменение native Android UX; обычный сайт/PWA Web Push и iOS не переделывай.

## Что подготовлено в foodhome-app

В локальной ветке `codex/android-notification-banners` подготовлен Android
**0.2.3 / versionCode 5**. Для чтения доступны:

- `C:/Users/Arsen/Desktop/foodhome-app/docs/integration/android-notification-permission-ux.md`
- `C:/Users/Arsen/Desktop/foodhome-app/docs/reports/2026-09-11-android-notification-permission-ux.md`
- `C:/Users/Arsen/Desktop/foodhome-app/android/app/src/main/java/market/foodhome/app/notifications/NotificationPermissionFlow.kt`

Bridge **1.5.0 / major 1 остаётся прежним**, schemas и опубликованный checksum
manifest не менялись. Не придумывай 1.6.0, новые методы, API или provenance.
На момент подготовки этого задания изменения APK локальные: commit/push/PR для
них не выполнены. Это честный локальный snapshot для разработки, не опубликованный
релиз. До production rollout нужно отдельно подтвердить публикацию native-кода и
точную согласованную сборку; не указывай базовый commit как commit новых изменений.

Поведение нового APK:

- `getNotificationStatus` ничего не показывает, только возвращает состояние.
- `requestNotificationPermission` сразу обращается к Android, без своего AlertDialog.
- Android 13+: запрос показывается при notDetermined и только в foreground.
- Android <=12: системного runtime-окна уведомлений нет, возвращается текущее разрешение.
- Уже разрешено — authorized без окна. Отказ/заблокированный канал/ранее закрытый
  или прерванный запрос — effective denied, без повторного автоматического окна.
- Denied не доказывает нажатие кнопки «Запретить»: закрытие окна тоже подавляет
  повторный запрос. При этом разрешение после ручного изменения настроек читается заново.
- Concurrent/background/stale requests безопасно отменяются. Clear/revoke отменяют
  ожидающий callback разрешения. Само разрешение не получает FCM token и не делает bind.
- `managePush` и generation-aware nonce bind/clear/revoke работают по существующему
  контракту. Только status=enabled подтверждает локальную привязку, не будущую доставку.
- APK не может автоматически включить плавающие уведомления Xiaomi или отменить
  настройки пользователя. В bridge нет метода открытия настроек Android — не выдумывай его.

## Сначала изучи существующий код

В проверенном локальном состоянии связаны:

- `frontend/src/lib/nativePush/NativePushBootstrap.tsx`
- `frontend/src/lib/nativePush/controller.ts`, `client.ts`, `useNativePush.ts`
- `frontend/src/components/settings/NativePushSettings.tsx`
- `frontend/src/components/settings/NotificationSettings.tsx`, `PushNotificationSettings.tsx`
- `frontend/src/app/(main)/profile/page.tsx` и фактические точки настроек продавца
- `frontend/src/lib/foodHomeBridge/adapter.ts`, native mode и auth lifecycle.

Bootstrap сейчас вызывает refresh, а refresh требует ранее запомненного owner.
Текущий enable() сначала вызывает permission, затем принудительно сохраняет
consent=true. **Не вызывай enable() автоматически без изменения этой логики**:
так можно отменить сохранённый отказ. Client уже читает `/api/auth/me/` и
`/api/notifications/settings/`, использует `push_enabled`, purpose-specific nonce
из `/api/v1/mobile/installations/binding-nonces/`. Проверь актуальность этих фактов.

## Требуемая реализация

1. Раздели автоматическую сверку состояния и явное действие пользователя.
   Автоматический путь никогда не PATCH-ит push_enabled=true. Сначала подтверди
   серверную сессию и настройки. Любое push_enabled=false сохрани как opt-out:
   без prompt, без bind, без сброса в true. Если происхождение false неизвестно,
   не делай массовую миграцию и не считай это разрешением на включение.

2. Новый автоматический путь разрешён только в проверенном Android native режиме,
   после валидированного bridge handshake и при эффективных capabilities
   `managePush`, `getNotificationStatus`, `requestNotificationPermission`.
   Дополнительно нужен APK >=0.2.3 и buildNumber >=5. Это UX compatibility gate,
   не security/auth gate. Не определяй по UA, query string или bridge версии 1.5.0.
   Поля appVersion/buildNumber уже есть в handshake и парсятся adapter, но в
   проверенном коде не включены в getSnapshot(): безопасно предоставь их через
   адаптер и тесты, без изменения wire schema. Сравнение версий числовое и строгое,
   неизвестные/некорректные версии fail closed. Для старых APK сохрани ручную
   возможность подключения в настройках, но не запускай их старое окно автоматически.

3. После успешного login/register, восстановления подтверждённой сессии и готовности
   bridge выполни единую идемпотентную сверку. Не запускай запрос на экране гостя,
   по одному лишь localStorage auth marker или до успешного получения профиля.
   Для server-enabled пользователя: query notification status; authorized — переход
   к привязке; notDetermined — один requestNotificationPermission; bind только после
   ответа authorized. Denied/unavailable/CANCELLED/TIMEOUT — не блокировать интерфейс,
   не открывать кастомный prompt, настройки ОС или бесконечный retry.

4. Защити от StrictMode, remount, focus/pageshow/online/visibility, нескольких вкладок
   WebView lifecycle и повторных auth events: single-flight, bounded backoff и
   отсутствие повторных prompt. Помни попытку автоматического запроса, включая
   timeout, до вызова; не считай её согласием. При недоступном хранилище не создавай
   цикл запросов. Точный native one-shot guard уже есть; веб-защита нужна дополнительно.

5. Владельца установки меняй только после подтверждения текущей серверной сессии
   и успешного завершения нужных шагов. Старый локальный binding clear-ится до нового.
   Проверяй revision/владельца после КАЖДОГО await; результат предыдущего login,
   permission, nonce, bind или preference PATCH не должен подключить другого
   пользователя. Owner marker — не consent и не authentication. Не передавай user ID,
   cookies, JWT, FCM token или installation ID в JS-native payload.

6. Используй действующий nonce/binding flow. Новый nonce на каждую попытку, никакого
   повторного использования отправленного nonce. Не делай лишний rebind при каждом
   focus, когда текущая согласованная привязка enabled. needsBinding и отсутствие
   привязки у подходящего пользователя восстанавливай с ограниченными попытками.
   При logout/switch/expiry/deletion/disable сохрани немедленный local clear и
   существующий серверный revoke/cleanup; offline и remote revoke failure отражай
   честно, не возвращай автоматически в enabled.

7. Удали рекламные native-карточки/дублирующие кнопки, в том числе из profile/page.tsx.
   В существующих настройках покупателя и продавца оставь один доступный control с
   truthful status. Явный opt-out должен быть устойчив к reload/login; ручное
   включение может менять серверную настройку, автоматическая сверка — нет.
   При OS-denied объясни путь «Настройки телефона → Приложения → Food&Home →
   Уведомления», без обещания открыть их через несуществующий bridge метод.
   Действия «проверить статус» и обычный вход в настройки не должны повторно спрашивать
   разрешение. Не показывай «подключено» только по push_enabled или OS authorized.

8. Не скрывай browser/PWA controls по общей проверке nativeMode, если от этого
   меняется неподдерживаемый iOS flow. Проверь capability/platform branches и обе роли.
   Не ломай существующие уведомления сайта и backend suppression/recipient проверки.

9. Не расширяй backend без реальной необходимости: текущий API настроек и nonce
   сначала используй как есть. Любой отсутствующий контракт или неоднозначное
   consent состояние зафиксируй отдельно, не подменяй фиктивными данными/ручками.

## Обязательные проверки

Добавь/обнови controller, bootstrap, adapter/client и UI tests для:

- guest и auth marker без подтверждённой сессии: 0 permission/bind;
- подходящий Android + authorized + server consent=true: bind без prompt;
- notDetermined: один OS bridge request, затем bind только при authorized;
- denial/dismissal/CANCELLED/TIMEOUT/unavailable: нет повтора при reload/focus/login;
- server consent=false и явный opt-out: 0 prompt/bind/автоматических PATCH true;
- ручное re-enable, возврат из настроек ОС, needsBinding, ограниченный retry;
- параллельные события, StrictMode, storage failure и повторная инициализация;
- A→B/logout/expiry во время session/permission/nonce/bind/consent: старый результат
  не подключает B, старые notifications/taps не обходят generation checks;
- offline logout/revoke failure и отсутствие ложного подтверждения отзыва;
- старый/неизвестный APK, некорректная версия/build, отсутствие capability,
  браузер/PWA/iOS — безопасный прежний/manual fallback, без нового auto-flow;
- обе роли, отсутствие промокарточки на профиле, доступный control в настройках;
- отсутствие provider tokens, credentials и персональных данных в логах/payload.

Запусти профильные тесты, typecheck, lint и проверки контракта по AGENTS.md.
Не отключай тесты/CI, не подгоняй успешные статусы. Device/FCM E2E не заявляй по
mock-тестам. Для реального Android 13+ OS dialog нужен отдельный подтверждённый
девайс/эмулятор; Xiaomi Android 10 этого не проверяет.

## Границы и сдача

Не меняй foodhome-app, архитектуру, Firebase/IAM/credentials, production Compose,
серверные push-флаги и тестовые таймеры. Не отправляй push и не включай доставку всем.
Этот промпт разрешает реализацию и проверки в food-home, но не deployment или
изменение production. Не делай commit/push/PR без отдельной команды владельца.

В конце дай отчёт по AGENTS.md: изменения и файлы, результаты реально выполненных
проверок, ограничения, текущая ветка/локальные изменения и порядок публикации двух
репозиториев. Сохрани полезное решение в Qdrant без секретов. Раздели «код готов»,
«native-код опубликован», «matching APK установлен», «food-home опубликован/задеплоен»
и «реальная доставка проверена» — не объявляй все этапы завершёнными автоматически.
