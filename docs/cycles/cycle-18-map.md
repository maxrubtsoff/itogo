# Карта цикла 18 (B-124, B-125, B-126, U-76, F-57)

Базовая ветка `cycle-18`. Карта составлена `explorer`, код не менялся. Пути — относительно `app/src/main/java/ru/maxrubtsoff/bankagg/` (тесты — `app/src/test/java/ru/maxrubtsoff/bankagg/`).

## B-124 — «изменили вручную» (наличные), старая запись

| Слой | Место | Что там |
|---|---|---|
| domain/model | `CapitalEvents.kt:237-239` | каждая запись `CashChange` → событие «Наличные · изменили вручную», `ref = HistoryRef.Cash(c.atMs)` (редактируется) |
| domain/model | `CapitalEvents.kt:240-242` | **старая запись**: если `cash.updatedAt` не совпадает ни с одной `CashChange`, событие строится **без `ref`** (`null`), delta = `null` |
| ui/history | `OperationSheet.kt:78-82` | `ref` = `Balance` → `BalanceFlow`; `Manual` → `ManualEditFlow`; иначе общий просмотр/редактирование по `ref`. Для `null` ref редактировать/удалять нечего |
| domain/usecase | `HistoryOperations.kt:233` (preview), `:255-262` (check), `:282-285` (delete), `:311-313` (restore), `:328-342` (save) | все операции с `HistoryRef.Cash` ищут `CashChange` по `atMs`; для записи без `CashChange` ничего не находят (`?: return null`) |
| domain/usecase | `HistoryOperations.kt:348` | `Balance`/`Manual` в `save` → `null` (no-op) |
| domain/model | `HistoryRows.kt:155` | отдельная строка «Остаток изменили вручную» для валюты (не наличные) |
| domain/model | `CashEdit.kt:9-13` | режимы SET/ADD/TAKE, результат не может быть `< 0` |
| ui/capital | `CashSheet.kt:64,70,75` | шторка «Задать / + Добавить / − Взять» для наличных (вкладка Капитал) |
| ui/capital | `CapitalScreen.kt:388` | вызов `CashSheet` |
| domain/repository | `CashRepository.kt:9-19` | `observe`, `get`, `observeChanges`, `set`, `removeChange(atMs)`, `addChange` |
| data/repository | `FxRepositoryImpl.kt:42,50,60` | реализация `CashRepository` (в том же классе, что FX), `removeChange`/`addChange` в транзакции |
| data/repository/sample | `SampleMoneyRepositories.kt:68-87` | sample-реализация |
| ui/history | `CapitalHistoryViewModel.kt:112` | `combine(cash.observeChanges())` |

Вывод по B-124: запись без операции — это строка из `CapitalEvents.kt:241` (`ref = null`); в UI нет ветки, которая её редактирует, и в `HistoryOperations` нет `CashChange` с таким `atMs`. Править остаток наличных можно только через `CashSheet` на вкладке Капитал (`CashEdit.Mode.SET`), но не через строку истории. Точный «лишний шаг» на графике задаёт `cash.updatedAt` (CapitalEvents:240).

Тесты: `CashHistoryTest.kt`, `CapitalEventsTest.kt`, `RebuildCurrencyHistoryTest.kt` (по наличным/событиям), `CapitalHistoryTest.kt`.

## B-125 / B-126 / U-76 — парсер уведомлений

| Слой | Место | Что там |
|---|---|---|
| domain/parser | `NotificationParser.kt:37` | `object NotificationParser`; `VERSION = 6` (:51) — при росте версии журнал перечитывается (`RepairFromJournal`) |
| domain/parser | `NotificationParser.kt:60` | `NUM` — формат чисел (пробелы/NBSP, запятая/точка) |
| domain/parser | `NotificationParser.kt:64-75` | `CURRENCY_MARKS` (₽, руб, р, $, €, коды валют) |
| domain/parser | `NotificationParser.kt:80` / `:83` | `AMOUNT_THEN_CCY` (сумма ₽) / `CCY_THEN_AMOUNT` ($100) |
| domain/parser | `NotificationParser.kt:91-96` | `MASK` — маски карт/счетов (не сумма) |
| domain/parser | `NotificationParser.kt:103` | `ORDER_AMOUNT` — п/п без знака валюты («списано 440.00 счет *4863») |
| domain/parser | `NotificationParser.kt:124` | `EXPENSE` (снятие, оплата, списание…) |
| domain/parser | `NotificationParser.kt:125` | **`BALANCE_MARKERS = doступно, остаток, баланс`** — подписи баланса (нет «Баланс» после суммы через «—» как отдельной логики) |
| domain/parser | `NotificationParser.kt:128-132` | `OUT_MARKS` (исходящие) |
| domain/parser | `NotificationParser.kt:135-138` | `IN_MARKS` (входящие) |
| domain/parser | `NotificationParser.kt:154-164` | `PROMO_MARKS` / `PROMO_PATTERNS` — реклама → `null` |
| domain/parser | `NotificationParser.kt:179-227` | `parse(title, text, defaultCurrency)`: promo → kind → direction → сумма до слова баланса (`:201-206`) → баланс (`:210-214`) |
| domain/parser | `NotificationParser.kt:259-279` | `detectDirection`: слово (OUT/IN) → знак `+`/`-` у суммы → «Перевод на <сумма>» → TRANSFER с контрагентом; иначе `null` |
| domain/parser | `NotificationParser.kt:291` | `toMinor` — перевод в копейки |
| domain/parser | `OpTimeParser` (внутри `:225`) | время операции |

Разбор текстов из цикла (по коду, без запуска):
- B-126 «100 000 ₽ — Баланс: 79 821,66 ₽ Счёт карты МИР •• 0402»: сумма с валютой есть, баланс есть, но в тексте нет слова направления (ни OUT_MARKS, ни IN_MARKS, ни знака у суммы, ни «Перевод на») → `detectDirection` = `null` → `parse` = `null` («не разобрано»). Слово «Снятие» в тексте не встречается.
- B-125 Сбер «70 000 ₽ — Баланс: 9 821,66 ₽»: та же причина — нет направления.
- B-125 Т-Банк «Перевод в Кубышку 20 000 ₽, счет RUB Баланс: 59 342,18 ₽»: kind = TRANSFER (`TRANSFER` содержит «перевод»), но OUT_MARKS содержат «перевод на карту/со счет/с карты», а не «перевод в»; знака нет; контрагент (`counterparty`) по `NAME_*` скорее всего `null` → `null`.
- Для сравнения уже разобранное: Т-Банк «Пополнение на 70 000 ₽ … Доступно» — IN по «пополнение»/`TRANSFER_IN`.

Точка для U-76: набор кейсов и правило «сумма — Баланс» без глагола относятся к `detectDirection` (`:259`) и `EXPENSE/OUT_MARKS` (`:124`, `:128`). Консервативность: не уверен → `null` (сохранить).

`RecordNotification.kt`:
- `:27-32` класс, зависимости `EventRepository`, `LedgerRepository`, `TransactionRunner`
- `:35-41` `invoke(packageName, title, text, postedAt, notificationKey)` под `Mutex`
- `:46` `NotificationParser.parse(title, normalized, brand.defaultCurrency)`
- `:50-100` одна транзакция: dedup (`hasSimilar`, `:53`) → `events.record` (`:56`) → если `op != null`: `ledger.upsertAccountBalance` (`:77`, accountId = `"${brand.key}:${accountCurrency}"`, `:74`) → `ledger.addTransaction` (`:85`), пропуск при `foreignAmount`
- `:108-109` `dedupKey`; `:112` `DEDUP_WINDOW_MS` ±2 мин
- `di/AppContainer.kt:172` — `recordNotification` by lazy

Кто ещё зовёт парсер: `RepairFromJournal.kt:35,62` (перечитывание журнала), `HistoryOperations.kt:121` (разбор заметки для строки истории).

Тесты парсера: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/NotificationParserTest.kt` (21 вхождение «Баланс/Доступно/Остаток/снят»). Также `RecordNotificationTest.kt`, `OpTimeParserTest.kt`, `RebuildCurrencyHistoryTest.kt`. Для U-76 новых тест-кейсов ещё нет.

## F-57 — валютные счета (Капитал → Валюта → «На счетах»)

| Слой | Место | Что там |
|---|---|---|
| domain/model | `Capital.kt:47` | `holdings: List<CurrencyHolding>` — «валюта на счетах» |
| domain/model | `Capital.kt:111-122` | сборка `holdings` из иностранных счетов (`foreign.map`), `valueMinor` через курс |
| domain/usecase | `CapitalByBank.kt:30` | `snap.currency.holdings.filterNot { excluded }` |
| ui/capital | `CurrencySection.kt:121-129` | блок «На счетах»: `GroupTitle` + `Panel` + `HoldingRow(h)` |
| ui/capital | `CurrencySection.kt:261` | `private fun HoldingRow(h)` — **нет `onClick`**, редактирования и удаления нет |
| ui/capital | `CurrencyDetailScreen.kt` | экран валюты (вызывает `CurrencySection`), не разбирался детально |

Вкладка «Счета» (RUB) — как редактируется и удаляется:
- `ui/capital/CapitalScreen.kt:256-260` — `editing: Account?` → `BalanceSheet(a, since = vm.balanceSince(a), onSave = vm.setBalance(a.id, it))`
- `ui/capital/CapitalScreen.kt:265` — `LongPressMenu`: только «Учитывать / Не учитывать в капитале» → `vm.setExcluded(ids, flag)`. Удаления счёта в UI не найдено.
- `ui/capital/CapitalScreen.kt:269` — один счёт: тап по банку ставит баланс (B-29)
- `ui/capital/BalanceSheet.kt:54` — шторка остатка (`since`, `onSave`, `onDismiss`, `onRestore`)
- `ui/capital/CapitalViewModel.kt:64-65` — `setBalance(accountId, minor)` → `ledger.setBalance(accountId, minor, now)`
- `ui/history/BalanceFlow.kt:27-44` — то же для строк истории (`vm.setBalance(a.id, it, restored)`)
- `ui/history/CapitalHistoryViewModel.kt:157-159` → `domain/usecase/HistoryOperations.kt:157-162` `setBalance(accountId, minor, counted)`; `:165` `balanceSince`; `:172` `restoreBalance`
- `domain/repository/LedgerRepository.kt:25` `setBalance`, `:28` `restoreBalance`, `:57` `deleteAccount`, `:65` `setExcluded`, `:60` `resetBalanceBase`
- `data/repository/LedgerRepositoryImpl.kt:46` `setBalance`, `:77` `deleteAccount` → `dao.deleteAccount`
- `data/local/LedgerDao.kt:19` `setBalance`, `:64` `deleteAccount`
- `data/repository/sample/SampleLedgerRepository.kt:39`, `:72`
- `ui/common/ViewModelFactory.kt` — ветка для `CapitalViewModel` (не проверял построчно)

Чего не хватает для валютных (по коду):
1. В `CurrencySection.kt:126` строка `HoldingRow` не кликабельна: нет шторки остатка (аналога `BalanceSheet`) и нет пункта удаления.
2. Нет ViewModel-метода, который меняет остаток именно валютного счёта через UI вкладки «Валюта» (`setBalance` есть, но вызывается только для RUB-счетов с `CapitalScreen`; для валютных нужна проверка, что `BalanceSheet` принимает валютный `Account` — не проверено).
3. Удаления счёта через UI нет ни для RUB, ни для валютных: `LedgerRepository.deleteAccount` есть, но не вызывается из `ui/` (поиск по `deleteAccount` в `ui/` пуст). Для F-57 удаление валютного счёта понадобится с нуля (шторка/подтверждение, `hasTransactions` в `LedgerRepository.kt:54` — для проверки перед удалением).
4. Строка валюты в `FxPortfolio`/`CapitalPositions` не связана с `holdings` (holdings — отдельная модель `CurrencyHolding`, не `Account`-список напрямую; для правок нужна ссылка на `accountId`, которая в `CurrencyHolding.account` уже есть — `Capital.kt:67,111`).

## Пробелы в карте (не проверено)
- `CapitalHistoryScreen.kt` / `ManualEditFlow.kt` — не открывал построчно.
- `BalanceSheet.kt` — только сигнатура, поведение для валютного счёта не проверено.
- `CurrencyDetailScreen.kt` — не читал, как вызывается `CurrencySection`.
- Тестов на `LedgerRepository` удаления/`hasTransactions` не искал.
