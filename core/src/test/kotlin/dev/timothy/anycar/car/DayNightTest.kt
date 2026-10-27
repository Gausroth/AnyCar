package dev.timothy.anycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayNightTest {
    @Test fun autoFollowsTheClock() {
        assertTrue(DayNight.isNight("auto", 20))
        assertTrue(DayNight.isNight("auto", 0))
        assertTrue(DayNight.isNight("auto", 5))
        assertFalse(DayNight.isNight("auto", 6))
        assertFalse(DayNight.isNight("auto", 19))
    }

    @Test fun manualOverridesAuto() {
        assertFalse(DayNight.isNight("day", 23))
        assertTrue(DayNight.isNight("night", 12))
    }

    @Test fun toggleFlipsWhatYouSeeFirst() {
        assertEquals("night", DayNight.next("auto", 12))
        assertEquals("day", DayNight.next("auto", 22))
        assertEquals("night", DayNight.next("day", 12))
        assertEquals("auto", DayNight.next("night", 12))
    }

    @Test fun obdNames() {
        listOf("OBDII", "Veepeak OBDCheck", "vLinker MC+", "V-LINK", "BAFX Products", "ELM327").forEach { assertTrue(it, ObdNames.looksLikeAdapter(it)) }
        listOf("SYNC", "Pixel Buds", null).forEach { assertFalse(ObdNames.looksLikeAdapter(it)) }
    }
}
