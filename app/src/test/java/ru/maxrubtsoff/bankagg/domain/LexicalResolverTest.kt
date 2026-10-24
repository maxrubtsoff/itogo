package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.domain.model.LexicalResolver
import ru.maxrubtsoff.bankagg.domain.model.Resolution

class LexicalResolverTest {

    private fun assertGroup(text: String, key: String, name: String) {
        val r = LexicalResolver.resolve(text)
        assertEquals(text, Resolution.Kind.GROUP, r.kind)
        assertEquals(text, key, r.groupKey)
        assertEquals(text, name, r.displayName)
    }

    @Test fun exactSynonym() = assertGroup("Медицинские услуги", "health", "Здоровье")

    @Test fun groupNameItself() = assertGroup("Здоровье", "health", "Здоровье")

    @Test fun caseAndYo() = assertGroup("МЕДИЦИНА", "health", "Здоровье")

    @Test fun phraseSplit() = assertGroup("Связь, интернет и ТВ", "mobile", "Связь и интернет")

    @Test fun phraseSplitAnd() = assertGroup("Здоровье и медицина", "health", "Здоровье")

    @Test fun typoStillMatches() {
        assertGroup("Супермаркегы", "groceries", "Супермаркеты")
        assertGroup("Здоровъе", "health", "Здоровье")
    }

    @Test fun shortWordTypo() {
        assertGroup("Аптекы", "pharmacy", "Аптеки")
        assertGroup("Спорг", "sport", "Спорт")
    }

    @Test fun latinLookAlikeLetters() {
        assertGroup("Aптеки", "pharmacy", "Аптеки")   // Latin A
        assertGroup("Taкси", "taxi", "Такси")         // Latin T, a
        assertGroup("A3C", "fuel", "АЗС")             // all Latin + digit 3
    }

    @Test fun fourLetterWordsAreNotFuzzy() {
        // "Кофе" must not become "Кафе" by one letter — too risky for short words.
        assertEquals(Resolution.Kind.AS_IS, LexicalResolver.resolve("Кофр").kind)
    }

    @Test fun latinBrandStaysAsIs() =
        assertEquals(Resolution.Kind.AS_IS, LexicalResolver.resolve("Lamoda").kind)

    @Test fun unknownStaysAsIs() {
        val r = LexicalResolver.resolve("Золотая Рыбка")
        assertEquals(Resolution.Kind.AS_IS, r.kind)
        assertEquals("Золотая Рыбка", r.displayName)
        assertEquals(CategoryGroups.OTHER, r.groupKey)
    }

    @Test fun knownShopKeepsItsNameAndBorrowsTheIcon() {
        val lamoda = LexicalResolver.resolve("Lamoda")
        assertEquals(Resolution.Kind.AS_IS, lamoda.kind)
        assertEquals("Lamoda", lamoda.displayName)
        assertEquals("clothing", lamoda.groupKey)
        assertEquals("home", LexicalResolver.resolve("Hoff").groupKey)
        assertEquals("travel", LexicalResolver.resolve("СберТревел").groupKey)
    }

    @Test fun newGroups_artAutoAndActiveRest() {
        assertEquals("art", LexicalResolver.resolve("Искусство").groupKey)
        assertEquals("auto", LexicalResolver.resolve("Автоуслуги").groupKey)
        assertEquals("sport", LexicalResolver.resolve("Активный отдых").groupKey)
        assertEquals("travel", LexicalResolver.resolve("Отели").groupKey)
    }

    @Test fun regroup_oldOtherRowsGetTheirGroup() {
        assertEquals("art", CategoryGroups.regroup("Искусство", CategoryGroups.OTHER))
        assertEquals("home", CategoryGroups.regroup("Hoff", CategoryGroups.OTHER))
        assertEquals("auto", CategoryGroups.regroup("Автоуслуги", CategoryGroups.OTHER))
        assertEquals(CategoryGroups.OTHER, CategoryGroups.regroup("Ной", CategoryGroups.OTHER))
        // A row that already has a group keeps it.
        assertEquals("entertainment", CategoryGroups.regroup("Искусство", "entertainment"))
    }

    @Test fun cleanWording_onlyCyrillic() {
        assertTrue(LexicalResolver.isCleanWording("Медицинские услуги"))
        assertTrue(LexicalResolver.isCleanWording("Связь, интернет и ТВ"))
        assertFalse(LexicalResolver.isCleanWording("A3C"))
        assertFalse(LexicalResolver.isCleanWording("Аптеки 5"))
    }

    @Test fun garbageIsEmpty() {
        assertEquals(Resolution.Kind.EMPTY, LexicalResolver.resolve(" — ").kind)
        assertEquals(Resolution.Kind.EMPTY, LexicalResolver.resolve("до").kind)
    }

    @Test fun properNames() {
        assertTrue(LexicalResolver.looksLikeProperName("Lamoda"))
        assertTrue(LexicalResolver.looksLikeProperName("Детский Мир"))
        assertTrue(LexicalResolver.looksLikeProperName("«Пятёрочка»"))
        assertFalse(LexicalResolver.looksLikeProperName("Медицинские услуги"))
    }

    @Test fun everyGroupHasSynonymsAndUniqueKey() {
        assertEquals(CategoryGroups.all.size, CategoryGroups.all.map { it.key }.toSet().size)
        CategoryGroups.all
            .filter { it.key !in setOf("base", "all", "other") }
            .forEach { assertTrue(it.key, it.synonyms.size >= 3) }
    }

    @Test fun filterByQuery() =
        assertEquals(listOf("health"), CategoryGroups.filter("здор").map { it.key })

    @Test fun unknownKeyFallsBackToOther() =
        assertEquals(CategoryGroups.OTHER, CategoryGroups.byKey("nope").key)
}
