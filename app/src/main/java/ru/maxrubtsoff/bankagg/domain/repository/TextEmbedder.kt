package ru.maxrubtsoff.bankagg.domain.repository

/**
 * Port for the offline text-similarity model. Turns short texts into
 * L2-normalised vectors, so a dot product is the cosine similarity. The model
 * itself (ONNX, on-device) is a data-layer detail; tests use a fake.
 */
interface TextEmbedder {
    /** One vector per text, or null if the model is unavailable. */
    suspend fun embed(texts: List<String>): List<FloatArray>?
}
