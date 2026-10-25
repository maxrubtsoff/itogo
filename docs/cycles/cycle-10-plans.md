# Планы цикла 10

От `architect`. Опора — `docs/cycles/cycle-10-map.md` + точечные дочтения. F-36 и F-38 — у designer.

---

### B-60 — Скрытие денег в редактировании остатка счёта и вклада
- **Диагноз:** В `CashSheet` строки «сейчас X / станет X» (`ui/capital/CashSheet.kt:89-97`) рендерятся сырым `Text(Money.format())`, минуя `MoneyText`. В `BalanceSheet` (`ui/capital/BalanceSheet.kt:75-77`) значения — через `MoneyText`. `TopUpSheet` — ок. `AmountField` маскировать не нужно.
- **Правка:**
  - `ui/capital/CashSheet.kt:89-97` — `Text(...)` → `MoneyText(...)`.
  - `ui/common/HiddenAmounts.kt:117` — не трогать.
- **Файлы:** `ui/capital/CashSheet.kt`.
- **Приёмка:** при «скрыть деньги» в шторке «Наличные» строки «сейчас / станет / столько нет» показывают маску; поле ввода читаемо.
- **Тесты:** нет доменной логики.
- **Риск:** проверить, что `LocalAmountsHidden` проходит в `Dialog` `SlitokSheet` (`ui/common/Sheets.kt:188`).

### B-61 — Тоглы «Новый месяц кэшбэка» и «Списание подписки» связаны
- **Диагноз:** `ReminderSettings` (`domain/model/Reminders.kt:9-16`) — только `cashbackMonth`; отдельного `subscriptions` нет. Оба тогла в `NotificationsScreen` пишут одно поле.
- **Правка:**
  - `domain/model/Reminders.kt:9-16` — добавить `val subscriptions: Boolean = true`; `any` = `newBank || depositEnding || cashbackMonth || subscriptions`.
  - `:90-98` — обернуть подписки `if (settings.subscriptions && SUBSCRIPTION in kinds)`.
  - `data/notify/ReminderStore.kt:20-28` — писать новое поле (`KEY_SUB`); `:38-43` — читать с дефолтом `true`.
  - `ui/settings/NotificationsScreen.kt:124-180` — для `SUBSCRIPTION` читать/писать `s.subscriptions`, для `CASHBACK_MONTH` — `s.cashbackMonth`, каждый тогл правит только своё.
- **Файлы:** `domain/model/Reminders.kt`, `data/notify/ReminderStore.kt`, `ui/settings/NotificationsScreen.kt`.
- **Приёмка:** тоглы независимы; переживают перезапуск.
- **Тесты:** `ReminderSettingsTest` — `any = false` только при всех false; `subscriptions=false` подавляет подписки, оставляя кэшбэк.
- **Риск:** существующие prefs — поле `subscriptions` дефолтится `true` (не теряет для пользователя).

### B-62 — Экран покупки валюты уезжает за статус-бар при IME
- **Диагноз:** `SlitokSheet` + `imeField = existing == null` в `FxOperationSheet.kt:137` — для нового контент длинный, поднимается за верх. Нужен scroll внутри sheet или `heightIn(max)`.
- **Правка:**
  - `ui/common/Sheets.kt:188-240` — при `imeField = true` включать `scroll = true` и `imePadding`.
  - `ui/capital/FxOperationSheet.kt:137` — передать `scroll = true`, добавить `imePadding` в корневой `Column`.
- **Файлы:** `ui/capital/FxOperationSheet.kt`, `ui/common/Sheets.kt`.
- **Приёмка:** заголовок «Покупка валюты» не срезается статус-баром; поле «Сколько» видно; скролл работает.
- **Тесты:** нет доменной логики.
- **Риск:** разные клавиатуры, редактирование существующей (`imeField = false`).

### B-63 — Пустой preview виджета кэшбэка в пикере
- **Диагноз:** `res/xml/cashback_widget_info.xml` — только `initialLayout="@layout/glance_default_loading_layout"`, без `previewLayout`/`previewImage`.
- **Правка:**
  - Новый `app/src/main/res/layout/cashback_widget_preview.xml` — статичный превью 3×2: «Кэшбэк» + 4-6 строк «Категория · N% · Банк», цвета-токены (hex в xml-layout допустимы).
  - `res/xml/cashback_widget_info.xml` — `android:previewLayout="@layout/cashback_widget_preview"`.
- **Файлы:** `res/xml/cashback_widget_info.xml`, новый `res/layout/cashback_widget_preview.xml`.
- **Приёмка:** в системном пикере плитка «Кэшбэк» показывает демо-содержимое.
- **Тесты:** нет доменной логики.
- **Риск:** `previewLayout` — API 31+; на старых останется лоадер.

### B-64 — После merge скринов вклада остаётся один
- **Диагноз (дочитать):** `DepositBatch.merge` возвращает `ids` всех исходных, но при `saveGroups` в `DepositsViewModel` вероятно пишется ссылка только на первый id → `DepositDetailScreen` видит один скрин.
- **Дочитать:** `ui/deposits/DepositsViewModel.kt` (`saveGroups`, `collapseGroup`, `observeShotsOf`), `data/repository/DepositRepositoryImpl.kt`, `ui/deposits/DepositDetailScreen.kt:73-144`.
- **Правка:**
  - data: `dao.setDepositId(ids, depositId)` батчем для всех `g.ids`.
  - ui: `DepositsViewModel.saveGroups` — связать все `g.ids` с одним `depositId`; `DepositDetailScreen` — грузить скрины фильтром `depositId == this`.
- **Приёмка:** 2 скрина → merge → сохранить → в карточке «Дополнить» видно оба.
- **Тесты:** `DepositBatchTest` — две разные Item склеиваются и возвращают оба id.
- **Риск:** повторный merge, разъединение, удаление одного из слитых.

### B-65 — После обучения не открываются «Настройки» (P1)
- **Диагноз (дочитать):** После `tour.close()` `tour` в MainActivity (`:610`) остаётся не null; `TourSandboxLayer` поверх всего ловит касания.
- **Дочитать:** `ui/MainActivity.kt:560-640`, `ui/onboarding/TourCoach.kt`.
- **Правка:**
  - В `MainActivity` рядом с `TourSession` (`:627`) добавить/проверить `LaunchedEffect(tour) { if (tour?.closing == true) { tour.coachAlpha.animateTo(0f); tour.sandboxAlpha.animateTo(0f); tour = null } }`.
  - `TourSandboxLayer`/`TourCoach` не рисуется при `tour == null`.
- **Файлы:** `ui/MainActivity.kt`, возможно `ui/onboarding/TourCoach.kt`.
- **Приёмка:** закрытие тура любым путём → «Настройки» и все пункты реагируют без перезапуска.
- **Тесты:** нет.
- **Риск:** проверить device-check все секции, «Ещё раз» работает.

### B-66 — Обучение подменяет данные; не возвращает в «Обучение»
- **Диагноз:**
  1. Мигание реальных: `tour = TourSession(..., container.sandbox(features))` создаётся *после* `nav.navigate(screen.route)` → экран сначала с реальными данными, потом подмена.
  2. Возврат: `nav.navigate(screen.route)` без `popUpTo(Routes.LEARNING, saveState=true)` → close не возвращает.
- **Правка:**
  - `ui/MainActivity.kt:605-628` — `var tourFromLearning` → при входе проверить `entry.destination.route == Routes.LEARNING`; при close — `nav.navigate(Routes.LEARNING) { popUpTo(Routes.LEARNING) { inclusive = false }; launchSingleTop = true }`.
  - Поднимать `TourSandboxLayer` (`sandbox alpha=1` сразу) *до* `nav.navigate`; `TourSession` создавать с `playing=false, sandboxAlpha=1`; после `RESUMED` — `coachAlpha.animateTo(1)`.
- **Файлы:** `ui/MainActivity.kt`, `ui/onboarding/TourSession.kt` (возможно `init(sandboxImmediate: Boolean)`).
- **Приёмка:** при старте из «Обучение» экран сразу с тестовыми данными; после «Готово» возврат в «Обучение».
- **Тесты:** нет.
- **Риск:** несколько секций; старт не из Learning должен работать как раньше.

### B-67 — Оверлеи обучения налезают, рамки прилетают из-за экрана
- **Диагноз:** `boundsOf(target)` в `TourSession.kt:275-276` — глобальные координаты; `TourFrame`/`TourCoach` не учитывают `WindowInsets.safeDrawing` и/или анимируются от Rect(0,0,0,0).
- **Дочитать:** `ui/onboarding/TourFrame.kt`, `ui/onboarding/TourCoach.kt`.
- **Правка:**
  - `TourCoach.kt` — позиционирование карточки с учётом `WindowInsets.statusBars/navigationBars`, min 12.dp от целевой рамки; если не помещается — прижать к ближайшему краю safe area.
  - `TourFrame.kt` — fade-in in-place на итоговых bounds (анимировать alpha + сжатие 4-8 dp внутрь); не появляется до появления `boundsOf != null`.
  - Все анимации — через `Motion.enabled`.
- **Файлы:** `ui/onboarding/TourCoach.kt`, `ui/onboarding/TourFrame.kt`.
- **Приёмка:** на каждом шаге всех секций коуч не перекрывает цель и не срезается; рамка не влетает.
- **Тесты:** нет; device-check по всем секциям.
- **Риск:** разные размеры экрана.

### B-68 — Не сохраняется картинка подписки и банка
- **Диагноз (дочитать):** `LogoMaker.make` корректно; `SubscriptionsViewModel.saveWithPicture` вызывает `logos?.make(..)`. Если `logos == null` — вернёт null и затрёт `logoFile`.
- **Дочитать:** `ui/subscriptions/SubscriptionEditScreen.kt` (вызов `saveWithPicture`), `ui/settings/BanksViewModel.kt:120-140`, `data/repository/SubscriptionRepositoryImpl.kt`, `di/AppContainer.kt:80`.
- **Правка:**
  - data: `SubscriptionRepositoryImpl.save` сохраняет `look.logoFile` в entity (маппер).
  - ui:
    - `SubscriptionEditScreen` — при выборе картинки вызывать `saveWithPicture(..., picked, usePicture = true)`, не `save(...)`.
    - `BanksViewModel.kt:129` — результат `logos.make` пишется в `BankBrand.logoFile`/`BrandDecor`, репозиторий сохраняет.
    - Рендер читает `logoFile`, не статическую `CbrBanks` иконку.
- **Файлы:** `ui/subscriptions/SubscriptionEditScreen.kt`, `ui/subscriptions/SubscriptionsViewModel.kt`, `ui/settings/BanksViewModel.kt`, при необходимости `data/repository/SubscriptionRepositoryImpl.kt`.
- **Приёмка:** картинка подписки/банка показывается после сохранения; переживает перезапуск; «Без картинки» убирает.
- **Тесты:** один маппер «entity.logoFile сохраняется в toDomain/toEntity» если нет.
- **Риск:** `LogoBackup` на устройстве.

### U-61 — Плавный переход LockScreen → MainActivity
- **Диагноз:** `MainActivity:321-330` снимает `LockGate` мгновенно.
- **Правка:**
  - `ui/MainActivity.kt:321-331` — обернуть в `Crossfade(isLocked, Motion.tween(Motion.NORMAL))` или `AnimatedContent` между `AppRoot` и `LockGate`.
- **Файлы:** `ui/MainActivity.kt`.
- **Приёмка:** после пин/биометрии lock плавно растворяется. С «Убрать анимации» мгновенно.
- **Тесты:** нет.
- **Риск:** `openRoute` (пуш) не теряется; статус-бар appearances.

### U-62 — Плавный exit подсказки «Ноябрь уже близко» в кэшбэке
- **Диагноз:** подсказка снимается `if (…) { … }` без анимации (нужно найти блок рядом с `Reminders.CASHBACK_WINDOW_DAYS`).
- **Дочитать:** `ui/cashback/CashbackScreen.kt` блок подсказки «Новый месяц», ~`:200-350`.
- **Правка:**
  - Обернуть в `AnimatedVisibility(visible, enter = fadeIn + expandVertically, exit = fadeOut + shrinkVertically)` через `Motion.FAST/NORMAL`.
  - Если снимается по `onClick` — двухфазно: `shown=false` → exit → итог.
- **Файлы:** `ui/cashback/CashbackScreen.kt`.
- **Приёмка:** нажатие плавно сворачивает подсказку, без прыжка списка.
- **Тесты:** нет.
- **Риск:** взаимодействие с раскрытием категорий.

### U-63 — Плашку «Не банки» вниз на экране «Банки»
- **Диагноз:** `SettingsBanksScreen.kt:105-108` `NotBanksPanel` стоит *перед* списком `mine` (`:109-123`).
- **Правка:** перенести `NotBanksPanel` после панели `mine`, последним в `Column`.
- **Файлы:** `ui/settings/SettingsBanksScreen.kt`.
- **Приёмка:** порядок: `SectionActions` → баннеры → «Новые банки · N» → мои банки → «Не банки · N».
- **Тесты:** нет.
- **Риск:** при пустом `mine` — «Не банки» в конце.

### U-64 — Унификация «скрины ждут проверки»
*Решение в дизайн-варианте F-38 (designer). Инлайн-секция вместо плашки — реализуется coder'ом вместе с F-38.*

### U-65 — Объединение скринов долгим нажатием + перетаскиванием
*Реализуется coder'ом внутри нового `PendingShotsSection` (см. F-38 designer).*

### F-37 — Время последнего изменения капитала
- **Диагноз:** нет общего «lastChangedAt»; есть `account.updatedAt`, `cash.updatedAt`, `fxOperation.createdAt`, `deposit.topUp.createdAt`.
- **Два варианта событий вклада** (решить с Максимом):
  - **A (узкий):** только добавление / окончание / удаление / топ-ап; ежедневное начисление НЕ считать.
  - **B (широкий):** плюс ежедневное начисление: `lastChangedAt = max(…, today.atStartOfDay())`.
- **Рекомендация:** A — пользователь ждёт «событий», не тика процентов.
- **Правка:**
  - `domain/model/Capital.kt` — `CapitalSnapshot.lastChangedAt: Long?`.
  - `domain/model/CapitalCalculator.kt` — `lastChangeOf(accounts, cash, fxOps, deposits)` чистая функция, `max` из источников; для вклада (A) — `max(createdAt, endDate.atStartOfDay() если прошла, deletedAt, topUps.max.createdAt)`.
  - data: при необходимости добавить `createdAt`/`deletedAt` в `DepositEntity` (миграция, `version +1`).
  - ui: `CapitalViewModel` передаёт `lastChangedAt`; `CapitalScreen` под хедером — `RuDays.relative(...)` «обновлено X назад».
- **Файлы:** `domain/model/Capital.kt`, `domain/model/CapitalCalculator.kt`, `ui/capital/CapitalViewModel.kt`, `ui/capital/CapitalScreen.kt`; возможно `data/local/*Entity.kt`, `AppDatabase.kt` + Migration.
- **Приёмка:** в хедере «Капитал» строка «обновлено X назад»; обновляется при операции/правке; (A) не на каждом тике процентов.
- **Тесты:**
  - `lastChange_takesLatestOfAccountsCashFxDeposits`.
  - `lastChange_variantA_ignoresDailyAccrual`.
  - `lastChange_depositEnd_countsAsChange`.
- **Риск:** словесно пояснить, что строка про «изменения», а не про тик процентов.

---

## Общие замечания
- Миграций Room — только если F-37 расширит схему депозитов.
- U-64/U-65 — внутри F-38 designer.
- Анимации уважают `Motion.enabled` (U-61, U-62, B-67, B-66).
- Приоритет: **B-65 (P1)** → **B-66, B-67** (тур) → B-60..64, U-61..63, F-37.
