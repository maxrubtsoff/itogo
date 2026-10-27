# Карта кода цикла 14

Сканирование неполное: часть координат не проверена, помечено «не проверено».

## B-107 — парсер Альфа-Бизнес «Списано … п/п … Остаток»
- `app/src/main/java/ru/maxrubtsoff/bankagg/domain/parser/NotificationParser.kt:64` — AMOUNT_THEN_CCY (сумма + знак валюты)
- `.../NotificationParser.kt:67` — CCY_THEN_AMOUNT
- `.../NotificationParser.kt:75` — MASK (маска счёта/карты, `*4863` сейчас вырезается)
- `.../NotificationParser.kt:92` — EXPENSE: слова списания («списание», «оплата»…)
- `.../NotificationParser.kt:93` — BALANCE_MARKERS («доступно», «остаток», «баланс»)
- `.../NotificationParser.kt:97` — список слов «деньги ушли» (`списан` уже есть, `п/п` нет)
- `.../NotificationParser.kt:147` — `parse(title, text, defaultCurrency)`, вход
- `.../NotificationParser.kt:150` — очистка: MASK и PHONE заменяются пробелами до поиска суммы
- `.../NotificationParser.kt:162–166` — граница «баланс» и выбор первой суммы до неё
- `.../NotificationParser.kt:171` — разбор остатка `balance`
- `.../NotificationParser.kt:177–182` — сборка `ParsedOp` (amount, balance)
- `app/src/test/java/ru/maxrubtsoff/bankagg/domain/NotificationParserTest.kt` — тесты парсера (сами кейсы не читал)
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/log/LogScreen.kt:89,104–105` — `StatusChip(e.parsed)`: «разобрано» / «не разобрано»
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/log/LogViewModel.kt:11` — ViewModel журнала (содержимое не проверено)

## B-108 — подписи сумм у точек графика капитала
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/common/CapitalChart.kt:69` — `fun CapitalChart(...)`, компонент графика
- `.../CapitalChart.kt:80` — `markColor` по умолчанию `{ color }`
- `.../CapitalChart.kt:165` — заливка под линией: `Brush.verticalGradient(color.copy(alpha = 0.18f) → Transparent)`
- `.../CapitalChart.kt:166–167` — свечение линии (alpha 0.22, 7 dp) и сама линия (2 dp, `color`)
- `.../CapitalChart.kt:172–176` — маркеры точек (`MarkShape` BIG/SMALL/RING), цвет `tint = markColor(m.part)`
- `.../CapitalChart.kt:180` — **обводка подписи**: `drawText(s.layout, color = Slitok.Bg.copy(alpha = 0.7f), drawStyle = Stroke(0.75.dp))`
- `.../CapitalChart.kt:181` — **текст подписи**: `drawText(s.layout, color = Slitok.Muted, topLeft = s.topLeft)`
- `.../CapitalChart.kt:199` — подписи осей: `labelSmall`, `Slitok.Faint`
- `.../CapitalChart.kt:224` — `class LabelSpot(layout, topLeft)`
- `.../CapitalChart.kt:231` — `placeLabels(...)`: раскладка подписей у точек
- `.../CapitalChart.kt:52–64` — KDoc: подписи крупных меток, «не касаются линии и точки»
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/history/CapitalHistoryScreen.kt:300–302` — вызов `CapitalChart(points, axis, selected, marks = u.marks, markColor = { it.color() })`
- Тесты: не проверено

## B-109 — расхождение суммы точки графика и списка событий
Ветка «график»:
- `app/src/main/java/ru/maxrubtsoff/bankagg/domain/usecase/GetCapital.kt:26–35` — `invoke(allParts)`: сумма через `CapitalCalculator.compute` (стр. 33), фильтр по `features.capitalParts` (стр. 32, 34)
- `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/Capital.kt:74` — `object CapitalCalculator`
- `.../Capital.kt:76` — `compute(...)`
- `.../Capital.kt:156` — `lastChangeOf(...)`
- `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/CapitalPast.kt:23` — `withDeposits(points, deposits, included, today)` (вклады добавляются к точкам)
- `.../CapitalPast.kt:14` — `depositsOn(deposits, day)`
- `.../CapitalPast.kt:69` — `withShifts(...)`
- `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/CapitalReconstruction.kt:41` — `build(...)`: восстановление точек по дням
- `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/CapitalHistory.kt:69` — `series(points, period, today, part)` — ряд для графика
- `.../CapitalHistory.kt:44` — `totalled(points, included)`
- `.../CapitalHistory.kt:9` — `valueOf(part)`: `null` = total
Ветка «события»:
- `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/CapitalEvents.kt:152` — `build(transactions, accounts, ops, deps, cash, days, today, rates, cashChanges)`
- `.../CapitalEvents.kt:385` — `within(events, from, part)` (фильтр событий периода)
- `.../CapitalEvents.kt:389` — `byMonth(events, points, part)`
- `.../CapitalEvents.kt:320` — `splitInterest(...)` (проценты вклада)
- `.../CapitalEvents.kt:403` — `rubOf(t, rates, zone)`
Оркестрация UI:
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/history/CapitalHistoryViewModel.kt:116` — `CapitalEvents.build(...)` (события)
- `.../CapitalHistoryViewModel.kt:119` — `CapitalHistory.series(i.days, period, day, p)` (график)
- `.../CapitalHistoryViewModel.kt:125–127` — `series`, `change`, `events = shown`, `marks = ChartMarks.of(...)`
- Тесты: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/CapitalCalculatorTest.kt`, `.../CapitalHistoryTest.kt` (кейсы не читал)
- Вывод для агента: `series` строится из `i.days` (дневные точки), события — из `CapitalEvents.build`; точная причина расхождения не выявлена, не проверено.

## B-110 — обрезанные шторки истории капитала
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/history/CapitalHistoryScreen.kt:123` — `OperationSheet(...)` открывается из экрана
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/history/OperationSheet.kt:65` — `fun OperationSheet(...)`
- `.../OperationSheet.kt:81` — `SlitokSheet(onDismiss, imeField = mode == Mode.EDIT) { ... }`
- `.../OperationSheet.kt:158` — `EditBody(...)` (шторка «Изменить»)
- `.../OperationSheet.kt:193–195` — список счетов (`SlitokChip` + `BankIcon`) — самодельный выбор банка/счёта
- `.../OperationSheet.kt:182–183` — приход/расход (`SlitokChip`)
- `.../OperationSheet.kt:213`, `:237` — кнопки «Отмена»
- Общий компонент: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/common/Sheets.kt:88` — `SlitokSheet(onDismiss, scroll = true, imeField = false, content)`
- `.../Sheets.kt:100–102` — `ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true))`
- `.../Sheets.kt:108–114` — Column: `verticalScroll` (если `scroll`), `navigationBarsPadding()`, `imePadding()`
- `.../Sheets.kt:173` — `ImeSheet` (ветка для ввода)
- `.../Sheets.kt:191` — `topInset` (статус-бар + 8 dp) для ImeSheet
- `.../Sheets.kt:273` — `InlineSheet` (внутрислойная шторка)
- Шторка выбора банка: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/common/BankPickSheet.kt:21,25` — `SlitokSheet(onDismiss)` (см. U-69)
- Причина обрезки в коде не установлена, не проверено (device-check).

## U-69 — выбор банка: все места
Канонический компонент:
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/common/BankPickSheet.kt:21` — `BankPickSheet(selected: BankBrand?, onPick, onDismiss, onClear: (() -> Unit)? = null)`
- Поле выбора: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/cashback/CashbackForm.kt:61` — `BankPickerRow(selected, onSelect, modifier)`; шторка — `CashbackForm.kt:74`
- Использования `BankPickerRow`: `ui/deposits/DepositForm.kt:172`; `ui/deposits/DepositBatchScreen.kt:239`; `ui/cashback/CashbackBatchScreen.kt:271`; `ui/cashback/BankCashbackEditScreen.kt:115`; `ui/cashback/ShotPreviewScreen.kt:121`
- Прямые `BankPickSheet`: `ui/cashback/SaveDialogs.kt:41` (`BankPickDialog`); `ui/subscriptions/SubscriptionScreens.kt:187` (через `SelectRow`-подобную строку, см. design-system/README.md:69)
Самодельный выбор:
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/history/OperationSheet.kt:193–195` — счета/банки `SlitokChip` в шторке «Изменить» истории капитала (не `BankPickerRow`/`BankPickSheet`)
- Шторка выбора банка на экране «История капитала» — отдельной шторки не найдено; в истории банк выбирается через `OperationSheet` (не проверено полностью)

## U-70 — правила для ИИ
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/CLAUDE.md:6` — «Только компоненты `ui/common` и токены»
- `.../ui/CLAUDE.md:8` — «Нет нужного компонента → сначала добавь его в дизайн-систему»
- `.../ui/CLAUDE.md:15` — запрет своих кнопок/полей; ссылка на `DesignSystemGuardTest`
- `docs/design-system/README.md:9–10` — «Экран собирается только из компонентов (`ui/common`)»; нового компонента нет — сначала в README/витрину
- `docs/design-system/README.md:55` — раздел «Компоненты (`ui/common`)»
- `docs/design-system/README.md:69` — строка `SelectRow` + `BankPickSheet` (выбор одного значения; «лента банков» — не для него)
- `.claude/agents/designer.md:13–15` — экран только из `ui/common`; нет компонента → добавить; перечень проверки в `:29`
- `.claude/agents/reviewer.md:19` — проверка «Слиток», `DesignSystemGuardTest`
- Раздела «переиспользовать существующий компонент выбора банка» нет; общее правило «нет компонента → добавить» есть
- `app/src/test/java/ru/maxrubtsoff/bankagg/ui/DesignSystemGuardTest.kt:13` — класс теста

## B-111 — подсказка обучения поверх шторки «Добавить вклад»
- Текст подсказки: `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/Tour.kt:143` и `:150` — `TourStep(TourTarget.ADD_SHEET, "Со скрина быстрее", ..., anim = TourAnim.OPEN_SHEET)`
- Показ: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/onboarding/Tour.kt:269` — `TourCoach(session, onFinished, onAllowAccess)`; карточка `CoachCard` `:369`; кнопки «Ещё раз» `:414`, «Далее» `:421`
- Оверлей-песочница: `.../onboarding/Tour.kt:155` — `TourSandboxLayer`; `:168` — `LocalTourOffer provides { _, _ -> null }`
- Сессия: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/onboarding/TourSession.kt:52` — `class TourSession`; `:99` `next()`, `:103` `again()`, `:125` `play()`
- Запуск из MainActivity: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/MainActivity.kt:1028` — `TourCoach(...)`; `:645–668` — `tourFromLearning`; `:700` — `LocalTourOffer provides tourOffer`
- Предложение тура: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/onboarding/Learning.kt:39` — `LocalTourOffer`
- Вход «Вклады»: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/deposits/DepositsScreen.kt:162` — `EmptyState(onAdd, LocalTourOffer.current(TourSection.DEPOSITS, false))`; `:298` — `tourTarget(TourTarget.LIST_ROW)`
- Шторка «Добавить вклад»: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/deposits/DepositsScreen.kt:192` — `AddSheet(...)`
- Что не связано: `TourCoach` не читает состояние открытых шторок, `AddSheet` не читает состояние тура — связи нет (по сканированию). Не проверено подробно.

## B-112 — нижний отступ шторки «Добавить вклад»
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/common/Buttons.kt:257` — `fun AddSheet(title, onShots, onManual, onDismiss)`
- `.../Buttons.kt:258` — `SlitokSheet(onDismiss) { ... }`
- `.../Sheets.kt:113` — `navigationBarsPadding()` в колонке `SlitokSheet`
- `.../Sheets.kt:280–282` — `InlineSheet`: `navigationBarsPadding()`
- Для сравнения: `ui/common/ShotBatchUi.kt:149` — `AddShotSheet` → `SlitokSheet` (тот же путь)
- Вызов: `ui/deposits/DepositsScreen.kt:192`; также `ui/cashback/CashbackScreen.kt:226`, `ui/MainActivity.kt:1043`
- Причина обрезки не установлена, не проверено (device-check).

## U-68 — скрин уже существующего вклада
- `app/src/main/java/ru/maxrubtsoff/bankagg/domain/usecase/RecognizeDepositShot.kt:21` — `class RecognizeDepositShot`
- `.../RecognizeDepositShot.kt:24–29` — `Draft(status, brand, deposit, duplicateOf: Deposit?)`
- `.../RecognizeDepositShot.kt:32` — `invoke(shot, existing)`
- `.../RecognizeDepositShot.kt:52` — `DepositMatch.find(parsed.brand, d, existing)` — проверка дубликата
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/deposits/DepositsViewModel.kt:44` — `recognizeShot: RecognizeDepositShot`
- `.../DepositsViewModel.kt:107` — отложенные дубликаты пакета (U-66)
- `.../DepositsViewModel.kt:112`, `:121` — `pendingDuplicates` / возврат
- `.../DepositsViewModel.kt:236` — `if (draft.duplicateOf != null) { ... }` — ветка дубликата
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/deposits/DepositBatchScreen.kt:91, 101–110` — `pendingDuplicates`, `onDone()` при пустом списке и без дубликатов
- `.../DepositBatchScreen.kt:142–144` — «N пропустили · Вернуть»
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/deposits/DepositShotPreviewScreen.kt:80` — подсказка `ShotHints::deposit`
- Навигация назад: `app/src/main/java/ru/maxrubtsoff/bankagg/ui/MainActivity.kt:907` (`DepositShotPreviewScreen(... onBack = { if (fromBatch) popBackStack else backToDeposits(nav) })`), `:916` (`onDone = { backToDeposits(nav) }`)
- Сообщения о дубликате одиночного скрина: в `DepositsViewModel`/экранах не найдено отдельного текста (не проверено)
- Подсветка операции (жёлтая полоска слева, временная): `app/src/main/java/ru/maxrubtsoff/bankagg/ui/history/CapitalHistoryScreen.kt:380` — `litChild`; `:385`, `:397` — `highlighted = lit`; `:405` — `EventRow(... highlighted: Boolean)`; `:392` — `RowItem` с `highlighted`. Сама отрисовка полоски (drawRect/Box) не найдена в этих строках, не проверено.
- Скролл к вкладу: `DepositsScreen.kt:298` — `tourTarget(LIST_ROW, active = ...)`; механизма скролла к карточке не найдено (не проверено)
