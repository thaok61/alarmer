package com.thao_soft.alarmer.provider

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmAndAlarmInstanceDAO {

    @Transaction
    @Query("SELECT * FROM ${ClockDatabaseHelper.ALARMS_TABLE_NAME}")
    fun getAlarmInstancesWithAlarm(): Cursor
}