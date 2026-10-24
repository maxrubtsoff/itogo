package ru.maxrubtsoff.bankagg.domain.model

/**
 * A photo (screenshot) the user uploaded to extract cashback from. The image
 * itself lives as a file in app storage; [imagePath] points at it. A shot
 * belongs to one month and (once known) one bank; its [status] drives the card
 * shown in the cashback tab.
 *
 * The shot is recognised once: the resulting draft [lines] (and the bank) are
 * stored with it, so opening it again shows that draft instead of re-running
 * OCR. Once the user saves, the rows become [CashbackItem]s and the shot is
 * deleted. [fileName] is the picker's original name (may name the bank's app).
 */
data class CashbackShot(
    val id: String,
    val monthKey: String,          // "YYYY-MM"
    val imagePath: String,         // absolute path to the stored image file
    val brand: BankBrand? = null,  // null until recognised/confirmed
    val status: ShotStatus = ShotStatus.PROCESSING,
    val createdAt: Long = 0,
    val fileName: String? = null,
    val lines: List<ShotLine> = emptyList()
)

/**
 * One recognised row of a shot: display [text] (group name or verbatim; "" if
 * unreadable), its [groupKey], the [percent] (null if unreadable) and the OCR
 * line text it came from (without the percent) — the picker starts its search from [sourceText].
 */
data class ShotLine(
    val text: String,
    val groupKey: String,
    val percent: Double?,
    val sourceText: String
)

object ShotLines {
    /**
     * One row per offer (by [CashbackIdentity]), highest percent wins, first
     * appearance keeps its place. Unreadable rows (blank text) are all kept.
     */
    fun dedupe(lines: List<ShotLine>): List<ShotLine> {
        val out = ArrayList<ShotLine>()
        val index = HashMap<String, Int>()
        for (l in lines) {
            if (l.text.isBlank()) { out += l; continue }
            val key = CashbackIdentity.key(l.groupKey, l.text)
            val at = index[key]
            if (at == null) { index[key] = out.size; out += l }
            else if ((l.percent ?: 0.0) > (out[at].percent ?: 0.0)) out[at] = l
        }
        return out
    }
}

/**
 * Lifecycle of an uploaded shot. The UI colours the status pill by this:
 * - [PROCESSING]  — OCR is running.
 * - [RECOGNIZED]  — bank + at least one line were read; user should verify/save.
 * - [NEEDS_INPUT] — partially read (e.g. bank unknown); user must complete it.
 * - [FAILED]      — nothing usable was read; user fills it in by hand.
 * A saved shot is deleted, so there is no "saved" state.
 */
enum class ShotStatus { PROCESSING, RECOGNIZED, NEEDS_INPUT, FAILED }
