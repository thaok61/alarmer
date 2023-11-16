package com.thao_soft.alarmer.provider

import android.database.Cursor
import android.provider.BaseColumns
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(alarm: Alarm): Long

    @Query("DELETE FROM ${ClockDatabaseHelper.ALARMS_TABLE_NAME} WHERE ${BaseColumns._ID} = :id")
    fun delete(id: Long): Int

    @Query("SELECT * FROM ${ClockDatabaseHelper.ALARMS_TABLE_NAME}")
    fun getAlarms(): Cursor

    @Update
    fun update(alarm: Alarm): Int

    @Query("SELECT * FROM ${ClockDatabaseHelper.ALARMS_TABLE_NAME} WHERE ${BaseColumns._ID} = :id")
    fun getAlarmById(id: Long): Cursor
}