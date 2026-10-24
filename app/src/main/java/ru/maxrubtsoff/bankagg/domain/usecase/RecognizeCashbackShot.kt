package ru.maxrubtsoff.bankagg.domain.usecase

import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.CashbackParser
import ru.maxrubtsoff.bankagg.domain.model.CashbackShot
import ru.maxrubtsoff.bankagg.domain.model.CategoryResolver
import ru.maxrubtsoff.bankagg.domain.model.LexicalResolver
import ru.maxrubtsoff.bankagg.domain.model.Resolution
import ru.maxrubtsoff.bankagg.domain.model.ScreenshotFileName
import ru.maxrubtsoff.bankagg.domain.model.ShotLine
import ru.maxrubtsoff.bankagg.domain.model.ShotLines
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.repository.OcrEngine
import java.time.LocalDate

/**
 * Runs OCR over a saved shot once and turns the text into draft cashback rows.
 *
 * Orchestration only — parsing ([CashbackParser]), naming ([CategoryResolver])
 * and de-duplication ([ShotLines]) are pure and tested separately. The bank
 * comes from the file name first (Samsung/MIUI append the app), then the text.
 * When OCR is unavailable or reads nothing usable, the status is
 * [ShotStatus.NEEDS_INPUT]/[ShotStatus.FAILED] so the UI falls back to manual entry.
 */
class RecognizeCashbackShot(
    private val ocr: OcrEngine,
    private val resolver: CategoryResolver,
    private val today: () -> LocalDate = { LocalDate.now() }
) {
    data class Draft(
        val status: ShotStatus,
        val brand: BankBrand?,
        val lines: List<ShotLine>,
        /** The month the screen names ("Категории на октябрь"), if it names one. */
        val monthKey: String? = null
    )

    suspend operator fun invoke(shot: CashbackShot): Draft {
        val fileBank = ScreenshotFileName.guessBank(shot.fileName)
        if (!ocr.isReady()) return Draft(ShotStatus.NEEDS_INPUT, fileBank, emptyList())

        val text = ocr.recognize(shot.imagePath)
        if (text.isNullOrBlank()) return Draft(ShotStatus.FAILED, fileBank, emptyList())

        val parsed = CashbackParser.parse(text, brandHint = fileBank)
        val lines = ShotLines.dedupe(parsed.lines.map { l ->
            val r = resolver.resolve(l.text)
            // The bank's own wording stays; a group match only sets the icon.
            // OCR-garbled text ("A3C") falls back to the group's name.
            val name = when (r.kind) {
                Resolution.Kind.EMPTY -> ""
                Resolution.Kind.GROUP -> l.text.trim().takeIf(LexicalResolver::isCleanWording) ?: r.displayName
                Resolution.Kind.AS_IS -> r.displayName
            }
            ShotLine(text = name, groupKey = r.groupKey, percent = l.percent, sourceText = l.text)
        })
        val status = when {
            lines.isEmpty() -> ShotStatus.FAILED
            parsed.brand == null -> ShotStatus.NEEDS_INPUT   // lines read, bank unknown
            else -> ShotStatus.RECOGNIZED
        }
        return Draft(status, parsed.brand, lines, CashbackMonth.guessKey(CashbackParser.monthsMentioned(text), today()))
    }
}
