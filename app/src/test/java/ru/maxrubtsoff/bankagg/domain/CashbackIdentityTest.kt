package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackIdentity
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.identityKey

class CashbackIdentityTest {

    @Test fun groupNamedRowKeyIsGroup() =
        assertEquals("health", CashbackIdentity.key("health", "Здоровье"))

    @Test fun groupNameCaseInsensitive() =
        assertEquals("health", CashbackIdentity.key("health", "ЗДОРОВЬЕ"))

    @Test fun asIsRowKeyIncludesText() =
        assertEquals("clothing|lamoda", CashbackIdentity.key("clothing", "Lamoda"))

    @Test fun brandAndGroupDiffer() =
        assertNotEquals(CashbackIdentity.key("clothing", "Lamoda"), CashbackIdentity.key("clothing", "Одежда"))

    @Test fun itemExtension() {
        val item = CashbackItem("1", "2026-10", BankBrand.SBER, "Lamoda", 10.0, groupKey = "clothing")
        assertEquals("clothing|lamoda", item.identityKey)
    }
}
