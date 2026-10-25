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
- На устройстве: 2 скрина вклада → перенести → «Изменить» → форма с объединёнными полями и двумя миниатюрами → «Сохранить» → `DepositDetailScreen` «Скрины · 2». То же для кэшбека.
