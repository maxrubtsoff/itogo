package ru.maxrubtsoff.bankagg.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hidden amounts (F-11, B-11): a formatted sum handed straight to a plain
 * `Text(` skips the dust. Every sum on screen goes through MoneyText/AmountText.
 */
class HiddenAmountsLeakTest {

    /** Screens where the user is typing the amount right now — nothing to hide there. */
    private val editing = setOf("CashSheet.kt", "FxOperationSheet.kt", "DepositBatchScreen.kt")

    private val plainText = Regex("""(?<![A-Za-z])Text\(""")
    private val formatted = Regex("""money\(|signedMoney\(|signedRub\(|\.format\(\)|MoneyCompact\.""")

    @Test fun noFormattedSumInPlainText() {
        val root = File("src/main/java/ru/maxrubtsoff/bankagg/ui")
        assertTrue("ui sources not found from ${File(".").absolutePath}", root.isDirectory)
        val leaks = root.walk()
            .filter { it.isFile && it.extension == "kt" && it.name !in editing }
            .flatMap { file ->
                val src = file.readText()
                plainText.findAll(src).mapNotNull { m ->
                    // The first argument: up to the first comma or closing paren at depth 0.
                    val arg = firstArgument(src, m.range.last + 1)
                    if (formatted.containsMatchIn(arg)) "${file.name}:${src.lineAt(m.range.first)}" else null
                }
            }
            .toList()
        assertTrue("Sums shown without hiding: $leaks", leaks.isEmpty())
    }

    /**
     * B-42: a sum passed to a small helper (`Fact("В год", money)`) that draws
     * its value with a plain `Text(value` is a leak too.
     */
    @Test fun noFormattedSumThroughPlainHelper() {
        val root = File("src/main/java/ru/maxrubtsoff/bankagg/ui")
        val helper = Regex("""fun (\w+)\(([^)]*\bvalue: String\??[^)]*)\)""")
        val leaks = root.walk()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val src = file.readText()
                helper.findAll(src)
                    .filter { m ->
                        val body = src.substring(m.range.last, src.indexOf("\n}\n", m.range.last).takeIf { it > 0 } ?: src.length)
                        plainValue.containsMatchIn(body)
                    }
                    .flatMap { m ->
                        val name = m.groupValues[1]
                        Regex("""(?<![A-Za-z.])$name\(""").findAll(src)
                            .filter { call -> call.range.first != m.range.first + 4 }
                            .mapNotNull { call ->
                                val args = allArguments(src, call.range.last + 1)
                                if (formatted.containsMatchIn(args)) "${file.name}:${src.lineAt(call.range.first)}" else null
                            }
                    }
            }
            .toList()
        assertTrue("Sums shown without hiding through a helper: $leaks", leaks.isEmpty())
    }

    private val plainValue = Regex("""(?<![A-Za-z])Text\(\s*value\b""")

    private fun allArguments(src: String, from: Int): String {
        var depth = 0
        var i = from
        while (i < src.length) {
            when (src[i]) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> if (depth == 0) break else depth--
            }
            i++
        }
        return src.substring(from, i)
    }

    private fun firstArgument(src: String, from: Int): String {
        var depth = 0
        var i = from
        while (i < src.length) {
            when (src[i]) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> if (depth == 0) break else depth--
                ',' -> if (depth == 0) break
            }
            i++
        }
        return src.substring(from, i)
    }

    private fun String.lineAt(index: Int): Int = substring(0, index).count { it == '\n' } + 1
}
