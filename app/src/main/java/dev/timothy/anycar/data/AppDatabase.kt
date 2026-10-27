package dev.timothy.anycar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [VehicleProfile::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicles(): VehicleProfileDao

    companion object {
        // Destructive migration is fine before the first public release (§7.6).
        fun create(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "anycar.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
