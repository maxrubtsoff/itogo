package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BatchLayout

class BatchLayoutTest {
    @Test fun roundTrip() {
        val g = listOf(listOf("a", "b"), listOf("c"))
        assertEquals(g, BatchLayout.decode(BatchLayout.encode(g)))
        assertEquals(emptyList<List<String>>(), BatchLayout.decode(""))
    }

    @Test fun reconcileDropsGoneAndAddsNew() {
        val g = listOf(listOf("a", "b"), listOf("c"))
        assertEquals(listOf(listOf("a"), listOf("d")), BatchLayout.reconcile(g, listOf("a", "d")))
    }

    @Test fun moveBetweenGroupsAndOut() {
        val g = listOf(listOf("a", "b"), listOf("c"))
        assertEquals(listOf(listOf("a"), listOf("c", "b")), BatchLayout.move(g, "b", 1))
        assertEquals(listOf(listOf("a", "b"), listOf("c")), BatchLayout.move(listOf(listOf("a", "b", "c")), "c", null))
        // Moving the only shot of a group removes the empty group.
        assertEquals(listOf(listOf("a", "b", "c")), BatchLayout.move(g, "c", 0))
    }
}
