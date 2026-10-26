# Цикл 13 — планы

Пути от `app/src/main/java/ru/maxrubtsoff/bankagg/` (ниже `…/`), тесты — `app/src/test/java/ru/maxrubtsoff/bankagg/domain/`.

> B-82, B-83, B-84 правят один файл (`…/domain/parser/NotificationParser.kt`) — одна группа, коммит на пункт, порядок B-82 → B-83 → B-84. `NotificationParser.VERSION` поднимается **один раз** (2 → 3) в коммите B-82: при старте `RepairFromJournal` перечитает журнал и уберёт ложные операции (в т.ч. +20,26 млн). Ложный стартовый баланс после починки — отдельный пункт B-90.

## B-82

**Цель:** число без валютной метки никогда не становится суммой операции; маски карт/счетов (`··`, `ECMC`, `MC`, `MAES`, «Счёт 4081 7810…») не дают чисел.

**Домен — `…/domain/parser/NotificationParser.kt`:**
1. `:160-162` — убрать запасной путь `?: plainAmount(clean, balanceAt, defaultCurrency)`: сумма операции только из `hits` (метка валюты до/после числа). Нет hit до баланса → `return null`. Обоснование: консервативность; ни один существующий тест не опирается на голое число (проверено), а пуш без метки уйдёт в журнал «не разобрано» и перечитается, если позже научимся.
2. `:192-198` — удалить `plainAmount` целиком. `hasUnit`/`UNIT_AFTER` (`:122-126`, `:200-202`) оставить только если ещё используются; иначе удалить (мертвый код ловит ревью).
3. Параметр `defaultCurrency` в `parse` (`:141`) **не удалять** (его передают `RecordNotification:42`, `RepairFromJournal`, тесты :118-130): переписать KDoc `:135-140` — «валюта берётся только из метки; `defaultCurrency == null` — банк, где метки `р`/`руб` нельзя читать как RUB, оставлено для совместимости». Логику по нему не добавлять.
4. Баланс `:166-169`: голое число после слова баланса оставить (баланс не создаёт операцию; вне объёма пункта).
5. `MASK` `:73-76` — расширить (одна регулярка, `IGNORE_CASE`), три альтернативы:
   - точки-маски: `[*•·∙‧]+[  ]?\d{2,}` (добавлены `·` U+00B7, `∙` U+2219, `‧` U+2027);
   - бренды карт: `(?<![a-zа-яё])(?:ecmc|mc|maes|maestro|mastercard|master|mir|visa|мир)[-*  ·•]?\d{4}(?!\d)`;
   - счёт/карта с номером: `(?:сч[её]т|карт[аы])[  ]?(?:№[  ]?)?-?\d{4,}(?:[  ]\d{4})*(?!\d)(?![   ]?(?:$MARK_ALT)(?![a-zа-яё]))` — отрицательный просмотр вперёд на валюту, чтобы «на счёт 5000 ₽» осталось суммой. `MASK` объявить **после** `MARK_ALT` (порядок инициализации `object`).
6. `VERSION` `:34` → `3`, в KDoc строка «3 — сумма только с меткой валюты, маски карт/телефонов (B-82…B-84)».

**Слои 2–9:** без изменений.

**Тесты — `NotificationParserTest.kt` (дописать; до фикса красные, кроме помеченных «регресс»):**
- `bare_account_number_is_not_an_amount` — «Перевод от Иван И. Счёт 40817810 Баланс 5 000 ₽» → `null`.
- `bare_date_like_number_is_not_an_amount` — «Зачисление 20261026 Баланс 1 000 ₽» → `null`.
- `middle_dot_card_mask_is_not_an_amount` — «Карта ·· 2026 Покупка 500 ₽» → −500,00 ₽ (а «Карта ·· 2026 Покупка» без суммы → `null`).
- `card_brand_masks_are_not_amounts` — «ECMC2026 Покупка 300 ₽», «MC*1234 Оплата 300 ₽», «MAES 5678 Покупка 300 ₽» → −300,00 ₽ каждое.
- `spaced_account_number_is_masked` — «Счёт 4081 7810 Зачисление 1 500 ₽» → +1 500,00 ₽.
- `amount_after_schet_with_currency_stays` (регресс) — «Перевод на счёт 5000 ₽» → сумма 5 000,00 ₽ (направление — как сейчас).
- `plain_number_without_currency_is_null` — «Покупка 500 Магнит» → `null`.
- Все существующие тесты `NotificationParserTest` зелёные без правок ожиданий.

**Критерии приёмки:** `./gradlew testDebugUnitTest assembleDebug` зелёные; инвариант: `ParsedOp.amount` всегда происходит из числа с валютной меткой; маски карт/счетов не дают сумму; деньги только `Money`.

## B-83

**Цель:** «+7» телефона не задаёт направление, цифры телефона — не сумма.

**Домен — `…/domain/parser/NotificationParser.kt`:**
1. Новая константа `PHONE` рядом с `MASK`: `(?<![\d+])(?:\+7|8)[  \-(]*\d{3}[  \-)]*\d{3}[  \-]*\d{2}[  \-]*\d{2}(?!\d)`.
2. `:144` — маскировать телефон до `MASK`: `clean = MASK.replace(PHONE.replace(blob){пробелы той же длины}){…}` (длина сохраняется, позиции `low`/`clean` совпадают).
3. Знак учитывается только у суммы с валютой (вторая линия защиты): в `parse` вычислять `hits(clean)` (`:156`) **до** `detectDirection` и передавать `hits` в `detectDirection` (`:214-219`). В `:225-227` брать `SIGNED_AMOUNT.findAll(clean)` и принимать первый знак, за которым (после необязательного пробела) начинается `Hit` (`hit.start == match.range.last + 1`) или `CCY_THEN_AMOUNT`-hit, у которого знак стоит перед меткой; иначе знак игнорируется.
4. Итог для «Перевод по номеру +7 926 263-45-67 1 500 ₽»: направления нет → `null` (выбрано: консервативно; «перевод по номеру» бывает и входящим, и исходящим — угадывать не будем).

**Тесты — `NotificationParserTest.kt`:**
- `phone_plus_is_not_income_sign` — «Перевод по номеру +7 926 263-45-67 1 500 ₽» → `null` (сейчас IN).
- `phone_digits_are_not_amount` — «Перевод от +79262634567» → `null` (сейчас 792 626 345,67).
- `phone_with_explicit_word_keeps_amount` — «Списание 1 500 ₽ перевод на +7 926 263-45-67» → −1 500,00 ₽.
- `explicit_sign_still_works` (регресс) — «+1 100 ₽ Иван И.» → IN 1 100,00; «−500 ₽ Магнит» → OUT 500,00.

**Критерии приёмки:** сборка и тесты зелёные; инвариант: телефон (`+7…`/`8…`, 11 цифр) не влияет ни на сумму, ни на знак.

## B-84

**Цель:** «Входящий перевод 500 ₽ от Ивана Иванова» — доход +500.

**Домен — `…/domain/parser/NotificationParser.kt`:**
1. `IN_MARKS` `:94-97` — добавить `"входящ"` (входящий/входящая/входящее), `"перевод получен"`, `"получен перевод"`, `"вам поступил"`.
2. `OUT_MARKS` `:87-91` — добавить `"исходящ"` (симметрия; «исходящий» не содержит «входящ» — проверено).
3. Новый `IN_FROM = Regex("""(?<![А-Яа-яЁё])от[  ]+[А-ЯЁ][а-яё]""")` — на **исходном регистре** (`clean`, не `low`): «… от Ивана Иванова/от Иван И.» — отправитель. В `detectDirection` `:221` его позиция участвует в `income` наравне с `IN_MARKS` (минимум позиций), правило «кто раньше — тот решает» `:222` сохраняется — «Оплата … от Ивана» остаётся OUT.
4. Запасное `TRANSFER && counterparty ⇒ OUT` (`:229`) не трогать: до него теперь доходят только переводы без признаков дохода.

**Тесты — `NotificationParserTest.kt`:**
- `incoming_transfer_with_sender_is_income` — «Входящий перевод 500 ₽ от Ивана Иванова» → +500,00, `kind=TRANSFER`, `direction=IN`.
- `transfer_from_name_not_adjacent_is_income` — «Перевод 500 ₽ от Ивана Иванова» → +500,00.
- `outgoing_transfer_to_name_stays_out` (регресс) — «Перевод 3 000 ₽ Ивану Петрову. Доступно 10 000 ₽» → −3 000 (существующий тест :32).
- `outgoing_word_wins` — «Исходящий перевод 700 ₽ Ивану Петрову» → −700,00.
- `payment_from_name_stays_out` — «Оплата 200 ₽ от Ивана Иванова» → −200,00.

**Критерии приёмки:** сборка и тесты зелёные; инвариант: явный признак направления (слово/«от Имени») раньше в тексте решает, без признаков — прежние правила.

## B-92

**Цель:** две честные одинаковые операции без баланса в пуше (два кофе по 300 ₽ за минуту) записываются обе; повтор того же пуша (обновление, пересортировка группы) по-прежнему склеивается.

**Решение (компромисс):** окно ±2 мин остаётся. Внутри окна повтором считается:
- пуш **с балансом** — по содержимому, как сейчас (две реальные операции дают разный баланс → разный `contentHash`; дубль от банка с новым id по-прежнему склеится);
- пуш **без баланса** (или не разобранный) — только если совпадает ключ уведомления Android `StatusBarNotification.key` (обновление/пересортировка сохраняют ключ; новая операция с отдельным id — нет). Если ключ неизвестен у любой стороны (старые записи, починка, тесты) — склеиваем как раньше.
Остаточный риск, принятый явно: банк, который шлёт все пуши под одним id, склеит два одинаковых кофе (как сейчас — не хуже).

**1. Домен:**
- `…/domain/model/CapturedEvent.kt:18` — добавить `val notificationKey: String? = null` (KDoc: ключ уведомления Android, только для дедупа).
- `…/domain/usecase/RecordNotification.kt`:
  - `:32-37` — параметр `notificationKey: String? = null` последним (существующие вызовы/тесты компилируются).
  - companion — чистая функция `dedupKey(op: ParsedOp?, notificationKey: String?): String? = if (op?.balance != null) null else notificationKey` (KDoc: null = сверять только содержимое).
  - `:49` — `events.hasSimilar(content, from, to, dedupKey(op, notificationKey))`; `:52-62` — в `CapturedEvent` передать `notificationKey = notificationKey`.
  - `contentHash`/`eventHash` (`:101-112`) **не менять** — на них опирается `RepairFromJournal:31,55`.

**2. Интерфейс:** `…/domain/repository/EventRepository.kt:11` → `hasSimilar(contentHash: String, from: Long, to: Long, notificationKey: String?): Boolean`; KDoc: при `notificationKey != null` совпадением считается запись с тем же ключом или без ключа.

**3. Use-case:** см. п.1 (оркестрация уже в `RecordNotification`).

**4. Room (v18 → v19):**
- `…/data/local/EventEntity.kt:21` — `val notificationKey: String? = null`.
- `…/data/local/EventDao.kt:16-17` — `@Query("SELECT COUNT(*) FROM events WHERE contentHash = :contentHash AND postedAt BETWEEN :from AND :to AND (:key IS NULL OR notificationKey IS NULL OR notificationKey = :key)") suspend fun countSimilar(contentHash: String, from: Long, to: Long, key: String?): Int`.
- `…/data/local/EventMappers.kt:15,27` — проброс `notificationKey` в обе стороны.
- `…/data/local/AppDatabase.kt:19` — `version = 19`; рядом с `:38` новый `MIGRATION_18_19`:
  `ALTER TABLE events ADD COLUMN notificationKey TEXT` (nullable, без DEFAULT — совпадает со схемой Room для `String?`); добавить в `addMigrations(...)` `:143`. Если другой пункт цикла уже занял v19 — взять следующую версию, цепочку не ломать.

**5. Реализация:** `…/data/repository/EventRepositoryImpl.kt:20-21` — новая сигнатура → `dao.countSimilar(contentHash, from, to, notificationKey) > 0`.

**6. DI:** без изменений (`AppContainer.kt:156`).

**Сервис (Android-слой):** `…/service/BankNotificationListener.kt:64-69` — передать `notificationKey = sbn.key`. `NewBankAnswers` (вызывает `recordNotification` без ключа) — не трогать: дефолт `null` = прежнее поведение.

**Бэкап:** `BackupManager` (`:305`, `:597`) ключ **не** сериализует — после восстановления ключи чужого сеанса бессмысленны; `null` = прежнее поведение.

**7–9. UI:** без изменений.

**Тесты:**
- `RecordNotificationTest.kt:27-28` — фейк `hasSimilar` повторяет SQL: `contentHash == … && postedAt in from..to && (key == null || it.notificationKey == null || it.notificationKey == key)`. `RepairFromJournalTest.kt:36` — сигнатура.
- `two_same_payments_without_balance_from_different_notifications_both_recorded` — «Покупка 300 ₽ Кофе» дважды, `postedAt` +60 с, ключи `k1`/`k2` → обе записаны, 2 транзакции (красный до фикса).
- `repost_of_same_notification_is_deduped` — тот же текст, тот же ключ `k1`, +30 с → вторая не записана.
- `same_push_with_balance_is_deduped_even_with_new_key` — «Покупка 300 ₽. Баланс 1 000 ₽», ключи `k1`/`k2`, +30 с → одна запись.
- `unknown_key_keeps_old_window_dedup` — ключ `null` у обоих → одна запись.
- `dedupKey_null_when_balance_known` — юнит на `RecordNotification.dedupKey`.

**Критерии приёмки:** `./gradlew testDebugUnitTest assembleDebug` зелёные; миграция 18→19 не стирает данные (обновление поверх на устройстве: журнал и операции на месте); инварианты: повтор пуша с тем же ключом или с тем же балансом в окне — одна операция; `contentHash`/`eventHash` прежние (починка из журнала не перезаписывает всё).

## B-80

**Цель:** на всех пяти вёрстках карточки (`docs/cycles/cycle-13-shots/B-80/deposit-1..5.jpg`) распознаются: банк (Альфа-Банк), название «Вклад №1: Альфа-Банк», сумма открытия 650 000,00, сумма в конце 770 191,47, ставка 18,49 %, даты 07.08.2024 — 07.08.2025. Статус «Завершён» — производный от `endDate < сегодня` (`DepositMath.completed`), отдельного поля не заводим (в `DepositDraft` его нет, модель не расширяем).

**Найденные причины (по коду):**
- `DepositParser.amountHits` `:183` берёт **одну** сумму на строку (`AMOUNT.find`) — в строчной выдаче OCR «650 000 ₽ 770 191,47 ₽ 18,49%» вторая сумма теряется, а первая получает подпись строки выше целиком, где первым находится EXIT («на выход») → 650 000 записывается как выход, открытие пустое (вёрстка 1).
- Подпись в две строки «СУММА ПРИ ОТКРЫТИИ / ВКЛАДА» (2): над суммой «ВКЛАДА», `captionOf` → null, сумма без вида.
- Нет подписи (4) / подпись «СУММА» (5): `captionOf` отбрасывает голые «вклад»/«сумма`, а запасное «нет подписей вовсе» `:148` не срабатывает, раз выход подписан → открытие null.
- `titleOf` `:343-350`: `TITLE_WORD` пропускает слова КАПСОМ → «СУММА ПРИ ОТКРЫТИИ ВКЛАДА» становится названием; настоящее «Вклад №1: Альфа-Банк» отбрасывается из-за токена «№1:».
- Даты: при трёх масштабах OCR (`recognizeThorough` склеивает 3 прохода) один ошибочно прочитанный год расширяет `distinct.first()/last()` `:286`; разделители «07.08. 2024»/«07,08.2024» не ловятся `DATE` `:29`.

**1. Домен — `…/domain/model/DepositParser.kt`:**
1. **Колонки в одной строке.** `amountHits` `:177-214`: вместо `AMOUNT.find` — все значения строки по порядку (`AMOUNT.findAll` с прежним фильтром «похоже на деньги» `:188-189`, проценты `PERCENT`, даты). Если в строке ≥ 2 денежных значений или подпись выше содержит ≥ 2 подписей-колонок: разбить строку подписи на сегменты — новая чистая `captionColumns(norm: String): List<Kind?>` (позиции всех фраз из `INCOME_WORDS`/`EXIT_WORDS`/`IN_CAPTIONS` + `"ставк"`, `"дат"`/`"период"`/`"срок вклад"` как не-денежные колонки; сортировка по позиции, перекрывающиеся фразы — длиннейшая). Денежные значения сопоставить денежным сегментам по порядку; если значений меньше сегментов и строка значений начинается с букв (хвост перенесённой подписи «ВКЛАДА 770 191,47 ₽ …») — выравнивать **справа**; иначе слева. Сопоставленные хиты `strong = true`.
2. **Подпись в две строки.** Если `captionOf(p1) == null` и `p1` без цифр — пробовать `captionOf("$p2 $p1")` (склейка двух строк подписи) до перехода к `INCOME_WORDS.any { p2 }` `:204`. Для колонок — несвязанные с значениями сегменты строки подписи «переносятся» на следующую строку со значением (тот же механизм: оставшийся сегмент IN достаётся «650 000 ₽» на следующей строке).
3. **Голая подпись «СУММА».** В `captionOf` `:217`: строка, нормализованная ровно в `"сумма"`, — IN-подпись (в отличие от заголовка «Вклад», это подпись поля).
4. **Вывод открытия методом исключения** (после `pick` `:140-142`, до `:154`): если `principal == null`, `exit != null`, и среди хитов без вида (`kind == null`) ровно одно **различное** значение `< exit` — это открытие; `guessed += PRINCIPAL`. Два и более кандидата — `null` (консервативно).
5. **Название** `titleOf` `:343-350`:
   - пропускать строки-подписи: если `kindOf(norm) != null` при отсутствии слова на позиции имени, т.е. строка содержит любую фразу из `INCOME_WORDS`/`EXIT_WORDS`/`IN_CAPTIONS`/`"сумма"` — не название;
   - пропускать строки целиком в верхнем регистре (≥ 2 букв-слов и `line == line.uppercase()`): на карточках капсом пишут подписи и статус;
   - токены `№\d+:?` и слова с хвостовым `:` допустимы (`TITLE_WORD` расширить альтернативой `№\d+:?` и разрешить `:` в конце слова);
   - если первое слово «Вклад», кавычек нет, а слов ≥ 2 — вернуть строку целиком («Вклад №1: Альфа-Банк»); с кавычками — прежнее правило (тест `:17` «Вклад «Надёжный»» → «Надёжный» остаётся).
6. **Даты.**
   - Новая `DATE_RANGE`: `DATE\s*[—–\-]\s*DATE` в одной строке; если найдена — `start`/`end` из неё напрямую (диапазон на одной строке надёжнее min/max по всему тексту), иначе прежняя логика `:281-291`.
   - `DATE` `:29` — допустить пробел после разделителя и `,` как разделитель **только при 4-значном годе**: `(?<!\d)(\d{1,2})\s?[.,\-/]\s?(\d{1,2})\s?[.,\-/]\s?(\d{4})(?!\d)` отдельной регуляркой `DATE_LOOSE`, применяется если `DATE` ничего не нашла в строке (ставка «18,49%» не совпадёт — нет третьей части).
7. **Суммы, склеенные со знаком.** `CURRENCY_AFTER` `:27` — добавить строчные `р`/`p` (OCR «650000р»). `AMOUNT` для `650000₽` уже работает (`\d{4,}`), правку не трогать.
8. **Голосование по проходам OCR.** Новая `fun parsePasses(passes: List<String>, brandHint: BankBrand? = null, today: LocalDate = LocalDate.now()): Result` — `parse` каждого прохода отдельно, по каждому полю (`principal`, `exitAmount`, `rateBp`, `startDate`, `endDate`, `income`, `title`, `method`, бренд) — самое частое не-null значение, ничья → из более раннего прохода (масштаб 1 первый). `guessed` поля — если победившее значение помечено угаданным в первом давшем его проходе. Затем проверка `exit <= principal → exit = null` и `withDerivedAmounts()`. Пустой список → `Result(brandHint, DepositDraft())`. `parse(rawText)` оставить как есть (одна строка — один проход).

**2. Интерфейс — `…/domain/repository/OcrEngine.kt`:** добавить `suspend fun recognizePasses(imagePath: String): List<String> = listOfNotNull(recognize(imagePath))`; `recognizeThorough` `:29` переопределить по умолчанию как `recognizePasses(...).joinToString("\n\n").takeIf { it.isNotBlank() }` (контракт прежний).

**3. Use-case — `…/domain/usecase/RecognizeDepositShot.kt:36-42`:** `val passes = ocr.recognizePasses(shot.imagePath)`; пусто → `FAILED`; `looksLikeDeposit(passes.joinToString("\n"))`; `DepositParser.parsePasses(passes, brandHint = fileBank)`. Остальное (`:43-52`) без изменений.

**4. Room:** без изменений.

**5. Data / OCR — `…/data/ocr/TesseractOcrEngine.kt`:**
- `:52-69` `read(...)` → возвращает `List<String>` (тексты проходов, пустые выкинуты), без склейки.
- `:46` `recognize` = `read(path, listOf(1f)).firstOrNull()`; `:50` вместо `recognizeThorough` — `override suspend fun recognizePasses(imagePath) = read(imagePath, listOf(1f, 0.5f, 2f))`; `recognizeThorough` берётся из дефолта интерфейса.
- Режим сегментации Tesseract не меняем (лишний проход — лишние секунды; обе формы выдачи — по колонкам и построчно — обрабатывает парсер).

**6–9. DI/VM/Factory/Screen:** без изменений.

**Тесты — `DepositParserTest.kt` (дописать; фикстуры — тексты в стиле выдачи Tesseract, по две формы для колоночных вёрсток):**
- `layout1_row_wise_three_columns` — «Вклад №1: Альфа-Банк ЗАВЕРШЕН / СУММА НА ВХОДЕ СУММА НА ВЫХОДЕ СТАВКА / 650 000 ₽ 770 191,47 ₽ 18,49% / ДАТЫ ПЕРИОДА / 07.08.2024 — 07.08.2025» → все поля (красный: principal, exit).
- `layout1_block_wise` — те же значения столбцами (подпись/значение построчно) → все поля.
- `layout2_row_wise_wrapped_caption` — «СУММА ПРИ ОТКРЫТИИ СУММА В КОНЦЕ СРОКА СТАВКА ДАТЫ ПЕРИОДА / ВКЛАДА 770 191,47 ₽ 18,49% 07.08.2024 — 07.08.2025 / 650 000 ₽» → principal 650 000, exit 770 191,47, даты.
- `layout2_block_wise_wrapped_caption` — «СУММА ПРИ ОТКРЫТИИ / ВКЛАДА / 650 000 ₽ / СУММА В КОНЦЕ СРОКА / 770 191,47 ₽ …» → principal 650 000, не угадано.
- `layout3_glued_amounts` — «650000₽», «770191,47₽» (и вариант «650000Р») → 65 000 000 / 77 019 147 коп.
- `layout4_amount_without_caption` — открытие без подписи → principal 650 000, `PRINCIPAL in guessed`.
- `layout5_bare_summa_caption` — подпись «СУММА» → principal 650 000, не угадано.
- `caption_is_never_a_title` — во всех фикстурах `title == "Вклад №1: Альфа-Банк"`, никогда не «СУММА ПРИ ОТКРЫТИИ ВКЛАДА».
- `date_range_on_one_line_wins` — диапазон «07.08.2024 — 07.08.2025» плюс посторонняя дата «01.01.2021» в другой строке → start/end из диапазона.
- `loose_date_separators` — «07.08. 2024 — 07,08.2025» → даты прочитаны.
- `two_unlabeled_candidates_stay_null` — выход подписан, без подписи два разных числа меньше выхода → principal `null` (консервативность).
- `parsePasses_votes_per_field` — 3 прохода: в одном год прочитан «2021», в двух «2024» → start 07.08.2024; поле, прочитанное только одним проходом, всё равно заполнено.
- Существующие 7 тестов `DepositParserTest` зелёные без правок ожиданий.

**Критерии приёмки:** `./gradlew testDebugUnitTest assembleDebug` зелёные; домен без Android, суммы — `Money`/минорные (`Long`), без `Double` для денег (ставка — как сейчас через `rateOf`); не уверен — `null`, угаданное помечено в `guessed`. На устройстве (`device-check`): загрузить пять скринов `deposit-1..5.jpg` в «Вклады» → у каждого банк Альфа, название «Вклад №1: Альфа-Банк», 650 000 ₽ → 770 191,47 ₽, 18,49 %, 07.08.2024 — 07.08.2025, вклад в завершённых; при расхождении снять текст OCR (временный лог только в debug) и добавить его фикстурой в тесты.

# Разделы architect: B-87, B-90, B-91, B-93, B-94, B-95, B-98, B-99, B-103

Версии Room цикла: 18 → 19 занимает B-92 (`events.notificationKey`); B-95 (`subscriptions.anchorDay`) — 19 → 20. Если порядок коммитов изменится, номера сдвигаются, цепочка `addMigrations` не ломается. Тесты: `T/` = `app/src/test/java/ru/maxrubtsoff/bankagg/domain/`.

## B-87
**Цель:** «Это мой ВТБ» для приложения с новым пакетом сохраняет пакет в банке, `BankBrand.fromPackage(pkg)` находит банк, операции идут в счёт этого банка, а не в `unknown:RUB`.

Причина (`…/domain/usecase/NewBankAnswers.kt:41-47`): ветка `bank.isKnown` вызывает только `banks.add(bank)` — пакет не добавляется; `BankStore.add` (`…/data/settings/BankStore.kt:63-66`) выходит, если банк уже есть; `BankStore.write` (:87-91) сохраняет определение известного банка, только если изменились вид/логотип/название — смена `packages` не считается изменением.

Слои:
1. Домен `NewBankAnswers.link`: убрать ветвление; `val linked = bank.copy(packages = bank.packages + c.packageName)`; банка нет в `banks.current()` — `banks.add(linked)`, иначе `banks.update(linked)` (выбор: `add` не обновляет существующий, `update` не добавляет).
2. Интерфейсы и Room не меняются.
3. Data `BankStore.write` (:87-91): в условие `changed` добавить `|| b.packages != k.packages` (`BankBrand.equals` только по key — сравнивать поле явно). Проверить, что `toJson/fromJson` (:118+) сериализуют `packages` и для известных банков.
4. Домен `BankBrand.fromPackage` (`…/domain/model/BankBrand.kt:115-116`): сейчас `all = known + BankDirectory.extra()`, `known` первым, и его `packages` без нового пакета → UNKNOWN. Искать сначала в определениях пользователя: `(BankDirectory.extra() + known).firstOrNull { pkg in it.packages } ?: UNKNOWN`. Аналогично проверить `all`/`selectedBankCount` (:113, :128) на дубли по key.
5. DI/VM/UI не меняются.

**Тесты** (`T/NewBankAnswersTest.kt` — новый, фейки `BankRepository`/`CandidateRepository`/`SettingsRepository`/`RecordNotification` по образцу `RepairFromJournalTest`):
- `link_known_bank_adds_candidate_package` — после `link(c, VTB)` в репозитории у банка `packages` содержит `c.packageName` (красный до фикса).
- `link_known_bank_already_mine_updates_instead_of_skipping` — банк уже в `current()`: пакет добавлен, дубля нет.
- `link_unknown_bank_keeps_old_behavior`.
- `fromPackage_finds_user_added_package_of_known_bank` (`T/BankBrandTest.kt`): после `BankDirectory.replace(listOf(VTB.copy(packages = VTB.packages + "x.y")))` `fromPackage("x.y").key == VTB.key`; `@After` очищает `BankDirectory`.

**Приёмка:** `./gradlew testDebugUnitTest assembleDebug` зелёные; два приложения ВТБ с разными пакетами пишут в `vtb:RUB`; инвариант: `link` всегда оставляет пакет кандидата в `packages` банка. Уже записанные в `unknown:RUB` операции не мигрируем.

## B-90
**Порядок: после B-82.** B-82 поднимает `NotificationParser.VERSION` (2 → 3) и запускает перечитывание журнала; B-90 версию **не** поднимает и опирается на то, что реклама уже не разбирается как операция. Файлы пересекаются только через `RepairFromJournal`.

**Цель:** после «Починки из журнала» баланс не опирается на «точный» остаток из ложного пуша, а счёт, созданный только ложной операцией, исчезает.

Причина (`…/domain/usecase/RepairFromJournal.kt:44-50, 74-77`): база = текущий `balance − sumSince(checkedAt)`; если `checkedAt` поставлен ложным пушем, база остаётся ложной; счёт без операций и без точного остатка остаётся.

Решение: `checkedAt` считается отравленным, если среди `changes` есть событие счёта с `event.postedAt == account.checkedAt` (`RunningBalance` ставит `checkedAt = postedAt` пуша с балансом — coder сверяет по `…/domain/model/RunningBalance.kt`, при отличии скорректировать условие). Для отравленного счёта база пересчитывается из журнала.

Слои:
1. Домен: `…/domain/model/RepairBase.kt` — `pick(account, accountEvents: List<RepairEvent(postedAt, balanceMinor?)> /* новый разбор */, changedPostedAts: Set<Long>): Base` = `Keep(checkedAt, balance)` | `FromJournal(postedAt самого позднего события с balance != null, balance)` | `None`.
2. Интерфейс `…/domain/repository/LedgerRepository.kt`: `deleteAccount(accountId)` и `resetBalanceBase(accountId)` (checkedAt = null, balance 0, estimated = false).
3. Use-case `RepairFromJournal.invoke` (:44-77): для каждого затронутого счёта собрать `RepairEvent` из `events.all()` (повторный разбор; для `changes` — `change.op`), `RepairBase.pick`; `FromJournal` → `ledger.setBalance(id, balance, checkedAt)` до пересчёта `setCountedBalance`; `None` и операций у счёта не осталось → `deleteAccount`; `None` и операции остались → `resetBalanceBase` + `setCountedBalance(id, sumSince(id, 0), estimated = true)`; не отравлен → прежняя логика. Ручное «Сейчас на счёте» не имеет события с таким `postedAt`, не трогается.
4. Room: `…/data/local/LedgerDao.kt` — `@Query("DELETE FROM accounts WHERE id = :id")` и `@Query("UPDATE accounts SET balanceMinor = 0, checkedAt = NULL, estimated = 0 WHERE id = :id")`. Схема не меняется, миграции нет.
5. `…/data/repository/LedgerRepositoryImpl.kt` (рядом с :67) и `…/data/repository/sample/SampleLedgerRepository.kt` (:58) — оба метода.
6. DI/UI без изменений.

**Тесты** (`T/RepairFromJournalTest.kt`, `FakeLedger` дополнить; плюс `T/RepairBaseTest.kt`):
- `account_created_only_by_false_operation_is_deleted` (красный до фикса).
- `poisoned_checked_balance_is_rebuilt_from_earlier_exact_balance` — два пуша с балансом, поздний ложный → баланс = остаток раннего + операции после него.
- `poisoned_without_earlier_balance_but_with_other_operations_resets_base_and_counts` — `checkedAt == null`, `estimated`, баланс = сумма операций.
- `manual_balance_is_kept` — `checkedAt` не совпадает ни с одним изменённым событием → прежний результат; существующие тесты зелёные без правок.
- `RepairBaseTest`: Keep / FromJournal / None.

**Приёмка:** тесты и сборка зелёные; инварианты: баланс = точный остаток + операции после него; не-операция не влияет на баланс и `checkedAt`; ручной остаток не затирается; деньги только `Money`/Long.

## B-91
**Цель:** потеря данных из-за ключа невозможна по построению: новый ключ и новая фраза базы создаются только когда терять нечего (нет файла или файл не зашифрован); зашифрованный файл не удаляется никогда; при временном сбое Keystore база просто не открывается в этот заход и открывается при повторе; при действительно утраченном ключе данные открываются паролем копии (если он был задан), иначе — экран «повторить / восстановить / начать заново» (старый файл уходит в `.broken`).

**Причины (проверено по коду):**
- `PhoneKeys.backupKey` (`…/data/security/PhoneKeys.kt:30-41`): `getKey == null` → молча новый ключ; ошибка и «нет ключа» неразличимы (`runCatching.getOrNull`). Тот же `backupKey()` зовут `BackupManager:87,171,176`, `BackupPassword:17,22`, `ShotImageFiles:18,24,28`, `CandidateStore:63,72` — любой из них может первым создать «новый» ключ.
- `DatabaseCipher.passphrase` (`…/data/local/DatabaseCipher.kt:89-98`): нет фразы в prefs → генерирует свежую, не глядя, есть ли файл; любое исключение → `null`.
- `AppDatabase.get` (`…/data/local/AppDatabase.kt:129-138`): `null` + зашифрованный файл → `deleteDatabase`.
- Проверка по манифесту: у `BankNotificationListener` нет `directBootAware`, `BOOT_COMPLETED`/`LOCKED_BOOT` не используется → до первой разблокировки слушатель и каталог данных недоступны. Версия «слушатель поднят до разблокировки» не подтверждена; временный сбой Keystore (`ProviderException`/`KeyStoreException` под нагрузкой, смена пользователя) остаётся реальным, поэтому три исхода различаем всё равно. На устройстве проверить: перезагрузка с PIN на экране блокировки → уведомление банка до разблокировки не теряется и не ломает базу.

**Решения (выбрано):**
- Исходы ключа: `PRESENT` / `ABSENT` (алиаса нет, `UnrecoverableKeyException`, `KeyPermanentlyInvalidatedException`) / `UNAVAILABLE` (любая иная ошибка Keystore). Фраза «не открылась» (`AEADBadTagException` при живом ключе) = `REJECTED` (потеря); любое другое исключение при открытии фразы = `UNKNOWN` → как `UNAVAILABLE`, а не потеря (лучше лишний повтор, чем ложное «потеряно»).
- Создание ключа/фразы разрешено только решениями `CreateNew` и `ConvertPlain` (и осознанным «Начать заново»). Глобальный флаг `PhoneKeys.creationAllowed` (volatile): выставляет `StorageGate` по решению; при `false` `backupKey()` возвращает `null`, ничего не генерируя. Закрывает «любой другой вызов создал новый ключ».
- Второй путь: если пользователь задал пароль копии, фраза базы дополнительно запечатывается им (`BackupCipher.sealWithPassword`, уже есть) в **файл** `filesDir/db_secret_pw.bin` (не prefs: переживает порчу prefs; Keystore не нужен). Ввод пароля на экране → `openWithPassword` → фраза → заново запечатывается новым/живым ключом Keystore (`creationAllowed` на этот шаг) → база открывается, данные целы.
- Пока Keystore недоступен (`Wait`): БД не открываем, пустую не создаём. Слушатель делает одну попытку `gate.retry()`; не вышло — уведомление пропускается (в открытом виде писать некуда: приватность важнее). Журнал/починка эти пуши не вернут — принятая потеря окна сбоя.
- Автокопию пустой базой не перетираем (защита на «Начать заново»): `BackupGuard`, как в прежнем плане, но минимальный.

**Слои:**
1. Домен `…/domain/model/StorageGuard.kt` (чисто): `StorageState(file: Absent|Plain|Encrypted, secretStored, key: KeyStatus, secret: SecretCheck{OPENS,REJECTED,NOT_STORED,UNKNOWN}, passwordSealStored)` → `decide(state): Decision`:
   - файла нет: ключ PRESENT → `CreateNew` (старый секрет/seal заменяются); иначе → `PlainFallback` (позже конвертируется);
   - файл Plain: ключ PRESENT → `ConvertPlain`; иначе `PlainFallback`;
   - файл Encrypted: `secret=OPENS` → `Open`; `key=UNAVAILABLE` или `secret=UNKNOWN` → `Wait`; иначе (ключ ABSENT, `REJECTED`, `NOT_STORED`) → `AskPassword` если `passwordSealStored`, иначе `Lost`.
   - `mayCreateKeyOrSecret(decision)` — true только для `CreateNew`, `ConvertPlain`; `actions(decision): Set<Action{RETRY,ENTER_PASSWORD,RESTORE,START_OVER,CLOSE}>`: `Wait` → RETRY, CLOSE; `AskPassword` → всё; `Lost`/`Corrupt` → RETRY, RESTORE, START_OVER, CLOSE. Инвариант: `START_OVER` никогда не при `Wait`/`Open`. `Corrupt` — исход data при падении первого запроса с корректной фразой.
   - `…/domain/model/BackupGuard.kt`: `mayOverwrite(existing: CopyStats?, fresh: CopyStats)` (запрет, если `fresh` пуст, а сегодняшняя копия нет), `mayRotate(fresh)`.
2. Интерфейсов репозитория нет. Use-case не нужен: оркестрация — в `data` (`StorageGate`).
3. Data `security`: `PhoneKeys.lookup(): KeyLookup{Found(key)|Absent|Failed}` (без генерации), `create()`; `backupKey()` = lookup, создаёт только при `creationAllowed`. Ошибки не глотать — маппить в три исхода. `pinSeal()` не трогаем (риск ниже).
4. Data `local`: `DatabaseCipher.prepare` → `Prepared.Ready(passphrase) | Plain | Blocked(decision)`; `passphrase()` собирает `StorageState` (`StorageProbe`), зовёт `StorageGuard.decide`; новая фраза — только при `CreateNew`/`ConvertPlain` (для `ConvertPlain` при уже сохранённой фразе — открыть её). `…/data/security/DbPasswordSeal.kt`: `write(ctx, passphrase, password)`, `open(ctx, password)`, `exists(ctx)`. `AppDatabase`: убрать `deleteDatabase` (:137); `open(context): OpenResult{Opened(db)|Blocked(decision)}` с принудительным открытием (`openHelper.writableDatabase`) в try — исключение при `OPENS`-фразе → `Corrupt`, файл не трогаем; `quarantine(context)` — `renameTo("bankagg.db.broken")` (+`-wal/-shm`; если `.broken` есть — `.broken.<ts>`). Миграций нет (версия не меняется; 18→19 и 19→20 заняты другими пунктами).
5. Data `local/StorageGate.kt` (синглтон, `StateFlow<StorageStatus>`: `Ready|Waiting|Blocked(decision)`): `open()`, `retry()`, `unlockWithPassword(pw)`, `startOver()` (quarantine → `creationAllowed=true` → open), `restoreFrom(uri, pw)` (см. 8). После `Ready` вызывает `ensurePasswordSeal`: если `BackupPassword.get() != null` и файла seal нет — записать (самолечение для паролей, заданных до фикса).
6. `BackupPassword.set` (`…/data/backup/BackupPassword.kt:21-25`): после запечатывания Keystore-ом — `DbPasswordSeal.write` с текущей фразой (брать у `StorageGate`, не из prefs заново). Смена пароля перезаписывает seal. Пароль в приложении не сохраняется в открытом виде.
7. DI/запуск: `AppContainer.kt:76` (`db by lazy`) → `dbRef` из `StorageGate`; `val storage: StateFlow<StorageStatus>`. `App.kt:22-51`: сначала `gate.open()`, весь блок с БД (:27-32 кроме проверки, `trackCapitalHistory`, `autoBackup.start()`, `repairJournal`, `trimJournal`, виджет) вынести в идемпотентный `startDatabaseWork()`, вызывается при `Ready` (и из `retry`). `ReminderWorker.schedule`/`CapitalSnapshotWorker.schedule` — как есть. Точки входа, трогающие БД, проверяют `container.isReady` и выходят/`Result.retry()`: `BankNotificationListener.kt:37`, `ReminderWorker:21`, `CapitalSnapshotWorker:18`, `NewBankActionReceiver:18`, `CashbackWidget:109,122`.
8. Восстановление из копии на экране: файл + пароль проверяются **до** карантина (`data/backup/BackupFileCheck(context).check(uri, pw)`: только расшифровка, без БД; копии, запечатанные ключом телефона, при потере ключа не открываются — честно сказать «эта копия не открывается на этом телефоне»). Успех → `startOver()` → `backupManager.restoreFrom(uri, pw)`. Неудача проверки — карантина нет.
9. UI: `ui/storage/StorageErrorScreen.kt` + `StorageErrorViewModel` (ветка в `ui/common/ViewModelFactory`, берёт `StorageGate`, БД не касается). `MainActivity.onCreate` (:305-346): при `Waiting` — 3 тихих повтора по 500 мс, затем экран; при `Blocked` — экран вместо `AppRoot`/`LockGate`; навигация и остальные VM не создаются. Блок `PasswordField` (сейчас private в `ui/settings/SettingsBackupScreen.kt:400`) вынести в `ui/common` + строка в `docs/design-system/README.md` + витрина/`showcase.html` (правило `ui/CLAUDE.md`); остальное — существующие компоненты и токены «Слитка», макет вариант A (artifact KHoZjvBqzJFzay3LUX7UwK).
   Тексты (без слов «ключ», «Keystore», «шифрование»):
   - `Wait`: «Данные пока не открылись» / «Подождите немного и повторите. Ничего не потеряно.» — «Повторить», «Закрыть».
   - `Lost`/`Corrupt`: «Данные не открылись» / «Они на месте, ничего не удалено, но на этом телефоне приложение их сейчас открыть не может.» — «Повторить», «Восстановить из копии», «Начать заново», «Закрыть».
   - `AskPassword`: сверху поле «Пароль копии» и «Открыть»; подпись «Этот пароль вы задали для копий. Он открывает данные.»
   - Подтверждение «Начать заново»: «Приложение начнёт с пустых данных. Прежние данные сохранятся в отдельном файле на телефоне, но приложение их не откроет.» — «Начать заново» / «Отмена».
10. Автокопия: `BackupManager.autoSave` (:86-92) и `AutoBackup.save` (:59-68) — сравнить `CopyStats` текущих данных и сегодняшней копии, `BackupGuard.mayOverwrite`; отказ — не писать и `pending` не возвращать в true (иначе цикл). `AutoBackup.start()` не вызывается до `Ready`.

**Миграция:** нет.

**Тесты** (JVM, `T/StorageGuardTest.kt`, `T/BackupGuardTest.kt`, `T/DbPasswordSealTest.kt` — если `BackupCipher` без `android.*`, иначе проверить этот шаг на устройстве). До фикса не компилируются/красные (классов нет; tester пишет первыми по сигнатурам):
- `fresh_install_key_present_creates_new`; `fresh_install_keystore_down_falls_back_plain`.
- `encrypted_file_secret_opens_is_open`.
- `encrypted_file_keystore_unavailable_waits_never_creates` и `encrypted_file_secret_unknown_waits`.
- `encrypted_file_key_absent_with_stored_secret_is_lost_not_create` (ядро находки 1: `mayCreateKeyOrSecret == false`).
- `encrypted_file_secret_rejected_without_password_seal_is_lost`; `..._with_password_seal_asks_password`.
- `encrypted_file_without_stored_secret_is_lost_never_fresh_secret` (находка 3).
- `plain_file_key_present_converts`; `plain_file_key_absent_is_plain_fallback`; `no_file_stale_secret_is_replaced`.
- `only_create_new_and_convert_plain_may_create_key`; `start_over_never_offered_while_waiting_or_open`; `wait_offers_only_retry_and_close`.
- `BackupGuardTest`: `empty_fresh_does_not_overwrite_nonempty_copy`; `nonempty_overwrites`; `empty_over_empty_allowed`; `empty_does_not_rotate_old_copies`.
- `DbPasswordSealTest`: round trip верным паролем; неверный пароль → `WrongKey`.

**Приёмка:** `./gradlew testDebugUnitTest assembleDebug` зелёные. Инварианты: `deleteDatabase`/`delete()` зашифрованного файла нигде нет (grep: допустимы только `quarantine` и `clearApplicationUserData` в сбросе по PIN, `MainActivity:362`); нигде нет `PhoneKeys.create`/генерации ключа и фразы вне `CreateNew`/`ConvertPlain`/`startOver`; при `Wait`/`Blocked` пустая БД не создаётся и автокопия не пишется; уведомления в этот период ничего не пишут на диск. Устройство (`device-check`): (а) испортить `db_secret` в prefs без пароля копии → экран «Данные не открылись», `bankagg.db` на месте, «Начать заново» → `.broken` создан; (б) то же с заданным паролем копии → поле пароля, верный пароль открывает прежние данные; (в) удалить алиас ключа (`PhoneKeys.deleteAll` через отладочный путь) + пароль → данные открываются паролем; (г) «Восстановить из копии» с неверным паролем не создаёт карантин. Подпись/keystore проекта не трогаем.

**Остаточные риски:** (1) пароль копии становится вторым ключом к базе — на отнятом root-устройстве стойкость базы = стойкость пароля (PBKDF2 из `BackupCipher`); путь включается только если пользователь сам задал пароль, Максиму решить, ок ли; (2) `pinSeal()` при временном сбое отдаёт `null` — проверка PIN идёт по другому пути, соседний баг вне B-91; (3) уведомления, пришедшие в окно сбоя (`Wait`/`Blocked`), теряются; (4) классификация исключений Keystore зависит от прошивки — лучше ложный `Wait`, чем ложное «потеряно», но нестандартные OEM-коды проверить только на железе; (5) старые автокопии, запечатанные ключом телефона, при его потере не открываются — на экране это сказано, спасают только копии с паролем.

## B-93
**Цель:** «Забыли код?» → проверка телефона → выход → смерть процесса → повторный вход показывает ввод кода, а не «Придумайте код».

Причина: `…/ui/lock/LockScreen.kt:85` — `settingNew` в `rememberSaveable` переживает смерть процесса, а проверка телефона — нет.

Слои (домен/Room/DI/VM не меняются):
1. `…/di/AppContainer.kt:199-205` (`LockSession`, живёт только в процессе): `var phoneVerified = false`. Выбрано вместо простого `remember`, чтобы «Придумайте код» переживал поворот экрана, но не смерть процесса.
2. `LockScreen.kt:85`: `settingNew` → `remember`, начальное значение `verified` (параметр `verified: Boolean`, `onVerified: () -> Unit`); `onFooter` (:122) по успеху `phoneCheck` вызывает `onVerified()` и `settingNew = true`.
3. `MainActivity.kt:355-358`: передать `verified = session.phoneVerified`, `onVerified = { session.phoneVerified = true }`; сброс — в `LockSession.markUnlocked()` (вводится в B-94).
4. Тесты (общий файл с B-94, `T/LockSessionTest.kt`): `new_session_is_not_phone_verified`; `markUnlocked_clears_phoneVerified`. UI без JVM-теста.

**Приёмка:** сборка зелёная; на устройстве: «Забыли код?» → подтвердить телефоном → `adb shell am kill ru.maxrubtsoff.bankagg.debug` → открыть из недавних → обычный ввод кода; после подтверждения и поворота «Придумайте код» остаётся. Инвариант: подтверждение телефона не хранится на диске и в saved state.

## B-94
**Цель:** после включения пина в настройках сессия разблокирована, возврат из выбора фото/настроек/«Поделиться» не показывает экран входа.

Причина: `session.unlocked = true` ставится только в `MainActivity.unlock()` (:368-373); включение пина (`SettingsViewModel.kt:70`, `ui/common/ViewModelFactory.kt:49`) сессию не трогает, и `onStart` (:379-381) через `AppLock.needsUnlock(coldStart = true)` блокирует.

Слои:
1. `…/di/AppContainer.kt:199-205` `LockSession`: `fun markUnlocked() { unlocked = true; backgroundedAt = null; phoneVerified = false }`.
2. `MainActivity.unlock()` (:368-373) — `session.markUnlocked()`, `locked.value = false`.
3. `ViewModelFactory.kt:49`: `SettingsViewModel(container.settings) { container.checkPin.setNew(it); container.lockSession.markUnlocked() }` (пин включает уже вошедший пользователь). Путь `onNewPin` на экране входа (`MainActivity.kt:358`) не ломается.
4. Домен `AppLock` не меняется.

**Тесты** (`T/LockSessionTest.kt`, `T/AppLockTest.kt`): `markUnlocked_sets_unlocked_and_clears_background` (красный: метода нет); `after_enabling_pin_return_does_not_lock` — `needsUnlock(enabled = true, coldStart = !session.unlocked, backgroundedAt = null, now)` после `markUnlocked()` = false; `background_longer_than_timeout_still_locks`.

**Приёмка:** устройство: включить пин → выбрать фото / системные настройки → вернуться без экрана входа; сворачивание дольше таймаута блокирует; холодный старт процесса всегда требует код.

## B-95
**Цель:** ежемесячная подписка с датой 31-го: 31.01 → 28.02 → 31.03 → 30.04 …; напоминание приходит в верный день.

Причина (`…/domain/model/Subscription.kt:174-180`): `upcoming` шагает цепочкой `period.next` (31.01→28.02→28.03…). Глубже: `paid()` (:146-150) строит дату от `p.date`, и сохранённая `nextDate` дрейфует. Исходный день нигде не хранится.

Решение: хранить `anchorDay` (nullable; null — взять `nextDate.dayOfMonth`), каждый шаг прижимать к якорю.

Слои:
1. Домен `Subscription.kt`: поле `anchorDay: Int? = null` последним (:51-65); свойство `anchor = anchorDay ?: nextDate.dayOfMonth`; `Period.nextAnchored(from, anchorDay)` — MONTH: `from.plusMonths(1)` + `withDayOfMonth(min(anchorDay, lengthOfMonth))`; YEAR — так же по году (29.02 возвращается в високосный год); WEEK/DAYS как `next`. `upcoming` (:175-180) и `paid` (:146-150) используют `nextAnchored(…, s.anchor)`. `fromFinding` (:170-172): `anchorDay = f.payments.last().date.dayOfMonth`. `Subscriptions.reanchor(old: Subscription?, edited): Subscription` — если `old == null || old.nextDate != edited.nextDate` → `anchorDay = edited.nextDate.dayOfMonth`, иначе якорь сохраняется.
2. Room: `…/data/local/SubscriptionEntity.kt` — `anchorDay: Int?`; маппинг в `toEntity/toDomain`. **Миграция 19 → 20:** `MIGRATION_19_20`: `ALTER TABLE subscriptions ADD COLUMN anchorDay INTEGER` (nullable, без DEFAULT); `version = 20` (`AppDatabase.kt:19`, после занятой B-92 версии 19); добавить в `addMigrations(...)` (:143) после `MIGRATION_18_19`. Старые строки → null → якорь из `nextDate`.
3. Бэкап: `…/data/backup/BackupManager.kt` — экспорт/импорт подписок: `anchorDay` (в старых копиях нет → null); `…/data/repository/sample/SampleOtherRepositories.kt` — по компиляции.
4. VM `SubscriptionsViewModel.save`/`saveWithPicture` (:94-109): `Subscriptions.reanchor(existing, s)` перед `repo.save`.
5. DI/Factory/UI не меняются; grep `period.next(` и `upcoming(` на прямые вызовы (напоминания в `data/notify`).

**Тесты** (`T/SubscriptionsTest.kt`): `monthly_31st_upcoming_keeps_day_through_short_months` (28/29.02, 31.03, 30.04, 31.05…; красный до фикса); `monthly_30th_clamps_in_february_and_returns`; `paid_in_short_month_returns_to_anchor_day` (платёж 28.02, якорь 31 → 31.03); `yearly_leap_day_returns_to_29_feb`; `reanchor_keeps_anchor_when_date_unchanged_and_resets_on_edit`. Существующие `SubscriptionsTest`/`SubscriptionDecorTest` зелёные.

**Приёмка:** тесты и сборка; если экспортируется схема — закоммитить новый json в `app/schemas`; миграция сохраняет подписки; инвариант: дата не «залипает» на меньшем дне после короткого месяца; неделя/дни не меняются.

## B-98
**Цель:** пополнение вклада вне срока не делает доход отрицательным: «Вложено» и `project` используют один набор пополнений.

Причина: `DepositMath.project` (`…/domain/model/DepositMath.kt:51-53`) берёт пополнения в `[start, end)`, а `Deposit.investedTotal` (`…/domain/model/Deposit.kt:43`) — все; `summary` (:152, :162) считает `income = payout − invested` < 0.

Решение: единый источник `Deposit.countedTopUps` (даты в `[startDate, endDate)`), пополнение вне срока не считается вложением и не растит выплату.

Слои:
1. `Deposit.kt`: `val countedTopUps get() = topUps.filter { !it.date.isBefore(startDate) && it.date.isBefore(endDate) }`; `investedTotal` (:43) = `principal + countedTopUps.sum`.
2. `DepositMath.project` (:49-53): `countedTopUps` вместо `topUps` и локального фильтра.
3. Room/Repo/DI/UI без изменений. Записи пополнений не удаляем; отдельный индикатор «не учтено» — возможный follow-up дизайна.

**Тесты** (`T/DepositMathTest.kt`): `topup_on_end_date_does_not_make_income_negative` (`summary.income == project.totalIncome`, красный до фикса); `topup_after_end_is_not_invested`; `topup_before_start_is_not_invested`; `topup_inside_term_still_counts` (регресс); `summary_income_equals_payout_minus_invested_for_mixed_topups`.

**Приёмка:** тесты и сборка; инвариант: `Summary.income >= 0`, `investedTotal` и `project` согласованы. `BankSummary.kt:53` и `DepositDetailScreen.kt:242` подхватят через свойство.

## B-99
**Цель:** автосинк подписки не откатывает параллельную правку (`setRemind` и др.).

Причина (`…/ui/subscriptions/SubscriptionsViewModel.kt:62-71`): `repo.save(synced)` из `combine` со стухшим снимком `s`, отдельной корутиной без порядка относительно записей пользователя.

Решение: синк вынесен из `combine`, применяется к свежей записи; все записи VM — под одним `Mutex`.

Слои:
1. Интерфейс `…/domain/repository/SubscriptionRepository.kt`: `suspend fun get(id: String): Subscription?` (если нет); реализация в `data/repository` и `SampleOtherRepositories.kt`; DAO `@Query("SELECT * FROM subscriptions WHERE id = :id")` (`SubscriptionEntity.kt:49` рядом), схема не меняется.
2. Домен: `Subscriptions.syncFresh(fresh, payments): Subscription?` — `sync(fresh, payments)` или `null` при отсутствии изменений; меняет только `nextDate/priceMinor/previousPriceMinor`, остальное из `fresh`.
3. VM: `state = combine(all, payments, today.date)` (:62-76) — без побочных эффектов. Синк — в `init`: `combine(all, payments).collect { mutex.withLock { активные → repo.get(id) → syncFresh → repo.save } }`; тот же `mutex` вокруг `save`, `saveWithPicture` (:94-109), `setRemind` и прочих записей VM.
4. DI/Factory/UI без изменений.

**Тесты** (`T/SubscriptionsTest.kt`): `syncFresh_keeps_remind_flag_changed_after_snapshot` (платёж сдвигает дату, `fresh.remind = true` → оба); `syncFresh_returns_null_when_nothing_to_do`; `syncFresh_does_not_touch_title_decor_status`. Гонку корутин на JVM не тестируем.

**Приёмка:** сборка и тесты; в `combine` нет `repo.save`; устройство: переключить напоминание сразу после пуша-платежа — флаг не откатывается.

## B-103
**Цель:** закрытая подсказка общего обучения не возвращается после пересоздания Activity (шрифт/тема).

Причина (`…/ui/MainActivity.kt:526-548`): `toursDone` собирается с `initialValue = emptySet()`; первая композиция видит пустой набор, `TourScript.startAfterSetup` (`…/domain/model/Tour.kt:211`) = true до прихода реального значения, а `setupSeen` в `rememberSaveable` уже true. (`SettingsStore._tours`, `…/data/settings/SettingsStore.kt:185-190`, держит значение синхронно, но отдаётся как `Flow`.)

Решение: отличать «не загружено» от «пусто»; интерфейс `SettingsRepository` не менять.

Слои:
1. Домен `Tour.kt:211`: `TourScript.shouldStartAfterSetup(loaded, onboarded, setupSeen, done): Boolean = loaded && onboarded && setupSeen && TourSection.GENERAL !in done`.
2. `MainActivity.kt:526-527`: `toursDone.collectAsStateWithLifecycle<Set<String>?>(initialValue = null)`; `toursLoaded = value != null`; `LaunchedEffect(toursLoaded, onboarded, setupSeen)` (:546-548) вызывает `shouldStartAfterSetup`; `tourOffer` (:550-552) не предлагает обучения при `!toursLoaded`.
3. Проверить, что крестик подсказки вызывает `markTourDone` для GENERAL; если нет — добавить, иначе подсказка вернётся и при загруженном состоянии.
4. Room/DI/Factory без изменений.

**Тесты** (`T/TourTest.kt` — найти файл с `startAfterSetup`): `not_loaded_never_starts_general_tour` (красный: функции нет); `loaded_empty_after_setup_starts`; `loaded_with_general_done_does_not_start`; `not_onboarded_does_not_start`.

**Приёмка:** тесты и сборка; устройство (`font_scale` 1.3 / смена темы): закрыть подсказку крестиком → сменить масштаб → не возвращается; на чистой установке после первичной настройки показывается один раз. `MainActivity.kt` правят B-93/B-94/B-103 (и вне плана B-102/B-104) — одна группа/один агент, участки :316-373, :526-552.

---

Ниже — пункты истории капитала (B-79, B-85, B-86, B-97, B-101). Общие замечания:
- `T/` = `app/src/test/java/ru/maxrubtsoff/bankagg/domain/`; `…/` = `app/src/main/java/ru/maxrubtsoff/bankagg/`.
- **Порядок коммитов в группе:** B-97 → B-86 → B-79 → B-85 → B-101 (после ответа Максима). B-79 и B-85 вместе — один агент (общий use-case `SyncCapitalPast`).
- **Совместимость с F-51** (шторка будет править `CapitalHistoryViewModel.kt`, `HistoryLine.kt`, `CapitalEvents.kt`): этими пунктами **не трогаются** `HistoryLine.kt` и `CapitalHistoryViewModel.kt` совсем; в `CapitalEvents.kt` правятся только: блок операций `build` :165-177 (B-86 — сумма/подпись, поле `target` и `kind` не трогаем), вызов `moved(...)` :209 и тело `moved` :236-287 (B-85/B-86), `reasonOf` :311-344 (B-85, только фильтры дат). Объявления `HistoryEvent`/`HistoryTarget`/`HistoryKind` (:8-89) и блоки наличных :202-208 не меняются — их меняет F-51. Если F-51 добавит поле в `HistoryEvent` (id операции), B-86 его не задевает.
- Миграций Room в этих пунктах нет (B-101 по варианту А — без миграции; вариант Б см. «Вопрос Максиму»).

## B-97
**Цель:** операции банков, убранных из «Моих банков», и операции исключённых счетов не видны в истории капитала и в поиске подписок.

Решение: ограничить в одной точке — декоратор `MyBanksScope.Ledger` (им уже пользуются все экраны, `AppContainer.kt:94`); копия (`BackupManager` ходит в `db` напрямую, :288) и экран счёта не задеваются. VM истории не трогаем (конфликт с F-51 исключён).

Слои:
1. Домен `…/domain/usecase/MyBanksScope.kt` (после `only` :23-24): чистая `fun ops(transactions: List<Transaction>, accounts: List<Account>, mine: Set<String>): List<Transaction>` — оставляет операцию, если `keeps(t.brand, mine)` и её счёт (`accountId`) не `excluded`; операция без известного счёта остаётся (правило «без банка — видно»). `accounts` — **нескоупленный** список (`inner.observeAccounts()`), чтобы флаг `excluded` был виден.
2. `MyBanksScope.Ledger` (:29-32): `override fun observeTransactions(limit: Int)` = `combine(inner.observeTransactions(limit), inner.observeAccounts(), banks.myBanks) { t, a, mine -> ops(t, a, keys(mine)) }`. Обновить KDoc класса (:14-19): операции скрываются так же.
3. Интерфейсы/Room/DI/Factory/UI — без изменений. `SubscriptionsViewModel.kt:46` и `CapitalHistoryViewModel.kt:97` получают уже отфильтрованный поток. `BuildCapitalHistory.kt:39` тоже — реконструкция и так берёт только неисключённые счета (`CapitalReconstruction.kt:56`), итог не меняется.
4. Песочница (`SampleLedgerRepository`) не скоупится — без изменений.

**Тесты** (`T/MyBanksScopeTest.kt`, красные до фикса — функции нет):
- `ops_drop_operations_of_removed_bank` — операция `BankBrand.TBANK` при `mine = {sber}` уходит, `SBER` остаётся.
- `ops_keep_unknown_brand` — `BankBrand.UNKNOWN` остаётся.
- `ops_drop_operations_of_excluded_account` — счёт `excluded = true` → его операции уходят, соседнего — остаются.
- `ops_keep_operation_of_unknown_account` — `accountId` вне списка счетов остаётся.

**Приёмка:** сборка и тесты зелёные; устройство: убрать банк из «Моих банков» → его операции пропали из «Истории капитала» и из подсказок «Найти подписки»; вернуть банк — вернулись; исключить счёт (U-18) — его операции пропали из истории, на экране счёта остались.

## B-86
**Цель:** операция валютного счёта в истории показана в рублях по курсу ЦБ своего дня (с исходной суммой в валюте в подписи) и только один раз; группы складывают только рубли.

Слои (только домен, `…/domain/model/CapitalEvents.kt`):
1. Приватный помощник в `CapitalEvents` (рядом с `dayOf` :369): `rateOn(rates, currency, day): Long?` — тот же выбор, что в `reasonOf` :312-313 (`FxRates.latestOnOrBefore`, иначе самый ранний известный); `reasonOf` переводится на него (поведение то же). И `rubOf(t: Transaction, rates, zone): Long?` = `FxMath.rubOf(t.amount.minor, rateOn(t.currency, dayOf(t.postedAt)))`, null — нет курса.
2. `build`, блок операций :165-177, только для `t.currency != RUB`:
   - `amountMinor` = `rubOf(t, rates, zone)` (null при отсутствии курса — строка без рублёвой суммы, в группы не идёт: `HistoryRows` уже считает `amountMinor ?: 0`);
   - `foreign = ForeignAmount(t.currency, t.amount.minor)` (метки графика `ChartMarks.kt:60` уже умеют);
   - `subtitle` = `listOfNotNull(t.counterparty ?: titleOf[t.accountId], Money.ofMinor(t.amount.minor).format(t.currency)).joinToString(" · ")` — исходная сумма видна в строке без правки `HistoryLine.kt` (F-51 её трогает).
   Рублёвые операции, `kind`, `target` — без изменений.
3. Повтор через `moved()`: сигнатура `moved(points, fxOps, deposits, rates, transactions, zone)` (вызов :209 — передать отфильтрованные `transactions.filter { it.id !in own }` не нужно: валютные не бывают в `own`, передать `transactions`). В `moved` рядом с `dealt` (:268): `val booked = transactions.filter { it.currency != RUB && dayOf(it.postedAt) in (prev.date, cur.date] }.sumOf { rubOf(it) ?: 0L }`; `revalued = Δ CURRENCY − dealt − booked`. Порог `manual` в `reasonOf` (:341-342) не трогаем.
4. UI/Room/DI — без изменений. Известное ограничение (вне пункта): курсовая переоценка остатка валютного **счёта** по-прежнему не раскладывается на «ноги» (`reasonOf` берёт валюты только из `fxOps`) — уходит в «Остаток изменили вручную» только курсовой эффект, не сама операция.

**Тесты** (`T/CapitalEventsTest.kt`, красные до фикса):
- `foreign_account_payment_is_in_rubles_at_cbr_rate` — счёт UZS, платёж −20 263 000,00 UZS, курс 0,0075 ₽ → `amountMinor` = рублёвый эквивалент (через `FxMath.rubOf`), а не −2 026 300 000.
- `foreign_account_payment_keeps_foreign_amount_and_shows_it_in_subtitle` — `foreign = ForeignAmount("UZS", −2026300000)`, подпись содержит «UZS»/символ.
- `foreign_account_payment_without_rate_has_no_ruble_sum` — курсов нет → `amountMinor == null`.
- `foreign_account_payment_is_not_listed_again_as_revaluation` — точки: вчера CURRENCY = X, сегодня X − рубли платежа, курс неизменен → событий `REVALUATION` нет.
- `group_of_foreign_and_ruble_spends_sums_rubles` (`HistoryRows.days`) — рублёвая и валютная траты в один день → итог группы = сумма рублёвых эквивалентов.

**Приёмка:** тесты и сборка; инвариант: в `HistoryEvent.amountMinor` всегда копейки ₽; валюта не складывается с рублями без курса ЦБ. Устройство: операция валютного счёта показывает «−… ₽» и «… UZS» в подписи, без второй строки «Переоценка».

## B-79
**Цель:** вклад виден на графике капитала с даты открытия (пополнения — со своих дат, закрытие — с даты окончания) независимо от того, когда его внесли в приложение; уже внесённые вклады тоже переезжают на свои даты.

Причина: прошлое строится один раз (`BuildCapitalHistory.kt:36` — только при пустой истории), дальше пишется лишь точка «сегодня» (`RecordCapitalDay.kt:29-37`). Вклад, внесённый задним числом, попадает только в сегодняшнюю точку.

Решение: часть «Вклады» — точная функция списка вкладов (`DepositMath`), поэтому на каждое изменение списка вкладов **пересчитываем часть DEPOSITS у всех сохранённых дней раньше сегодня** и досоздаём недостающие дни (дата открытия, пополнения, закрытия, день перед первым открытием). Выбор полного пересчёта, а не дельты: он идемпотентен и чинит уже сохранённые данные.

Слои:
1. Домен, новый `…/domain/model/CapitalPast.kt` (`object CapitalPast`):
   - `depositsOn(deposits, day): Long` — вынести из `CapitalReconstruction.kt:76-78` (фильтр `!day.isBefore(startDate) && isActive`, `project(...).currentValue`); `CapitalReconstruction.build` вызывает её (поведение то же).
   - `withDeposits(points, deposits, included: Set<CapitalPart>, today): List<CapitalPoint>` — возвращает **только изменившиеся или новые** точки, все с датой `< today` и `≥ today − CapitalReconstruction.MAX_DAYS`:
     - для каждой существующей точки `DEPOSITS := depositsOn(day)`; `totalMinor += (new − old)`, если `DEPOSITS in included` (до B-101 итог хранится; после B-101 он на чтении пересчитывается и это просто согласованный кэш);
     - ключевые дни без точки: `startDate`, даты пополнений, `endDate` (если `< today`), и `min(startDate) − 1`, если раньше первой точки. Новая точка берёт остальные части у последней точки до неё; если её нет (день раньше истории) — у первой точки (выбор: линия до начала истории и так рисовалась ровной, сеть/курсы для реконструкции не нужны); DEPOSITS — `depositsOn`, итог — сумма `included` частей.
   - Пустая история → пустой результат (её строит `BuildCapitalHistory`).
2. Use-case, новый `…/domain/usecase/SyncCapitalPast.kt`: `class SyncCapitalPast(history: CapitalHistoryRepository, deposits: DepositRepository, settings: SettingsRepository, today: Today)`; `suspend fun track()` = `deposits.observeAll().distinctUntilChanged().collect { list -> val changed = CapitalPast.withDeposits(history.days(), list, settings.capitalParts.first() intersect settings.features.first().capitalParts, today.date.value); if (changed.isNotEmpty()) history.upsertAll(changed) }`. Репозиторий — скоупленный (`AppContainer.kt:109`), как у `GetCapital`: вклад убранного банка уходит из прошлого и возвращается с банком (правило B-21).
3. Интерфейс репозитория/Room — без изменений (`upsertAll` есть, `CapitalHistoryRepository.kt:12`). Миграции нет.
4. DI `…/di/AppContainer.kt:176-185`: `val syncCapitalPast by lazy { SyncCapitalPast(capitalHistory, depositRepository, settings, today) }`; `trackCapitalHistory()` — после `buildCapitalHistory()`: `coroutineScope { launch { syncCapitalPast.track() }; recordCapitalDay.track() }` (сначала строится прошлое, потом оба следят параллельно; пишут разные дни: sync — `< today`, record — `today`).
5. VM/Factory/Screen — без изменений (точки приходят через `observeDays`).

**Тесты** (`T/CapitalPastTest.kt`, новый; красные — объекта нет):
- `deposit_added_today_with_past_start_lands_on_start_date` — точки 01.05…сегодня без вклада, вклад с `startDate` 01.06 → точка 01.06 имеет DEPOSITS = principal, 31.05 — 0.
- `missing_start_day_is_created_with_carried_parts` — точек на 01.06 нет (есть 28.05 и 05.06) → создана точка 01.06, ACCOUNTS/CURRENCY/CASH как у 28.05.
- `deposit_older_than_history_prepends_day_before_start` — первая точка 10.06, вклад с 01.06 → точки 31.05 (DEPOSITS 0) и 01.06, прочие части как у 10.06.
- `topup_and_close_days_get_points` — пополнение 15.06 и закрытие 20.06 → точки есть, DEPOSITS на 20.06 = 0.
- `today_is_never_touched` — точка сегодня не попадает в результат.
- `second_run_changes_nothing` — повторный вызов на результате первого → пусто (идемпотентность).
- `total_follows_only_when_deposits_included` — DEPOSITS не в `included` → `totalMinor` не меняется.
- `CapitalReconstructionTest` (существующие) остаются зелёными после выноса `depositsOn`.

**Приёмка:** тесты и сборка; инварианты: точки только `< today`, суммы — копейки, повторный прогон ничего не пишет. Устройство: внести вклад с датой открытия 01.06.2026 → на графике «Вклады» и «Всего» ступенька на 01.06, не сегодня; уже внесённый ранее такой вклад после обновления тоже на своей дате.

## B-85
**Цель:** вклад/пополнение/валютная сделка с прошлой датой не дают в истории ложных «Проценты» и «Переоценку валюты».

Две причины: (а) `moved` считает взнос вложением, только если его дата **равна** дате точки (`CapitalEvents.kt:242-245, 251, 268`) — при пропуске дней (точек нет каждый день) взнос уходит в «Проценты»/«Переоценку»; (б) сделка задним числом попадает в сохранённые точки только сегодня (как вклад в B-79), а `dealt` ищет её в прошлом.

Слои:
1. Домен `…/domain/model/CapitalEvents.kt`, `moved` :236-287 и `reasonOf` :311-344 — дата взноса сравнивается с **полуинтервалом** `(prev.date, cur.date]` вместо `== cur.date`: `own` (:242-245), `put` (:251), `dealt` (:268), в `reasonOf` сделки «дня» (:322 — `it.date.isAfter(from) && !it.date.isAfter(to)`). Помощник `private fun LocalDate.inStep(from, to)`. Больше в `CapitalEvents` ничего не меняется.
2. Домен `…/domain/model/CapitalPast.kt` (из B-79): `withFxChange(points, before: List<FxOperation>, after: List<FxOperation>, rates, included, today): List<CapitalPoint>` — сравнивает списки по `id` (добавлена/удалена/изменена сделка); для каждой точки `< today` прибавляет к CURRENCY `fxValue(after∖before, day) − fxValue(before∖after, day)`, где `fxValue(ops, day) = Σ по валютам FxMath.rubOf(qty со сделок с date ≤ day, курс дня)` — та же формула, что в `CapitalReconstruction.kt:71-75` (вынести туда же общей функцией `fxOn`); курс дня — как `CapitalReconstruction.rateAt` (:90-92, сделать `internal`). Дни сделок без точки создаются так же, как в `withDeposits`. Выбор дельты, а не полного пересчёта: CURRENCY включает валютные счета, которые точно не восстановить; дельта меняет только вклад самих сделок.
3. Use-case `SyncCapitalPast` (B-79): второй поток `fx.observeAll()` (+ `rates`): помнит предыдущий список в памяти; первая эмиссия — только запоминание, дальше на каждую смену — `withFxChange` → `upsertAll`. Конструктор + `fx: FxRepository, rates: RatesRepository`; `track()` = два `launch` в `coroutineScope`. DI-строка B-79 дополняется `fxRepository, ratesRepository`. Ограничение: сделка, внесённая до этого обновления, остаётся как была (дельту не от чего считать) — история таких сделок не перестраивается.
4. Room/интерфейсы/VM/UI — без изменений.

**Тесты** (красные до фикса):
- `T/CapitalEventsTest.kt`: `deposit_opened_between_points_is_not_interest` — точки 28.05 (DEPOSITS 0) и 05.06 (DEPOSITS 1 000 000 ₽ + проценты за 4 дня), вклад с 01.06 → «Проценты» = только начисленное, не 1 000 000+; `topup_between_points_is_not_interest`; `fx_buy_between_points_is_not_revaluation` — точки 01.06 и 08.06, покупка $1 000 04.06, курс не менялся → `REVALUATION` нет; `fx_buy_between_points_revaluation_counts_only_rate_move` — курс сдвинулся → переоценка = qty × Δкурса после сделки, не вся сумма; существующие тесты «в день точки» остаются зелёными.
- `T/CapitalPastTest.kt`: `backdated_fx_buy_shifts_currency_from_its_date` — покупка датой неделю назад → CURRENCY прошлых точек с её даты += qty × курс дня, до неё — без изменений; `removed_fx_deal_is_taken_out_of_past`; `unchanged_fx_list_changes_nothing`; `fx_shift_never_touches_today`.

**Приёмка:** тесты и сборка; инвариант: Σ «Проценты» + взносы за шаг = Δ DEPOSITS; «Переоценка» = Δ CURRENCY − сделки шага − операции валютных счетов шага (B-86). Устройство: внести вклад на 1 000 000 с открытием месяц назад и покупку $1 000 недельной давности → в истории «Новый вклад» и «Купили» на своих датах, нет «Проценты +1 000 000» и «Переоценки» на всю сумму.

## B-101
**Цель:** итог капитала на графике не прыгает при смене учитываемых частей.

Диагноз: дневная точка уже хранит **каждую часть отдельно** (`capital_days.accountsMinor/depositsMinor/fxMinor/cashMinor`, `CapitalSnapshotEntity.kt:13-20`); от состава зависит только сохранённый `totalMinor` (`RecordCapitalDay.kt:32-34` берёт `snap.totalMinor`, `CapitalReconstruction.kt:83` — сумма `included`). Вторая дыра: `GetCapital.kt:33` выкидывает разделы, выключенные в «Функциях», и `RecordCapitalDay.kt:34` пишет за них 0 — их прошлое не копится.

Решение (вариант А, если Максим не выберет иное): итог собирается **на чтении** из частей по текущему составу; запись хранит все части всегда. **Миграция Room не нужна** (колонки частей есть; `totalMinor` остаётся и пишется как раньше — для старых копий и как кэш). **Пересчёт истории не нужен** для частей, исключённых переключателем «учитывать» (они хранились). Не восстанавливаются только дни, когда раздел был выключен в «Функциях» (за них в базе 0) — это и есть «Вопрос Максиму».

Слои:
1. Домен `…/domain/model/CapitalHistory.kt` (после `CapitalPoint` :7-10 / в `object CapitalHistory`): `fun totalled(points: List<CapitalPoint>, included: Set<CapitalPart>): List<CapitalPoint>` — `totalMinor = Σ parts[p] для p in included`.
2. Use-case/декоратор, новый `…/domain/usecase/CapitalDaysScope.kt`: `class CapitalDaysScope(inner: CapitalHistoryRepository, settings: SettingsRepository) : CapitalHistoryRepository by inner`, `override fun observeDays()` = `combine(inner.observeDays(), settings.capitalParts, settings.features) { d, parts, f -> CapitalHistory.totalled(d, parts intersect f.capitalParts) }` (по образцу `MyBanksScope`; так VM не меняются — нет конфликта с F-51).
3. `GetCapital.kt:25-35`: параметр `operator fun invoke(allParts: Boolean = false)`; при `true` не фильтрует `snap.parts` по `kept` (:33). `RecordCapitalDay.kt:23, 27` зовут `getCapital(allParts = true)` — за выключенные разделы пишутся настоящие значения.
4. DI `…/di/AppContainer.kt:175`: рядом `val capitalDays: CapitalHistoryRepository by lazy { CapitalDaysScope(capitalHistory, settings) }`. `RecordCapitalDay`/`BuildCapitalHistory`/`SyncCapitalPast` остаются на `capitalHistory` (сырые точки).
5. Factory `…/ui/common/ViewModelFactory.kt`: в ветках `CapitalHistoryViewModel` и `CapitalViewModel` (`CapitalViewModel.kt:49` параметр `history`) передавать `container.capitalDays` вместо `container.capitalHistory`. Код VM не меняется. Песочница (`SandboxContainer`) — оставить как есть.
6. Room/миграция — нет.

**Тесты** (красные до фикса):
- `T/CapitalHistoryTest.kt`: `totalled_sums_only_included_parts`; `totalled_makes_series_flat_when_part_switched_off` — две точки с одинаковыми частями, но разным сохранённым `totalMinor` (сохранены при разных составах) → после `totalled` ряд `series(..., part = null)` ровный; `totalled_keeps_parts_untouched`.
- `T/CapitalCalculatorTest.kt` / тест `GetCapital` (если есть фейки репозиториев; иначе — только доменные): `all_parts_snapshot_keeps_feature_off_part_value`.

**Приёмка:** тесты и сборка; инварианты: итог любого дня = Σ включённых сейчас частей; части в базе не зависят от настроек. Устройство: выключить «Наличные» в учёте капитала → весь график «Всего» сдвинулся целиком, ступеньки в день переключения нет; включить обратно — как было.

**Вопрос Максиму (B-101).** Что должен значить итог прошлого дня на графике «Всего»?
- **А. «По сегодняшнему составу»** (рекомендую): итог любого дня пересчитывается из частей по тому, какие разделы учитываются сейчас. Переключил раздел — весь график сдвигается целиком, скачков нет. Без миграции и пересчёта. Минус: дни, когда раздел был **выключен в «Функциях»**, хранятся без него (0) — после включения его линия начнётся с дня включения. Можно дополнительно один раз досчитать такие дни приблизительно (как при первом построении истории: по операциям и курсам ЦБ) — **А+**, тоже без миграции.
- **Б. «Как было тогда»:** прошлые итоги не меняются, в день смены состава остаётся ступенька, но на графике она помечается («состав изменён»). Нужна миграция Room (20 → 21: `ALTER TABLE capital_days ADD COLUMN includedMask INTEGER`) и макет метки.
- Отдельно: тот же вопрос про **исключённые счета** (U-18) и **убранные банки**. Сейчас часть «Счета» хранится уже без них, поэтому исключение счёта тоже даёт ступеньку. По варианту А её убирает только хранение по каждому счёту (новая таблица дневных остатков счетов, миграция 20 → 21, заметный объём). Делать в этом цикле или оставить ступеньку для счетов? (Вклады по B-79 уже следуют текущему списку: вклад убранного банка уходит из прошлого и возвращается вместе с банком.)
