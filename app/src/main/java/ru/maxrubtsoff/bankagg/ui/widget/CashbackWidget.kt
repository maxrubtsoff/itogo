package ru.maxrubtsoff.bankagg.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.App
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.CashbackSpotlight
import ru.maxrubtsoff.bankagg.domain.model.CashbackWidgetLayout
import ru.maxrubtsoff.bankagg.domain.model.CashbackWidgetOrder
import ru.maxrubtsoff.bankagg.domain.model.CashbackWidgetSort
import ru.maxrubtsoff.bankagg.R
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import java.time.LocalDate
import ru.maxrubtsoff.bankagg.data.notify.AppNotifier
import ru.maxrubtsoff.bankagg.ui.MainActivity
import ru.maxrubtsoff.bankagg.ui.cashback.formatPercent
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.util.InstalledApps

/**
 * Home-screen widget: the month's cashback, filling whatever size it is given
 * ([CashbackWidgetLayout]). Small — what the home card shows; medium — more
 * categories; large — compact blocks "bank → its categories". The base rate
 * comes first. Reads the local Room data directly (no network); a tap opens
 * the app.
 *
 * Widths are laid out explicitly from [LocalSize] rather than weights, and
 * spacing is done with Spacers, not paddings on sized rows, so the percent
 * column always sits whole and flush right.
 */
class CashbackWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    // The sort mode lives in the widget instance's own state (F-56).
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    private companion object {
        /** Glance renders at most this many children per Row/Column. */
        const val MAX_CHILDREN = 10
        /** Category lines per bank block (+ its header stays within MAX_CHILDREN). */
        const val BLOCK_ROWS = 8
        /** Touch zone of one sort button (at least 32dp). */
        const val SORT_BTN = 32f
    }

    /** A bank mark: its installed app icon when available, else its initial on its colour. */
    private data class Logo(val bitmap: Bitmap?, val initial: String, val color: Long, val textDark: Boolean)

    private data class WidgetRow(val category: String, val logo: Logo, val percent: String)

    /** One base-rate chip: the banks sharing a percent. */
    private data class BaseChip(val logos: List<Logo>, val percent: String)

    /** What the widget shows; the order is applied in [Content] by [CashbackWidgetOrder]. */
    private data class Model(
        val month: String,
        val spotRows: List<CashbackSpotlight.Row>,
        val groups: List<CashbackWidgetOrder.BankGroup>,
        val logos: Map<BankBrand, Logo>,
        val cardRows: Int,
        val base: List<BaseChip>,
        val missing: Boolean,
        val nextHint: String?,
        /** Cashback isn't kept at all: the widget says so and opens «Что храним» (U-52). */
        val cashbackOff: Boolean = false
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = load(context)
        provideContent {
            val sort = CashbackWidgetSort.parse(currentState<Preferences>()[SORT_KEY])
            Content(model, sort)
        }
    }

    // The month is recomputed on every draw; how many rows render is decided by
    // the widget's size in [Content], so a larger widget simply shows more.
    private suspend fun load(context: Context): Model {
        val today = LocalDate.now()
        val key = CashbackMonth.keyOf(today)
        // Closed data (B-91): an empty widget until the app has opened it.
        if (!App.instance.container.isReady) {
            return Model(CashbackMonth.shortLabel(key), emptyList(), emptyList(), emptyMap(), 0, emptyList(), false, null)
        }
        val repo = App.instance.container.cashbackRepository
        if (!App.instance.container.settings.currentFeatures().showCashback) {
            return Model(CashbackMonth.shortLabel(key), emptyList(), emptyList(), emptyMap(), 0, emptyList(), false, null, cashbackOff = true)
        }
        val items = repo.observeMonth(key).first()
        val hasNext = repo.observeMonth(CashbackMonth.nextKey(key)).first().isNotEmpty()
        val spot = CashbackSpotlight.build(items, hasNext, today)
        val iconPx = (20 * context.resources.displayMetrics.density).toInt().coerceAtLeast(16)
        val logos = HashMap<BankBrand, Logo>()
        fun logo(bank: BankBrand) = logos.getOrPut(bank) {
            // The user's own picture first (B-49), then the app's icon.
            val container = (context.applicationContext as App).container
            val bitmap = bank.logoFile?.let { android.graphics.BitmapFactory.decodeFile(it) }
                ?: container.bankIcons.icons.value[bank.key]
                ?: InstalledApps.iconFor(context, bank, iconPx)
            Logo(bitmap, bank.title.take(1).uppercase(), bank.primaryColor, bank.onPrimaryDark)
        }
        val groups = CashbackWidgetOrder.blocks(items)
        val base = spot.base.map { g -> BaseChip(g.banks.map(::logo), formatPercent(g.percent)) }
        spot.allRows.forEach { r -> logo(r.banks.first()) }
        groups.forEach { logo(it.brand) }
        val nextHint = if (spot.offerNextMonth) "Добавьте кэшбэк на ${CashbackMonth.shortLabel(spot.nextMonthKey).lowercase()}" else null
        val cardRows = spot.rows.size + (if (spot.maximum != null) 1 else 0)
        return Model(CashbackMonth.shortLabel(key), spot.allRows, groups, logos, cardRows, base, spot.missingThisMonth, nextHint)
    }

    @androidx.compose.runtime.Composable
    private fun Content(model: Model, sort: CashbackWidgetSort) {
        val size = LocalSize.current
        val w = size.width.value
        val h = size.height.value
        val hasBase = model.base.isNotEmpty()
        val withBase = CashbackWidgetLayout.grid(w, h, baseLine = hasBase)
        val innerW = w - 2 * CashbackWidgetLayout.PADDING
        val narrow = withBase.columns == 1
        val empty = model.spotRows.isEmpty() && !hasBase
        val showSort = !model.cashbackOff && !empty
        val rows = CashbackWidgetOrder.rows(model.spotRows, sort).map {
            WidgetRow(it.title, model.logos.getValue(it.banks.first()), formatPercent(it.percent))
        }

        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(ColorProvider(Slitok.PanelBottom))
                .clickable(
                    actionStartActivity(
                        Intent(LocalContext.current, MainActivity::class.java).apply {
                            // Cashback switched off: the tap goes to «Что храним» (U-52).
                            if (model.cashbackOff) putExtra(AppNotifier.EXTRA_OPEN, AppNotifier.OPEN_FEATURES)
                        }
                    )
                )
                .padding(CashbackWidgetLayout.PADDING.dp)
        ) {
            val titleW = if (showSort) (innerW - 3 * SORT_BTN - 4f).coerceAtLeast(40f) else innerW
            Row(
                GlanceModifier.fillMaxWidth().height(SORT_BTN.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Row(GlanceModifier.width(titleW.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(
                        "Кэшбэк",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Slitok.Muted), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                    // The month only where there is room: the wide widget (F-56).
                    if (!narrow) {
                        Text(
                            " · ${model.month}",
                            maxLines = 1,
                            style = TextStyle(color = ColorProvider(Slitok.Faint), fontSize = 11.sp)
                        )
                    }
                }
                if (showSort) {
                    Spacer(GlanceModifier.width(4.dp))
                    SortSwitch(sort)
                }
            }
            Spacer(GlanceModifier.height((CashbackWidgetLayout.HEADER - SORT_BTN).dp))

            when {
                model.cashbackOff -> Text(
                    "Кэшбэк выключен · включить в «Что храним»",
                    style = TextStyle(color = ColorProvider(Slitok.Muted), fontSize = 13.sp)
                )
                empty -> Text(
                    if (model.missing) "Кэшбэк на ${model.month.lowercase()} не добавлен" else "Нет кэшбэка в этом месяце",
                    style = TextStyle(color = ColorProvider(Slitok.Muted), fontSize = 13.sp)
                )
                sort == CashbackWidgetSort.BANK -> {
                    // Grouped by bank, base line on top (F-56 mockup B).
                    if (hasBase) {
                        BaseLine(model.base, innerW)
                        Spacer(GlanceModifier.height((CashbackWidgetLayout.BASE_LINE - 18f).dp))
                    }
                    val free = CashbackWidgetLayout.banksHeight(h) - (if (hasBase) CashbackWidgetLayout.BASE_LINE else 0f)
                    BankBlocks(model.groups, model.logos, CashbackWidgetLayout.grid(w, h, baseLine = false), free)
                }
                else -> {
                    // The base rate leads (U-8), then the categories, as on the home card.
                    if (hasBase) {
                        BaseLine(model.base, innerW)
                        Spacer(GlanceModifier.height((CashbackWidgetLayout.BASE_LINE - 18f).dp))
                    }
                    val flat = CashbackWidgetLayout.flat(withBase, rows.size)
                    CategoryGrid(rows.take(flat.shown), withBase)
                    if (flat.showMore) {
                        Text(
                            "+" + RuPlural.categories(flat.hidden), maxLines = 1,
                            modifier = GlanceModifier.padding(top = CashbackWidgetLayout.GAP.dp),
                            style = TextStyle(color = ColorProvider(Slitok.Muted), fontSize = 12.sp)
                        )
                    }
                    val usedLines = (flat.shown + withBase.columns - 1) / withBase.columns
                    // Room left at month's end: nudge to add next month.
                    if (model.nextHint != null && withBase.lines - usedLines >= 2) {
                        Text(
                            model.nextHint, maxLines = 1,
                            modifier = GlanceModifier.padding(top = 10.dp),
                            style = TextStyle(color = ColorProvider(Slitok.Brass2), fontSize = 12.sp)
                        )
                    }
                }
            }
        }
    }

    /** Three sort buttons in an inset: bank, size, A-Z; the chosen one in brass. Each is a 32dp touch zone. */
    @androidx.compose.runtime.Composable
    private fun SortSwitch(current: CashbackWidgetSort) {
        Row(
            GlanceModifier.cornerRadius(10.dp).background(ColorProvider(Slitok.Inset)),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            listOf(
                CashbackWidgetSort.BANK to R.drawable.ic_widget_sort_bank,
                CashbackWidgetSort.SIZE to R.drawable.ic_widget_sort_size,
                CashbackWidgetSort.NAME to R.drawable.ic_widget_sort_name
            ).forEach { (mode, icon) ->
                val on = mode == current
                Box(
                    GlanceModifier.size(SORT_BTN.dp).clickable(
                        actionRunCallback<SetSortAction>(actionParametersOf(SortParam to mode.name))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        GlanceModifier.width(28.dp).height(24.dp).cornerRadius(8.dp)
                            .background(ColorProvider(if (on) Slitok.BrassPill else Color.Transparent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(icon),
                            contentDescription = mode.label,
                            colorFilter = ColorFilter.tint(ColorProvider(if (on) Slitok.Brass2 else Slitok.Faint)),
                            modifier = GlanceModifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    /** Lines of one or two columns; nested columns of ≤10 since Glance drops the rest. */
    @androidx.compose.runtime.Composable
    private fun CategoryGrid(rows: List<WidgetRow>, grid: CashbackWidgetLayout.Grid) {
        Column(GlanceModifier.fillMaxWidth()) {
            rows.chunked(grid.columns).chunked(MAX_CHILDREN).forEachIndexed { block, lines ->
                Column(GlanceModifier.fillMaxWidth()) {
                    lines.forEachIndexed { i, line ->
                        val top = if (block > 0 || i > 0) CashbackWidgetLayout.GAP else 0f
                        Row(GlanceModifier.fillMaxWidth().padding(top = top.dp)) {
                            CategoryRow(line[0].logo, line[0].category, line[0].percent, grid)
                            if (line.size > 1) {
                                Spacer(GlanceModifier.width(CashbackWidgetLayout.COLUMN_GAP.dp))
                                CategoryRow(line[1].logo, line[1].category, line[1].percent, grid)
                            }
                        }
                    }
                }
            }
        }
    }

    /** The large widget: bank blocks packed into columns, none split; "+N банков" for the rest. */
    @androidx.compose.runtime.Composable
    private fun BankBlocks(
        banks: List<CashbackWidgetOrder.BankGroup>,
        logos: Map<BankBrand, Logo>,
        grid: CashbackWidgetLayout.Grid,
        heightDp: Float
    ) {
        val shown = banks.map { it.copy(rows = it.rows.take(BLOCK_ROWS)) }
        val packing = CashbackWidgetLayout.packDp(shown.map { it.rows.size }, grid.columns, heightDp)
        Row(GlanceModifier.fillMaxWidth()) {
            packing.columns.forEachIndexed { c, indices ->
                if (c > 0) Spacer(GlanceModifier.width(CashbackWidgetLayout.COLUMN_GAP.dp))
                Column(GlanceModifier.width(grid.columnWidth.dp)) {
                    indices.take(MAX_CHILDREN - 1).forEachIndexed { k, idx ->
                        BankBlockView(shown[idx], logos.getValue(shown[idx].brand), grid, top = if (k > 0) CashbackWidgetLayout.GAP else 0f)
                    }
                    if (c == packing.columns.lastIndex && packing.hidden > 0) {
                        Text(
                            "+" + RuPlural.banks(packing.hidden),
                            maxLines = 1,
                            modifier = GlanceModifier.padding(top = CashbackWidgetLayout.GAP.dp),
                            style = TextStyle(color = ColorProvider(Slitok.Muted), fontSize = 12.sp)
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BankBlockView(block: CashbackWidgetOrder.BankGroup, logo: Logo, grid: CashbackWidgetLayout.Grid, top: Float) {
        Column(GlanceModifier.width(grid.columnWidth.dp).padding(top = top.dp)) {
            Row(GlanceModifier.width(grid.columnWidth.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                LogoMark(logo, CashbackWidgetLayout.LOGO.toInt())
                Spacer(GlanceModifier.width(CashbackWidgetLayout.LOGO_GAP.dp))
                Text(
                    block.brand.title,
                    maxLines = 1,
                    modifier = GlanceModifier.width((grid.columnWidth - CashbackWidgetLayout.LOGO - CashbackWidgetLayout.LOGO_GAP).dp),
                    style = TextStyle(color = ColorProvider(Slitok.Fg), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
            }
            block.rows.forEach { item ->
                val name = item.category
                val pct = formatPercent(item.percent)
                Row(
                    GlanceModifier.width(grid.columnWidth.dp).padding(top = CashbackWidgetLayout.GAP.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Spacer(GlanceModifier.width((CashbackWidgetLayout.LOGO + CashbackWidgetLayout.LOGO_GAP).dp))
                    Text(
                        name,
                        maxLines = 1,
                        modifier = GlanceModifier.width(grid.nameWidth.dp),
                        style = TextStyle(color = ColorProvider(Slitok.Fg), fontSize = 13.sp)
                    )
                    Spacer(GlanceModifier.width(CashbackWidgetLayout.NAME_GAP.dp))
                    PercentText(pct)
                }
            }
        }
    }

    /** "Базовый" and as many chips (logos + percent) as fit the width. */
    @androidx.compose.runtime.Composable
    private fun BaseLine(chips: List<BaseChip>, innerW: Float, modifier: GlanceModifier = GlanceModifier) {
        var left = innerW - 60f
        val fitting = chips.take(MAX_CHILDREN - 1).takeWhile { c ->
            val w = 12f + 18f * minOf(c.logos.size, 3) + 4f + 34f
            (left >= w).also { if (it) left -= w }
        }
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text("Базовый", style = TextStyle(color = ColorProvider(Slitok.Muted), fontSize = 11.sp))
            fitting.forEach { c ->
                Row(GlanceModifier.padding(start = 12.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    c.logos.take(3).forEachIndexed { i, l ->
                        LogoMark(l, 16, GlanceModifier.padding(start = if (i > 0) 2.dp else 0.dp))
                    }
                    FigureImage(c.percent, 12f, Slitok.Brass2, 34f, GlanceModifier.padding(start = 4.dp), alignEnd = false)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LogoMark(logo: Logo, sizeDp: Int, modifier: GlanceModifier = GlanceModifier) {
        if (logo.bitmap != null) {
            Image(
                provider = ImageProvider(logo.bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.size(sizeDp.dp).cornerRadius((sizeDp / 4).dp)
            )
        } else {
            Box(
                modifier
                    .size(sizeDp.dp)
                    .cornerRadius((sizeDp / 4).dp)
                    .background(ColorProvider(Color(logo.color))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    logo.initial,
                    style = TextStyle(
                        color = ColorProvider(if (logo.textDark) Slitok.OnBrassInk else Color.White),
                        fontSize = (sizeDp / 2).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun CategoryRow(logo: Logo, name: String, percent: String, grid: CashbackWidgetLayout.Grid) {
        Row(GlanceModifier.width(grid.columnWidth.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
            LogoMark(logo, CashbackWidgetLayout.LOGO.toInt())
            Spacer(GlanceModifier.width(CashbackWidgetLayout.LOGO_GAP.dp))
            Text(
                name,
                maxLines = 1,
                modifier = GlanceModifier.width(grid.nameWidth.dp),
                style = TextStyle(color = ColorProvider(Slitok.Fg), fontSize = 13.sp)
            )
            Spacer(GlanceModifier.width(CashbackWidgetLayout.NAME_GAP.dp))
            PercentText(percent)
        }
    }

    @androidx.compose.runtime.Composable
    private fun PercentText(percent: String) {
        FigureImage(percent, 14f, Slitok.Brass2, CashbackWidgetLayout.PERCENT_WIDTH)
    }
}

private val SORT_KEY = stringPreferencesKey("sort")
private val SortParam = ActionParameters.Key<String>("sort")

/** A sort button tapped: remember the mode for this widget instance and redraw it. */
class SetSortAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mode = CashbackWidgetSort.parse(parameters[SortParam])
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { it.toMutablePreferences().apply { this[SORT_KEY] = mode.name } }
        CashbackWidget().update(context, glanceId)
    }
}

class CashbackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CashbackWidget()
}
