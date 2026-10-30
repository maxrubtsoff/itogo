# Карта цикла 16 — B-118, F-55, B-119

Сбор `explorer`. Пути от `app/src/main/java/ru/maxrubtsoff/bankagg/` (сокращено `bankagg/`), тесты от `app/src/test/java/ru/maxrubtsoff/bankagg/`.

## B-118

**Симптом (из пункта):** «Товары и услуги» не разбирается, «Возврат средств» разбирается.

**Причина по коду (трассировка `NotificationParser.parse`):**
- Вид: `detectKind` (`domain/parser/NotificationParser.kt:285`) ищет слова TRANSFER/INCOME/EXPENSE (`:103-105`). В тексте «Товары и услуги» нет ни одного слова → `TransactionKind.UNKNOWN`.
- Направление: `detectDirection` (`:231`) ищет OUT_MARKS (`:109-113`) и IN_MARKS (`:116-119`). «Товары», «услуги» и «Успешно» ни в одном списке нет. «Возврат» есть в IN_MARKS (`:118`) и INCOME (`:104`) — поэтому «Возврат средств» проходит.
- Знак по `SIGNED_AMOUNT` (`:128`) не срабатывает (знака перед суммой нет); `TRANSFER_IN` (`:125`) не срабатывает; `kind == TRANSFER` не выполняется.
- `detectDirection` возвращает `null` → `parse` делает `return null` (`:174`) → «не разобрано».
- Валюта суммы «1 USD» / «9.94 EUR» читается нормально (`AMOUNT_THEN_CCY`, `:67`). Остаток «Остаток: 100.29 EUR» читается в `balance` (`:190-193`) как голое число, валюта остатка не проверяется. Это значит, что при расхождении валют суммы и остатка баланс счёта (`accountId = brand:currency`, `RepairFromJournal.kt:125-127`) может быть записан по валюте операции, а не по валюте остатка — проверить при правке (пункт явно это упоминает).
- BSB в парсере без особых правил: брендовых правил под BSB нет, только `BankBrand.BSB` с `defaultCurrency = null` (`domain/model/BankBrand.kt:86-87`, `:106`). `parse(...)` вызывается с `brand.defaultCurrency` (`RecordNotification.kt:43`), но параметр не влияет на результат (`NotificationParser.kt:156-159`).

**Где вызывается:**
- `domain/usecase/RecordNotification.kt:43` — основной путь: `NotificationParser.parse(title, normalized, brand.defaultCurrency)`. Вызывается из `service/BankNotificationListener.kt` через `App.container`.
- `domain/usecase/RepairFromJournal.kt:32` — перепарсинг всего журнала (`events.all()`), сравнение хеша с `event.contentHash` (`:34`).
- `RepairFromJournal.kt:55` — перепарсинг для базы баланса (`RepairBase.pick`).
- `domain/usecase/HistoryOperations.kt:97` — разбор сохранённого уведомления при показе операции.
- Версия парсера: `NotificationParser.VERSION = 4` (`NotificationParser.kt:38`), комментарий-история в `:29-37`. Для B-118 нужна новая версия (5), иначе журнал не перечитается сам.

**RepairFromJournal (`domain/usecase/RepairFromJournal.kt`):**
- `:23` класс; конструктор `(events: EventRepository, ledger: LedgerRepository, tx: TransactionRunner)`.
- `:30` `invoke()` — возвращает число исправленных записей.
- `:31-35` список изменений: старый `contentHash` ≠ новому → `Change`.
- `:40-44` затронутые счета; `:49-63` базы баланса через `RepairBase.pick(account, mine, changedAt, since)`.
- `:65-85` удаление старой операции и добавление новой (`ledger.addTransaction`), `events.updateParse(...)` (`:84`).
- `:87-120` пересчёт баланса: `RepairBase.Keep` / `FromJournal` / `None`.
- Использует `RepairBase`, `RepairEvent` из `domain/model` (не смотрел, найти через Grep при необходимости).
- DI: `di/AppContainer.kt:172` `val repairFromJournal by lazy { RepairFromJournal(eventRepository, ledgerRepository, transactionRunner) }`.
- Кто вызывает `invoke()` в UI/данных: не найдено в этом проходе (Grep по `repairFromJournal` в `ui`/`service` не делался). Проверить через `AppContainer.repairFromJournal`.
- `RecordNotification.contentHash` `:110`, `:113` (перегрузка с `op`), `eventHash` `:121` — `domain/usecase/RecordNotification.kt`.

**Тесты:**
- `app/src/test/.../domain/RepairFromJournalTest.kt` — 10+ кейсов (`:135`, `:164`, `:183`, `:205`, `:222`…`:286`). BSB-специфичного кейса нет.
- `app/src/test/.../domain/RecordNotificationTest.kt:87` `bsb_without_currency_is_logged_but_not_counted`, `:95` `bsb_byn_goes_to_a_byn_account` — оба используют «Оплата …», не «Товары и услуги».
- `app/src/test/.../domain/BankBrandTest.kt:42-44` — BSB в каталоге, `defaultCurrency == null`.
- `app/src/test/.../domain/BankSignalTest.kt:21` — BSB по заголовку.
- `NotificationParser`-тестов с фразами «Возврат средств» / «Товары и услуги» нет (Grep по `app/src/test` не нашёл).
- Ближайший кейс для проверки: `app/src/test/.../domain/` (файл парсера найти Grep по `NotificationParser.parse(`) — не открывал.

**Подсказка для архитектора (без правок):** стоят три точки — `detectDirection` (`:231`), `detectKind` (`:285`), и `parse` `return null` на `:174`. Проверка валюты остатка — `:190-193`.

---

## F-55

**Что в UI сейчас:** строка «Остаток изменили вручную» в истории капитала (вкладка «Валюта») — производная, без своей сущности и без `HistoryRef`.

**Формирование события (domain):**
- `domain/model/CapitalEvents.kt:314` — `revalued = Δ(CURRENCY) − dealt − booked − opened`.
- `:316` `reasonOf(prev.date, cur.date, revalued, fxOps, rates)` (определение `:356`) → `RevaluationReason(legs, manualMinor)` (`:113`). `manualMinor` — остаток, не объяснённый курсами.
- `:320-323` — `HistoryEvent(cur.date, CURRENCY, "Остаток изменили вручную", <валюта, если одна>, reason.manualMinor, kind = REVALUATION)` **без `ref`** → строка не открывается и не удаляется.
- `:324-328` — общая строка «Переоценка валюты» с `parts` и `target`.
- `:126` — подзаголовок «· остаток изменён» в `RevaluationText.subtitle`.
- `domain/model/HistoryRows.kt:128-157` `revaluation(...)` — групповая строка за месяц; `:133` суммирует `manualMinor` по дням, `:147-149` добавляет ту же строку «Остаток изменили вручную» (без target).

**Ручное изменение остатка валюты — откуда данные:**
- Остаток счёта хранится в `accounts`/`LedgerRepository`: `domain/repository/LedgerRepository.kt:25` `setBalance(accountId, balanceMinor, at)`, `:57` `resetBalanceBase`.
- Реализация: `data/repository/LedgerRepositoryImpl.kt:46-49` `setBalance` → `dao.setBalance(...)` (`data/local/LedgerDao.kt:19`), `:76` `resetBalanceBase`.
- Пишется из UI: `ui/capital/CapitalViewModel.kt:64-65` `setBalance(accountId, balanceMinor)` → `ledger.setBalance(..., System.currentTimeMillis())`. Экран: `ui/capital/CapitalScreen.kt:259` `BalanceSheet(...onSave = { vm.setBalance(a.id, it) })`.
- Сам «ручной остаток» не лежит отдельной записью: это разница между текущим остатком и тем, что объясняют курсы/операции. Поэтому событие нельзя открыть по id — нет сущности для ref.
- Валюта: `Currencies.nameOf(it.currency)` (`CapitalEvents.kt:321`); курсы — `FxRate`/`rates` в `reasonOf`. Сумма в минорных единицах (`Money`), FX в `rubOf` (`FxMath`).
- Ручная наличка (для сравнения, уже редактируется): `domain/model/CapitalEvents.kt:224-227` — строка «Наличные» с `ref = HistoryRef.Cash(c.atMs)`; `:227` — без ref, если только текущая наличка.

**Уже есть редактирование/удаление других событий:**
- `domain/model/CapitalEvents.kt:32` `sealed interface HistoryRef` — варианты `Tx`, `Cash(atMs)`, `TopUp(depositId, topUpId)`, `Fx(id)` (ссылки на `HistoryRef.*` в `HistoryOperations.kt:93-201`).
- `domain/usecase/HistoryOperations.kt`:
  - `:55` `pair: HistoryRef.Cash?` (парная наличка при переводе)
  - `:93-121` сборка деталей по ref (`Tx`, `Cash`, `TopUp`, `Fx`)
  - `:140-160` загрузка по ref (`cash.observeChanges().first()` `:145`)
  - `:163-168` проверка (`check`) — покупка, на которую опирается продажа, не удаляется
  - `:179-204` `delete(refs)` — `Cash` → `cash.removeChange(atMs)` (`:193`), `Tx` → `ledger.deleteTransaction` (`:190`), `TopUp`, `Fx`
  - `:236-257` сохранение правок: `saveTx`, `Cash` (`:237`), `TopUp` (`:252`), `Fx` (`:257`)
- UI — `domain`-ui: `ui/history/OperationSheet.kt:174-194` (шторка операции; подписи «Добавил/Взял» для Cash `:181`), `ui/history/CapitalHistoryViewModel.kt:145` `fun delete(refs: List<HistoryRef>)`.
- Данные: `data/repository/FxRepositoryImpl.kt:50` `removeChange(atMs)` (транзакция), `data/local/FxDao.kt:76` `deleteCashChangeAt(atMs)`. Cash-изменения — таблица в `FxDao`/`FxEntity` (не открывал файл сущности).
- Ledger-операции: `LedgerRepositoryImpl.kt:67` `deleteTransaction`.
- Шторки: в `ui/common` есть `BankSheet.kt`, `BankPickSheet.kt`, `BrandDecorEditor.kt` (банки); шторка операции — в `ui/history/OperationSheet.kt`. Общей шторки «редактировать событие истории» в `ui/common` не найдено.

**Чего не хватает для F-55 (наблюдение):** у ручного изменения остатка валюты нет `HistoryRef`, нет сущности, нет `HistoryOperations`-ветки. Редактирование значит сохранение самого `setBalance`-факта (сейчас не сохраняется как событие — остаётся только текущий `accounts.balance`), и удаление — откат остатка, а не delete строки. Решение за архитектором.

**Тесты:**
- `app/src/test/.../domain/CapitalCalculatorTest.kt` (по Grep — упоминает `CapitalCalculator`), есть ли тесты `CapitalEvents`/`HistoryRows`/`HistoryOperations` — не проверял точно. Поиск: Grep по `CapitalEvents\|HistoryRows\|HistoryOperations` в `app/src/test`.

---

## B-119

**Как пользователь создаёт свой банк:**
- `ui/settings/AddBankScreen.kt`, `ui/settings/BankEditorScreen.kt`, `ui/settings/SettingsBanksScreen.kt`, `ui/settings/BankCardScreen.kt`, `ui/settings/NewBanksScreen.kt`.
- `ui/settings/BanksViewModel.kt:124-134` `saveOwn(existing, title, look, picture, usePicture, onDone)`:
  - `:127` ключ `BankBrand.USER_PREFIX + UUID.randomUUID()` (`domain/model/BankBrand.kt:67`, формат `user:<uuid>`)
  - `:128` `logos.make(picture, key)` — файл логотипа (`data/icons/LogoMaker.kt:18`)
  - `:129` `LogoChoice.resolve(...)` (`domain/model/LogoBackground.kt` или рядом — не открывал, найти Grep по `object LogoChoice`)
  - `:132` `banks.add(bank)` / `banks.update(bank)`
- `ui/settings/BanksViewModel.kt:90-93` `add(bank)` — добавляет в `BankRepository` и выставляет пакеты в `settings.setSelectedPackages`.
- `BankRepository` (интерфейс) — `domain/repository/BankRepository.kt` (`add`, `update`, `remove(key)`, `myBanks`, `current`, `cbrEntries`).
- Реализация: `data/settings/BankStore.kt` (класс `BankStore`):
  - `:36-37` `myBanks` = `_mine` (ключи + определения)
  - `:63-66` `add`, `:68-70` `update`
  - `:72-74` `remove(key)` — **только убирает ключ из `_mine`**; определение в `KEY_DEFS` не удаляется.
  - `:83-100` `write(banks)`: `defs = readDefinitions() + changed` — старые определения **никогда не выбрасываются**; `BankDirectory.replace(defs)` (`:98`).
  - `:109-113` `readDefinitions()` — из prefs `KEY_DEFS`.
  - `:42` init → `BankDirectory.replace(readDefinitions())`; `:102-107` `readStored()`.
  - `:119-145` `toJson`/`fromJson`; поле `logo` (`:127`, `:142`) — путь к файлу логотипа.
- Удаление из UI: `ui/settings/BanksViewModel.kt:98-107` `remove(bank)` — по `B-21` банк **убирается из «Мои банки», данные не удаляются**, есть «Вернуть» (undo, `:103-106`). Жёсткого удаления банка и очистки следов в коде не нашёл (Grep по `deleteBank|removeBank|deleteOwn` — пусто).
- Удаление на карточке: `ui/settings/BankCardScreen.kt` (не открывал, найти кнопку «убрать»).
- `BanksViewModel.summary(bankKey)` `:111-116` — «что держит банк» для вопроса при удалении (U-24): `BankSummary.of(...)` (`domain/model/BankSummary.kt`).

**Как распознавание вклада выбирает банк (`RecognizeDepositShot`):**
- `domain/usecase/RecognizeDepositShot.kt:21-54`:
  - `:33` `fileBank = ScreenshotFileName.guessBank(shot.fileName)` — банк по имени файла (`domain/model/ScreenshotFileName.kt:22`).
  - `:43` `DepositParser.parsePasses(passes, brandHint = fileBank)`.
  - `:52` `DepositMatch.find(parsed.brand, d, existing)` — дубликат (`domain/model/DepositMatch.kt:10`).
- `domain/model/DepositParser.kt`:
  - `:134` `parse(rawText, brandHint, today)`; `:137` если `brandHint == null` → `CashbackParser.guessBank(...)`.
  - `:186-219` `parsePasses` — голосование по проходам OCR (`vote { it.brand }`, `:219`), `brandHint ?: vote`.
- `domain/model/CashbackParser.kt`:
  - `:164-170` `guessBank(text)` — первое совпадение `KEYWORDS` по нормализованному тексту.
  - `:173-176` `KEYWORDS` = `BankBrand.all.flatMap(bankKeywords)` — **без фильтра по «убран из Мои банки»**. Комментарий `:172`: «user's own banks join the list as they're added (F-22)».
  - `:179` `bankKeywords(b)` — ключевые слова банка (название, буквы, ключи).
- `domain/model/BankBrand.kt`:
  - `:113` `val all = known + BankDirectory.extra()` — источник для парсеров.
  - `:116` `fromPackage(pkg)`: `(BankDirectory.extra() + known)`.
  - `:121` `fromKey(key)`: `BankDirectory.find(key) ?: known...` — по ключу `user:…` найдётся удалённый банк, если его определение осталось в `KEY_DEFS`.
  - `:148` `object BankDirectory` (`extra()`, `find`, `replace`), `:155` `find(key)`.
- Сопутствующий путь: `domain/model/BankSignal.kt`, `NewBanks.kt` (`:31` `decide(...)` — «новый банк» по пакету/тексту, использует `BankBrand`; учитывает `ownBsb` в тестах).
- `domain/usecase/NewBankAnswers.kt:18`, `MyBanksScope.kt`, `ui/common/MyBanks.kt` — «мои банки» как область; не проверял, учитывают ли они удалённые.
- **Эмбеддер не участвует в выборе банка.** `OnnxTextEmbedder` (`data/embed/OnnxTextEmbedder.kt:17`) через `TextEmbedder` (`domain/repository/TextEmbedder.kt:8`) используется только в `domain/model/CategoryResolver.kt:19,61,70` (категории операций). Эмбеддингов банков нет.

**Где могут остаться следы удалённого банка «ВК»:**
| Место | Что остаётся | Где |
|---|---|---|
| Определение банка | `KEY_DEFS` в SharedPrefs `banks` — `write()` не удаляет (`BankStore.kt:93`) | `data/settings/BankStore.kt:72-74`, `:83-100` |
| Каталог в памяти | `BankDirectory.extra()` включает удалённого (`BankBrand.kt:113`) | `domain/model/BankBrand.kt:113,116,121,148` |
| Ключевые слова распознавания | `CashbackParser.KEYWORDS` из `BankBrand.all` (`:173-176`) | `domain/model/CashbackParser.kt:164-176` |
| Логотип | файл `logos.make(picture, key)` — не удаляется при `remove` | `ui/settings/BanksViewModel.kt:128`, `data/icons/LogoMaker.kt:18`, `data/icons/BankIconCache.kt:28`, `data/backup/LogoBackup.kt` |
| Кэш иконок/акцентов | `BankIconCache`, `ui/common/BankAccentCache.kt` | `data/icons/BankIconCache.kt`, `ui/common/BankAccentCache.kt` (не открывал) |
| Данные банка | `brandKey = user:…` в `accounts`/`deposits`/`cashback`; `remove` их не трогает (B-21) | `data/local/*Entity` (`brandKey`), `BanksViewModel.kt:98-107` |
| Глобальный кэш вкладов | `DepositShotDao`, `DepositEntity` с `brandKey` — остаются | `data/local/DepositShotDao.kt`, `data/local/DepositDao.kt` |
| Резерв в бэкапе | `data/backup/LogoBackup.kt`, `BackupManager` (определения банков в бэкапе) | `data/backup/` (не открывал) |

**Почему «ВК» выбирается распознаванием (вывод по коду, не проверено на устройстве):** `CashbackParser.KEYWORDS` берёт `BankBrand.all`, а `all` включает `BankDirectory.extra()`, где определение `user:…` осталось после `remove`. Ключевое слово «вк» (короткое, из `bankKeywords`) может совпасть с OCR-текстом скрина вклада. `DepositParser.parse` берёт `CashbackParser.guessBank` (`DepositParser.kt:137`) и голосование (`:219`). Короткая аббревиатура — риск ложного совпадения (см. `bankKeywords` `CashbackParser.kt:179`; не проверял, есть ли минимальная длина).

**Тесты:**
- `app/src/test/.../domain/NewBanksTest.kt:13-43` — `ownBsb`, `NewBanks.decide`, `similar`.
- `app/src/test/.../domain/BankBrandTest.kt:14`, `:42-44`.
- `app/src/test/.../domain/CapitalCalculatorTest.kt:76,104` — `BankBrand.SBER`, `BankBrand.BSB`.
- `app/src/test/.../domain/BankSignalTest.kt:21`.
- Тестов на `CashbackParser.guessBank` с удалёнными банками, на `DepositParser` с банками пользователя и на `RecognizeDepositShot` (`app/src/test/.../domain/`) в этом проходе не найдено — Grep по `RecognizeDepositShot|guessBank` в `app/src/test` не делался.

---

## Сводные проверки (не выполнялись)
- `RepairFromJournal` вызывается где-то кроме DI? — Grep по `repairFromJournal` в `ui/` и `service/`.
- Тесты `CashbackParser.guessBank`, `RecognizeDepositShot`, `HistoryOperations`, `HistoryRows` — Grep по `app/src/test`.
- Минимальная длина ключевого слова в `bankKeywords` (`CashbackParser.kt:179-`) — прочитать.
- `LogoChoice` — файл не открыт.
