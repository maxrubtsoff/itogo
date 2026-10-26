# Планы цикла 11

Планы `architect`-а по рядовым пунктам. Пути относительно `app/src/main/java/ru/maxrubtsoff/bankagg/`.

## B-69. Картинка банка и подписки не сохраняется / не отображается

**Цель:** выбранная картинка банка и подписки видна сразу после «Сохранить» и после перезапуска.

### Что цело
- Подписка: `SubscriptionScreens.kt:105` → `:168-176` → `SubscriptionsViewModel.saveWithPicture` (`:102-111`) → `SubscriptionEntity.toEntity` (`:80`) → колонка `logoFile` (миграция `AppDatabase.kt:73`, v16) → `toDomain` (`:70-73`) → `BrandAvatar(...)`.
- Банк: `BankEditorScreen.kt:47` → `BanksViewModel.saveOwn` (`:124-136`) → `BankStore.update/add` → `write` → `toJson` → `BankDirectory.replace` → `BankIcon` (`BankIcon.kt:30-41`).
- Рендер: `rememberPicture` → `BankIconCache.loadPicture` (`data/icons/BankIconCache.kt:51-57`) → `decodeScaled`.

### Найденные дефекты и подозреваемые
1. **Главный подозреваемый — `LogoMaker.make` (`data/icons/LogoMaker.kt:23-47`) молча возвращает `null`.** Оба VM пишут результат прямо в `logoFile`. `null` при неудачном `openInputStream(uri)` или `decodeStream` → картинка теряется без ошибки. Подписка ещё и затирает старое значение.
2. **Дефект `BankStore.write` (`:86`).** `b != BankBrand.known.firstOrNull { it.key == b.key }` всегда `false` для известного банка (`BankBrand.equals` только по ключу). Правки известного/CBR-банка не попадают в `KEY_DEFS`. Для «своих» (`user:`) не мешает.
3. **Дублированная логика `when` в двух VM** (`BanksViewModel.kt:127-131`, `SubscriptionsViewModel.kt:104-108`) — вынести чистой функцией.
4. Проверить: не теряет ли `decor` перезапись в `SubscriptionRepositoryImpl.save` и `Subscriptions.sync/fromFinding`.

### Шаги

**Шаг 0 (coder, до правок): воспроизвести и локализовать.**
- Временный `Log.d("B69", …)` в трёх точках: результат `logos.make(...)` (путь или `null`, `File.exists()`); итог `logoFile` перед сохранением; `path` и `Picture` в `rememberPicture` (`BrandAvatar.kt:82`).
- Прогнать на эмуляторе: банк (свой) и подписка, картинка из галереи.
- Если `make` вернул `null` — обернуть в `runCatching{}.onFailure { Log.w("LogoMaker", it) }`. Логи убрать перед коммитом.

**1. Домен.** Новый чистый `domain/model/BrandDecor.kt` рядом с классом:
```
LogoChoice.resolve(usePicture: Boolean, made: String?, hadPicked: Boolean, existing: String?): String?
```
- `!usePicture` → `null`.
- `hadPicked && made != null` → `made`.
- `hadPicked && made == null` → `existing` (не теряем прежнее).
- Иначе → `existing`.

`BankBrand.equals` не трогаем.

**2. Data.**
- `BankStore.kt:86`: содержательное сравнение. Банк в `changed`, если `logoFile != null` или отличаются `letters`/`primaryColor`/`textColor`/`title` от записи `known`; для `user:`/CBR — всегда. Приватная функция, без правки `equals`.
- `LogoMaker.kt:23-47`: `runCatching` + `Log.w`, при сбое `null`.
- Room/миграция/версия — без изменений, v16.

**3. UI (VM).**
- `BanksViewModel.kt:127-131`, `SubscriptionsViewModel.kt:104-108`: через `LogoChoice.resolve(...)`.
- Если `make` вернул `null` при выбранной картинке — fallback на `existing` и `Log.w`. UI-сообщение опционально.
- Фабрика/DI не меняются.

**4. Экраны.** Если лог укажет на `BankIconCache` — чистить `asked` при `bitmap == null` (`:52`), иначе без правок.

### Миграция
Не нужна.

### Тесты (JVM, `app/src/test/.../domain/`)
- `LogoChoiceTest.lettersModeDropsLogo` — `usePicture=false` → `null`.
- `LogoChoiceTest.newPictureWins` — `made="x"`, `existing="y"` → `"x"`.
- `LogoChoiceTest.failedPictureKeepsExisting` — `made=null`, `existing="y"` → `"y"`.
- `LogoChoiceTest.noPickedKeepsExisting` — не выбрана, `existing="y"` → `"y"`; `null` → `null`.
- `SubscriptionEntityMapperTest.logoFileRoundTrip` — `logoFile` сохраняется через `toEntity().toDomain()`.
- `BankBrandDecorTest.withDecorKeepsLogo` — `withDecor` сохраняет `logoFile`.

### Критерии
- `./gradlew testDebugUnitTest assembleDebug` зелёные. Домен без `android.*`. `DesignSystemGuardTest` зелёный.
- На эмуляторе: свой банк с картинкой → видна в списке и карточке; редактирование без смены — остаётся; подписка с картинкой — та же проверка; перезапуск — картинки на месте; известный банк со сменой картинки — после перезапуска сохраняется; «Картинка → Буквы» — картинка убирается.

### Файлы для coder
- `domain/model/BrandDecor.kt` (новый)
- `data/settings/BankStore.kt:86`
- `data/icons/LogoMaker.kt:23-47`
- `data/icons/BankIconCache.kt:51-57` (условно)
- `ui/settings/BanksViewModel.kt:124-136`
- `ui/subscriptions/SubscriptionsViewModel.kt:102-111`

## Пачка «История капитала» (F-42 / F-44 / B-73 / F-41)

**Порядок:** F-42 → F-44 → B-73 → F-41. Один коммит на пункт. Все четыре правят `CapitalHistoryScreen.kt` и `CapitalEvents.kt`. Новые поля `HistoryEvent` — с дефолтами.

**Room/миграции:** не меняется, v16. Курсы — из `RatesRepository`.

**Инварианты:** деньги в копейках/`Money`; курс — `Long rubPerUnitMicro` или `BigDecimal` через `FxMath`, без `Double`; домен без `android.*`; подписи формирует домен.

**Зависимость от F-45/F-39:** если дизайн-макет перекроет верхние ряды и структуру ленты — брать итоговую компоновку оттуда. Источник выбранного раздела — `vm.part`.

### F-42. Причина переоценки валюты

Домен (`CapitalEvents.kt`):
- `RevaluationLeg(currency, fromRateMicro, toRateMicro, qtyMinor, effectMinor)`.
- `RevaluationReason(legs, manualMinor)`.
- `HistoryEvent.reason: RevaluationReason? = null`.
- `build(..., rates: List<FxRate> = emptyList())`.
- В `moved` для переоценки:
  1. Остаток валюты на `prev.date` — сумма `fxOps` до даты (BUY+/SELL−).
  2. Курс на `prev/cur.date` — последний из `rates`.
  3. `effect = FxMath.rubOf(qty, toRate) - FxMath.rubOf(qty, fromRate)`.
  4. `manualMinor = revalued - Σ effect`; порог `max(100, 1%)` → 0.
  5. Валюты без обоих курсов → в `manualMinor`.
- `RevaluationText.subtitle(reason)`, `formatRate(micro)` — `BigDecimal` HALF_UP 2 знака.
- Формат: одна валюта «USD 91,20 → 92,50 ₽ · 1 200 $»; несколько — «Курс USD, EUR»; с `manualMinor` — «· остаток изменили вручную»; без ног — «Курс или остаток изменились».

VM: `CapitalHistoryViewModel` получает `rates: RatesRepository`, подписка в `combine`, передача в `build`. `ViewModelFactory.kt:36` — `container.ratesRepository`.

UI: `EventRow` при `reason` — `maxLines = 2`.

Тесты: `revaluationExplainedByRate`, `revaluationWithManualRemainder`, `revaluationWithoutRatesFallsBackToManual`, `revaluationTwoCurrencies`, `rateFormat`, `dealt` без двойного учёта.

### F-44. Тап по операции ведёт к объекту

Домен (`CapitalEvents.kt`):
- `enum HistoryKind { TRANSACTION, FX_DEAL, DEPOSIT_OPEN, DEPOSIT_TOPUP, DEPOSIT_CLOSE, INTEREST, REVALUATION, CASH }`.
- `sealed interface HistoryTarget { Deposit(id); Currency(code); Section(part: CapitalPart) }`.
- `HistoryEvent.kind = TRANSACTION`, `target: HistoryTarget? = null`.

| Событие | kind | target |
|---|---|---|
| Транзакция ₽ | TRANSACTION | `Section(ACCOUNTS)` |
| Транзакция валюта | TRANSACTION | `Currency(code)` |
| Сделка валюты | FX_DEAL | `Currency(code)` |
| Открытие/пополнение/закрытие вклада | DEPOSIT_* | `Deposit(id)` |
| Проценты | INTEREST | `Deposit(id)` если живой один, иначе `Section(DEPOSITS)` |
| Переоценка | REVALUATION | `Currency` с макс. `|effect|`, иначе `Section(CURRENCY)` |
| Наличные | CASH | `Section(CASH)` |

- Отдельного `Account(id)` не делаем.
- Подписки — N/A (их нет в `CapitalEvents`).
- `CapitalEvents.isAlive(target, deposits)`.

VM: `HistoryUi.aliveDeposits: Set<String>` из `Inputs.deps`.

UI:
- `EventRow(onClick)`, `Row.clickable`, шеврон если `target != null`.
- `CapitalHistoryScreen(..., onOpenTarget: (HistoryTarget) -> Unit)`.
- Живой — `onOpenTarget`; удалённый — локальный `SlitokSheet` с деталями.
- `MainActivity.kt:805` — маршруты: `Deposit` → `Routes.depositDetail(id)`; `Currency` → `Routes.currency(code)`; `Section(p)` → `Routes.capital(p)`.

Тесты: `depositEventsPointToDeposit`, `rubTransactionPointsToAccounts`, `fxTransactionPointsToCurrency`, `fxDealPointsToCurrency`, `interestSingleDepositTargetsDeposit`, `interestManyDepositsTargetsSection`, `revaluationTargetsLargestLeg`, `isAliveFalseForDeletedDeposit`.

### B-73. Свайп по истории капитала

Решение: свайпом переключаются **разделы** («Всего» + `kept`); период — плашками, не свайпается (иначе спорит с жестом графика).

Компонент: `ui/common/SectionPager` (`:31`). Эталон — `ui/capital/CapitalScreen.kt:119-180`.

VM:
- `state` → `HistoryScreenUi(period, kept, today, pages: List<HistoryUi>, aliveDeposits)`, `pages[0] = Всего`.
- `all = build(...)` один раз; `series/change/within` — на каждую страницу.
- `_part`/`setPart`/`lastPart`/`setByParts` — пока без изменений.
- Синхронизация в UI: `snapshotFlow { pagerState.settledPage } → vm.setPart`; `LaunchedEffect(u.part) → animateScrollToPage`.

UI:
- Вертикальный скролл на колонке экрана; пейджер внутри.
- Содержимое `HistoryContent` (с графика) — в страницу пейджера.
- `CapitalChart` поглощает горизонтальный драг — свайп только вне графика.

Тесты (домен): `sections(kept) = [null] + kept`; `pageOf(part, sections)` c `-1 → 0`; `sectionsStartWithTotal`, `pageOfUnknownPartIsTotal`.

### F-41. Долгое нажатие → операции дня

Состояние в экране (не в VM):
- `rememberSaveable { mutableStateOf<Long?>(null) }` — epoch day.
- Индекс = `series.indexOfFirst { it.date.toEpochDay() == picked }`.
- Общий `ScrollState` — в `HistoryContent`.

Сброс: смена периода/раздела; `SlitokChip("Сбросить")` рядом с подписью дня; касание другой точки — переставляет; отпускание — не сбрасывает.

`CapitalChart.kt`:
- Контракт управляемый: `selected: Int?`, `onScrub: (Int) -> Unit`, `onRelease: (Int) -> Unit`.
- Убрать `set(null)` в `:68`, `:73`, `:74`.
- `onPress → onScrub(i)`, `tryAwaitRelease → onRelease(i)`.
- Драг: `onDragStart`/`onHorizontalDrag → onScrub`, `onDragEnd → onRelease(last)`.
- Внутренний `scrub` удалить.
- Обновить KDoc, `docs/design-system/README.md`, `ShowcaseScreen.kt:300`.

Автоскролл:
- `onScrub` обновляет `picked`; hero-сумма и дата — по `picked`.
- Скролл ленты — только по `onRelease`.
- `var scrollTick`; `onRelease` увеличивает.
- `Events` (`:160`): фильтр по дню (`:161`) убрать.
- `Caption` дня (`:196`) — `Slitok.Brass2`.
- `BringIntoViewRequester` в `remember`-мапе `date → requester`.
- `LaunchedEffect(scrollTick)` → `bringIntoView()` на запросчике ближайшего дня.
- Годовой/общий (`u.months`, `:168`): нужный месяц в `open` до скролла; `open` поднять в `HistoryContent`.

Домен: `CapitalEvents.nearestDay(events, date): LocalDate?` — ближайший день с событиями, при равном — ранний; пусто → `null`.

Тесты: `nearestDayExactMatch`, `nearestDayPicksClosest`, `nearestDayTieGoesToEarlier`, `nearestDayEmptyIsNull`, `monthKeyOfNearestDay`.

### Общие критерии

- `./gradlew testDebugUnitTest assembleDebug` зелёные.
- `DesignSystemGuardTest` не ослаблен.
- Домен без `android.*`, деньги в копейках, миграций нет.
- На устройстве: свайп разделов; scrub+release с автоскроллом в «Неделя» и «Год»; тап по вкладу/валюте/счёту; шторка для удалённого вклада; строка переоценки с курсом.

## B-70. Редактирование карточки после переноса скрина

**Цель.** На «Проверьте вклады» у любой карточки, в том числе после переноса скрина, есть «Изменить». Форма открывается с данными всей группы и со всеми её скринами. У кэшбека форма «Исправить» тоже получает все скрины группы, а не один.

**Причина.**
- `DepositsViewModel.collapseGroup` (`ui/deposits/DepositsViewModel.kt:218`) и `CashbackViewModel.collapseGroup` (`ui/cashback/CashbackViewModel.kt:234`) пишут данные группы в первый скрин и удаляют остальные (`drop(1).forEach { shotRepo.delete }`). Потом открывают превью одного `shotId`. Остальные скрины теряются.
- Кнопка у вклада (`DepositBatchScreen.kt:243`) показана только при `!group.isSavable`. У готового вклада кнопки нет.
- `BankCashbackEditScreen` в этом не виноват — он правит сохранённый кэшбек банка и месяца, про скрины не знает. «Исправить» ведёт в `ShotPreviewScreen`.

**Решение.** Форма получает список `shotIds` группы, первый из них основной. Слияние считает чистая доменная `merge`, в БД до сохранения ничего не пишется. `DepositBatch.merge` (`domain/model/DepositBatch.kt:46`) и `CashbackBatch.merge` (`CashbackBatch.kt:28`) уже есть.

**Шаги по слоям**

1. **Домен.** Менять нечего, `merge(items)` уже возвращает `Group(ids, brand, draft/lines, monthKey)`. Покрыть тестами.
2. **Репозитории, Room, миграция.** Не меняются.
3. **ViewModel.**
   - `DepositsViewModel.kt:218`: удалить `collapseGroup`.
   - `DepositsViewModel.kt:256`, `saveDeposit`: параметр `shotIdToDiscard: String?` → `shotIds: List<String> = emptyList()`. На `:284` вызвать `shotRepo.setDepositId(shotIds, deposit.id)`.
   - `observeShots(ids)` уже есть (`:176`).
   - `CashbackViewModel.kt:234`: удалить `collapseGroup`. `discardShots(ids)` (`:221`) уже есть.
4. **Навигация** (`ui/MainActivity.kt`).
   - `Routes.CASHBACK_SHOT` (`:202`) и `Routes.DEPOSITS_SHOT` (`:237`) получают необязательный query `with` (остальные id через запятую). `navArgument.defaultValue = ""`.
   - Хелперы `cashbackShot(shotId, with = emptyList())` и `depositShot(shotId, with = emptyList())`.
   - В `composable` на `:828` и `:859` разобрать `with`, передать в экран `listOf(shotId) + with`.
5. **Экраны.**
   - `DepositBatchScreen.kt:243-245`: кнопка показывается всегда, подпись «Изменить». `onFill` (`:157`) → `onEditGroup: (List<String>) -> Unit`. `onOpenShot` оставить для миниатюр. В `MainActivity.kt:867` — `nav.navigate(Routes.depositShot(ids.first(), ids.drop(1)))`.
   - `CashbackBatchScreen.kt:173,293`: «Исправить» вызывает `onEditGroup(g.ids)`. Подключить в `MainActivity.kt:832-839`.
   - `DepositShotPreviewScreen.kt:42`: сигнатура `(vm, shotIds, onBack)`. `vm.observeShots(shotIds)`, инициализация формы — из `DepositBatch.merge(items)`. `rememberDepositFormState(key = shotIds.joinToString())`. Под `ShotHeader` показать `ShotThumbs` (`ui/common/ShotBatchUi.kt:75`) если скринов > 1. Сохранение — `shotIds = shotIds`.
   - `ShotPreviewScreen.kt:48` (кэшбек): то же. Строки/месяц/банк из `CashbackBatch.merge(items)`. Миниатюры всех скринов. Сохранение: `rememberSaveCashback(vm) { vm.discardShots(shotIds); leave() }`.
   - Одиночные вызовы (`DepositAddScreen`, `CashbackScreen`) — `listOf(id)`.
   - Если скринов не осталось — `leave()`, как сейчас.
6. `DepositDetailScreen.kt:126` «Изменить» → `DepositManualScreen` не трогаем.
7. Дизайн-система не меняется.

**Миграция.** Не требуется.

**Тесты** (`app/src/test/java/ru/maxrubtsoff/bankagg/domain/`):
- `mergeOfMovedShotKeepsBothIds` — два скрина, `Group.ids` содержит оба, `draft` объединён.
- `mergeSingleItemEqualsItsDraft` — один `Item` → без изменений.
- `cashbackMergeKeepsLinesFromAllShots` — строки обоих без дублей, `ids` оба.
- `layoutMoveThenReconcileKeepsAllIds`.

**Критерии.**
- `./gradlew testDebugUnitTest assembleDebug` зелёные.
- Инварианты: при открытии формы из батча скрины не удаляются, в БД ничего не пишется. При сохранении все `shotIds` привязываются к вкладу через `setDepositId`; у кэшбека — `discardShots`.
- На устройстве: 2 скрина вклада → перенести → «Изменить» → форма с объединёнными полями и двумя миниатюрами → «Сохранить» → `DepositDetailScreen` без блока скринов (U-66), в БД `deposit_shots` этих id нет. То же для кэшбека (у кэшбека своё поведение).

**Пересечение с U-66:** после U-66 блок «Скрины · N» в карточке вклада удалён, при сохранении скрины удаляются (`shotRepo.delete` вместо `setDepositId`). В B-70 — то же: `vm.saveDeposit(shotIds = shotIds)` в итоге вызывает `delete`.

## Пачка дизайн-пунктов — порядок

**Порядок в группе «истории»:** F-42 → F-44 → **B-73+F-45 (один коммит)** → F-39 → F-43 → F-40+B-72 → F-41. U-66 независим.

## F-45 + B-73. Пересобранная шапка истории

**Цель:** один ряд `PagerTabs` разделов (`Всё · <kept>`), листается свайпом. Период — компактные плашки под графиком в герое. Режим «Всего / По категориям» удалён.

**Домен** (`domain/model/CapitalHistory.kt`): `sections(kept) = [null] + kept`; `pageOf(part, sections)` (−1 → 0); `partOf(page, sections)`.

**UI (VM, `CapitalHistoryViewModel.kt`):**
- Удалить `setByParts` (`:79-81`), `lastPart` (`:63,:70,:75`) если больше не нужны.
- `HistoryUi` → `HistoryScreenUi(period, kept, today, pages: List<HistoryUi>, aliveDeposits)`, `pages[0] = Всё`.
- `build` один раз, `series/change/within` — на каждую страницу.
- `setPart`, `open` остаются.

**UI (`CapitalHistoryScreen.kt`):**
- `:97-107` → `PagerTabs(labels = sections.map { it?.label ?: "Всё" }, pager, onSelect = animateScrollToPage)`.
- Ряд всегда один; при `kept.size == 1` — без пейджера (один экран).
- Ниже `SectionPager` (`ui/common/SectionPager.kt:31`), страница = `HistoryContent`.
- `snapshotFlow { pager.settledPage } → vm.setPart`; `LaunchedEffect(u.part) → animateScrollToPage`.
- `verticalScroll` на внешней колонке (`:78`).

**PeriodChips** (`ui/common/PeriodChips.kt:30`): параметр `compact: Boolean = false`; вставить под `CapitalChart` (`:137`) в панель героя. Сброс `scrub` при `onPick`. Обновить `docs/design-system/README.md` и `ShowcaseScreen.kt`.

**Удалить:** `modeTabs`/`PagerTabsStatic` импорт, `SlitokChip` импорт — если не используются.

**Тесты:** `sectionsStartWithTotal`, `pageOfUnknownPartIsTotal`, `pageOfKnownPart`, `partOfPageRoundTrip`, `singleKeptStillHasTotalAndOne`.

**Критерии:** один ряд вкладок + период под графиком; нет режима «По категориям»; свайп переключает разделы; вход в историю из «Вклады» открывает «Вклады»; смена периода сохраняется при смене раздела.

## F-39. Группировка ленты

**Цель:** своих переводов нет; списания за день — одна раскрывающаяся группа; переводы людям — отдельная группа.

**Домен** (`CapitalEvents.kt`):
- `HistoryKind` расширить (вместе с F-44): `INCOME`, `SPEND`, `OWN_TRANSFER`, `TRANSFER`, остальные уже есть. `TRANSACTION` остаётся для `UNKNOWN`.
- `build` (`:42-51`): `kind` по `t.kind`: INCOME → `INCOME`, EXPENSE → `SPEND`, TRANSFER → `TRANSFER`, UNKNOWN → `TRANSACTION`.
- Приватная `ownTransferIds(transactions, accounts): Set<String>`: валюта RUB, `kind == TRANSFER`, один день, суммы противоположных знаков и равны по модулю, разные `accountId`, оба в `accounts`. Пары жадно 1:1. Обе ноги выкидываются до вывода.
- `sealed interface HistoryRow { Single(event); Group(id, date, kind, count, totalMinor, children) }`; `id = "${date.toEpochDay()}:${kind.name}"`.
- `CapitalEvents.group(events): List<HistoryRow>`: на каждый день группируются `SPEND` и `TRANSFER` (`count >= 2`); иначе `Single`. Группа встаёт на место первого элемента.

**UI (VM):** `HistoryUi.rows: List<HistoryRow>` для дневного режима; `months` остаётся из несгруппированного; группировка в раскрытом месяце — тоже `group()`.

**UI (`CapitalHistoryScreen.kt:195-203`, `:186-190`):**
- Рендер по `HistoryRow`. `Group` — строка-заголовок «Списания · N» / «Переводы · N» + сумма + шеврон; раскрытие по тапу.
- `rememberSaveable { mutableStateOf<List<String>>(emptyList()) }` для раскрытых `Group.id`.
- Дети рисуются `EventRow` с отступом. F-44: группа не кликабельна как цель, только раскрывается.

**Тесты:** `ownTransferPairIsHidden`, `transferToStrangerStays`, `differentDayTransferNotPaired`, `differentAmountNotPaired`, `sameAccountNotPaired`, `twoEqualPairsPairedOneToOne`, `spendGroupedPerDay`, `singleSpendStaysSingle`, `spendAndTransferGroupedSeparately`, `incomeNeverGrouped`, `groupIdIsStableByDayAndKind`.

**Критерии:** свой перевод не виден (капитал не меняется, `capital_days` не трогаем); тап по группе — раскрыть/свернуть; поворот сохраняет раскрытые; F-44 остаётся по детям.

## F-43 + Sparkline Y-span

**Цель:** мини-линия и график не раздувают копеечные колебания — минимум Y 2 % от среднего.

**Домен** (`domain/model/ChartScale.kt`):
```
object ChartScale { fun range(points: List<Long>, minSpanBp: Int = 200): Pair<Long, Long> }
```
Целочисленно: `min`, `max`, `ref = abs(среднее)`, `minSpan = max(1, ref * minSpanBp / 10_000)`; если `max - min >= minSpan` → `(min, max)`, иначе центрировать `(mid - minSpan/2, mid + (minSpan - minSpan/2))`. Пусто → `(0, 1)`. Без `Double`.

**UI:**
- `ui/common/Sparkline.kt:22-40`: параметр `minSpanFraction = 0.02f`; `lo`/`span` → `ChartScale.range(points, bp)`. Ветка «плоский» (`:33,:37`) уходит.
- `ui/common/CapitalChart.kt:45`: такой же параметр, расчёт `lo`/`span` в `Canvas` ниже `:66` → `ChartScale.range`. KDoc «Y axis starts at the lowest value» убрать. Остальной контракт — F-41.
- Обновить README + `ShowcaseScreen.kt:300`.

**Тесты** (`ChartScaleTest.kt`): `flatSeriesGetsMinSpanCentered`, `smallWiggleExpandsToTwoPercent`, `largeSpanUntouched`, `emptySeriesSafe`, `singlePointSafe`, `negativeAverageUsesAbs`, `spanNeverZero`.

**Критерии:** ряд ±0,1 % выглядит почти плоским; ряд 5 % — как раньше; линия не выходит за холст.

## F-40 + B-72. AmountText.compact + плитки убраны

**Цель:** в герое истории нет плиток; суммы в строках-дельтах не обрезаются многоточием, при нехватке места — «1,77 млн ₽».

**Домен** (`domain/model/MoneyCompact.kt`):
```
object MoneyCompact { fun short(minor: Long, signed: Boolean = false): String }
```
- Модуль в рублях (`minor / 100`) через `BigDecimal`, HALF_UP, 2 знака, хвостовые нули срезать. Запятая.
- < 10 000 ₽ → полная `Money.format()` (compact не нужен).
- ≥ 10 000 → «тыс»; ≥ 1 000 000 → «млн»; ≥ 1 000 000 000 → «млрд».
- Если после округления значение достигло 1000 следующей единицы (999 999 ₽ → «1 млн ₽»).
- Знак: `signed` даёт «+»/настоящий минус (как `signedSum`); перед «₽» — `Money.NBSP`.

**UI:**
- `ui/common/AmountText.kt:33,:53`: параметр `compact: String? = null`. Если полный текст не влез в `maxWidth` при `maxSize` и `compact != null` — показать `compact`; дальше обычное сжатие до `minSize`.
- Проверить `MoneyMask.ranges` (`HiddenAmounts.kt`): regex накрывает «тыс/млн/млрд».
- `ui/common/HiddenAmounts.kt:117` (`MoneyText`): проброс `compact` в `AmountText`.
- `DeltaLine.kt:58-64`: `MoneyText(..., compact = MoneyCompact.short(deltaMinor, signed = true))`; убрать `weight(1f, fill = false)` если период не уезжает на 360 dp.
- `CapitalHistoryScreen.kt:129`: compact для дельты героя (суммовая часть).
- `EventRow`: `MoneyText(signedSum(it), compact = MoneyCompact.short(it, signed = true))`.
- Герой-сумма (`:121`, `Amounts.Hero`) не трогать.
- **F-40:** удалить `Row` трёх `StatTile` (`:142-146`) и `Sum` (`:151-157`), импорт `StatTile` (`:57`). `StatTile` в `ui/common` оставить, если используется где-то ещё — поиск `StatTile(`.
- README + витрина: правило compact.

**Тесты** (`MoneyCompactTest.kt`): `belowTenThousandStaysFull`, `tenThousandIsThousands`, `fortyEightPointThree` (48 300 → «48,3 тыс ₽»), `millionsTwoDigits` (1 770 000 → «1,77 млн ₽»), `billions`, `roundingHalfUp`, `promotesWhenRoundsToThousand` (999 999 → «1 млн ₽»), `signedPositiveAndNegative`, `zeroIsFull`, `maskCoversCompactText`.

**Критерии:** «+1 770 000 ₽ за месяц» на узком экране → «+1,77 млн ₽»; короткие — полностью; в скрытом режиме «пыль» накрывает compact; в герое истории плиток нет; герой-сумма и дельта на месте.

## U-66. Без скринов в карточке + авто-пропуск дубликатов

**Цель:** скринов вклада не храним — после сохранения удаляются. При разборе дубликаты уже сохранённых вкладов отсеиваются автоматически; плашка «N дубликатов пропустили · Вернуть».

**Домен** (`domain/model/DepositMatch.kt`):
```
object DepositMatch { fun find(brand: BankBrand?, draft: DepositDraft, existing: List<Deposit>): Deposit? }
```
Строгое совпадение всех четырёх: банк (`brand.key`), сумма, ставка, срок (обе даты). Любое `null` в драфте → `null`. Нечёткости нет.

**Use-case** (`RecognizeDepositShot.kt`):
- `Draft` (`:22`) получает `duplicateOf: Deposit? = null`.
- `invoke(shot, existing: List<Deposit> = emptyList())` (`:28`).
- При `status == RECOGNIZED` вызывать `DepositMatch.find(...)`. Решение «удалить» — в VM.

**`RuPlural`:** `duplicatesSkipped(n)` → «N дубликат/дубликата/дубликатов».

**Room / миграция:**
- `AppDatabase.kt:19`: `version = 17`. `MIGRATION_16_17` рядом с `MIGRATION_15_16` (`:50`), в `.addMigrations(...)` (`:126`):
  ```sql
  DELETE FROM deposit_shots WHERE depositId IS NOT NULL;
  ```
- Колонка `depositId` остаётся (не отбрасываем).
- `DepositShotDao.setDepositId`, `DepositShotRepository`/`Impl`/sample — оставить (вызовов не будет; чистка — отдельный пункт).

**Файлы картинок на диске:** миграция не трогает файлы. Разовая чистка «картинки без записи в `deposit_shots`» при старте — **следующим коммитом в этом же цикле**, в `AppContainer`/`App.onCreate`: сравнить файлы в `imageStore` dir с `shotRepo.observeAll().first().map { it.imagePath }` и удалить лишние. Назвать коммитом «U-66 часть 2: чистка орфанных картинок скринов».

**VM** (`DepositsViewModel.kt`):
- `:284` `shotIdToDiscard?.let { shotRepo.setDepositId(...) }` → `shotRepo.delete(it)`.
- `:208` в `saveGroups`: `g.ids.forEach { shotRepo.delete(it) }`; коммент B-64 убрать.
- Удалить `attachShots` (`:111`), `addShotTo` (`:116`), `observeDepositShots` (`:119`); `shots` (`:106`) → без фильтра по `depositId`.
- `:292-297` `delete(id)`: убрать `linked`/`finish`.
- Дубликаты: `stash = MutableStateFlow<List<DepositShot>>(emptyList())`; `pendingDuplicates: StateFlow<Int>` (`stash.map { it.size }`, `stateIn`).
- `recognise(shot)` (`:232`): `recognizeShot(shot, repo.observeAll().first())`; если `draft.duplicateOf != null` → в `stash` + `shotRepo.deleteKeepingImage(shot.id)` (картинка жива для «Вернуть»).
- `restoreDuplicates()`: `stash` → `shotRepo.add(...)` каждый со статусом `NEEDS_INPUT` (идут как «Проверьте», не автосохранение). Очистить stash.
- Чистка stash: в `saveGroups` (после `onDone`) и в `onCleared()` — удалить картинки оставшихся.

**Note:** `shotRepo.deleteKeepingImage` может не существовать. Если нет — добавить метод в `DepositShotRepository` (удалить запись, оставить файл) или в `DepositShotDao` + оболочка в репозитории. Это data-слой, не домен.

**UI** (`DepositDetailScreen.kt`): удалить `DepositShotsBlock` (`:182-191`) и вызов (`:153`), `depositShots` (`:92-93`), `addingShot` (`:96`), `pending` (`:97`), `gallery` (`:98-100`), `AddShotSheet` (`:161-176`), лишние импорты. `AddShotTile`/`AddShotSheet` в `ui/common/ShotBatchUi.kt` — оставить если нужны кэшбэку, иначе удалить + README + витрина.

**UI** (`DepositBatchScreen.kt`): сверху, над списком групп (под заголовком `:130`), плашка при `pendingDuplicates > 0`: «N дубликатов пропустили · Вернуть» (`onClick → vm.restoreDuplicates()`). Если очередь пуста — экран не закрывается, показывает плашку и «Новых вкладов нет». Переиспользовать `ui/common/PendingShotsPlate.kt` или новый компонент (решает оркестратор при реализации).

**Тесты:**
- `DepositMatchTest`: `exactMatchFound`, `differentBankNoMatch`, `differentPrincipalNoMatch`, `differentRateNoMatch`, `differentEndDateNoMatch`, `differentStartDateNoMatch`, `missingFieldNoMatch`, `picksTheEqualOneAmongMany`, `emptyExistingNull`.
- `RecognizeDepositShotTest` (если есть фейк `OcrEngine`): `duplicateOnlyWhenRecognized`.
- `RuPluralTest`: `duplicatesSkippedForms`.

**Критерии:**
- В карточке нет блока скринов; после сохранения вклада записей `deposit_shots` с этим скрином нет, файла нет.
- v16 → v17 данные целы, `deposit_shots` с `depositId IS NOT NULL` пусты.
- Дубликат (банк+сумма+ставка+обе даты совпали) не появляется карточкой; плашка; «Вернуть» возвращает как «Проверьте».
- На устройстве: два одинаковых скрина, повторный разбор, возврат, «Новых вкладов нет».

## Общие критерии для всей группы

- `./gradlew testDebugUnitTest assembleDebug` зелёные.
- `DesignSystemGuardTest` не ослаблен.
- Домен без `android.*`, деньги только `Money`/`Long` в копейках.
- Миграция одна: v16 → v17 (U-66), плюс следующий коммит — чистка орфанных картинок.
- `HistoryEvent.kind` и `target` из F-44 используются совместно с F-39.
