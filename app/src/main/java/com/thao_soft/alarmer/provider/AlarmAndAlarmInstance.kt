package com.thao_soft.alarmer.provider

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.media.RingtoneManager
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import androidx.room.Embedded
import androidx.room.Relation
import com.thao_soft.alarmer.data.AlarmSettingColumns
import com.thao_soft.alarmer.data.AlarmsColumns
import com.thao_soft.alarmer.data.InstancesColumns
import com.thao_soft.alarmer.data.Weekdays
import com.thao_soft.alarmer.provider.Alarm.Companion.INSTANCE_ID_INDEX
import com.thao_soft.alarmer.provider.Alarm.Companion.INSTANCE_VIBRATE_INDEX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.LinkedList

data class AlarmAndAlarmInstance (
    @Embedded
    var alarm: Alarm,
    @Relation(
        parentColumn = "id",
        entityColumn = "alarmId"
    )
    var alarmInstance: AlarmInstance?
) {

    companion object {
        private const val ALARM_JOIN_INSTANCE_COLUMN_COUNT = INSTANCE_VIBRATE_INDEX + 1
        val TAG: String = "AlarmAndAlarmInstance"
        suspend fun getAlarms(cr: ContentResolver): MutableList<AlarmAndAlarmInstance> {
            return withContext(Dispatchers.IO) {
                val result: MutableList<AlarmAndAlarmInstance> = LinkedList()
                val cursor: Cursor? =
                    cr.query(AlarmsColumns.ALARMS_WITH_INSTANCES_URI, null, null, null, null)

                cursor?.let {
                    if (it.moveToFirst()) {
                        do {
                            result.add(constructorHelper(it))
                        } while (cursor.moveToNext())
                        Log.d(TAG, "moveToFirst:")
                    }
                }
                cursor?.close()
                return@withContext result
            }
        }

        fun isTomorrow(alarmAndAlarmInstance: AlarmAndAlarmInstance, now: Calendar): Boolean {
            if (alarmAndAlarmInstance.alarmInstance?.alarmState == InstancesColumns.SNOOZE_STATE) {
                return false
            }

            val totalAlarmMinutes = alarmAndAlarmInstance.alarm.hour * 60 + alarmAndAlarmInstance.alarm.minutes
            val totalNowMinutes = now[Calendar.HOUR_OF_DAY] * 60 + now[Calendar.MINUTE]
            return totalAlarmMinutes <= totalNowMinutes
        }

        fun constructorHelper(c: Cursor): AlarmAndAlarmInstance {
            val id = c.getLong(Alarm.ID_INDEX)
            val enabled = c.getInt(Alarm.ENABLED_INDEX) == 1
            val hour = c.getInt(Alarm.HOUR_INDEX)
            val minutes = c.getInt(Alarm.MINUTES_INDEX)
            val daysOfWeek = Weekdays.fromBits(c.getInt(Alarm.DAYS_OF_WEEK_INDEX))
            val vibrate = c.getInt(Alarm.VIBRATE_INDEX) == 1
            val label = c.getString(Alarm.LABEL_INDEX)
            val deleteAfterUse = c.getInt(Alarm.DELETE_AFTER_USE_INDEX) == 1

            val alert = if (c.isNull(Alarm.RINGTONE_INDEX)) {
                // Should we be saving this with the current ringtone or leave it null
                // so it changes when user changes default ringtone?
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } else {
                Uri.parse(c.getString(Alarm.RINGTONE_INDEX))
            }

            val alarm =  Alarm(
                id,
                enabled,
                hour,
                minutes,
                daysOfWeek,
                vibrate,
                label,
                alert,
                deleteAfterUse
            )

            return AlarmAndAlarmInstance(alarm, null)
        }
    }
}