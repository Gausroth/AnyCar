package dev.timothy.anycar

import dev.timothy.anycar.capability.KnownVehicleNotes
import dev.timothy.anycar.data.CheckStatus
import dev.timothy.anycar.data.ProbeKeys
import dev.timothy.anycar.data.VehicleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownVehicleNotesTest {
    private val taurus = VehicleProfile(btMac = "AA:BB:CC:DD:EE:FF", btName = "SYNC", make = "Ford", year = 2009)

    @Test fun confirmedSyncGen1PreSeedsWheelButtonFail() {
        val v = KnownVehicleNotes.apply(taurus.copy(makeConfirmed = true), now = 1)
        assertEquals(CheckStatus.FAIL, v.capabilityCheckResults[ProbeKeys.WHEEL_BUTTON]?.status)
        assertFalse(v.wheelButtonSupported)
    }

    @Test fun unconfirmedGuessIsNeverUsed() {
        assertTrue(KnownVehicleNotes.apply(taurus).capabilityCheckResults.isEmpty())
    }

    @Test fun otherYearsDontMatch() {
        assertTrue(KnownVehicleNotes.apply(taurus.copy(makeConfirmed = true, year = 2012)).capabilityCheckResults.isEmpty())
    }
}
