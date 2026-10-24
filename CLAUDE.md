# CLAUDE.md — руководство по доработке проекта

Инструкция для ИИ-ассистента (и людей), как устроен проект и как его развивать, не ломая архитектуру и стиль. Читай целиком перед первой задачей в репозитории.

---

## 1. Что это

**Itogo** (раньше «Агрегатор счетов») — личное Android-приложение. Собирает операции и балансы по счетам, читая **push-уведомления банков** на устройстве, и показывает общий капитал из четырёх частей (счета, вклады, валюта, наличные) и кэшбэк.

Ключевые принципы продукта:
- **Данные локально.** Никакого сервера. Данные живут в Room на телефоне. В сеть приложение ходит **только за публичными курсами ЦБ РФ** (`data/rates/CbrRatesSource`, `HttpURLConnection`), без данных пользователя. (Серверный код в `server/` — опциональный артефакт на будущее, в сборку приложения не входит.)
- **Приватность.** Только уведомления (не SMS). Конкретные пакеты банков объявлены в `<queries>` — без `QUERY_ALL_PACKAGES`.
- **Офлайн и бесплатно.** Любые зависимости должны работать без интернета и без платных сервисов. Без сети приложение работает полностью — только курс не свежий.

Пользователь: Максим, Казань. Весь UI — на русском.

---

## 2. Технологии и инструменты

| Что | Чем |
|-----|-----|
| Язык | Kotlin **2.0.21** |
| UI | Jetpack Compose (BOM **2024.10.01**), Material 3 |
| Compose-компилятор | плагин `org.jetbrains.kotlin.plugin.compose` (не kotlinCompilerExtensionVersion) |
| Навигация | `navigation-compose` 2.8.x (predictive back) |
| Хранение | Room 2.6.1 + **KSP** (не kapt) |
| Асинхронность | Coroutines + Flow |
| DI | **Ручной** контейнер `di/AppContainer` (без Hilt/Dagger) |
| Сборка | Gradle (Kotlin DSL), AGP 8.5.2, `./gradlew assembleRelease` (то, что ставится), `assembleDebug` для отладки |
| Тесты | JUnit4 (JVM, `app/src/test`), гоняются в CI |
| Шрифты | зашиты в `res/font` (Unbounded, Golos Text) — вариативные, offline |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |

APK и тесты собираются в **GitHub Actions** (`.github/workflows/build.yml`) при пуше в `main` и в ветки разработки `dev*`. Ставится **release**-сборка (не отладочная — Compose в ней в разы быстрее, применяются baseline-профили библиотек), артефакт — `bank-aggregator`. Release подписан ключом из секретов CI (`SIGNING_*`, см. `docs/publishing.md`), а пока их нет — **фиксированным** `app/debug.keystore` (в репозитории), чтобы обновление ставилось поверх. Debug ставится рядом: `applicationIdSuffix = ".debug"`. `versionCode` = номер сборки CI. В release включён **R8** (правила — `app/proguard-rules.pro`) и свой Baseline Profile (`app/src/main/baseline-prof.txt`). Все зависимости сверяются по контрольным суммам (`gradle/verification-metadata.xml`): новую зависимость добавляй вместе с обновлением файла (`./gradlew --write-verification-metadata sha256 testDebugUnitTest assembleRelease`).

---

## 3. Архитектура (DDD, три слоя)

Зависимости направлены **внутрь**: `ui → domain ← data`. Домен ни от чего не зависит (чистый Kotlin, тестируемый на JVM). UI и data реализуют интерфейсы домена.

```
┌─────────────────────────── ui (Compose) ───────────────────────────┐
│ MainActivity (NavHost, нижняя панель, переходы)                     │
│ overview/ capital/ cashback/ deposits/ settings/ log/ widget/       │
│ theme/ common/                                                      │
│ каждый экран: <Name>Screen + <Name>ViewModel                        │
│ ViewModel ── вызывает ──▶ use-case или repository (интерфейс домена) │
└───────────────┬─────────────────────────────────────────────────────┘
                │ создаёт VM через
         ui/common/ViewModelFactory ──читает──▶ di/AppContainer
                                                     │ держит синглтоны
                ┌────────────────────────────────────┴──────────────┐
                ▼                                                    ▼
┌──────────────── domain (чистый Kotlin) ───────┐   ┌──────── data (Android) ────────┐
│ model/     Money, Currencies, BankBrand,       │   │ local/   Room: *Entity, *Dao,  │
│            Account, Deposit*, Fx*, Capital*,   │◀──│          AppDatabase, *Mappers │
│            Cashback*, CashEdit                 │   │ repository/  *RepositoryImpl    │
│ parser/    NotificationParser (→ ParsedOp)     │   │ settings/    SettingsStore      │
│ repository/ *Repository, Today, RatesSource    │◀──│ backup/      BackupManager      │
│            (ИНТЕРФЕЙСЫ)                         │   │ rates/   CbrRatesSource, CbrXml │
│ usecase/   RecordNotification, GetCapital,     │   │ time/    SystemToday            │
│            RefreshRates, Recognize*Shot        │   │ (реализуют интерфейсы домена)   │
└────────────────────────────────────────────────┘   └─────────────────────────────────┘

service/BankNotificationListener ──(через App.container)──▶ RecordNotification use-case
util/   InstalledApps, Perms
```

### Граф потоков данных (главные сценарии)

**Захват операции из уведомления:**
```
BankNotificationListener.onNotificationPosted
  → App.container.recordNotification(pkg,title,text,postedAt)          [use-case RecordNotification]
      → NotificationParser.parse(title,text) : ParsedOp?
      → EventRepository.record(CapturedEvent)      (журнал, дедуп по hash)
      → LedgerRepository.upsertAccountBalance(...) (баланс из «Доступно…»)
      → LedgerRepository.addTransaction(Transaction)
  → Room (accounts/transactions/events)
  → Flow → AccountsViewModel.overview → OverviewScreen/AccountsScreen (реактивно)
```

**Кэшбэк:**
```
CashbackScreen / CashbackCard / CashbackWidget ─ CashbackViewModel ─ CashbackRepository ─ CashbackDao (Room)
домен: CashbackMonth (ключи месяцев), CashbackSummary (по категориям), CashbackSpotlight (плашка и виджет)
```

**Капитал:**
```
OverviewScreen / CapitalScreen ─ CapitalViewModel ─ GetCapital (use-case)
  ← LedgerRepository, DepositRepository, FxRepository, RatesRepository, CashRepository,
    SettingsRepository (части в итоге), Today
  → CapitalCalculator.compute(...) : CapitalSnapshot  (домен, под тестами)
Курсы: MainActivity.onResume → RefreshRates.ifNeeded → CbrRatesSource (единственный сетевой вызов)
```

---

## 4. Карта пакетов

- `domain/model` — доменные модели и чистая логика. **Money в минорных единицах (копейки)**, `Currencies` — справочник валют ЦБ, `BankBrand` — банк: известные (с пакетами приложений), из справочника ЦБ (`CbrBanks`, `assets/banks/cbr.tsv`) и свои пользователя (`BankDirectory`); встроенных банков нет — у каждого свой список (`BankRepository`), `DepositMath`, `FxPortfolio` (средняя цена), `CapitalCalculator`, `CashbackSpotlight` — логика под тесты.
- `domain/parser` — `NotificationParser`: текст уведомления → `ParsedOp` (сумма со знаком, валюта, тип, баланс, контрагент). Консервативен: не уверен — `null`.
- `domain/repository` — **только интерфейсы**.
- `domain/usecase` — оркестрация (`RecordNotification`, `GetOverview`).
- `data/local` — Room: `*Entity`, `*Dao`, `AppDatabase`, `*Mappers` (Entity ↔ domain). Версия БД растёт при изменении схемы; с версии 10 каждая смена схемы идёт с миграцией (`Migration`), чтобы данные пользователя не стирались. `fallbackToDestructiveMigration()` остаётся только для старых схем.
- `data/repository` — реализации интерфейсов домена.
- `data/settings` — `SettingsStore` (выбранные банки, части капитала, последний раздел, последняя валюта, отметки обновления курсов).
- `data/rates` — `CbrRatesSource` (HTTP к ЦБ) и `CbrXml` (чистый разбор ответа, под тестом на образце из `app/src/test/resources/cbr`).
- `data/time` — `SystemToday`: живая дата, тикает в полночь.
- `data/backup` — `BackupManager` (экспорт/импорт JSON в «Загрузки/BankAggregator» без диалога пути).
- `di` — `AppContainer` (все синглтоны и use-cases), создаётся в `App`.
- `service` — `BankNotificationListener` (NotificationListenerService).
- `ui/theme` — дизайн-система «Слиток» (`Slitok` палитра, `Fonts`, `Type`, `Theme`).
- `ui/common` — переиспользуемое: `Panel`, `heroGlow`, `BankIcon`, `ViewModelFactory`.
- `ui/<feature>` — экран + ViewModel на фичу: `overview` (Обзор), `capital` (вкладка «Капитал»: счета, валюта, наличные, шторки), `deposits` (раздел «Вклады» и его экраны), `cashback`, `settings`, `log`, `widget`.
- Нижняя панель — ровно четыре вкладки: Обзор · Капитал · Платежи · Настройки. «Платежи» — «Кэшбэк · Подписки» (`ui/subscriptions`).
- `benchmark` — модуль замеров (Macrobenchmark) и генератор Baseline Profile; гоняется на эмуляторе/телефоне, не в CI (`docs/analysis/performance.md`).
- `util` — `InstalledApps` (иконки/наличие банков через `<queries>`), `Perms`.

---

## 5. Как добавить фичу (обязательный порядок)

Пример: новая сущность с экраном. Двигайся **снаружи домена внутрь, затем наружу в UI**:

1. **Домен:** модель в `domain/model`; при наличии логики — чистые функции/объекты (тестируемые).
2. **Интерфейс репозитория** в `domain/repository`.
3. **Use-case** в `domain/usecase`, если есть оркестрация (иначе ViewModel зовёт репозиторий напрямую).
4. **Room:** `*Entity` + `*Dao` + `*Mappers` в `data/local`; добавь entity и `abstract fun dao()` в `AppDatabase` и **подними `version`**.
5. **Реализация репозитория** в `data/repository` (Entity ↔ domain через мапперы).
6. **DI:** добавь в `AppContainer` (`by lazy`).
7. **ViewModel** в `ui/<feature>`: Flow из репозитория → `StateFlow` через `stateIn`.
8. **Factory:** ветка в `ui/common/ViewModelFactory`.
9. **Screen** (Compose) + маршрут в `MainActivity` (`Routes`, `composable(...)`, навигация).
10. **Тесты** на доменную логику (минимум — то, что считает/парсит/решает).

Не перепрыгивай слои: UI не трогает Room/Entity напрямую, домен не знает про Android.

---

## 6. Стандарты и правила

**Домен чист.** В `domain/*` нет импортов `android.*`/Compose. Любую считающую/парсящую/решающую логику выноси в чистые функции домена и покрывай тестами — UI только отображает. (Счётчик банков уже ловится тестом — так и держим.)

**Деньги.** Только `Money` (минорные единицы, `ofMajor/ofMinor`), никаких `Double`/`Float` для сумм в хранении и расчётах.

**Flow реактивно.** Экраны читают `StateFlow` из репозиториев; никаких ручных «обновить». Для `StateFlow<T?>` с начальным `null` используй `stateIn<T?>(...)`.

**Строки UI — на русском.** Пользовательские тексты по-русски, в тоне «Слитка» (коротко, по делу, без канцелярита).

**Дизайн-система «Слиток» — [`docs/design-system/README.md`](docs/design-system/README.md).** Перед любой работой с интерфейсом прочитай этот файл — он эталон (утверждён в цикле 7, F-31). Правила:
- Экран собирается **только** из компонентов `ui/common` и токенов `ui/theme` (`Slitok`, `AppTypography`, шкала сумм `Amounts`). Суммы — только через `MoneyText`/`AmountText`.
- Нужного компонента нет — сначала добавь его в дизайн-систему (`ui/common` + раздел в README + пример в `docs/design-system/showcase.html` и в «Витрине» `ui/showcase/ShowcaseScreen.kt`), потом используй. Новый компонент или изменение вида — только через макет и согласование с Максимом.
- Никаких своих кнопок, полей, диалогов, переключателей, меток, `Card`, hex-цветов и ручного `fontFamily = Unbounded` на экранах — это ловит `DesignSystemGuardTest`; не ослабляй его, исключение — только с причиной в самом тесте.
- Меняешь компонент — в том же коммите обнови README, витрину и её пример.
- Тёмная тема одна (без light/dynamic) — не возвращай `dynamicColor`.

**Room.** Меняешь схему — поднимаешь `version` и пишешь `Migration` (данные не стираем). Используем **KSP**, не kapt.

**Приватность/разрешения.** Известный банк с приложением → пакеты в `<queries>` манифеста и в `BankBrand.known`; остальные банки пользователь добавляет сам, новые приложения находятся по уведомлениям (`NewBanks`). Не добавляй широких разрешений; SMS не читаем.

**DI вручную.** Зависимости — через `AppContainer`, конструкторная инъекция. Не тащи Hilt.

**Стабильная подпись.** Не трогай `signingConfigs`/`app/debug.keystore` — иначе у пользователя обновление не встанет поверх. Переход на ключ публикации — только по `docs/publishing.md` и решению Максима.

**UX и проактивность.** При любой доработке предлагай пользователю самый удобный для него вариант дизайна, а не самый простой в реализации. Будь проактивен: замечаешь неудобство, лишний шаг или баг рядом с задачей — скажи и предложи решение (с макетом, если это экран). Упрощай интерфейс везде, где можно: меньше экранов, полей, диалогов и пояснений; то, что приложение может решить само, оно решает само, оставляя пользователю одно касание на исправление. При этом интерфейс должен оставаться удобным, современным и информативным: человек всегда понимает, что произошло и что он может сделать.

---

## 7. Сборка, тесты, CI

- Локально: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`.
- CI (`.github/workflows/build.yml`): ставит JDK 17, принимает лицензии SDK, **гоняет юнит-тесты**, собирает release APK, грузит артефакт `bank-aggregator`. Падение теста/компиляции роняет сборку.
- Тесты домена — в `app/src/test/java/...` (JVM, без эмулятора). Для Compose UI-тестов подключены `androidTest`-зависимости (нужен эмулятор/устройство).
- **Работа идёт прямо в `main`: коммить и пушь в `main`, без PR и без feature-веток** — push в `main` сразу запускает CI (`build.yml`) и сборку APK. PR заводи только если об этом попросят явно.
- Коммиты подписывай по правилам сессии (атрибуция в конце сообщения).
- **После пуша дождись зелёной сборки.** Работа не считается сделанной, пока `build.yml` на этом коммите не прошёл. Красная сборка: прочитай лог (`gh run view --log-failed`), исправь, запушь и снова жди — повторяй, пока не станет зелёной. До пуша локально прогоняй `./gradlew testDebugUnitTest assembleDebug` и так же циклично чини ошибки. Не отключай и не ослабляй тесты, чтобы сборка прошла.

---

## 8. Дорожная карта

- `docs/TZ.md` — ТЗ текущего цикла (ветка `dev-N`): только то, что делается в этом цикле, сводка пунктов со статусами. Прошлые циклы — в `docs/archive/TZ-N.md`.
- `docs/ROADMAP.md` — все идеи (`I-n`) и их статусы, продуктовый анализ, волны к публикации. Новая идея, не взятая в цикл, пишется туда; взятая — получает пункт в ТЗ, а в ROADMAP статус «в цикле N (пункт)»; по итогам цикла статус меняется на «сделано».
- Документ проекта `claude/status.md` (claude.ai) — краткий статус.

---

## 9. Чего не делать

- Не добавлять сервер и сетевые вызовы, кроме курсов ЦБ. Никаких данных пользователя наружу.
- Не возвращать чтение SMS и `QUERY_ALL_PACKAGES`.
- Не класть бизнес-логику в Compose-функции — она в домене, под тестами.
- Не ломать слои DDD и не хардкодить цвета/шрифты мимо «Слитка».
- Не менять подпись/keystore.
- Не открывать PR и не плодить feature-ветки под свои изменения — пушить напрямую в `main` (CI собирает по push в `main`).
