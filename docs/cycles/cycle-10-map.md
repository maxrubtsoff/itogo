# Карта кода — цикл 10

Карта от `explorer`. Используется всеми агентами (`architect`, `coder`, `tester`) — репозиторий заново не сканируется.

### B-60 — Скрытие денег в редактировании остатка счёта и вклада
- `ui/capital/CashSheet.kt:64` — `CashSheet`, ввод наличных через `BasicTextField` (`:67`), сумма выводится сырым текстом, `MoneyText` не используется
- `ui/capital/BalanceSheet.kt:52` — `BalanceSheet` (остаток счёта), суммы через `MoneyText` (`:90`)
- `ui/capital/CapitalScreen.kt:243` — вызов `BalanceSheet`; `:371` — вызов `CashSheet`
- `ui/deposits/DepositTopUps.kt:77` — `TopUpSheet`; вызов `ui/deposits/DepositDetailScreen.kt:144`
- `ui/common/HiddenAmounts.kt:117` — `MoneyText` (маскирует при `LocalAmountsHidden`), `:165` — чтение флага
- `data/settings/SettingsStore.kt:161-166`, ключ `KEY_HIDDEN` `:252` — флаг скрытия
- `ui/MainActivity.kt:464-466` — `hidden`/`toggleHidden`; `:638` — `CompositionLocalProvider(LocalAmountsHidden …)`
- Что менять: проверить, теряется ли `LocalAmountsHidden` в шторках, и почему `CashSheet` не маскирует сумму.

### F-36 — Обзор изменения капитала за период
- `domain/model/Capital.kt:18` — `CapitalSnapshot`; `:73` — `compute(...)`
- `domain/usecase/GetCapital.kt:16` — класс; `:25` — `invoke(): Flow<CapitalSnapshot>`; `:37` — `Inputs`
- `data/repository/LedgerRepositoryImpl.kt:16`; `data/repository/FxRepositoryImpl.kt:17` (fx), `:33` (cash)
- `data/local/LedgerDao.kt:10`, `LedgerEntities.kt:9` (`accounts`); `FxDao.kt:10`, `FxEntities.kt:8/20/28` (`fx_operations`, `fx_rates`, `cash`); `DepositDao.kt:10`; `DepositTopUpEntity.kt:13/22`
- `ui/capital/CapitalViewModel.kt:68` — `snapshot` (stateIn); `:71` — `operations`
- `ui/capital/CapitalScreen.kt:95` — `CapitalScreen`
- Что менять: истории капитала по датам нет, только текущий срез. Нужна новая таблица или журнал снимков; это решение дизайна.

### U-61 — Плавный переход с окна авторизации в приложение
- `ui/MainActivity.kt:274` — `locked`; `:315` — `needsUnlock`; `:321-330` — `isLocked`, `LockGate`; `:337` — `LockGate`; `:356` — `unlock()`
- `ui/lock/LockScreen.kt`, `ui/lock/Biometrics.kt`, `ui/lock/PhoneCheck.kt` — экран входа
- `ui/MainActivity.kt:665-677` — `enterTransition`/`exitTransition`; `:261` — `tabTween`
- `ui/common/Motion.kt:48/51/54` — `FAST`, `NORMAL`, `SLOW`
- Что менять: переход сейчас мгновенный (`LockGate` снимается без анимации, `:330`). Нужен `AnimatedVisibility`/`Crossfade` через `Motion`.

### B-61 — Тоглы «Новый месяц кэшбэка» и «Списание подписки» связаны
- `domain/model/Reminders.kt:9-16` — `ReminderSettings` (`newBank`, `depositEnding`, `cashbackMonth`, `any`); `:90` — `ReminderKind.SUBSCRIPTION`
- `ui/settings/NotificationsScreen.kt:82` — экран; `:85` — `settings`; `:100-105` — `change(next)`
- `data/notify/ReminderStore.kt:11` — класс; `:20` — `set`; `:38` — `read()`
- `domain/repository/ReminderRepository.kt:7-10` — интерфейс
- `data/notify/ReminderWorker.kt:43-54` — `checkReminders`, `Reminders.due(...)`
- `data/notify/AppNotifier.kt:47-49` — каналы `CASHBACK_MONTH`/`SUBSCRIPTION`
- `ui/settings/SettingsScreen.kt:101` — `ReminderKinds.subtitle(features)`
- Что менять: проверить, не пишутся ли оба флага вместе в `change`/`ReminderStore.set`.

### F-37 — Время последнего изменения капитала
- `domain/model/Capital.kt:18` — `CapitalSnapshot` (поля времени не проверены)
- `ui/capital/CapitalViewModel.kt:52` — `ledger.setBalance(..., System.currentTimeMillis())`; `:166` — `cash.set(CashBalance(..., currentTimeMillis()))`
- `ui/capital/CapitalScreen.kt:317-322` — «сверено N», порог 30 дней (уже есть логика времени)
- `domain/model/CapitalBar.kt`, `FxPortfolio` — время не найдено; `DepositMath` — уточнить поле
- `data/local/FxEntities.kt:28` — `cash` (время есть), `LedgerEntities.kt:9` — `accounts`
- Что менять: нужен ответ Максима по событиям вкладов. Источники времени в основном уже хранятся.

### U-62 — Резкое удаление при «Ноябрь уже близко»
- `ui/cashback/CashbackScreen.kt:51` — импорт `AnimatedVisibility`; `:374` — `AnimatedVisibility(expanded && unfolds)`
- `ui/cashback/CashbackScreen.kt:487` — блок «Скрины кэшбэка»
- `domain/model/Reminders.kt:110-111` — `CASHBACK_WINDOW_DAYS` (текст подсказки уточнить)
- Что менять: проверить `enter`/`exit` у `AnimatedVisibility` (нет или `expandVertically` без `fadeOut`), сверить с `Motion`.

### U-63 — Плашка «Не банки» — перенести вниз
- `ui/settings/SettingsBanksScreen.kt:105-106` — `if (collectsNotifications && notBanks.isNotEmpty()) NotBanksPanel(...)`
- `ui/settings/SettingsBanksScreen.kt:178-187` — `NotBanksPanel`, заголовок «Не банки · N»
- `ui/settings/BanksViewModel.kt:60` — `notBanks: StateFlow<Set<String>>`
- `domain/usecase/NewBankAnswers.kt:64,74` — ответы «Не банк»
- Что менять: панель уже в конце списка по коду; проверить порядок блоков в `Column`/`LazyColumn` на `SettingsBanksScreen.kt:105`.

### B-62 — Экран покупки валюты уезжает вверх при клавиатуре
- `ui/capital/FxOperationSheet.kt:137` — `SlitokSheet(onDone, imeField = existing == null)`; `:80` — `@OptIn`
- `ui/capital/CashSheet.kt:19` — `imePadding` (образец); `:72` — `SlitokSheet(onDismiss, imeField = true)`
- `ui/capital/CurrencyDetailScreen.kt:62` — экран валюты
- `ui/common/Sheets.kt` — `SlitokSheet` (`imeField`, отступы; `:208` — `pointerInput`)
- Что менять: сверить `imeField` в `SlitokSheet` и отступы `FxOperationSheet` с `CashSheet`.

### B-63 — Превью виджета кэшбэка пустое
- `app/src/main/AndroidManifest.xml:70-78` — `CashbackWidgetReceiver`, `@xml/cashback_widget_info`
- `app/src/main/res/xml/cashback_widget_info.xml` — `previewImage`/`previewLayout`, размер 3×2 (уточнить)
- `ui/widget/CashbackWidget.kt:66` — `class CashbackWidget : GlanceAppWidget()`; `:151-153` — `CashbackWidgetLayout.grid`
- `domain/model/CashbackWidgetLayout` — раскладка; `CashbackWidget.kt:251` — «+N»
- Что менять: preview-картинки/лэйаут не найдены; нужна отрисовка демо-данными.

### U-64 — Единый показ скринов «ждут проверки»
- `ui/deposits/DepositsScreen.kt:213-216` — «N скринов ждут проверки»
- `ui/cashback/CashbackScreen.kt:487` — блок «Скрины кэшбэка» (карусель внизу)
- `ui/common/ShotBatchUi.kt:116` — «Перенести в…» (общий компонент)
- Что менять: вынести плашку сверху в `ui/common`, по образцу вкладов.

### B-64 — После объединения скринов вклада остаётся один
- `domain/model/DepositBatch.kt:46` — `merge(items, chosen)`
- `ui/deposits/DepositBatchScreen.kt` — экран пакета (место вызова `merge` уточнить)
- `ui/deposits/DepositDetailScreen.kt:73` — экран; `:144` — `TopUpSheet`
- `data/repository/DepositShotRepositoryImpl.kt:14`; `data/local/DepositShotDao.kt:10`; `DepositShotEntity.kt:11` (`deposit_shots`)
- Что менять: проверить, переносятся ли скрины на `depositId` при merge (`DepositShotRepositoryImpl`, `DepositBatch.merge`).

### F-38 — «Скрины ждут проверки» как после сканирования
- `ui/deposits/DepositsScreen.kt:213-216` — счётчик
- `ui/deposits/DepositBatchScreen.kt` — экран пакета (образец «сразу после сканирования»)
- `ui/deposits/DepositShotPreviewScreen.kt` — экран одного скрина (не проверен)
- `domain/model/DepositBatch.kt:46` — группировка
- `ui/cashback/CashbackBatchScreen.kt`; `domain/model/CashbackBatch.kt:28` — аналог кэшбэка
- `ui/cashback/CashbackViewModel.kt:274` — `merged(lines)`
- Что менять: макет на этапе `designer`.

### U-65 — Объединение долгим нажатием и перетаскиванием
- `ui/common/ShotBatchUi.kt:116` — текущий «Перенести в…»
- `ui/deposits/DepositTopUps.kt:49` — `combinedClickable(onLongClick)` (образец)
- `ui/overview/OverviewScreen.kt:389`; `ui/capital/CapitalScreen.kt:254`; `ui/capital/CurrencySection.kt:220` — `combinedClickable`
- `ui/common/SectionPager.kt:39`; `ui/common/Sheets.kt:208` — `pointerInput`
- Что менять: нужен `detectDragGesturesAfterLongPress` в `ShotBatchUi`.

### B-65 — После обучения не открывается «Настройки» (P1)
- `ui/MainActivity.kt:228` — `Routes.LEARNING = "settings/learning"`
- `ui/MainActivity.kt:513-535` — `toursDone`, `pendingTour`, `startAfterSetup`
- `ui/MainActivity.kt:568` — `showBottomBar = … && tour?.view != TourView.SHOT`
- `ui/MainActivity.kt:605-627` — `LaunchedEffect(pendingTour, backStack)`, `TourSession(...)` `:627`
- `ui/MainActivity.kt:926-929` — `composable(Routes.LEARNING)`, `onOpen = { pendingTour = it }`
- `ui/onboarding/TourSession.kt:107` — `close()`
- Что менять: проверить, не остаётся ли `tour` не-null после закрытия и не перехватывает ли `TourCoach`/`TourSandboxLayer` касания.

### B-66 — Обучение подменяет данные; не возвращает в «Обучение»
- `ui/onboarding/TourSession.kt:52` — `TourSession`; `:99-107` — `next/again/close`; `:125` — `play()`
- `ui/onboarding/TourSession.kt:90`, `:134-136` — `hidden`
- `ui/MainActivity.kt:605-627` — подмена экрана (`tourScreenOf`, `openTab`, `nav.navigate`); `:616` — ветка GENERAL/`openTab`
- `data/repository/sample/SampleSettings.kt:17-60` — тестовые настройки (`hidden`)
- `ui/MainActivity.kt:627` — `container.sandbox(features)`
- Что менять: возврат в «Обучение» требует сохранить `Routes.LEARNING` в стеке, сейчас `navigate` его не сохраняет.

### B-67 — Обучение: оверлеи и рамки прилетают из-за экрана
- `ui/onboarding/Tour.kt:39` — `LocalTourOffer`; `:164` — провайдер
- `ui/onboarding/TourFrame.kt` — рамки (не проверен)
- `ui/onboarding/TourSession.kt:275` — `boundsOf(target)`; `:279` — `tap(target)`
- `ui/common/TourTargets` — `tourTarget` (образец: `ui/overview/OverviewScreen.kt:151`)
- `ui/onboarding/Learning.kt:63` — `LearningScreen`
- Что менять: `boundsOf` не учитывает безопасные зоны (`WindowInsets`); проверить `TourFrame`.

### B-68 — Картинка подписки и банка не сохраняется
- `data/icons/LogoMaker.kt:17` — класс; `:23` — `make(uri, bankKey)`; `:41` — новое имя файла
- `ui/subscriptions/SubscriptionsViewModel.kt:42` — `private val logos: LogoMaker? = null`; `:106` — `logos?.make(picked, s.id)`; `:109` — `repo.save(...)`
- `ui/settings/BanksViewModel.kt:41` — `logos: LogoMaker`; `:129` — `logos.make(picture, key)`
- `ui/common/ViewModelFactory.kt:38` — `container.logoMaker`; `:55` — `SubscriptionsViewModel(..., container.logoMaker)`
- `di/AppContainer.kt:80` — `logoMaker by lazy`
- `data/backup/LogoBackup.kt:11` — `object LogoBackup`; `data/backup/BackupManager.kt:279,393,493,510` — encode/restore
- Что менять: в `SubscriptionsViewModel` `logos` по умолчанию `null`; проверить передачу и сохранение `logoFile` в `repo.save`.
