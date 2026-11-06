# Планы цикла 17

Группы: **A** = F-56 + B-120 (виджет: `CashbackWidget.kt`, `CashbackWidgetLayout.kt`), сначала B-120, потом F-56. **B** = B-122 + B-123 (парсер/запись/восстановление), сначала B-122, потом B-123; `NotificationParser.VERSION` 5→6 один раз (в B-122). **C** = B-121 (история капитала), отдельно. Группы A, B и C можно вести параллельно; B-123 и B-121 оба затрагивают `CapitalEvents` только косвенно (по дате), сами файлы не правят.

---

## B-120

**Цель:** широкий виджет в режиме BANKS показывает все блоки, которые реально помещаются по высоте, «+N» только для того, что не влезло, без большого пустого хвоста.

**Причины (по коду):**
1. `CashbackWidgetLayout.grid` (`CashbackWidgetLayout.kt:46`) считает каждую строку как `LINE 20 + GAP 8`. В `BankBlockView` (`CashbackWidget.kt:283-297`) строки категорий — текст 13sp **без логотипа** (≈18dp), логотип 20dp есть только в шапке блока. Вместимость занижена: на высоких виджетах остаётся хвост, на низких лишний блок уходит в «+N».
2. `pack` (`CashbackWidgetLayout.kt:85-87`): если что-то скрыто, раскладка повторяется с `lines - 1` **во всех колонках**, хотя «+N» рисуется только в последней (`CashbackWidget.kt:255`). Колонка 0 теряет строку, из-за чего выпадает ещё один блок (случай «+2 банка»).
3. Жадная раскладка в исходном порядке (по числу строк убыв., `CashbackWidget.kt:143`) уже по сути first-fit decreasing; это оставляем.

**Шаги:**
1. Домен, `CashbackWidgetLayout.kt`:
   - добавить константы `ROW = 18f` (строка категории в блоке) и `BLOCK_HEAD = LOGO` (шапка блока = 20), `MORE_LINE = 18f` (строка «+N»);
   - новая функция `packDp(blocks: List<Int /*число категорий*/>, columns: Int, heightDp: Float): Packing` — высота блока = `BLOCK_HEAD + n*(GAP+ROW)`, между блоками `GAP`; раскладка в наименее заполненную колонку, где влезает целиком; если что-то скрыто — повтор, где **только последняя колонка** получает лимит `heightDp - (GAP + MORE_LINE)`; остальные колонки сохраняют полный лимит.
   - `fun banksHeight(widthDp, heightDp): Float` = `heightDp - 2*PADDING - HEADER` (свободная высота под блоки, без базовой строки — как сейчас в `CashbackWidget.kt:200`).
   - старый `pack(lines)` удалить вместе с его тестами-заменами (используется только в `CashbackWidget.kt:247`), чтобы не держать две модели.
   - `mode` (`:54-58`) не трогать.
2. UI, `CashbackWidget.kt:200,245-247`: `BankBlocks` получает `grid` (ширина колонок) и `CashbackWidgetLayout.banksHeight(w,h)`, зовёт `packDp(shown.map { it.rows.size }, grid.columns, height)`. Отрисовку блоков не менять; паддинг строки категорий остаётся `GAP`.
3. Если в Glance фактическая высота строки 13sp другая — правится одной константой `ROW` (на device-check сверить; это единственное место подстройки).

**Тесты** (`app/src/test/.../domain/CashbackWidgetLayoutTest.kt`):
- `packDpFitsAllWhenHeightEnough` — 5 блоков (3,2,2,1,1 категорий), 2 колонки, высота ровно под сумму → `hidden == 0`.
- `packDpHidesOnlyWhatDoesNotFit` — высота на 1dp меньше нужной для последнего блока → `hidden == 1`, остальные на местах.
- `packDpReservesMoreLineOnlyInLastColumn` — блок, который влезает в колонку 0 на полном лимите, при скрытии остаётся там (раньше уходил из-за `lines-1`).
- `packDpNoExtraTail` — для каждой высоты H: если `hidden==0`, то при H-ROW-GAP хотя бы... проще: заполненность максимальной колонки > `H - (BLOCK_HEAD+GAP+ROW)` либо всё показано (нет «хвоста» размером в блок при скрытых).

**Критерии:** `./gradlew testDebugUnitTest assembleDebug` зелёные. Инвариант: ни один блок не режется; «+N» только в последней колонке и только при `hidden>0`. Device-check: 3–4 высоты × 2 ширины, 3 и 5 банков — нет пропавших блоков при свободном месте, нет обрезанных снизу строк.

---

## F-56

**Цель:** виджет кэшбэка умеет три сортировки (банк / название / размер), по умолчанию в узком (1 колонка) — по банку; режим переключается тапом в виджете и хранится на экземпляр виджета.

**Решения:**
- Хранение — Glance `PreferencesGlanceStateDefinition` (состояние на `GlanceId`, сохраняется после перезапуска/обновления, удаляется с виджетом). Не `SettingsStore`: тот глобальный, а нужно на экземпляр.
- Переключение — `actionRunCallback<CycleSortAction>()` на отдельной кликабельной иконке/метке режима в шапке (вид — designer). Остальная площадь виджета по-прежнему открывает приложение. Цикл: BANK → NAME → SIZE → BANK.
- Режим BANKS (широкий большой) — блоки по банку всегда; сортировка там влияет на порядок категорий внутри блока (NAME/SIZE) и блоков (NAME — по названию банка, SIZE — по максимальному проценту, BANK — как сейчас). Это выносит текущие сортировки `CashbackWidget.kt:140,143` в домен.
- Карточку на главном (`CashbackSpotlight`) не трогаем.

**Шаги:**
1. Домен, новый `domain/model/CashbackWidgetSort.kt`:
   - `enum class CashbackWidgetSort { BANK, NAME, SIZE; fun next(): CashbackWidgetSort; companion fun parse(s: String?): CashbackWidgetSort /*null/мусор → BANK*/ }`, подпись режима `label` («Банк»/«Название»/«Размер»).
   - `object CashbackWidgetOrder`:
     - `fun rows(rows: List<SpotRow>, sort): List<SpotRow>` — для плоского списка (берёт `spot.allRows`, `CashbackSpotlight.kt`): BANK — группировка по `banks.first().title` (порядок групп — по первому появлению в ранжировании Spotlight, внутри — ранг Spotlight); NAME — `category` без регистра, затем процент убыв.; SIZE — процент убыв., затем название. Тип строки — тот, что уже в `spot.allRows` (не новый).
     - `fun blocks(items: List<CashbackItem>, sort): List<BankGroup>` где `data class BankGroup(brand: BankBrand, basePercent: <тип percent>?, rows: List<CashbackItem>)` — перенос логики `CashbackWidget.kt:134-143` (partition по `CashbackSpotlight::isBase`) с учётом sort.
2. UI, `ui/widget/CashbackWidget.kt`:
   - `override val stateDefinition = PreferencesGlanceStateDefinition`; ключ `stringPreferencesKey("sort")`.
   - `provideGlance` (`:101-104`): внутри `provideContent` читать `currentState<Preferences>()[SORT]` → `CashbackWidgetSort.parse`, передавать в `Content`; `load` возвращает сырые данные (spot + items + logos), порядок применяется в `Content` через `CashbackWidgetOrder` (иначе после смены режима нужен перезапуск `load`). `Model` хранит `spotRows`/`items` вместо готовых `rows`/`banks`, маппинг в `WidgetRow`/`BankBlock` — после сортировки.
   - Новый `class CycleSortAction : ActionCallback` (в том же файле): `updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { it[SORT] = parse(it[SORT]).next().name }` затем `CashbackWidget().update(context, id)`.
   - Элемент в шапке (`:174-188`) по макету designer, с `clickable(actionRunCallback<CycleSortAction>())`; при `cashbackOff`/пусто — не показывать.
   - В узкой раскладке BANK опционально логотип — уже есть в каждой строке; разделители групп — по макету.
3. Room/DI/VM/Factory/маршрут — не нужны.

**Тесты** (`app/src/test/.../domain/CashbackWidgetOrderTest.kt`):
- `defaultSortIsBank` — `parse(null)` и `parse("junk")` → BANK; `next` цикличен.
- `bankSortGroupsRowsOfOneBankTogether` — вход Альфа, Озон, Сбер, Альфа, Озон → Альфа, Альфа, Озон, Озон, Сбер; внутри порядок Spotlight сохранён.
- `nameSortIsCaseInsensitive`, `sizeSortByPercentThenName`.
- `blocksMatchOldOrderForBankSort` — повторяет прежнее поведение `CashbackWidget.kt:140,143`.
- `basePercentSeparatedFromRows` — базовые позиции не попадают в `rows`.

**Критерии:** сборка и тесты зелёные; в `CashbackWidget.kt` не осталось `sortedWith`/`sortedBy`. Device-check: узкий виджет по умолчанию сгруппирован по банку; тап по переключателю меняет порядок; два виджета держат разные режимы; режим сохраняется после перезагрузки телефона и после изменения данных кэшбэка.

---

## B-122

**Цель:** «Поступило 322581.00 счет *4863 от ООО "Бета" счет *1007. Остаток 325218.58» → приход +322 581,00 ₽ на *4863, баланс 325 218,58 ₽, контрагент `ООО "Бета"`.

**Шаги:**
1. `domain/parser/NotificationParser.kt:99-102` `ORDER_AMOUNT`: альтернатива глагола `(списано|зачислено|поступило)`; остальное (сумма, затем первый `счет *dddd` — свой) без изменений. Свой счёт — **первый** `счет *dddd` после суммы; второй (после `от`) игнорируется как маска.
2. `orderHit` (`:217-220`): для «поступило» направление IN (как «зачислено»). Контрагент: регекс `от\s+(.+?)\s+сч[её]т\s+\*?\d{4}` → `counterparty` (кавычки сохраняем как есть, trim). Для «списано» существующий путь `в адрес …` не трогать.
3. `NotificationParser.kt:47` `VERSION = 6` + строка в комментарии версий (42-46): «6 — Альфа-Бизнес «Поступило … счет … от …»; время операции из текста (B-123)».
4. Больше слоёв не нужно: «Восстановить из журнала» перепарсит по версии (`RepairFromJournal.kt:30-36`).

**Тесты** (`NotificationParserTest.kt`, рядом с `:280`):
- `alfaBusinessIncomingFromCompany` — текст из пункта (title null и «Альфа-Бизнес»): IN, amount 322581.00 RUB, balance 325218.58, counterparty `ООО "Бета"`.
- `alfaBusinessIncomingSenderAccountIsNotOurs` — маска/счёт результата = 4863, не 1007 (тем же способом, что `:258`).
- `alfaBusinessIncomingWithoutOwnMaskIsNull` — «Поступило 100.00 от ООО "Бета". Остаток 5.00» → null (консервативность).
- Существующие `:245-290` остаются зелёными.

**Критерии:** тесты зелёные; парсер не принимает «Поступило» без `счет *dddd` и без знака валюты. Device-check: «Восстановить из журнала» → запись 03.11 17:31 разобрана.

---

## B-123

**Цель:** если в тексте есть время/дата операции — это время операции (лента, история, выбор итогового баланса); три Альфа-уведомления из пункта дают баланс 413 684,62 ₽ при любом порядке прихода.

**Решения:**
- Время прихода (`postedAt` журнала) остаётся идентичностью события: `eventHash` (`RecordNotification.kt:45`, `RepairFromJournal.kt:68`) и дедуп (`:50`) — **без изменений** (иначе после перепарсинга сменятся id и задвоятся операции).
- Время операции кладётся в существующее `Transaction.postedAt` и в баланс — без новой колонки и без миграции (поле и так «время операции» для ленты/истории).
- Защита от бреда: время из текста принимается, только если оно в окне `[arrival − 3 сут, arrival + 10 мин]`, иначе берётся время прихода.

**Шаги:**
1. Домен, `domain/parser/NotificationParser.kt:16-26`: в `ParsedOp` поле `opTime: OpTime? = null`; новый `domain/model/OpTime.kt`: `data class OpTime(val day: Int?, val month: Int?, val year: Int?, val hour: Int, val minute: Int)`.
2. Домен, новый `domain/parser/OpTimeParser.kt` (`object OpTimeParser { fun find(text: String): OpTime? }`), форматы:
   - Альфа «3 ноября 17:39» (месяц словом в родительном падеже, `в`/запятая между — опционально);
   - «03.11 17:39», «03.11.26 17:39», «03.11.2026 в 17:39»;
   - только время «17:39» / «в 17:39», если в тексте ровно одно время.
   - Не путать с `п/п №92712 от 27.10.26` (дата без времени → null; дата документа — не время операции). Несколько разных времён → null.
   - Вызов в `parse` (`:169`) после определения op: `op.copy(opTime = OpTimeParser.find(blob))`.
3. Домен, новый `domain/model/OperationTime.kt`: `object OperationTime { fun resolve(op: OpTime?, arrivedAt: Long, zone: ZoneId): Long }`:
   - нет `op` → `arrivedAt`;
   - нет года → год прихода; если вышло > arrivedAt + 10 мин — год −1 (через Новый год);
   - нет даты → дата прихода; если > arrivedAt + 10 мин — день −1 (полночь: «23:58», пришло в 00:01);
   - вне окна `[arrivedAt − 3 сут, arrivedAt + 10 мин]` → `arrivedAt`.
   - `java.time` допустим (JDK, не Android).
4. Use-case `domain/usecase/RecordNotification.kt`: `val at = OperationTime.resolve(op.opTime, postedAt, zone)` после парса; `ledger.upsertAccountBalance(..., postedAt = at)` (`:72-79`) и `Transaction(..., postedAt = at)` (`:80-92`). `zone` — параметр конструктора (`ZoneId`), в `AppContainer` — `ZoneId.systemDefault()`.
5. `domain/usecase/RepairFromJournal.kt`: `:58` `RepairEvent(at, …)` с `at = OperationTime.resolve(op.opTime, e.postedAt, zone)`; `:80` `Transaction.postedAt = at`; `:68` `eventHash` — по-прежнему `e.postedAt`. Тот же `zone` через конструктор.
6. `domain/model/RepairBase.kt:30` — без изменений по коду (`maxByOrNull { postedAt }` теперь получает время операции); при равенстве времени — последнее по приходу (добавить в `RepairEvent` `arrivedAt` и `thenBy`).
7. `domain/model/RunningBalance.apply` и `LedgerDao.kt:35-36` `updateBalanceIfNewer` — логика уже «новее побеждает» (`postedAt >= checkedAt`, `updatedAt <= :postedAt`); получив время операции, позднее пришедшее, но более раннее уведомление баланс не перетрёт (ветка `:33`). Код не меняется; закрепить тестом.
8. DI: `di/AppContainer` — передать `ZoneId.systemDefault()` в `RecordNotification` и `RepairFromJournal`.
9. UI не меняется (журнал `LogScreen` по-прежнему показывает время прихода).

**Тесты:**
- `OpTimeParserTest`: `alfaDayMonthWordTime` («3 ноября 17:39» → 3/11/–/17:39); `numericDateTime` («03.11.2026 в 17:39»); `timeOnly`; `paymentOrderDateIsNotOpTime` (текст `alfa` из `NotificationParserTest:245` → null); `twoDifferentTimesIsNull`; `amountWithColonNotTime` — суммы «1 045», «281 764,12» не дают время.
- `OperationTimeTest`: `noTimeUsesArrival`; `timeOnlyBeforeMidnight` (23:58, пришло 00:01 → вчера 23:58); `dateWithoutYearAcrossNewYear` (31 декабря 23:59, пришло 1 января 00:02 → прошлый год); `farFromArrivalFallsBack` (дата за неделю → arrival).
- `RunningBalanceTest`: `laterArrivalWithEarlierOpTimeDoesNotOverwrite` — три Альфа-операции в порядке прихода 17:43(413684.62), 17:39(1764.12), 17:42(281764.12) с временами операции → итог 413 684,62.
- `RecordNotificationTest`: `balanceFromLatestOperationTime` — те же три текста, приход одним `postedAt` (03.11 17:43) в перемешанном порядке → баланс счёта 413 684,62, `Transaction.postedAt` = 17:39/17:42/17:43.
- `RepairFromJournalTest`: `repairPicksBalanceByOperationTime` — тот же набор → база 413 684,62, id событий не изменились.
- `RepairBaseTest`: `tieOnOpTimeBrokenByArrival`.

**Критерии:** тесты и сборка зелёные; `eventHash` и дедуп не зависят от времени из текста; домен без `android.*`. Device-check: «Восстановить из журнала» на трёх уведомлениях → баланс 413 684,62 ₽, лента в порядке 17:43, 17:42, 17:39.

---

## B-121

**Цель:** ручная правка валюты (и с вкладки «Валюта», и на банковском валютном счёте) видна в истории отдельной строкой «Правка остатка · вручную» (макет вариант 1, `docs/mockups/cycle-17/B-121/index.html`). Тап открывает шторку с «Изменить»/«Удалить», удаление можно отменить снекбаром «Вернуть». После удаления график и суммы за прошлые дни пересчитаны, текущие остатки не тронуты.

**Два источника правки (по коду):**
- **Вкладка «Валюта» без банка.** Отдельного «остатка» у позиции нет: позиция = сумма `fx_operations` BUY/SELL (`FxEntities.kt:8`, `FxRepository`). Правка — это добавление или изменение операции (`HistoryOperations.kt:300`, `CurrencyDetailScreen.kt:147` `edit:`). Изменение старой операции не пересчитывает уже записанные дни `capital_days`, поэтому между замороженными точками и сегодняшним днём возникает ступенька. `CapitalEvents.kt:364-398` выводит её как ручной остаток (`manualMinor`, ref `null`/`Balance`) — это и есть событие −811 107,46 от 27.10.
- **Банковский валютный счёт.** `setBalance` (`HistoryOperations.kt:142-147`) → `accounts.balanceMinor`; в истории тоже появляется выведенный `manualMinor`, ref `HistoryRef.Balance` (`CapitalEvents.kt:327,342`).

В обоих случаях отдельной записи правки нет, есть только ступенька в `capital_days`. Значит, «удалить правку» = убрать ступеньку из истории: сдвинуть прошлые дневные точки части CURRENCY (и total) на `manualMinor` события. Сдвигаем дни **от начала ступени до дня перед событием**; начало ступени — день после ближайшего предыдущего ручного события той же части с противоположным знаком, если его нет — первая точка истории. Текущие `accounts`, `fx_operations` и точки с дня события не меняются (требование пункта: текущий остаток не тронут).

**Сущность и миграция — не нужны (выбор):** строка строится из уже выводимого события. Новая таблица правок не покрыла бы уже случившуюся ошибку 27.10: она нигде не записана. «Вернуть» держит снимок заменённых точек в памяти сессии — так же, как `BalanceWas` (`HistoryOperations.kt:66-67`). `version` остаётся 20.

**Шаги:**
1. Домен, `domain/model/CapitalEvents.kt`:
   - новый `HistoryRef.Manual(part: CapitalPart, date: LocalDate, manualMinor: Long, source: ManualSource)`, рядом с `HistoryRef.Balance`;
   - `enum class ManualSource { CURRENCY_POSITION, ACCOUNT }` — `ACCOUNT`, если в шаге менялся остаток валютного счёта (есть кандидат из `HistoryRef.Balance(currency)`), иначе `CURRENCY_POSITION`.
   - В `:317-327` ручная часть **всегда** выделяется в отдельное событие `manualEvent` (`:336-342`) с заголовком «Правка остатка · вручную» и `ref = HistoryRef.Manual(...)`. Строка «Переоценка валюты» остаётся только с курсовой частью (`legs`); если `legs` пуст — переоценки нет.
2. Домен, `domain/model/HistoryRows.kt:127-155`: ручные события **не сворачиваются** в месячную строку переоценки. Каждое — своя строка «Правка остатка · вручную» с её `ref` (`:150-152` заменить; подпись группы `:180` не трогать).
3. Домен, новый `domain/model/ManualUndo.kt`, `object ManualUndo`:
   - `fun range(points, ref, events): ClosedRange<LocalDate>?` — дни ступени по правилу выше;
   - `fun shift(points, ref, byMinor: Long): List<CapitalPoint>` — возвращает только изменённые точки диапазона, у которых part и total сдвинуты на `byMinor`.
   - Удалить = `shift(.., ref.manualMinor)`. Изменить на новую величину `v` = `shift(.., ref.manualMinor - v)`, тогда в истории остаётся правка `v`.
4. Репозиторий истории капитала (интерфейс в `domain/repository`, отдающий `CapitalPoint`; реализация на `CapitalSnapshotDao`, `CapitalSnapshotEntity.kt:23`): добавить `suspend fun replaceDays(points: List<CapitalPoint>)` → `upsertAll(points.map { it.toEntity() })` (`:29,54`). Схема Room не меняется.
5. Use-case `domain/usecase/HistoryOperations.kt`:
   - `class ManualWas(points: List<CapitalPoint>)` — снимок для «Вернуть»;
   - `suspend fun removeManual(ref): ManualWas`;
   - `suspend fun editManual(ref, newMinor): ManualWas`;
   - `suspend fun restoreManual(was)` → `replaceDays(was.points)`;
   - `delete` (`:243`) получает ветку `is HistoryRef.Manual -> removeManual`; `preview` (`:163-196`) показывает части до/после; `check` (`:199-212`) проверяет, что диапазон не пуст.
   - Ветки `HistoryRef.Balance` не трогать.
6. UI, `ui/history/OperationSheet.kt:78-79`:
   - `HistoryRef.Manual` → новая шторка `ManualEditFlow` (рядом с `BalanceFlow.kt`, по макету варианта 1): сумма правки (`AmountText`), дата, источник («Валюта» / название счёта), кнопки «Изменить» (поле суммы как в `BalanceFlow`) и «Удалить».
   - После удаления — снекбар «Вернуть» → `restoreManual`, тем же механизмом, что «Вернуть» в F-55 (`HistoryOperations.kt:156-159`).
   - `HistoryRef.Balance` → `BalanceFlow`, как раньше. Компоненты — из `ui/common` (как в макете, новых нет).
7. `ui/history/CapitalHistoryViewModel.kt` (около `:156`): `removeManual`/`editManual`/`restoreManual`, последний `ManualWas` хранится в VM. Точки перерисует Flow.
8. DI/Factory/маршрут — без изменений.

**Тесты:**
- `ManualUndoTest`: `shiftsDaysBeforeEventByManualPart` (точки 20–26.10 по 1,62 млн, 27.10 — 0,81 млн, событие −811 107,46 → дни 20–26.10 стали 0,81 млн, total сдвинут так же, 27.10 и позже не тронуты); `stopsAtOppositeManualStep` (была +811k ступень 15.10 → сдвиг только 15–26.10, до 15.10 нетронуто); `otherPartsUntouched`; `noPointsBeforeIsEmpty`.
- `ManualUndoTest`: `editShiftsByDifference` (правка −811k → −100k: сдвиг на −711k).
- `CapitalEventsTest`: `manualRemainderIsSeparateManualEvent` (обновить ожидания `:94`, `:111`, `:251-268`: отдельное событие «Правка остатка · вручную» с `HistoryRef.Manual`, «Переоценка валюты» только с курсовой частью); `fxPositionEditSourceIsCurrencyPosition` (шаг без смены остатка счёта, ops изменены → `CURRENCY_POSITION`); `accountEditSourceIsAccount`.
- `HistoryRowsTest` (`:137-174`): ручные события не сворачиваются, по строке на каждое.
- `HistoryOperationsTest`: `removeManualKeepsAccountsAndFxOps` (фейки: `accounts`/`fx_operations` не тронуты, `replaceDays` получил ожидаемые точки); `restoreManualReturnsOriginalPoints`.

**Критерии:** сборка и тесты зелёные; инварианты: суммы только в копейках (Long), удаление правки не трогает `accounts`, сегодняшняя точка и точки после события неизменны. Device-check: в «Капитал → История → Валюта» 27.10 видна строка «Правка остатка · вручную −811 107,46 ₽», тап открывает шторку с «Изменить»/«Удалить», а не «Остаток · BSB Bank»; «Удалить» → снекбар «Вернуть» работает; после удаления ступенька 26–27.10 исчезает, ранние дни ≈0,81 млн, остаток BSB Bank 91,28 $ прежний.
