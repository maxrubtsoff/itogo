# Карта цикла 12 (explorer)

Основные файлы истории капитала: `ui/history/*` (не `ui/overview`), `domain/model/CapitalEvents.kt`, `domain/model/CapitalHistory.kt`.

## B-74 Проценты кэшбэка уезжают за край
| Место | Что |
|---|---|
| `ui/cashback/CashbackLinesEditor.kt:54` | `CashbackLinesEditor`, строки категорий, `LazyColumn` :82 |
| `ui/cashback/CashbackForm.kt:80` | одна строка кэшбэка (U-39), поле процента (компактное) |
| `ui/cashback/CashbackBatchScreen.kt:285` | `l.percent?.let(::formatPercent) ?: "?"` — подпись процента в карточке банка после распознавания (`BankGroupCard` :244) |
Вероятная причина: узкий `weight(1f)`/`maxLines=1` без ограничения ширины для процента (`CashbackBatchScreen.kt:275-282`).
Тесты: нет UI-тестов; `DesignSystemGuardTest` не затронут.

## F-46 Клик по графику → подсветка операций в списке
| Место | Что |
|---|---|
| `ui/common/CapitalChart.kt:49` | `CapitalChart(points, axis, selected, ...)`, тап `:77`, drag `:85`, `indexAt` :67, `scrubbed` callback |
| `ui/history/CapitalHistoryScreen.kt:211` | вызов `CapitalChart` на вкладке истории |
| `ui/history/CapitalHistoryScreen.kt:164,183` | `picked`, `CapitalEvents.nearestDay(u.events, it)` :183 → `requesters` (BringIntoView) :167,242 |
| `domain/model/CapitalEvents.kt:260` | `nearestDay` |
Нет: ничего не подсвечивает группу/операцию; подсветки нет (только скролл к дню).
Тесты: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/CapitalEventsTest.kt`.

## B-75 Свайп графика не работает на пустой части
| Место | Что |
|---|---|
| `ui/common/SectionPager.kt:39` | `swipe = Modifier.pointerInput(state){ detectHorizontalDragGestures }`, :41 |
| `ui/common/SectionPager.kt:65` | `modifier.bleed(gutter).then(swipe)` — жест на корне пейджера |
| `ui/common/CapitalChart.kt:84-85` | собственный `detectHorizontalDragGestures` графика (конфликт/перехват жеста) |
| `ui/history/CapitalHistoryScreen.kt:153` | `SectionPager(pager)` для вкладок истории |
| `ui/common/Motion.kt:87` | `pointerInput(Unit)` (проверить, не на фоне) |
Вероятная причина: `SectionPager` вешает drag на контейнер, а пустые области вне плашки/графика не получают жест (или их перехватывает `verticalScroll` на `CapitalHistoryScreen.kt:121`). Перепроверить на устройстве (`device-check`).
Тесты: нет.

## F-47 Пояснение к операциям изменения капитала
| Место | Что |
|---|---|
| `domain/model/CapitalEvents.kt:9-21` | `HistoryEvent(date, part, title, subtitle, amountMinor, reason, kind, target)` |
| `domain/model/CapitalEvents.kt:63-95` | `RevaluationLeg`, `RevaluationReason(legs, manualMinor)`, `RevaluationText.subtitle` :81 — пояснение пересчёта уже есть |
| `domain/model/CapitalEvents.kt:157` | «Новый вклад» — `target = HistoryTarget.Deposit(d.id)` (id есть) |
| `domain/model/CapitalEvents.kt:159` | «Пополнение вклада» — `target = Deposit(id)` |
| `domain/model/CapitalEvents.kt:224-225` | «Проценты» (начисление) — target = `Deposit(id)` только если живой вклад один, иначе `Section(DEPOSITS)`; **нет deposit id у нескольких вкладов** |
| `domain/model/CapitalEvents.kt:231` | «Переоценка валюты» + reason |
| `domain/model/CapitalEvents.kt:242` | `reasonOf` |
| `ui/history/CapitalHistoryScreen.kt:99-103` | `onTap`: `HistoryTarget.Deposit` → `onOpenTarget(t)` или `gone` |
| `ui/history/CapitalHistoryScreen.kt:314-326` | `EventRow`: subtitle, `maxLines = if reason!=null 2 else 1` |
| `ui/history/CapitalHistoryViewModel.kt:95` | `CapitalEvents.build(...)` |
Нет в домене: `HistoryEvent` для «Проценты» не хранит deposit id при нескольких вкладах и не несёт список затронутых вкладов; `INTEREST` не содержит суммы по каждому вкладу.
Навигация: `onOpenTarget` в `MainActivity` (маршрут истории) — не просмотрено.

## B-76 Экран ввода валюты улетает вверх при клавиатуре
| Место | Что |
|---|---|
| `ui/capital/FxOperationSheet.kt:137` | `SlitokSheet(onDone, scroll = true, imeField = existing == null)` |
| `ui/common/Sheets.kt:96-98` | при `imeField` → `ImeSheet(onDismiss, scroll=true, content)` (комментарий B-62) |
| `ui/common/Sheets.kt:100-119` | `ModalBottomSheet` + `imePadding()` :114 |
| `AndroidManifest.xml:50` | `windowSoftInputMode="adjustResize"` (activity) |
| `ui/capital/FxOperationSheet.kt:219,244` | `CurrencyPickerSheet` (вложенная шторка) |
Вероятная причина: `ImeSheet` / `imePadding` + `adjustResize` в шторке; верхняя плашка уезжает при полном сдвиге (проверить `ImeSheet` в `Sheets.kt`). Сравнить с `CashSheet.kt:73`, `BalanceSheet.kt:59`.
Тесты: нет UI-тестов.

## B-77 Разный шрифт «за сегодня»/«за месяц»
| Место | Что |
|---|---|
| `ui/deposits/DepositsScreen.kt:228-230` | `append(signedMoney(summary.dailyAccrual))` + `append(" сегодня")` — подпись в `buildAnnotatedString`, размер через span |
| `ui/common/DeltaLine.kt:37` | `DeltaLine(..., period: String, spark, ...)` — общий компонент подписи периода |
| `ui/capital/CapitalUi.kt:136` | `DeltaLine(..., "за месяц", ...)` |
| `ui/overview/OverviewScreen.kt:345` | `DeltaLine(..., "за месяц", ...)` |
| `ui/showcase/ShowcaseScreen.kt:296-299` | витрина `DeltaLine` (обновить пример при правке компонента) |
| `ui/common/PeriodChips.kt:24` | `"за месяц"` в `HistoryPeriod` label |
| `ui/deposits/DepositMonthsChart.kt:152-155` | подписи «за месяц» у графика вкладов |
Вероятная причина: `DepositsScreen.kt:228-230` — `" сегодня"` без того же стиля, что `DeltaLine` (проверить `DeltaLine.kt` стиль).
Тесты: `DesignSystemGuardTest` (шрифт токенами).

## F-48 Дробная группировка операций по вкладкам
| Место | Что |
|---|---|
| `domain/model/CapitalEvents.kt:24-33` | `HistoryKind` (INCOME, SPEND, OWN_TRANSFER, TRANSFER, FX_DEAL, DEPOSIT_OPEN, DEPOSIT_TOPUP, DEPOSIT_CLOSE, INTEREST, REVALUATION, CASH) — основа для дробления |
| `domain/model/CapitalEvents.kt:36-53` | `HistoryRow.Single/Group`, `Group.title` :51 («Списания»/«Переводы») |
| `domain/model/CapitalEvents.kt:193-207` | `group(events)`: группирует только SPEND и TRANSFER по `date to kind` (`foldable` :194) |
| `domain/model/CapitalEvents.kt:97` | `MonthEvents(month, events, deltaMinor)`; `byMonth` :273 |
| `ui/history/CapitalHistoryScreen.kt:265,277` | группировка по дню внутри месяца |
| `ui/history/CapitalHistoryScreen.kt:224-290` | `Events` / `RowItem` :291 |
Нет: группировок вкладов (создание/начисление отдельно), логики по вкладкам Капитала/Валют.
Тесты: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/CapitalEventsTest.kt`.

## F-49 Релевантные точки на графиках
| Место | Что |
|---|---|
| `domain/model/CapitalHistory.kt:7` | `CapitalPoint(date, totalMinor, parts)` — точки без событий |
| `domain/model/CapitalHistory.kt:55-80` | `series`, `thin`, `change` |
| `ui/common/CapitalChart.kt:49` | `CapitalChart(points, axis, selected, ...)` — отрисовка, нет маркеров событий |
| `ui/history/CapitalHistoryScreen.kt:211` | передача серии на график |
| `ui/showcase/ShowcaseScreen.kt:304` | витрина `CapitalChart` |
Нет: нет модели «значимой точки» (новый вклад/сумма) на графике.
Тесты: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/CapitalHistoryTest.kt`.

## B-78 Дёргание списка кэшбека при раскрытии группы
| Место | Что |
|---|---|
| `ui/cashback/CashbackLinesEditor.kt:82-96` | `LazyColumn` + `Modifier.cascade(i, reveal && i < firstRows).animateItem(` :90 — анимация элементов при изменении |
| `ui/cashback/CashbackLinesEditor.kt:86` | `PercentState.Asked` — состояние строки |
| `ui/cashback/CashbackBatchScreen.kt:168` | `Box(Modifier.cascade(i, sawReading).shotDropTarget(...))` — каскад в батче |
| `ui/common/Motion.kt:36,87` | `cascade`-модификатор (проверить) |
Вероятная причина: `animateItem` + `cascade` без стабильных ключей `items(key=)` при раскрытии группы (проверить `CashbackLinesEditor.kt:82-96`). Нужен `device-check`.
Тесты: нет.

## F-50 Списания/переводы без раскрытия
| Место | Что |
|---|---|
| `domain/model/CapitalEvents.kt:193-207` | `group()` — группы SPEND/TRANSFER, сейчас раскрываются |
| `ui/history/CapitalHistoryScreen.kt:291-310` | `RowItem`: `is HistoryRow.Group` → `clickable { toggle(row.id) }` :297, стрелка :305, `if (expanded) children` :308 |
| `ui/history/CapitalHistoryScreen.kt:162-166` | `groupsOpen`/`open` state |
| `domain/model/CapitalEvents.kt:9-21` | `HistoryEvent.target` (у SPEND/TRANSFER target = null — не кликабельны) |
| `ui/history/CapitalHistoryScreen.kt:314-326` | `EventRow`: clickable только при `target != null` |
Вероятная причина: `Group` сейчас имеет `clickable`/стрелку, включая SPEND/TRANSFER; нужен флаг `expandable` (или поведение только по kind) в `HistoryRow.Group`.
Тесты: `CapitalEventsTest.kt`.

## Пересечение файлов
| Файл | B-74 | F-46 | B-75 | F-47 | B-76 | B-77 | F-48 | F-49 | B-78 | F-50 |
|---|---|---|---|---|---|---|---|---|---|---|
| `ui/history/CapitalHistoryScreen.kt` | | x | x | x | | | x | | | x |
| `domain/model/CapitalEvents.kt` | | x | | x | | | x | | | x |
| `ui/common/CapitalChart.kt` | | x | x | | | | | x | | |
| `ui/common/SectionPager.kt` | | | x | | | | | | | |
| `ui/history/CapitalHistoryViewModel.kt` | | | | x | | | x | | | |
| `domain/model/CapitalHistory.kt` | | | | | | | | x | | |
| `ui/cashback/CashbackLinesEditor.kt` | x | | | | | | | | x | |
| `ui/cashback/CashbackForm.kt` | x | | | | | | | | | |
| `ui/cashback/CashbackBatchScreen.kt` | x | | | | | | | | x | |
| `ui/capital/FxOperationSheet.kt` | | | | | x | | | | | |
| `ui/common/Sheets.kt` | | | | | x | | | | | |
| `ui/common/DeltaLine.kt` | | | | | | x | | | | |
| `ui/deposits/DepositsScreen.kt` | | | | | | x | | | | |
| `ui/showcase/ShowcaseScreen.kt` | | | | | | x | | x | | |
| `ui/common/Motion.kt` | | | | | | | | | x | |

Группы для разработки: (A) `CapitalHistoryScreen.kt` + `CapitalEvents.kt` + VM — F-46, F-47, F-48, F-50 (одна группа); F-49 + CapitalChart отдельно или в группе A; B-75 — `SectionPager`/`CapitalChart` (связан с F-46 через `CapitalChart.kt`); (B) кэшбэк — B-74, B-78 (`CashbackLinesEditor.kt`); (C) B-76 `Sheets.kt`/`FxOperationSheet.kt`; (D) B-77 независимо (`DepositsScreen.kt`, `DeltaLine.kt`).

Тесты, затрагиваемые: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/CapitalEventsTest.kt`, `CapitalHistoryTest.kt`. Нет тестов на UI-поведение (B-74/75/76/77/78).

Не проверено: `MainActivity` маршрут `onOpenTarget`, `CapitalHistoryViewModel` полностью, `ImeSheet` (в `Sheets.kt`, строки ниже 96).
