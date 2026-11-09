# Карта цикла 19 (F-58, F-59, B-128, U-77)

Сбор: explorer, 2026-11-09. Ветка cycle-19. Пути абсолютные относительно `C:\Projects\Itogo\app\src\main\java\ru\maxrubtsoff\bankagg\` (далее `BA/`), тесты — `app\src\test\java\ru\maxrubtsoff\bankagg\` (далее `TA/`).

## Главные наблюдения
1. **B-128 — причина в парсере, и по коду кейс не должен проходить.** В `BA/domain/parser/NotificationParser.kt` ни «купите», ни «вернём до» не входят в `PROMO_MARKS` (:162-166) и `PROMO_PATTERNS` (:168-172). В IN_MARKS (:141-146) нет «вернём». Текст из U-76/B-128 по коду даёт `null` (нет направления, :219). Значит, либо в реальном push есть другое слово (например, «пополнение» в ином месте), либо код менялся после сбора. **Проверить на реальном тексте** (скрин `docs/cycles/cycle-19-shots/b-128-promo.jpg`) до написания правки.
2. **RepairFromJournal уже умеет удалять ложные операции.** В `BA/domain/usecase/RepairFromJournal.kt:75-76` старая операция удаляется (`ledger.deleteTransaction(change.oldId)`), новая добавляется только если `op != null` (:79). Но запуск — только при росте `NotificationParser.VERSION` (`BA/di/AppContainer.kt:158-160`, версия = 7 в `NotificationParser.kt:55`). Без bump версии до 8 ложные операции не вычистятся.
3. **Перевод себе частично уже есть, но только в списке.** `BA/domain/model/CapitalEvents.kt:265-281` `ownTransferIds`: пары RUB-переводов с противоположным знаком, в один день, на равную сумму, между своими счетами (accountId из known accounts) — скрываются из списка (:184). Тип `HistoryKind.OWN_TRANSFER` (`BA/domain/model/CapitalEvents.kt:58-59`) объявлен, но в `build` не создаётся. Для трат F-58/F-59 готового подсчёта нет.
4. **График U-77 — масштаба и горизонтального скролла нет.** `BA/ui/common/CapitalChart.kt` только тап/скраб (`:136-144`, `detectTapGestures`); диапазоны — только `HistoryPeriod` (WEEK/MONTH/YEAR/ALL, `BA/domain/model/CapitalHistory.kt:14`). Прокрутка экрана — только вертикальная (`CapitalHistoryScreen.kt:228`) и свайп секций (`sectionSwipe`, :136). Точки прореживаются до `MAX_POINTS = 120` (`CapitalHistory.kt:37, 87`).
5. **Категорий трат и агрегатов по месяцу/банку для трат нет.** `category` в `TransactionEntity` есть (`BA/data/local/LedgerEntities.kt:36`), но при записи всегда `null` (`RepairFromJournal.kt:88`). Есть только `HistoryMonth.deltaMinor` / `CapitalEvents.byMonth` (`CapitalEvents.kt:459-462`) и `CapitalHistory.monthDelta` (`CapitalHistory.kt:129`) — дельта капитала, не траты.

## F-58 / U-77 — экран «История капитала»

| Путь:строка | Что там |
|---|---|
| `BA/ui/history/CapitalHistoryScreen.kt:116` | `CapitalHistoryScreen(vm, partArg, onBack, onOpenTarget, onOpenLog)` — экран, заголовок «История капитала» (:136) |
| `BA/ui/history/CapitalHistoryScreen.kt:136-166` | Scaffold + `sectionSwipe(pager)`, `PagerTabs(sections.map { it?.label ?: "Всё" })` — вкладки: Всё/Счета/Вклады/Валюта/Наличные (метки из `CapitalPart`) |
| `BA/ui/history/CapitalHistoryScreen.kt:153` | `HistorySections` (страницы pager) |
| `BA/ui/history/CapitalHistoryScreen.kt:172-177` | `HistoryContent` / `HistoryPage` — страница вкладки |
| `BA/ui/history/CapitalHistoryScreen.kt:241-308` | `Hero` — шапка с графиком: `CapitalChart(...)` (:300), `PeriodChips(compact=true)` (:308); схлопывание в полоску (U-67) |
| `BA/ui/history/CapitalHistoryScreen.kt:316` | `Events` — список событий |
| `BA/ui/history/CapitalHistoryScreen.kt:377-423` | `RowItem` / `EventRow` — строка списка, тап → `onTap` |
| `BA/ui/history/CapitalHistoryScreen.kt:433-446` | `axisOf`, `deltaText`, `signedPercent` |
| `BA/ui/history/CapitalHistoryViewModel.kt:50` | `data class HistoryScreenUi` |
| `BA/ui/history/CapitalHistoryViewModel.kt:65` | `data class HistoryUi` (series, period, events) |
| `BA/ui/history/CapitalHistoryViewModel.kt:85` | `class CapitalHistoryViewModel(...)` |
| `BA/ui/history/CapitalHistoryViewModel.kt:101-106` | `setPeriod`, `open(part)`, `setPart` |
| `BA/ui/history/CapitalHistoryViewModel.kt:137-186` | `detail`, `preview`, `check`, `delete`, `balanceSince`, `setBalance`, `manualFrom`, `manualDetail`, `removeManual`, `editManual`, `save` — делегаты в `HistoryOperations` |
| `BA/ui/history/OperationSheet.kt:67` | `OperationSheet(...)` — шторка операции; режимы `Mode { VIEW, EDIT, CONFIRM }` (:59); `ViewBody` :113, `EditBody` :166, `ConfirmBody` :232 (удаление) |
| `BA/ui/history/ManualEditFlow.kt:50` | `ManualEditFlow` — правка «ручного» остатка валюты |
| `BA/ui/history/BalanceFlow.kt:31` | `BalanceFlow` — ручная правка остатка счёта |
| `BA/ui/common/CapitalChart.kt:72` | `CapitalChart(points, axis, selected, marks, ...)` — график; тап/скраб `:136-144`, `ChartMarks.snap` (:108) |
| `BA/ui/common/PeriodChips.kt:30` | `PeriodChips(selected, onPick, compact)` — переключатель периода (Неделя/Месяц/Год/Всё) |
| `BA/ui/common/DeltaLine.kt` | линия дельты (в списке grep по истории) |
| `BA/ui/common/HistoryLine.kt:115` | `Modifier.accentBar(active)` — полоска строки |
| `BA/ui/MainActivity.kt` | маршрут истории капитала (grep по `CapitalHistoryScreen`) |
| `BA/ui/common/ViewModelFactory.kt` | ветка для `CapitalHistoryViewModel` (grep) |
| `BA/di/AppContainer.kt` | синглтоны history-use-cases (grep `HistoryOperations`) |

**Диаграмм pie/donut/bar в `ui/common` нет.** Есть: `ui/overview/OverviewScreen.kt:438` `CompositionBar` (private, полоса долей), `ui/common/BankSheet.kt:93` `CompositionBar` (private), `ui/deposits/DepositMonthsChart.kt:70` `MonthsChart` (столбцы, по вкладу). Переиспользуемых chart-компонентов для F-58 нет.
**Чипы/переключатели в `ui/common`:** `Controls.kt:48` `SlitokSwitch`, `:74` `SwitchRow`, `:103` `SlitokChip`, `:134` `ValueChip`; `Buttons.kt:167` `ChipButton`; `PeriodChips.kt:30`. Вне common: `ui/capital/CashSheet.kt:58` `ModeChip`, `ui/cashback/CashbackControls.kt:62` `ViewToggle` (переключатель вида), `:41` `MonthChip`.

### Данные истории: откуда
- Точки графика: `BA/domain/model/CapitalHistory.kt:34-150` (`series`, `thin`, `weekly`, `change`, `glance`, `monthDelta`); `CapitalPoint` :7; `HistoryPeriod` :13 (WEEK 7, MONTH 30, YEAR 365, ALL null).
- Список событий: `BA/domain/model/CapitalEvents.kt:166-259` `CapitalEvents.build(transactions, accounts, fxOps, deposits, cash, points, today, zone, rates, cashChanges)`. Строит события из операций (:184-211), сделок FX (:212), вкладов (:227), остатков на начало (:236), переоценки/процентов `moved` (:284), правок наличных (:255).
- Группировка: `CapitalEvents.within` :455, `byMonth` :459; модели строк `HistoryRow` `HistoryEvents.kt`-блок в `CapitalEvents.kt:71-99` (`Single`, `Group`), `HistoryDay` :102, `HistoryMonth` :105.
- Use-case: `BA/domain/usecase/HistoryOperations.kt:99` `class HistoryOperations(...)`; `detail` :115 (`OperationDetail` :46), `delete` :287, `restore` :320, `save` :341, `saveTx` :383, `preview` :219, `check` :255. Удаление операции — через `Removed` (:85) с `Item.Tx` (:87).
- Build-цепочка: `BuildCapitalHistory.kt`, `CapitalDaysScope.kt`, `RecordCapitalDay.kt`, `SyncCapitalPast.kt`, `BackfillCapitalParts.kt` (`BA/domain/usecase/`).
- Репозиторий истории: интерфейс `BA/domain/repository/CapitalHistoryRepository.kt`; реализация `BA/data/repository/CapitalHistoryRepositoryImpl.kt`; демо `BA/data/repository/sample/SampleCapitalHistory.kt`.
- Операции из Room: `TransactionEntity` `BA/data/local/LedgerEntities.kt:25-39`; `LedgerMappers.kt` (мапперы); DAO — `LedgerDao` (в `LedgerEntities.kt`/`AppDatabase`, grep `@Dao` в data/local).
- Доменная операция: `BA/domain/model/Account.kt:26-36` `Transaction(id, brand, accountId, amount: Money, currency, kind, category, counterparty, postedAt: Long)`; `TransactionKind` :4 `{ INCOME, EXPENSE, TRANSFER, UNKNOWN }`; `Account` :7-23 (`excluded`, `checkedAt`, `estimated`).
- Флаги: нет поля «перевод себе»/«по телефону»/«СБП»; направление берётся из `kind` и `amount` (знак). `counterparty` — строка. Сущность `HistoryEvent` `BA/domain/model/CapitalEvents.kt:9-29` (поля: date, part, title, subtitle, amountMinor, reason, kind, target, parts, bank, foreign, ref).
- `HistoryRef` :32-47: `Tx(id)`, `Cash(atMs)`, `TopUp`, `Fx`, `Balance`, `Manual`.

### Тесты F-58 / U-77 (TA/domain/)
`CapitalHistoryTest.kt`, `CapitalEventsTest.kt`, `HistoryOperationsTest.kt`, `HistorySpanTest.kt`, `ChartMarksTest.kt`, `ChartScaleTest.kt`, `CapitalPartsSwitchTest.kt`, `CapitalPastTest.kt`, `CapitalReconstructionTest.kt`, `CashHistoryTest.kt`, `HistoryEditsTest.kt`, `SyncCapitalPastTest.kt`, `CapitalByBankTest.kt`, `CapitalBarTest.kt`, `CapitalSummaryTest.kt`. Тест на масштаб/диапазон графика нет.

## F-59 — переводы

| Путь:строка | Что там |
|---|---|
| `BA/domain/parser/NotificationParser.kt:126` | `TRANSFER` — слова детекта вида: «перевод», «между счет», «с карты на карту», «себе», «пополнение с карты» |
| `BA/domain/parser/NotificationParser.kt:131-138` | `OUT_MARKS` — «вы перевели», «перевод на карту», «перевод со счет», «перевод в кубышк…» (B-125) — перевод как расход |
| `BA/domain/parser/NotificationParser.kt:141-146` | `IN_MARKS` — «перевод от», «вам перевели», «перевод получен» и др. |
| `BA/domain/parser/NotificationParser.kt:152` | `TRANSFER_IN` — «Перевод на <сумма>» = зачисление |
| `BA/domain/parser/NotificationParser.kt:303-323` | `detectDirection` — направление: слово (первое в тексте) → знак суммы → TRANSFER_IN → TRANSFER с контрагентом = OUT; иначе `null` |
| `BA/domain/parser/NotificationParser.kt:220-224` | `kind`: detectKind, либо EXPENSE/INCOME по направлению |
| `BA/domain/parser/NotificationParser.kt:346-355` | `counterparty` — ФИО/инициалы |
| `BA/domain/parser/NotificationParser.kt:17-35` | `ParsedOp(amount, currency, kind, direction, balance, counterparty, balanceCurrency, opTime)`; `OpDirection` :15 |
| Признака «свой счёт»/СБП/по номеру телефона в парсере нет (PHONE :119 — только маскировка номера). |
| `BA/domain/model/CapitalEvents.kt:265-281` | `ownTransferIds` — пары «между своими» (RUB, равная сумма, противоположные счета, тот же день) — скрываются из списка |
| `BA/domain/model/CapitalEvents.kt:184` | фильтр `transactions.filter { it.id !in own }` |
| `BA/data/local/LedgerEntities.kt:29-39` | `TransactionEntity` — `id, brandKey, accountId, amountMinor, currency, kind (String), category, counterparty, postedAt`. Флага перевода нет (вид — строкой в `kind`) |
| `BA/data/local/AppDatabase.kt:21` | **version = 20** |
| `BA/data/local/AppDatabase.kt:39-53` | `MIGRATION_19_20`, `MIGRATION_18_19` (последние две) |
| `BA/data/local/AppDatabase.kt:166` | цепочка `addMigrations(MIGRATION_9_10 … MIGRATION_19_20)` |

Итог F-59: признак перевода себе **не сохраняется**. Для тратового подсчёта нужен либо новый флаг/вид (`OWN_TRANSFER`) в `Transaction`/Entity (миграция 20→21), либо вычисление пар на лету (как `ownTransferIds`) — это решение архитектора.

## B-128 — реклама

| Путь:строка | Что там |
|---|---|
| `BA/domain/parser/NotificationParser.kt:162-166` | `PROMO_MARKS` — «розыгрыш», «выиграй», «получите до», «акция», «предложение», «скидка», «промокод», «оформите»… **нет «купите», «вернём до», «вернем до»** |
| `BA/domain/parser/NotificationParser.kt:168-172` | `PROMO_PATTERNS` — `до\d+%`, «кешб… в категори», `получите…\d` — **нет «вернём/вернем до <сумма>»** |
| `BA/domain/parser/NotificationParser.kt:178` | `BILL_MARKS` — «оплатите», «к оплате» |
| `BA/domain/parser/NotificationParser.kt:204-252` | `parse`: `isPromo || isBill` → null (:210); без направления → null (:219); сумма — первая с валютой до баланса (:230) |
| `BA/domain/parser/NotificationParser.kt:293-294` | `isPromo(low)` |
| `BA/domain/parser/NotificationParser.kt:55` | `VERSION = 7` (bump до 8 нужен, чтобы `RepairFromJournal` перечитал журнал) |
| `BA/domain/parser/NotificationParser.kt:48-54` | история версий (комментарий) |
| `BA/domain/usecase/RepairFromJournal.kt:25-133` | перепарсинг журнала: `parse` :38, пересчёт `content`/`timeOff` :39-44; **удаление старой операции** `deleteTransaction(change.oldId)` :76; добавление новой только при `op != null` :79-93; `events.updateParse(...)` :94 |
| `RepairFromJournal.kt:37` | `parseBare != null` → не трогаем (B-126) |
| `BA/di/AppContainer.kt:158-160` | запуск repair: `if (settings.repairedParserVersion() >= NotificationParser.VERSION) return`, затем `setRepairedParserVersion` |
| `BA/di/AppContainer.kt:175` | `repairFromJournal` (lazy) |
| `BA/domain/usecase/RecordNotification.kt` | `contentHash(...)`, `eventHash(...)` (grep) — дедуп |
| `BA/domain/parser/NotificationParser.kt:281-291` | `parseBare` — строгая форма «сумма — Баланс», тоже проверяет promo/bill |

**Таблица кейсов U-76:** `TA/domain/NotificationParserTableTest.kt` — `Case(...)` (:48…), словарь `"sber utilities promo" to (...)` (:59), `"alfa fuel"` (:62), `@Test fun table()` (:69), `@Test fun negatives()` (:86). Кейс «Купите любой ОПИФ…» пока **не добавлен** (B-128 просит добавить как негатив). Основной набор — `TA/domain/NotificationParserTest.kt` (121 упоминание парсинга).

Также: `TA/domain/NotificationParserTableTest.kt` — единственный файл, где встречаются слова «кейс/промо/акция»-подобные (`grep U-76|промо|...`).

Ошибка-подозрение: по коду текст B-128 даёт `null`. Ложная запись «Пополнение +1 500 ₽» значит, что реальный push содержит слово, которого нет в тексте из задачи (или версия парсера на устройстве старше). Проверить по журналу (`ui/log`) до правки.

## Тесты, которые касаются цикла
- Парсер: `TA/domain/NotificationParserTest.kt`, `TA/domain/NotificationParserTableTest.kt`, `TA/domain/OpTimeParserTest.kt`.
- Капитал/история: `TA/domain/CapitalEventsTest.kt`, `CapitalHistoryTest.kt`, `HistoryOperationsTest.kt`, `HistorySpanTest.kt`, `ChartMarksTest.kt`, `CapitalPartsSwitchTest.kt`, `RebuildCurrencyHistoryTest.kt`, `SyncCapitalPastTest.kt`.
- Дизайн-гард: `TA/ui/DesignSystemGuardTest.kt` (цвета/шрифты только токенами «Слитка»).
- Также `TA/ui/MotionGuardTest.kt`, `TA/ui/TabMotionTest.kt`.

## Слои, которых нет
- **Домен F-58:** нет агрегатов трат по месяцу/банку/категории и нет переключателя «с переводами/без»; нет модели «сводка трат».
- **Room F-59:** нет поля/вида «перевод себе» (`TransactionEntity` без флага); версия БД 20, миграции до 19→20 есть, новой нет.
- **UI F-58:** нет вкладки-аналитики «Счета» (вкладка есть, но только список), нет диаграмм трат, нет переключателя.
- **UI U-77:** нет масштаба/горизонтального скролла в `CapitalChart`.
- **Тесты:** нет теста на диапазон/масштаб графика; нет кейса B-128 в таблице; нет теста на перевод-себе в агрегатах трат.
