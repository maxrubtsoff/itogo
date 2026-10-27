# Цикл 15 — планы (architect)

Пути от `app/src/main/java/ru/maxrubtsoff/bankagg/` (если не сказано иначе). Строки — по состоянию ветки `cycle-15` на 2026-10-27.

**Группы по общим файлам:** `MainActivity.kt` — U-72, B-115, B-116 (одна группа, коммит на пункт, порядок B-116 → B-115 → U-72); `OverviewScreen.kt` — B-116 и U-73 (U-73 после B-116); `SubscriptionScreens.kt`/`SubscriptionsSection.kt` — только U-73. F-54 — отдельно, параллельно.

---

## U-72

**Цель:** любой внутренний снекбар убирается смахиванием вбок или вниз — сразу; «Вернуть» работает как раньше.

**Решение:** один компонент `SlitokSnackbarHost` в `ui/common` — им пользуются оба хоста (сейчас вид снекбара продублирован, а в «Капитале» вообще штатные цвета M3 мимо «Слитка»). Вбок — `SwipeToDismissBox` (Material3 1.3 из BOM 2024.10.01); вниз — `SwipeToDismissBox` не умеет, поэтому вертикальный `draggable` с порогом. Новый вид не вводится: цвета те же, что сейчас в `MainActivity.kt:691`.

Шаги:
1. **Новый файл `ui/common/Snackbars.kt`:**
   - `@Composable fun SlitokSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier)` → `SnackbarHost(state, modifier) { data -> SlitokSnackbar(data) }`.
   - `@Composable private fun SlitokSnackbar(data: SnackbarData)`:
     - `key(data) { … }` — у каждого нового снекбара свежее состояние жеста.
     - `val box = rememberSwipeToDismissBoxState(confirmValueChange = { if (it != SwipeToDismissBoxValue.Settled) data.dismiss(); true })`.
     - `SwipeToDismissBox(box, backgroundContent = {}, enableDismissFromStartToEnd = true, enableDismissFromEndToStart = true)` { содержимое }.
     - Вниз: `var dy by remember { mutableFloatStateOf(0f) }`; `Modifier.offset { IntOffset(0, dy.roundToInt()) }.draggable(rememberDraggableState { dy = (dy + it).coerceAtLeast(0f) }, Orientation.Vertical, onDragStopped = { if (dy > порог 48.dp в px) data.dismiss() else dy = 0f })`. Вверх не тянется (`coerceAtLeast(0f)`).
     - Внутри — `Snackbar(data, containerColor = Slitok.PanelTop, contentColor = Slitok.Fg, actionColor = Slitok.Brass2)` (как сейчас в `MainActivity.kt:691`).
   - Ни цвета, ни шрифта не добавляется: только токены `Slitok`.
   - Конфликта с жестом скрытия сумм нет: `twoFingerSwipeDown` (`MainActivity.kt:683`) — двумя пальцами, а снекбар тянут одним.
2. **`ui/MainActivity.kt:688-693`:** `snackbarHost = { SlitokSnackbarHost(snackbar, if (showBottomBar) Modifier else Modifier.navigationBarsPadding()) }`; убрать ставшие лишними импорты `Snackbar`/`SnackbarHost`.
3. **`ui/capital/CapitalScreen.kt:191`:** `SlitokSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))`.
4. **`ui/common/Undo.kt`** — логику не трогаем. Сейчас смахивание = `data.dismiss()` → `showSnackbar` возвращает `Dismissed` → `restored=false` → `finish()` (удаление окончательное). Так и должно быть: смахнул — значит согласен.
5. **Дизайн-система:** `docs/design-system/README.md`, таблица компонентов (`:55`) — строка `SlitokSnackbarHost`: «Снекбар приложения; смахивается вбок и вниз (U-72). Сообщения — только через `UndoCenter.offer/say` или этот хост». Витрина `ui/showcase/ShowcaseScreen.kt` — кнопка «Показать снекбар» с локальным `SlitokSnackbarHost` (+ пример в `docs/design-system/showcase.html`, если там есть раздел компонентов).
6. **Тесты:** доменной логики нет. Чтобы правило держалось, в `DesignSystemGuardTest.forbidden` добавить `"SnackbarHost(" to Regex("""(?<![A-Za-z])SnackbarHost\(""")` (экраны берут `SlitokSnackbarHost`; `ui/common` не проверяется, его исключение уже есть).

**Критерии:**
- `./gradlew testDebugUnitTest assembleDebug` зелёные, `DesignSystemGuardTest` тоже, в том числе с новым запретом.
- В `ui/**` вне `ui/common` нет `SnackbarHost(` и `Snackbar(`.
- На устройстве (device-check): удалить скрин → снекбар «Вернуть» → смахнуть вбок: пропал сразу, скрин не вернулся. Ещё раз удалить → «Вернуть» → скрин вернулся. Смахнуть вниз: пропал. В «Капитале» снекбар «Нет связи с ЦБ» в цветах «Слитка» и тоже смахивается.

---

## B-115

**Цель:** из карточки банка строка «Подписка» ведёт прямо в карточку подписки («Назад» возвращает в банк), а после перехода в «Платежи» из банка вкладки нижней панели снова переключают экраны.

**Что нашлось (причина):**
- `MainActivity.kt:963`: из банка «Подписки» открывают *вкладочный* маршрут `cashback?section=1` поверх стека Настройки → Банки → Банк. Нижняя панель видна (`:601`, маршрут — вкладка).
- Тап по «Настройки» → `openTab` (`:614-626`): `popUpTo(start){saveState=true}` сохраняет весь стек [settings, banks, bank-card, cashback] под id настроек, затем `navigate(settings){restoreState=true}` его же восстанавливает. Пользователь снова в «Платежах»: «нажатие не срабатывает, переход не происходит». То же с «Капиталом» и «Кэшбэком» из банка (`:961-962`).
- Иконка в карточке подписки (`SubscriptionScreens.kt:215`) — карандаш, обработчик в порядке. Её не трогаем.
- Вывод сделан по коду, на устройстве не воспроизведён: device-check сначала воспроизводит на старой сборке (Настройки → Банки → банк с подпиской → «Подписка» → тап «Настройки» в панели), потом проверяет фикс.

Шаги:
1. **Домен, `domain/model/BankSummary.kt`:**
   - `:12-20` — поле `val subscriptionIds: List<String> = emptyList()` (порядок как в списке `subs`).
   - `:56` — `subscriptionIds = subs.map { it.id }`.
   - Новое свойство `val onlySubscriptionId: String? get() = subscriptionIds.singleOrNull()` с KDoc: «B-115: одна подписка открывается сразу, несколько — список „Платежей“».
2. **`ui/settings/BankCardScreen.kt`:**
   - `:72-81` — параметр `onOpenSubscription: (String) -> Unit` после `onOpenSubscriptions`; пробросить в `Holdings` (`:126`, `:138-144`).
   - `:155-156` — `open = s.onlySubscriptionId?.let { id -> { onOpenSubscription(id) } } ?: onOpenSubscriptions`.
3. **`ui/MainActivity.kt:963`** — добавить `onOpenSubscription = { nav.navigate(Routes.subscription(it)) }`. Карточка подписки — экран деталей (без панели), «Назад» = `popBackStack` → банк.
4. **`ui/MainActivity.kt:614-626`, `openTab` — «объезд» больше не застревает:**
   ```
   val pattern = topDests.first { it.route == route }.pattern
   // B-115: the tab is already in this stack (Настройки under a bank's «Платежи»): back to its top.
   if (nav.popBackStack(pattern, inclusive = false)) return
   // A tab opened on top of another tab's screens (bank card → «Платежи») is a detour: not kept as a tab's stack.
   val startDest = nav.graph.findStartDestination()
   val detour = nav.previousBackStackEntry?.destination?.let { it.id != startDest.id } == true
   nav.navigate(route) { popUpTo(startDest.id) { saveState = !detour }; launchSingleTop = true; restoreState = true }
   ```
   Ветку `route == startRoute` (`:617-620`) оставить первой как есть. `popBackStack(route: String, inclusive)` в navigation 2.8 ищет по шаблону маршрута — передаём `pattern`, не `route` (у «Платежей» и «Капитала» они различаются, `:260-261`).
   В обычном стеке [start, вкладка] ничего не меняется: `previous` = start → `detour=false`, а тап по текущей вкладке — `popBackStack` на неё же, то есть ничего не делает (как раньше `launchSingleTop`).
5. **Тесты, `app/src/test/.../domain/BankSummaryTest.kt`:**
   - `oneSubscriptionOpensItself` — у банка 1 активная подписка → `onlySubscriptionId == её id`.
   - `severalSubscriptionsOpenTheList` — 2 подписки → `onlySubscriptionId == null`, `subscriptionIds.size == 2`.
   - `pausedSubscriptionIsNotCounted` — активная + неактивная → `onlySubscriptionId` = id активной.

**Критерии:**
- Тесты и сборка зелёные.
- Инвариант: `subscriptionIds.size == subscriptionCount`.
- На устройстве: банк с одной подпиской → «Подписка» → открылась карточка подписки → карандаш открывает форму → «Назад» ×2 → карточка банка. Банк с двумя → «Платежи / Подписки» → тап «Настройки» в панели → корень «Настроек» (не «Платежи»). Из банка «Счета» → «Капитал» → тап «Настройки» → «Настройки».

---

## U-73

**Цель:** все самостоятельные цифры (суммы, проценты, ставки, сроки, счётчики-значения, подписи графиков, виджет) набраны одним шрифтом — Unbounded с цифрами одной ширины (`tnum`) через токены `Amounts.*`. Правило записано в дизайн-системе и проверяется тестом.

### Аудит (что есть сейчас)

| Где | Чем набрано | Нарушение |
|---|---|---|
| Суммы через `MoneyText`/`AmountText` + `Amounts.*` (Deposits*, DepositDetail `:156,213,216`, BankCard `:171`, Capital*, CurrencySection и др.) | Unbounded + tnum | — эталон |
| `ui/cashback/CashbackScreen.kt:447-458` `PercentText` | `titleMedium/titleSmall.copy(fontFamily = ru.maxrubtsoff.bankagg.ui.theme.Unbounded)`, без tnum | ручной `fontFamily` (guard-тест его не ловит: полное имя пакета) |
| `ui/deposits/DepositDetailScreen.kt:257` | `if (MoneyMask.hasMoney(value)) Amounts.Row else bodyLarge` | ставка «18 %», срок «12 мес» в столбце — Golos, суммы рядом — Unbounded |
| `ui/subscriptions/SubscriptionsSection.kt:122` | `MoneyText(…, headlineSmall, Bold)` | Unbounded без tnum, мимо шкалы |
| `SubscriptionsSection.kt:179` | `titleSmall` SemiBold | сумма в Golos |
| `SubscriptionsSection.kt:208, :226` | `bodyMedium` | сумма в Golos |
| `ui/subscriptions/SubscriptionScreens.kt:228` | `headlineSmall` Bold | без tnum, мимо шкалы |
| `SubscriptionScreens.kt:253` | `bodyMedium` SemiBold | сумма в Golos |
| `SubscriptionScreens.kt:268` | `MoneyText(value, bodyLarge)` — строка «ярлык — значение» | значение в Golos |
| `ui/capital/CapitalUi.kt:122` | `AmountText(…, displaySmall)` | без tnum, мимо шкалы |
| `ui/capital/BalanceSheet.kt:90`, `CurrencySection.kt:252`, `overview/OverviewScreen.kt:426`, `history/OperationSheet.kt:133`, `deposits/DepositAddScreen.kt:198` | `body*` | проверить по правилу ниже: самостоятельная цифра → `Amounts`, сумма внутри фразы → текст |
| `ui/cashback/CashbackScreen.kt:429` «+N», `ui/common/BankStack.kt:34` «+N» | `labelSmall/labelMedium` | значок-счётчик в Golos |
| `ui/deposits/DepositMonthsChart.kt:134,145,212` | `label*` | подписи графика — проверить, цифры ли там |
| Графики `ui/common` (`CapitalChart`, `Sparkline`) | — | подписи осей не открывались: coder проверяет `grep -n "Text(" ui/common/CapitalChart.kt ui/common/Sparkline.kt` |
| `ui/widget/CashbackWidget.kt:322-327, 281-286, 379-389` | Glance `TextStyle` без шрифта — системный Roboto | проценты виджета не Unbounded |
| Мёртвые импорты `ui.theme.Unbounded`: `DepositTopUps.kt:35`, `DepositsScreen.kt:109`, `DepositMonthsChart.kt:44`, `IntroSlides.kt:53`, `DepositForm.kt:57`, `DepositDetailScreen.kt:67`, `LockScreen.kt:63`, `FxOperationSheet.kt:63`, `CurrencyDetailScreen.kt:56`, `CashSheet.kt:55` | — | удалить |

Шрифтов два (`ui/theme/Fonts.kt`): Unbounded (заголовки и суммы) и Golos Text (текст). Ручных `fontFamily` на экранах один (`CashbackScreen.kt:451`); остальные расхождения — цифры, набранные текстовыми стилями.

### Правило (решение)
- **Самостоятельная цифра** — значение, стоящее отдельно: сумма, процент, ставка, срок «12 мес», счётчик-значок «+3», подпись оси. Набирается только `Amounts.*`: Unbounded + `tnum`.
- **Цифра внутри фразы** («подорожала на 100 ₽», «Подписки · 3», даты «12 марта») — стилем фразы (Golos). Иначе внутри строки скачет шрифт.
- **Даты** — текст, не цифра.
- Это смена правила дизайн-системы: выбор «внутри фразы — Golos» подтвердить у Максима на гейте 1. Альтернатива — `MoneyText` рисует суммы внутри фраз тоже Unbounded.

### Шаги
1. **Домен, новый `domain/model/Figures.kt`:** `object Figures { fun isFigure(text: String): Boolean }` — строка целиком является значением: необязательный знак `+ - −`, цифры с пробелами-разделителями (обычный, неразрывный и узкий неразрывный) и `,`/`.`, затем необязательная единица. Единица — одна из: `%`, валютный знак (тот же набор, что в `MoneyMask`), трёхбуквенный код `[A-Z]{3}`, явный список `мес мес. дн дн. д. г. год года лет шт шт. тыс млн млрд` (+ `₽` после `млн`/`тыс`). Явный список нужен, чтобы «3 мая» не считалось цифрой. Пробел между числом и единицей — любой из трёх.
2. **Токены, `ui/theme/Type.kt:90-116`:** в `Amounts` добавить `Caption` (Unbounded SemiBold, tnum, 11sp, lineHeight 14sp) — для значков, подписей графиков и виджета. KDoc объекта переписать: «Every standalone figure — sum, percent, rate, term, badge, axis label — takes one of these (U-73)».
3. **Нарушители по таблице аудита:**
   - `CashbackScreen.kt:450-452` → `style = if (small) Amounts.Small else Amounts.Row`, убрать `.copy(fontFamily=…)` и `fontWeight` (вес уже в токене).
   - `DepositDetailScreen.kt:257` → `if (Figures.isFigure(value)) Amounts.Row else MaterialTheme.typography.bodyLarge`.
   - `SubscriptionScreens.kt:268` → то же правило через `Figures.isFigure`.
   - `SubscriptionsSection.kt:122`, `SubscriptionScreens.kt:228` → `Amounts.Large`, без `fontWeight`.
   - `SubscriptionsSection.kt:179` → `Amounts.Row`; `:208, :226`, `SubscriptionScreens.kt:253` → `Amounts.Small`; убрать `fontWeight`.
   - `CapitalUi.kt:122` → `Amounts.Hero`.
   - `CashbackScreen.kt:429`, `BankStack.kt:34` → `Amounts.Caption`.
   - Строки «проверить» из таблицы и подписи графиков: самостоятельная цифра → `Amounts.Small`/`Caption`, внутри фразы → оставить.
   - Удалить 10 мёртвых импортов `Unbounded`.
4. **Виджет `ui/widget/CashbackWidget.kt`.** Glance 1.x задаёт только системные семейства (`FontFamily("sans-serif")` и т.п.) и не грузит `res/font`. Поэтому проценты рисуем картинкой: так же, как виджет уже рисует логотипы.
   - Новый `ui/widget/FigureBitmap.kt`: `fun figureBitmap(context: Context, text: String, sizeSp: Float, color: Int, widthDp: Float, alignEnd: Boolean): Bitmap`. Внутри:
     - шрифт `ResourcesCompat.getFont(context, R.font.unbounded_wght)`; на API 28+ `Typeface.create(tf, 600, false)`, ниже — как есть;
     - `Paint` с `fontFeatureSettings = "tnum"`, `textSize` в px по `displayMetrics.scaledDensity`;
     - ширина = `widthDp` в px, высота = по `fontMetrics`, `ARGB_8888`.
   - `PercentText` (`:379-389`), процент чипа `BaseLine` (`:322-327`) и `block.base` (`:281-286`) → `Image(ImageProvider(figureBitmap(...)), contentDescription = текст, modifier = GlanceModifier.size(w.dp, h.dp))`.
   - Цвет — `Slitok.Brass2.toArgb()` / `Slitok.Muted.toArgb()`, размеры прежние (14/12sp).
   - Подписи и названия категорий остаются текстом Glance: это текст, не цифры.
   - Context брать в `provideGlance`, где он уже есть.
5. **Guard, `app/src/test/java/ru/maxrubtsoff/bankagg/ui/DesignSystemGuardTest.kt`:**
   - `:37` — заменить `"fontFamily = Unbounded"` на `"fontFamily =" to Regex("""fontFamily\s*=""")`. Ловит и `.copy(fontFamily = …)` с полным именем.
   - `:24` — исключение виджета удалить: он больше не задаёт шрифт.
   - Новый запрет `"sum in a text style"`: `Regex("""(MoneyText|AmountText)\([^)]*style = MaterialTheme\.typography\.(display|headline|title)""")` — крупная сумма только шкалой `Amounts`. `body*` разрешены: это суммы внутри фраз.
6. **`docs/design-system/README.md:38`** — абзац «Цифры — одним шрифтом (U-73)»: правило выше, шкала `Hero/Large/Row/Small/Caption`, `Figures.isFigure` для строк «ярлык — значение», даты — текст. Отметить, что виджет рисует цифры картинкой тем же шрифтом. Витрина `ShowcaseScreen.kt` — строка с `Amounts.Caption` рядом с остальной шкалой.
7. **Тесты, новый `app/src/test/java/ru/maxrubtsoff/bankagg/domain/FiguresTest.kt`:**
   - `percentIsFigure` — «18 %», «18,5%», «+3».
   - `moneyIsFigure` — «1 200,50 ₽», «4,27 млн ₽», «100 USD».
   - `termIsFigure` — «12 мес», «3 года», «40 дн.».
   - `dateIsNotFigure` — «3 мая», «12 марта 2026».
   - `wordsAreNotFigures` — «Ежемесячно», «до 18 %», «Т-Банк».
   - `emptyIsNotFigure` — «», «—».

**Критерии:**
- Тесты и сборка зелёные, `DesignSystemGuardTest` проходит с усиленными запретами.
- `grep -rn "fontFamily" app/src/main/java/.../ui` даёт совпадения только в `ui/theme`.
- Инвариант: каждый `TextStyle` с цифрой-значением берётся из `Amounts`, а Unbounded задаётся только в `ui/theme`.
- На устройстве: «Платежи/Подписки» (сумма в месяц, цены), карточка подписки, карточка вклада (ставка и срок тем же шрифтом, что суммы), «Кэшбэк» (проценты), виджет кэшбэка (проценты Unbounded, не обрезаны, не размыты).

---

## B-116

**Цель:** в режиме «только кэшбэк и подписки» глаз в «Обзоре» есть, пока есть что скрывать, и после тапа не пропадает, а переключается.

**Причина (по коду):** в манифесте у Activity стоит `screenOrientation="portrait"` (`AndroidManifest.xml:49`). «Перевернуть» здесь — положить экраном вниз: срабатывает `FlipDetector` (`HiddenAmounts.kt:98`) и ставит `hidden=true`.
- В этом режиме `hasAmounts` (`OverviewScreen.kt:155`) считается только по частям капитала → `false`. Глаз виден лишь из-за `hidden` (`HideToggle.visible = hasAmounts || hidden`, `HiddenAmounts.kt:90`).
- Тап → `hidden=false` → `false || false` → глаз исчезает.
- Ошибка в том, что не учтены суммы подписок. Скрытие — на всё приложение, оно прячет цены во вкладке «Платежи». У кэшбэка сумм нет (только проценты; `CashbackCard` не использует `MoneyText`), его не считаем.

Шаги:
1. **Домен, `domain/model/HiddenAmounts.kt:85-91`:** в `HideToggle` добавить
   `fun hasAmounts(features: Features, capitalHasData: Boolean, activeSubscriptions: Int): Boolean = (features.showCapital && capitalHasData) || (features.showSubscriptions && activeSubscriptions > 0)`.
   KDoc: «B-116: hiding is app-wide, so the eye counts every section with sums, not only the capital». `visible` не трогать.
2. **`ui/overview/OverviewScreen.kt`:**
   - `:126-142` — параметр `activeSubscriptions: Int = 0`.
   - `:155` → `val hasAmounts = HideToggle.hasAmounts(features, snapshot?.parts?.any { it.hasData } == true, activeSubscriptions)`.
3. **`ui/MainActivity.kt`, вызов `OverviewScreen` (≈`:728-748`):** выше в `AppRoot` (рядом с `:495`) `val subscriptionsState by subscriptionsVm.state.collectAsStateWithLifecycle()`; передать `activeSubscriptions = subscriptionsState.active.size`.
4. **Тесты, `app/src/test/java/ru/maxrubtsoff/bankagg/domain/HideToggleTest.kt`:**
   - `subscriptionsOnlyModeShowsEye` — Features без частей капитала, с подписками, 2 активные → `hasAmounts == true` и `visible(…, hidden=false) == true`.
   - `eyeStaysAfterShowingAgain` — тот же режим: `visible(hasAmounts, hidden=true)` и `visible(hasAmounts, hidden=false)` — оба `true`.
   - `capitalWithoutDataAndNoSubscriptionsHidesEye` — `hasAmounts == false`.
   - `subscriptionsSwitchedOffDontCount` — подписки выключены в `Features`, 3 активные → `false`.
   - Features для тестов собрать так, как это делают существующие `FeaturesTest`.

**Критерии:**
- Тесты и сборка зелёные.
- Инвариант: если `hasAmounts == true`, глаз виден при любом `hidden`.
- На устройстве: режим «кэшбэк + подписки», есть подписка. Глаз виден сразу. Тап — суммы во вкладке «Платежи» скрыты, глаз латунный. Тап — показаны, глаз на месте. Положить экраном вниз и поднять — глаз латунный, тап его не убирает.
- Остаётся по правилу U-41: без единой подписки глаз после «показать» исчезает, потому что скрывать нечего.

---

## F-54

**Цель:** CI собирает release APK и AAB, в репозитории есть политика конфиденциальности, а в `docs/publishing.md` — полный чеклист RuStore: что готово в приложении и что сделать Максиму.

**Решения:**
- **В RuStore загружаем APK, AAB только собираем про запас.** Для AAB RuStore просит загрузить ему ключ подписи, а с APK ключ остаётся только у Максима. Перед первой загрузкой сверить с консолью RuStore.
- **Подпись не трогаем.** `signingConfigs` и `debug.keystore` не меняются. Но в RuStore нельзя выкладывать сборку на `app/debug.keystore`: ключ лежит в публичном репозитории, и подписать «обновление» сможет кто угодно. Поэтому первый шаг Максима — ключ публикации по `docs/publishing.md:13-40`, и только потом загрузка.
- **`versionCode` = `GITHUB_RUN_NUMBER`** (`app/build.gradle.kts:17-19`) растёт монотонно, схему оставляем. `versionName` `0.6.N` — вопрос Максиму на гейте 1: выйти в магазин как `1.0.N`? Правка одной строки `:19`.

Шаги (приложение):
1. **`.github/workflows/build.yml:126-139`:**
   - шаг переименовать в «Build release APK and AAB»;
   - `:133` → `./gradlew assembleRelease bundleRelease --no-daemon --stacktrace`. Один запуск: R8 общий, второй раз не идёт;
   - после проверки APK (`:134`) — такая же проверка `app/build/outputs/bundle/release/app-release.aab`.
2. **`.github/workflows/build.yml`, после `:146`:** шаг «Upload AAB» — тот же pinned `actions/upload-artifact@ea165f8d…`, `name: itogo-aab`, `path: app/build/outputs/bundle/release/app-release.aab`, `if-no-files-found: error`. Артефакт `bank-aggregator` (APK) не переименовывать: на него опираются установка и device-check.
3. **Проверка зависимостей:** если `bundleRelease` упрётся в `gradle/verification-metadata.xml`, выполнить `./gradlew --write-verification-metadata sha256 testDebugUnitTest assembleRelease bundleRelease` и закоммитить изменения файла.
4. **`app/proguard-rules.pro`** (в конец) — страховка от отладочного вывода в release:
   `-assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); }`.
   `Log.w` с ошибками (`LogoMaker.kt:26`, `PhoneKeys.kt:57`, `AppDatabase.kt:174`, `DatabaseCipher.kt:161,179`) оставить: в них нет данных пользователя, только тип сбоя.
5. **Аудит release (правок не требует, записать в publishing.md как «проверено»):**
   - `isDebuggable` в release не задан, то есть `false`;
   - `isMinifyEnabled`/`isShrinkResources = true` (`build.gradle.kts:61-62`);
   - `baseline-prof.txt` на месте, `profileinstaller` подключён (`:109`);
   - `allowBackup="false"` (`AndroidManifest.xml:36`);
   - разрешения только INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS (`:6-9`); нет SMS и `QUERY_ALL_PACKAGES`, банки перечислены в `<queries>` (`:16-32`);
   - слушатель уведомлений защищён `BIND_NOTIFICATION_LISTENER_SERVICE`, `exported="false"` (`:98-106`);
   - targetSdk 34, minSdk 26; `applicationId` `ru.maxrubtsoff.bankagg`; имя «Itogo» (`res/values/strings.xml:3`).
6. **Новый `docs/privacy.md`.** Две версии:
   - **Коротко** (до 500 знаков, для поля карточки): данные хранятся только на телефоне, база зашифрована; приложение читает уведомления только выбранных банков; в сеть ходит только за курсами ЦБ РФ; рекламы, аналитики и передачи третьим лицам нет.
   - **Полностью**, разделы:
     - какие данные: операции, остатки, вклады, кэшбэк, подписки, скрины;
     - откуда: уведомления банков, которые выбрал пользователь, и скрины, которые он сам передал. SMS не читаются;
     - где хранятся: Room + SQLCipher, ключ в Android Keystore;
     - сеть: только `cbr.ru`, курсы валют, без данных пользователя;
     - распознавание скринов офлайн, на телефоне;
     - резервная копия: шифруется паролем пользователя и уходит только туда, куда он её отправит сам;
     - разрешения и зачем каждое;
     - удаление данных: удалить приложение или очистить данные;
     - дети, изменения политики, контакт.
   - Контакт — заглушка `<email для связи>`, её заполнит Максим. Дата редакции.
   - Тексты сверить с `docs/analysis/distribution-and-banks.md` и экраном `ui/settings/SettingsPrivacyScreen.kt`: формулировки не должны противоречить экрану «Приватность».
7. **`docs/publishing.md:55-60`** — раздел «Что ещё нужно для RuStore» заменить двумя:
   - **«RuStore: что готово в приложении»:** список аудита из шага 5, артефакты CI (`bank-aggregator` — APK для загрузки, `itogo-aab` — AAB про запас), где лежит политика.
   - **«RuStore: что сделать Максиму»** — чеклист с галочками по порядку:
     1. Ключ публикации и 4 секрета CI (ссылка на раздел выше). Ключ и пароли в менеджере паролей плюс офлайн-копия файла. Потеря ключа = больше не выпустить обновление.
     2. Перевести свой телефон на новую подпись по разделу «Переход на новый ключ» (копия → удалить → поставить → восстановить).
     3. Аккаунт разработчика в консоли RuStore (вход через VK ID). Для бесплатного приложения хватает физлица, регистрация бесплатная. ИП или самозанятость нужны только для платных приложений и покупок — сверить с текущими условиями консоли.
     4. Опубликовать политику по постоянному адресу. Рекомендация: GitHub Pages из отдельного маленького публичного репозитория `itogo-privacy` (адрес под контролем Максима, без сервера приложения). Запасной вариант — telegra.ph. Ссылку вписать в карточку и в `docs/privacy.md`.
     5. Карточка:
        - название «Itogo»;
        - короткое и полное описание (основа — вводные слайды F-29);
        - категория «Финансы»;
        - возраст — по анкете консоли;
        - иконка 512×512 PNG;
        - скриншоты: минимум 2–3 вертикальных с устройства — Обзор, Капитал, Платежи, виджет, на демо-данных или со скрытыми суммами (точные размеры — по требованиям консоли);
        - ссылка на политику.
     6. Загрузить `app-release.apk` из артефакта `bank-aggregator` сборки `main` с ключом публикации. Перед загрузкой проверить подпись: `apksigner verify --print-certs` должен показать новый сертификат, а не `Android Debug`.
     7. Ответы модерации — готовые тексты в этом же разделе:
        - зачем доступ к уведомлениям: читаются только уведомления выбранных банковских приложений, чтобы записать операцию и остаток; ничего не отправляется; доступ можно выключить в настройках Android;
        - зачем интернет: только курсы ЦБ;
        - зачем `POST_NOTIFICATIONS`: свои напоминания;
        - зачем `<queries>`: показать иконки установленных банков без широкого разрешения.
     8. Каждый выпуск: новый `versionCode` (повторный запуск того же CI-run даёт тот же номер, RuStore его отклонит — выпускать новым пушем).
8. **`docs/cycles/cycle-15.md`, пункт F-54:** одна строка-ссылка на раздел «RuStore: что сделать Максиму» в `docs/publishing.md`, без копии текста.

**Тесты:** доменной логики нет. Есть `app/src/test/.../domain/PrivacyTest.kt`, он должен остаться зелёным. Новый тест не нужен: разрешения и `<queries>` уже стерегут `PrivacyTest` и хук.

**Критерии:**
- Локально `./gradlew testDebugUnitTest assembleDebug bundleRelease` зелёные.
- В CI оба артефакта, `bank-aggregator` (APK) и `itogo-aab` (AAB), сборка зелёная.
- `docs/privacy.md` есть, в нём короткая и полная версии, и он не противоречит экрану «Приватность».
- В `docs/publishing.md` есть разделы «RuStore: что готово» и «RuStore: что сделать Максиму» с текстами для модерации.
- `signingConfigs` и `app/debug.keystore` без изменений (`git diff` по ним пуст).
- В release: не debuggable, R8 и shrink включены, `Log.v/d/i` вырезаются.

---

## Решения гейта 1

- **U-73.** Цифры внутри фраз («подорожала на 100 ₽», даты) — текстовым Golos. Unbounded — только отдельно стоящие значения (суммы в hero, проценты в карточках кэшбэка, значения на графиках). Правило — в `design-system/README.md` и `ui/theme/AppTypography`.
- **F-54.** Версия релиза `1.0.0` (versionName), versionCode = номер сборки CI. Политика конфиденциальности — GitHub Pages из этого репо (ветка `main`, папка `/docs` или `/privacy`). Контакт в политике и карточке RuStore — `maxrubtsoff@yandex.ru`.

## F-53

**Выбор на гейте 2:** вариант В (свайп в герое `По частям · По банкам`). Макет — `docs/mockups/cycle-15/F-53/banks-slice.html`.

**Правки к макету:**
- Банки с нулевой суммой/без операций в списке не показывать совсем.
- Строка банка в списке — только логотип, название, сумма. Никаких счётчиков «N счетов / N вкладов / N подписок», никакой «кэшбэк за месяц» строкой.
- Шторка банка (по тапу) — счёт банка с суммой и вклады банка с суммами (если есть), плюс ссылки «Подписки этого банка» → фильтр в «Платежах», «Кэшбэк этого банка» → фильтр в «Кэшбэке». Без «прибыли по кэшбэку» и прочих агрегатов, которых в домене нет.

Цвет полоски банка — автоматом по `androidx.palette:palette-ktx`:
- взять битмапу логотипа (через `LogoMaker` или Coil cache),
- `Palette.from(bitmap).generate().vibrantSwatch ?: dominantSwatch`,
- если `HSLToColor` даёт `saturation < 0.2` — запас: `BankBrand.accent` (если есть) или `Slitok.Brass`,
- результат кэшить по id банка (map в `LogoMaker` или отдельный `BankAccentCache`).

**Файлы:**
- Домен: `domain/model/BankSlice.kt` (данные среза), `domain/usecase/CapitalByBank.kt` (чистая функция `slice(accounts, deposits, subscriptions, cashback, rates, myBanks) -> List<BankSlice>` + остаток «Не в банке», сортировка по сумме убыв., фильтр `isNotEmpty`), тест на фильтрацию пустых и сортировку.
- Repo: ничего нового, использует `AccountRepository`, `DepositRepository`, `SubscriptionRepository`, `CashbackRepository`, `RatesSource`, `MyBanksScope`.
- VM: расширить `OverviewViewModel` — Flow `banksSlice: StateFlow<List<BankSlice>>`, состояние выбранного разреза (`SliceMode.ByParts | ByBanks`), сохранение выбора в `SettingsStore`.
- UI: `ui/overview/OverviewScreen.kt` — `PagerTabs` «По частям · По банкам» над полосой; при `ByBanks` отрисовка списка банков через `BankShareRow`. Если банков < 2 (только один с суммой или ноль) — переключатель не показывать, оставить обычный обзор.
- Общее: `ui/common/BankShareRow.kt` (строка банка в списке), `ui/common/BankSheet.kt` (шторка банка с деталями: счета, вклады, кэшбэк за месяц, подписки), `ui/common/BankAccentCache.kt` (Palette → цвет).
- Данные для цвета: `data/icons/LogoMaker.kt` — добавить метод `accentColorFor(bankId): Color` с кэшем.

**Критерии:**
- Доменный тест `CapitalByBankTest`: пустые банки отфильтрованы, сумма банка = счета + вклады (валюта по курсу), остаток «Не в банке» равен капиталу минус сумма банков.
- Переключатель видно, если банков ≥ 2 с ненулевой суммой.
- Полоска банка окрашена по Palette; на сером лого — запасной цвет.
- Выбранный разрез переживает reconfiguration/перезапуск (сохранён в `SettingsStore`).
- `./gradlew testDebugUnitTest assembleDebug` зелёный.

**Группа:** F-53 трогает `OverviewScreen.kt`. Общие файлы с U-73 (там токены цифр) и B-116 (`HiddenAmounts`/OverviewScreen). Порядок: Group A (B-116 → B-115 → U-72) → U-73 → F-53. Параллельно с A идёт F-54 (разные файлы).

## U-74

**Файлы:** `ui/overview/OverviewScreen.kt`, модели/форматтеры подстрок (`domain/model/Capital*` или `ui/overview/OverviewRow*` — уточнить по месту).

**Шаги:**
1. Строка «Вклады»: подстрока прироста (`+N ₽ в месяц`) — убрать акцентный зелёный, подать тоном `Slitok.Faint`/вторичного текста (как подзаголовок «6 вкладов»). Остаётся Golos (цифры в фразе).
2. Строка «Валюта»: подстрока = `$ X · € Y · ...` через `·`. Форматирование: короткий знак валюты + сумма без копеек, рубль туда не включаем. Если валют нет — строки «Валюта» нет (как сейчас). Формировать в UI-слое из `Capital.currencies`.
3. Готовой витрины на экране валют не трогать.

**Критерии:**
- В обзоре строка «Вклады» не светится зелёным: подстрока прироста — вторичный серый.
- Строка «Валюта» показывает сами суммы, а не счётчик позиций.
- Тесты зелёные.
