# CLAUDE.md — карта проекта Itogo

Короткая карта для ИИ-ассистента и людей: что это, где что лежит, как развивать, не ломая архитектуру. Подробные правила живут рядом с кодом (вложенные `CLAUDE.md`) и в скиллах — они подгружаются сами, когда нужны.

---

## 1. Что это

**Itogo** (раньше «Агрегатор счетов») — личное Android-приложение. Собирает операции и балансы по счетам, читая **push-уведомления банков** на устройстве, и показывает общий капитал (счета, вклады, валюта, наличные) и кэшбэк. Пользователь — Максим, Казань; весь UI на русском.

Принципы продукта:
- **Данные локально.** Никакого сервера. Данные — в Room на телефоне, БД зашифрована (SQLCipher, ключ в Android Keystore). В сеть приложение ходит **только за публичными курсами ЦБ РФ** (`data/rates`, `HttpURLConnection`), без данных пользователя. (`server/` — опциональный артефакт на будущее, в сборку не входит.)
- **Приватность.** Только уведомления (не SMS). Пакеты банков объявлены в `<queries>` — без `QUERY_ALL_PACKAGES`. Вход — пин/отпечаток (`ui/lock`). Копия — шифрованная, с паролем.
- **Офлайн и бесплатно.** Все зависимости работают без интернета и без платных сервисов, включая распознавание скринов (OCR Tesseract + ONNX-эмбеддер, офлайн). Без сети приложение работает полностью — только курс не свежий.

## 2. Технологии

| Что | Чем |
|-----|-----|
| Язык | Kotlin **2.0.21** |
| UI | Jetpack Compose (BOM **2024.10.01**), Material 3; плагин `org.jetbrains.kotlin.plugin.compose` |
| Навигация | `navigation-compose` 2.8.x (predictive back) |
| Хранение | Room 2.6.1 + **KSP** (не kapt), SQLCipher (`net.zetetic:sqlcipher-android`) |
| Асинхронность | Coroutines + Flow |
| DI | **ручной** контейнер `di/AppContainer` (без Hilt/Dagger) |
| Картинки | Coil (`coil-compose`) |
| Виджет | Glance (`glance-appwidget`) |
| Напоминания | WorkManager (`work-runtime-ktx`), офлайн |
| Распознавание | Tesseract4Android (OCR, кириллица) + ONNX Runtime (эмбеддер, `assets/embedder`) |
| Вход | `androidx.biometric` (отпечаток), пин |
| Сборка | Gradle (Kotlin DSL), AGP 8.5.2; `./gradlew assembleRelease` (ставится), `assembleDebug` для отладки |
| Тесты | JUnit4 (JVM, `app/src/test`), гоняются в CI |
| Шрифты | зашиты в `res/font` (Unbounded, Golos Text), вариативные, offline |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |

APK и тесты собираются в **GitHub Actions** (`.github/workflows/build.yml`) при пуше в `main` и `dev*`. Ставится **release** (не отладка — Compose быстрее, применяются baseline-профили), артефакт `bank-aggregator`. Release подписан ключом из секретов CI (`SIGNING_*`, см. `docs/publishing.md`), а пока их нет — **фиксированным** `app/debug.keystore` (в репозитории), чтобы обновление ставилось поверх. Debug ставится рядом (`applicationIdSuffix = ".debug"`). `versionCode` = номер сборки CI. В release включён R8 (`app/proguard-rules.pro`) и Baseline Profile (`app/src/main/baseline-prof.txt`). Зависимости сверяются по контрольным суммам (`gradle/verification-metadata.xml`): новую добавляй вместе с `./gradlew --write-verification-metadata sha256 testDebugUnitTest assembleRelease`.

## 3. Архитектура (DDD, три слоя)

Зависимости направлены **внутрь**: `ui → domain ← data`. Домен — чистый Kotlin, ни от чего не зависит, тестируется на JVM. UI и data реализуют интерфейсы домена.

```
ui (Compose)  →  domain (чистый Kotlin)  ←  data (Android)
  экраны+VM        model/ parser/             local/(Room) repository/
  создаёт VM       repository(интерфейсы)/     rates/ settings/ backup/
  через            usecase/                    time/ ocr/ embed/ icons/
  ViewModelFactory                             notify/ security/
      ↑ di/AppContainer держит все синглгоны и use-cases
service/BankNotificationListener → (через App.container) → RecordNotification
```

Главный сценарий (захват операции): `BankNotificationListener.onNotificationPosted` → `recordNotification` (use-case) → `NotificationParser.parse` → запись в журнал (дедуп), баланс и операция в Room → Flow → ViewModel → экран (реактивно).

Правила каждого слоя — во вложенных файлах, подгружаются автоматически:
- `app/src/main/java/ru/maxrubtsoff/bankagg/domain/CLAUDE.md` — чистота домена, деньги, парсеры.
- `app/src/main/java/ru/maxrubtsoff/bankagg/ui/CLAUDE.md` — дизайн-система «Слиток».
- `app/src/main/java/ru/maxrubtsoff/bankagg/data/local/CLAUDE.md` — Room, миграции, шифрование.

## 4. Карта пакетов

- `domain/model` — доменные модели и чистая логика (`Money`, `Currencies`, `BankBrand`/`CbrBanks`/`BankDirectory`, `Deposit*`, `Fx*`, `Capital*`, `Cashback*`, `Subscription`, `Features`, `CapitalCalculator`, `DepositMath`, `FxPortfolio`, `CashbackSpotlight`, парсеры `DepositParser`/`CashbackParser`).
- `domain/parser` — `NotificationParser` (текст уведомления → `ParsedOp`, консервативно: не уверен — `null`).
- `domain/repository` — **только интерфейсы**.
- `domain/usecase` — оркестрация: `RecordNotification`, `GetCapital`, `RefreshRates`, `RecognizeCashbackShot`, `RecognizeDepositShot`, `RepairFromJournal`, `CheckPin`, `ClassifyScreenshot`, `MyBanksScope`, `NewBankAnswers`.
- `data/local` — Room: `*Entity`, `*Dao`, `AppDatabase`, `*Mappers`, `DatabaseCipher`.
- `data/repository` — реализации интерфейсов домена.
- `data/rates` — `CbrRatesSource` (HTTP к ЦБ) и `CbrXml` (разбор, под тестом).
- `data/settings` — `SettingsStore`. `data/time` — `SystemToday`. `data/backup` — `BackupManager`/`BackupCipher`/`LogoBackup`.
- `data/ocr` — Tesseract OCR. `data/embed` — ONNX-эмбеддер. `data/icons` — `LogoMaker`. `data/notify` — напоминания. `data/security` — ключи (`PhoneKeys`).
- `di` — `AppContainer` (синглтоны, use-cases), создаётся в `App`.
- `service` — `BankNotificationListener` (NotificationListenerService).
- `ui/theme` — «Слиток» (`Slitok`, `AppTypography`, `Motion`). `ui/common` — переиспользуемое (`ScreenHeader`, `MoneyText`, `BankIcon`, `ViewModelFactory`, шторки).
- `ui/<feature>` — экран + ViewModel: `overview`, `capital`, `cashback`, `subscriptions` (вкладка «Платежи»), `deposits`, `settings`, `log`, `onboarding` (слайды, обучение), `lock` (вход), `showcase` (витрина).
- `benchmark` — Macrobenchmark и генератор Baseline Profile (не в CI, см. `docs/analysis/performance.md`).
- `util` — `InstalledApps`, `Perms`.

## 5. Как добавить фичу (порядок слоёв, не перепрыгивать)

1. **Домен:** модель в `domain/model`; логика — чистые функции под тест.
2. **Интерфейс репозитория** в `domain/repository`.
3. **Use-case** в `domain/usecase` — если есть оркестрация.
4. **Room:** `*Entity`+`*Dao`+`*Mappers` в `data/local`; entity и `dao()` в `AppDatabase`, поднять `version` + `Migration`.
5. **Реализация репозитория** в `data/repository`.
6. **DI:** `by lazy` в `AppContainer`.
7. **ViewModel** в `ui/<feature>`: Flow → `StateFlow` через `stateIn`.
8. **Factory:** ветка в `ui/common/ViewModelFactory`.
9. **Screen** + маршрут в `ui/MainActivity` (`Routes`, `composable`, навигация).
10. **Тесты** на доменную логику.

UI не трогает Room/`*Entity` напрямую; домен не знает про Android. Детали правил — во вложенных `CLAUDE.md` (§3).

## 6. Мультиагентная разработка

Полный цикл ведут субагенты (`.claude/agents/`), главная сессия — оркестратор: держит решения, раздаёт задачи, мержит; грязную работу (чтение кода, тесты, логи) отдаёт агентам, чтобы её объём не копился в окне.

| Агент | Модель | Зачем |
|-------|--------|-------|
| `explorer` | haiku | найти код, карта фичи по слоям (только чтение) |
| `architect` | opus | план фичи по DDD (не кодит) |
| `designer` | opus | вид и компоненты по «Слитку» (макет) |
| `coder` | sonnet | реализация по плану + зелёная сборка |
| `tester` | sonnet | доменные тесты (TDD) |
| `reviewer` | sonnet | ревью дифа по правилам (только чтение) |
| `device-check` | sonnet | проверка интерфейса на эмуляторе (adb: скрины, кадры) |

Цикл запускается двумя скиллами: **`cycle-start`** (пишешь «новая задача» — ветка `cycle-N` от `main`, файл `docs/cycles/cycle-N.md`, режим сбора без анализа) и **`cycle-run`** (пишешь «запускай полный цикл» — весь поток ниже). Запреты-инварианты стоят хуком (`.claude/hooks/guard.ps1`), а не только текстом.

`cycle-run` работает **фазами по всем пунктам сразу**, а не гоняет полный конвейер на каждый пункт:
1. **Классификация:** рядовой пункт (баг/доработка в текущем дизайне) едет автономно; дизайн/структурный (редизайн, новая навигация, смена решения, смежного с инвариантом) — только предложение, ждёт согласования.
2. **Анализ** всех пунктов: `explorer` (карта, файлы) → `architect` (планы) → `designer` (варианты для дизайн-пунктов).
3. **Вопросы одним пакетом** (гейт 1) → **макеты одним пакетом** (гейт 2). После гейта 2 — автономно до `main`.
4. **Разработка группами:** пункты с общими файлами — одна группа (коммит на пункт), непересекающиеся — параллельно; `coder` → `tester` → `reviewer`.
5. **CI волнами** (пуш пачкой, не по пункту) → **`device-check`** на эмуляторе → **слияние** (`release`).

**Два уровня правил.** *Инварианты* (слои, деньги в копейках, домен без Android, приватность, подпись, офлайн, тесты, цвет/шрифт токенами) не нарушаются даже при редизайне — это хук. *Текущие решения* (4 вкладки, палитра, набор экранов) — сегодняшний вид, их меняет дизайн/структурный пункт после согласования, обновляя правила (`ui/CLAUDE.md`, `design-system/README.md`) в том же изменении.

## 7. Сборка, тесты, CI

- Локально: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`.
- CI (`build.yml`): JDK 17, лицензии SDK, **юнит-тесты**, release APK, артефакт `bank-aggregator`. Падение теста/компиляции роняет сборку.
- Тесты домена — `app/src/test/java/...` (JVM). Для Compose UI-тестов — `androidTest` (нужен эмулятор).
- **Работа идёт прямо в `main`** (кроме циклов разработки в ветке `dev-N`, см. `docs/TZ.md`): коммить и пушь, без PR и feature-веток, если не попросят иначе. Push запускает CI.
- **После пуша дождись зелёной сборки.** Красная: `gh run view --log-failed`, исправь, запушь, жди — пока не зелёная. До пуша локально `./gradlew testDebugUnitTest assembleDebug`. Тесты не отключать и не ослаблять.

## 8. Дорожная карта

- `docs/cycles/cycle-N.md` — файл текущего цикла (ветка `cycle-N`), пункты со статусами; заводит его `cycle-start`. Прошлые циклы лежат там же — это и есть архив. _(Цикл 8 ещё на старой схеме: ветка `dev-8`, `docs/TZ.md`, архив `docs/archive/TZ-N.md`. Новые циклы — по новой схеме.)_
- `docs/ROADMAP.md` — все идеи (`I-n`) и статусы. Новая идея вне цикла пишется туда.
- `claude/status.md` (claude.ai) — краткий статус.

## 9. Чего не делать

- Не добавлять сервер и сетевые вызовы, кроме курсов ЦБ. Никаких данных пользователя наружу.
- Не возвращать чтение SMS и `QUERY_ALL_PACKAGES`. _(хук)_
- Не класть бизнес-логику в Compose — она в домене, под тестами. _(хук на `import android.*` в домене)_
- Не ломать слои DDD, не хардкодить цвета/шрифты мимо «Слитка». _(хук + `DesignSystemGuardTest`)_
- Не менять подпись/keystore (`signingConfigs`, `app/debug.keystore`) — иначе обновление не встанет поверх. _(хук; переход на ключ публикации — по `docs/publishing.md`)_
- Не открывать PR и не плодить feature-ветки под свои изменения.

## Compact Instructions

При сжатии контекста сохраняй: текущий пункт `docs/TZ.md` и его статус; какие файлы уже изменены и зачем; вывод `./gradlew testDebugUnitTest assembleDebug` (успех/ошибки); решения по макетам и выбранные варианты; что ещё не проверено на устройстве.
