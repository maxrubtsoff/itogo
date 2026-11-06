package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.OpTime
import ru.maxrubtsoff.bankagg.domain.model.OperationTime
import java.time.LocalDateTime
import java.time.ZoneId

class OperationTimeTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test fun noTimeUsesArrival() {
        assertEquals(5L, OperationTime.resolve(null, 5L, zone))
    }
    @Test fun dateAndTime() {
        assertEquals(ms(2026, 11, 3, 17, 39),
            OperationTime.resolve(OpTime(3, 11, null, 17, 39), ms(2026, 11, 3, 17, 43), zone))
    }
    @Test fun timeOnlyBeforeMidnight() {
        assertEquals(ms(2026, 11, 2, 23, 58),
            OperationTime.resolve(OpTime(null, null, null, 23, 58), ms(2026, 11, 3, 0, 1), zone))
    }
    @Test fun dateWithoutYearAcrossNewYear() {
        assertEquals(ms(2026, 12, 31, 23, 59),
            OperationTime.resolve(OpTime(31, 12, null, 23, 59), ms(2027, 1, 1, 0, 2), zone))
    }
    @Test fun farFromArrivalFallsBack() {
        val arrival = ms(2026, 11, 10, 12, 0)
        assertEquals(arrival, OperationTime.resolve(OpTime(3, 11, null, 17, 39), arrival, zone))
    }
    @Test fun futureTimeOnlyMeansYesterday() {
        val arrival = ms(2026, 11, 3, 12, 0)
        assertEquals(ms(2026, 11, 2, 12, 30),
            OperationTime.resolve(OpTime(null, null, null, 12, 30), arrival, zone))
    }
    @Test fun futureDateFallsBack() {
        val arrival = ms(2026, 11, 3, 12, 0)
        assertEquals(arrival, OperationTime.resolve(OpTime(3, 11, 2026, 12, 30), arrival, zone))
    }
}
