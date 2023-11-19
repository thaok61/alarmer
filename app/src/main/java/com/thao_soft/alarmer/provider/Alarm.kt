package com.thao_soft.alarmer.provider

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.thao_soft.alarmer.data.AlarmSettingColumns
import com.thao_soft.alarmer.data.AlarmsColumns
import com.thao_soft.alarmer.data.DataModel
import com.thao_soft.alarmer.data.InstancesColumns
import com.thao_soft.alarmer.data.Weekdays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.LinkedList

@Entity(tableName = ClockDatabaseHelper.ALARMS_TABLE_NAME)
@TypeConverters(WeekdaysConverter::class, UriTypeConverter::class)
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = BaseColumns._ID)
    var id: Long,
    @ColumnInfo(name = AlarmsColumns.ENABLED)
    var enabled: Boolean = false,
    @ColumnInfo(name = AlarmsColumns.HOUR)
    var hour: Int,
    @ColumnInfo(name = AlarmsColumns.MINUTES)
    var minutes: Int,
    @ColumnInfo(name = AlarmsColumns.DAYS_OF_WEEK)
    var daysOfWeek: Weekdays,
    @ColumnInfo(name = AlarmSettingColumns.VIBRATE)
    var vibrate: Boolean,
    @ColumnInfo(name = AlarmSettingColumns.LABEL)
    var label: String?,
    @ColumnInfo(name = AlarmSettingColumns.RINGTONE)
    var alert: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
    @ColumnInfo(name = AlarmsColumns.DELETE_AFTER_USE)
    var deleteAfterUse: Boolean,
) : AlarmsColumns {
    // Public fields

    fun createInstanceAfter(time: Calendar): AlarmInstance {
        val nextInstanceTime = getNextAlarmTime(time)
        Log.d(TAG, "createInstanceAfter: ${nextInstanceTime.time}")
        val result = AlarmInstance(
            INVALID_ID,
            nextInstanceTime[Calendar.YEAR],
            nextInstanceTime[Calendar.MONTH],
            nextInstanceTime[Calendar.DAY_OF_MONTH],
            nextInstanceTime[Calendar.HOUR_OF_DAY],
            nextInstanceTime[Calendar.MINUTE],
            "",
            false,
            null,
            alarmId = id,
            alarmState = InstancesColumns.SILENT_STATE
        )

        result.vibrate = vibrate
        result.label = label
        result.ringtone = alert
        return result
    }

    fun getNextAlarmTime(currentTime: Calendar): Calendar {
        val nextInstanceTime = Calendar.getInstance(currentTime.timeZone)
        nextInstanceTime[Calendar.YEAR] = currentTime[Calendar.YEAR]
        nextInstanceTime[Calendar.MONTH] = currentTime[Calendar.MONTH]
        nextInstanceTime[Calendar.DAY_OF_MONTH] = currentTime[Calendar.DAY_OF_MONTH]
        nextInstanceTime[Calendar.HOUR_OF_DAY] = hour
        nextInstanceTime[Calendar.MINUTE] = minutes
        nextInstanceTime[Calendar.SECOND] = 0
        nextInstanceTime[Calendar.MILLISECOND] = 0

        // If we are still behind the passed in currentTime, then add a day
        if (nextInstanceTime.timeInMillis <= currentTime.timeInMillis) {
            nextInstanceTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        // The day of the week might be invalid, so find next valid one
        val addDays = daysOfWeek.getDistanceToNextDay(nextInstanceTime)
        if (addDays > 0) {
            nextInstanceTime.add(Calendar.DAY_OF_WEEK, addDays)
        }

        // Daylight Savings Time can alter the hours and minutes when adjusting the day above.
        // Reset the desired hour and minute now that the correct day has been chosen.
        nextInstanceTime[Calendar.HOUR_OF_DAY] = hour
        nextInstanceTime[Calendar.MINUTE] = minutes

        return nextInstanceTime
    }

    fun getPreviousAlarmTime(currentTime: Calendar): Calendar? {
        val previousInstanceTime = Calendar.getInstance(currentTime.timeZone)
        previousInstanceTime[Calendar.YEAR] = currentTime[Calendar.YEAR]
        previousInstanceTime[Calendar.MONTH] = currentTime[Calendar.MONTH]
        previousInstanceTime[Calendar.DAY_OF_MONTH] = currentTime[Calendar.DAY_OF_MONTH]
        previousInstanceTime[Calendar.HOUR_OF_DAY] = hour
        previousInstanceTime[Calendar.MINUTE] = minutes
        previousInstanceTime[Calendar.SECOND] = 0
        previousInstanceTime[Calendar.MILLISECOND] = 0

        val subtractDays = daysOfWeek.getDistanceToPreviousDay(previousInstanceTime)
        return if (subtractDays > 0) {
            previousInstanceTime.add(Calendar.DAY_OF_WEEK, -subtractDays)
            previousInstanceTime
        } else {
            null
        }
    }

    companion object {
        fun createIntent(context: Context?, cls: Class<*>?, alarmId: Long): Intent {
            return Intent(context, cls).setData(getContentUri(alarmId))
        }

        fun getContentUri(alarmId: Long): Uri {
            return ContentUris.withAppendedId(AlarmsColumns.CONTENT_URI, alarmId)
        }

        suspend fun getAlarm(cr: ContentResolver, alarmId: Long): Alarm? {
            return withContext(Dispatchers.IO) {
                var alarm: Alarm? = null
                val cursor: Cursor? =
                    cr.query(getContentUri(alarmId), QUERY_COLUMNS, null, null, null)
                cursor?.let {
                    if (it.moveToFirst()) {
                        alarm = constructorHelper(it)
                    }
                }
                cursor?.close()
                return@withContext alarm
            }
        }

        fun constructorHelper(c: Cursor): Alarm {
            val id = c.getLong(ID_INDEX)
            val enabled = c.getInt(ENABLED_INDEX) == 1
            val hour = c.getInt(HOUR_INDEX)
            val minutes = c.getInt(MINUTES_INDEX)
            val daysOfWeek = Weekdays.fromBits(c.getInt(DAYS_OF_WEEK_INDEX))
            val vibrate = c.getInt(VIBRATE_INDEX) == 1
            val label = c.getString(LABEL_INDEX)
            val deleteAfterUse = c.getInt(DELETE_AFTER_USE_INDEX) == 1

            val alert = if (c.isNull(RINGTONE_INDEX)) {
                // Should we be saving this with the current ringtone or leave it null
                // so it changes when user changes default ringtone?
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } else {
                Uri.parse(c.getString(RINGTONE_INDEX))
            }

            return Alarm(
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
        }

        fun constructorHelper(hour: Int = 0, minutes: Int = 0): Alarm {
            val id = INVALID_ID
            val enabled = false
            val daysOfWeek = Weekdays.NONE
            val vibrate = true
            val label = ""
            val deleteAfterUse = false

            val alert = DataModel.dataModel.defaultAlarmRingtoneUri

            return Alarm(
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
        }

        suspend fun deleteAlarm(contentResolver: ContentResolver, alarmId: Long): Boolean {
            if (alarmId == INVALID_ID) return false
            return withContext(Dispatchers.IO) {
                val deletedRows: Int = contentResolver.delete(getContentUri(alarmId), "", null)
                return@withContext deletedRows == 1
            }

        }

        suspend fun updateAlarm(contentResolver: ContentResolver, alarm: Alarm): Boolean {
            if (alarm.id == INVALID_ID) return false
            return withContext(Dispatchers.IO) {
                val values: ContentValues = createContentValues(alarm)
                val rowsUpdated: Long =
                    contentResolver.update(getContentUri(alarm.id), values, null, null).toLong()
                return@withContext rowsUpdated == 1L
            }

        }

        fun createContentValues(alarm: Alarm): ContentValues {
            val values = ContentValues(COLUMN_COUNT)
            if (alarm.id != INVALID_ID) {
                values.put(BaseColumns._ID, alarm.id)
            }

            values.put(AlarmsColumns.ENABLED, if (alarm.enabled) 1 else 0)
            values.put(AlarmsColumns.HOUR, alarm.hour)
            values.put(AlarmsColumns.MINUTES, alarm.minutes)
            values.put(AlarmsColumns.DAYS_OF_WEEK, alarm.daysOfWeek.bits)
            values.put(AlarmSettingColumns.VIBRATE, if (alarm.vibrate) 1 else 0)
            values.put(AlarmSettingColumns.LABEL, alarm.label)
            values.put(AlarmsColumns.DELETE_AFTER_USE, alarm.deleteAfterUse)
            values.put(AlarmSettingColumns.RINGTONE, alarm.alert.toString())
            return values
        }

        private fun getId(contentUri: Uri): Long {
            return ContentUris.parseId(contentUri)
        }

        suspend fun addAlarm(contentResolver: ContentResolver, alarm: Alarm): Alarm {
            return withContext(Dispatchers.IO) {
                val values: ContentValues = createContentValues(alarm)
                val uri: Uri = contentResolver.insert(AlarmsColumns.CONTENT_URI, values)!!
                alarm.id = getId(uri)
                return@withContext alarm
            }

        }

        fun getAlarms(
            cr: ContentResolver,
            selection: String?,
            vararg selectionArgs: String?
        ): List<Alarm> {
            val result: MutableList<Alarm> = LinkedList()
            val cursor: Cursor? =
                cr.query(AlarmsColumns.CONTENT_URI, QUERY_COLUMNS,
                    selection, selectionArgs, null)
            cursor?.let {
                if (cursor.moveToFirst()) {
                    do {
                        result.add(constructorHelper(cursor))
                    } while (cursor.moveToNext())
                }
            }

            return result
        }

        val TAG: String = Alarm::class.java.simpleName

        const val INVALID_ID: Long = -1

        private val QUERY_COLUMNS = arrayOf(
            BaseColumns._ID,
            AlarmsColumns.HOUR,
            AlarmsColumns.MINUTES,
            AlarmsColumns.DAYS_OF_WEEK,
            AlarmsColumns.ENABLED,
            AlarmSettingColumns.VIBRATE,
            AlarmSettingColumns.LABEL,
            AlarmSettingColumns.RINGTONE,
            AlarmsColumns.DELETE_AFTER_USE
        )

        const val ID_INDEX = 0
        const val ENABLED_INDEX = 1
        const val HOUR_INDEX = 2
        const val MINUTES_INDEX = 3
        const val DAYS_OF_WEEK_INDEX = 4
        const val VIBRATE_INDEX = 5
        const val LABEL_INDEX = 6
        const val RINGTONE_INDEX = 7
        const val DELETE_AFTER_USE_INDEX = 8
        const val INSTANCE_ID_INDEX = 10
        const val INSTANCE_YEAR_INDEX = 11
        const val INSTANCE_MONTH_INDEX = 12
        const val INSTANCE_DAY_INDEX = 13
        const val INSTANCE_HOUR_INDEX = 14
        const val INSTANCE_MINUTE_INDEX = 15
        const val INSTANCE_LABEL_INDEX = 16
        const val INSTANCE_VIBRATE_INDEX = 17

        private const val COLUMN_COUNT = DELETE_AFTER_USE_INDEX + 1
    }
}