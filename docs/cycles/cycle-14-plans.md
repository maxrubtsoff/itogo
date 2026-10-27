# Цикл 14 — планы (B-110, B-111, B-112)

Корень путей: `app/src/main/java/ru/maxrubtsoff/bankagg/`. Причины установлены по скринам и коду; на устройстве не гонялось.

## B-109

**Цель:** на «Истории капитала» точка за любой прошлый день показывает только то, что на тот день было известно, а каждая ступенька графика объясняется строкой в списке. Для данных со скрина точка 24–30.12.2024 = вклад (≈650 000 ₽ + проценты), а не 2,73 млн.

**Причина (найдена).** Счёт задним числом переносится в даты, когда о нём ничего не было известно:
- `domain/model/CapitalReconstruction.kt:61–70` (`build`): часть «Счета» за день = `сегодняшний баланс − операции позже этого дня`. Начало истории (`:55`) берётся по самому раннему событию **любого** источника, здесь это вклад 07.08.2024. Счета, впервые увиденные в 2025–2026, попадают в 2024 год с полным остатком (≈2,08 млн), а в списке у этого остатка строки нет: остаток, который сообщил банк или ввёл пользователь, операцией не является.
- `domain/model/CapitalPast.kt:87–94` (`newDays`) даёт тот же эффект на сохранённых днях: день раньше начала истории (вклад, внесённый задним числом через `withDeposits`) копирует **первую точку целиком**, со всеми её счетами.
- Проверенные гипотезы. Скраб не врёт: подпись — это `CapitalHistory.spanAt` (`CapitalHistory.kt:104`) недельной точки, значение относится к 30.12.2024, позиция на оси (~17 %) с этой датой сходится. Фильтр событий по периоду тут ни при чём, потому что на «Всё» `within` берёт всё. Список и график расходятся не во внутренних шагах (650 000 ₽ — это шаг), а в **базе**: в базу молча зашит остаток счетов, которого в 2024 году не было.

**Что считать правильным.** Значение части «Счета» на день D = сумма строк части «Счета» с датой ≤ D. Для этого у каждого счёта есть день, с которого он известен (`knownFrom`). До этого дня счёт даёт 0, в этот день в списке появляется строка «Остаток на начало» на сумму его начального остатка. Для «Вкладов» это уже так (`depositsOn` + открытие/пополнения/проценты/закрытие). «Валюта» и «Наличные» остаются приближёнными, не в этом пункте: курс за прошлые дни и «наличные как сейчас» лежат вне B-109, наличные — кандидат в `docs/ROADMAP.md`. Почему именно так: точка обязана показывать известное на ту дату, а придуманный остаток в прошлом — это ровно то, что пользователь видит как «откуда 2,7 млн».

**Класс правки:** доменная логика восстановления прошлого + одноразовое исправление уже сохранённых дней. Схема Room не меняется.

### Шаги

1. **Домен — `domain/model/CapitalReconstruction.kt`**
   - После `rateDays` (`:39`) добавить:
     - `fun knownFrom(account: Account, transactions: List<Transaction>, zone: ZoneId = systemDefault()): LocalDate` — минимум из дня первой операции счёта, дня `updatedAt` (если > 0) и дня `checkedAt` (если не null);
     - `fun openingOf(account: Account, transactions: List<Transaction>): Long` = `balance.minor − Σ amount.minor` всех его операций (в валюте счёта).
   - `build` (`:65–70`): если `day < knownFrom(a)`, счёт пропускается (0 в ACCOUNTS/CURRENCY). Остальной расчёт не меняется. В KDoc (`:7–14`) заменить «accounts from today's balance…» на правило «счёт — с дня, когда стал известен».
   - После `fillEmptyParts` (`:108`) добавить `fun capUnknown(saved: List<CapitalPoint>, rebuilt: List<CapitalPoint>, accounts: List<Account>, transactions: List<Transaction>, included: Set<CapitalPart>, today: LocalDate, zone): List<CapitalPoint>`. Правило: для сохранённых дней раньше `today` **и** раньше самого позднего `knownFrom` среди учитываемых (`!excluded`) счетов часть ACCOUNTS := `min(saved, rebuilt)`, часть CURRENCY — так же. `totalMinor` меняется на разницу, только если часть входит в `included`. Возвращает только изменённые точки. Почему `min`: это идемпотентно (повторный прогон и восстановление старой копии на новый телефон ничего не ломают), значение только уменьшается и только там, где был неизвестный счёт.

2. **Домен — `domain/model/CapitalPast.kt`**
   - `newDays` (`:87–94`): день **раньше первой сохранённой точки** даёт `CapitalPoint(day, 0, emptyMap())` вместо копии первой точки, потому что до начала истории ничего не известно. День внутри истории по-прежнему копирует предыдущую точку. В KDoc (`:87`) исправить фразу «as on its first».
   - `withShifts` (`:72`): дни сдвигов раньше первой точки в `newDays` не передавать (иначе база 0 + дельта даст отрицательную часть). Значит, `shifts.map { it.from }.filter { !it.isBefore(sorted.first().date) }`.

3. **Домен — `domain/model/CapitalEvents.kt`**
   - `HistoryKind` (`:48`): добавить `ACCOUNT_OPEN`.
   - `build`, после цикла операций (`:195`): по каждому счёту с `!excluded` и `openingOf != 0` добавить событие `HistoryEvent(knownFrom, part = RUB ? ACCOUNTS : CURRENCY, title = "Остаток на начало", subtitle = account.title, amount = RUB ? opening : FxMath.rubOf(opening, CapitalReconstruction.rateAt(rates, currency, knownFrom)) (нет курса → null), kind = ACCOUNT_OPEN, bank = account.brand, foreign = для валютного ForeignAmount(currency, opening), ref = null, target = RUB ? null : Currency(code))`.
   - `moved` (`:291–295`): из `revalued` вычесть рублёвые суммы открытий валютных счетов, чей `knownFrom` попадает в шаг (`inStep(prev, cur)`), иначе появление валютного счёта превратится в «Переоценку». `moved` получает `accounts` параметром, вызов на `:227` дополнить.
4. **Домен — `domain/model/ChartMarks.kt:45`:** `ACCOUNT_OPEN` добавить в ветку `INCOME, SPEND, TRANSFER, TRANSACTION`: крупная ступенька на «Всё»/«Счета» получает метку, как крупная операция.
5. **Домен — `domain/model/HistoryRows.kt:168`** (и все остальные исчерпывающие `when (kind)`, их покажет компилятор): `ACCOUNT_OPEN -> "Остатки счетов · $n"`.
6. **Use-case — новый `domain/usecase/RepairAccountsPast.kt`**, по образцу `BackfillCapitalParts.kt` (те же зависимости + `historyLock`). Если `settings.accountsPastRepaired()`, выход. Если `history.days()` пуст, пометить и выйти. Иначе — `build(..., all parts, day)`, затем под `lock.run` вызвать `capUnknown(history.days(), rebuilt, …)` → `upsertAll` → `markAccountsPastRepaired()`. Курсы ЦБ отдельно не запрашивать: CURRENCY берётся по тем, что уже есть, `min` всё равно не завысит.
   - `BuildCapitalHistory.kt:50`: после `upsertAll` вызвать `settings.markAccountsPastRepaired()`, потому что свежая сборка уже верная.
7. **Интерфейс — `domain/repository/SettingsRepository.kt:67`:** `suspend fun accountsPastRepaired(): Boolean = true` и `suspend fun markAccountsPastRepaired() {}` (KDoc «B-109»).
8. **Data — `data/settings/SettingsStore.kt:41`:** реализация на `prefs`, ключ `KEY_ACCOUNTS_PAST_REPAIRED`, по образцу `partsBackfilled`.
9. **DI — `di/AppContainer.kt:207`:** `val repairAccountsPast by lazy { RepairAccountsPast(capitalHistory, ledgerRepository, depositRepository, fxRepository, ratesRepository, cashRepository, settings, today, historyLock) }`. В `trackCapitalHistory` (`:214`) добавить `runCatching { repairAccountsPast() }` после `backfillCapitalParts()`.
10. **UI:** ViewModel и экран не меняются. В строке события проверить, что `ACCOUNT_OPEN` с `bank` рисуется иконкой банка, как «Новый вклад» (где читается `event.bank`); если там исчерпывающий `when` по kind — добавить ветку как у `INCOME`. Строка без `ref`, шторка операции на неё не открывается.

**Миграция:** не нужна, схема не меняется (БД version 20). Сохранённые дни правит одноразовый `RepairAccountsPast` (шаг 6), флаг лежит в prefs.

### Тесты (JVM, `app/src/test/java/ru/maxrubtsoff/bankagg/domain/`)
Кейсы `build` — в существующий тест восстановления, если он есть, иначе новый `CapitalReconstructionTest.kt`.
- `b109_pointOfDecember2024_isDepositOnly` (**падает сейчас**). Данные: вклад 650 000 ₽ от 07.08.2024; RUB-счёт с балансом 2 082 958,29 и первой операцией 01.10.2026; today = 27.10.2026. `build` → `CapitalHistory.series(ALL)` → `weekly`: точка 30.12.2024 == `CapitalPast.depositsOn(deposits, 30.12.2024)`, ACCOUNTS == 0.
- `build_accountBeforeKnownDay_isZero` (**падает сейчас**) и `build_accountFromKnownDay_isOpeningPlusOps`.
- `accountsPart_equalsSumOfAccountEvents` (**падает сейчас**, инвариант): для каждого дня `build` `valueOf(ACCOUNTS)` == сумма `amountMinor` событий `CapitalEvents.build` части ACCOUNTS с датой ≤ дня. Два счёта с разными `knownFrom`, доход/расход, пара своих переводов.
- `knownFrom_isEarliestOfFirstTxUpdatedChecked`; `openingOf_isBalanceMinusAllOps`.
- `capUnknown_lowersOverstatedDays` (день с 2,73 млн до `knownFrom` → значение rebuilt), `capUnknown_isIdempotent` (второй прогон даёт пустой список), `capUnknown_neverRaises_andSkipsDaysAfterAllKnown`, `capUnknown_totalFollowsIncludedOnly`.
- `CapitalPastTest.withDeposits_dayBeforeHistory_hasNoAccounts` (**падает сейчас**: копирует 2 млн), `withShifts_shiftBeforeHistory_addsNoDay`.
- `CapitalEventsTest.accountOpen_onKnownDay_withOpeningSum`, `accountOpen_excludedAccount_none`, `foreignAccountOpen_isNotRevaluation` (в день появления валютного счёта нет «Переоценки»).

### Критерии готовности
- `./gradlew testDebugUnitTest assembleDebug` зелёные; три теста, помеченные «падает сейчас», на старом коде красные.
- Инварианты: часть «Счета» за день = сумма строк «Счета» до этого дня; счёт не попадает в дни раньше своего `knownFrom`; `capUnknown` только уменьшает и идемпотентен; сегодняшний капитал (`GetCapital`/`CapitalCalculator`) не меняется; деньги только в `Long`-копейках.
- На устройстве (device-check): «История капитала» → «Всё». Точки до появления счетов ≈ сумма вкладов. На дне первого известного остатка — ступенька, и в списке есть «Остаток на начало». Текущая сумма справа не изменилась.

## B-110

**Цель:** шторки «Изменить» (и её прокрутка к «Счёт/Сохранить/Отмена») в истории капитала занимают высоту экрана, содержимое не обрезается, под ним нет пустого фона.

**Причина (общая, в `SlitokSheet`).** Две «шторки» на скринах — одна: `OperationSheet` в `Mode.EDIT` (`ui/history/OperationSheet.kt:81`, `imeField = true`), второй скрин — она же, прокрученная вниз. `imeField` уводит `SlitokSheet` в `ImeSheet` (`ui/common/Sheets.kt:95-98, 173`). Там в колонку шторки после прокручиваемого содержимого вставлен `ImeReserve()` (`Sheets.kt:247`): место под клавиатуру 280 dp (`DefaultIme`, `:124, :157`) держится с первого кадра. В `EditBody` клавиатура не открывается (поля не берут фокус), и резерв остаётся навсегда. Он вычитается из высоты `weight(1f, fill=false)` колонки (`:241`): прокрутка обрезается по краю, ниже пустая панель. Арифметика по скрину сходится: пустой хвост ≈ 675 px ≈ 280 dp + 12 dp + жестовая полоса.

**Шаги (один слой, `ui/common/Sheets.kt`):**
1. `ImeSeen` (`:127`): добавить `var released by mutableStateOf(false)` (читается в `layout`, поэтому смена перемеряет).
2. `ImeReserve` (`:143`): `DisposableEffect`/`LaunchedEffect(seen)`: через `Motion.NORMAL` + 300 мс после показа, если `seen.peak == 0` (клавиатура так и не пришла) — `seen.released = true`. В `layout`: `held = if (seen.leaving || seen.released) 0 else ...`. Если клавиатура позже придёт — высота растёт по `now` (путь «клавиатура выше ожидаемой» уже есть). Выбор: тайм-аут в самом резерве, а не флаг на экране, — чинит все шторки с `imeField`, без правок экранов.
3. `lastImePx` при `released` не трогать (не обнулять), чтобы шторка с настоящим полем по-прежнему держала место.
4. Экран не трогать. Дизайн-система: правка поведения внутри `SlitokSheet`; в `docs/design-system/README.md:78` одной фразой дописать «место под клавиатуру отпускается, если клавиатура не пришла».

**Тесты:** логика резерва лежит в UI, JVM-теста нет. Вынести решение в чистую функцию `ImeRoom.height(held, now, bars, peak, leaving, released)` в `ui/common` (без Compose, как `TourFrame`) и покрыть `ImeRoomTest` (`ui/` в `app/src/test/.../ui/`): `releasedWithoutKeyboardIsBarsOnly`, `keyboardTallerThanHeldGrows`, `leavingDropsHeld`, `neverBelowBars`.

**Критерии:** `./gradlew testDebugUnitTest assembleDebug` зелёные; `DesignSystemGuardTest` не тронут. На устройстве: «Изменить» в истории — «Отмена» видна без пустого хвоста; шторка с реальным полем (клавиатура сразу) открывается без скачка (B-58 не сломан).

## B-111

**Цель:** на шаге обучения «Со скрина быстрее» карточка не накрывает демонстрируемую шторку и шторка видна целиком.

**Вывод (предпосылка задания неверна).** Скрин `b-111-hint-overlap.jpg` — не реальная шторка, а песочница тура. Признак: шторка нарисована под нижней панелью приложения (реальная `ModalBottomSheet` — окно над панелью). Шаг (`domain/model/Tour.kt:143,150`) нарочно открывает «Добавить вклад» (`TourAnim.OPEN_SHEET` → `TourSandboxLayer`, `ui/onboarding/Tour.kt:184-189` → `AddSheet` → `LocalInlineSheets` → `InlineSheet`/`InlineSheetLayer`, `Sheets.kt:273,292`), целью света служит сама шторка (`TourTarget.ADD_SHEET`, `Sheets.kt:305`), карточка по `TourFrame.cardTop` встаёт над ней (`Tour.kt:349`). На скрине карточка действительно над шторкой (нижний край 1418 < верх шторки 1450), а «перекрытие» — это карточка вклада под ней, затемнённая по замыслу. Взаимное исключение «шторка ↔ подсказка» сломало бы этот шаг и тур с `PICK_SHOTS`, поэтому его НЕ вводим. Реальная шторка в приложении отдельное окно поверх `TourCoach`, подсказка поверх неё лечь не может.

Что исправляется на деле: шторка обрезана нижней панелью (B-112), поэтому её рамка/границы (`ADD_SHEET` bounds) и вид «шторка сломана, карточка поверх» неверны. Починка B-112 снимает основной дефект.

**Где держать знание (механизм, даром для будущих шторок).** Все шторки в туре идут через `SlitokSheet → InlineSheet → InlineSheetLayer`, и слой сам регистрирует границы шторки как `TourTarget.ADD_SHEET`. Правило «карточка не пересекает окно шторки» держим в чистой `TourFrame` (уже есть `cardTop`), а не в экранах: новая шторка получит его автоматически, лишь бы шла через `SlitokSheet`. Домен/сессию не расширяем (флаг «шторка открыта» уже есть: `session.sheetOpen`, `host.content != null`). Прошлые правки (cycle-10, B-56, B-67: «безопасные зоны» `safe` в `Tour.kt:305`) учитывали только края экрана и жестовую полосу — не панель и не шторку; поэтому наложение вернулось. Не повторять: не добавлять флаг-проверку в `TourCoach` или экран.

**Шаги:**
1. После B-112 (порядок: сначала B-112). `ui/onboarding/TourFrame.kt:41` `cardTop`: добавить в тест-покрытие инвариант — если окно шторки целиком в нижней половине, карточка (`above` ветка) не пересекает окно; ветку `return (safe.bottom - cardHeight)` (`:47`) для окна, чей верх ниже карточки, заменить на «над окном, но не выше `safe.top`»: `(window.top - gap - cardHeight).coerceAtLeast(safe.top)`. Выбор: лучше уйти под статус-бар до `safe.top`, чем лечь на шторку.
2. `Tour.kt:305` `safe`: нижнюю границу брать не `height - bottomInset`, а минус высота нижней панели приложения (передать `padding.calculateBottomPadding()` из `TourSandboxLayer`/MainActivity:1028 — проверить, доступно ли в `TourCoach`; если нет, достаточно шагов 1 и B-112).
3. Устройство: пройти оба тура (вклады, кэшбэк) до шага шторки; подтвердить, что карточка не касается шторки.

**Тесты:** в `app/src/test/.../ui/TourFrameTest.kt` — `cardAboveBottomWindowNeverOverlapsIt` (окно шторки в нижней трети, карточка высотой 300: `card.bottom <= window.top`), `tallWindowKeepsCardInsideSafeTop`.

**Критерии:** тесты и сборка зелёные; шаг «Со скрина быстрее»: шторка целиком над панелью, карточка над шторкой, не пересекает.

## B-112

**Цель:** в шторке «Добавить вклад» видны иконки способов и подписи, нижняя панель их не закрывает.

**Причина.** Тот же скрин: это шторка тура (`InlineSheetLayer`, `ui/common/Sheets.kt:292-316`), а не `ModalBottomSheet`. Слой — `Box(fillMaxSize, BottomCenter)` в песочнице; нижняя панель приложения (реальная, `MainActivity.kt:694-696`) рисуется поверх и закрывает низ шторки. `padding` (с высотой панели) приходит в `TourSandboxLayer` (`Tour.kt:155`), но в `InlineSheetLayer` (`Tour.kt:182`) не передаётся; `InlineSheet` (`Sheets.kt:282`) учитывает только `navigationBarsPadding()`. В реальных шторках (`Sheets.kt:113`, `navigationBarsPadding` + `imePadding`) проблемы нет: окно над панелью.

**С B-110 причина разная** (B-110 — резерв клавиатуры в `ImeSheet`; B-112 — слой шторки тура не знает про панель). Один фикс на оба не годится; общее лишь «геометрия низа шторки». Разделы оставлены оба.

**Шаги:**
1. `ui/common/Sheets.kt:292` `InlineSheetLayer(host, modifier)`: добавить параметр `bottomInset: Dp = 0.dp`; внутри `Box` перед шторкой — `Modifier.padding(bottom = bottomInset)`. Колонка шторки остаётся в границах над панелью; `navigationBarsPadding` в `InlineSheet` убрать (`:282`), если `bottomInset` уже включает системную панель (padding Scaffold-а включает её) — проверить по `padding.calculateBottomPadding()`.
2. `ui/onboarding/Tour.kt:182`: `InlineSheetLayer(session.sheets, bottomInset = padding.calculateBottomPadding())`.
3. Обновить пример в `docs/design-system/README.md`/витрине не требуется (поведение тура).

**Тесты:** чистой логики нет; проверка на устройстве (`device-check`): шаг «Со скрина быстрее» — обе плитки с подписями над панелью.

**Критерии:** `./gradlew testDebugUnitTest assembleDebug` зелёные; плитки «Со скринов / Вручную» видны целиком в туре вкладов и кэшбэка.

## B-107

**Цель:** уведомление `Списано 440.00 счет *4863 п/п №92712 от 27.10.26 в адрес АО "АЛЬФА-БАНК" счет 0741. Остаток 3079.36` даёт `ParsedOp`: −440,00 ₽ (RUB), остаток 3079,36, и не ломает прежние форматы.

**Где отваливается (проверено по коду).** Не на «п/п» и не на направлении: `списан` есть в `OUT_MARKS` (`NotificationParser.kt:97`) → `detectDirection` даёт OUT; промо-меток нет; `PHONE`/`MASK` (стр. 150) вырезают `*4863` и `счет 0741` (3-я ветка MASK, за ним нет знака валюты) — это верно и нужно. Формат падает на стр. 159–167: `hits()` требует знак валюты рядом с суммой (`AMOUNT_THEN_CCY`/`CCY_THEN_AMOUNT`), а в тексте `440.00` и `3079.36` без «₽/руб» → `hits` пуст → `hits.firstOrNull{…} ?: return null` (стр. 166–167). Остаток сам по себе читается без знака (запасной `Regex(NUM)` стр. 173) — проблема только в сумме операции.

**Решение (консервативно):** общее правило «голое число — не сумма» не трогаем. Добавляем узкий запасной разбор «платёжного поручения», который включается ТОЛЬКО когда `hits` пуст, поэтому прежние форматы не меняют результата.

**Шаги (домен, `domain/parser/NotificationParser.kt`):**
1. Рядом с `MASK` (стр. ~75) — `private val ORDER_AMOUNT = Regex("""(?<![а-яё])(?:списано|зачислено)[\s  ]+$NUM[\s  ]+сч[её]т[\s  ]+[*•]\d{4}(?!\d)""", IGNORE_CASE)` — сумма стоит СРАЗУ после глагола и сразу за ней идёт «счет *маска» нашего счёта. Ловушки отсекаются якорем: `№92712` и дата `27.10.26` не стоят сразу после глагола; `счет 0741` (получатель) без звезды не подходит под `[*•]\d{4}`; остаток в сумму не попадёт (берётся группа сразу после глагола).
2. В `parse` после стр. 159: `val hits = hits(clean).ifEmpty { orderHit(blob) }`, где `orderHit` ищет `ORDER_AMOUNT` в `blob` и возвращает `listOf(Hit(start = индекс группы суммы, major = группа, currency = RUB))` или пусто. `clean` той же длины, что `blob` (маски заменяются пробелами одной длины), значит индекс совпадает со сравнением с `balanceAt`.
3. Валюта RUB жёстко (маска «счет *dddd» + п/п — российский расчётный счёт); обоснование: формат не содержит знака валюты, иначе суммы не прочесть.
4. Дальше без изменений: `balanceAt` = «остаток» (стр. 162), сумма = первый хит до него, остаток = запасной `Regex(NUM)` после «остаток» → `3079.36` → 307936. Мелкая правка: в этой ветке `kind`, если `UNKNOWN` (в тексте «списано», а `EXPENSE` ищет «списание»), ставить по направлению: OUT → `EXPENSE`, IN → `INCOME`. Не менять общие списки `EXPENSE`/`INCOME` (иначе изменится вид у прежних форматов).
5. `const val VERSION = 3 → 4` (стр. 35) + строка в KDoc «4 — Альфа-Бизнес, списание по п/п (B-107)»: `AppContainer.kt:155` пере-прочитает журнал один раз, и уже записанное «не разобрано» исправится (`RepairFromJournal`). Проверить Grep по тестам на жёсткое `VERSION == 3`.

**Тесты** (`app/src/test/java/ru/maxrubtsoff/bankagg/domain/NotificationParserTest.kt`):
- `alfaBusinessPaymentOrderParsed` — текст из задачи (title `null` и title «Альфа-Бизнес»): amount = −44000, currency RUB, direction OUT, balance = 307936, kind EXPENSE.
- `alfaBusinessRecipientAccountIsNotOurs` — тот же текст: balance ≠ 74100, amount ≠ 92712/2710 копеек-варианты, `counterparty == null` (получатель/«0741» не попал ни в сумму, ни в остаток).
- `alfaBusinessWithoutOwnMaskIsNull` — `Списано 440.00 счет 0741. Остаток 3079.36` (нет `*маски` нашего счёта) → `null`.
- `bareAmountStillNotAnAmount` — `Списано 440.00` → `null`; `Оплата 440.00 счет *4863` (глагол вне белого списка) → `null`.
- `alfaBusinessWithoutBalance` — `Списано 440.00 счет *4863 п/п №92712` → amount −44000, balance `null`.
- `alfaBusinessCredit` — `Зачислено 1000.00 счет *4863 …` → IN (синтетический, формат предполагаем по аналогии).
- Регрессия: все прежние кейсы файла зелёные без правок (запасной разбор их не затрагивает: у них `hits` непуст).

**Критерии:** `./gradlew testDebugUnitTest assembleDebug` зелёные; инварианты: сумма только в копейках `Money`; голое число без знака валюты вне формата «глагол + сумма + счет *маска» остаётся не суммой; счёт получателя без звезды не читается; рекламные тексты по-прежнему `null`.

## U-68

**Цель:** скрин вклада, который уже есть, не заканчивается молча: сообщение «Такой вклад уже есть», список прокручен к карточке, карточка ненадолго подсвечена жёлтой полоской слева. Остальные исходы (сохранили новый вклад) тоже сообщают результат и подсвечивают добавленное.

**Что есть.** Правило дубликата уже в домене и под тестом: `DepositMatch.find` (`domain/model/DepositMatch.kt:10`, `DepositMatchTest`), вызывается из `RecognizeDepositShot.kt:52` только при `RECOGNIZED`. Молчание — от UI: `DepositsViewModel.recognise` (`:236–239`) удаляет дубликат-скрин (в `stash`), предпросмотр `DepositShotPreviewScreen.kt:55–56` видит пустой список и молча делает `leave()` → `backToDeposits` (`MainActivity.kt:907,1100`).

**Правило дубликата (решение): оставить точным** — банк (`brand.key`) + сумма (`principal`) + ставка (`rateBp`) + дата начала + дата конца; порогов нет. Обоснование: ложное срабатывание удаляет скрин настоящего нового вклада (две похожие вклады одного банка бывают с разными датами/суммой), а пропуск дубликата безопасен (пользователь увидит форму и сам отменит). Все поля OCR целые/даты — допуска на «дрейф» не нужно; неполный черновик (`!isComplete`) дубликатом не считается. Менять `DepositMatch` не требуется. Допустимо дописать в `DepositMatchTest` один тест `matchIgnoresExitAmountAndTitle` (вклад с другим `title`/`exitAmount` при тех же 5 полях — всё равно дубликат), если такого ещё нет.

**Шаги:**
1. **Домен/VM-механика.** `DepositsViewModel`: (а) в `recognise` при дубликате запомнить `duplicateOf: MutableMap<shotId, depositId>` (рядом со `stash`); (б) `fun duplicateOfShots(ids: List<String>): String?` — вернуть id вклада и очистить запись; (в) `val spotlight = MutableStateFlow<Spotlight?>(null)`, `data class Spotlight(val depositId: String, val nonce: Long)`; `fun spotlight(depositId)` (nonce = `System.nanoTime`) и `fun clearSpotlight(nonce)` (сбрасывает, только если nonce тот же). Всё в ViewModel — чистой логики нет, домен не меняется.
2. **Сообщение без «Вернуть».** `ui/common/Undo.kt:23`: в `UndoCenter` добавить `fun say(message: String)` (обычное сообщение без кнопки); `Action` получает `val undoable: Boolean = true`; в `showIn` (`:46`) при `!undoable` — `showSnackbar(message, duration = Short)` без `actionLabel`. VM зовёт `undo.say(...)` (VM уже держит `undo`, см. `offerUndo` `:199`). Хост — общий в `MainActivity.kt:491–492`, виден поверх вкладки «Вклады»; текст рисует designer.
3. **Куда ведут исходы (одиночный скрин и ручное добавление):**
   - Дубликат: в `DepositShotPreviewScreen.kt:56` до `leave()`: `vm.duplicateOfShots(shotIds)?.let { vm.spotlight(it); vm.say("Такой вклад уже есть") }`. Пакет (`DepositBatchScreen`): итог по пропущенным уже есть («N пропустили · Вернуть», `:142–144`); при `onDone()` с `stash` непустым добавить `say("Уже были: N")` (в `dropStash`, `saveGroups` `:224`) — одно сообщение, без прокрутки.
   - Новый вклад: в `saveDeposit` (`:261`) при `existingId == null` после `repo.add` — `spotlight(deposit.id)` + `say("Вклад добавлен")`; в `saveGroups` — если сохранён один вклад: то же; если несколько: `say("Добавлено вкладов: N")` без подсветки. Правка существующего (`existingId != null`) — молча, как раньше (пользователь сам нажал «Сохранить» на карточке).
   - Не распознан (`FAILED`/`NEEDS_INPUT`) — экран предпросмотра остаётся с формой (не возврат), менять не нужно.
4. **Подсветка — переиспользуем, не новый компонент.** Полоска сейчас нарисована внутри `HistoryLine` (`ui/common/HistoryLine.kt:58,63`: `animateColorAsState(Slitok.Brass2/Transparent, Motion.tween(NORMAL))` + `drawRect(bar, Offset.Zero, Size(3.dp, height))`). Вынести эти две строки в `fun Modifier.accentBar(active: Boolean): Modifier` в том же файле (`ui/common`); `HistoryLine` зовёт её вместо inline-кода (вид не меняется); `DepositRow` (`DepositsScreen.kt:337`, корневой `Row` на `:340`) получает параметр `highlighted: Boolean` и `.accentBar(highlighted)`. Строка в `docs/design-system/README.md` у `HistoryLine`: «`Modifier.accentBar` — жёлтая полоска слева, общая для истории и вкладов»; в витрине `ShowcaseScreen.kt:343` менять нечего (вид тот же). Токен только `Slitok.Brass2`; `DesignSystemGuardTest` не затрагивается (код в `ui/common`).
5. **Скролл к карточке.** Экран — одна `Column(verticalScroll(rememberTabScrollState()))` (`DepositsScreen.kt:129–133`), поэтому нужен `BringIntoViewRequester`, а не `LazyListState`. В `DepositList` (`:279`) для строки с `id == spotlight?.depositId` создать `BringIntoViewRequester` и навесить `.bringIntoViewRequester(r)` на `Box` строки (`:298`); `DepositList` получает `spotlightId: String?` и `onSpotlightShown`. Сквозная передача: `DepositsScreen` (`:120`) читает `val spot by vm.spotlight.collectAsStateWithLifecycle()` и передаёт в `DepositList`/`BankGroup` (`:171–173`, `:328`).
6. **Погашение подсветки.** В `DepositList` для найденной строки: `LaunchedEffect(spot.nonce) { withFrameNanos {}; requester.bringIntoView(); delay(2_200); vm.clearSpotlight(nonce) }`; `highlighted = spot?.depositId == id` — полоска гаснет через `animateColorAsState` сама. Задержка кадра нужна, чтобы строка (каскад `cascade(i, play)`) уже стояла. Если анимации отключены — `Motion` отдаёт мгновенно, поведение то же.
7. **Вклад в «завершённых».** Список завершённых сворачиваем (`showCompleted`, `:122`): если `spot.depositId` среди `state.completed` — `LaunchedEffect(spot)` ставит `showCompleted = true` до подсветки (иначе строки нет и подсветка не сработает). Если вклада нет ни в одном списке (удалён) — `vm.clearSpotlight`.
8. **Вкладка.** Подсветка запускается только когда экран вкладок «Вклады» реально на экране; `backToDeposits` (`MainActivity.kt:1100`) ведёт на `Routes.capital(DEPOSITS)` — эффект стартует при композиции списка, состояние живёт во ViewModel (activity-уровня), так что не теряется при переходе. Ничего в навигации не менять.

**Тесты:** чистая логика уже покрыта `DepositMatchTest` (точное совпадение; разные банк/сумма/ставка/даты — не дубликат; неполный черновик; пустой список). Добавить `DepositMatchTest.matchIgnoresExitAmountAndTitle` (см. выше). Механика UI (spotlight/clear по nonce) — не выносим в домен: решения там нет. Проверка на устройстве (`device-check`): залить скрин существующего вклада (одиночный) → возврат на «Вклады», сообщение, прокрутка, полоска 2 с и гаснет; то же для вклада в «Завершённых» и при сортировке «По банкам»; залить скрин нового вклада → «Вклад добавлен» + полоска на нём.

**Критерии:** `./gradlew testDebugUnitTest assembleDebug` зелёные; инварианты: дубликат — только по 5 полям точно; домен не меняется, без `android.*`; полоска — `Slitok.Brass2`, один код в `HistoryLine`/`DepositRow`; сообщения коротко, по-русски (точный текст и вид — designer).

## U-69

**Цель:** выбор счёта/банка в шторке «Изменить» истории капитала — строка-поле `SelectRow` и шторка списка, как в кэшбэке и вкладах, а не простыня `SlitokChip`.

**Почему не `BankPickSheet` как есть.** `BankPickSheet(selected: BankBrand?, onPick(BankBrand))` (`ui/common/BankPickSheet.kt:21`) выбирает бренд, а в `OperationSheet` выбирается счёт (`accountId`, `d.accounts` — `a.id/a.title/a.brand`), и у одного банка может быть несколько счетов — по бренду счёт не определить. Решение: в тот же файл `BankPickSheet.kt` добавить `AccountPickSheet` — тот же `SlitokSheet` + заголовок + `SheetList`/`SheetRow` (`leading = BankIcon(36.dp)`, `selected`, закрытие после выбора), только по списку `(id, title, brand)`; вид идентичен банковской шторке, новых токенов нет. Строка-поле — готовый `SelectRow`.

**Шаги (UI; домен/данные не меняются):**
1. `ui/common/BankPickSheet.kt` (после стр. 46): `@Composable fun AccountPickSheet(accounts: List<AccountChoice>, selectedId: String?, onPick: (String) -> Unit, onDismiss: () -> Unit)` и `data class AccountChoice(val id: String, val title: String, val brand: BankBrand)` — чтобы `ui/common` не зависел от модели истории. Поиск не нужен (счетов мало).
2. `ui/history/OperationSheet.kt:191–198`: вместо `Text("Счёт")` + `Column` с чипами — `var pickingAccount by rememberSaveable { mutableStateOf(false) }` (рядом с `picking`, стр. 168); `val acc = d.accounts.firstOrNull { it.id == accountId }`; `SelectRow(value = acc?.title, placeholder = "Выберите счёт", onClick = { pickingAccount = true }, leading = acc?.let { { BankIcon(it.brand, 36.dp) } })`; под ним `if (pickingAccount) AccountPickSheet(d.accounts.map { AccountChoice(it.id, it.title, it.brand) }, accountId, { accountId = it }, { pickingAccount = false })`. Условие показа `ref is HistoryRef.Tx && d.accounts.size > 1` оставить.
3. Импорты: `SelectRow`, `AccountPickSheet`, `AccountChoice`; неиспользуемые убрать.
4. Дизайн-система: в `docs/design-system/README.md` (строка `SelectRow` + `BankPickSheet`, ~:69) дописать «`AccountPickSheet` — то же для выбора счёта»; пример в витрине `ShowcaseScreen.kt` рядом с `BankPickSheet`, если он там есть (иначе добавить строку примера, как требует `ui/CLAUDE.md`). Макет/согласование — designer (по плану цикла новое решение не вводится, это канон).
5. Шторка выбора поверх шторки «Изменить» (`ImeSheet` при `imeField = mode == EDIT`, `OperationSheet.kt:81`): вложенный `ModalBottomSheet` допустим (так уже делает `BankPickerRow` внутри форм); при проверке на устройстве убедиться, что не конфликтует с B-110.

**Другие места с самодельным выбором банка — найдено 1 (чинится тут же):**
1. `ui/history/OperationSheet.kt:193–195` — выбор счёта чипами с логотипом банка (п. выше).
Проверено и НЕ является самодельным единичным выбором: `ui/settings/AddBankScreen.kt:148,157` — `SlitokChip` с `BankIcon` для множественного «добавить мои банки» (лента банков, не выбор одного; по README `SelectRow` для неё не подходит — не трогать); `ui/settings/NewBanksScreen.kt:124` — ответ «Это <банк>?» (`Choice`), не выбор из набора; `ui/cashback/CashbackScreen.kt:396,439,533`, `ui/deposits/DepositsScreen.kt:316,349`, `DepositBatchScreen.kt:227`, `CashbackBatchScreen.kt:257` — показ логотипа, не выбор. Уже каноничны: `BankPickerRow` (5 мест), `SaveDialogs.kt:41`, `SubscriptionScreens.kt:159,187`.

**Тесты:** чистой логики нет (замена контрола); `DesignSystemGuardTest` обязан остаться зелёным без ослабления (код в `ui/common` и `ui/history` не использует hex/шрифты).

**Критерии:** `./gradlew testDebugUnitTest assembleDebug` зелёные; на устройстве (`device-check`): правка операции с несколькими счетами — поле «Счёт» с логотипом и названием, тап открывает шторку, выбранный отмечен, после выбора шторка закрывается и в форме новое значение; «Сохранить» пишет выбранный `accountId`.
