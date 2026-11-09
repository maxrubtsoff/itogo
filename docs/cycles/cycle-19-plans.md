# Цикл 19 — планы (architect)

Пути: `BA/` = `app/src/main/java/ru/maxrubtsoff/bankagg/`, `TA/` = `app/src/test/java/ru/maxrubtsoff/bankagg/`. Карта: `docs/cycles/cycle-19-map.md`.

## Группировка для разработки
- **Группа 1 (отдельно, параллельно):** B-128 — только `domain/parser/NotificationParser.kt` + тесты парсера/repair.
- **Группа 2 (последовательно, общие файлы):** F-59 → F-58 → U-77. Общие: `ui/history/CapitalHistoryViewModel.kt` (все три), `domain/model/CapitalEvents.kt` (F-59, U-77), `ui/history/CapitalHistoryScreen.kt` (F-58, U-77), `ui/common/ViewModelFactory.kt` + `di/AppContainer.kt` (F-59/F-58). Доменные части U-77 (`ChartWindow`) и F-58 (`Spending`) можно писать параллельно, VM/экран — по очереди.
- Миграции Room в цикле **нет** (версия остаётся 20).

---

## B-128
**Размер:** средний. **Цель:** промо «Купите любой ОПИФ и мы вернём до 1500 ₽ с учётом суммы пополнения 👉» (с любым заголовком) → `parse` = `null`; ранее записанная ложная операция удаляется repair'ом при обновлении.

**Причина (по коду).** С заголовком «Альфа-Банк» текст даёт `null`: «пополнения» ≠ IN-маркер «пополнение» (`NotificationParser.kt:142`), «вернём» нет в маркерах. Значит направление IN дал **заголовок** push'а (`blob = title + text`, `:205`; в шторке операции заголовок не виден, только текст). Кандидаты из кода, дающие IN при таком тексте: в заголовке «кешбэк/кэшбэк», «возврат», «пополнение», «зачисление», «начислен» (`IN_MARKS :141-146`) или «… от Альфа-Банка» (`IN_FROM :149`, «от» + заглавная). Фикс делаем независимым от заголовка: промо распознаётся по тексту, `isPromo` проверяется до направления (`:210`). Coder: если есть доступ к журналу на устройстве — сверить заголовок и добавить его дословно в негатив; не блокирует.

**Шаги (домен, `BA/domain/parser/NotificationParser.kt`):**
1. `PROMO_MARKS :162-166` — добавить: «купите», «вернём до», «вернем до», «мы вернём», «мы вернем», «с учётом суммы», «с учетом суммы».
2. `PROMO_PATTERNS :168-172` — добавить:
   - `верн[её]м[^.!?]{0,30}\d` (обещание вернуть сумму);
   - «до N ₽» без остатка: новая приватная проверка `isUpToAmountPromo(low)` = есть `(?<![а-яё])до[  ]?<NUM>[  ]?(₽|руб|р\.)` **и** нет ни одного `BALANCE_MARKERS` (`:129`) **и** нет маски счёта/карты в исходном blob (`MASK.containsMatchIn(blob)` — вызывать до замены). Обоснование: «до 1500 ₽» в операциях не встречается без остатка/счёта; с остатком — оставляем как есть (консервативно к позитивам).
   - эмодзи без остатка: новая `hasPictograph(blob)` (кодовые точки `0x1F300..0x1FAFF`, `0x2600..0x27BF`, через `codePoints()`) **и** нет `BALANCE_MARKERS` → промо. Чистый Kotlin, без `android.*`.
   `isPromo` (`:293`) принимает и `blob` (для маски/эмодзи): сигнатура `isPromo(low, blob)`; обновить оба вызова `:210`, `:284`.
3. `VERSION :55` → **8**, строка в истории версий `:48-54`: «8 — промо „купите / вернём до / до N ₽ без остатка / эмодзи без остатка“ не операция (B-128)».

**Repair (проверено по коду, правок не нужно):** `RepairFromJournal.kt:37` пропускает только `parseBare != null` (для B-128 он `null`); при `op == null` `contentHash` меняется (`RecordNotification.contentHash` включает сумму, `:140-148`) → `Change` → `deleteTransaction(oldId)` `:76`, новая не добавляется `:79`, `updateParse(..., parsed=false)` `:94`, баланс счёта пересчитывается от базы (`:97-131`). Запуск — по `VERSION` (`AppContainer.kt:157-161`). Достаточно bump'а.

**Тесты (TDD, сначала красные):**
- `TA/domain/NotificationParserTableTest.kt` `negatives :58-67` — добавить:
  - `"alfa opif promo"` — («Альфа-Банк», точный текст B-128);
  - `"alfa opif promo cashback title"` — («Кешбэк за ОПИФ», тот же текст);
  - `"alfa opif promo refund title"` — («Возврат до 1500 ₽», тот же текст);
  - `"alfa opif promo from title"` — («Подарок от Альфа-Банка», тот же текст);
  - `"up to amount no balance"` — («Т-Банк», «Вернём до 3 000 ₽ за первую покупку»);
  - `"emoji promo"` — («Т-Банк», «Пополните счёт на 1 000 ₽ и получите подарок 🎁»).
  Таблица позитивов `ops` (`:20-55`) должна остаться зелёной — это доказательство, что консервативность не сломана.
- `TA/domain/NotificationParserTest.kt`: `promoUpToAmountWithBalanceIsStillOperation` — «Зачисление 1 500 ₽. Остаток до операции … Баланс 10 000 ₽»-подобный реальный push с «до» и остатком остаётся операцией (защита от перегиба правила «до N ₽»).
- `TA/domain/RepairFromJournalTest.kt`: `promoOperationIsRemovedOnRepair` — в журнале событие B-128 (заголовок «Кешбэк за ОПИФ»), в ledger лежит транзакция `+150000` с `id = dedupHash`, contentHash старый (с суммой); после `invoke()` транзакции нет, `updateParse(parsed=false)` вызван, счёт без других операций и без `checkedAt` удалён (или баланс сброшен — по существующей ветке `RepairBase.None`). Использовать фейки, уже имеющиеся в этом тесте.

**Критерии:** `./gradlew testDebugUnitTest assembleDebug` зелёные; `NotificationParser.VERSION == 8`; все прежние позитивы таблицы U-76 проходят; на устройстве после обновления операция «Пополнение +1 500 ₽ 2 ноября» исчезла из истории, баланс Альфы вернулся.

---

## F-59
**Размер:** средний. **Цель:** переводы между своими счетами распознаются одной чистой функцией домена и не попадают ни в список истории, ни в траты (F-58).

**Решение: вычисление на лету, без флага в БД (миграции 20→21 нет).** Обоснование: пары зависят от обеих ног — добавление/удаление/правка одной ноги должна сразу менять результат; на лету это реактивно и применяется к уже накопленным данным без бэкфилла; `kind` не входит в `contentHash`, так что сохранённый флаг repair не перепишет. Текстовые признаки берём из журнала (`CapturedEvent.dedupHash == Transaction.id`). Ограничение (принято): после очистки журнала (`JournalRetention`) одиночная нога с «перевод себе» без пары снова считается обычным переводом.

**Шаги:**
1. **Домен, новый файл `BA/domain/model/OwnTransfers.kt`** — `object OwnTransfers`:
   - `fun isOwnText(title: String?, text: String): Boolean` — маркеры (lowercase): «перевод себе», «себе на», «между своими», «между счетами», «между своими счетами», «на свой счёт/счет», «на свою карту», «с вашего счёта в». Без «себе» одиночным словом (слишком широко).
   - `fun ids(transactions, accounts, textMarked: Set<String> = emptySet(), zone): Set<String>` — правила:
     - всё из `textMarked`, что есть в `transactions` → своё (одиночная нога допускается);
     - пары 1:1: одна валюта, суммы равны по модулю и противоположны, разные `accountId`, оба счёта в `accounts`; время: тот же день **или** `|Δt| ≤ PAIR_WINDOW_MS = 2 ч` (полночь); исходящая нога `kind ∈ {TRANSFER, UNKNOWN}` (покупка EXPENSE никогда не пара), входящая `kind ∈ {TRANSFER, INCOME, UNKNOWN}` (Альфа пишет входящий перевод как «Пополнение»); исходящие перебираются по `postedAt`, пара — ближайшая по времени свободная.
     - Обоснование расширения (INCOME, все валюты): текущее правило (`CapitalEvents.kt:265-281`, только TRANSFER+RUB) пропускает «Перевод → Пополнение» между Т-Банком и Альфой — ровно жалоба F-59.
2. **`BA/domain/model/CapitalEvents.kt`:** `build` (`:168-179`) — новый параметр `ownIds: Set<String>? = null`; `:183` → `val own = ownIds ?: OwnTransfers.ids(transactions, accounts, emptySet(), zone)`; удалить приватный `ownTransferIds :261-281`. `HistoryKind.OWN_TRANSFER` оставить как есть (в список не выводится).
3. **Use-case `BA/domain/usecase/OwnTransferMarks.kt`:** `class OwnTransferMarks(events: EventRepository)`, `fun observe(): Flow<Set<String>> = events.observeRecent(Int.MAX_VALUE).map { list -> list.filter { it.parsed && OwnTransfers.isOwnText(it.title, it.text) }.map { it.dedupHash }.toSet() }.distinctUntilChanged()`. Новый метод репозитория не нужен (`EventRepository.kt:15`).
4. **DI `BA/di/AppContainer.kt`** после `:203`: `val ownTransferMarks by lazy { OwnTransferMarks(eventRepository) }`.
5. **ViewModel `BA/ui/history/CapitalHistoryViewModel.kt`:** конструктор `:85-96` + `ownMarks: OwnTransferMarks`; в `inputs :108-113` `.combine(ownMarks.observe()) { i, m -> i.copy(ownMarks = m) }` (поле в `Inputs`); в `state :117` посчитать `val own = OwnTransfers.ids(i.tx, i.accounts, i.ownMarks, zone)` и передать `ownIds = own` в `CapitalEvents.build`. `own` переиспользуется F-58.
6. **Factory `BA/ui/common/ViewModelFactory.kt:42-46`:** добавить `container.ownTransferMarks`.

**Тесты (`TA/domain/OwnTransfersTest.kt`, новый):**
- `pairAcrossBanksTransferToIncome` — Т-Банк −5000 TRANSFER, Альфа +5000 INCOME, 10 мин → обе id.
- `purchaseNeverPairs` — −5000 EXPENSE + +5000 INCOME → пусто.
- `differentSumsNoPair`, `sameAccountNoPair`, `unknownAccountNoPair`.
- `pairAcrossMidnightWithinWindow` — 23:50 и 00:20 → пара; `farApartNextDayNoPair` — 23:00 и 03:30 следующего дня → нет.
- `oneToOne` — один −5000 и два +5000 → в паре ровно одна входящая (ближайшая).
- `textMarkedSingleLeg` — одиночная нога в `textMarked` → её id.
- `isOwnTextMarkers` — «Перевод себе в Альфа-Банк» → true; «Перевод Ивану И.» → false; «между своими счетами» → true.
- `TA/domain/CapitalEventsTest.kt` — существующие тесты на скрытие пар остаются зелёными; добавить `ownPairTransferIncomeHidden` (TRANSFER−/INCOME+ скрыты из `build`).

**Критерии:** тесты зелёные; инвариант — пара всегда 1:1, EXPENSE никогда не «свой»; на устройстве перевод Т-Банк → Альфа (себе) не виден в списке «Счета».

---

## F-58 (домен/данные; вид — по макету)
**Размер:** тяжёлый (UI по макету). **Цель:** на вкладке «Счета» истории видно, сколько потрачено за выбранный месяц, с разбивкой по банкам и получателям; переключатель переводов меняет только эти цифры.

**Решения:**
- Трата = операция в RUB на не исключённом (`!Account.excluded`) счёте, сумма < 0, не своя (F-59 — своя никогда не трата). Обоснование RUB: вкладка «Счета» = рублёвые счета (`CapitalEvents.kt:191`); валютные операции живут в «Валюте».
- `includeTransfers` (по умолчанию `false`): `false` — исходящие `kind == TRANSFER` (переводы людям) **не** в итоге; `true` — включены. Своё (F-59) не включается никогда. Модель отдаёт обе суммы, так что смена трактовки — правка одной строки (см. вопрос оркестратору).
- Возвраты (INCOME) траты не уменьшают — проще и предсказуемо.
- Состояние переключателя и месяца — в VM, не сохраняется (транзитное).
- Use-case не нужен для подсчёта (чистая функция на данных, которые VM уже держит, как `CapitalEvents.build`); оркестрация источников — `OwnTransferMarks` из F-59.

**Шаги:**
1. **Домен, новый `BA/domain/model/Spending.kt`:**
   - `data class BankSpend(val brand: BankBrand, val minor: Long, val count: Int)`;
   - `data class PartySpend(val name: String?, val minor: Long, val count: Int)` (`name == null` — «без получателя»);
   - `data class MonthSpending(val month: YearMonth, val totalMinor: Long, val purchasesMinor: Long, val transfersMinor: Long, val ownMinor: Long, val count: Int, val byBank: List<BankSpend>, val byParty: List<PartySpend>, val includeTransfers: Boolean)` — все суммы положительные копейки (`Long`), сортировка по убыванию;
   - `object Spending`:
     - `fun month(transactions, accounts, ownIds: Set<String>, month: YearMonth, includeTransfers: Boolean, zone): MonthSpending`; `byParty` — топ `TOP_PARTIES = 5` + хвост, сложенный в `PartySpend("Остальное", …)`; ключ контрагента — `counterparty` (`Account.kt:26-36`), иначе `null`;
     - `fun months(transactions, accounts, ownIds, zone): List<YearMonth>` — месяцы, где есть хоть одна трата, новые первыми (+ текущий месяц всегда);
     - `fun shares(byBank): List<Int>` — доли в промилле, сумма ровно 1000 (метод наибольших остатков) — для диаграммы без `Float` в UI-логике.
2. **ViewModel `CapitalHistoryViewModel.kt`:** `_spendMonth = MutableStateFlow<YearMonth?>(null)` (null — текущий), `_includeTransfers = MutableStateFlow(false)`; `fun setSpendMonth(m: YearMonth)`, `fun setIncludeTransfers(on: Boolean)`. Combine в `state :115` (лимит 5 аргументов — свести `_spendMonth`+`_includeTransfers` в один `combine` → `SpendChoice`). В `HistoryUi :65-81` поле `spending: MonthSpending?` и `spendMonths: List<YearMonth>` — заполняются только для `p == CapitalPart.ACCOUNTS`, иначе `null`/пусто. Не влияет на `series`, `change`, `events`, `marks`.
3. **ui/common (новые компоненты дизайн-системы — по макету `docs/mockups/cycle-19/F-58/`):** диаграмма трат по банкам (компонент `SpendByBank`/имя по макету: полоса или кольцо долей из `shares`, иконки `BankIcon`, суммы через `MoneyText`), строка «банк/получатель — сумма — доля» (если макет не переиспользует `HistoryLine`), выбор месяца (переиспользовать `MonthChip` → если нужен в `ui/common`, перенос из `ui/cashback/CashbackControls.kt:41`), переключатель — `SwitchRow` (`Controls.kt:74`). Каждый новый компонент: `ui/common` + раздел `docs/design-system/README.md` + `showcase.html` + `ui/showcase/ShowcaseScreen.kt`.
4. **Screen `BA/ui/history/CapitalHistoryScreen.kt`:** **заглушка — по утверждённому макету `docs/mockups/cycle-19/F-58/`** (блок трат на странице `ACCOUNTS` в `HistoryPage :172-177`). Маршрут не меняется.

**Тесты (`TA/domain/SpendingTest.kt`, новый):**
- `totalExcludesOwnTransfers` — своя пара не входит ни при `includeTransfers=false`, ни при `true`.
- `transfersToPeopleFollowSwitch` — TRANSFER −3000 Ивану: `false` → нет в `totalMinor`, есть в `transfersMinor`; `true` → в итоге.
- `incomeAndRefundsIgnored`, `foreignCurrencyIgnored`, `excludedAccountIgnored`.
- `monthBoundaryByZone` — операция 31-го 23:30 MSK попадает в свой месяц при `zone = Europe/Moscow`.
- `byBankSortedDesc`, `byPartyTopFiveAndRest`.
- `sharesSumTo1000` — три банка 1/3 каждый → сумма 1000, никаких `Double` в результате.
- `monthsNewestFirstIncludesCurrent`.

**Критерии:** тесты зелёные; инварианты — суммы только `Long`-копейки/`Money`, переключатель не меняет `series/change/events` (проверить: VM-поля графика строятся без `includeTransfers`); `DesignSystemGuardTest` зелёный; на устройстве — итог месяца на «Счета» совпадает с суммой списаний в списке за месяц без переводов себе.

---

## U-77
**Размер:** средний. **Цель:** на графике истории щипок масштабирует, двумя пальцами — прокрутка влево/вправо по всей истории периода; тап и скраб одним пальцем работают как раньше.

**Решения:**
- Жесты: **два пальца** — масштаб+сдвиг; **один палец** — как сейчас (тап/скраб, `CapitalChart.kt:136-151`). Обоснование: горизонтальный drag одним пальцем уже занят скрабом, а свайп разделов — пейджером.
- Окно общее для всех страниц экрана (у всех частей одни даты); сброс — при выборе периода (`setPeriod`, в т.ч. того же).
- Окно действует и на сумму изменения, и на список событий: «выбрать диапазон» = смотреть его целиком. Траты F-58 не зависят от окна (у них свой месяц).
- Без инерции (fling) — меньше анимаций, честно при «Убрать анимации».

**Шаги:**
1. **Домен, новый `BA/domain/model/ChartWindow.kt`** (`object ChartWindow`, окно — существующий `DaySpan`, `CapitalHistory.kt:25`):
   - `const val MIN_DAYS = 7`, `const val WEEKLY_AFTER_DAYS = 92`;
   - `fun bounds(full: List<DayValue>): DaySpan?`;
   - `fun zoom(window: DaySpan, bounds: DaySpan, factor: Double, focus: Double): DaySpan` — новая длина `round(days / factor)` в `[MIN_DAYS, bounds.days]`, день под `focus` (0..1) остаётся на месте, затем сдвиг внутрь `bounds`;
   - `fun pan(window, bounds, shiftFraction: Double): DaySpan` — сдвиг на `round(shiftFraction * days)` дней, длина неизменна, зажим в `bounds`;
   - `fun isWhole(window, bounds): Boolean`;
   - `fun visible(full: List<DayValue>, window: DaySpan?): List<DayValue>` — срез;
   - `fun drawn(visible: List<DayValue>): List<DayValue>` = `CapitalHistory.thin(if (visible.size > WEEKLY_AFTER_DAYS) CapitalHistory.weekly(visible) else visible)` — ≤ `MAX_POINTS` (`CapitalHistory.kt:37`) на любом окне; при зуме точки плотнеют (суточные внутри окна).
2. **`CapitalEvents.within` (`CapitalEvents.kt:455`):** добавить параметр `to: LocalDate? = null` (верхняя граница, включительно); старые вызовы не меняются.
3. **ViewModel `CapitalHistoryViewModel.kt`:** `_window = MutableStateFlow<DaySpan?>(null)`; `setPeriod :101` сбрасывает окно в `null`; `fun zoom(factor: Double, focus: Double)`, `fun pan(shiftFraction: Double)` — считают через `ChartWindow` от `bounds` текущей серии (хранить последние `bounds` в state); `fun resetWindow()`. В `state :119-131`: `visible = ChartWindow.visible(full, window)`; `drawn = ChartWindow.drawn(visible)` (заменяет `:122-123`, где `long`-ветка); `change = CapitalHistory.change(visible)`; `shown = CapitalEvents.within(all, window?.from ?: startOf(...), p, window?.to)`; `days/months` — от прежнего `long`-признака периода (вид списка не меняется). В `HistoryUi` поле `window: DaySpan?` (null — целиком), чтобы экран знал, что масштаб активен. `_window` сводить в общий `combine` с `_period` (лимит 5).
4. **`BA/ui/common/CapitalChart.kt`:**
   - новые параметры `onZoom: ((factor: Float, focusFraction: Float) -> Unit)? = null`, `onPan: ((shiftFraction: Float) -> Unit)? = null` (null — жесты выключены, остальные вызовы графика не меняются);
   - новый `pointerInput` на `awaitEachGesture`: при втором пальце — считать `calculateZoom()`/`calculatePan()`/центроид, звать `onZoom(zoom, centroid.x / width)` и `onPan(-pan.x / width)`, `consume()` изменения (не отдавать пейджеру/скрабу), сброс `lastTouched` без `onRelease`; один палец не трогать (существующие tap/drag-детекторы `:136-151` остаются);
   - анимация роста `:99-102`: ключ `LaunchedEffect` сменить с `points` на новый параметр `growKey: Any? = points` — экран передаёт `period to part`, чтобы при каждом шаге зума линия не «вырастала» заново; `Motion.tween` уже учитывает «Убрать анимации»;
   - KDoc графика — дописать про жесты двумя пальцами.
5. **Screen `CapitalHistoryScreen.kt`:** в `Hero` (`:300`) передать `onZoom = vm::zoom`, `onPan = vm::pan`, `growKey`; при смене `window` сбрасывать выбранную точку (индекс скраба становится недействителен). Подпись диапазона/кнопка «весь период» — по макету designer, если будет; без макета — сброс по тапу на чип периода (`PeriodChips :308` уже зовёт `setPeriod`).
6. **Дизайн-система:** обновить раздел `CapitalChart` в `docs/design-system/README.md` (жесты двумя пальцами), витрина — параметры по умолчанию null, правка не нужна.

**Тесты (`TA/domain/ChartWindowTest.kt`, новый):**
- `zoomInKeepsFocusDay` — окно 365 дн, factor 2, focus 0.5 → ~183 дн, центральный день тот же.
- `zoomClampsToMinDays`, `zoomOutClampsToBounds`.
- `panClampsAtEdges` — сдвиг за левую/правую границу упирается, длина не меняется.
- `drawnNeverExceedsMaxPoints` — серия 2000 дней, окно целиком → ≤ 120; окно 60 дн → суточные точки (size 60).
- `drawnKeepsFirstAndLast` — первая и последняя дата окна присутствуют.
- `visibleNullWindowIsWhole`.
- `TA/domain/CapitalEventsTest.kt`: `withinUpperBound` — событие после `to` не попадает.

**Критерии:** тесты зелёные; инварианты — ≤ `MAX_POINTS` точек на любом окне, окно всегда внутри `bounds` и ≥ 7 дней (или весь период, если он короче); на устройстве (`device-check`): щипок на «Всё» приближает, двумя пальцами листает, тап/скраб одним пальцем выбирают точку, свайп разделов одним пальцем вне графика работает, щипок не листает пейджер; с «Убрать анимации» зум без анимации роста линии.

---

## F-58 — макет

**Артефакт (Design-холст):** https://claude.ai/artifact/ANCtNiUuwGdNz4r1qEW7ou · исходники `docs/mockups/cycle-19/F-58/project/`. Ждёт согласования пользователем.

**Варианты:** **В (рекомендую)** — карточка «Траты» во вкладке, месяц выбирается касанием столбика (6 последних месяцев); **А** — та же карточка, месяц плашкой «Ноябрь ▾» со шторкой (`SheetList`), сравнение строкой текста; **Б** — во вкладке одна строка «18 505,50 ₽ потрачено с 1 ноября ›», отдельный экран «Траты» с кольцом и плашками банков.
Почему В: один экран, ноль шторок, месяц и динамика видны сразу, переход к операциям банка — на месте.

**Вид (В), сверху вниз на странице «Счета»:** герой с графиком как сейчас (U-67, сжимается) → карточка `Panel` «Траты» → операции по дням.
Карточка: слева «Потрачено с 1 ноября» / «Потрачено в октябре» (`bodySmall`, `Muted`) и итог `Amounts.Large`; справа «Пришло» и `+95 000 ₽` (`Amounts.Small`, `Mint`). Ниже 6 столбиков месяцев (выбранный `Brass`, подпись `Brass2`; прочие `Fg` 14 %, подпись `Muted`; высота ∝ сумме, макс. 60 dp; касание — выбрать месяц). Ниже полоса долей 8 dp (цвета банков, `rememberBankAccent`), строки банков = `SummaryRow`/`SummaryTile` (логотип 40, название + сумма, «14 операций»; без процентов, по правилу SummaryRow). Банков > 5 — четыре + «Ещё 3 банка» (`RowKind.OPEN`). Внизу `SwitchRow` «Переводы себе», подпись «2 перевода · 10 000 ₽».
Включённый переключатель меняет **только** карточку: итог, высоты столбиков, сегмент `Faint` в полосе и строку «Переводы себе» (плитка с двумя стрелками). Герой, график, список — без изменений (артборды 1 и 2 рядом).
Касание строки банка: список фильтруется по банку (на месте, без нового экрана), герой сжимается, вверху списка — логотип 32, «Т-Банк», «14 операций · 8 420 ₽ трат» и плашка «Сбросить» (как сброс дня на графике). «Назад» тоже снимает фильтр.
Список: свои переводы — одна строка «Между своими», подпись «Альфа-Банк → Т-Банк ⌄», сумма без знака `Muted`, точка `Faint`, раскрывается на две ноги (`RowKind.OPEN`, `nested`). Предложение рядом: заголовок строки — получатель («Додо Пицца»), подпись — банк (сейчас наоборот: «Списание / Додо Пицца»).
Состояния: нет трат за месяц — «Трат пока нет» вместо суммы, столбики остаются, полосы и строк нет; один банк — без полосы, одна строка; много банков — свёртка «Ещё N банков»; ни одной траты за историю — карточки нет. Смена месяца: подсветка столбика сразу, цифры/строки — наложением (уход `Motion.FAST`, приход `Motion.NORMAL`), «Убрать анимации» — мгновенно.

**Из чего собрано:** `Panel`, `MoneyText`/`AmountText` (`Amounts.Large/Small`, сумма строки 14 sp как в `SummaryRow`), `SummaryRow`/`SummaryTile`, `BankIcon`, `SwitchRow`, `HistoryLine` (OPEN/`nested`), `ChipButton`-плашка «Сбросить», `HeroCollapse`, `Motion`; токены `Slitok.Brass/Brass2/Mint/Muted/Faint/Fg/Line/PanelTop→PanelBottom`.

**Новое в `ui/common` (по согласовании):**
- `ShareBar(parts: List<Pair<Color, Long>>, height = 8.dp, gap = 2.dp)` — сегментная полоса долей; вынести из двух private `CompositionBar` (`ui/overview/OverviewScreen.kt:438`, `ui/common/BankSheet.kt:93`) и переиспользовать там же.
- `MonthBars(months: List<MonthBar(label, minor)>, selected: Int, onPick: (Int) -> Unit)` — столбики месяцев; высота 86 dp с подписью, касание по колонке целиком (≥ 44 dp), `semantics`: «Траты в октябре».
- `SpendCard(spending: MonthSpending, months, selected, onPickMonth, includeOwn, onIncludeOwn, onBank)` — карточка целиком (композиция из вышеперечисленного), чтобы экран оставался тонким.
Каждый — раздел в `docs/design-system/README.md`, пример в `showcase.html` и в витрине.

**Для coder (строки):** «Потрачено с 1 ноября», «Потрачено в октябре», «Пришло», «Трат пока нет», «N операция/операции/операций», «Ещё N банка/банков», «Переводы себе», «N перевод/перевода/переводов · X ₽», «Между своими», «Сбросить», «N операций · X ₽ трат».

**Расхождение с планом architect (решить оркестратору/пользователю):** в задаче переключатель — «переводы **себе**» (F-58/F-59), у architect `includeTransfers` — переводы **людям**, а свои не считаются никогда. Макет нарисован по формулировке задачи. Если правильно у architect — меняется только подпись строки («Переводы людям») и сегмент. Ещё: architect держит переключатель в VM (не сохраняется); предлагаю сохранять (`SettingsStore`) — человек выбирает трактовку один раз. Разбивку по получателям (`byParty`) макет не показывает: в карточке хватает банков; получатели — следующим шагом, если попросят.

**Проверка:** `DesignSystemGuardTest` (нет hex/`Card`/ручного шрифта на экране; цвета банков — через `rememberBankAccent`), `MotionGuardTest`. Заснять: карточку в ноябре и октябре, переключатель вкл/выкл (герой и список не меняются), касание банка → фильтр и «Сбросить», свёрнутый перевод между своими и раскрытый, крупный шрифт системы (строки банков не переносятся внутри слова), «Убрать анимации».

---

## U-77 — макет

**Артефакт (Design-холст):** https://claude.ai/artifact/HnVXD6SPAR6p5DACEExSSE · исходники `docs/mockups/cycle-19/U-77/project/`. Ждёт согласования.

**Варианты:** **А (рекомендую)** — окно на графике + мини-карта всей истории; **Б** — протяжка одним пальцем как в картах, скраб — удержанием, без мини-карты.
Почему А: скраб одним пальцем остаётся как есть, мини-карта всегда показывает, где окно во всей истории, и даёт двигать окно одним пальцем (щипок одной рукой неудобен). Совпадает с жестами плана architect (два пальца — масштаб+сдвиг), добавляет мини-карту.

**Вид (А):** под осью графика — мини-карта: полоса 28 dp (зона касания 44 dp), фон `Inset`, радиус 8, линия всей истории `Faint` 1,5 dp; окно — рамка `BrassEdge` 1,5 dp, заливка `BrassTint`, радиус 6, ручки по краям 3×12 `Brass2`. Под суммой вместо «с 10 октября» — даты окна «12 марта – 30 мая» (`Muted`); сумма — на последний день окна, изменение — от первого до последнего дня окна. Плашки периода, пока окно своё, не выбраны; касание любой — сброс окна на этот период (в т. ч. «Всё»). Мини-карта видна при любом периоде (на «Неделе» — узкое окно у правого края). Герой сжат (U-67) — мини-карта и плашки складываются, окно сохраняется.
Жесты: один палец по графику — скраб (без изменений); два пальца — щипок и сдвиг; один палец по окну мини-карты — сдвиг, за ручку — растянуть/сжать; касание мини-карты вне окна — перенести окно туда. Свайп разделов с графика и мини-карты не срабатывает. Окно ≥ 7 дней; ≤ 92 дней — точки по дням, шире — по неделям.
«Убрать анимации»: окно идёт за пальцами (это сам жест), без инерции; сброс на период — мгновенно, иначе `Motion.NORMAL`, `Calm`. Рост линии при каждом шаге зума не повторяется (`growKey` из плана architect).

**Из чего собрано:** `CapitalChart`, `PeriodChips(compact)`, `HeroCollapse`, `MoneyText`, `Motion`; токены `Slitok.Inset/Faint/BrassEdge/BrassTint/Brass2/Muted`.

**Новое в `ui/common` (по согласовании):** `ChartOverview(points: List<Long>, window: ClosedRange<Int>?, onPan: (shiftFraction: Float) -> Unit, onResize: (edge: Edge, fraction: Float) -> Unit, onJump: (centerFraction: Float) -> Unit, color = Slitok.Faint)` — мини-карта; `CapitalChart` — параметры `onZoom`/`onPan`/`growKey` по плану architect. Раздел README «История капитала» дополнить жестами и мини-картой; пример — в `showcase.html` и витрине.

**Для coder (строки):** даты окна «12 марта – 30 мая» (одинаковый год — без года; разные — «9 ноября 2025 – 30 мая»), `contentDescription` мини-карты «Вся история, окно 12 марта – 30 мая».

**Проверка:** `DesignSystemGuardTest`, `MotionGuardTest`. Заснять: щипок на «Всё», сдвиг окна по мини-карте и двумя пальцами, растяжение за ручку, скраб в окне, сброс касанием «Всё», сжатый герой с окном, свайп разделов вне графика, «Убрать анимации».

## Решения гейта 1 (пользователь, 2026-11-09)
- F-58: переводы между своими счетами не трата никогда (F-59). Переключатель «Переводы людям» добавляет к тратам переводы другим людям; по умолчанию выключен. Макет F-58 перерисован под это (подпись «Переводы людям»). Положение переключателя запоминается (SettingsStore), как предложил designer.
