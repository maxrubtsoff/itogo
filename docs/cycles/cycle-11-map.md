# Карта кода — цикл 11

Составлена explorer по `docs/cycles/cycle-11.md`. Пути относительно `app/src/main/java/ru/maxrubtsoff/bankagg/` (`B/`) если не указано иное. Строки — на момент сборки карты.

## Общие компоненты (затрагивают несколько пунктов)
| Что | Где |
|---|---|
| Дельта «+сумма за месяц» (B-72) | `B/ui/common/DeltaLine.kt:36-71` — `MoneyText(... weight(1f, fill=false))` строка 58-64, период — строка 65 |
| Сумма с шириной/минСайз | `B/ui/common/AmountText.kt:33,53`; `MoneyText` — `B/ui/common/HiddenAmounts.kt:117` |
| График капитала | `B/ui/common/CapitalChart.kt:45` (`CapitalChart`), тап: `:67-68` (`detectTapGestures` + `tryAwaitRelease(); set(null)`), горизонт. драг: `:70-71`; `indexAt` `:58`; `onScrub` → `set` `:60` |
| Мини-спарклайн в дельте | `B/ui/common/Sparkline.kt:22` |
| Плашки периода | `B/ui/common/PeriodChips.kt` |
| Таб-ряды | `B/ui/common/PagerTabs.kt:42` (`PagerTabs`, pager), `:47` (`PagerTabsStatic`, без свайпа) |
| Свайп разделов | `B/ui/common/SectionPager.kt:31` (`SectionPager`, `pointerInput` `:39`, `HorizontalPager` `:63`) |
| Шторка «Убрать скрин» | `B/ui/common/ShotBatchUi.kt:176` (`MoveShotSheet`), опция `:197` «Убрать скрин», `SheetOption` `:203` |
| Пачка скринов: миниатюры | `B/ui/common/ShotBatchUi.kt:75` (`ShotThumbs`), `:109` (`AddShotTile`), `:135` (`AddShotSheet`) |
| Жесты перетаскивания скрина | `B/ui/common/ShotDrag.kt:140` (`pointerInput(shotId)`) |

## B-69. Картинка банка и подписки не сохраняется / не отображается (P1)
**Домен**
- `B/domain/model/BrandDecor.kt:5-13` — `BrandDecor(colorArgb, letters, textColorArgb, logoFile)`; `logoFile` — файл в хранилище приложения.
- `B/domain/model/BankBrand.kt:29` (поле `logoFile`), `:45` (`decor()`), `:49` (`withDecor`), `:58-59` (copy).

**Data**
- Банки (не Room): `B/data/settings/BankStore.kt:121` (запись `logo`), `:136` (чтение `logo`). Хранилище — `SettingsStore`/`BankStore` (JSON).
- Подписки (Room): `B/data/local/SubscriptionEntity.kt:37` (поле `logoFile`), `:70-72` (`toDomain`: decor собирается, если `logoFile != null`), `:80` (обратно). Миграция колонки: `B/data/local/AppDatabase.kt:73` (`ALTER TABLE subscriptions ADD COLUMN logoFile`).
- Логотип-файлы: `B/data/icons/LogoMaker.kt` (генерация из картинки; ссылок на `LogoMaker.` в коде не найдено — точка вызова не выяснена), `B/data/backup/LogoBackup.kt:11` (encode/decode PNG в бэкап), `B/data/backup/BackupManager.kt:281,406,477,509,526,528,556,663,666` (экспорт/импорт, `SafeFiles.inside` проверка пути).
- Нотификации: `B/data/notify/DecorBitmap.kt:27` — берёт `logoFile` через `BitmapFactory`.
- Виджет: `B/ui/widget/CashbackWidget.kt:123`.

**Сохранение (ViewModel)**
- Банк: `B/ui/settings/BanksViewModel.kt:130` (`else -> existing?.logoFile`), `:133` (`withDecor(... logoFile = logo)`), метод `saveOwn`.
- Подписка: `B/ui/subscriptions/SubscriptionsViewModel.kt:107` (`else -> look.logoFile`), `:109` (`repo.save(s.copy(decor = look.copy(logoFile = logo)))`).

**Форма ввода / UI**
- Редактор банка: `B/ui/settings/BankEditorScreen.kt:26-47` (`saveOwn(existing, look.title, look.decor(), look.picked, look.usePicture, onDone)`); `B/ui/settings/AddBankScreen.kt`; `B/ui/settings/SettingsBanksScreen.kt`; карточка `B/ui/settings/BankCardScreen.kt`.
- Подписка: `B/ui/subscriptions/SubscriptionScreens.kt:170` (`vm.saveWithPicture(...)`, `:175` передача `look.picked, look.usePicture`), иконка-редактирование `:33`.
- Общий редактор логотипа: `B/ui/common/BrandDecorEditor.kt:103` (`logoFile = existingLogoFile.takeIf { usePicture }`), `:141` (инициализация из `initialDecor.logoFile`), `:167` (вкладки «Буквы / Картинка»).
- Отрисовка: `B/ui/common/BankIcon.kt:30` (`rememberPicture(brand.logoFile, images)`), `:38-40` (fallback на `BrandAvatar`); `B/ui/common/BrandAvatar.kt:116` (`rememberPicture(decor.logoFile.takeIf { picked == null }, images)`), `:132`.
- Выбор банка: `B/ui/common/BankPickSheet.kt`, `B/ui/common/BankStack.kt`.

**Гипотеза по месту разрыва (только для архитектора, без решения):** два пути — Room (подписки, `SubscriptionEntity`) и JSON (банки, `BankStore`); сохранение `logoFile` и отрисовка `rememberPicture` идут через разные ветки (`picked` / `usePicture`). Проверить `BrandBrandDecor`-цепочку при `picked != null`.

**Соседи:** экраны Платежи (`ui/subscriptions/PaymentsScreen.kt`, `SubscriptionsSection.kt`), Настройки → Банки, виджет кэшбэка, уведомления (`DecorBitmap`), бэкап.

**Тесты:** искать в `app/src/test` по `BrandDecor`, `SubscriptionEntity`, `BankStore`, `BackupManager` (не проверялось).

## B-70. «Проверьте вклады»: редактирование карточки после переноса скрина; кэшбек открывает один скрин (P2)
**Экран пакетной проверки вкладов**
- `B/ui/deposits/DepositBatchScreen.kt:81` (`DepositBatchScreen`), заголовок `:130` «Проверьте вклады», `:85` `onOpenShot`, `:156-157` (`onOpenShot` / `vm.collapseGroup`), `:179-187` (`MoveShotSheet`, `onRemove` → `vm.deleteShot`), `:203` `GroupCard`, `:242` `ShotThumbs`, `:252` `Fact`.
- Пустых кнопок «Редактировать» в `DepositBatchScreen.kt` нет (grep по `Edit`/`Редакт` пуст) — карточка группы не имеет входа в форму вклада.
- VM: `B/ui/deposits/DepositsViewModel.kt` (`collapseGroup`, `deleteShot`, `moveShot`); логика групп — `B/domain/model/DepositBatch*` (файл не найден по имени, поиск `DepositBatch` — см. `B/domain/usecase/RecognizeDepositShot.kt`).

**Карточка вклада (редактирование)**
- `B/ui/deposits/DepositDetailScreen.kt:82` (`DepositDetailScreen`), `:86` `onEdit(depositId)`, `:126` `MoreAction("Изменить", Icons.Filled.Edit)`, `:184` `DepositShotsBlock` (U-66).
- Форма вклада: `B/ui/deposits/DepositAddScreen.kt`, `B/ui/deposits/DepositForm.kt`, `B/ui/deposits/DepositManualScreen.kt`. Маршрут — `ui/MainActivity.kt` (искать `DepositAdd`/`deposit_edit`, не проверено).

**Кэшбек**
- Экран пакетной проверки: `B/ui/cashback/CashbackBatchScreen.kt:76` (`CashbackBatchScreen`), `:170` `shots = g.ids.mapNotNull(byId::get)` (группа → список скринов), `:195` `MoveShotSheet`, `:213` `answer(SaveMode)`, `:242` `BankGroupCard`, `:292` `ShotThumbs`.
- Форма редактирования кэшбека: `B/ui/cashback/BankCashbackEditScreen.kt` (открывает форму с одним скрином — предполагаемый источник: передача `shotId` вместо списка; не проверено), `B/ui/cashback/CashbackForm.kt`, `B/ui/cashback/CashbackCard.kt`, `B/ui/cashback/CashbackViewModel.kt`, `B/ui/cashback/ShotPreviewScreen.kt`.
- Домен: `B/domain/model/CashbackShot.kt`, `B/domain/model/CashbackSummary.kt`; Room `B/data/local/CashbackShotEntity.kt`, `CashbackShotMappers.kt`; репозиторий `B/data/repository/CashbackShotRepositoryImpl.kt`, интерфейс `B/domain/repository/CashbackShotRepository.kt`.

**Соседи:** `ui/showcase/ShowcaseScreen.kt` (витрина), `ui/common/ShotDrag.kt` (перенос скрина между группами).

## B-71. Скрины на проверке нельзя удалить явно (P2)
- Шторка: `B/ui/common/ShotBatchUi.kt:176` `MoveShotSheet(... onRemove)`, пункт «Убрать скрин» `:197`, `SheetOption` `:203`. Открывается из обоих батч-экранов: `DepositBatchScreen.kt:179`, `CashbackBatchScreen.kt:195`.
- Миниатюры в группе (место для явного «удалить»): `ShotBatchUi.kt:75` `ShotThumbs`, `:157` `SheetThumb`; `ShotDrag.kt:140`.
- Удаление в VM: вклады — `DepositsViewModel` (`deleteShot`); кэшбек — `CashbackViewModel` (`deleteShot`/аналог, не проверено). Таблицы: `DepositShotEntity.kt`, `CashbackShotEntity.kt`; DAO — `B/data/local/*ShotDao` (не проверено).
- Очередь на проверке (плашка): `B/ui/common/PendingShotsPlate.kt`; `B/ui/common/ShotHeader.kt`; `B/ui/common/ShotBatchUi.kt:53` `BatchProgress`.
- Соседи: `ui/overview` (плашка «есть скрины»), `ui/capital/CapitalScreen.kt`, `ui/deposits/DepositShotPreviewScreen.kt`, `ui/cashback/ShotPreviewScreen.kt`.

## B-72. Сумма за месяц обрезана многоточием (P2)
- Строка дельты: `B/ui/common/DeltaLine.kt:36-71`; сумма `MoneyText(...)` `:58-64` с `modifier = weight(1f, fill = false)`, период `:65` (`maxLines = 1`), спарклайн `:68`, стрелка `:69`.
- Вызовы: `B/ui/overview/OverviewScreen.kt:345` (`DeltaLine(... "за месяц", it.spark, ... onClick = onOpenHistory)`), `B/ui/capital/CapitalUi.kt:136` (`DeltaLine(... "за месяц", glance.spark, color=part.color())`), `B/ui/showcase/ShowcaseScreen.kt:295-297` (витрина), `B/ui/common/DeltaLine.kt:55` (ветка «без изменений»).
- Формат суммы: `Money.ofMinor(...).format()` (`B/domain/model/Money.kt`); ближайший общий форматтер — `B/ui/common/AmountGrouping.kt`, `B/ui/common/RuText.kt`, `B/ui/common/AmountText.kt:33,53` (автосжатие `minSize`).
- Дельта в шапке истории: `B/ui/history/CapitalHistoryScreen.kt:124-133` (`deltaText(change)` `:239`, `MoneyText` `:129`, `maxLines=2` у «с dd.MM» `:133`), `signedSum` `:242`, `signedPercent` `:249`.
- Расчёт: `B/domain/model/CapitalHistory.kt:88-91` (`Glance`, `glance(...)`), `:71` `change(...)`, `:80` `monthDelta(...)`; `CapitalChange` `:25`.
- Соседи: `B/ui/capital/CapitalUi.kt:109,119` (ellipsis у заголовков).

## F-39. История капитала: группировка / скрыть переводы и списания (P2, дизайн)
- Экран: `B/ui/history/CapitalHistoryScreen.kt:72` (`CapitalHistoryScreen`), `:93` (`HistoryContent`), `:160` (`Events`), `:173-188` (сворачивание по месяцам `open` set, `:175`), `:197-199` (плоский список), `:208` (`EventRow`), `:227` `dayCaption`.
- VM: `B/ui/history/CapitalHistoryViewModel.kt:34` (`HistoryUi`), `:44` (`events`), `:51` (`class CapitalHistoryViewModel`), `:90-97` (сборка: `CapitalHistory.series`, `CapitalEvents.build`, `within`, `byMonth`), `setPart`/`setByParts`/`setPeriod` — в этом же файле.
- Модель событий: `B/domain/model/CapitalEvents.kt:9-16` (`HistoryEvent(date, part, title, subtitle, amountMinor)` — **нет типа события**, фильтровать можно по `title`: «Переход/списание» и т. п.), `:19` `MonthEvents`, `:27` `object CapitalEvents`, `:29-75` `build`, `:100` `within`, `:104` `byMonth`.
- Соседи: `ui/overview` (вход через `onOpenHistory`), `ui/capital/CapitalScreen.kt` (секции).
- Тест: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/CapitalHistoryTest.kt` (есть).

## F-40. «Было / стало / дельта»: длинные числа мелкие (P2, дизайн)
- Плитки: `B/ui/history/CapitalHistoryScreen.kt:142-146` (`StatTile` «Было», «Стало», «Дельта»), `:152-157` `Sum(minor, signed)` — `AmountText(... Amounts.Small, minSize = 9.sp)` — **здесь мелкий шрифт**.
- Hero-сумма: `:121` `AmountText(... Amounts.Hero, minSize = 20.sp)`.
- Шкала сумм: `Amounts` (ui/theme, `B/ui/theme/*`, не проверено точное имя файла).
- `StatTile` — определение не найдено отдельно (поиск `fun StatTile` не делался; вероятно `B/ui/common/*` или `B/ui/capital/CapitalUi.kt`).
- Соседи: `CapitalHistoryScreen.kt:208-218` (`EventRow` суммы `Amounts.Small`), `ui/deposits/DepositDetailScreen.kt:298` (`Fact`).

## F-41. График капитала: долгое нажатие к операциям дня (P2)
- Компонент: `B/ui/common/CapitalChart.kt:45` (`CapitalChart(points, axis, onScrub, color)`), тап-жест `:67-68` — **`tryAwaitRelease(); set(null)`**, т.е. при отпускании индекс сбрасывается (`onScrub(null)`) → линия и строка исчезают (это и описано в F-41). Горизонтальный драг `:70-71`.
- Использование: `B/ui/history/CapitalHistoryScreen.kt:137` (`CapitalChart(... onScrub = { scrub = it })`), состояние `scrub` `:94`, `:117` `at`, `:148` `Events(u, at?.date)`, `:161` фильтр событий по дню.
- Витрина: `B/ui/showcase/ShowcaseScreen.kt:300`.
- Точки: `B/domain/model/CapitalHistory.kt:7` (`CapitalPoint`), `:22` `DayValue`, `:46` `series`, `:64` `thin` (`MAX_POINTS`).
- Переход к операциям дня: сейчас только фильтр `Events` по `scrubbed` (`CapitalHistoryScreen.kt:161`) — **скролла к дате нет** (`verticalScroll` в `:78`, без `ScrollState.animateScrollTo`).
- Соседи: `ui/capital/CapitalUi.kt:135` (`glance`), `ui/overview/OverviewScreen.kt:145` (`CapitalHistory.glance`).

## F-42. Переоценка валюты: показать причину (P2)
- Формирование: `B/domain/model/CapitalEvents.kt:77` `moved(points, fxOps, deposits)`, `:93` `val revalued = cur.valueOf(CURRENCY) - prev.valueOf(CURRENCY) - dealt`, `:94` `HistoryEvent(cur.date, CURRENCY, "Переоценка валюты", null, revalued)`. **`subtitle = null`**, курс и пара валют не передаются.
- Курс для сравнения доступен в данных: `FxOperation` (`B/domain/model/Fx*.kt`), `RatesRepository` (`B/domain/repository/`), снимки `CapitalPoint` (`CapitalHistory.kt:7`).
- Отображение строки: `B/ui/history/CapitalHistoryScreen.kt:208-218` (`EventRow`: `title` + `subtitle` через `MoneyText`, сумма `:218`).
- Вторичные показы: `ui/capital/CurrencySection.kt` (`realizedMinor` `:181`), `ui/capital/CurrencyDetailScreen.kt`.
- Тест: `CapitalHistoryTest.kt`.

## F-43. График истории на всех вкладках Обзора (P2, дизайн)
- Вкладка Обзор: `B/ui/overview/OverviewScreen.kt:145` (`glance`), `:308` (`glance: CapitalHistory.Glance?`), `:345` (DeltaLine + спарклайн `it.spark`).
- Вкладки Капитал (Счета/Вклады/Валюта/Наличные): `B/ui/capital/CapitalScreen.kt:119` (`pager`), `:171` `PagerTabs`, `:180` `SectionPager`; `B/ui/capital/CapitalUi.kt:135-136` (`glance` и `DeltaLine` с `part.color()`), `:109,119` (заголовки).
- Полный график: только `B/ui/history/CapitalHistoryScreen.kt:137` (`CapitalChart`); на вкладках — только `Sparkline` (`B/ui/common/Sparkline.kt:22`, 72×24 dp по умолчанию) внутри `DeltaLine`.
- Вкладки разделов по данным: `CapitalHistory.series(... part)` (`CapitalHistory.kt:46`), `CapitalPart`.
- Соседи: `B/ui/capital/CurrencySection.kt`, `B/ui/deposits/DepositMonthsChart.kt` (отдельный график вклада), `B/ui/cashback/*`.

## F-44. История капитала: тап по операции ведёт к объекту (P2)
- Строка: `B/ui/history/CapitalHistoryScreen.kt:208-218` (`EventRow`) — **нет `clickable`**.
- Модель: `B/domain/model/CapitalEvents.kt:9-16` `HistoryEvent` — **нет идентификатора объекта** (есть только `title`/`subtitle`/`part`). Для связи нужны ссылки: `Transaction.accountId` (есть, `:50`), `Deposit` (`:65-67` по `name`), `FxOperation` (`:56`), cash (`:70`), подписка — в `build` не участвует.
- Маршруты: `ui/MainActivity.kt:804-805` (`CapitalHistoryScreen(historyVm, entry.arguments?.getString("part"), onBack)`) — навигации в объекты из истории нет; существующие маршруты для вкладов/валюты/счетов см. в `MainActivity.kt` (не выписаны).
- Карточки-цели: `ui/deposits/DepositDetailScreen.kt:82`, `ui/capital/CurrencyDetailScreen.kt`, `ui/capital/CapitalScreen.kt` (счета), `ui/subscriptions/SubscriptionScreens.kt`.
- Расчётная точка: `B/domain/model/CapitalEvents.kt:29` (`build` принимает `transactions, accounts, fxOps, deposits, cash, points, today`).

## B-73. Свайп по истории капитала не работает (P2)
- Экран `B/ui/history/CapitalHistoryScreen.kt`: разделы — `SlitokChip` в `Row` `:101-105` (не pager), режим — `PagerTabsStatic` `:99` (без свайпа, `PagerTabs.kt:47`), период — `PeriodChips` `:97`. Жестового перехода нет (`SectionPager` не используется, `pointerInput` в файле отсутствует).
- Эталон свайпа: `B/ui/common/SectionPager.kt:31-63` (используется в `ui/capital/CapitalScreen.kt:180`, `ui/subscriptions/PaymentsScreen.kt:57`, `ui/cashback/CashbackScreen.kt`).
- Внутри экрана — `CapitalChart` `:67-71` использует `detectHorizontalDragGestures` (конфликт жестов с возможным свайпом).
- Соседи: `CapitalScreen.kt:119-180` (эталон: `rememberPagerState` + `HorizontalPager`).

## F-45. История капитала: слишком много вкладок сверху (P2, дизайн)
- Три ряда управления в `B/ui/history/CapitalHistoryScreen.kt`:
  1. Период: `PeriodChips(u.period, onPick = ...)` `:97`, `B/ui/common/PeriodChips.kt`; `HistoryPeriod` (`B/domain/model/CapitalHistory.kt:13-17`, `fromKey`).
  2. Режим «Всего / По категориям»: `PagerTabsStatic` `:98-99` (только если `u.kept.size > 1`).
  3. Раздел (части): `SlitokChip` в `Row` `:100-106` — последний обрезан (`weight(1f)`).
- VM-методы: `CapitalHistoryViewModel.setPeriod`, `setByParts(...)`, `setPart` (`B/ui/history/CapitalHistoryViewModel.kt`, `kept` — список частей).
- Тест: `CapitalHistoryTest.kt`.
- Соседи: `B/ui/common/SlitokChip` (определение — `B/ui/common/Controls.kt`, не проверено), `B/ui/common/PagerTabs.kt`.

## U-66. «Скрины · 2» и «+» в карточке вклада (P3)
- Блок: `B/ui/deposits/DepositDetailScreen.kt:184-191` (`DepositShotsBlock(shots, onAdd)`), заголовок `:187` `"Скрины · ${shots.size}"` (или «Скрины», если пусто). Вызов блока внутри экрана — `DepositDetailScreen.kt` (поиск `DepositShotsBlock(` в файле, не выписан).
- Экран-параметры: `DepositDetailScreen.kt:82-86` (`onEdit`, `onAddShot`/`onAdd` — маршрут не проверен).
- Данные: `B/domain/model/DepositShot.kt`, `B/domain/repository/DepositShotRepository.kt`, `B/data/repository/DepositShotRepositoryImpl.kt`, Room `B/data/local/DepositShotEntity.kt`, `DepositShotMappers.kt`, `AppDatabase.kt` (таблица `deposit_shots`).
- Добавление скрина: `B/ui/deposits/DepositAddScreen.kt` (ветка «Со скринов»), `B/ui/deposits/DepositShotPreviewScreen.kt`, `B/domain/usecase/RecognizeDepositShot.kt`.
- Маршрут `DepositDetail`: `ui/MainActivity.kt` (не выписан).
- Тот же паттерн в кэшбеке: `B/ui/cashback/CashbackCard.kt`, `B/ui/cashback/CashbackScreen.kt` (проверить наличие «+» скринов), `B/ui/subscriptions/SubscriptionScreens.kt` (не проверено).
- Соседи: `B/ui/common/ShotBatchUi.kt:109` (`AddShotTile` — плитка «+» для скринов), `B/ui/common/ShotDrag.kt`, `B/ui/common/ShotImage.kt`.

## Пробелы (не проверено в этом проходе)
- Маршруты в `ui/MainActivity.kt` для вклада/кэшбэка/подписки (`Routes`, `composable`).
- Тесты, покрывающие `DepositBatch`, `CashbackBatch`, `CapitalEvents` (только `CapitalHistoryTest.kt` подтверждён).
- Определения `StatTile`, `SlitokChip`, `MoneyText` (тело) и `Amounts`.
- `ViewModelFactory` (`B/ui/common/ViewModelFactory.kt:36` — ветка `CapitalHistoryViewModel`; остальные ветки не проверялись).
