# Карта цикла 20 (пункты B-130, B-131, B-132, U-81, F-62)

Сбор: explorer, 2026-11-17. Статус: найдено частично (см. «Не проверено»).

## Группировка по файлам
- **B-130 + B-131** — общий путь иконок/банков: `domain/model/BankBrand.kt`, `data/icons/LogoMaker.kt`, `ui/common` (BankIcon/BankAccentCache). Делят файлы, одна группа.
- **U-81 + F-62** — общий экран: `ui/history/CapitalHistoryViewModel.kt`, `ui/common/SpendCard.kt`. Одна группа.
- **B-132** — отдельно: `domain/model/CapitalEvents.kt` (+ `HistoryRows.kt`). Пересекается с B-121/F-55 (ручные правки), осторожно.
- U-79 уже сделан; U-81 связан с ним по экрану (`CapitalHistoryScreen`).

## B-130 — свой банк превращается в «Банк» (фиолетовый «?»)
| Место | Что там |
|---|---|
| `domain/model/BankBrand.kt:127-129` | `fromKey`: сначала `BankDirectory.find(key)`, потом `known`, иначе фолбэк `BankBrand(key, "Банк", …, letters="?")` — это и есть «безымянный Банк» |
| `domain/model/BankBrand.kt:156-163` | `BankDirectory.replace(list, activeKeys)` — единственное место, где пользовательские банки попадают в резолв; до вызова `find` пуст |
| `domain/model/BankBrand.kt:166-169` | `recognizableOf`/`find`: пользовательский банк резолвится по ключу, только если directory наполнен |
| `domain/model/BankBrand.kt:122-123` | `fromPackage` использует `BankDirectory.extra()` — тот же источник |
| `ui/settings/BankEditorScreen.kt:42` | Редактор читает банк напрямую из хранилища → поэтому в редакторе всё видно (объяснение симптома) |
| `ui/common/BankPickSheet.kt:27`, `ui/history/OperationSheet.kt:201`, `ui/deposits/DepositForm.kt:170`, `ui/cashback/BankCashbackEditScreen.kt:114` | «Банк» как подпись/заголовок — проверить, не фолбэк ли это |

Гипотеза для разбора (не проверено): directory не наполняется после перезапуска/пересоздания (нет вызова `replace` с пользовательскими банками на старте или при сохранении) → `fromKey` падает в фолбэк. Проверить: `grep "BankDirectory.replace("` в `data/` и `App`/`AppContainer`. Тест: `app/src/test` на `BankBrand`/`BankDirectory`.

## B-131 — загруженная картинка чернеет
| Место | Что там |
|---|---|
| `data/icons/LogoMaker.kt:24-55` | Путь: `make(uri, bankKey)` → декод с `inSampleSize` (:31-38) → квадрат по центру (:40) → `createScaledBitmap(..., true).copy(ARGB_8888)` (:41) → `LogoBackground.detect/clear` вырезает фон (:45-47) → `PNG compress` (:53) |
| `LogoMaker.kt:51-52` | Имя файла `bankKey_<millis>.png` (новое имя на каждый раз) |
| `domain/model/LogoBackground` (найти файл через grep `object LogoBackground`) | `detect`/`clear` — здесь очищается альфа/цвет фона; вероятная причина: прозрачные пиксели с RGB=0 (чёрный) и отрисовка без альфа (нет `Modifier`-фона/`Bitmap.Config` при показе) |
| `data/icons/BankIconCache.kt:96` | Кэш иконок банков: `bmp.compress(PNG)` — второй путь записи PNG |
| `data/notify/DecorBitmap.kt:23` | `createBitmap(SIZE, SIZE, ARGB_8888)` — генерация иконки уведомлений; проверить фон |
| `data/backup/LogoBackup.kt:7-25` | Логотипы в бэкапе (PNG base64) — путь восстановления |
| `ui/common/BankAccentCache.kt:32` | `resolve(brand, logo)` — читает картинку для акцентного цвета |
| `ui/widget/FigureBitmap.kt:41` | `ARGB_8888` для виджета — не связан напрямую, для сравнения |
| `ui/common/` (BankIcon) | Отображение: найти `BankIcon` (Coil/`AsyncImage`) — здесь проверить подложку |
| Подписки | Путь подписки — отдельный выбор логотипа; искать в `ui/subscriptions` (`grep "LogoMaker"`) |

Не проверено: точная строка, где альфа становится чёрной (кандидаты: `LogoBackground.clear`, отрисовка BankIcon без подложки).

## B-132 — ложная «Правка остатка · вручную» в «Валюте»
| Место | Что там |
|---|---|
| `domain/model/CapitalEvents.kt:320-328` | `revalued` = Δ валюты − сделки − проводки − открытия. Любая неучтённая разница попадает сюда |
| `CapitalEvents.kt:329-336` | Если `reason.manualMinor != 0` → `manualEvent` → «Правка остатка» (ref `HistoryRef.Manual`) |
| `CapitalEvents.kt:344` | «Переоценка валюты» = `revalued - reason.manualMinor` (сумма по курсам) |
| `CapitalEvents.kt:405-437` `reasonOf` | Курсовые «legs» только по `fxOps` (сделкам). **Если сделок валюты нет, legs пуст → весь `rest` уходит в manual.** |
| `CapitalEvents.kt:435` | Порог: `|rest| < max(100, |revalued|/100)` → 0, иначе manual. Для +24,62 ₽ (2462 коп.) порог = 100 коп. → проходит как «вручную». Это объясняет симптом +24,62 ₽ |
| `domain/model/CapitalEvents.kt:370-381` `manualEvent` | Источник ручного: `ACCOUNT` если `ManualBalance.touchedOn` нашёл изменение счёта в день, иначе `CURRENCY_POSITION` («Валюта») |
| `domain/model/HistoryRows.kt:132-159` | Второй путь создания «Остаток изменили вручную» по сумме `manualMinor` дня (сумма ручных → строка с `manual != 0`) |
| `domain/usecase/RepairFromJournal.kt:112` | Комментарий про «ручную цифру» — проверить, не пишет ли сверка остатки в БД |
| `ui/history/CapitalHistoryScreen.kt:473` | Тег «вручную» ставится, если `ref is HistoryRef.Manual` — UI не решает сам |
| `ui/common/HistoryLine.kt:55` | Рендер плашки «вручную» |

Вывод для разбора: ложные записи — следствие порога/округления в `reasonOf` (CapitalEvents:435) при отсутствии legs, а не отдельный путь записи. Тесты: искать `CapitalEventsTest` / `HistoryRowsTest` в `app/src/test`.

## U-81 — подвисание при тогле «Переводы людям»
| Место | Что там |
|---|---|
| `ui/history/CapitalHistoryViewModel.kt:194-195` | `setIncludeTransfers` → `settings.setSpendWithTransfers` (запись в настройки) |
| `CapitalHistoryViewModel.kt:213-221` | `base = combine(inputs, today, _includeTransfers)`: **при смене тоггла заново запускается `CapitalEvents.build(...)` (:216) и `Spending.month` по всем 12 месяцам (:218)** — полный пересчёт, хотя от тоггла зависит только спенд |
| `CapitalHistoryViewModel.kt:226-258` | `state` — весь `HistoryUi` пересобирается при любом изменении `base` |
| `CapitalHistoryViewModel.kt:213` | Вычисления идут в `combine` на потоке ViewModel (`viewModelScope`/Default не задан) — UI-поток не вынесен |
| `domain/model/Spending.kt:43-68` `month(…, includeTransfers)` | Тоггл меняет только `totalMinor`/`count` (:63, :68); переводы считаются всегда (:55-57) |
| `domain/model/CapitalEvents.kt:215-216` | `OwnTransfers.ids` и `build` — не зависят от тоггла, но пересчитываются |

Направление для разбора (без правок): развести `_includeTransfers` из `base` (CapitalHistoryViewModel:213) — пересчитывать только `spend`, а `CapitalEvents.build` оставить от `inputs`; вынести тяжёлое с основного потока (Dispatchers.Default). Тесты: `Spending` (`app/src/test`), `CapitalEvents`.

## F-62 — детализация «Переводов людям»
| Место | Что там |
|---|---|
| `ui/common/SpendCard.kt:136` | Строка-переключатель «Переводы людям» (`includeTransfers, onIncludeTransfers`) — сейчас не кликабельна как строка |
| `ui/common/SpendCard.kt:206` | Строка с `title = "Переводы людям", valueMinor = m.transfersMinor` — здесь добавить переход в детализацию |
| `ui/history/CapitalHistoryViewModel.kt:264` | `spendUi`: `bank` — сужение списка по банку (F-58), уже есть механизм фильтра по банку |
| `CapitalHistoryViewModel.kt:243-244` | Фильтр событий `shown` по `bank` (переиспользуемый паттерн «детали списания» — по `source`/`parts`) |
| `domain/model/Spending.kt:55-57` | `transfers` (список `TRANSFER`) уже вычислен — для списка переводов нужны эти операции, `transfersCount` (:21) |
| `domain/model/RuPlural.kt:50` | `transfers(n)` — подпись для счётчика |
| Детализация списаний | Найти компонент: `ui/history/OperationSheet.kt` (шторка операции, F-51) и `ui/history/BalanceFlow.kt`; список списаний — `ui/common/SpendCard.kt` (строки категорий → `byBank`/месяц). Точную точку входа списаний (строка с `onClick`) проверить в `SpendCard.kt` около :206 соседних строк |
| `domain/model/HistoryRows.kt:26` | Комментарий: «Ruble spendings and transfers are only a sum; a group with a currency child opens» — переводы сейчас только сумма, без детализации |

Вывод: детализация списаний живёт в той же карточке (`SpendCard`) и шторке операции (`OperationSheet`); переиспользовать можно фильтр по переводам (`Spending.month`, `TRANSFER`) и шторку операции. Дизайн-вопроса нет (повтор паттерна) — подтвердить в анализе.

## Смежные тесты (найти в app/src/test)
- `BankBrand`/`BankDirectory` — B-130
- `LogoBackground` — B-131
- `CapitalEvents` / `HistoryRows` / `ManualBalance` — B-132
- `Spending`, `CapitalHistoryViewModel` (если есть) — U-81, F-62
- `DesignSystemGuardTest` — любые правки в ui

## Не проверено
- Точная строка, где альфа становится чёрной (B-131), и место вызова `BankDirectory.replace` (B-130).
- Компонент BankIcon (`ui/common`) и `LogoBackground` — не открывались.
- Тесты в `app/src/test` не сканировались (только указаны ожидаемые имена).
