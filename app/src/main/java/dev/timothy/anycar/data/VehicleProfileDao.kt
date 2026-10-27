package dev.timothy.anycar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleProfileDao {
    @Query("SELECT * FROM vehicle_profiles ORDER BY createdAt") fun observeAll(): Flow<List<VehicleProfile>>
    @Query("SELECT * FROM vehicle_profiles WHERE id = :id") suspend fun get(id: Long): VehicleProfile?
    @Query("SELECT * FROM vehicle_profiles WHERE btMac = :mac COLLATE NOCASE LIMIT 1") suspend fun findByMac(mac: String): VehicleProfile?
    @Query("SELECT * FROM vehicle_profiles WHERE isDefaultVehicle = 1 LIMIT 1") suspend fun getDefault(): VehicleProfile?
    @Query("SELECT * FROM vehicle_profiles WHERE btMac IS NULL ORDER BY createdAt") suspend fun noBluetoothVehicles(): List<VehicleProfile>
    @Query("SELECT COUNT(*) FROM vehicle_profiles") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(p: VehicleProfile): Long
    @Update suspend fun update(p: VehicleProfile)
    @Delete suspend fun delete(p: VehicleProfile)
    @Query("UPDATE vehicle_profiles SET isDefaultVehicle = 0") suspend fun clearDefault()
    @Query("UPDATE vehicle_profiles SET isDefaultVehicle = 1 WHERE id = :id") suspend fun setDefaultById(id: Long)
    @Transaction suspend fun setDefault(id: Long) { clearDefault(); setDefaultById(id) }
}
