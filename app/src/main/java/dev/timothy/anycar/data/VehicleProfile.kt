package dev.timothy.anycar.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One car (§7.2). Field names, types, and defaults are canonical: add new ones to the spec first. */
@Entity(
    tableName = "vehicle_profiles",
    indices = [Index(value = ["btMac"], unique = true)],
)
data class VehicleProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val btMac: String?,                         // UPPERCASE "AA:BB:CC:DD:EE:FF"; null = car without Bluetooth (M1.10)
    val btName: String,                         // bonded-device name at pairing time, or the user's name for a no-Bluetooth car
    val make: String = "",
    val model: String = "",
    val year: Int = 0,                          // 0 = unknown
    val makeConfirmed: Boolean = false,         // gates KnownVehicleNotes and OBDb matching
    val vin: String? = null,
    val vinDecodePending: Boolean = false,      // release 1.1
    val cdmAssociationId: Int? = null,          // a cache; CompanionDeviceManager is the source of truth (M1.3)
    val projectionChoice: String = "ask",       // "ask" | "android_auto" | "anycar" (M1.6)
    val projectionAsked: Int = 0,
    val obdTransport: String = "none",          // "none" | "spp" | "ble" | "wifi" | "usb"
    val obdAdapterAddress: String? = null,      // never triggers car mode
    val obdProtocol: String = "not-tested",
    val speakOverCallChannel: Boolean = false,  // release 1.1
    val wheelButtonSupported: Boolean = false,
    val wheelButtonEnabled: Boolean = false,
    val scoHoldsReliably: Boolean? = null,      // release 1.1
    val hotwordEnabled: Boolean = false,        // release 1.1
    val hotwordUseCarMic: Boolean = false,      // release 1.1
    val wakePhrase: String = "Hey AnyCar",      // release 1.1
    val hotwordThreshold: Float = 0.5f,         // release 1.1
    val capabilityCheckResults: Map<String, CheckResult> = emptyMap(),
    val isDefaultVehicle: Boolean = false,      // exactly one profile is true; used for manual launch
    val createdAt: Long = System.currentTimeMillis(),
) {
    val hasBluetooth: Boolean get() = btMac != null

    /** "2009 Ford Taurus", or the Bluetooth name when nothing is known. */
    val displayName: String
        get() = listOf(if (year > 0) year.toString() else "", make, model).filter { it.isNotBlank() }
            .joinToString(" ").ifBlank { btName }
}
