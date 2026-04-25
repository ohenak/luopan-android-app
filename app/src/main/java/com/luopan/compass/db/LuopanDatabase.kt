package com.luopan.compass.db

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.luopan.compass.bearing.BearingDao
import com.luopan.compass.bearing.BearingRecord
import com.luopan.compass.calibration.CalibrationRecord
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [CalibrationRecord::class, BearingRecord::class],
    version = 2,
    exportSchema = true
)
abstract class LuopanDatabase : RoomDatabase() {
    abstract fun calibrationDao(): CalibrationDao
    abstract fun bearingDao(): BearingDao

    companion object {
        @Volatile private var INSTANCE: LuopanDatabase? = null

        fun getInstance(context: Context, passphrase: ByteArray): LuopanDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, passphrase).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, passphrase: ByteArray): LuopanDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context.applicationContext, LuopanDatabase::class.java, "luopan.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        @VisibleForTesting
        fun buildInMemory(context: Context): LuopanDatabase {
            return Room.inMemoryDatabaseBuilder(context, LuopanDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }

        /**
         * Closes the singleton instance and clears the reference.
         * For instrumented tests only — allows tests to close the real on-disk database
         * so that WAL frames are checkpointed before reading raw file bytes.
         */
        @VisibleForTesting
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
