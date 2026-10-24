# Кэшбэк без справочника категорий — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Строки кэшбэка (со скрина и ручные) сами сводятся к фиксированным группам через синонимы и офлайн-модель смысловой близости. Справочник категорий убирается. Банк угадывается по имени файла скрина. Скрин распознаётся один раз и удаляется после сохранения. Появляются правка строки и правка банка целиком, а при повторном скрине — «Дополнить / Заменить».

**Architecture:** Вся логика в чистом домене: группы, лексический резолвер, ключ идентичности, слияние, банк по имени файла. Эмбеддинги закрыты портом `TextEmbedder`: реализация — ONNX Runtime в `data/embed`, в тестах — фейк. `CashbackItem` получает `groupKey`, сводка и плашка группируют по ключу идентичности. UI использует одно окно выбора с поиском на всех сценариях.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.10.01, Room 2.6.1 + KSP, ONNX Runtime Android 1.21.1, модель `cointegrated/rubert-tiny2` (int8 ONNX: 312 измерений, вектор с токена CLS, L2-нормализация), JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-20-cashback-categories-design.md`

## Global Constraints

- DDD: в `domain/*` нет `android.*` и Compose; UI не трогает Room/Entity; зависимости приходят через `AppContainer` (без Hilt).
- Офлайн и бесплатно: никаких сетевых вызовов в приложении; модель лежит в APK (`app/src/main/assets/embedder/`).
- Room: поднять `version` до 6, допустим `fallbackToDestructiveMigration()`, миграцию старых данных не делаем.
- Не трогать `signingConfigs` и `app/debug.keystore`.
- UI-тексты на русском, в стиле «Слитка»: `Panel`, палитра `Slitok`, шрифты из `typography`, без hex мимо палитры.
- Не добавлять разрешений (`READ_MEDIA_IMAGES` и т. п.); Photo Picker не менять.
- Работа и пуш прямо в `main`, без PR. Коммиты по-русски, в стиле репозитория, с последней строкой `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Локальная проверка: `./gradlew testDebugUnitTest` и `./gradlew assembleDebug`. Если нет `local.properties`, создать (он в `.gitignore`) со строкой `sdk.dir=C\:\\Users\\maxim\\AppData\\Local\\Android\\Sdk`.
- Пути ниже относительно `app/src/main/java/ru/maxrubtsoff/bankagg/` (`B/`) и `app/src/test/java/ru/maxrubtsoff/bankagg/domain/` (`T/`).

## Review Focus

1. Скрин открыт, пока идёт OCR: экран ждёт результат и показывает его, а не пустой или устаревший список (Task 9, ручная проверка в Task 11).
2. Опечатки Tesseract в словах из словаря («Супермаркегы», «Здоровъе») всё равно попадают в группу (Task 1, тест `typoStillMatches`).
3. Модель не загрузилась или упала: резолвер работает только по словарю и ничего не роняет (Task 5, тест `embedderUnavailable_fallsBackToLexical`).
4. Одна категория дважды на одном скрине разными словами («Медицина 3%», «Медицинские услуги 5%»): остаётся одна строка «Здоровье 5%» (Task 6, тест `dedupe_keepsMaxPerIdentity`).
5. Имя файла с хвостом от галереи («…_T-Bank (1).jpg», «.png»): банк всё равно определяется (Task 3, тест `copySuffixAndPng`).

---

### Task 1: Группы, синонимы и словарный резолвер

**Files:**
- Create: `B/domain/model/CategoryGroup.kt`, `B/domain/model/Resolution.kt`, `B/domain/model/LexicalResolver.kt`
- Test: `T/LexicalResolverTest.kt`

**Interfaces:**
- Produces:
  - `data class CategoryGroup(val key: String, val name: String, val iconKey: String, val synonyms: List<String>)`
  - `object CategoryGroups { const val OTHER = "other"; val all: List<CategoryGroup>; fun byKey(key: String): CategoryGroup /* неизвестный ключ → OTHER */; fun filter(query: String): List<CategoryGroup> }`
  - `data class Resolution(val kind: Kind, val groupKey: String, val displayName: String) { enum class Kind { GROUP, AS_IS, EMPTY } }`
  - `object LexicalResolver { fun resolve(text: String): Resolution; fun looksLikeProperName(text: String): Boolean }`

- [ ] **Step 1: Тесты** в `T/LexicalResolverTest.kt`:

```kotlin
@Test fun exactSynonym() = assertGroup("Медицинские услуги", "health", "Здоровье")
@Test fun groupNameItself() = assertGroup("Здоровье", "health", "Здоровье")
@Test fun caseAndYo() = assertGroup("МЕДИЦИНА", "health", "Здоровье")
@Test fun phraseSplit() = assertGroup("Связь, интернет и ТВ", "mobile", "Связь и интернет")
@Test fun phraseSplitAnd() = assertGroup("Здоровье и медицина", "health", "Здоровье")
@Test fun typoStillMatches() { assertGroup("Супермаркегы", "groceries", "Супермаркеты"); assertGroup("Здоровъе", "health", "Здоровье") }
@Test fun unknownStaysAsIs() { val r = LexicalResolver.resolve("Lamoda")
    assertEquals(Resolution.Kind.AS_IS, r.kind); assertEquals("Lamoda", r.displayName); assertEquals(CategoryGroups.OTHER, r.groupKey) }
@Test fun garbageIsEmpty() { assertEquals(Resolution.Kind.EMPTY, LexicalResolver.resolve(" — ").kind); assertEquals(Resolution.Kind.EMPTY, LexicalResolver.resolve("до").kind) }
@Test fun properNames() { assertTrue(LexicalResolver.looksLikeProperName("Lamoda")); assertTrue(LexicalResolver.looksLikeProperName("Детский Мир"))
    assertTrue(LexicalResolver.looksLikeProperName("«Пятёрочка»")); assertFalse(LexicalResolver.looksLikeProperName("Медицинские услуги")) }
@Test fun everyGroupHasSynonymsAndUniqueKey() { assertEquals(CategoryGroups.all.size, CategoryGroups.all.map { it.key }.toSet().size)
    CategoryGroups.all.filter { it.key !in setOf("base", "all", "other") }.forEach { assertTrue(it.key, it.synonyms.size >= 3) } }
@Test fun filterByQuery() = assertEquals(listOf("health"), CategoryGroups.filter("здор").map { it.key })
// assertGroup(text, key, name): resolve → kind GROUP, groupKey == key, displayName == name
```

- [ ] **Step 2: Запустить** `./gradlew testDebugUnitTest --tests "*LexicalResolverTest"`. Ожидается: FAIL (нет классов).

- [ ] **Step 3: Реализация.** Состав `CategoryGroups.all` — ровно текущие 22 пункта `CategoryPresets.all`, в том же порядке: `key` = `iconKey` (`"base"`, `"groceries"`, … , `"other"`), `name` и `iconKey` те же. Синонимы — по 3–8 типовых формулировок Сбера, Т-Банка, Альфы, Яндекса, МТС и Ozon на группу (обязательно: `health` ← «медицина», «медицинские услуги», «клиники», «стоматология»; `mobile` ← «связь», «мобильная связь», «интернет», «тв»; `cafe` ← «фастфуд», «кофейни»). `CategoryPresets.kt` пока не трогать (удаляется в Task 6). `resolve`:
  1. `CategoryMatcher.normalize`.
  2. Меньше 3 букв → `EMPTY` (`displayName = ""`, `groupKey = OTHER`).
  3. Точное совпадение с нормализованным `name` или синонимом → `GROUP`, `displayName = group.name`.
  4. То же через `CategoryMatcher.similarity ≥ 0.85` (опечатки OCR).
  5. Разбить по `,`, ` и `, `/`, `&`, `+`; первая часть, прошедшая шаги 3–4 → `GROUP`.
  6. Иначе `AS_IS`, исходный текст после `trim()`, `groupKey = OTHER`.

  `looksLikeProperName` = нет кириллицы, или есть кавычки `«»"`, или не первое слово начинается с заглавной.

- [ ] **Step 4: Запустить** те же тесты. Ожидается: PASS.

- [ ] **Step 5: Коммит** `Кэшбэк: фиксированные группы категорий и словарный резолвер`.

---

### Task 2: Ключ идентичности строки и слияние «Дополнить»

**Files:**
- Modify: `B/domain/model/Cashback.kt` (поле `groupKey`)
- Create: `B/domain/model/CashbackIdentity.kt`, `B/domain/model/CashbackMerge.kt`
- Test: `T/CashbackIdentityTest.kt`, `T/CashbackMergeTest.kt`

**Interfaces:**
- Consumes: `CategoryGroups.byKey`, `CategoryMatcher.normalize`.
- Produces:
  - `CashbackItem(..., val groupKey: String = CategoryGroups.OTHER)` — новое поле последним параметром.
  - `object CashbackIdentity { fun key(groupKey: String, displayName: String): String }` и `val CashbackItem.identityKey: String`.
  - `object CashbackMerge { enum class Decision { SAVE, ASK }; fun decide(existing: List<CashbackItem>, incoming: List<CashbackItem>): Decision; fun append(existing: List<CashbackItem>, incoming: List<CashbackItem>): List<CashbackItem> }`

- [ ] **Step 1: Тесты.**

```kotlin
// CashbackIdentityTest
@Test fun groupNamedRowKeyIsGroup() = assertEquals("health", CashbackIdentity.key("health", "Здоровье"))
@Test fun asIsRowKeyIncludesText() = assertEquals("clothing|lamoda", CashbackIdentity.key("clothing", "Lamoda"))
@Test fun brandAndGroupDiffer() = assertNotEquals(CashbackIdentity.key("clothing", "Lamoda"), CashbackIdentity.key("clothing", "Одежда"))
// CashbackMergeTest (item(key, name, pct) — CashbackItem с фиксированным месяцем/банком)
@Test fun emptyExisting_save() = assertEquals(SAVE, CashbackMerge.decide(emptyList(), listOf(item("health","Здоровье",5.0))))
@Test fun sameKeys_save() = assertEquals(SAVE, CashbackMerge.decide(listOf(item("health","Здоровье",3.0)), listOf(item("health","Здоровье",5.0))))
@Test fun differentKeys_ask() = assertEquals(ASK, CashbackMerge.decide(listOf(item("health","Здоровье",3.0)), listOf(item("cafe","Кафе",5.0))))
@Test fun append_updatesSameKeyKeepsIdAndAddsNew() {
    val old = item("health","Здоровье",3.0).copy(id = "a")
    val r = CashbackMerge.append(listOf(old), listOf(item("health","Здоровье",5.0), item("cafe","Кафе",7.0)))
    assertEquals(listOf("a"), r.filter { it.groupKey == "health" }.map { it.id }); assertEquals(5.0, r.first { it.id == "a" }.percent, 0.0)
    assertEquals(2, r.size) }
```

- [ ] **Step 2: Запустить** `--tests "*CashbackIdentityTest" --tests "*CashbackMergeTest"`. Ожидается: FAIL.

- [ ] **Step 3: Реализация.** `key`: если `normalize(displayName) == normalize(byKey(groupKey).name)`, то `groupKey`, иначе `"$groupKey|${normalize(displayName)}"`. `append`: сначала существующие строки в исходном порядке (у совпавших по ключу — процент из новой строки, `id` старый), затем новые ключи в порядке поступления.

- [ ] **Step 4: Запустить.** Ожидается: PASS. Плюс `./gradlew testDebugUnitTest` целиком: старые тесты не сломаны (поле с дефолтом).

- [ ] **Step 5: Коммит** `Кэшбэк: ключ идентичности строки и слияние «Дополнить»`.

---

### Task 3: Банк по имени файла скрина

**Files:**
- Create: `B/domain/model/ScreenshotFileName.kt`
- Modify: `B/data/ocr/ShotImageStore.kt`
- Test: `T/ScreenshotFileNameTest.kt`

**Interfaces:**
- Produces:
  - `object ScreenshotFileName { fun guessBank(fileName: String?): BankBrand? }`
  - `ShotImageStore.save(uri: Uri): SavedImage?`, где `data class SavedImage(val path: String, val displayName: String?)` (в том же файле).

- [ ] **Step 1: Тесты.**

```kotlin
@Test fun ozon() = assertEquals(BankBrand.OZON, ScreenshotFileName.guessBank("Screenshot_20261020_141742_Ozon.jpg"))
@Test fun tbank() = assertEquals(BankBrand.TBANK, ScreenshotFileName.guessBank("Screenshot_20261020_141731_T-Bank.jpg"))
@Test fun yandexPayWithSpace() = assertEquals(BankBrand.YANDEX, ScreenshotFileName.guessBank("Screenshot_20261020_141737_Yandex Pay.jpg"))
@Test fun copySuffixAndPng() { assertEquals(BankBrand.TBANK, ScreenshotFileName.guessBank("Screenshot_20261020_141731_T-Bank (1).jpg"))
    assertEquals(BankBrand.OZON, ScreenshotFileName.guessBank("Screenshot_20261020_141742_Ozon.png")) }
@Test fun xiaomiPackage() = assertEquals(BankBrand.SBER, ScreenshotFileName.guessBank("Screenshot_2026-10-20-14-17-42-123_${BankBrand.SBER.primaryPackage}.jpg"))
@Test fun noAppName() { assertNull(ScreenshotFileName.guessBank("Screenshot_20261020-141742.png")); assertNull(ScreenshotFileName.guessBank("1000012345.jpg")); assertNull(ScreenshotFileName.guessBank(null)) }
@Test fun unrelatedApp() = assertNull(ScreenshotFileName.guessBank("Screenshot_20261020_141742_Chrome.jpg"))
```

- [ ] **Step 2: Запустить** `--tests "*ScreenshotFileNameTest"`. Ожидается: FAIL.

- [ ] **Step 3: Реализация.** Сначала ищем подстроку из `BankBrand.packages`. Затем суффикс приложения: убрать расширение и хвост ` (N)`, взять часть после последнего `_` с датой и временем, нормализовать, сравнить с метками `{"ozon"→OZON, "t-bank"→TBANK, "yandex pay"→YANDEX}`. Метки держать в одной `private val` для будущих банков. В `ShotImageStore.save` дочитать `OpenableColumns.DISPLAY_NAME` через `contentResolver.query`; при любой ошибке — `null`. Вызов в `CashbackViewModel.addShot` поправить, чтобы компилировалось: пока брать `.path`; `displayName` подключается в Task 9.

- [ ] **Step 4: Запустить** тесты и `./gradlew assembleDebug`. Ожидается: PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Коммит** `Скрин кэшбэка: банк по имени файла (Samsung, Xiaomi)`.

---

### Task 4: Офлайн-эмбеддер (rubert-tiny2, ONNX)

**Files:**
- Create: `tools/embedder/export_rubert_tiny2.py`, `tools/embedder/requirements.txt`, `app/src/main/assets/embedder/model.onnx`, `app/src/main/assets/embedder/vocab.txt`
- Create: `B/domain/repository/TextEmbedder.kt`, `B/data/embed/WordPieceTokenizer.kt`, `B/data/embed/OnnxEmbedderCore.kt`, `B/data/embed/OnnxTextEmbedder.kt`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/ru/maxrubtsoff/bankagg/data/WordPieceTokenizerTest.kt`, `.../data/OnnxEmbedderCoreTest.kt`

**Interfaces:**
- Produces:
  - `interface TextEmbedder { suspend fun embed(texts: List<String>): List<FloatArray>? }` — векторы L2-нормализованы; `null`, если модель недоступна.
  - `class WordPieceTokenizer(vocab: Map<String, Int>) { fun encode(text: String, maxLen: Int = 64): IntArray; companion object { fun fromVocabLines(lines: Sequence<String>): WordPieceTokenizer } }`
  - `class OnnxEmbedderCore(modelBytes: ByteArray, tokenizer: WordPieceTokenizer) : AutoCloseable { fun embed(texts: List<String>): List<FloatArray> }` — только `ai.onnxruntime.*`, без `android.*`.
  - `class OnnxTextEmbedder(context: Context) : TextEmbedder`

- [ ] **Step 1: Экспорт модели.** Скрипт ставит зависимости из `requirements.txt` (`optimum[onnxruntime]`, `transformers`, `torch` CPU) в venv в scratchpad и запускает экспорт: `optimum` → `feature-extraction` → `onnxruntime.quantization.quantize_dynamic` (QInt8). Результат: `assets/embedder/model.onnx` и копия `vocab.txt`. Дополнительно скрипт печатает `input_ids` для «Медицинские услуги», «Связь, интернет и ТВ», «Lamoda» — эталоны для теста токенайзера. Проверка: `model.onnx` ≤ 50 МБ; вывод скрипта записать в комментарий теста. Если на машине нет Python или экспорт не собирается — остановиться и спросить Максима; сторонний `KvaytG/rubert-tiny2-onnx` без согласия не брать.

- [ ] **Step 2: Тест токенайзера.** Он читает `app/src/main/assets/embedder/vocab.txt` и проверяет, что `encode(...)` для трёх строк совпадает с эталонами из Step 1 (с `[CLS]` в начале и `[SEP]` в конце). Отдельно: `encode("x".repeat(500), maxLen = 64).size == 64`. Запуск → FAIL.

- [ ] **Step 3: `WordPieceTokenizer`.** BERT с учётом регистра (`do_lower_case=false`, акценты не снимать): разбивка по пробелам, знаки пунктуации — отдельные токены, жадный WordPiece с префиксом `##`, `[UNK]` для слов > 100 символов или без разбора; обрезка до `maxLen` с `[SEP]` в конце. Запуск → PASS.

- [ ] **Step 4: Gradle.** `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.21.1")`; для JVM-тестов — `testImplementation("com.microsoft.onnxruntime:onnxruntime:1.21.1")` и исключить `onnxruntime-android` из `testRuntimeClasspath`/`testCompileClasspath` (`configurations.matching { it.name.startsWith("test") }.configureEach { exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android") }` — точное имя конфигурации подобрать). Добавить `androidResources { noCompress += "onnx" }`.

- [ ] **Step 5: Тест ядра.** `OnnxEmbedderCoreTest`: загрузить `model.onnx` и `vocab.txt` из assets; `embed(listOf("Здоровье", "Медицинские услуги", "АЗС"))` → 3 вектора длиной 312, норма ≈ 1 (±1e-3), `cos(здоровье, медуслуги) > cos(здоровье, азс)`. Запуск → FAIL.

- [ ] **Step 6: `OnnxEmbedderCore` и `OnnxTextEmbedder`.** Входы — по именам из экспортированной модели (`input_ids`, `attention_mask`, `token_type_ids`, int64, батч с паддингом нулями). Выход `last_hidden_state[:, 0, :]` + L2-нормализация. `OnnxTextEmbedder` лениво читает оба файла из `assets`, работает на `Dispatchers.Default` под `Mutex`, любой `Throwable` превращает в `null` и больше не пытается. Запуск тестов → PASS. Если JVM ORT в юнит-тестах не поднимается: перенести проверку в `tools/embedder/check.py` (те же утверждения на Python) и отметить это в коммите.

- [ ] **Step 7: Проверка размера.** `./gradlew assembleDebug`, затем размер APK. Ожидается прирост ≤ 120 МБ относительно `main` до задачи (согласовано с Максимом).

- [ ] **Step 8: Коммит** `Офлайн-модель смысловой близости rubert-tiny2 (ONNX)`.

---

### Task 5: Резолвер с моделью и калибровка порогов

**Files:**
- Create: `B/domain/model/CategoryResolver.kt`, `app/src/test/resources/cashback_phrases.tsv`
- Test: `T/CategoryResolverTest.kt`, `app/src/test/java/ru/maxrubtsoff/bankagg/data/CategoryCalibrationTest.kt`

**Interfaces:**
- Consumes: `LexicalResolver`, `CategoryGroups`, `TextEmbedder`, `OnnxEmbedderCore`.
- Produces: `class CategoryResolver(private val embedder: TextEmbedder?, private val high: Float = HIGH, private val mid: Float = MID) { suspend fun resolve(text: String): Resolution; suspend fun suggest(query: String, limit: Int = 3): List<CategoryGroup>; companion object { const val HIGH: Float; const val MID: Float } }`

- [ ] **Step 1: Тесты с `FakeEmbedder`** (словарь «текст → вектор», неизвестный текст → ортогональный вектор):

```kotlin
@Test fun lexicalWinsWithoutModel()          // resolve("Медицинские услуги") == GROUP health, embed не вызывался
@Test fun highSimilarity_renames()           // фейк: «Врачи и анализы» ~ health выше high → GROUP, displayName "Здоровье"
@Test fun midSimilarity_keepsTextTakesGroup() // ~ health между mid и high → AS_IS, displayName "Врачи и анализы", groupKey "health"
@Test fun properName_neverRenamed()          // «Lamoda» ~ clothing выше high → AS_IS, "Lamoda", groupKey "clothing"
@Test fun lowSimilarity_other()              // ниже mid → AS_IS, groupKey OTHER
@Test fun embedderUnavailable_fallsBackToLexical() // embed → null: «Медицинские услуги» → GROUP health; «Врачи» → AS_IS OTHER; без исключений
@Test fun suggest_lexicalFirstThenModel()     // suggest("медиц") → первой health; длина ≤ limit
```

- [ ] **Step 2: Запустить** `--tests "*CategoryResolverTest"` → FAIL.

- [ ] **Step 3: Реализация.** Сначала `LexicalResolver.resolve`: если `GROUP` или `EMPTY`, вернуть его. Иначе модель: векторы групп — `name` и каждый синоним, оценка группы = максимум косинуса; векторы считаются один раз и кешируются под `Mutex`. Оценка ≥ `high` и не `looksLikeProperName` → `GROUP`. Оценка ≥ `mid` (или ≥ `high` у имени собственного) → `AS_IS` с этой группой. Иначе `AS_IS OTHER`. `suggest`: точные и префиксные совпадения по `CategoryGroups.filter`, затем по оценке модели (без модели — по `CategoryMatcher.similarity` с названиями), без дублей. Запуск → PASS.

- [ ] **Step 4: Фикстура** `cashback_phrases.tsv` (`фраза<TAB>kind<TAB>groupKey`): ≥ 40 строк из публичных условий кэшбэка Сбера, Т-Банка, Альфы, Яндекса, МТС и Ozon. Из них ≥ 10 — бренды и партнёры (Lamoda, Пятёрочка, Детский мир, Л'Этуаль, Wildberries, Яндекс Маркет, Ozon, М.Видео, Самокат, Кинопоиск), все с ожиданием `AS_IS`.

- [ ] **Step 5: `CategoryCalibrationTest`** на реальной модели через `OnnxEmbedderCore` (адаптер к `TextEmbedder`):
  - ни один бренд не переименован (`kind != GROUP`) — жёсткий assert;
  - у каждой фразы с ожиданием `GROUP`, которая получила `GROUP`, правильный `groupKey` — жёсткий assert;
  - печать сводки (сколько ожидаемых `GROUP` пойманы, оценки) — для подбора порога.
  Подобрать `HIGH` и `MID` так, чтобы asserts проходили, а поймано было как можно больше; итоговые значения и «поймано N из M» записать в KDoc `CategoryResolver`. Запуск → PASS.

- [ ] **Step 6: Коммит** `Резолвер категорий: модель смысловой близости и калиброванные пороги`.

---

### Task 6: Переход на группы (домен, данные, VM) и удаление справочника

Самая крупная задача: компиляция связывает слои, поэтому всё делается одним коммитом.

**Files:**
- Delete: `B/domain/model/Category.kt`, `B/domain/model/CategoryPresets.kt`, `B/domain/repository/CategoryRepository.kt`, `B/data/repository/CategoryRepositoryImpl.kt`, `B/data/local/CategoryEntity.kt`, `B/data/local/CategoryDao.kt`, `B/data/local/CategoryMappers.kt`
- Modify: `B/domain/model/CategoryMatcher.kt` (удалить `match`, оставить `normalize`/`similarity`), `B/domain/model/CashbackParser.kt`, `B/domain/model/CashbackSummary.kt`, `B/domain/model/CashbackShot.kt`, `B/domain/usecase/RecognizeCashbackShot.kt`, `B/domain/repository/SettingsRepository.kt`, `B/data/settings/SettingsStore.kt`, `B/data/local/{CashbackEntity,CashbackMappers,CashbackShotEntity,AppDatabase}.kt` (+ маппер скрина), `B/data/backup/BackupManager.kt`, `B/di/AppContainer.kt`, `B/ui/common/ViewModelFactory.kt`, `B/ui/cashback/{CashbackViewModel,CashbackScreen,CashbackCard,ShotPreviewScreen,CategoryPickerDialog,FavoriteCategoriesScreen}.kt`
- Test: обновить `T/CashbackParserTest.kt`, `T/CashbackSummaryTest.kt`, `T/CashbackHomeCardTest.kt`, `T/CategoryMatcherTest.kt`; создать `T/RecognizeCashbackShotTest.kt`, `T/ShotLinesTest.kt`

**Interfaces:**
- Consumes: Task 1–5.
- Produces:
  - `CashbackParser.Line(val text: String, val percent: Double, val rawText: String)`; `CashbackParser.parse(rawText: String, brandHint: BankBrand? = null): Result` — без словаря и без дедупликации; `guessBank` как был.
  - В `CashbackShot.kt`: `data class ShotLine(val text: String, val groupKey: String, val percent: Double?, val sourceText: String)`; `CashbackShot(..., val fileName: String? = null, val lines: List<ShotLine> = emptyList())`; из `ShotStatus` удалить `SAVED`.
  - `object ShotLines { fun dedupe(lines: List<ShotLine>): List<ShotLine> }` (ключ — `CashbackIdentity.key`, побеждает максимум процента, порядок первого появления).
  - `RecognizeCashbackShot(ocr: OcrEngine, resolver: CategoryResolver)`; `suspend operator fun invoke(shot: CashbackShot): Draft`, `data class Draft(val status: ShotStatus, val brand: BankBrand?, val lines: List<ShotLine>)`. Строка с `EMPTY` → `text = ""`, `groupKey = OTHER`. Банк = `ScreenshotFileName.guessBank(shot.fileName) ?: CashbackParser.guessBank(...)`.
  - `CashbackSummary.bestPerCategory(items)` — группировка по `identityKey`. `CashbackSummary.homeCard(items, favoriteGroupKeys: Set<String>, limit = 4, maxBanksPerRow = 3)` — только строки, у которых `identityKey == groupKey` и `groupKey in favoriteGroupKeys`; в `CardRow` `category` = имя группы, `iconKey` — иконка группы; параметр `iconOf` удалить.
  - `SettingsRepository`: `val favoriteGroups: Flow<Set<String>>`, `suspend fun setFavoriteGroups(keys: Set<String>)` (по умолчанию пустое множество).
  - `CashbackViewModel`: убрать `categories`, `addCategory`, `setFavorite(id, …)`; добавить `val favoriteGroups: StateFlow<Set<String>>`, `fun toggleFavorite(groupKey: String)`, `suspend fun resolve(text: String): Resolution`, `suspend fun suggest(query: String): List<CategoryGroup>`. `DraftLine(val text: String, val groupKey: String, val percentText: String, val sourceText: String = "")`.

- [ ] **Step 1: Тесты домена.**

```kotlin
// ShotLinesTest
@Test fun dedupe_keepsMaxPerIdentity() // [Здоровье/health 3.0 (из «Медицина»), Здоровье/health 5.0 (из «Медицинские услуги»)] → одна строка 5.0
@Test fun dedupe_brandAndGroupBothKept() // [Lamoda/clothing 10, Одежда/clothing 5] → 2 строки
// RecognizeCashbackShotTest (FakeOcr с готовым текстом, CategoryResolver(null))
@Test fun linesResolvedAndDeduped()    // "Медицинские услуги 5%\nМедицина 3%\nLamoda 10%" → [Здоровье 5.0 health, Lamoda 10.0 other], status RECOGNIZED при fileName "…_Ozon.jpg", brand OZON
@Test fun bankFromTextWhenNoFileName() // fileName null, текст содержит "СберБанк" → SBER
@Test fun unknownBank_needsInput()     // нет банка нигде → NEEDS_INPUT, brand null
@Test fun nothingRead_failed()         // OCR "" → FAILED
// CashbackSummaryTest / CashbackHomeCardTest — переписать под groupKey:
@Test fun bestPerCategory_groupsByIdentity() // Сбер «Здоровье» 3, Т-Банк «Здоровье» 5, Т-Банк «Lamoda» 10 (clothing) → health 5 (Т-Банк) и Lamoda отдельно
@Test fun homeCard_favoriteGroupExcludesBrands() // favorites {clothing}: «Одежда» 5 в карточке, «Lamoda» 10 — нет
```

- [ ] **Step 2: Запустить** `./gradlew testDebugUnitTest` → FAIL (компиляция).

- [ ] **Step 3: Домен.** Реализовать интерфейсы выше; удалить словарные классы; `CategoryMatcherTest` сократить до `normalize` и `similarity`.

- [ ] **Step 4: Данные.**
  - `CashbackEntity` + колонка `groupKey: String`.
  - `CashbackShotEntity` + `fileName: String?`, `linesJson: String` (JSON-массив объектов `{text, groupKey, percent|null, sourceText}` через `org.json` в маппере).
  - `AppDatabase`: убрать `CategoryEntity` и `categoryDao()`, `version = 6`, удалить `MIGRATION_3_4`/`MIGRATION_4_5`.
  - `SettingsStore`: ключ `favorite_groups`, `StringSet`, `MutableStateFlow` как у пакетов.
  - `BackupManager`: убрать блок `categories`, писать и читать `groupKey` у кэшбэка (при отсутствии — `OTHER`) и `favoriteGroups` в настройках.
  - `AppContainer`: `textEmbedder: TextEmbedder by lazy { OnnxTextEmbedder(appContext) }`, `categoryResolver by lazy { CategoryResolver(textEmbedder) }`, `recognizeCashbackShot` с резолвером; `categoryRepository` удалить.

- [ ] **Step 5: VM и UI, чтобы собиралось.**
  - VM: `addShot` после распознавания сохраняет `lines`/`brand`/`status` в скрин; `loadShotDraft` берёт строки из `shot.lines`, OCR не вызывает. Окончательное поведение — в Task 9.
  - `save(...)`: принимает `groupKey`.
  - `CashbackScreen`: иконка строки — `CategoryGroups.byKey(item.groupKey).iconKey`.
  - `FavoriteCategoriesScreen`: список `CategoryGroups.all`, переключатель — `vm.toggleFavorite`.
  - `CategoryPickerDialog`: временно показывает `CategoryGroups.all` без «Своя», заменяется в Task 7.
  - `ShotStatus.SAVED` убрать из `StatusPill` и `RecognitionBanner`.

- [ ] **Step 6: Запустить** `./gradlew testDebugUnitTest assembleDebug` → PASS, BUILD SUCCESSFUL.

- [ ] **Step 7: Коммит** `Кэшбэк: группы вместо справочника категорий (БД v6)`.

---

### Task 7: Окно выбора с поиском

**Files:**
- Create: `B/ui/cashback/CategorySearchSheet.kt`
- Delete: `B/ui/cashback/CategoryPickerDialog.kt`
- Modify: `B/ui/cashback/ShotPreviewScreen.kt`, `B/ui/cashback/CashbackScreen.kt` (`ManualCashbackDialog`)

**Interfaces:**
- Consumes: `vm.suggest`, `vm.resolve`, `CategoryGroups.filter`.
- Produces: `@Composable fun CategorySearchSheet(initialQuery: String, suggest: suspend (String) -> List<CategoryGroup>, resolve: suspend (String) -> Resolution, onPick: (displayName: String, groupKey: String) -> Unit, onDismiss: () -> Unit)`

- [ ] **Step 1: Реализация** (`ModalBottomSheet`, стиль «Слитка»):
  - Поле поиска с фокусом, предзаполнено `initialQuery`.
  - «Похоже»: `suggest(query)` с debounce 200 мс, пока запрос не пуст.
  - «Оставить «<запрос>»», если запрос не пуст: `resolve(query)` → `onPick(query.trim(), resolution.groupKey)` (для `GROUP` тоже оставляем текст пользователя, ключ — группа).
  - «Все»: `CategoryGroups.filter(query)`.
  - IME-действие «Готово» = «Оставить».
  - Тап по группе: `onPick(group.name, group.key)`.
- [ ] **Step 2: Подключение.** На экране скрина `initialQuery = line.sourceText.ifBlank { line.text }`; в ручном диалоге — `""` для новой строки и `item.category` для правки.
- [ ] **Step 3: Проверка** `./gradlew assembleDebug` → BUILD SUCCESSFUL; `grep -r CategoryPickerDialog app/src` — пусто.
- [ ] **Step 4: Коммит** `Кэшбэк: одно окно выбора категории с поиском`.

---

### Task 8: Банк не выбран по умолчанию, «Дополнить / Заменить»

**Files:**
- Modify: `B/ui/cashback/CashbackForm.kt` (`BankPickerRow`), `B/ui/cashback/CashbackScreen.kt`, `B/ui/cashback/ShotPreviewScreen.kt`, `B/ui/cashback/CashbackViewModel.kt`, `B/domain/repository/CashbackRepository.kt`, `B/data/repository/CashbackRepositoryImpl.kt`
- Create: `B/ui/cashback/SaveDialogs.kt`

**Interfaces:**
- Consumes: `CashbackMerge`.
- Produces:
  - `BankPickerRow(selected: BankBrand?, onSelect: (BankBrand) -> Unit, …)`.
  - `CashbackRepository.listBankMonth(monthKey: String, brand: BankBrand): List<CashbackItem>`.
  - VM: `enum class SaveMode { APPEND, REPLACE }`; `suspend fun decideSave(monthKey: String, brand: BankBrand, lines: List<DraftLine>): CashbackMerge.Decision`; `fun commit(monthKey: String, brand: BankBrand, lines: List<DraftLine>, mode: SaveMode, onDone: () -> Unit)`. `APPEND` → `replaceBankMonth(monthKey, brand, CashbackMerge.append(existing, incoming))`, `REPLACE` → `replaceBankMonth(…, incoming)`. Строки с пустым названием или процентом ≤ 0 отбрасываются, как сейчас.
  - `@Composable fun BankPickDialog(onPick: (BankBrand) -> Unit, onDismiss: () -> Unit)`; `@Composable fun AddOrReplaceDialog(bank: BankBrand, existingCount: Int, onAppend: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit)`.

- [ ] **Step 1: Реализация.**
  - Банк по умолчанию `null` в `ManualCashbackDialog` (новая запись) и на экране скрина (если не определился).
  - «Сохранить» при `null` → `BankPickDialog`, после выбора — продолжение сохранения.
  - Экран скрина: `decideSave`; `SAVE` → `commit(APPEND)`; `ASK` → `AddOrReplaceDialog`.
  - Тексты диалога: заголовок «У банка уже есть кэшбэк», текст «У «{bank.title}» за этот месяц уже {N} категорий.», кнопки «Дополнить» и «Заменить»; `ReplaceBankDialog` удалить.
  - Ручное добавление одной строки — всегда `APPEND`, без диалога: та же категория обновляет процент, а не дублируется.
- [ ] **Step 2: Проверка** `./gradlew testDebugUnitTest assembleDebug` → PASS.
- [ ] **Step 3: Коммит** `Кэшбэк: банк не выбран по умолчанию, «Дополнить / Заменить»`.

---

### Task 9: Скрин: одно распознавание и удаление после сохранения

**Files:**
- Modify: `B/domain/repository/CashbackShotRepository.kt`, `B/data/repository/CashbackShotRepositoryImpl.kt`, `B/data/local/CashbackShotDao.kt`, `B/ui/cashback/CashbackViewModel.kt`, `B/ui/cashback/ShotPreviewScreen.kt`

**Interfaces:**
- Produces: `CashbackShotRepository.observe(id: String): Flow<CashbackShot?>`; VM `fun observeShot(id: String): Flow<CashbackShot?>`.

- [ ] **Step 1: Реализация.**
  - `addShot` сохраняет `SavedImage.displayName` в `CashbackShot.fileName`, распознаёт один раз, пишет `lines`/`brand`/`status`.
  - `ShotPreviewScreen` подписан на `observeShot`: пока `PROCESSING` — индикатор; при первом статусе ≠ `PROCESSING` строки один раз копируются в редактируемое состояние; дальнейшие эмиссии их не перетирают.
  - `loadShotDraft` удалить.
  - После `commit` вызывается `shotRepo.delete(shotId)` (удаляет строку и файл), затем возврат назад.
  - Скрин удалили, пока экран открыт (`null`) → назад.
- [ ] **Step 2: Проверка** `./gradlew assembleDebug` → BUILD SUCCESSFUL; `grep -rn "loadShotDraft\|recognizeShot(" app/src/main` — только в `addShot`.
- [ ] **Step 3: Коммит** `Скрин кэшбэка: распознаётся один раз и удаляется после сохранения`.

---

### Task 10: Редактирование строки и банка

**Files:**
- Create: `B/ui/cashback/CashbackLinesEditor.kt`, `B/ui/cashback/BankCashbackEditScreen.kt`
- Modify: `B/ui/cashback/CashbackScreen.kt` (`BankCashbackPanel`, `ManualCashbackDialog`), `B/ui/cashback/ShotPreviewScreen.kt`, `B/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `CategorySearchSheet`, `vm.commit`.
- Produces:
  - `@Composable fun CashbackLinesEditor(lines: SnapshotStateList<DraftLine>, vm: CashbackViewModel)` — строки (иконка группы, название, процент, крестик) и «Добавить строку». Новая строка сразу открывает `CategorySearchSheet` с пустым запросом, после выбора фокус на поле процента; закрытие окна без выбора удаляет пустую строку.
  - Маршрут `Routes.CASHBACK_BANK_EDIT = "cashback/bank/{monthKey}/{brandKey}"`; `@Composable fun BankCashbackEditScreen(vm: CashbackViewModel, monthKey: String, brand: BankBrand, onBack: () -> Unit)` — заголовок «{bank.title} · {месяц}», `CashbackLinesEditor`, «Сохранить» → `commit(REPLACE)`.

- [ ] **Step 1: Реализация.**
  - `ShotPreviewScreen` переводится на `CashbackLinesEditor`.
  - `BankCashbackPanel`: крестики со строк убрать; в шапке `IconButton` с карандашом (`Icons.Filled.Edit`, `contentDescription = "Изменить кэшбэк банка"`, без текста) → маршрут.
  - `ManualCashbackDialog` при правке получает «Удалить» (цвет `Slitok.Coral`) → `vm.delete(id)`.
- [ ] **Step 2: Проверка** `./gradlew testDebugUnitTest assembleDebug` → PASS.
- [ ] **Step 3: Коммит** `Кэшбэк: правка строки и банка целиком`.

---

### Task 11: Итоговая проверка и выкладка

- [ ] **Step 1:** `./gradlew testDebugUnitTest assembleDebug` → PASS, BUILD SUCCESSFUL.
- [ ] **Step 2:** `git push origin main`; дождаться зелёного `build.yml` (`gh run watch`), артефакт `bank-aggregator-debug` на месте.
- [ ] **Step 3: Ручной чек-лист для Максима на Samsung** (передать текстом):
  1. Скрин из Ozon, Т-Банка или Яндекс Пэй → банк выбран сам. Это проверка того, что Photo Picker отдаёт настоящее имя файла.
  2. Скрин Сбера → банк по тексту или пустой; «Сохранить» спрашивает банк.
  3. Открыть скрин сразу после загрузки → индикатор, затем строки; повторно не распознаётся.
  4. «Медицинские услуги» → «Здоровье»; «Связь, интернет и ТВ» → «Связь и интернет»; бренд остаётся как на скрине.
  5. Второй скрин того же банка с другими категориями → «Дополнить / Заменить»; после сохранения скрин исчез из ленты.
  6. Ручной ввод: «медиц» → сверху «Здоровье»; «Готово» сохраняет свой текст.
  7. Карандаш у банка → правка всех строк; тап по строке → диалог с «Удалить».
