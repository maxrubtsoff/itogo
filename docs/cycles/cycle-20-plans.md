# Планы цикла 20

Пути ниже — от `app/src/main/java/ru/maxrubtsoff/bankagg/` (main) и `app/src/test/java/ru/maxrubtsoff/bankagg/domain/` (тесты). Проверка каждой группы: `./gradlew testDebugUnitTest assembleDebug`. ДИЗАЙН-пунктов нет (см. конец).

## B-130

**Цель:** свой банк после перезапуска приложения остаётся с названием и картинкой во всех списках, а не «Банк»/«?».

**Корневая причина (проверено).** `data/settings/BankStore.kt`: поле `_mine` (стр. 36) инициализируется через `readStored()` → `BankBrand.fromKey(...)` **раньше**, чем `init { BankDirectory.replace(readDefinitions(), ...) }` (стр. 41-43). Kotlin идёт по тексту сверху вниз, поэтому после старта процесса справочник пуст и `fromKey` (`domain/model/BankBrand.kt:127-129`) отдаёт заглушку `BankBrand(key, "Банк", фиолет, letters="?")`. Заглушка попадает в `myBanks` и списки. «Через некоторое время» = перезапуск процесса (в той же сессии `write()` вызывает `replace`, поэтому сразу после добавления всё хорошо). Редактор берёт данные из `defs` в prefs, поэтому там всё на месте.
Усугубляет: любой следующий `write()` (стр. 83-100) сохраняет заглушку в `defs` (`k == null` → «изменён») и затирает настоящее определение. Данные пока целы (редактор их показывает), но порча неминуема.

**Шаги по слоям**
1. Домен, `domain/model/BankBrand.kt`: у `BankBrand` добавить `val isPlaceholder: Boolean` (`letters == "?"` и `title == "Банк"` и `key != UNKNOWN.key`, т.е. то, что строит ветка стр. 129; вынести конструктор заглушки в одну функцию `placeholder(key)` и использовать в обоих местах, чтобы признак не разъехался). Новый чистый объект `domain/model/BankDefinitions.kt`: `fun persistable(banks, known): List<BankBrand>` (логика «изменённые/свои» из `BankStore.write` стр. 87-92, **без заглушек**) и `fun merge(stored: List<BankBrand>, banks: List<BankBrand>, known): List<BankBrand>` (stored + persistable, заглушка не перетирает сохранённое определение).
2. Интерфейс репозитория: без изменений.
3. Use-case: не нужен.
4. Room: не трогаем (банки в SharedPreferences, миграция не нужна).
5. `data/settings/BankStore.kt`: (а) загрузить `defs` и вызвать `BankDirectory.replace(defs, activeKeys = keys из prefs)` **до** вычисления `_mine`: прочитать ключи `mine` отдельно (`readKeys()`), сделать `replace`, потом `_mine = MutableStateFlow(readStored()...)`; блок `init` стр. 41-43 убрать/перенести выше `_mine`. (б) `write()` стр. 87-93 заменить на `BankDefinitions.merge(readDefinitions(), banks, BankBrand.known)`. (в) `migrate()` стр. 57 — `fromKey` там после (а) уже видит справочник.
6. DI: проверить в `di/AppContainer`, что `BankStore` создаётся раньше `BankIconCache.start()` и любых `BankBrand.all`/`fromKey` (иначе справочник пуст и там). Если нет — поднять `by lazy`-зависимость/вызов; других правок нет.
7-9. ViewModel/Factory/Screen: не меняются.

**Тесты** (`BankDefinitionsTest`, новый; сброс справочника в `@After`, как в `BankLookTest`)
- `placeholderIsRecognised` — `BankBrand.fromKey("own:x")` при пустом справочнике → `isPlaceholder`; свой банк и `known` — нет.
- `placeholderIsNeverPersisted` — `persistable(listOf(placeholder), known)` пусто (без фикса нет функции → красный).
- `placeholderDoesNotOverwriteStoredDefinition` — `merge(stored=[own "WantToPay (TG)" с logoFile], banks=[placeholder того же ключа])` сохраняет название и logoFile.
- `ownBankIsPersisted` / `knownBankWithChangedLogoIsPersisted` / `untouchedKnownBankIsNotPersisted` — перенесённая логика `write` (B-49) не потеряна.
- `resolvesByKeyAfterReplace` — `BankDirectory.replace(defs)` → `fromKey(key).title == "WantToPay (TG)"`, не заглушка (регресс порядка).

**Критерии приёмки:** после убийства процесса и запуска банк «WantToPay (TG)» с картинкой отображается в выборе, на экране банка и в списках; сохранение другого банка не меняет его определение; `defs` не содержит записей «Банк»/«?». На устройстве: добавить банк → закрыть приложение из «недавних» → открыть.

## B-131

**Цель:** загруженная иконка банка/подписки выглядит одинаково в редакторе и после сохранения, без чёрного квадрата.

**Корневая причина (проверено по коду; чёрный цвет — вывод, подтвердить device-check).**
1. `domain/model/LogoBackground.kt:18-25` `near` игнорирует альфу. У PNG с прозрачным фоном углы `0x00000000` → `detect` считает фоном **чёрный** → `clear` обнуляет все тёмные непрозрачные пиксели логотипа (чёрные буквы пропадают). `LogoMaker.kt:45-47` сохраняет PNG с дырами (сжатие не виновато: PNG без потерь, `createScaledBitmap` и `copy` альфу сохраняют).
2. Вырезанный фон (прозрачность) при показе ложится на тёмную панель: `ui/common/BankIcon.kt:36` и `ui/common/BrandAvatar.kt:130` рисуют `PictureFace` (стр. 152-161) **без подложки**, а превью в редакторе (`BrandAvatar.kt:124-129`, ветка `picked`) рисуется с `background(Color(decor.colorArgb))`, т.е. на цвете (белом). Отсюда «в редакторе белый, после выхода чёрный».

**Шаги по слоям**
1. Домен `LogoBackground.kt`: `detect` — если угловой пиксель (почти) прозрачный (alpha < 16), фона для вырезания нет → `null` (фон уже прозрачный); `near` для двух непрозрачных пикселей — как раньше; прозрачный пиксель к непрозрачному не «близок». `clear` — не трогает пиксели с альфой, если фон непрозрачный цвет и они непрозрачные и далеки; не менять.
2-4. Интерфейсы/use-case/Room: нет. `data/icons/LogoMaker.kt` не менять (формат и имя файла остаются; старые файлы с дырами выправит подложка).
5. UI-компоненты `ui/common/BrandAvatar.kt`: `PictureFace(bitmap, cd, shape, modifier, backdrop: Color? = null)` — при `backdrop != null` добавить `.background(backdrop)` до `border`; на стр. 130 передать `Color(decor.colorArgb)`. `ui/common/BankIcon.kt:36` — `backdrop = Color(brand.primaryColor)`; стр. 39 (значок установленного приложения) — без подложки. Токены не добавляются, hex нет (`DesignSystemGuardTest` зелёный).
6. Проверить, не рисуется ли своя картинка на прозрачном ещё где-то: `ui/widget/CashbackWidget.kt:146` (decodeFile → рисовать на цвете банка), `ui/common/BankAccentCache.kt:32/54` (прозрачные пиксели не считать чёрными при выборе акцента: пропускать alpha < 16). Правки точечные, если подтвердится.

**Тесты** (`LogoBackgroundTest`, дополнить существующий или создать)
- `transparentCornersMeanNoBackground` — четыре угла `0x00000000` → `detect == null` (сейчас вернёт чёрный → красный).
- `opaqueBlackLogoSurvivesOnTransparentSheet` — пиксели: прозрачные по углам + непрозрачный `0xFF000000` в центре; полный путь `detect`→(если не null) `clear` оставляет центр непрозрачным.
- `whiteBackgroundStillCleared` — белые углы → `detect` белый, `clear` обнуляет белое, цветной пиксель цел (регресс F-22).
- `semiTransparentIsNotNearOpaque` — `near(0x80FFFFFF, 0xFFFFFFFF)` == false при строгом учёте альфы.

**Критерии приёмки:** PNG с прозрачностью и JPEG с белым фоном: банк и подписка в списке, на экране и в редакторе выглядят как превью; чёрных букв/квадратов нет; значки установленных приложений не изменились. На устройстве: подписка «Мегафон» (скрины 3/4) — до/после.

## B-132

**Цель:** строки «Правка остатка · вручную» в истории валюты появляются только при реальной правке остатка; курсовое изменение остатков на счетах попадает в «Переоценку валюты».

**Корневая причина (проверено).** Курсовые «ноги» `legs` (`domain/model/CapitalEvents.kt:405-433`) строятся **только по сделкам** `fxOps`. Валютные **счета** (EUR у BSB) в `legs` не учитываются, хотя в стоимости валюты считаются (`CapitalReconstruction.build:87-92`: `(balance − операции позже) × курс`). Шаг истории — 7 дней (`rateDays`, `CapitalReconstruction.kt:35`), курс за неделю сдвигает рублёвую стоимость остатка счёта; `revalued` (стр. 328) это содержит, `legs` пусты → весь `rest` (стр. 434) идёт в `manual` (порог стр. 435 — всего 1 ₽) → `manualEvent` (стр. 331-336) с плашкой «вручную». Это и −1 770,70 ₽ (BSB € — единственная нога счёта), и +24,62 ₽ («Валюта», источник CURRENCY_POSITION). Округление/RepairFromJournal ни при чём (`RepairFromJournal.kt:112` — только комментарий).

**Решение (консервативно).** Не прятать и не «сглаживать» остаток порогом — объяснить его курсом точно, в копейках (`FxMath.rubOf`, `Long`). Остаток, который курс по счетам+сделкам объяснить не может, остаётся «вручную» как сейчас (B-121/F-55 не ломаем). Счёт, чей остаток **назван точно внутри шага** (`checkedAt` в (prev, cur]), в курсовые ноги не включается: там возможна настоящая правка.

**Шаги по слоям**
1. Домен, новый чистый объект `domain/model/AccountFxEffect.kt`: `fun legsOf(accounts, transactions, rates, from, to, zone): List<RevaluationLeg>` по каждой валюте счетов (`!excluded`, валюта ≠ RUB, известен курс `r1` на `to`):
   - `qty0` = остаток на конец дня `from` = `balance − Σ операций счёта с postedAt ≥ начало дня from+1` (как `CapitalReconstruction.build:89-90`); счёт, известный (`knownFrom`) позже `from`, пропускается (его открытие уже вычтено как `opened`, стр. 325);
   - `effect` = `rubOf(qty0, r1) − rubOf(qty0, r0)` + для каждой операции счёта внутри шага `rubOf(amount, r1) − rubOf(amount, курс дня проводки)` (то, что `booked` на стр. 322 оценил по курсу дня проводки, а стоимость на `to` — по `r1`);
   - `RevaluationLeg(cur, shownFrom=r0, r1, qty, effect)`; нулевой effect — без ноги. Курс `r0` — как в `reasonOf`: нет курса на `from` → `r1`.
2-4. Интерфейсы/use-case/Room: нет.
5. `CapitalEvents.kt:405` `reasonOf`: принять `accounts`, `transactions`, `zone`; ноги сделок (как сейчас) **слить по валюте** с `AccountFxEffect.legsOf` (effect и qty складываются; `shownFrom` — как для сделок, если нога сделок есть, иначе r0). `rest` и порог стр. 434-435 без изменений. Вызов на стр. 330 обновить. `manualEvent` не менять.
6-9. DI/VM/Factory/Screen: без изменений (UI рисует плашку по `HistoryRef.Manual`, `CapitalHistoryScreen.kt:473`).

**Тесты** (`CapitalEventsTest`, дополнить; помощник `events(...)` расширить параметрами accounts/transactions)
- `foreignAccountRateMoveIsRevaluationNotManual` — счёт 100 € на обоих днях, курс 90→92 ₽, без сделок, точки из `pointFx` по этому: есть «Переоценка валюты» с effect 20000 коп., нет «Правка остатка» (сейчас красный).
- `accountRateEffectIsExactInKopecks` — нечётный остаток (123,45 €) и курсы с 6 знаками: сумма effect == `rubOf` разности, `manualMinor == 0`.
- `realHandChangeOfAccountStaysManual` — остаток счёта дан точно внутри шага (`checkedAt` в шаге) и точки показывают скачок: «Правка остатка» остаётся (`accountEditSourceIsAccount` не ломается).
- `dealsAndAccountsOfOneCurrencyMergeIntoOneLeg` — сделка USD + счёт USD: одна нога, effect = сумма.
- `rateMoveOfTransactionInsideStep` — проводка по валютному счёту в шаге: разница курса дня проводки и `r1` в effect, а не в ручной.
- Существующие (`manualRemainderIsSeparateManualEvent`, `fxPositionEditSourceIsCurrencyPosition`, `revaluationWithoutRatesFallsBackToManual`, `compensatedManualStepKnowsItsPlateau`) должны остаться зелёными без правок ожиданий.

**Критерии приёмки:** инвариант `manual + Σlegs == revalued` держится на каждом шаге; деньги только `Long`; на данных пользователя (скрин 5) 14.11 и 12.11 не дают «вручную»; настоящая ручная правка (F-55) по-прежнему даёт плашку. Осторожно: пересечение с B-121/F-55 (`ManualBalance`, `withStepTo`) — их не менять.

## U-81

**Цель:** переключение «Переводы людям» не подвешивает интерфейс.

**Корневая причина (проверено).** `ui/history/CapitalHistoryViewModel.kt:213`: `_includeTransfers` — вход `base`, поэтому смена тоггла заново запускает `CapitalEvents.build` (стр. 216) и `Spending.month` по 6 месяцам (стр. 218), хотя от тоггла зависит только `totalMinor`/`count` (`domain/model/Spending.kt:63,68`). Вдобавок `combine` не имеет `flowOn`: лямбда идёт на коллекторе (`viewModelScope` = Main), то есть тяжёлый расчёт на UI-потоке. Затем `state` (стр. 226) пересобирает страницы всех разделов.

**Шаги по слоям**
1. Домен `domain/model/Spending.kt`: чистая `fun withTransfers(m: MonthSpending, on: Boolean): MonthSpending` — `totalMinor = purchasesMinor + (on ? transfersMinor : 0)`, `count = (count − (m.includeTransfers ? transfersCount : 0)) + (on ? transfersCount : 0)`, `includeTransfers = on`; идемпотентна от любого входа. `byBank` и прочее не трогает.
2-4. Интерфейсы/use-case/Room: нет.
5. Репозиторий: нет.
6. DI: нет.
7. `CapitalHistoryViewModel.kt`:
   - `base` (стр. 213): убрать `_includeTransfers`; `spend` считать с `includeTransfers = false`; поле `Base.transfers` удалить; на цепочку добавить `.flowOn(Dispatchers.Default)`.
   - `state` разбить на два этапа: (а) страницы (ряды, график, события, фильтр по `bank`, стр. 226-256) без `spendMonth` и тоггла; `.flowOn(Dispatchers.Default)`; (б) лёгкий `combine(этап_а, base, _spendMonth, _includeTransfers)`, который подставляет `spending` на страницу ACCOUNTS (`spendUi` применяет `Spending.withTransfers` к месяцам; логика сужения `bank` — от `pick.bank`, не от месяца). Итог остаётся `StateFlow<HistoryScreenUi?>` через `stateIn<HistoryScreenUi?>`.
   Выбор: двухэтапный `combine`, а не отдельный StateFlow — экран и `HistoryUi.spending` не меняются.
8. Factory: без изменений.
9. Screen: без изменений (`SpendSection`).

**Тесты** (`SpendingTest`, дополнить)
- `withTransfersOnAddsTransfersToTotalAndCount` — база (off) → on: `total = purchases + transfers`, `count += transfersCount`.
- `withTransfersEqualsMonthWithFlag` — `withTransfers(month(…, false), on)` == `month(…, on)` для on = true/false (доказывает, что пересчёт месяца не нужен).
- `withTransfersIsIdempotentAndReversible` — on→off→on и дважды on дают то же.
- `withTransfersDoesNotTouchBanks` — `byBank`, `purchasesMinor`, `incomeMinor` неизменны.

**Критерии приёмки:** `CapitalEvents.build` не вызывается при смене тоггла (проверка ревью по коду: тоггл не входит в `base`); расчёт вне Main; суммы карточки при включении/выключении те же, что раньше (`Spending.month` с флагом). На устройстве: тоггл включается и выключается без паузы.

## F-62

**Цель:** строка «Переводы людям» нажимается и сужает список истории до переводов, как строка банка сужает его до списаний банка; из списка операция открывается в `OperationSheet`.

**Как устроена детализация списаний (проверено).** `SpendCard.kt:215-221` `BankSpendRow` → `onBank` → `vm.setBank` (VM:198) → `shown` фильтруется по банку (VM:244) → `SpendSection` (`CapitalHistoryScreen.kt:250-276`) вместо карточки показывает шапку (иконка, название, счётчик, «Сбросить», `BackHandler`) → строки списка открывают `OperationSheet`. Повторяем тот же паттерн; нового вида и компонентов нет.

**Шаги по слоям**
1. Домен, новый `domain/model/SpendFilter.kt`: `sealed interface SpendFilter { object All; data class Bank(val brand: BankBrand); object Transfers }` и чистая `fun keep(events: List<HistoryEvent>, f: SpendFilter): List<HistoryEvent>`: `All` — всё; `Bank` — `e.source == brand || e.parts.any { it.source == brand }` (логика VM:244 переезжает сюда); `Transfers` — `e.kind == HistoryKind.TRANSFER || e.parts.any { it.kind == HistoryKind.TRANSFER }`. Группа переводов за день (`HistoryRows`, раздельно со списаниями) остаётся группой.
2-4. Интерфейсы/use-case/Room: нет.
5-6. Репозиторий/DI: нет.
7. `CapitalHistoryViewModel.kt`: `_bank: MutableStateFlow<BankBrand?>` заменить на `_filter: MutableStateFlow<SpendFilter>`; `setBank(b)` сохранить как обёртку (`All`/`Bank`), добавить `fun setTransfers()` и `fun clearFilter()`; `Picks` несёт `filter`; фильтр (стр. 242-244) = `SpendFilter.keep(allShown, filter)`; `SpendUi` (стр. 74-85): добавить `val transfersOnly: Boolean` (и оставить `bank`, `bankSpend`); месячные `transfersMinor/transfersCount` берутся из `months[selected]`. Фильтр сбрасывается при выходе со страницы «Счета» так же, как сейчас bank (не менять правило).
8. Factory: без изменений.
9. UI:
   - `ui/common/SpendCard.kt:203-209`: в строку «Переводы людям» добавить `onClick = onTransfers, chevron = false` (как `BankSpendRow`); в `SpendCard` (стр. 107-115) новый параметр `onTransfers: () -> Unit`, прокинуть в `SpendBanks`. Нижнюю строку-переключатель (стр. 135) не трогать. Строка нажимается только когда включён тоггл (она тогда и видна) — как есть.
   - `CapitalHistoryScreen.kt:252-276` `SpendSection`: ветка `transfersOnly` — та же шапка, что у банка, плитка `SummaryTile(Icons.Filled.SwapHoriz, Slitok.Muted)` вместо `BankIcon`, заголовок «Переводы людям», подпись `RuPlural.transfers(count) + " · " + Money.ofMinor(minor).format()` через `MoneyText`; «Сбросить» и `BackHandler` — `vm.clearFilter()`. Если `SummaryTile` не `public` в `ui/common` — сделать `public` (то же файл-семейство, не новый компонент).
   - `ui/showcase/ShowcaseScreen.kt:431`: добавить `onTransfers = {}`; `docs/design-system/README.md` и `docs/design-system/showcase.html` — одной строкой отметить, что строка переводов нажимается как строка банка (правило ui/CLAUDE.md: меняешь компонент — обнови README и витрину).
   - Мост к операции: список под шапкой — обычный список истории, нажатие открывает `OperationSheet` (ничего нового).
   Решение по деталям: фильтр по виду операции всех месяцев и валют, как фильтр банка (он тоже не режет по месяцу); шапка показывает сумму выбранного месяца — так же, как у банка.

**Тесты** (`SpendFilterTest`, новый)
- `transfersKeepsOnlyTransferEvents` — события SPEND/TRANSFER/INCOME → остаются TRANSFER.
- `transfersKeepsGroupWithTransferChild` — группа/событие с `parts` из TRANSFER проходит.
- `bankFilterMatchesSourceAndParts` — перенесённая логика банка (регресс F-58).
- `allKeepsEverything`.

**Критерии приёмки:** строка «Переводы людям» (при включённом тоггле и ненулевых переводах) нажимается; список — только переводы с датами и суммами; нажатие на перевод открывает шторку операции; «Сбросить»/«Назад» возвращают карточку; фильтр банка работает как прежде. `DesignSystemGuardTest` зелёный. На устройстве проверить оба фильтра подряд.

---

## Группы, порядок, пересечения

**Группа A — B-130 + B-131 (иконки/банки).** Файлы: `domain/model/BankBrand.kt`, новый `domain/model/BankDefinitions.kt`, `domain/model/LogoBackground.kt`, `data/settings/BankStore.kt`, `ui/common/BrandAvatar.kt`, `ui/common/BankIcon.kt` (опц. `ui/widget/CashbackWidget.kt`, `ui/common/BankAccentCache.kt`), `di/AppContainer.kt` (только проверка порядка). Коммит на пункт: сначала B-130, затем B-131 (общих правок файлов между ними нет, группа общая по теме).

**Группа B — U-81 + F-62 (история капитала).** Общие файлы: `ui/history/CapitalHistoryViewModel.kt`, `ui/history/CapitalHistoryScreen.kt`, `ui/common/SpendCard.kt`. Порядок: **U-81 первым** (меняет `base`/`state` и `Picks`), затем F-62 поверх (меняет `_bank`→`_filter`, `SpendUi`, `SpendCard`). Новые файлы: `domain/model/SpendFilter.kt`; правки `domain/model/Spending.kt` (только U-81), `ui/showcase/ShowcaseScreen.kt`, README/showcase.html (только F-62).

**Группа C — B-132.** Файлы: `domain/model/CapitalEvents.kt`, новый `domain/model/AccountFxEffect.kt`, тесты `CapitalEventsTest`.

**Независимость.** A, B и C не пересекаются по файлам и могут идти параллельно. Мягкие пересечения: A и B оба затрагивают `ui/common`, но разные файлы (`BrandAvatar`/`BankIcon` против `SpendCard`); `SpendCard` использует `BankIcon` (A) только как потребитель — сигнатуру `BankIcon` не менять. C и B оба читают `CapitalEvents`/`HistoryEvent`, но B его не правит (`SpendFilter` использует `HistoryKind`/`source` как есть). Рекомендуемый порядок при последовательной работе: C → A → B (B самая объёмная по UI; C и A чисто доменные и быстрые).

**ДИЗАЙН:** нет. F-62 повторяет паттерн банка (шапка и строка существуют). B-131 возвращает вид превью (подложка цвета банка), новых токенов нет. B-132 — не вид, но меняет трактовку «вручную»: сохранён инвариант «правка = остаток, не объяснимый ни сделками, ни курсом по счетам».
