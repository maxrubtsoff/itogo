package ru.maxrubtsoff.bankagg.domain.model

/**
 * Hiding amounts (F-11): which parts of a text are money, and when the phone
 * has been turned face down. Pure, so the UI only draws the dust.
 */
object MoneyMask {

    /** A piece of text and whether it is money to hide. */
    data class Part(val text: String, val money: Boolean)

    private const val SPACE = "[   ]"

    /**
     * An amount: optional sign, digits in groups, optional kopecks, optional
     * "млн"/"млрд", then a currency sign or code. Without a sign/code or
     * млн/млрд a number is not money ("40 дн.", "3 вклада", "5%").
     */
    private val AMOUNT = Regex(
        "[+\\-−]?\\d{1,3}(?:$SPACE\\d{3})*(?:,\\d+)?" +
            "(?:(?:$SPACE(?:млн|млрд))(?:$SPACE?(?:₽|[$€£¥₸₺₹₩₴₾]|[A-Z]{3}\\b))?" +
            "|$SPACE?(?:₽|[$€£¥₸₺₹₩₴₾]|[A-Z]{3}\\b))"
    )

    fun split(text: String): List<Part> {
        val out = ArrayList<Part>()
        var at = 0
        for (m in AMOUNT.findAll(text)) {
            if (m.range.first > at) out += Part(text.substring(at, m.range.first), false)
            out += Part(m.value, true)
            at = m.range.last + 1
        }
        if (at < text.length) out += Part(text.substring(at), false)
        return out
    }

    fun hasMoney(text: String): Boolean = AMOUNT.containsMatchIn(text)

    /**
     * What turns into dust (B-44): where every amount of [text] sits, or the
     * whole text when [all]. The dust covers exactly these characters of the
     * laid-out text, so hiding never changes its width.
     */
    fun ranges(text: String, all: Boolean = false): List<IntRange> = when {
        text.isEmpty() -> emptyList()
        all -> listOf(text.indices)
        else -> AMOUNT.findAll(text).map { it.range }.toList()
    }

    private val KOPECKS = Regex(",\\d+")
    private val MILLIONS = Regex("млн|млрд")

    /**
     * Where the kopecks sit (U-35): the comma and the digits after it in every
     * amount of [text]. Shortened sums ("4,27 млн") have no kopecks.
     */
    fun fractions(text: String): List<IntRange> =
        AMOUNT.findAll(text)
            .filterNot { MILLIONS.containsMatchIn(it.value) }
            .mapNotNull { m -> KOPECKS.find(m.value)?.let { (m.range.first + it.range.first)..(m.range.first + it.range.last) } }
            .toList()
}

/**
 * How the kopecks of an amount look (U-56), by the amount's font size: large
 * sums (`Amounts.Hero`, `Amounts.Large`) set them smaller and dimmer, small
 * ones (`Amounts.Row`, `Amounts.Small`, sums inside text) the same size and
 * only a little dimmer, so tiny kopecks don't fall apart from the rubles.
 * [scale] is the share of the font size, [alpha] — of the amount's colour.
 */
enum class KopeckLook(val scale: Float, val alpha: Float) {
    SMALLER(0.7f, 0.55f),
    SAME(1f, 0.75f);

    companion object {
        /** sp: between `Amounts.Row` (16) and `Amounts.Large` (22). */
        const val LARGE_FROM_SP = 19f

        /** [sp] of the amount as drawn on its step; unknown (NaN) counts as small. */
        fun forSize(sp: Float): KopeckLook = if (sp >= LARGE_FROM_SP) SMALLER else SAME
    }
}

/**
 * U-41: the «hide amounts» eye is there only when there is something to hide —
 * or when amounts are hidden right now, so they can always be shown again.
 */
object HideToggle {
    fun visible(hasAmounts: Boolean, hidden: Boolean): Boolean = hasAmounts || hidden
}

/**
 * "Turned face down" from gravity readings: the screen faces the floor (z well
 * below zero) and stays so for [HOLD_MS], so a quick wobble does nothing.
 * Feed it readings; it says once when the phone went face down.
 */
class FlipDetector(private val holdMs: Long = HOLD_MS) {
    private var downSince: Long? = null
    private var fired = false

    /** Returns true once per flip, when the phone has lain face down long enough. */
    fun onGravity(z: Float, atMs: Long): Boolean {
        if (z > FACE_DOWN_Z) {
            downSince = null
            fired = false
            return false
        }
        val since = downSince ?: atMs.also { downSince = it }
        if (!fired && atMs - since >= holdMs) {
            fired = true
            return true
        }
        return false
    }

    companion object {
        /** m/s²: lying screen-down reads about −9.8. */
        const val FACE_DOWN_Z = -8.0f
        const val HOLD_MS = 150L
    }
}

/** The capital parts sheet (F-12): a tap toggles a part, the last one can't be switched off. */
object CapitalPartsToggle {
    fun toggle(included: Set<CapitalPart>, part: CapitalPart): Set<CapitalPart> = when {
        part !in included -> included + part
        included.size == 1 -> included
        else -> included - part
    }
}
