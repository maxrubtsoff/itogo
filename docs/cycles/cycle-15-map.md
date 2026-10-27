# Карта цикла 15 (разведка, только чтение)

Базовые точки общие для нескольких пунктов:
- Корень UI-хоста: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/MainActivity.kt`
- Навигация (Routes): `ui/MainActivity.kt:213-236` (CASHBACK_BASE, SUBSCRIPTION_EDIT, SUBSCRIPTION=`subscriptions/{id}`, BANK_CARD=`settings/bank-card/{key}`)
- Фичи/вкладки: `domain/model/Features.kt:4-80`
- Тестовый каталог домена: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/`

## U-72 — внутренние снекбары смахиваемыми

Что есть:
| Место | Что |
|---|---|
| `ui/MainActivity.kt:491-492` | `val snackbar = remember { SnackbarHostState() }`, `UndoSnackbarEffect(container.undo, snackbar)` — общий хост приложения |
| `ui/MainActivity.kt:528` | `snackbar.showSnackbar("Не получилось открыть скрины")` — ошибка, без кнопки |
| `ui/MainActivity.kt:688-693` | `snackbarHost = { SnackbarHost(snackbar, ...) { Snackbar(data, containerColor = Slitok.PanelTop, ...) } }` — визуал хоста, жеста смахивания нет (нет `SwipeToDismiss`/`dismissAction`) |
| `ui/common/Undo.kt:23-68` | `class UndoCenter`: `offer()` (удаление с «Вернуть», `undoable=true`, `SnackbarDuration.Long`), `say()` (уведомление, `undoable=false`, `Short`, опц. `actionLabel`) |
| `ui/common/Undo.kt:53-67` | `showIn(host)` — `collectLatest`, новый снекбар заменяет старый; `showSnackbar` вызывается без `dismissOnAction`/свайпа |
| `ui/common/Undo.kt:72-74` | `UndoSnackbarEffect` — подключение к хосту |
| `ui/capital/CapitalScreen.kt:143-144, 191` | отдельный локальный `SnackbarHostState` + `vm.messages` (сообщения «Нет связи с ЦБ…») |
| `ui/capital/CapitalViewModel.kt:92` | `messages` — one-off сообщения для снекбара |
| `ui/settings/SettingsBackupScreen.kt:92` | комментарий: снекбар с «Вернуть» (B-26), через `UndoCenter` |

Виды сообщений: удаление с «Вернуть» (`offer`); результат/ошибка без отмены (`say`); ошибка открытия скринов (`MainActivity:528`); курс ЦБ (`CapitalScreen`).

Жест смахивания: сейчас нет. Снекбары — только таймер + кнопка действия.

Тесты: UI-хост не покрыт JVM-тестами. Юнит-тестов на `UndoCenter` не найдено в этом обходе (не проверено полностью).

Файлы, которые будут трогаться: `ui/common/Undo.kt`, `ui/MainActivity.kt` (688-693), возможно `ui/capital/CapitalScreen.kt` (191), тема `ui/theme`.

## B-115 — из банка в подписку: иконка настроек не нажимается

Навигация (факты):
- Банк → подписка: `ui/MainActivity.kt:963` `onOpenSubscriptions = { nav.navigate(Routes.CASHBACK_BASE + "?section=1") }` — ведёт на вкладку «Платежи» (`cashback?section=1`), а не на карточку подписки. Экран `BankCardScreen` вызывается в `MainActivity.kt:953-965`.
- «Платежи» → подписка: `MainActivity.kt:777` `onOpen = { nav.navigate(Routes.subscription(it.id)) }` (из `SubscriptionsSection`, `ui/subscriptions/SubscriptionsSection.kt:133`).
- Карточка подписки: `MainActivity.kt:792-798` `composable(Routes.SUBSCRIPTION)` → `SubscriptionCardScreen(vm, id, onEdit, onBack)`.
- Иконка настроек в заголовке: `ui/subscriptions/SubscriptionScreens.kt:214-215` — `ScreenHeader(s.title, onBack) { IconButton(onClick = { vm.edit(s); onEdit() }) { Icon(Icons.Filled.Edit, "Изменить") } }`. Иконка — `Edit` (карандаш), не шестерёнка.
- `SubscriptionCardScreen:208` `val s = state.active.firstOrNull { it.id == id } ?: return` — если подписки нет в `state.active` (только активные), экран пустой; `state` берётся из `SubscriptionsViewModel` (`ui/subscriptions/SubscriptionsViewModel.kt:57-69`, `active` = `ACTIVE`).
- Переход edit: `MainActivity.kt:789-790` `SUBSCRIPTION_EDIT`.
- Общий путь: `subscriptionsVm` создаётся в `MainActivity.kt:488` (один на весь хост).

Различие навигации: с «Платежей» экран подписки открыт через `Routes.subscription(id)`; из банка до карточки подписки нет прямого маршрута (только переход на вкладку «Платежи»). Причина «не нажимается» по коду не установлена — требуется проверка на устройстве. Обработчик иконки в `SubscriptionScreens.kt:215` выглядит нормальным.

Тесты: `app/src/test/.../domain/` — логика `Subscriptions` (см. пункт F-53 ниже про `Subscriptions`); UI не покрыт.

Файлы: `ui/subscriptions/SubscriptionScreens.kt:206-216`, `ui/MainActivity.kt:792-798, 953-965`, `ui/bank/...` (BankCardScreen — не открыт в этом обходе).

## F-53 — разрез по банкам в обзоре

Экран обзора: `ui/overview/OverviewScreen.kt:126-200`. Показывает: шапку «Обзор» с глазом (`:153-163`), `NotificationAccessBanner` (`:170`), `NewBanksBanner` (`:171`), `EmptyCapital` или `CapitalHero` (`:173-196`), `CashbackCard` (`:198`). Нет группировки по банку.

Данные:
- `CapitalViewModel`: `vm.snapshot`, `vm.days` (`OverviewScreen.kt:143-145`), `vm.setPartIncluded` (`:193`). Файл VM: `ui/capital/CapitalViewModel.kt` (`messages` на `:92`).
- `OverviewScreen.kt:166-167`: `LocalMyBanks.current`, `collecting = myBanks.filter { b -> b.packages.any { it in selectedPackages } }` — единственная группировка банков на обзоре (только для пустого состояния).
- Домен: `domain/model/Capital.kt:68` (`computed so the overview can show them greyed out`), `CapitalCalculator` (`domain/model/CapitalCalculator.kt`, не открыт в этом обходе), `CapitalHistory.glance` (вызов `OverviewScreen.kt:145`).
- `BankBrand`/`CbrBanks`/`BankDirectory` в `domain/model` (не открыты в этом обходе).
- Группировка банков уже есть в `domain/usecase/MyBanksScope.kt:19` (скрытие операций по банку), и `domain/model/CashbackSummary.kt:29` (по банку, но для кэшбэка).
- Подписки по банку: `domain/model/Subscription` (поле `brand`, `payee`; `SubscriptionScreens.kt:226` `BrandAvatar(... overlay = s.brand)`).
- Вклады по банку: `domain/model/Deposit*` (не открыты).

Тесты: `app/src/test/.../domain/` (CapitalCalculator, CashbackSpotlight — по CLAUDE.md домена; не перечислены).

Файлы для F-53: `ui/overview/OverviewScreen.kt`, `ui/capital/CapitalViewModel.kt`, `domain/model/Capital.kt`, `domain/model/CapitalCalculator.kt`.

## U-73 — ревизия шрифтов цифр

Токены:
- `ui/theme/Type.kt:15-…` `AppTypography` (Unbounded для заголовков: `:17,21,25`, GolosText для текста `:29-64`).
- `ui/theme/Type.kt:90-115` `object Amounts`: `Hero` (34sp, Unbounded Bold, tnum), `Large` (22sp), `Row` (16sp), `Small` (13sp) — все с `fontFeatureSettings = "tnum"` (`:91`).
- `ui/theme/Fonts.kt:15-25`: `unbounded_wght` (Medium/SemiBold/Bold) и `golos_text_wght` (Normal/Medium/SemiBold/Bold). Файлы шрифтов — `res/font` (вариативные, `R.font.unbounded_wght`, `R.font.golos_text_wght`).

Места, где цифры: `ui/common/AmountText.kt:50` и `MoneyText` (сумма); `ui/capital/CapitalScreen.kt:286,354`; `ui/capital/BalanceSheet.kt:89-90`; `ui/capital/CurrencySection.kt:174,238,253,274`; `ui/deposits/DepositDetailScreen.kt:161`; `ui/deposits/DepositBatchScreen.kt:267`; `ui/deposits/DepositAddScreen.kt:194`. Полный список `fontWeight`/`fontFamily`-вхождений > 80 (вывод обрезан), полная ревизия не завершена.

Хардкод: `ui/CLAUDE.md:27` запрещает ручной `fontFamily = Unbounded`. В `ui/**` ручных `fontFamily` в обходе не найдено; `FontWeight.*` встречаются часто (ручные веса).

Виджет Glance: `ui/widget/CashbackWidget.kt`. Свой шрифт не задан: `TextStyle(...)` без `fontFamily` (`:181, 186, 194, 198, 214, 260, 278, 285, 298, 316, 326, 352-354, 371, 384-386`) — системный шрифт Glance, не `res/font`. Размеры: 11–14sp, `FontWeight.Medium/Bold`. `:354` `fontSize = (sizeDp / 2).sp` — динамический размер (цифры?).

Тесты: `DesignSystemGuardTest` (упомянут в `ui/CLAUDE.md`) — запрет цвета/шрифта на экранах; файл в `app/src/test` не открыт.

## B-116 — иконка глаза пропадает в «только кэшбэк и подписки»

- Иконка: `ui/overview/OverviewScreen.kt:154-162`. Видимость: `HideToggle.visible(hasAmounts, hidden)` (`:156`), где `hasAmounts = snapshot?.parts?.any { it.hasData }`.
- Логика видимости: `domain/model/HiddenAmounts.kt:86-90` — `fun visible(hasAmounts, hidden) = hasAmounts || hidden`.
- В режиме «только кэшбэк и подписки» капитал не показывается: `Features.showCapital` = `capitalParts.isNotEmpty()` (`domain/model/Features.kt:32`). Поэтому `snapshot.parts` без данных → `hasAmounts=false` → иконка скрыта (`:156`). Также `OverviewScreen.kt:174` `if (features.showCapital)` — капитал не рисуется.
- Обработчик: `onToggleHidden` (`OverviewScreen.kt:141, 156`) → `MainActivity.kt:497` `toggleHidden = { scope.launch { container.settings.setAmountsHidden(!hidden) } }` → `SettingsStore.kt:181-183` `setAmountsHidden` (ключ `KEY_HIDDEN`, `:270`).
- Поворот: `MainActivity.kt:683` `Modifier.twoFingerSwipeDown(swipeOn, swipeDistance, toggleHidden)` — жест. Пересоздание состояния при повороте: `hidden` из `SettingsStore` (StateFlow `_hidden`, `SettingsStore.kt:178`) переживает поворот; `snapshot` в `CapitalViewModel` — тоже. `OverviewScreen` `rememberSaveable` только для `tuning` (`:147`). Причина исчезновения при повороте по коду не подтверждена — проверить на устройстве.
- Тесты: `HiddenAmounts` — `app/src/test/...` (не найден в обходе; искать `HideToggle`/`HiddenAmountsTest`).

Файлы: `ui/overview/OverviewScreen.kt:153-163`, `domain/model/HiddenAmounts.kt:86-90`, `ui/MainActivity.kt:495-498, 675-683`, `data/settings/SettingsStore.kt:176-183`.

## F-54 — выкладка в RuStore

Сборка: `app/build.gradle.kts`:
| Строка | Что |
|---|---|
| `:13` | `applicationId = "ru.maxrubtsoff.bankagg"` |
| `:18-19` | `versionCode = run ?: 1`, `versionName = "0.6.${run ?: 0}"` (номер сборки CI) |
| `:23` | `ndk { abiFilters arm64-v8a, armeabi-v7a, x86_64 }` |
| `:28` | `publishKeystore` из `SIGNING_KEYSTORE_PATH` |
| `:30-45` | `signingConfigs`: `debug` (`debug.keystore`, `:35`), `publish` (`:41-44`, пароли из env) |
| `:50-53` | `buildTypes.debug`: `applicationIdSuffix = ".debug"` |
| `:60-61` | release: `signingConfig = publish ?: debug`, `isMinifyEnabled = true` |
| `:63-66` | `proguardFiles(proguard-android-optimize.txt, proguard-rules.pro)` |
| `:100` | `stabilityConfigurationFile` |

AAB: в `app/build.gradle.kts` и `build.yml` сборки AAB не найдено (`bundle`/`aab` — 0 совпадений). Сборка только APK (`./gradlew assembleRelease`, `build.yml:133`).

CI: `.github/workflows/build.yml:115-133` — секреты `SIGNING_KEYSTORE_BASE64` (`:119`), `SIGNING_STORE_PASSWORD` (`:128`), `SIGNING_KEY_ALIAS` (`:129`), `SIGNING_KEY_PASSWORD` (`:130`); `:122-123` декодирование в `$RUNNER_TEMP/publish.keystore`; `:133` `assembleRelease`.

Манифест: `app/src/main/AndroidManifest.xml`:
- Permissions: `:6` INTERNET, `:7` ACCESS_NETWORK_STATE, `:9` POST_NOTIFICATIONS. Нет SMS, нет `QUERY_ALL_PACKAGES`.
- `:16-31` `<queries>` — 15 пакетов банков.
- `:34-41` `<application>`, `android:icon="@mipmap/ic_launcher"` (`:40`), `android:label="@string/app_name"` (`:41`).
- `:72` widget (`cashback_widget_label`), `:89` FileProvider (`${applicationId}.share`).
- `:101-104` NotificationListenerService с `BIND_NOTIFICATION_LISTENER_SERVICE` (`:102`).

Публикация: `docs/publishing.md` — подпись из секретов (`:7`), версия (`:9`), генерация ключа (`:20`), таблица секретов (`:35-38`), переход (`:53`).

ProGuard: `app/proguard-rules.pro` (файл есть; содержимое не открыто в этом обходе).

Иконка/имя: `@mipmap/ic_launcher`, `@string/app_name` (ресурсы `res/`, не открыто).

Политика конфиденциальности: в репозитории в этом обходе не найдена (поиск `privacy|конфиденц|политик` даёт только код `PrivacyTest`, `SettingsPrivacyScreen`, и `docs/publishing.md`, `docs/analysis/distribution-and-banks.md`, `docs/cycles/cycle-15.md`, `docs/ROADMAP.md`). Внешний текст/URL в `app/src/main/res` не проверен.

Тесты: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/PrivacyTest.kt` (есть).

## Не проверено
- Полный список цифр (U-73) — вывод обрезан.
- `ScreenHeader` (`ui/common`), `BankCardScreen`, `CapitalViewModel.snapshot`, `HiddenAmounts.kt` UI-часть, `CashbackWidget` (`:352-354`) — не открыты.
- Поведение при повороте (B-116) и нажатие иконки (B-115) — только по коду, требуется `device-check`.
