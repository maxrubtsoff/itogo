# Планы цикла 16

Пути от `app/src/main/java/ru/maxrubtsoff/bankagg/`, тесты — `app/src/test/java/ru/maxrubtsoff/bankagg/domain/`. Строки — по `cycle-16-map.md`.

## F-55

**Цель:** тап по строке «Остаток изменили вручную» в истории капитала открывает шторку со счетами этой валюты и их текущим остатком; остаток можно поправить, после чего строка пересчитывается (или пропадает).

**Факт:** истории ручных правок нет — `setBalance` (`CapitalViewModel.kt:64-65`) перезаписывает `accounts.balance`, строка — производная разница (`CapitalEvents.kt:320-323`). «Откатить к прошлому значению»/«поменять дату» без новой таблицы невозможны.
**Решение:** без миграции — исправление через текущий остаток. Удаление = поставить остаток так, чтобы разница ушла; это делает пользователь вводом суммы. Таблицу истории правок не заводим (P2, ручная правка редка; при необходимости — отдельный пункт в ROADMAP).

1. **Домен, модель** — `domain/model/CapitalEvents.kt:32-37`: добавить `data class Balance(val currency: String) : HistoryRef`. В `:320-323` строке «Остаток изменили вручную» ставить `ref = HistoryRef.Balance(cur)`, где `cur` — валюта единственной ноги (`reason.legs.singleOrNull()`); если ног несколько/ноль — валюта счёта(ов), изменившего остаток, иначе `ref = null` (как сейчас). Вынести выбор валюты в чистую функцию `ManualBalance.currencyOf(legs, accounts): String?` (новый `domain/model/ManualBalance.kt`): одна нога → её валюта; иначе — единственная не-RUB валюта среди неисключённых счетов; иначе `null`.
   - `domain/model/HistoryRows.kt:147-149` (групповая строка за месяц): тот же `ref`, если во всех днях месяца валюта одна, иначе `null`.
2. **Домен, выбор счетов** — в `ManualBalance.kt` `fun accountsOf(currency, accounts): List<Account>` — неисключённые счета этой валюты, сортировка по банку/названию.
3. **Use-case** — `domain/usecase/HistoryOperations.kt`:
   - `:93-121` деталь по ref: ветка `Balance` → `HistoryDetail.Balance(currency, accounts = ManualBalance.accountsOf(...))` (новый вариант детали рядом с остальными).
   - `:179-204` `delete`: `Balance` не удаляется — игнорировать (кнопки удаления в шторке нет).
   - `:236-257` сохранение: `saveBalance(accountId, Money)` → `ledger.setBalance(accountId, minor, now)` (тот же путь, что `CapitalViewModel.setBalance`; `now` — из уже используемого в use-case источника времени).
   - Репозиторий не меняется (`LedgerRepository.setBalance` `:25` есть).
4. **Room / data / DI** — без изменений, миграции нет.
5. **ViewModel** — `ui/history/CapitalHistoryViewModel.kt` (около `:145`): `fun setBalance(accountId: String, minor: Long)` → use-case.
6. **Factory** — без изменений.
7. **Screen** — `ui/history/OperationSheet.kt:174-194`: ветка для `HistoryDetail.Balance`: заголовок «Остаток <валюта>», список счетов (банк + текущий остаток `MoneyText`), тап по счёту → существующий `BalanceSheet` (`ui/capital/CapitalScreen.kt:259`; если он `private` — перенести в `ui/common/BalanceSheet.kt` без изменения вида). Один счёт — сразу поле остатка. Без кнопки «Удалить». Вид — по макету designer.
8. **Тесты** — `ManualBalanceTest`:
   - `single_leg_gives_its_currency`, `many_legs_one_foreign_account_gives_account_currency`, `ambiguous_gives_null`, `accounts_of_skip_excluded_and_other_currencies`.
   - `CapitalEvents`-тест (в существующем файле тестов истории или новом `CapitalEventsManualRefTest`): строка ручного изменения USD получает `HistoryRef.Balance("USD")`; после выставления остатка, равного объяснённому, строка не появляется (`manualMinor == 0`).

**Критерии:** `./gradlew testDebugUnitTest assembleDebug` зелёные; деньги только `Money`/минорные; домен без Android. На устройстве: История → Валюта → «Остаток изменили вручную» открывается, правка остатка USD меняет/убирает строку и капитал.

## B-119

**Цель:** пользовательский банк, убранный из «Мои банки», не угадывается при распознавании вклада и кэшбэка; «Вернуть» (B-21) и показ старых данных по `user:…` продолжают работать.

**Решение:** определение не удаляем (на нём держатся undo, `fromKey` для старых данных, бэкап) — разделяем «известен» и «распознаётся». Угадывание берёт встроенные банки + только те `user:…`, что сейчас в «Мои банки». Чистку логотипа при убирании не делаем: это сломает undo (B-21); файл маленький, приватность не страдает.

1. **Домен** — `domain/model/BankBrand.kt`:
   - `object BankDirectory` (`:148`): поле `active: Set<String>` (ключи пользовательских банков в «Мои банки»); `replace(defs, activeKeys: Set<String>)` (второй параметр с дефолтом «все defs» — для тестов и бэкапа).
   - Рядом с `:113`: `val recognizable: List<BankBrand> get() = known + BankDirectory.extra().filter { it.key in BankDirectory.active }`. `all`, `fromKey`, `fromPackage` не трогать.
   - Чистая функция `BankDirectory.recognizableOf(known, extra, activeKeys)` — под тест.
2. **Парсеры** — `domain/model/CashbackParser.kt:173-176`: `KEYWORDS` из `BankBrand.recognizable` вместо `BankBrand.all` (покрывает и `DepositParser.kt:137`, и `CashbackParser.kt:41`). `domain/model/ScreenshotFileName.kt:22` `guessBank` — если перебирает `BankBrand.all`, заменить на `recognizable`.
3. **Репозиторий/data** — `data/settings/BankStore.kt`: в `:42` (init), `:98` (`write`) передавать в `BankDirectory.replace(defs, activeKeys = ключи _mine с префиксом user:)`; в `remove` (`:72-74`) — через `write`, чтобы `active` обновился (undo `add` вернёт ключ обратно → банк снова распознаётся). Интерфейс `BankRepository` не меняется.
4. **Room / DI / VM / Factory / Screen** — без изменений, вид не меняется.
5. **Тесты** — `BankDirectoryRecognizeTest`:
   - `removed_user_bank_not_guessed_in_cashback` — defs с «ВК», active пуст → `CashbackParser.guessBank("… ВК …")` == null/не ВК.
   - `removed_user_bank_not_guessed_in_deposit` — `DepositParser.parse` текста вклада с «ВК» → `brand` не ВК.
   - `active_user_bank_is_guessed` — active содержит ключ → угадывается.
   - `removed_user_bank_still_found_by_key` — `BankBrand.fromKey("user:…")` находит (старые данные, undo).
   - `undo_restores_recognition` — replace с ключом → снова угадывается.
   - Тесты сбрасывают `BankDirectory.replace(emptyList())` в `@After`.

**Критерии:** `./gradlew testDebugUnitTest assembleDebug` зелёные; встроенные банки распознаются как раньше (существующие тесты `CashbackParser`/`DepositParser` не меняются); домен без Android. На устройстве: создать банк, убрать, распознать скрин вклада — банк не подставляется; «Вернуть» — снова подставляется.
