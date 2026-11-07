# Планы цикла 18

Пути от `app/src/main/java/ru/maxrubtsoff/bankagg/` (тесты — `app/src/test/java/ru/maxrubtsoff/bankagg/`). Нового вида нет нигде, кроме отмеченного «НОВЫЙ ВИД».

## B-124 — правка/удаление старой записи «изменили вручную» (наличные)

**Цель:** строка «Наличные · изменили вручную» без `CashChange` открывается как обычная правка и удаляется; удаление убирает ступеньку с графика.

**Решение (минимум):** материализовать запись лениво. У старой записи появляется `ref = HistoryRef.Cash(cash.updatedAt)` и расчётная дельта; при первой операции (preview/check/save/delete) в журнал вставляется `CashChange(atMs = cash.updatedAt, delta)` БЕЗ изменения суммы. Дальше работают уже существующие ветки `HistoryOperations` и `OperationSheet`. Обоснование: ни одной новой шторки, весь поток F-48/F-51 переиспользуется.

Шаги:
1. Домен `domain/model/CapitalEvents.kt:240-242`: дельта старой записи = `cash.amount − Σ cashChanges.delta` (инвариант: Σ всех дельт = остаток); `ref = HistoryRef.Cash(cash.updatedAt)`; если дельта 0 — запись не строить. Вынести расчёт в чистую `CashEdit.legacyDelta(cash, changes): Long` (`domain/model/CashEdit.kt`, рядом с режимами).
2. Интерфейс `domain/repository/CashRepository.kt:19`: добавить `suspend fun materializeChange(change: CashChange)` — только вставка в журнал, сумма не двигается (в отличие от `addChange`).
3. `domain/usecase/HistoryOperations.kt`: общий приватный `cashChange(atMs)`: ищет в `observeChanges`; если нет и `atMs == cash.updatedAt` и `legacyDelta != 0` — зовёт `materializeChange` и возвращает его. Заменить поиск по `atMs` в `:233, :255-262, :282-285, :311-313, :328-342` на этот хелпер.
4. Data: `data/repository/FxRepositoryImpl.kt:60` (рядом с `addChange`) — реализация `materializeChange` (insert без смены суммы, в транзакции); `data/repository/sample/SampleMoneyRepositories.kt:68-87` — то же. Room/миграция не нужны (таблица журнала есть).
5. UI: `ui/history/OperationSheet.kt:78-82` менять не надо (ветка по `ref` уже есть). Проверить, что `CapitalHistoryViewModel.kt:112` пересобирает события после материализации (Flow `observeChanges`).

**Тесты** (`CapitalEventsTest`, `CashHistoryTest`):
- `legacyEventGetsRefAndDelta`: cash 2 000 000, `updatedAt`, журнал пуст → событие с `ref=Cash(updatedAt)`, delta 2 000 000.
- `legacyDeltaWithOtherChanges`: журнал с +100 000 → delta = amount − 100 000.
- `noLegacyEventWhenUpdatedAtMatchesChange` (прежнее поведение) и `noLegacyEventWhenDeltaZero`.
- `deleteLegacyZeroesCash`: на фейк-репозитории материализация + `removeChange` → остаток 0, события нет.
- `materializeDoesNotMoveAmount`.

**Готово:** `./gradlew testDebugUnitTest assembleDebug`; инвариант Σ дельт = остаток; на устройстве: тап по старой записи → правка суммы/удаление, график без ступеньки.

## B-125 / B-126 / U-76 — парсер (одна группа, один файл `NotificationParser.kt`)

**Цель:** разбираются «Перевод в Кубышку» (Т-Банк) и снятие наличных Сбера «сумма — Баланс»; платёжки и реклама остаются `null`.

### B-125-а: Т-Банк «Перевод в Кубышку 20 000 ₽, счет RUB Баланс: 59 342,18 ₽»
1. `domain/parser/NotificationParser.kt:128-132` `OUT_MARKS`: добавить `"перевод в кубышк"`, `"перевод в копилк"`, `"перевод в накопительн"`, `"перевод на накопительн"`. Не добавлять общий «перевод в» (слишком широко). Вид TRANSFER, направление OUT, баланс = счёт RUB (79 342,18 − 20 000 = 59 342,18 — сходится).
2. `NotificationParser.kt:51` `VERSION = 6 → 7` (журнал перечитывается).

### B-126 / B-125-б: Сбер «100 000 ₽ — Баланс: 79 821,66 ₽ Счёт карты МИР •• 0402» (направление без слова)
**Как сейчас:** `RecordNotification.kt:46` зовёт `parse`; нет слова/знака/«Перевод на» → `detectDirection` = `null` → событие пишется `parsed=false`, баланс и операция не трогаются. Снятия наличных в доменной логике нет вообще (нет кассы в `RecordNotification`).

**Решение:** парсер остаётся без состояния и направление по-прежнему не угадывает. Добавляется отдельный узкий разбор + решение по сравнению с прежним балансом счёта.
1. `NotificationParser.kt`: новая `fun parseBare(title, text): BareOp?` (`BareOp(amount, currency, balance)`). Матч только строгого шаблона: текст НАЧИНАЕТСЯ с `NUM ccy`, затем `—`/`–`/`-`, затем «Баланс:» `NUM ccy` (валюты совпадают, RUB). Всё иное, любые лишние слова — `null`. Промо-проверка (`isPromo`) выполняется первой.
2. Чистая функция `domain/model/CashWithdrawal.kt`: `resolve(bare, prevBalance: Long?): ParsedOp?`. Правило: `prevBalance != null && prevBalance − amount == balance` → `ParsedOp(OUT, EXPENSE, counterparty = "Снятие наличных", balance)`. Всё остальное (нет прежнего баланса, `prev + amount == balance`, не сходится) → `null`. Консервативность: только точное равенство; рост баланса (внесение) не угадываем — «не уверен — null». Честное ограничение: B-126 (100 000, баланс 79 821,66) разберётся лишь если прежний баланс счёта известен (179 821,66); B-125 (70 000 → 9 821,66) сходится по цепочке. Сам факт не-распознанного B-126 при неизвестном `prev` — допустимый `null`.
3. `domain/usecase/RecordNotification.kt:46`: `op = parse(...) ?: bare?.let { CashWithdrawal.resolve(it, ledger.<текущий баланс счёта "${brand.key}:RUB">) }` (метод чтения счёта у `LedgerRepository` — найти, при отсутствии добавить `suspend fun accountBalance(id): Long?` + Impl `data/repository/LedgerRepositoryImpl.kt` + Sample `SampleLedgerRepository.kt` + запрос в `LedgerDao`). Читать prev ДО `upsertAccountBalance` (`:77`), внутри `tx.run` и `Mutex`.
4. Приход в наличные: в `RecordNotification` (внутри транзакции, `:85`) для снятия — `cash.addChange(CashChange(atMs = at, delta = +amount))` (конструктор `CashChange` сверить; `atMs` уникален — при коллизии +1 мс). Добавить зависимость `CashRepository` в конструктор + `di/AppContainer.kt:172`. Вид операции — `EXPENSE` с контрагентом «Снятие наличных» (нового значения `TransactionKind` не вводим — нет миграции).
5. Повтор: `RepairFromJournal.kt:35,62,76` перечитывает журнал через `parse` без prev — снятие там не восстановится. Coder обязан проверить, не удаляет ли Repair ранее записанную операцию/баланс, если `parse` для неё теперь `null`; если удаляет — Repair пропускает события, у которых `parseBare != null` (не трогает). Дубль в наличных исключить (дедуп Repair/`hasSimilar`).

### Правка по Татэнергосбыту («6106.66 ₽ Татэнергосбыт Оплатите в приложении»)
Сейчас `null` (слово «оплатите» ≠ «оплата»). Закрепить тестом; добавить `BILL_MARKS = "оплатите", "к оплате", "счет на оплату"` → `null` до направления (`parse:185`).

### U-76: набор тестов и доработка
Файл `domain/NotificationParserTest.kt` (+ `RecordNotificationTest.kt` для снятия). Таблицы кейсов (каждый — реалистичный текст, ожидание: direction/kind/amount/balance либо `null`):
- Снятие/внесение: Сбер «Снятие наличных 5 000 ₽ … Баланс», Т-Банк «Снятие в банкомате», внесение «Взнос наличными» (IN).
- Переводы: между своими, в Кубышку/накопительный, СБП «Перевод по СБП», на карту другого банка, «Перевод от Ивана И.», «Перевод на 1 100 ₽» (IN).
- Комиссия (OUT), возврат, кэшбэк (IN), валюта («Покупка 12,50 $ … Баланс 100 $»), иностранная сумма при другой валюте счёта.
- Подписи баланса: «Баланс:», «Доступно», «Остаток».
- Числа: `1 234,56`, `1,234.56`, NBSP/NNBSP, `440.00`, без копеек.
- Сумма — Баланс: Сбер OUT-случай и нестрогие варианты → `null` (`parse`) .
- Негатив: реклама Сбер ЖКХ/Т-Банк «25 брендов»/Ozon/Альфа «заправляйтесь», платёжка Татэнергосбыт, уведомление без суммы.
Прогнать все; расхождения — минимальные правки списков (`OUT_MARKS`/`IN_MARKS`/`PROMO_*`) только если текст однозначен; сомнительные оставить `null`. Любая правка парсера — `VERSION++` один раз (в сумме 7).

**Тесты B-125/B-126:**
- `kubyshkaTransferIsOutWithBalance`: -20 000 ₽, TRANSFER, balance 59 342,18.
- `bareAmountBalanceParsesAsBareOp` / `bareRejectsExtraWords` / `bareParseIsNullInMainParse`.
- `withdrawalWhenBalanceDropsByAmount`: prev 79 821,66, 70 000, баланс 9 821,66 → OUT «Снятие наличных».
- `bareNullWhenPrevUnknown`, `bareNullWhenBalanceRises`, `bareNullWhenMismatch`.
- `recordWithdrawalDebitsAccountAndCreditsCash` (RecordNotification с фейками): счёт 9 821,66, в наличных +70 000, одна транзакция; повтор того же push — без дубля.
- `billIsNull`.

**Готово:** тесты и сборка зелёные; `null` для всех негативов; деньги только `Money`/Long.

## F-57 — правка и удаление валютного счёта («На счетах», Капитал → Валюта)

**Цель:** тап по строке валютного счёта — шторка остатка; долгое нажатие — меню «Изменить остаток / Удалить счёт»; удаление с подтверждением.

Шаги:
1. Домен: `domain/usecase/DeleteAccount.kt` (чистая оркестрация): `check(id): Boolean = ledger.hasTransactions(id)`; `delete(id)` → `ledger.deleteAccount(id)` (`LedgerRepository.kt:54,57`). Coder проверяет `LedgerDao.kt:64`: удаляет ли `deleteAccount` операции; если нет — удалять и их запретить/оставить сиротами нельзя: тогда в `delete` сначала чистка операций счёта (метод репозитория при необходимости). Решение: подтверждение показывает «У счёта есть операции — они тоже уйдут из учёта; новое уведомление банка создаст счёт заново». Без операций — подтверждение короче.
2. DI: `di/AppContainer.kt` — `deleteAccount by lazy`.
3. VM: `ui/capital/CapitalViewModel.kt:64` — рядом с `setBalance` добавить `hasOperations(id)` и `deleteAccount(id)`; `ui/common/ViewModelFactory.kt` — передать use-case в ветку `CapitalViewModel`. VM уже используется в `CurrencySection`/`CurrencyDetailScreen` (сверить, как приходит; при необходимости прокинуть лямбды `onEditHolding`, `onDeleteHolding`).
4. UI: `ui/capital/CurrencySection.kt:126,261` — `HoldingRow(h, onClick, onLongClick)` (по образцу кликов `PositionRow`; `combinedClickable` как в `CapitalScreen.kt:265`). Тап → `BalanceSheet(h.account, since, onSave = vm.setBalance(h.account.id, it))` (`BalanceSheet.kt:54`). Долгое → `LongPressMenu` с двумя пунктами. Удаление → существующий компонент подтверждения (coder ищет в `ui/common` по таблице README; если нет — **НОВЫЙ ВИД**, остановиться и вернуть оркестратору, не изобретать).
5. Проверить `BalanceSheet` на валюту: `account.currency` уже идёт в `Currencies.symbolOf` и `format(account.currency)` (`:77-87`) — ок; проверить, что `vm.balanceSince(a)`/`since` не завязан на RUB, и что `setBalance` для валютного счёта не конвертирует по курсу.
6. Sample-репозиторий: `SampleLedgerRepository.kt:39,72` — только если интерфейс меняется.

**Тесты:** `DeleteAccountTest`: `hasOperationsTrueFalse`; `deleteRemovesAccount`; `deleteRemovesItsOperationsIfDaoDoesNot` (по факту); `setBalanceOnForeignAccountKeepsCurrency`. UI на устройстве: тап по «BSB USD» → шторка с «$»; удаление убирает строку и сумму из «На счетах» и общего капитала.

**Готово:** сборка/тесты зелёные; `DesignSystemGuardTest` проходит (без своих кнопок/диалогов).

## Пересечения файлов (для параллельности)

- Группа A (парсер): B-125, B-126, U-76 — `NotificationParser.kt`, `NotificationParserTest.kt`, `RecordNotification.kt`, `RepairFromJournal.kt`, `AppContainer.kt`. Один коммит на пункт, строго последовательно внутри группы.
- Группа B (B-124): `CapitalEvents.kt`, `CashEdit.kt`, `CashRepository.kt`, `FxRepositoryImpl.kt`, `SampleMoneyRepositories.kt`, `HistoryOperations.kt`.
- Группа C (F-57): `CurrencySection.kt`, `CapitalViewModel.kt`, `ViewModelFactory.kt`, `DeleteAccount.kt`, `AppContainer.kt`.
- Пересечения: A↔B — `CashRepository`/`CapitalEvents`/`HistoryOperations` не пересекаются, но A (снятие) вызывает `CashRepository.addChange`: сигнатуры не менять (B только добавляет метод `materializeChange`) — тестовые фейки `CashRepository` в обоих наборах тестов дополнить новым методом (конфликт в `app/src/test` фейках). A↔C и B↔C: общий только `di/AppContainer.kt` (разные строки `by lazy`; правка A — конструктор `recordNotification`) и `LedgerRepository` (A возможно добавляет `accountBalance`, C — не меняет интерфейс, если `deleteAccount` достаточно).
- Вывод: A, B, C можно параллельно, если слияние `AppContainer.kt` и тестовых фейков `CashRepository`/`LedgerRepository` — на оркестраторе; безопаснее B и C параллельно, A отдельно после B (или вместе, с проверкой фейков).
