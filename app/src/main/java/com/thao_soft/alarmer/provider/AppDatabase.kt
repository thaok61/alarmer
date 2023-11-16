package com.thao_soft.alarmer.provider

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thao_soft.alarmer.data.Weekdays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(entities = [Alarm::class, AlarmInstance::class], version = 1, exportSchema = true)
@TypeConverters(WeekdaysConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDAO
    abstract fun alarmInstanceDao(): AlarmInstanceDAO
    abstract fun alarmAndAlarmInstanceDao(): AlarmAndAlarmInstanceDAO

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    ClockDatabaseHelper.DATABASE_NAME
                ).addCallback(DatabaseCallback(scope))
                    .build()

                INSTANCE = instance

                // return instance
                instance
            }
        }
    }

    private class DatabaseCallback(val scope: CoroutineScope) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            INSTANCE?.let { foodItemRoomDB ->
                scope.launch {
                    // if you want to populate database
                    // when RoomDatabase is created
                    // populate here
                    foodItemRoomDB.alarmDao().insert(
                        Alarm(
                            0,
                            false,
                            8,
                            30,
                            Weekdays.fromBits(31),
                            false,
                            "",
                            deleteAfterUse = false
                        )
                    )
                }
            }
        }
    }
}

