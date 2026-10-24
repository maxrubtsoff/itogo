package ru.maxrubtsoff.bankagg.data

import java.io.File

/** The embedder files shipped in the APK, read directly by JVM tests. */
object TestAssets {
    private val dir: File = listOf("src/main/assets/embedder", "app/src/main/assets/embedder")
        .map(::File).first { it.isDirectory }

    val vocab: File get() = File(dir, "vocab.txt")
    val model: File get() = File(dir, "model.onnx")
}
