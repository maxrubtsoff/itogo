# План цикла 12: баги B-74 … B-78 (architect)

Все пять — чистый UI, без домена/Room/DI/миграций. Дизайн не меняется. Доменных тестов нет (логики не добавляется); `DesignSystemGuardTest` не затрагивается (цвета/шрифты только токенами). Проверка везде: `./gradlew testDebugUnitTest assembleDebug` зелёные + device-check там, где указано.

Группы: (1) B-74 — `CashbackForm.kt`; (2) B-78 — `Reorder.kt`+`CashbackScreen.kt`; (3) B-75 — `SectionPager.kt`+`CapitalHistoryScreen.kt`; (4) B-76 — `Sheets.kt`; (5) B-77 — `DepositsScreen.kt` (+`DeltaLine.kt`/витрина без правок). Файлы между собой не пересекаются, можно параллельно. Внимание: `CapitalHistoryScreen.kt` ещё правят F-46/47/48/50 — B-75 делать в той же группе, а не отдельно.

## B-74
**Цель:** процент в строке кэшбэка (после распознавания скрина) виден целиком.

**Причина (подтверждена по коду):** `ui/cashback/CashbackForm.kt:158` — поле процента `width(76.dp)` с `padding(horizontal = 10.dp)` (:163), внутри `BasicTextField weight(1f)` (:175) + `Spacer 3.dp` + «%» (:177-178). Остаётся ~41 dp на текст стилем `Amounts.Row` (Unbounded SemiBold 16sp, `Type.kt:106`, ~11 dp на цифру). Распознанный процент приходит как `percentToText` (`CashbackViewModel.kt:216,266`), например «12,5» / «10,00» (до 5 знаков, `take(5)` :169) — не влезает, хвост/начало уезжает за край. `CashbackBatchScreen.kt:285` (карточка банка) не причина: там `Text` без ширины, ограничения нет.

**Правки:**
- `CashbackForm.kt:158`: `.width(76.dp)` → `.width(100.dp)` (5 знаков + «%» помещаются: 100−20−3−~14 ≈ 63 dp под ~55 dp текста). Выбор: расширить поле, а не уменьшать шрифт (шрифт — токен `Amounts.Row`, его не трогаем). Название категории (`weight(1f)`, `maxLines = 2`, :106-121) сузится на 24 dp — переносится по словам, это уже предусмотрено (B-15, B-54).
- Больше ничего.

**Критерии:** строка «Вклад»/«Альфа-Банк» с «12,5», «10,00» и «100» показывает все знаки; «×» и название не вылезают; ширина одинакова во всех строках (поле фикс). **device-check: да** (экран распознавания скрина; проверить и ручную форму банка — `CashbackLinesEditor` общий; и с крупным системным шрифтом).

## B-75
**Цель:** на экране «История капитала» (открывается из «Обзора») горизонтальный свайп переключает разделы с любой точки экрана, включая пустое место под контентом; скраб графика по-прежнему работает.

**Причина (подтверждена по коду):** жест `detectHorizontalDragGestures` висит на самом `HorizontalPager` (`SectionPager.kt:39-41,65`), а его высота = высота контента страницы. В `CapitalHistoryScreen.kt:121` пейджер лежит внутри `Column(...verticalScroll)` (высота не ограничена), поэтому пейджер не занимает пустую часть экрана под контентом/вокруг — касание там не попадает ни в какой `pointerInput`. Конфликта с графиком нет в обратную сторону: drag графика (`CapitalChart.kt:84-91`) стоит ниже в иерархии, получает событие первым и потребляет его (slop-детектор), так что родитель на графике свайп не получает — это и нужно сохранить. Рядом с этим (CapitalScreen `:180` — `Modifier.fillMaxSize()`, PaymentsScreen `:57` — `weight(1f)`) пейджеры уже на всю область, там бага нет.

**Правки (решение: вынести жест в модификатор и повесить на весь экран; на графике он не срабатывает, потому что график потребляет drag раньше):**
1. `ui/common/SectionPager.kt`: тело `swipe` (строки 37-62) вынести в публичный `fun Modifier.sectionSwipe(state: PagerState): Modifier` (внутри `composed`/`pointerInput(state)`, `rememberCoroutineScope`, `flingPx` из `LocalDensity`; логика `onDragEnd`/`dispatchRawDelta` без изменений, `if (state.pageCount < 2) this`). `SectionPager` получает параметр `swipe: Boolean = true` и применяет `sectionSwipe` только при `true` (остальные экраны не меняются). Не менять `change.consume()` в обработчике: на графике он не вызовется, т.к. событие уже потреблено.
2. `ui/history/CapitalHistoryScreen.kt`: `rememberPagerState`/`LaunchedEffect`-ы из `HistorySections` (:137-149) поднять в `CapitalHistoryScreen` (до `Scaffold`, :119). Так как `ui` может быть `null`, pageCount считать из лямбды: `rememberPagerState(initialPage = …) { pages }` — взять `sections`/`kept` из `ui` с безопасным значением по умолчанию (1 страница, пока `ui == null`); initialPage вычислять при первом непустом `ui`, иначе `0` (если нужно — `key(ui != null)` вокруг создания, но сохранить текущее поведение выбора раздела `CapitalHistory.pageOf(u.part, sections)`). Передать `pager` в `HistorySections(u, vm, onTap, pager)`.
3. `CapitalHistoryScreen.kt:121`: в цепочку `Column` вставить `.sectionSwipe(pager)` ПЕРЕД `.verticalScroll(...)` (после `.padding(padding)`, до `.padding(horizontal = 16.dp)`) — область жеста = вся рабочая область экрана, включая поля и пустое место. `SectionPager(pager, swipe = false) { … }` в :153.
4. Порядок событий: вертикальный скролл и горизонтальный свайп не конфликтуют (разные оси, slop-детекторы). Скраб на графике: drag графика потребляет изменение при выходе за slop раньше родителя → страница не листается; тап по графику не затронут.

**Тесты:** нет (UI-жест). **Критерии:** свайп влево/вправо по пустой области под последней операцией, по полям слева/справа и по шапке плашки переключает раздел; по графику — только скраб (раздел не меняется, плавающая линия выбора двигается); вертикальный скролл списка работает; на первой/последней странице ничего не «тянется». **device-check: да** (в т.ч. короткая история, когда контент меньше экрана).

**Уточнение (группа истории, цикл 12):** B-75 — **первый коммит** группы «История» (B-75 → F-50 → F-47 → F-48-данные → F-48 → F-46 → F-49), до правок ленты. Состояние `picked`/`open` остаётся внутри `HistoryPage` (на страницу), не поднимать вместе с пейджером. Прилипание к точкам (F-49) и полоса недели (F-46) живут внутри `CapitalChart` и потребляют drag так же — п.4 остаётся в силе; device-check F-49 повторяет проверку «свайп мимо графика листает, по графику — скраб».

## B-76
**Цель:** при открытии клавиатуры в листе покупки/продажи валюты верхняя часть (заголовок, плашки валюты/даты) не уходит под статус-бар и не обрезается.

**Анализ (`Sheets.kt`, `ImeSheet` :173-249, `ImeReserve` :143-163):** для `imeField = true` (FxOperationSheet :137 при создании) лист — `Dialog(decorFitsSystemWindows = false)`; корневой `Box(fillMaxSize … .statusBarsPadding())` (:193-198), внутри `Column` по низу: ручка + `weight(1f, fill=false)` скролл-контент + `ImeReserve` (высота `max(held, ime, nav)`, :157-158) + 12 dp. Лист — это `Dialog`, окно которого в манифестной настройке `adjustResize` (`AndroidManifest.xml:50`) не участвует (настройка activity), а `WindowInsets.statusBars`/`ime` внутри окна диалога с `decorFitsSystemWindows=false` приходят нестабильно (на части версий статус-бар-инсет в окне диалога = 0 до первого прохода/после показа IME). Тогда `statusBarsPadding()` даёт 0, а высокий лист (форма валюты — самая высокая из `imeField`-шторок: `CashSheet.kt:73`, `BalanceSheet.kt:59` ниже) с резервом клавиатуры упирается в верх окна и контент оказывается под статус-баром. Точную сторону (нулевой инсет или двойной учёт IME при ресайзе окна) без устройства не отличить — поэтому правка закрывает обе.

**Правки (все в `ui/common/Sheets.kt`, `ImeSheet`):**
1. Верхний инсет брать не из окна диалога, а из окна activity: до вызова `Dialog(...)` (:189) прочитать `val top = WindowInsets.statusBars.getTop(LocalDensity.current)` (композиция родителя, надёжный инсет) и в `Box` (:193-198) заменить `.statusBarsPadding()` на `.padding(top = with(density) { top.toDp() } + 8.dp)`. Запас 8 dp — чтобы плашка не прилипала к статус-бару.
2. Двойной учёт IME исключить: в `ClearSystemBars` для `ImeSheet` (:190) выставить `window.setSoftInputMode(SOFT_INPUT_ADJUST_NOTHING)` (флаг передать параметром, по умолчанию не трогать — остальные диалоги без изменений); место под клавиатуру уже даёт `ImeReserve`, окно ресайзиться не должно. Если на API 26-29 `WindowInsets.ime` без resize перестанет приходить, оставить `SOFT_INPUT_ADJUST_RESIZE` и убрать из п.1 лишнее — проверить на эмуляторе API 28 и 34; выбор по умолчанию: ADJUST_NOTHING для API ≥ 30, ADJUST_RESIZE ниже.
3. Явный предел высоты скролл-блока: внутреннему `Column` (:235-243) добавить `heightIn(max = …)` не нужно (weight уже ограничивает), но проверить, что `weight(1f, fill = false)` стоит ДО `ImeReserve` — оставить как есть; если на устройстве контент всё равно прокручивается так, что заголовок скрыт, то в `FxOperationSheet.kt` форма уже внутри скролла и `keepVisibleWhenFocused` (`Keyboard.kt:42`) прокручивает к полю — это штатно, верх при этом должен оставаться виден за счёт лимита высоты (п.1).
4. `CurrencyPickerSheet` (`FxOperationSheet.kt:244`) и `SlitokDatePicker` — обычные `ModalBottomSheet` (не `imeField`), не меняются.

**Критерии:** на экране 6" и малом экране (эмулятор 5", 360×640) открыть «Купить/Продать валюту» → клавиатура → заголовок, «$ USD», дата полностью видны ниже статус-бара; сохраняются закрытие свайпом вниз/по вуали, перелёт листа «одним движением» (B-58), отсутствие пустого хвоста после закрытия клавиатуры; `CashSheet`/`BalanceSheet` не регрессируют. **device-check: да, обязателен** (причину точно сужает только устройство).

## B-77
**Цель:** подпись периода («сегодня», «за месяц», «за неделю») в блоках с графиком везде одним стилем.

**Причина (подтверждена):** эталон — `ui/common/DeltaLine.kt:69` — подпись периода `bodySmall` (Muted), сумма `bodyMedium`. На «Вкладах» (`ui/deposits/DepositsScreen.kt:225-234`) вся строка `AmountText(buildAnnotatedString{ сумма; " сегодня" }, style = bodyMedium)` — «сегодня» набрано `bodyMedium`, т.е. крупнее, чем «за месяц» в `DeltaLine`.

**Все места подписей периода (проверено grep по ui/):**
- `DeltaLine` (`bodySmall`, эталон): `CapitalUi.kt:136`, `OverviewScreen.kt:345`, `ShowcaseScreen.kt:296-299` — уже одинаково, не менять.
- `DepositsScreen.kt:228-230` — РАСХОЖДЕНИЕ, править.
- `CapitalHistoryScreen.kt:199,207` («без изменений ${spanWord}», «с 1 окт.») — `bodyMedium`; это не подпись периода под графиком, а строка дельты в шапке плашки; для единства привести `"с …"` (:207) к `bodySmall`, сумму оставить `bodyMedium`, как в `DeltaLine`; «без изменений …» (:199) тоже `bodyMedium` как в `DeltaLine.kt:58` — оставить.
- `DepositMonthsChart.kt:150-156` — `ChartFact` label `bodySmall` — совпадает, не менять.
- `PeriodChips.kt:20-25` — значения «за неделю/месяц/год» — тексты, шрифт чипа; не менять.

**Правки:**
1. `DepositsScreen.kt:228-231`: сумма остаётся в `SpanStyle(Mint, SemiBold)` (размер `bodyMedium`), а ` сегодня` добавить через `withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodySmall.fontSize, color = Slitok.Muted))`. Внешний `AmountText` оставить `bodyMedium`. (Токены, не хардкод.)
2. `CapitalHistoryScreen.kt:207`: `MoneyText("с …", style = MaterialTheme.typography.bodySmall, …)`; выравнивание `Alignment.Bottom` (:201) уже есть — базовые линии сохранятся.
3. Витрину/README не менять (компонент `DeltaLine` не меняется).

**Критерии:** на «Вкладах», «Капитале», «Обзоре», «Истории» подпись периода визуально одного размера (labels Muted `bodySmall`), сумма — `bodyMedium`; ничего не усечено (B-72 сохраняется). **device-check: да** (быстрый скрин четырёх экранов).

## B-78
**Цель:** раскрытие/сворачивание группы категории в списке «Кэшбэк» не вызывает прыжков строк ниже.

**Причина (подтверждена по коду; карта указывала на `CashbackLinesEditor`, но «группа категории» — это `CashbackScreen`, а не редактор):** `ui/cashback/CashbackScreen.kt:288-310` — каждая строка с `Modifier.reorderable(this@LookaheadScope)` (`ui/common/Reorder.kt:25-51`). `reorderable` анимирует ЛЮБОЙ сдвиг позиции строки к `target = lookaheadPosition` собственным tween (`CLOSE_MS`, :45). При раскрытии `AnimatedVisibility` (`CashbackScreen.kt:382`) lookahead сразу считает конечную высоту, `target` строк ниже скачет вниз на всю высоту раскрытия, а реальный layout растёт по кадрам: `shift = anim.value − current` (:47) расходится с настоящим положением → строки ниже сначала «убегают», потом возвращаются (вверх/вниз). Ключи стабильны (`key(row.groupKey + "|" + row.category)` :292), `cascade` (:303) играет один раз — они не при чём.

**Правки:**
1. `Reorder.kt`: `fun Modifier.reorderable(scope: LookaheadScope, active: () -> Boolean = { true })`. В layout-блоке (:33-49): если `!active()` — `anim` держать синхронным (`if (anim.targetValue != target) coroutines.launch { anim.snapTo(target) }`, без tween) и `placeable.place(0, 0)`, `return@layout`. Т.е. сдвиги, не вызванные сменой порядка, не анимируются дополнительно: их ведёт сам `AnimatedVisibility`. `isPlacementApproachInProgress` остаётся `offset?.isRunning == true`.
2. `CashbackScreen.kt` (около :274): `var reordering by remember { mutableStateOf(false) }`; в обработчиках смены порядка (`onFlip` :282 и `onPick` :321 — обернуть: `reordering = true` перед вызовом) и `LaunchedEffect(order) { if (reordering) { delay(CLOSE_MS + 100L); reordering = false } }` (`CLOSE_MS` сделать `internal` в `Reorder.kt`). `reorderable(this@LookaheadScope) { reordering }` (:293). Сортировка по-прежнему плавно перемещает строки (U-27), раскрытие — нет.
3. Если Reorder используется ещё где-то (grep `reorderable(`) — вызовы остаются с `active = { true }` по умолчанию; тот же дефект там проверить `device-check` (вклады) и при наличии применить тот же флаг.

**Критерии:** раскрытие/сворачивание группы — нижние строки плавно едут вместе с растущим блоком, без рывков вверх-вниз; смена сортировки по-прежнему анимирует перестановку; «Убрать анимации» — мгновенно. **device-check: да** (раскрыть/свернуть группу у верхней, средней, последней строк; затем сменить сортировку).

---

# Группа «История капитала»: B-75, F-50, F-47, F-48, F-46, F-49 (architect)

Пути от `app/src/main/java/ru/maxrubtsoff/bankagg/`. Макет: `docs/mockups/cycle-12/history-f46-f50.html` (вкладка «Вместе» + варианты A/B по утверждению). Один `coder` последовательно, **коммит на пункт**, порядок:

| # | Коммит | Зачем в этом порядке |
|---|--------|----------------------|
| 1 | `Цикл 12: B-75 …` | жест пейджера до перестройки ленты (план выше) |
| 2 | `Цикл 12: F-50 …` | тип строки (`RowKind`) + компонент `HistoryLine` — на нём стоят все следующие |
| 3 | `Цикл 12: F-47 …` | части (`parts`) у «Процентов»/«Переоценки» |
| 4 | `Цикл 12: F-48 история наличных (Room v18)` | данные для групп/отметок наличных |
| 5 | `Цикл 12: F-48 …` | группы день/месяц по вкладкам, строки строит домен |
| 6 | `Цикл 12: F-46 …` | недельная серия, отрезок точки, подсветка — опирается на `sources` строк из F-48 |
| 7 | `Цикл 12: F-49 …` | отметки на графике + прилипание — опираются на отрезки точек из F-46 |

После каждого коммита: `./gradlew testDebugUnitTest assembleDebug` зелёные. Существующие тесты `CapitalEventsTest`/`CapitalHistoryTest`, которые закрепляли старое поведение (FX без суммы, `FX_DEAL`, `Group.title` «Списания · N», `target` у рублёвых операций), **переписать под новое правило** в коммите, который его меняет — не удалять и не ослаблять остальные.

**Общая доменная модель (вводится по коммитам, итог):**
- `HistoryEvent` (`domain/model/CapitalEvents.kt:9`) получает поля с дефолтами: `parts: List<HistoryEvent> = emptyList()` (F-47), `bank: BankBrand? = null` (F-47, ведущий значок части-вклада), `foreign: ForeignAmount? = null` (F-49, подпись в валюте сделки). Новый `data class ForeignAmount(val currency: String, val minor: Long)` рядом.
- `HistoryKind` (:24): `FX_DEAL` → `FX_BUY`, `FX_SELL` (F-48: группы «Покупки»/«Продажи» раздельно).
- `enum class RowKind { GO, OPEN, SUM }` — тип строки по правилу макета: `GO` ведёт в объект, `OPEN` раскрывается на месте, `SUM` — итог без действия (F-50).
- `HistoryRow` (:36) — общие `id: String`, `date`, `kind: RowKind`, `sources: List<HistoryEvent>` (из чего строка сложилась — для подсветки F-46). `Single(id, event)`; `Group(id, date, eventKind: HistoryKind, part: CapitalPart, title, subtitle: String?, totalMinor: Long, children: List<HistoryEvent>, sources, target: HistoryTarget?, kind: RowKind)`. Вычисляемый `Group.title` (:51) удаляется — заголовок считает домен.
- Новый файл `domain/model/HistoryRows.kt` (`object HistoryRows`) — группировка и правила строки; `CapitalEvents.group` (:193) удаляется (его заменяет `HistoryRows.days`).

---

## F-50
**Цель:** списания/переводы/поступления в рублях выглядят и ведут себя как итог: без признака перехода и без отклика; группы списаний/переводов не раскрываются.

**Домен:**
1. `CapitalEvents.kt:130`: `target` у операции = `HistoryTarget.Currency(t.currency)` только для нерублёвых; для рублёвых — `null` (решение утверждено: в «Счета» целиком вести бессмысленно).
2. `RowKind` + `HistoryRows.kindOf(e: HistoryEvent): RowKind` — `parts.isNotEmpty()` → `OPEN`; `target != null` → `GO`; иначе `SUM`.
3. `HistoryRows.groupKind(eventKind, children): RowKind` — `SUM`, если `eventKind in {SPEND, TRANSFER, TRANSACTION}` и у всех детей `target == null`; иначе `OPEN` (валютные списания раскрываются — дети ведут в валюту). Одна функция для дня и месяца.
4. Заголовок/подпись итоговых групп: заголовок без числа («Списания», «Переводы», «Операции»), подпись — `RuPlural.operations(n)` («4 операции»). Добавить `RuPlural.operations` (`domain/model/RuPlural.kt`, рядом с `events` :45: операция/операции/операций).
5. Временно (до F-48) `HistoryRows.days(events, page)` повторяет текущее `group()` (только SPEND/TRANSFER по дню) уже в новом `HistoryRow` — заменяет `CapitalEvents.group`; вызовы в `CapitalHistoryScreen.kt:265,277` переключить.

**UI (дизайн-система):**
6. Новый компонент `ui/common/HistoryLine.kt`: `HistoryLine(title, subtitle: String?, amountMinor: Long?, kind: RowKind, modifier, expanded = false, highlighted = false, nested = false, subtitleLines = 1, lead: (@Composable () -> Unit)? = null, onClick: () -> Unit = {})`. Данные строки — **`kind` и `highlighted`**; правило: `SUM` — без `clickable` и без ripple (касание ничего не делает), `GO`/`OPEN` — `clickable(onClick)`. **Визуальная раскладка признака «ведёт/раскрывается» — параметр по варианту, который Максим выбирает во вкладке «Строка · варианты» макета (справа только сумма; признак — в подписи, счётчиком или слева); оркестратор уточнит перед F-50, coder делает по выбранному.** Подсветка (`highlighted`) — **только полоса слева `Brass2`** без заливки; появление за `Motion.NORMAL` (с «Убрать анимации» — сразу). Сумма — `MoneyText(signedSum(..), Amounts.Small, Mint если > 0, иначе Fg)`. `signedSum` (`CapitalHistoryScreen.kt:350`) перенести в `HistoryLine.kt` как публичную `fun signedSum(minor: Long)` (история импортирует).
7. `CapitalHistoryScreen.kt:291-328`: `RowItem`/`EventRow` переписать на `HistoryLine`; `Group` с `RowKind.SUM` не реагирует и не раскрывается; заголовок месяца (:253-263) — тоже `HistoryLine(kind = OPEN, expanded)`, чтобы суммы стояли по одному правому краю.
8. README (`docs/design-system/README.md`): новый раздел «Строка истории (`HistoryLine`)» — три типа GO/OPEN/SUM, подсветка полосой; строку таблицы компонентов (:87) дополнить. Витрина: `ui/showcase/ShowcaseScreen.kt:295-307` — по одной строке каждого типа + `highlighted` + `nested`; тот же пример в `docs/design-system/showcase.html`.

**Тесты (`CapitalEventsTest`, новый `HistoryRowsTest`):** `rubleOperationsLeadNowhere` (INCOME/SPEND/TRANSFER в RUB → `target == null`, в USD → `Currency("USD")`); `kindOfEvent` (parts → OPEN, target → GO, ничего → SUM); `spendGroupIsSum` (2 рублёвых списания дня → Group, `RowKind.SUM`, «Списания», «2 операции»); `transferGroupIsSum`; `currencySpendGroupOpens`; `operationsPlural` (1/2/5/11/21/22).

**Критерии:** рублёвые операции и их группы без признака перехода и без отклика; валютные — ведут в валюту; суммы во всех строках, включая заголовок месяца, выровнены. device-check: да (вместе с F-46).

---

## F-47
**Цель:** «Проценты» и «Переоценка валюты» раскрываются на месте на части; часть-вклад ведёт в свой вклад, часть-валюта — в свою валюту; свёрнутая переоценка подписана по-человечески.

**Домен (`CapitalEvents.kt`, `moved()` :210-239):**
1. `DepositMath.valueOn(deposit, date): Long` (`domain/model/DepositMath.kt`, рядом с `isActive` :121) — `0`, если `date < startDate`; иначе `project(deposit, date, seriesPoints = 2).currentValue.minor` (2 точки — дёшево для длинной истории).
2. Раскладка процентов дня (:220-226): вес вклада `w = valueOn(d, cur) − valueOn(d, prev) − own_d(cur)` (own — principal, если открыт в `cur`, + пополнения в `cur`), отрицательные → 0. `CapitalEvents.splitInterest(total: Long, weights: List<Pair<Deposit, Long>>, cur: LocalDate): List<Pair<Deposit, Long>>` (internal, под тест): доля `total·w/Σw` с округлением вниз (через `BigDecimal`, без `Double`), **остаток — крупнейшему вкладу** (наибольший `valueOn(d, cur)`; при равенстве — первый по id). `Σw ≤ 0` → вся сумма крупнейшему. Нулевые доли выкидываются. Решение: оставляю утверждённое правило (детерминированно, сумма частей == итогу; «наибольший остаток» сложнее и на копейках не заметен).
3. Событие «Проценты»: долей ≥ 2 → `subtitle = RuPlural.deposits(n)`, `target = null`, `parts` = по вкладу `HistoryEvent(cur, DEPOSITS, title = d.title?.let { "«$it»" } ?: d.brand.title, subtitle = d.title?.let { d.brand.title }, amountMinor = доля, kind = INTEREST, target = Deposit(d.id), bank = d.brand)`; доля одна → без `parts`, `subtitle` = имя вклада (`d.title ?: d.brand.title`), `target = Deposit(id)`, `bank`. Заменяет `Section(DEPOSITS)` (:224) — переход всегда в конкретный вклад.
4. «Переоценка» (:229-236): `parts` из `reason.legs` — `HistoryEvent(title = Currencies.nameOf(code), subtitle = "81,92 → 80,74 ₽ · 1 200 $" (как нынешняя строка одной ноги, :88-89), amount = effectMinor, kind = REVALUATION, target = Currency(code))` + при `manualMinor != 0` часть `title = "Остаток изменили вручную"`, `subtitle` = имя валюты, если нога одна, `target = null`. Частей < 2 → без `parts`, `target` как сейчас (:233).
5. `RevaluationText.subtitle(reason)` (:81) — подпись свёрнутой строки **только цифрами, без «подешевел/подорожал»** (чистая функция под тест):
   - 1 нога → «$ 81,92 → 80,74 ₽» (`Currencies.symbolOf(code)` + `formatRate(from)` → `formatRate(to)` + « ₽»);
   - ≥ 2 ног → «$ −1,18 ₽ · € +0,22 ₽» — изменение курса за 1 ед. каждой: `символ + знак + formatRate(|to − from|) + " ₽"`, знак «+»/«−» (настоящий минус U+2212), через « · », в порядке ног;
   - `manualMinor != 0` → суффикс « · остаток изменён» (к любому из вариантов выше);
   - ног нет → «Курс или остаток изменились» (как сейчас).
   Части раскрытия (п.4) — строка на валюту: заголовок `Currencies.nameOf(code)`, подпись «81,92 → 80,74 ₽ · 1 200 $» (курс было → стало · количество), сумма — эффект в ₽, `target = Currency(code)`; ручная часть — как в п.4.

**UI:** `CapitalHistoryScreen.kt` — `Single` с `RowKind.OPEN` раскрывается как группа (состояние — тот же `groupsOpen` по `row.id`), дети — `HistoryLine(nested = true, lead = …)`: `bank != null` → `BankIcon(bank, 28.dp)`; `target is Currency` → `CurrencyBadge(code, 28.dp)` (`ui/capital/CapitalUi.kt:94`); иначе `Dot`. Тап части → прежний `onTap` (:99, «вклада больше нет» сохраняется). Маршруты уже есть (`MainActivity.kt:809-814`), не меняются.

**Тесты (`CapitalEventsTest`):** `interestSplitsByDepositMath` (2 вклада, разные суммы/ставки → доли пропорциональны весам, Σ == итог); `interestRemainderGoesToLargest` (итог 101 коп., равные веса → крупнейший получает 51); `interestZeroWeightsGoToLargest`; `depositOpenedTodayDoesNotCountPrincipal`; `oneDepositLeadsToIt` (target `Deposit(id)`, подпись — имя, без parts); `interestPartsLeadToEachDeposit` (targets, `bank`); `revaluationPartsPerCurrencyAndManual` (2 ноги + ручная → 3 части, у ручной `target == null`); `revaluationOneLegHasNoParts`; `revaluationTextOneCurrency` («$ 81,92 → 80,74 ₽») / `revaluationTextTwoCurrencies` («$ −1,18 ₽ · € +0,22 ₽», настоящий минус) / `revaluationTextWithManual` (суффикс « · остаток изменён») / `revaluationTextNoLegs`; `revaluationPartSubtitle` («81,92 → 80,74 ₽ · 1 200 $»); `valueOnBeforeStartIsZero` (`DepositMathTest` или в том же).

**Критерии:** Σ частей == сумме строки всегда; из части — в нужный вклад/валюту; удалённый вклад — шторка «Этого вклада больше нет». device-check: да.

---

## F-48 (данные): история наличных — Room v18
**Цель:** каждое ручное изменение наличных сохраняется (с этого момента; прошлое не восстанавливается).

1. **Домен:** рядом с `CashBalance` (там, где он объявлен; `grep "class CashBalance"`) — `data class CashChange(val atMs: Long, val amount: Money, val delta: Money)`; `object CashHistory { fun change(prev: CashBalance?, next: CashBalance): CashChange? }` — `null`, если сумма не изменилась; `delta = next − (prev ?: 0)`.
2. **Интерфейс:** `domain/repository/CashRepository.kt` — `fun observeChanges(): Flow<List<CashChange>>` (старые первыми); kdoc «no history» (:6) исправить.
3. **Room:** `data/local/FxEntities.kt` (после `CashEntity` :29) — `@Entity("cash_changes") CashChangeEntity(@PrimaryKey(autoGenerate = true) id: Long = 0, atMs: Long, amountMinor: Long, deltaMinor: Long)`; `FxDao.kt` (секция cash :46-58) — `insertCashChange`, `insertCashChanges(list)`, `observeCashChanges()` (`ORDER BY atMs`), `cashChanges()`, `clearCashChanges()`; мапперы в `FxMappers.kt` (:34). Отдельный DAO не заводим — наличные уже в `FxDao` (меньше правок `AppDatabase`).
4. **`AppDatabase.kt`:** entity в массив (:16), `version = 18` (:19), `MIGRATION_17_18` (над :46), регистрация в `.addMigrations(...)` (:133):
   ```sql
   CREATE TABLE IF NOT EXISTS `cash_changes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `atMs` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, `deltaMinor` INTEGER NOT NULL)
   ```
   Таблица стартует пустой (дельту прошлого не знаем — не выдумываем).
5. **Репозиторий:** `data/repository/FxRepositoryImpl.kt:33` — `CashRepositoryImpl(db: AppDatabase)`: `set()` в `db.withTransaction { prev = cash(); setCash(..); CashHistory.change(prev, balance)?.let { insertCashChange(..) } }`; `observeChanges()`. Sample: `data/repository/sample/SampleMoneyRepositories.kt:55` — `observeChanges()` из in-memory списка, `set()` дописывает тем же `CashHistory.change`.
6. **DI:** `di/AppContainer.kt:116` → `CashRepositoryImpl(db)`; `SandboxContainer` — если создаёт `CashRepositoryImpl`/sample, поправить конструктор.
7. **Копия:** `data/backup/BackupManager.kt` — экспорт массива `cashChanges` (рядом с `cash` :397), поле снимка (:449), очистка (:490 `clearCashChanges`), восстановление (:503), разбор (:650, `optJSONArray`, нет — пусто: старые копии читаются). `data/backup/AutoBackup.kt:81` `TABLES` + `"cash_changes"`.
8. **Тест (`CashHistoryTest`):** `sameAmountIsNoChange`, `firstSetIsWholeAmount`, `nextSetIsDifference`.

**Критерии:** обновление поверх v17 с данными — всё на месте, не падает (device-check: установка поверх); изменение наличных пишет строку; копия/восстановление переносят историю; старая копия без поля восстанавливается.

---

## F-48
**Цель:** за Год/Всё раскрытый месяц — короткий список групп по смыслу вкладки (вариант B), за Неделю/Месяц — по дням с свёрткой одинакового в дне.

**Домен (`domain/model/HistoryRows.kt`, `CapitalEvents.kt`):**
1. `CapitalEvents.build` (:107): FX — `kind = FX_BUY/FX_SELL`, `amountMinor = +rubMinor / −rubMinor` (изменение части «Валюта»; согласовано с `dealt` :227), `foreign = ForeignAmount(o.currency, ±o.amountMinor)`. Закрытый вклад (:161) — `amountMinor = −DepositMath.valueOn(d, d.endDate)`, `bank = d.brand`; у «Новый вклад»/«Пополнение вклада» тоже `bank`. Новый параметр **в конце** `cashChanges: List<CashChange> = emptyList()` (существующие вызовы в тестах не ломаются): каждое изменение → `HistoryEvent(day(atMs), CASH, "Наличные", "изменили вручную", delta, kind = CASH, target = Section(CASH))`; старое событие по `cash.updatedAt` (:163-165) — только если ни одно изменение не совпадает с `updatedAt` (наличные, правленные до v18).
2. Модели: `data class HistoryDay(val date: LocalDate, val rows: List<HistoryRow>)`, `data class HistoryMonth(val month: YearMonth, val count: Int, val deltaMinor: Long, val rows: List<HistoryRow>)`.
3. `HistoryRows.days(events, page: CapitalPart?): List<HistoryDay>` (новые первыми) — в дне ключ `(eventKind, part)`; ≥ 2 → `Group` (id `"d:<epochDay>:<kind>:<part>"`), 1 → `Single` (id `"s:<epochDay>:<i>"`). Порядок — как события пришли (сейчас так).
4. `HistoryRows.months(months: List<MonthEvents>, page, today): List<HistoryMonth>` — в месяце ключ `(eventKind, part)`:
   - **INTEREST** — всегда одна строка: части по вкладу суммируются за месяц (из `parts` дня, а день без parts — сам событие-вклад); вкладов ≥ 2 → `Group(kind = OPEN, children = части, subtitle = RuPlural.deposits(n))`, один → `Group(kind = GO, target = Deposit(id), subtitle = имя)`; `sources` = все дневные события.
   - **REVALUATION** — одна строка, части по валюте: сумма эффектов за месяц, подпись «82,40 → 80,74 ₽ за месяц» (курс первой ноги месяца → последней), ручные — одна часть «Остаток изменили вручную»; подпись группы — тем же `RevaluationText.subtitle` по месячным ногам (курс первой ноги месяца → последней, сумма ручных за месяц); частей < 2 → `GO` в валюту.
   - Остальные: ≥ 2 → `Group` (`groupKind` из F-50), 1 → `Single` с датой в подписи (`"<subtitle> · 18 октября"`, `RuDays.dayMonth`); дети групп тоже с датой в подписи.
   - Сортировка строк месяца — по `|totalMinor|` убыв., при равенстве — по `HistoryKind.ordinal`. id групп `"m:<YYYY-MM>:<kind>:<part>"` (уникальны между месяцами — `groupsOpen` общий).
5. `HistoryRows.groupTitle(kind, part, n, page)` — по таблице макета: INCOME «Поступления · N»; SPEND/TRANSFER/TRANSACTION — «Списания»/«Переводы»/«Операции» + подпись «N операций» (F-50); у `part == CURRENCY` при `page == null` добавить «в валюте»; DEPOSIT_OPEN «Новые вклады · N»; DEPOSIT_TOPUP «Пополнения · N» на «Вкладах», иначе «Пополнения вкладов · N»; DEPOSIT_CLOSE «Закрытые вклады · N»; FX_BUY/FX_SELL «Покупки · N»/«Продажи · N» на «Валюте», иначе «Покупки валюты · N»/«Продажи валюты · N»; CASH «Изменения · N» на «Наличных», иначе «Наличные · N»; REVALUATION «Переоценка» на «Валюте», иначе «Переоценка валюты». Подпись групп с объектами — `HistoryRows.names(children)`: до двух различных подписей/дат через запятую + « и ещё N» (у CASH — даты: «5 и 17 октября»).

**VM (`ui/history/CapitalHistoryViewModel.kt`):** `inputs` (:87-91) + `.combine(cash.observeChanges())` → `Inputs.cashChanges`; `build(..., cashChanges = i.cashChanges)` (:95). `HistoryUi` (:50): вместо сырой группировки в UI — `days: List<HistoryDay>` (неделя/месяц) и `months: List<HistoryMonth>` (год/всё, из `CapitalEvents.byMonth` :273 → `HistoryRows.months`); `events` остаётся (для `nearestDay`). Пустой список — по-прежнему «За это время капитал не менялся».

**UI (`CapitalHistoryScreen.kt` `Events` :224-287):** рисует `u.days` (подпись дня + `Panel` со строками) и `u.months` (заголовок месяца `HistoryLine` + строки месяца без подписей дней). `CapitalEvents.group` больше не зовётся. README: абзац «Экран» (:203) — «за год и всё время месяц раскрывается в группы по смыслу вкладки» + ссылка на таблицу групп.

**Тесты (`HistoryRowsTest`, `CapitalEventsTest`):** `dayFoldsAnySameKind` (2 пополнения в день → «Пополнения вкладов · 2», OPEN); `monthGroupsSortedByAbsSum`; `monthInterestOneRowPartsPerDeposit` (Σ по вкладам за месяц, `sources` = все дни); `monthInterestOneDepositGoesToIt`; `monthRevaluationPartsPerCurrency` (подпись курс первый → последний); `monthSingleHasDateInSubtitle`; `titlesDependOnPage` (Вклады/Всё/Валюта/Наличные); `monthGroupIdsUniqueAcrossMonths`; `fxEventsSignedRubAndForeign` (BUY +, SELL −, `FX_BUY/SELL`); `closedDepositHasNegativeValue`; `cashChangesBecomeEvents`; `legacyCashEventOnlyWithoutChanges`.

**Критерии:** Вклады·Год·октябрь как в макете (новые вклады / пополнения / проценты / закрытые); Неделя/Месяц — по дням; сумма строк месяца не обязана равняться дельте месяца (дельта — по точкам, как сейчас). device-check: да.

---

## F-46
**Цель:** касание графика выделяет строки, повлиявшие на точку: в герое — изменение «за день» (за неделю — за Год/Всё, с полосой на графике), лента едет к ним, строки подсвечены полосой слева.

**Домен (`domain/model/CapitalHistory.kt`):**
1. `weekly(series): List<DayValue>` — последняя точка всегда, далее шаг 7 дней назад, первая точка всегда. VM: для YEAR/ALL `series = thin(weekly(full))`, для WEEK/MONTH — `thin(full)` (как сейчас, :102). Решение: точка Года — неделя (как в макете), отрезок точки получается сам из соседей.
2. `data class DaySpan(val from: LocalDate, val to: LocalDate) { val days: Int; fun contains(d) }`; `spanAt(series, i): DaySpan` — `from = series[i−1].date + 1` (у `i == 0` — `series[0].date`), `to = series[i].date`; `stepAt(series, i): Long?` — `series[i] − series[i−1]`, у `i == 0` — `null`.
3. `HistoryRows.touches(row, span)` — `row.sources.any { span.contains(it.date) }`; `HistoryRows.firstTouching(rows, span)`.
4. `CapitalEvents.focusDay(events, span): LocalDate?` — самый новый день с событиями внутри `span`; нет — `nearestDay(events, span.to)` (день без событий — **как сейчас**, лента едет к ближайшему).
5. Тексты (`RuDays`, `domain/model/BackupRotation.kt:90-113`): `RuDays.span(from, to, today)` — один месяц «8 – 14 октября», разные — «28 сентября – 4 октября» (через `dayMonth`); `RuDays.spanWord(days)` — 1 «за день», 7 «за неделю», иначе «за » + `RuPlural.days(n)`.

**UI (`CapitalHistoryScreen.kt` `HistoryPage` :162-221):**
6. `span = selected?.let { CapitalHistory.spanAt(u.series, it) }`. Герой (:196-197): строка даты — `span.days == 1 ? RuDays.dayWeekday : RuDays.span` (`Brass2`); под ней — `stepAt`: ≠ 0 → `MoneyText(signedSum, bodyMedium SemiBold, Mint/Fg)` + `Text(spanWord, bodySmall, Muted)` (как B-77); 0/null → «без изменений » + spanWord (`Muted`).
7. `LaunchedEffect(scrollTick)` (:181-190): день — `focusDay(u.events, span)`; для `months` — открыть месяц дня, затем `bringIntoView` первой касающейся строки месяца (`firstTouching`), иначе заголовка месяца. `requesters` — ключ `String`: `"d:<date>"` для подписей дней, `row.id` для строк месяца, `"m:<YYYY-MM>"` для заголовков. Группы сами не раскрываются (меньше прыжков; подсвечена сама группа).
8. Подсветка: в `Events` передать `span`; `HistoryLine(highlighted = HistoryRows.touches(row, span))`, у раскрытых детей — по дате ребёнка. Подпись дня `Brass2`, если `span.contains(day)` (вместо `== picked`, :268/:278); плашка «Сбросить» (:236-241) — подпись `RuDays.span`/`dayMonth`.
9. `CapitalChart` (`ui/common/CapitalChart.kt:49`): параметр `range: IntRange? = null` — при `range.first < range.last` полоса от `x(first)` до `x(last)` на всю высоту, токен `Slitok.BrassTint`, под линией. История передаёт `(selected − 1)..selected`, если `span.days > 1`. Полоса и подсветка строк появляются за `Motion.NORMAL`.
10. README `CapitalChart` (:205): про `range`, «за день/неделю» в герое, подсветку строк, недельные точки Года/Всё; витрина `ShowcaseScreen.kt:304` — пример с `range`; `showcase.html` — то же.

**Тесты (`CapitalHistoryTest`, `HistoryRowsTest`, `CapitalEventsTest`):** `weeklyKeepsLastAndStepsSevenDays`; `weeklyKeepsFirst`; `spanAtFirstPointIsItsDay`; `spanAtMiddleCoversGap`; `stepAtDifferenceAndNullAtStart`; `touchesByAnySource` (месячная группа процентов касается дня из `sources`); `focusDayNewestInSpan`; `focusDayFallsBackToNearest`; `spanTextSameAndCrossMonth`; `spanWordDayWeekDays`.

**Критерии:** Месяц: отпустили на дне → лента у дня, строки дня с полосой, «+X ₽ за день»; Год: полоса недели на графике, месяц раскрыт, подсвечены группы с событиями недели; день без событий — «без изменений за день», лента едет к ближайшему дню; смена периода и «Сбросить» снимают подсветку; лента не фильтруется (F-41). device-check: да.

---

## F-49
**Цель:** на графике точки значимых действий в цвете части; у ≤ 3 крупнейших — подпись суммы; закрытие вклада — кольцом; касание рядом прилипает к точке.

**Домен (новый `domain/model/ChartMarks.kt`):**
1. `enum class MarkShape { BIG, SMALL, RING }`; `data class ChartMark(val index: Int, val part: CapitalPart, val shape: MarkShape, val amountMinor: Long, val label: String?)`.
2. `ChartMarks.of(series, events, page: CapitalPart?, accountsAvgMinor: Long, maxLabels = 3): List<ChartMark>` — событие относится к точке, чей `spanAt` его содержит (`pointOf(series, date)`). Только действия (INTEREST, REVALUATION, OWN_TRANSFER — никогда):
   - INCOME/SPEND/TRANSFER/TRANSACTION части ACCOUNTS — если `|amount| > accountsAvgMinor / 10` (порог утверждён; `avg ≤ 0` — нет отметок) → BIG на «Счетах» и «Всё»; валютные операции — без отметок.
   - DEPOSIT_OPEN — BIG на «Вкладах», SMALL на «Всё»; DEPOSIT_TOPUP — SMALL; DEPOSIT_CLOSE — RING (подпись-кандидат только на «Вкладах»).
   - FX_BUY/SELL — BIG на «Валюте» (подпись из `foreign`: «+1 000 $» / «−500 €»), SMALL на «Всё».
   - CASH с суммой — BIG на «Наличных»; на «Всё» — по тому же порогу, что счета.
   - Несколько в одной точке → одна отметка: приоритет BIG > RING > SMALL, затем больший `|amount|`.
   - Подписи: кандидаты BIG (и RING на «Вкладах») по `|amount|` убыв., **не больше 3** → `label` (`MoneyCompact.short(amount, signed = true)` или валютная); остальные `label = null`. Раскладку по пикселям домен не делает.
3. `ChartMarks.snap(raw: Int, marks: List<Int>, tolerance: Int): Int` — ближайшая отметка в пределах `tolerance` индексов (при равенстве — меньший индекс), иначе `raw`.
4. `CapitalHistory.average(series): Long` (`Σ/size`, Long).

**VM:** `HistoryUi.marks = ChartMarks.of(series, shown, p, CapitalHistory.average(CapitalHistory.series(i.days, period, day, ACCOUNTS)))` (:97-106).

**UI (`ui/common/CapitalChart.kt`):**
5. Параметры `marks: List<ChartMark> = emptyList()`, `markColor: (CapitalPart) -> Color = { color }` (история передаёт `{ it.color() }`).
6. Рисование после линии, в том же `clipRect(grow)` (проявляются с ростом): BIG — круг Ø10 dp в цвете части с обводкой `Slitok.Bg` 2 dp; SMALL — Ø6 dp; RING — обводка 2 dp цветом части, внутри `Bg`. Выбранная точка (:121-126) — поверх.
7. Подписи — **цвет `Slitok.Muted`, без капсулы, без Mint**, `labelSmall` через `rememberTextMeasurer`, тёмная обводка текста: сначала `drawText(..., drawStyle = Stroke(3.dp), color = Slitok.Bg)`, затем заливкой. **Раскладка в UI (пиксели):** сначала рисуются все точки, потом подписи по убыванию `|amount|`. Кандидаты позиции по порядку: над точкой, под, слева, справа, слева-сверху, справа-снизу (отступ от точки ~6 dp). Позиция занята, если прямоугольник подписи: пересекается с линией графика (сэмплы линии через ~2 dp по x), с любой точкой (круг радиуса ~7 dp), с уже поставленной подписью, или выходит за область Canvas. Первая свободная — ставим; свободной нет — подписи нет, остаётся точка. Проверку пересечений вынести в приватную функцию в `CapitalChart.kt` (геометрия `Rect`, без аллокаций в цикле рисования — считать в `remember(points, marks, size)`). `LocalAmountsHidden.current` → подписей нет, точки есть.
8. Прилипание: в `touch()` (:69) индекс → `ChartMarks.snap(i, marks.map { it.index }, tolerance)`, `tolerance = ceil(12.dp.toPx() / шаг_точки)`. Drag по-прежнему потребляется графиком (B-75 не ломается).
9. README `CapitalChart` + витрина (`ShowcaseScreen.kt:304`: пример с BIG/SMALL/RING и подписями) + `showcase.html`.

**Тесты (`ChartMarksTest`, `CapitalHistoryTest`):** `onlyActionsAreMarked`; `accountsThresholdTenPercentOfAverage` (11 % — да, 9 % — нет, `avg ≤ 0` — нет); `depositsPageShapes` (открытие BIG, пополнение SMALL, закрытие RING); `totalPageDepositAndFxSmall`; `fxLabelInDealCurrency`; `atMostThreeLabelsLargestFirst`; `onePerPointPrefersBig`; `eventInWeeklySpanGoesToItsPoint`; `snapWithinTolerance` / `snapOutsideKeepsRaw` / `snapTiePrefersEarlier`; `averageOfSeries`.

**Критерии:** как в макете F-49 A по вкладкам; подписи не наезжают на линию, точки и друг друга и не выходят за график; касание в 12 dp от точки выбирает её день (подсветка F-46); скрытые суммы — без подписей; «Убрать анимации» — сразу. device-check: да (Вклады·Месяц, Всё·Месяц, Вклады·Год, малый экран).

---

### Инварианты группы
- Деньги — `Long` копейки/`Money`; раскладка процентов без `Double` (веса из `DepositMath` в копейках, деление `BigDecimal`), Σ частей == итог.
- Вся группировка, тип строки, отрезок точки, подсветка, отметки и прилипание — чистые функции домена под JVM-тестами; UI только рисует (и раскладывает подписи по пикселям).
- Сеть, разрешения, подпись не затрагиваются; миграция v17 → v18 без потери данных, копия совместима в обе стороны (старая читается).
- Цвета/шрифты только токенами (`BrassTint`, `Brass2`, `Muted`, `Bg`, `labelSmall`); `DesignSystemGuardTest` зелёный; README/витрина/`showcase.html` обновлены в коммитах, меняющих `CapitalChart`/`HistoryLine`.

## Решение по виду строки (Максим, после плана)
Вариант 1 «Значок в подписи» (макет `docs/mockups/cycle-12/history-f46-f50.html`, вкладка «Строка · варианты», блок `rowv`):
- справа в строке только сумма, ровно по правому краю — никаких стрелок и пустых слотов справа;
- признак — маленький шеврон (~14 dp, Muted) в конце серой подписи: GO — «›», OPEN — «⌄»/«⌃» (свёрнуто/раскрыто), SUM — без значка; при длинной подписи многоточие режет текст, а шеврон остаётся;
- части раскрытой группы — с отступом, на тонкой вертикальной направляющей слева (цвет Line);
- подсветка F-46 — только полоса Brass2 слева, без заливки.
