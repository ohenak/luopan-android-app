package com.luopan.compass.db

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.luopan.compass.calibration.CalibrationRecord
import net.sqlcipher.database.SQLiteDatabaseHook
import net.sqlcipher.database.SupportFactory

@Database(entities = [CalibrationRecord::class], version = 1, exportSchema = false)
abstract class LuopanDatabase : RoomDatabase() {
    abstract fun calibrationDao(): CalibrationDao

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
                .fallbackToDestructiveMigration()
                .build()
        }

        @VisibleForTesting
        fun buildInMemory(context: Context): LuopanDatabase {
            return Room.inMemoryDatabaseBuilder(context, LuopanDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}
