package dev.timothy.anycar.capability

import dev.timothy.anycar.data.CheckResult
import dev.timothy.anycar.data.CheckStatus
import dev.timothy.anycar.data.ProbeKeys
import dev.timothy.anycar.data.VehicleProfile

/** Verified facts that pre-seed results, so the owner never runs a test that's known to fail (§7.4). */
object KnownVehicleNotes {
    fun apply(v: VehicleProfile, now: Long = System.currentTimeMillis()): VehicleProfile {
        if (!v.makeConfirmed) return v
        val syncGen1 = v.make.equals("Ford", ignoreCase = true) && v.year in 2008..2010 &&
            v.btName.startsWith("SYNC", ignoreCase = true)
        if (!syncGen1) return v
        val note = CheckResult(
            CheckStatus.FAIL,
            "SYNC Gen 1 does not forward the steering-wheel voice button to the phone (verified Oct 2026).",
            now,
        )
        return v.copy(
            wheelButtonSupported = false,
            capabilityCheckResults = v.capabilityCheckResults + (ProbeKeys.WHEEL_BUTTON to note),
        )
    }
}
