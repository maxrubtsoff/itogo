package ru.maxrubtsoff.bankagg.data.embed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Runs the rubert-tiny2 ONNX model: batch → `last_hidden_state[:, 0, :]` (the
 * CLS vector the model is trained to pool) → L2-normalised. Uses only the ONNX
 * Runtime API, no Android, so the JVM tests run the real model too.
 */
class OnnxEmbedderCore(modelBytes: ByteArray, private val tokenizer: WordPieceTokenizer) : AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelBytes, OrtSession.SessionOptions())
    private val inputNames = session.inputNames

    fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val encoded = texts.map { tokenizer.encode(it) }
        val len = encoded.maxOf { it.size }
        val n = texts.size
        val ids = LongArray(n * len)
        val mask = LongArray(n * len)
        encoded.forEachIndexed { row, e ->
            e.forEachIndexed { col, id -> ids[row * len + col] = id.toLong(); mask[row * len + col] = 1 }
        }
        val shape = longArrayOf(n.toLong(), len.toLong())
        val tensors = mapOf(
            "input_ids" to ids,
            "attention_mask" to mask,
            "token_type_ids" to LongArray(n * len)
        ).filterKeys { it in inputNames }
            .mapValues { (_, data) -> OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape) }
        try {
            session.run(tensors).use { result ->
                @Suppress("UNCHECKED_CAST")
                val hidden = result[0].value as Array<Array<FloatArray>>
                return hidden.map { normalize(it[0].copyOf()) }
            }
        } finally {
            tensors.values.forEach { it.close() }
        }
    }

    private fun normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) for (i in v.indices) v[i] /= norm
        return v
    }

    override fun close() = session.close()
}
