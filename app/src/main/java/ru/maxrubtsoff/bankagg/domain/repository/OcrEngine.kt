package ru.maxrubtsoff.bankagg.domain.repository

/**
 * A port for optical character recognition. The domain only needs text out of an
 * image; how that text is produced (Tesseract, on-device, offline) is a data-layer
 * concern, kept behind this interface so the domain stays Android-free and the use
 * case is testable with a fake.
 *
 * Implementations must be offline and free (project rule). If the language model
 * is unavailable, [isReady] returns false and [recognize] returns null — the
 * caller then falls back to manual entry rather than failing.
 */
interface OcrEngine {
    /** Whether the engine can run now (model present and initialised). */
    suspend fun isReady(): Boolean

    /**
     * Recognise text from an image file at [imagePath]. Returns the raw text, or
     * null if the engine isn't ready or recognition failed. Never throws for the
     * ordinary "no model / bad image" cases.
     */
    suspend fun recognize(imagePath: String): String?

    /**
     * Like [recognize], but also reads the image at other scales and returns all
     * passes, one after another: OCR misses display-size figures ("1 300 000 ₽" on a
     * banner) at one scale and fine print at another. Slower; for dense screens.
     */
    suspend fun recognizeThorough(imagePath: String): String? = recognize(imagePath)
}
