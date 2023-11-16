package com.thao_soft.alarmer.provider

import android.database.Cursor
import android.provider.BaseColumns
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thao_soft.alarmer.data.InstancesColumns
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmInstanceDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(alarmInstance: AlarmInstance): Long

    @Query("DELETE FROM ${ClockDatabaseHelper.INSTANCES_TABLE_NAME} WHERE ${BaseColumns._ID} = :id")
    fun delete(id: Long): Int

    @Query("SELECT * FROM ${ClockDatabaseHelper.INSTANCES_TABLE_NAME}")
    fun getAlarmInstances(): Cursor

    @Query("SELECT * FROM ${ClockDatabaseHelper.INSTANCES_TABLE_NAME} WHERE ${InstancesColumns.ALARM_ID} = :id")
    fun getAlarmInstancesByAlarmId(id: Long): Cursor

    @Query("SELECT * FROM ${ClockDatabaseHelper.INSTANCES_TABLE_NAME} WHERE ${InstancesColumns.ALARM_STATE} < ${InstancesColumns.FIRED_STATE}")
    fun getAlarmInstancesByFiringAlarm(): Cursor

    @Update
    fun update(alarmInstance: AlarmInstance): Int

    @Query("SELECT * FROM ${ClockDatabaseHelper.INSTANCES_TABLE_NAME} WHERE ${BaseColumns._ID} = :id")
    fun getAlarmInstanceById(id: Long): Cursor
}