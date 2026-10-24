package ru.maxrubtsoff.bankagg.data.embed

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.domain.repository.TextEmbedder

/**
 * [TextEmbedder] backed by the model bundled in `assets/embedder/` — offline,
 * nothing leaves the device. Loads lazily on first use. If loading or a run
 * fails it reports "unavailable" (null) so callers fall back to dictionary
 * matching, and tries again on a later call — at most once a minute, so one
 * hiccup doesn't switch the model off for the whole session.
 */
class OnnxTextEmbedder(private val context: Context) : TextEmbedder {

    private val mutex = Mutex()
    private var core: OnnxEmbedderCore? = null
    private var failedAt: Long? = null

    override suspend fun embed(texts: List<String>): List<FloatArray>? = withContext(Dispatchers.Default) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            failedAt?.let { if (now - it < RETRY_AFTER_MS) return@withLock null }
            try {
                val c = core ?: load().also { core = it }
                c.embed(texts).also { failedAt = null }
            } catch (t: Throwable) {
                failedAt = now
                null
            }
        }
    }

    private fun load(): OnnxEmbedderCore {
        val assets = context.assets
        val tokenizer = assets.open("embedder/vocab.txt").bufferedReader().useLines {
            WordPieceTokenizer.fromVocabLines(it)
        }
        val bytes = assets.open("embedder/model.onnx").use { it.readBytes() }
        return OnnxEmbedderCore(bytes, tokenizer)
    }

    private companion object {
        const val RETRY_AFTER_MS = 60_000L
    }
}
