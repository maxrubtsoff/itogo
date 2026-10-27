package ru.maxrubtsoff.bankagg.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The «Слиток» design system holds (U-40, F-31): screens are built from
 * `ui/common` and `ui/theme` only. A stock control, a colour or a hand-set
 * figure font outside them fails the build. Exceptions are listed by file,
 * each with its reason.
 */
class DesignSystemGuardTest {

    private val root = File("src/main/java/ru/maxrubtsoff/bankagg/ui")

    /** Files that keep a pattern on purpose, and why. */
    private val allowed = mapOf(
        // The compact percent box of a cashback row is part of the cashback-form pieces shared by three screens.
        "BasicTextField(" to setOf("CashbackForm.kt"),
        // The 3D pictures of the parts are drawings with their own shading, not interface colours.
        "Color(0x" to setOf("PartArt.kt")
    )

    private val forbidden = listOf(
        "Card(" to Regex("""(?<![A-Za-z])Card\("""),
        "AlertDialog(" to Regex("""(?<![A-Za-z])AlertDialog\("""),
        "DatePickerDialog(" to Regex("""(?<![A-Za-z])DatePickerDialog\("""),
        // U-72: snackbars are swipeable — screens use SlitokSnackbarHost.
        "SnackbarHost(" to Regex("""(?<![A-Za-z])SnackbarHost\("""),
        "Switch(" to Regex("""(?<![A-Za-z])Switch\("""),
        "TextButton(" to Regex("""(?<![A-Za-z])TextButton\("""),
        // U-57: every title sits at one height — only ScreenHeader draws the top of a screen.
        "TopAppBar(" to Regex("""(?<![A-Za-z])(CenterAligned|Medium|Large)?TopAppBar\("""),
        "BasicTextField(" to Regex("""(?<![A-Za-z.])BasicTextField\("""),
        "Color(0x" to Regex("""Color\(0x"""),
        // U-73: fonts are set in ui/theme only (also catches .copy(fontFamily = full.package.Name)).
        "fontFamily =" to Regex("""fontFamily\s*="""),
        // U-73: a big sum takes the Amounts scale, not a text style.
        "sum in a text style" to Regex("""(MoneyText|AmountText)\([^)]*style = MaterialTheme\.typography\.(display|headline|title)""")
    )

    @Test fun screensUseTheDesignSystem() {
        assertTrue("ui sources not found from ${File(".").absolutePath}", root.isDirectory)
        val found = root.walk()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.parentFile.name == "common" || it.parentFile.name == "theme" }
            .flatMap { file ->
                val src = file.readText()
                forbidden.filter { (name, rx) -> rx.containsMatchIn(src) && file.name !in allowed[name].orEmpty() }
                    .map { (name, _) -> "${file.name}: $name" }
            }
            .toList()
        assertTrue("Outside the design system (use ui/common, ui/theme): $found", found.isEmpty())
    }

    /** A one-line text never loses its end silently (U-40/A15, B-33): «…» instead. */
    @Test fun oneLineTextsEndWithEllipsis() {
        val call = Regex("""(?<![A-Za-z.])Text\(""")
        val found = root.walk()
            .filter { it.isFile && it.extension == "kt" && it.name != "CashbackWidget.kt" }
            .flatMap { file ->
                val src = file.readText()
                call.findAll(src).mapNotNull { m ->
                    val args = arguments(src, m.range.last + 1)
                    if ("maxLines = 1" in args && "overflow" !in args && "softWrap = false" !in args)
                        "${file.name}:${src.substring(0, m.range.first).count { it == '\n' } + 1}" else null
                }
            }
            .toList()
        assertTrue("One-line texts cut without «…»: $found", found.isEmpty())
    }

    private fun arguments(src: String, from: Int): String {
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
}
