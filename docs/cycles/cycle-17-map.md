# Карта цикла 17 (F-56, B-120, B-121, B-122, B-123)

Построена explorer-агентом на ветке `cycle-17`, 2026-11-06. Только чтение. Номера строк — на момент карты; «не проверено» — место, которое не читалось целиком.

## F-56 / B-120 — виджет кэшбэка (Glance)

### Файлы и точки входа
| Путь:строка | Что там |
|---|---|
| `app/src/main/java/ru/maxrubtsoff/bankagg/ui/widget/CashbackWidget.kt:66` | `class CashbackWidget : GlanceAppWidget()`, `SizeMode.Exact` (68) |
| `CashbackWidget.kt:70-75` | `MAX_CHILDREN = 10`, `BLOCK_ROWS = 8` (лимиты Glance на Row/Column) |
| `CashbackWidget.kt:78-99` | модели: `Logo`, `WidgetRow`, `BaseChip`, `BankBlock`, `Model(month, rows, cardRows, base, banks, missing, nextHint, cashbackOff)` |
| `CashbackWidget.kt:101-104` | `provideGlance` → `load` → `provideContent { Content(model) }` |
| `CashbackWidget.kt:108-147` | `load`: данные из `repo.observeMonth(key).first()`, `CashbackSpotlight.build(items, hasNext, today)` (121), логотипы (124-131), `rows` (132), `base` (133), `banks` (134-143), `cardRows` (145) |
| `CashbackWidget.kt:116-117` | при выключенном кэшбэке (`features.showCashback`) — `cashbackOff = true` |
| `CashbackWidget.kt:149-156` | `Content`: `LocalSize` → `CashbackWidgetLayout.grid(w, h, baseLine)` (155) → `mode(grid, cardRows)` (156) |
| `CashbackWidget.kt:224-241` | `CategoryGrid` — раскладка «одна/две колонки» по строкам (`line[0]`, `line[1]`) |
| `CashbackWidget.kt:243-268` | `BankBlocks` — большой виджет: блоки банков, `CashbackWidgetLayout.pack` (247), «+N банков» — внутри (не читано целиком) |
| `CashbackWidget.kt:269-302` | `BankBlockView` — блок банка (logo + категории) |
| `CashbackWidget.kt:374` | `class CashbackWidgetReceiver : GlanceAppWidgetReceiver()` |
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/CashbackWidgetLayout.kt:13` | `object CashbackWidgetLayout` — вся геометрия (dp, чистая) |
| `CashbackWidgetLayout.kt:15-24` | константы: `PADDING 14`, `HEADER 26`, `LINE 20`, `GAP 8`, `BASE_LINE 28`, `COLUMN_GAP 12`, `PERCENT_WIDTH 46`, `LOGO 20` |
| `CashbackWidgetLayout.kt:27` | `TWO_COLUMNS_FROM = 290f` (внутренняя ширина) |
| `CashbackWidgetLayout.kt:30` | `BANKS_FROM_LINES = 7` |
| `CashbackWidgetLayout.kt:34-38` | `data class Grid(columns, columnWidth, lines)`, `slots`, `nameWidth` |
| `CashbackWidgetLayout.kt:40-48` | `grid(widthDp, heightDp, baseLine)`: колонки, `lines = ((free + GAP) / (LINE + GAP)).toInt()` (46) — расчёт вместимости по высоте |
| `CashbackWidgetLayout.kt:54-58` | `mode(grid, cardRows)`: BANKS (2 колонки и ≥7 строк) / MEDIUM (`slots > cardRows`) / SMALL |
| `CashbackWidgetLayout.kt:60-88` | `pack(blockLines, columns, lines)`: блоки в наименее заполненную колонку; если что-то не влезло — повторная раскладка с `lines - 1` (86) под «+N» |
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/CashbackSpotlight.kt:80` | `build(items, hasNextMonth, today): Spotlight` — порядок строк |
| `CashbackSpotlight.kt:96` | `regular.sortedWith(...)` — ранжирование (доменная сортировка категорий, НЕ по банку) |
| `CashbackSpotlight.kt:42-44` | `isBase`, `weightOf` (веса по группе/банку) |
| `CashbackSpotlight.kt:123-129` | `baseGroups` — группы базового процента, `sortedByDescending { percent }` |
| `CashbackWidget.kt:140` | сортировка категорий внутри блока банка: по `percent` убыв., затем `category` (виджет, не CashbackSpotlight) |
| `CashbackWidget.kt:143` | сортировка блоков банков: по числу строк убыв., затем `title` |
| `CashbackWidget.kt:132` | плоский список `rows` для узкого виджета — порядок из `spot.allRows` (т.е. ранжирование CashbackSpotlight, по банку не группируется) |

### Настройки виджета и обновление
- **Настроек виджета нет.** В `data/settings` по `widget` ничего не найдено (grep пустой). Сортировки/режима в хранилище сейчас нет — F-56 потребует нового места хранения.
- **Обновление:** `provideGlance` вызывается системой (нет явного `updateAll`/`refresh` в файле — не проверено по всему проекту). Данные читаются из Room (`App.instance.container.cashbackRepository`), сети нет.
- Открытие приложения по тапу: `clickable` в `Content` (около 164, не читано до конца).

### Тесты рядом
- `app/src/test/java/ru/maxrubtsoff/bankagg/domain/CashbackWidgetLayoutTest.kt:9` (`class CashbackWidgetLayoutTest`) — grid/mode/pack.
- `app/src/test/java/ru/maxrubtsoff/bankagg/domain/CashbackSpotlightTest.kt:14` (`class CashbackSpotlightTest`).
- Виджет (Glance) на JVM не тестируется; проверка — `device-check`.

### Чего нет
- Доменной сортировки виджета «по банку / по названию / по размеру» (F-56): сейчас только `CashbackSpotlight.kt:96` (по весу/проценту) и `CashbackWidget.kt:140,143` (локальные сортировки в UI-файле — логика в UI, нарушает правило домена, если переносить).
- Расчёт «+N банков» (`pack`) уже в домене — но `CashbackWidgetLayout.kt:86` оставляет одну строку под «+N» всегда, если что-то скрыто; это причина пустого места/потери строк в B-120 (гипотеза, не проверено на устройстве).

---

## B-121 — История капитала «Валюта»: ручная правка не удаляется

### Где рождается событие
| Путь:строка | Что там |
|---|---|
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/CapitalEvents.kt:105-117` | `RevaluationLeg`, `RevaluationText` (подпись «Переоценка валюты») |
| `CapitalEvents.kt:114-115` | `data class RevaluationReason(legs, manualMinor)` — `manualMinor` = остаток сверх курсов |
| `CapitalEvents.kt:270-275` | «Проценты» и «Переоценка валюты» — разница между точками, кроме сделок |
| `CapitalEvents.kt:309-312` | исключения: валютные счета в рублях (B-86), открытие счёта (B-109) — не переоценка |
| `CapitalEvents.kt:317-327` | `HistoryEvent(cur.date, CURRENCY, "Переоценка валюты", ...)`; при `reason.manualMinor != 0` добавляется `manualEvent` (322); если `legs` пуст и есть только ручная часть — `ref = HistoryRef.Balance(...)` (324-327), иначе `ref = null` |
| `CapitalEvents.kt:336-342` | `manualEvent(...)`: «Остаток изменили вручную» (F-55), `kind = REVALUATION`, `ref = HistoryRef.Balance(currency)` (342) |
| `CapitalEvents.kt:364-398` | расчёт `manual`: `rest = …`; `manual = 0` если `|rest| < max(100, |revalued|/100)` (397); иначе `manual = rest` (398). **Ручная правка НЕ хранится как запись — выводится из разницы остатков и курсов.** |
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/HistoryRows.kt:127-155` | `revaluation(...)`: сворачивает события месяца в одну строку; `manual` суммируется (130-133); отдельная строка «Остаток изменили вручную» (150-152) с `HistoryRef.Balance` |
| `HistoryRows.kt:180` | подпись группы `"Переоценка"` / `"Переоценка валюты"` |

### Тап и шторка
| Путь:строка | Что там |
|---|---|
| `app/src/main/java/ru/maxrubtsoff/bankagg/ui/history/OperationSheet.kt:78-79` | `if (event.ref is HistoryRef.Balance) BalanceFlow(event, vm, onDismiss)` — тап по ручной правке уходит в шторку «Остаток» |
| `app/src/main/java/ru/maxrubtsoff/bankagg/ui/history/BalanceFlow.kt:27,31` | `BalanceFlow` — «Остаток изменили вручную» (F-55): один счёт → его остаток |
| `app/src/main/java/ru/maxrubtsoff/bankagg/ui/history/CapitalHistoryViewModel.kt:156` | `setBalance(accountId, minor, restored)` — правка остатка из шторки |
| `app/src/main/java/ru/maxrubtsoff/bankagg/ui/capital/CapitalViewModel.kt:64` | `setBalance` (экран «Капитал») |

Объяснение симптома «тап ведёт в остаток счёта» (по коду, не проверено на устройстве): событие «Переоценка валюты» с одной только ручной частью получает `ref = HistoryRef.Balance` (CapitalEvents.kt:327) → `OperationSheet.kt:78` открывает `BalanceFlow`, а не удаление.

### Удаление / отмена
| Путь:строка | Что там |
|---|---|
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/usecase/HistoryOperations.kt:66-67` | `class BalanceWas(old: Account)` — снимок остатка «до правки» (только в памяти сессии) |
| `HistoryOperations.kt:142-147` | `setBalance(accountId, balanceMinor, counted)` → `ledger.setBalance` / `setCountedBalance` |
| `HistoryOperations.kt:156-159` | `restoreBalance(was)` → `ledger.restoreBalance(...)` — «Вернуть» (F-55), **откат только сразу после правки** |
| `HistoryOperations.kt:243` | `delete(refs)`: `is HistoryRef.Balance -> Unit` — **ручную правку удалить нельзя** («правится, а не удаляется») |
| `HistoryOperations.kt:163-196` | `preview(refs)`: `is HistoryRef.Balance -> Unit` (193) — для Balance нет расчёта «что станет» |
| `HistoryOperations.kt:199-212` | `check(refs)`: Balance не проверяется |

- **В репозитории удаления ручной правки нет.** Хранится только текущий остаток счёта (`accounts.balanceMinor`, `updatedAt`, `checkedAt`, `estimated`) — см. `LedgerDao.kt:19` (`setBalance`), `LedgerRepositoryImpl.kt:46,52` (`setBalance`, `restoreBalance`), `domain/repository/LedgerRepository.kt:25,28`. Истории правок как записей нет, поэтому удалить «ту самую» правку нельзя без новой сущности или пересчёта из журнала.
- Пересчёт остатка из журнала: `RepairFromJournal` (см. B-123), `RepairBase.kt:25-32`.

### Связь с F-55 (цикл 16)
- Тот же механизм: `manualEvent` (CapitalEvents.kt:336-342), `ManualBalance` (`domain/model/ManualBalance.kt:13`; `ManualBalance.rows` — `ManualBalance.kt:42-49`), `BalanceDetail` (`HistoryOperations.kt:63-64`), `HistoryOperations.kt:133-135`.
- Тесты: `app/src/test/java/ru/maxrubtsoff/bankagg/domain/ManualBalanceTest.kt:16`, `CapitalEventsTest.kt:94` (`revaluationWithManualRemainder`), `:111` (`revaluationWithoutRatesFallsBackToManual`), `:251`, `:258`, `:268`; `HistoryRowsTest.kt:46, 137, 162, 174`; `HistoryOperationsTest.kt:45` (класс).

### Тесты рядом
- `CapitalEventsTest.kt` (`app/src/test/java/ru/maxrubtsoff/bankagg/domain/CapitalEventsTest.kt`) — переоценка, ручная часть.
- `HistoryRowsTest.kt`, `HistoryOperationsTest.kt`, `ManualBalanceTest.kt`.

---

## B-122 — Альфа-Бизнес: «Поступило … счет … от … Остаток …» не разбирается

Текст: `Поступило 322581.00 счет *4863 от ООО "Бета" счет *1007. Остаток 325218.58`

### Почему не парсится (по коду)
| Путь:строка | Что там |
|---|---|
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/parser/NotificationParser.kt:169` | `parse(title, text, defaultCurrency)` |
| `NotificationParser.kt:181-182` | `hits = plain.ifEmpty { orderHit(blob) }` — `plain = hits(clean)` (222) требует знак валюты рядом с суммой; в тексте знака валюты нет → пусто |
| `NotificationParser.kt:99-102` | `ORDER_AMOUNT` — ищет **только** `(списано|зачислено)`, затем сумму, затем `счет *dddd`. Слово «Поступило» не входит → **нет совпадения** |
| `NotificationParser.kt:217-220` | `orderHit` — опирается на `ORDER_AMOUNT` → пусто |
| `NotificationParser.kt:242-262` | `detectDirection`: `IN_MARKS` содержит «поступил» (126) → направление IN (`income` найден). Направление здесь определяется, но дальше: |
| `NotificationParser.kt:194-195` | `op = hits.firstOrNull { ... } ?: return null` → **null**: нет суммы с валютной меткой → не операция |
| `NotificationParser.kt:131` | `IN_FROM = от[ ]+[А-ЯЁ][а-яё]` — «от ООО» не совпадёт (второй символ заглавный), но это не причина отказа, т.к. «поступил» уже дал IN |
| `NotificationParser.kt:172` | `MASK` съедает `*4863`, `*1007` (маска счёта) — сумма не из неё |
| `NotificationParser.kt:57` | `NUM` принимает `322581.00` и `325218.58` (разделитель точка, 2 знака) — число читается, но без метки валюты его не берут |
| `NotificationParser.kt:76-79` | `AMOUNT_THEN_CCY` / `CCY_THEN_AMOUNT` — нужна метка ₽/руб/RUB рядом |

**Причина:** сумма без валютной метки и без «списано/зачислено» перед «счет *dddd»; «Поступило» не покрыто `ORDER_AMOUNT`. Парсер консервативен (возвращает null), поэтому журнал: «не разобрано».

### Тесты парсера
`app/src/test/java/ru/maxrubtsoff/bankagg/domain/NotificationParserTest.kt`:
- `:105` `alfa_rur_with_balance`
- `:155`, `:163` — BYN, «Оплата … Остаток» без метки
- `:245` — `private val alfa = "Списано 440.00 счет *4863 п/п №92712 от 27.10.26 в адрес АО \"АЛЬФА-БАНК\" счет 0741. Остаток 3079.36"`
- `:247` `alfaBusinessPaymentOrderParsed` (title null и «Альфа-Бизнес»)
- `:258` `alfaBusinessRecipientAccountIsNotOurs` (счёт 0741 — не наш)
- `:265` `alfaBusinessWithoutOwnMaskIsNull`
- `:270` (`Списано 440.00` — null)
- `:274` `alfaBusinessWithoutBalance`
- `:280` `alfaBusinessCredit` — «Зачислено 1000.00 счет *4863 п/п №1. Остаток 5000.00» — **это ближайший аналог «Поступило»**, тест есть для «Зачислено»
- `:287` — «Поступило 500 ₽. Доступно 1 000 ₽» (с валютной меткой — проходит)

Тестов на «Поступило … счет *dddd … от ООО …» **нет**. Обратный формат «Списано … счет … Остаток …» покрыт (245-275).

### Версия парсера
- `NotificationParser.kt:47` `const val VERSION = 5`; комментарий версий 42-46. Изменение парсинга требует нового номера версии (журнал перечитывается через `RepairFromJournal`).

### Восстановление из журнала
- `RepairFromJournal.kt:30-36` — перепарсинг всех событий (`events.all()`), `content != event.contentHash` → изменение.
- `RecordNotification.contentHash` — `RecordNotification.kt:112-120`.

---

## B-123 — время операции из текста vs время уведомления

### Где берётся время сейчас
| Путь:строка | Что там |
|---|---|
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/parser/NotificationParser.kt:16-26` | `data class ParsedOp(amount, currency, kind, direction, balance, counterparty, balanceCurrency)` — **поля времени операции НЕТ** |
| `NotificationParser.kt` (весь файл) | **времени/даты из текста не читает.** `NUM` (56-57) явно не берёт «10:15» (`(?![\d:])`, `(?<![\d*•:.,])`). Регексов времени в парсере нет |
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/usecase/RecordNotification.kt:36` | `postedAt: Long` — **время уведомления** (единственный источник времени) |
| `RecordNotification.kt:45` | `eventHash(content, postedAt)` — id события = содержимое + время прихода |
| `RecordNotification.kt:50` | дедуп: `hasSimilar(content, postedAt ± 2 мин)` (`DEDUP_WINDOW_MS` 107) |
| `RecordNotification.kt:72-79` | `ledger.upsertAccountBalance(..., balanceMinor, postedAt = postedAt)` — баланс со временем прихода |
| `RecordNotification.kt:80-92` | `Transaction(..., postedAt = postedAt)` — операция со временем прихода |
| `app/src/main/java/ru/maxrubtsoff/bankagg/data/repository/LedgerRepositoryImpl.kt:20-32` | `upsertAccountBalance` → `RunningBalance.apply(existing?.state(), amountMinor, balanceMinor, postedAt)` (32) |
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/RunningBalance.kt:16,19` | `object RunningBalance`, `apply(state, amount, bankBalance, postedAt)` — порядок/новизна по `postedAt` (тело не читано) |
| `app/src/main/java/ru/maxrubtsoff/bankagg/data/local/LedgerDao.kt:35-36` | `updateBalanceIfNewer`: `UPDATE accounts ... WHERE updatedAt <= :postedAt` — баланс перетирается, если **время прихода** не меньше |
| `LedgerDao.kt:41-42` | `touch` — `MAX(updatedAt, :postedAt)` |
| `LedgerDao.kt:12` | `sumSince` по `transactions.postedAt` |
| `LedgerDao.kt:72` | лента: `transactions ORDER BY postedAt DESC` |
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/usecase/RepairFromJournal.kt:58` | `RepairEvent(e.postedAt, op.balance?.minor)` — база пересчёта = последнее по **postedAt** (время прихода) |
| `app/src/main/java/ru/maxrubtsoff/bankagg/domain/model/RepairBase.kt:4` | `data class RepairEvent(postedAt, balanceMinor)` |
| `RepairBase.kt:25-32` | `pick(...)`: `accountEvents.filter{balance!=null}.maxByOrNull { postedAt }` (30) — **последний по приходу, не по времени операции** |
| `RepairFromJournal.kt:68` | `newId = eventHash(content, change.event.postedAt)` |
| `RepairFromJournal.kt:80` | `Transaction.postedAt = change.event.postedAt` |

Итог: баланс и порядок операций везде привязаны к `CapturedEvent.postedAt` (время прихода). Времени операции в доменной модели нет.

### Что уже есть для похожего
- `domain/model/BackupRotation.kt:11,40,49` — `LocalTime` только для имён копий (не для уведомлений).
- `ui/log/LogScreen.kt:46` — формат `dd.MM HH:mm` для журнала (время прихода).

### Тесты рядом
- `app/src/test/java/ru/maxrubtsoff/bankagg/domain/RecordNotificationTest.kt:19` (`class RecordNotificationTest`)
- `app/src/test/java/ru/maxrubtsoff/bankagg/domain/RepairFromJournalTest.kt:27` (`class RepairFromJournalTest`)
- `app/src/test/java/ru/maxrubtsoff/bankagg/domain/RepairBaseTest.kt:11` (`class RepairBaseTest`)
- `NotificationParserTest.kt` — тестов времени нет.
- Тест `RunningBalance` не искался (не проверено).

---

## Пересечения между пунктами

| Файл | Пункты | Комментарий |
|---|---|---|
| `domain/parser/NotificationParser.kt` | **B-122, B-123** | оба меняют парсер; B-123 добавит поле времени в `ParsedOp` (строки 16-26) и регекс времени; B-122 — `ORDER_AMOUNT` (99-102) и `orderHit` (217-220). Поднять `VERSION` (47) один раз на оба. |
| `domain/parser/NotificationParser.kt` + `NotificationParserTest.kt` | **B-122, B-123** | тесты в одном файле (`:245-290` для Альфы). |
| `domain/usecase/RecordNotification.kt` | **B-122, B-123** | B-122 — через `parse`; B-123 — `postedAt` (36, 45, 50, 72-92): выбор времени операции здесь. Дедуп (50) и `eventHash` (45) зависят от времени — решение нужно согласовать. |
| `domain/usecase/RepairFromJournal.kt` | **B-122, B-123** | B-122 — «Восстановить из журнала» подтянет операцию (перепарсинг, 30-36); B-123 — ключ `postedAt` в `RepairEvent` (58), `Transaction.postedAt` (80), `eventHash` (68). |
| `domain/model/RepairBase.kt:25-32` | **B-123** (и косвенно B-121) | выбор базы баланса по `maxByOrNull { postedAt }` (30) — здесь нужно «последнюю по времени операции». |
| `data/repository/LedgerRepositoryImpl.kt:20-32`, `data/local/LedgerDao.kt:35-36` | **B-123** | `updateBalanceIfNewer` сравнивает `updatedAt` с `postedAt` — баланс «перетирается» по приходу. |
| `domain/model/RunningBalance.kt` | **B-123** | логика выбора баланса (не читана — проверить). |
| `domain/model/CapitalEvents.kt` / `HistoryRows.kt` | **B-121, B-123** | история по дате операции (`postedAt` → день): B-123 меняет дату операции, значит и day-группировку событий; B-121 — события «Переоценка» и ручная правка (322-342, 364-398). |
| `domain/usecase/HistoryOperations.kt` | **B-121** | `delete` (243) и `restoreBalance` (156-159) — если добавлять удаление правки, менять здесь. Связан с B-123 только через `setBalance` (142-147). |
| `ui/history/OperationSheet.kt:78`, `ui/history/BalanceFlow.kt` | **B-121** | тап → `BalanceFlow`; для «удалить правку» нужна ветка здесь. |
| `ui/widget/CashbackWidget.kt`, `domain/model/CashbackWidgetLayout.kt` | **F-56, B-120** | одни и те же файлы: F-56 — сортировка (`CashbackWidget.kt:132,140,143`, `CashbackSpotlight.kt:96`); B-120 — `grid`/`pack` (`CashbackWidgetLayout.kt:40-88`, `CashbackWidget.kt:155-156, 247`). |
| `domain/model/CashbackSpotlight.kt` | **F-56** (и home card) | порядок строк одинаков для карточки на главном экране и виджета (`CashbackWidget.kt:88` комментарий). Меняя сортировку виджета, не сломать карточку. |

Пункты **B-121** и **F-56/B-120** не пересекаются по файлам. **B-122 и B-123 пересекаются** по `NotificationParser.kt`, `RecordNotification.kt`, `RepairFromJournal.kt` — их нельзя вести параллельными группами без общей очереди.

## Не проверено
- Раскладка «+N банков» в `CashbackWidget.kt:243-268` прочитана по заголовкам, не построчно.
- `RunningBalance.apply` (`domain/model/RunningBalance.kt`) не читан.
- Есть ли в проекте явный `updateAll` для виджета — не искалось.
- Тесты `RunningBalance` и виджет-раскладки в Glance (только `CashbackWidgetLayoutTest`).
