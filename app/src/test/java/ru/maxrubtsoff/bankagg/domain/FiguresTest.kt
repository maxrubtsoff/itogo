package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Figures

class FiguresTest {
    @Test fun percentIsFigure() {
        listOf("18 %", "18,5%", "+3").forEach { assertTrue(it, Figures.isFigure(it)) }
    }

    @Test fun moneyIsFigure() {
        listOf("1 200,50 ₽", "4,27 млн ₽", "100 USD").forEach { assertTrue(it, Figures.isFigure(it)) }
    }

    @Test fun termIsFigure() {
        listOf("12 мес", "3 года", "40 дн.").forEach { assertTrue(it, Figures.isFigure(it)) }
    }

    @Test fun dateIsNotFigure() {
        listOf("3 мая", "12 марта 2026").forEach { assertFalse(it, Figures.isFigure(it)) }
    }

    @Test fun wordsAreNotFigures() {
        listOf("Ежемесячно", "до 18 %", "Т-Банк").forEach { assertFalse(it, Figures.isFigure(it)) }
    }

    @Test fun emptyIsNotFigure() {
        listOf("", "—").forEach { assertFalse(it, Figures.isFigure(it)) }
    }
}
