package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceLabelTest {
    private val flat = Plot(floatArrayOf(10f, 110f, 210f), floatArrayOf(100f, 100f, 100f))

    @Test fun flatLineGetsPlateAboveOrBelowNotOnLine() {
        val r = placeLabel(flat, 1, Size(60f, 20f), listOf(Offset(110f, 100f)), 220f, 200f, 1f)
        assertNotNull(r)
        assertTrue(r!!.bottom < 100f || r.top > 100f)
    }

    @Test fun plateStaysInsideTheCanvas() {
        val r = placeLabel(flat, 0, Size(60f, 20f), emptyList(), 220f, 200f, 1f)!!
        assertTrue(r.left >= 0f && r.right <= 220f && r.top >= 0f && r.bottom <= 200f)
    }

    @Test fun noRoomMeansNoPlate() {
        assertNull(placeLabel(flat, 1, Size(60f, 20f), emptyList(), 220f, 30f, 1f))
    }

    @Test fun avoidsAnotherDot() {
        val other = Offset(110f, 75f)
        val r = placeLabel(flat, 1, Size(60f, 20f), listOf(Offset(110f, 100f), other), 220f, 200f, 1f)!!
        assertTrue(r.top > 100f)
    }
}
