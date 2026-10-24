package ru.maxrubtsoff.bankagg.domain.usecase

import ru.maxrubtsoff.bankagg.domain.model.ShotKind
import ru.maxrubtsoff.bankagg.domain.repository.OcrEngine

/**
 * Screenshots shared into the app: reads up to [LOOK_AT] of them until one says
 * clearly whether it is cashback or a deposit ([ShotKind.guess]). Null — ask.
 */
class ClassifyScreenshot(private val ocr: OcrEngine) {

    suspend operator fun invoke(imagePaths: List<String>): ShotKind? {
        if (!ocr.isReady()) return null
        for (path in imagePaths.take(LOOK_AT)) {
            val text = ocr.recognize(path) ?: continue
            ShotKind.guess(text)?.let { return it }
        }
        return null
    }

    private companion object {
        const val LOOK_AT = 3
    }
}
