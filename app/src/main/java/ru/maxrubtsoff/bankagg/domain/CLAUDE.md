# Правила слоя `domain`

Загружается, когда работаешь в `domain/**`. Общая карта — в корневом `CLAUDE.md`.

Домен — чистый Kotlin, ядро архитектуры. Зависимости направлены внутрь: `ui → domain ← data`. Домен **ни от чего не зависит** и тестируется на JVM.

## Чистота
- В `domain/*` **нет** импортов `android.*` / `androidx.*` / Compose. (Ловится хуком `guard.ps1` и ревью.)
- Любую логику, которая **считает, парсит или решает**, выноси в чистые функции/объекты домена и покрывай тестами (`app/src/test/...`). UI только отображает результат, своих условий `features.has(...)` на экранах — по минимуму.
- Пример уже под тестом: счётчик банков (`BankBrand`/`CbrBanks`), `CapitalCalculator`, `DepositMath`, `FxPortfolio`, `CashbackSpotlight`, `Features` (что показывать), парсеры. Так и держим.

## Деньги
- Только `domain/model/Money` (минорные единицы, `ofMajor`/`ofMinor`). Никаких `Double`/`Float` для сумм ни в хранении, ни в расчётах.

## Парсеры (консервативность)
- `NotificationParser`, `DepositParser`, `CashbackParser`: не уверен — возвращай `null` (в журнал «не разобрано», баланс не трогаем). Лучше пропустить, чем ошибиться.
- Вид операции (`TransactionKind`) и направление (`OpDirection`) разделены: знак суммы — по направлению, не по виду.

## Пакеты домена
- `model` — модели и чистая логика (деньги, валюты, банки, вклады, кэшбэк, подписки, парсеры, `Features`, `CapitalCalculator`).
- `parser` — `NotificationParser` (текст уведомления → `ParsedOp`).
- `repository` — **только интерфейсы** (их реализует `data`).
- `usecase` — оркестрация: `RecordNotification`, `GetCapital`, `RefreshRates`, `RecognizeCashbackShot`, `RecognizeDepositShot`, `RepairFromJournal`, `CheckPin`, `ClassifyScreenshot`, `MyBanksScope`, `NewBankAnswers`.

## Порядок добавления (слои)
Домен идёт первым: модель → (при оркестрации) use-case → интерфейс репозитория. Реализацию, Room и UI делают следующие слои — см. корневой `CLAUDE.md`, раздел «Как добавить фичу».
