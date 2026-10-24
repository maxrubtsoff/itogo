package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.KopeckLook
import ru.maxrubtsoff.bankagg.domain.model.MoneyMask

/** U-56: kopecks by the size of the amount; B-44: the dust covers exactly the amounts. */
class KopeckLookTest {

    private val nb = ' '

    @Test fun largeStepsSetKopecksSmallerAndDimmer() {
        // Amounts.Hero 34, Amounts.Large 22.
        for (sp in listOf(34f, 22f)) {
            val look = KopeckLook.forSize(sp)
            assertEquals(KopeckLook.SMALLER, look)
            assertTrue(look.scale < 1f && look.alpha < 1f)
        }
    }

    @Test fun smallStepsKeepTheSizeOnlyDimmer() {
        // Amounts.Row 16, Amounts.Small 13, body text 14 and 12.
        for (sp in listOf(16f, 13f, 14f, 12f)) {
            val look = KopeckLook.forSize(sp)
            assertEquals(KopeckLook.SAME, look)
            assertEquals(1f, look.scale)
            assertTrue(look.alpha in 0.7f..0.8f)
        }
    }

    @Test fun smallKopecksAreLessDimThanLarge() {
        assertTrue(KopeckLook.SAME.alpha > KopeckLook.SMALLER.alpha)
    }

    @Test fun unknownSizeCountsAsSmall() {
        assertEquals(KopeckLook.SAME, KopeckLook.forSize(Float.NaN))
    }

    @Test fun thresholdSitsBetweenRowAndLarge() {
        assertTrue(KopeckLook.LARGE_FROM_SP > 16f && KopeckLook.LARGE_FROM_SP < 22f)
    }

    @Test fun dustCoversEveryAmountAndNothingElse() {
        val text = "Доход +1${nb}713,92${nb}₽ за 3 мес."
        val parts = MoneyMask.ranges(text).map { text.substring(it) }
        assertEquals(listOf("+1${nb}713,92${nb}₽"), parts)
    }

    @Test fun dustOfSeveralAmounts() {
        val text = "1${nb}000${nb}₽ из 5${nb}000${nb}₽"
        assertEquals(listOf("1${nb}000${nb}₽", "5${nb}000${nb}₽"), MoneyMask.ranges(text).map { text.substring(it) })
    }

    @Test fun hideAllCoversTheWholeFigure() {
        assertEquals(listOf(0..7), MoneyMask.ranges("2,80${nb}млн", all = true))
        assertEquals(emptyList<IntRange>(), MoneyMask.ranges("", all = true))
    }

    @Test fun noMoneyNoDust() {
        assertEquals(emptyList<IntRange>(), MoneyMask.ranges("40 дн., 5%"))
    }
}
