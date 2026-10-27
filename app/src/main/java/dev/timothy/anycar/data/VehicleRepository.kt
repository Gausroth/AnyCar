package dev.timothy.anycar.data

import dev.timothy.anycar.capability.KnownVehicleNotes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class VehicleRepository(private val dao: VehicleProfileDao) {
    fun observeAll(): Flow<List<VehicleProfile>> = dao.observeAll()
    suspend fun all(): List<VehicleProfile> = dao.observeAll().first()
    suspend fun get(id: Long): VehicleProfile? = dao.get(id)
    suspend fun findByMac(mac: String): VehicleProfile? = dao.findByMac(mac.uppercase())
    suspend fun getDefault(): VehicleProfile? = dao.getDefault()

    /**
     * Adds a car, or returns the existing profile for a MAC that already has one (§M13.1).
     * The first car becomes the default. Known-vehicle notes apply only once the make is confirmed.
     */
    suspend fun add(profile: VehicleProfile): VehicleProfile {
        val mac = profile.btMac?.uppercase()
        if (mac != null) findByMac(mac)?.let { return it }
        val first = dao.count() == 0
        val toSave = KnownVehicleNotes.apply(profile.copy(btMac = mac, isDefaultVehicle = first))
        return toSave.copy(id = dao.insert(toSave))
    }

    suspend fun setCdmAssociationId(id: Long, associationId: Int?) {
        dao.get(id)?.let { dao.update(it.copy(cdmAssociationId = associationId)) }
    }

    suspend fun recordResult(id: Long, key: String, result: CheckResult) {
        dao.get(id)?.let { dao.update(it.copy(capabilityCheckResults = it.capabilityCheckResults + (key to result))) }
    }

    /** Writes several probe results in one update (§7.6). */
    suspend fun recordResults(id: Long, results: Map<String, CheckResult>) {
        dao.get(id)?.let { dao.update(it.copy(capabilityCheckResults = it.capabilityCheckResults + results)) }
    }

    /** The wheel-button test result and its flags, together (M11.5). */
    suspend fun setWheelButton(id: Long, result: CheckResult, supported: Boolean, enabled: Boolean) {
        dao.get(id)?.let {
            dao.update(
                it.copy(
                    capabilityCheckResults = it.capabilityCheckResults + (ProbeKeys.WHEEL_BUTTON to result),
                    wheelButtonSupported = supported,
                    wheelButtonEnabled = enabled,
                ),
            )
        }
    }

    suspend fun setWheelButtonEnabled(id: Long, enabled: Boolean) {
        dao.get(id)?.let { dao.update(it.copy(wheelButtonEnabled = enabled && it.wheelButtonSupported)) }
    }

    suspend fun setDefault(id: Long) = dao.setDefault(id)
}
