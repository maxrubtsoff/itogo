# Карта кода цикла 13

Корень кода: `app/src/main/java/ru/maxrubtsoff/bankagg/` (ниже сокращено `…/`). Тесты — `app/src/test/java/...`.
Проверено: существование всех файлов и строки в §1–§2 (кроме помеченных «по тексту»). Строки, помеченные «не проверено», взяты из пункта без чтения кода.

## 1. Пункты с доменной логикой (нужны JVM-тесты)

| Пункт | Файлы для правки (от корня) | Ключевые file:line | Домен? | Что в коде |
|---|---|---|---|---|
| B-82 | `app/src/main/java/ru/maxrubtsoff/bankagg/domain/parser/NotificationParser.kt` | :73-76 MASK; :160-161 `op = hits.firstOrNull{…} ?: plainAmount(...)`; :192-198 `plainAmount` | да | Подтверждено: при отсутствии валютной метки берётся первое голое число до баланса, `NUM`-поиск без проверки контекста. MASK не покрывает `··`, `Счёт 4081…` с пробелом. |
| B-83 | `…/domain/parser/NotificationParser.kt` | :103 SIGNED_AMOUNT; :225-227 в `detectDirection` | да | Подтверждено: `SIGNED_AMOUNT` ловит `+` перед телефоном (`(?<![\d.,])` не отсекает `+7`). |
| B-84 | `…/domain/parser/NotificationParser.kt` | :94-97 IN_MARKS (нет «входящий»); :229 запасное `TRANSFER && counterparty ⇒ OUT` | да | Подтверждено: «перевод от» ловится только как подстрока, «входящий» отсутствует. |
| B-85 | `…/domain/model/CapitalEvents.kt`; `…/domain/model/DepositMath.kt` (если нужна проверка даты) | CapitalEvents :242-246 (`own` = principal/topUps только при `date == cur.date`); :268-269 (`dealt` только при `fxOps.date == cur.date`) | да | Подтверждено: взнос в «вложения» учитывается только в день точки; задним числом он попадает в «Проценты» (:246-261) и «Переоценку» (:268-283). |
| B-86 | `…/domain/model/CapitalEvents.kt`; `…/ui/common/HistoryLine.kt` | CapitalEvents :165-176 (`amountMinor` без валюты), :181-190 `fxOps` (foreign есть), :209 `moved()` вызывается всегда; HistoryLine :102 `signedSum(...)` (₽) | да (CapitalEvents) + UI | Подтверждено: суммы валютных операций в `transactions` (`t.currency != RUB`) идут в `HistoryEvent` без `foreign` и с ₽-форматом; `moved()` даёт повторную «Переоценку». |
| B-85 | см. выше | | | |
| B-79 | `…/domain/model/CapitalEvents.kt` (`deposits.forEach` :193-201 — дата точки по `d.startDate`); точка графика — не проверено (искать `CapitalReconstruction.kt`, `RecordCapitalDay.kt`, `ui/capital`) | CapitalEvents :196 `HistoryEvent(d.startDate, …)` | да | По тексту: история использует `startDate`; источник «точки по дате добавления» — не проверено (вероятно `RecordCapitalDay.kt`/`CapitalReconstruction.kt`). |
| B-80 | `…/domain/usecase/RecognizeDepositShot.kt` (не проверено); `…/domain/model/DepositParser.kt` (не проверено); `…/data/ocr/` (не проверено) | — | да (парсер) + OCR | По тексту: распознавание карточек вклада — `DepositParser` (домен) поверх OCR (`data/ocr`). Конкретные строки не проверены. |
| B-87 | `…/domain/usecase/NewBankAnswers.kt:40-45` (`link`: `banks.add(bank)` в обеих ветках); `…/data/settings/BankStore.kt` (не проверено, :87-91 по тексту) | NewBankAnswers :41-45 | да | Подтверждено: `link` вызывает `banks.add(bank)` (с `BankBrand`, не с пакетом) — пакет приложения не передаётся. Пакет не сохраняется в BankStore (по тексту). |
| B-88 | `…/data/backup/BackupManager.kt` | :278, :327, :339, :354, :380, :394 — экспорт `put("…createdAt")`; импорт :560-640 (`optLong/getLong`). Поля `excluded` в экспорте/импорте нет (grep по `excluded` в BackupManager.kt пуст) | нет (data) | Подтверждено: поле `excluded` не сериализуется в BackupManager. Точные строки аккаунтов (по тексту 261-266 / 568-578) — не проверены. |
| B-90 | `…/domain/usecase/RepairFromJournal.kt` (44-48, 72-75 по тексту) | — | да | По тексту: стартовый баланс из `checkedAt`. Строки не проверены. |
| B-92 | `…/domain/usecase/RecordNotification.kt:47-49` | :47-49 `DEDUP_WINDOW_MS`, `events.hasSimilar(content, postedAt ± …)` | да (use-case) | Подтверждено: окно ±DEDUP_WINDOW_MS по `content` (текст + сумма + баланс); без баланса в пуше склейка. |
| B-95 | `…/domain/model/Subscription.kt:174-180` (`upcoming`), `:15` (поле `nextDate`); `…/domain/model/Subscription.kt` `period.next(d)` | :175-179 — цепочка `s.period.next(d)` от `s.nextDate` | да | Подтверждено: `upcoming` шагает от текущей даты через `period.next` (накопительный сдвиг до 28-го). Исходный день в модели не хранится. |
| B-98 | `…/domain/model/DepositMath.kt:49-55` (`project`: фильтр `!isBefore(start) && isBefore(end)` :52), `:152, :162` (`investedTotal`, не проверено) | :52 | да | Подтверждено: `project` отбрасывает пополнения вне [start, end). Строки 152/162 — по тексту. |
| B-100 | `…/domain/model/AppLock.kt:73-76` (`waitLeft`) | :76 `(lastFailAt + pause - now).coerceIn(0L, pause)` | да | Подтверждено: при `now` в будущем (`lastFailAt + pause − now` < 0) результат 0 → пауза сразу закончилась (комментарий :69-71 обещает иное). |
| B-101 | `…/domain/usecase/RecordCapitalDay.kt:31-34`, `…/domain/model/CapitalReconstruction.kt:91` | — | да | По тексту. Не проверено. |
| B-97 | `…/domain/usecase/MyBanksScope.kt:23-31` | :23-24 `only(items, mine, brandOf)`; :29-31 `Ledger.observeAccounts` фильтрует только счета | да | Подтверждено: `observeAccounts` ограничен, `observeTransactions` (в `Ledger`, ниже :31) — не проверено. Вьюмодель `ui/history/CapitalHistoryViewModel.kt` — не проверено. |
| B-96 | `…/data/notify/ReminderStore.kt:33-37` | :35 `(sent() + ids).toList().takeLast(MAX_SENT).toSet()` | нет (data, но логика) | Подтверждено: порядок — из HashSet, а не по возрасту. |
| B-89 | `…/data/backup/AutoBackup.kt:81` | :81 `TABLES` без `deposit_topups` | нет | Подтверждено: в списке `accounts, transactions, cashback, deposits, fx_operations, cash, cash_changes, subscriptions` — `deposit_topups` отсутствует. |
| B-99 | `…/ui/subscriptions/SubscriptionsViewModel.kt:62-71` | :64-66 — `repo.save(synced)` внутри `combine` | нет (VM) | Подтверждено: `synced` строится из `s` из `all`-снимка; перезаписывает правку `setRemind`. |
| F-51 | `…/ui/common/HistoryLine.kt`, `…/ui/history/CapitalHistoryViewModel.kt`, `…/domain/model/CapitalEvents.kt` (`HistoryTarget`, `HistoryKind` :168-204 — цели `Currency`/`Deposit`/`Section`), `…/ui/capital/*` (шторка операции — не проверено), `…/domain/model/CashChange` (правка/удаление наличных — не проверено) | CapitalEvents :204 наличные → `HistoryTarget.Section(CASH)`; :207 строка «изменили вручную» без дельты; :168-170 счёт | да (модель истории) + UI | Подтверждено: для наличных и счетов цель — раздел (:204, :168 `target=null` для RUB), а не сама операция. |
| U-67 | `…/ui/capital/*` график+список — файлы не проверены (Glob по `CapitalScreen`/`*Chart*` не делался) | — | нет | По тексту: прокрутка к операции по выбору точки на графике. Конкретные файлы не проверены. |
| B-81 | `…/ui/capital/*` (подписи точек графика, F-49) — не проверено; `ui/theme` (Slitok, `AppTypography`) | — | нет | По тексту. |

## 2. Чисто UI / Android / data (без JVM-домена)

| Пункт | Файлы для правки | Ключевые file:line | Что в коде |
|---|---|---|---|
| B-91 | `…/data/local/AppDatabase.kt:129-140`, `…/data/local/DatabaseCipher.kt:94-99`, `…/data/security/PhoneKeys.kt:30-41` (не проверено) | AppDatabase :132-135 — `passphrase == null` → стартовать пустой БД (файл не удаляется в этом фрагменте, удаление — по тексту `:128-133`); DatabaseCipher :96 `commit` ключа; :97-98 `getOrNull()` (любое исключение → null) | Подтверждено: при `null` от `DatabaseCipher.prepare` БД открывается пустой; `getOrNull` скрывает сбой Keystore. Точка стирания файла — в `AppDatabase.kt` ниже :135 (не проверено). |
| B-93 | `…/ui/lock/LockScreen.kt:85` | :84 `forgot`, :85 `settingNew` (`rememberSaveable`) | Подтверждено: флаг `settingNew` переживает смерть процесса, `forgot` — нет. |
| B-94 | `…/ui/MainActivity.kt:368-373` (`unlock`), `:375-382` (`onStart`) | :370 `session.unlocked = true` только в `unlock()`; :380 `AppLock.needsUnlock(...)` | Подтверждено: включение пина не вызывает `unlock()`/`session.unlocked = true`. |
| B-102 | `…/ui/MainActivity.kt:302-303` (`enableEdgeToEdge()`) | :303 | Подтверждено: вызов без аргументов (стиль по умолчанию). Нужен `SystemBarStyle.dark(...)`. |
| B-103 | `…/ui/MainActivity.kt:526-548` | :526 `collectAsStateWithLifecycle(initialValue = emptySet())`; :546-548 `LaunchedEffect(onboarded, setupSeen)` → `pendingTour = GENERAL` | Подтверждено: до прихода `toursDone` пустое значение → подсказка открывается повторно. |
| B-104 | `…/ui/*` нижняя панель (`MainActivity.kt`, файл навигации; «Настройки» — не проверено) | — | По тексту (walk/75.png). Файл/строка не проверены. |
| B-105 | `…/ui/overview/*` (плашка «Баланс не обновляется») | — | По тексту. Не проверено. |
| B-106 | `…/ui/cashback/*` (редактор кэшбэка, «Вручную») | — | По тексту. Не проверено. |
| B-87 (UI-часть) | `…/data/settings/BankStore.kt` (не проверено) | — | см. §1. |

## 3. Пересечения файлов

| Файл | Пункты |
|---|---|
| `…/domain/parser/NotificationParser.kt` | B-82, B-83, B-84 (+ B-86 косвенно: вторая причина +20,26 млн) |
| `…/domain/model/CapitalEvents.kt` | B-85, B-86, F-51 (+ B-79 по дате вклада) |
| `…/ui/common/HistoryLine.kt` | B-86, F-51 |
| `…/ui/history/CapitalHistoryViewModel.kt` | B-97, F-51 |
| `…/domain/model/DepositMath.kt` | B-85 (опц.), B-98 |
| `…/domain/usecase/RecordCapitalDay.kt` / `…/domain/model/CapitalReconstruction.kt` | B-79 (опц.), B-101 |
| `…/domain/model/Subscription.kt` | B-95 |
| `…/ui/subscriptions/SubscriptionsViewModel.kt` | B-99 |
| `…/domain/usecase/MyBanksScope.kt` | B-97 |
| `…/data/backup/BackupManager.kt` | B-88 |
| `…/data/backup/AutoBackup.kt` | B-89 |
| `…/data/settings/BankStore.kt` | B-87 |
| `…/domain/usecase/NewBankAnswers.kt` | B-87 |
| `…/domain/usecase/RepairFromJournal.kt` | B-90 (зависит от B-82) |
| `…/domain/usecase/RecordNotification.kt` | B-92 |
| `…/data/local/AppDatabase.kt` | B-91 |
| `…/data/local/DatabaseCipher.kt` | B-91 |
| `…/ui/lock/LockScreen.kt` | B-93 |
| `…/ui/MainActivity.kt` | B-94, B-102, B-103, B-104 (нижняя панель — не проверено) |
| `…/domain/model/AppLock.kt` | B-100 |
| `…/data/notify/ReminderStore.kt` | B-96 |
| `…/domain/parser/NotificationParser.kt` ↔ `…/domain/usecase/RepairFromJournal.kt` | B-82 + B-90 |

Итог по пересечениям: `NotificationParser.kt` (B-82/83/84) и `CapitalEvents.kt` (B-85/86/F-51) — самые связанные; `MainActivity.kt` трогают четыре пункта (B-94, B-102, B-103, B-104).

## 4. Не проверено
- Точные строки B-79, B-80, B-88 (экспорт аккаунтов), B-90, B-101, B-104–B-106, U-67, B-81, F-51 (шторка и правка наличных/счетов).
- Файлы UI-капитала (`ui/capital`) и OCR-распознавания вкладов (`RecognizeDepositShot`, `DepositParser`) не открывались.
