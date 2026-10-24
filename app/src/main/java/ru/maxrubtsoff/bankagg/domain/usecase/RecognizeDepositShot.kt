package ru.maxrubtsoff.bankagg.domain.usecase

import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.DepositDraft
import ru.maxrubtsoff.bankagg.domain.model.DepositParser
import ru.maxrubtsoff.bankagg.domain.model.DepositShot
import ru.maxrubtsoff.bankagg.domain.model.ScreenshotFileName
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.repository.OcrEngine

/**
 * Runs OCR over a saved deposit shot once and turns the text into a [DepositDraft].
 *
 * Orchestration only — parsing ([DepositParser]) is pure and tested separately. The
 * bank comes from the file name first (Samsung/MIUI append the app), then the text.
 * When OCR is unavailable or reads nothing usable, the status is
 * [ShotStatus.NEEDS_INPUT]/[ShotStatus.FAILED] so the UI falls back to manual entry.
 */
class RecognizeDepositShot(
    private val ocr: OcrEngine
) {
    data class Draft(
        val status: ShotStatus,
        val brand: BankBrand?,
        val fields: DepositDraft
    )

    suspend operator fun invoke(shot: DepositShot): Draft {
        val fileBank = ScreenshotFileName.guessBank(shot.fileName)
        if (!ocr.isReady()) return Draft(ShotStatus.NEEDS_INPUT, fileBank, DepositDraft())

        val text = ocr.recognizeThorough(shot.imagePath)
        if (text.isNullOrBlank()) return Draft(ShotStatus.FAILED, fileBank, DepositDraft())

        val parsed = DepositParser.parse(text, brandHint = fileBank)
        val d = parsed.draft
        val readAny = d.principal != null || d.exitAmount != null || d.rateBp != null ||
            d.startDate != null || d.endDate != null
        val status = when {
            !readAny -> ShotStatus.FAILED
            parsed.brand == null || !d.isComplete -> ShotStatus.NEEDS_INPUT
            else -> ShotStatus.RECOGNIZED
        }
        return Draft(status, parsed.brand, d)
    }
}
