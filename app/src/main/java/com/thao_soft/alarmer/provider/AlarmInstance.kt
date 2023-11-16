package com.thao_soft.alarmer.provider

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.provider.BaseColumns._ID
import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.thao_soft.alarmer.R
import com.thao_soft.alarmer.alarms.AlarmStateManager
import com.thao_soft.alarmer.data.AlarmSettingColumns
import com.thao_soft.alarmer.data.DataModel
import com.thao_soft.alarmer.data.InstancesColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.LinkedList

@Entity(tableName = ClockDatabaseHelper.INSTANCES_TABLE_NAME)
@TypeConverters(UriTypeConverter::class)
data class AlarmInstance(
    // Public fields
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = _ID)
    var id: Long = 0,
    @ColumnInfo(name = InstancesColumns.YEAR)
    var year: Int = 0,
    @ColumnInfo(name = InstancesColumns.MONTH)
    var month: Int = 0,
    @ColumnInfo(name = InstancesColumns.DAY)
    var day: Int = 0,
    @ColumnInfo(name = InstancesColumns.HOUR)
    var hour: Int = 0,
    @ColumnInfo(name = InstancesColumns.MINUTES)
    var minute: Int = 0,
    @ColumnInfo(name = AlarmSettingColumns.LABEL)
    var label: String? = null,
    @ColumnInfo(name = AlarmSettingColumns.VIBRATE)
    var vibrate: Boolean = false,
    @ColumnInfo(name = AlarmSettingColumns.RINGTONE)
    var ringtone: Uri? = null,
    @ColumnInfo(name = InstancesColumns.ALARM_ID)
    var alarmId: Long? = null,
    @ColumnInfo(name = InstancesColumns.ALARM_STATE)
    var alarmState: Int
) : InstancesColumns {

    override fun hashCode(): Int {
        return java.lang.Long.valueOf(id).hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AlarmInstance) return false
        return id == other.id
    }

    fun getLabelOrDefault(context: Context): String {
        return if (label.isNullOrEmpty()) context.getString(R.string.default_label) else label!!
    }

    @get:Ignore
    val missedTimeToLive: Calendar
        get() {
            val calendar = alarmTime
            calendar.add(Calendar.HOUR_OF_DAY, MISSED_TIME_TO_LIVE_HOUR_OFFSET)
            return calendar
        }

    @get:Ignore
    val highNotificationTime: Calendar
        get() {
            val calendar = alarmTime
            calendar.add(Calendar.MINUTE, HIGH_NOTIFICATION_MINUTE_OFFSET)
            return calendar
        }


    @get:Ignore
    val lowNotificationTime: Calendar
        get() {
            val calendar = alarmTime
            calendar.add(Calendar.HOUR_OF_DAY, LOW_NOTIFICATION_HOUR_OFFSET)
            return calendar
        }

    @get:Ignore
    val timeout: Calendar?
        get() {
            val timeoutMinutes = DataModel.dataModel.alarmTimeout

            // Alarm silence has been set to "None"
            if (timeoutMinutes < 0) {
                return null
            }

            val calendar = alarmTime
            calendar.add(Calendar.MINUTE, timeoutMinutes)
            return calendar
        }

    @get:Ignore
    var alarmTime: Calendar
        get() {
            val calendar = Calendar.getInstance()
            Log.d(TAG, "hour: $hour")
            calendar[Calendar.YEAR] = year
            calendar[Calendar.MONTH] = month
            calendar[Calendar.DAY_OF_MONTH] = day
            calendar[Calendar.HOUR_OF_DAY] = hour
            calendar[Calendar.MINUTE] = minute
            calendar[Calendar.SECOND] = 0
            calendar[Calendar.MILLISECOND] = 0
            return calendar
        }
        set(calendar) {
            year = calendar[Calendar.YEAR]
            month = calendar[Calendar.MONTH]
            day = calendar[Calendar.DAY_OF_MONTH]
            hour = calendar[Calendar.HOUR_OF_DAY]
            minute = calendar[Calendar.MINUTE]
        }

    companion object {

        /**
         * Offset from alarm time to show low priority notification
         */
        const val LOW_NOTIFICATION_HOUR_OFFSET = -2

        /**
         * Offset from alarm time to show high priority notification
         */
        const val HIGH_NOTIFICATION_MINUTE_OFFSET = -30

        /**
         * Offset from alarm time to stop showing missed notification.
         */
        private const val MISSED_TIME_TO_LIVE_HOUR_OFFSET = 12

        const val INVALID_ID: Long = -1

        private val TAG: String = AlarmInstance::class.java.simpleName

        private const val ID_INDEX = 0
        private const val YEAR_INDEX = 1
        private const val MONTH_INDEX = 2
        private const val DAY_INDEX = 3
        private const val HOUR_INDEX = 4
        private const val MINUTES_INDEX = 5
        private const val LABEL_INDEX = 6
        private const val VIBRATE_INDEX = 7
        private const val RINGTONE_INDEX = 8
        private const val ALARM_ID_INDEX = 9
        private const val ALARM_STATE_INDEX = 10
        private const val COLUMN_COUNT = ALARM_STATE_INDEX + 1


        private val QUERY_COLUMNS = arrayOf(
            _ID,
            InstancesColumns.YEAR,
            InstancesColumns.MONTH,
            InstancesColumns.DAY,
            InstancesColumns.HOUR,
            InstancesColumns.MINUTES,
            AlarmSettingColumns.LABEL,
            AlarmSettingColumns.VIBRATE,
            AlarmSettingColumns.RINGTONE,
            InstancesColumns.ALARM_ID,
            InstancesColumns.ALARM_STATE
        )

        @JvmStatic
        fun getId(contentUri: Uri): Long {
            return ContentUris.parseId(contentUri)
        }

        @JvmStatic
        fun createIntent(context: Context?, cls: Class<*>?, instanceId: Long): Intent {
            return Intent(context, cls).setData(getContentUri(instanceId))
        }

        private fun getContentUri(instanceId: Long): Uri {
            return ContentUris.withAppendedId(InstancesColumns.CONTENT_URI, instanceId)
        }

        suspend fun getInstance(cr: ContentResolver, instanceId: Long): AlarmInstance? {
            return getInstanceHelper(cr, instanceId)
        }

        suspend fun getInstancesByAlarmId(
            contentResolver: ContentResolver,
            alarmId: Long
        ): List<AlarmInstance> {
            return getInstances(
                contentResolver,
                ClockProvider.GET_ALL_INSTANCES_BY_ALARM_ID,
                alarmId.toString()
            )
        }

        suspend fun getNextUpcomingInstanceByAlarmId(
            contentResolver: ContentResolver,
            alarmId: Long
        ): AlarmInstance? {
            val alarmInstances = getInstancesByAlarmId(contentResolver, alarmId)
            if (alarmInstances.isEmpty()) {
                return null
            }
            var nextAlarmInstance = alarmInstances[0]
            for (instance in alarmInstances) {
                if (instance.alarmTime.before(nextAlarmInstance.alarmTime)) {
                    nextAlarmInstance = instance
                }
            }
            return nextAlarmInstance
        }

        suspend fun getInstances(
            cr: ContentResolver,
            selection: String?,
            vararg selectionArgs: String?
        ): MutableList<AlarmInstance> {
            return withContext(Dispatchers.IO) {
                val result: MutableList<AlarmInstance> = LinkedList()
                val cursor: Cursor? =
                    cr.query(
                        InstancesColumns.CONTENT_URI, QUERY_COLUMNS,
                        selection, selectionArgs, null
                    )
                cursor?.let {
                    if (cursor.moveToFirst()) {
                        do {
                            result.add(constructorHelper(cursor, false))
                        } while (cursor.moveToNext())
                    }
                }
                cursor?.close()

                return@withContext result
            }
        }

        fun constructorHelper(c: Cursor, joinedTable: Boolean): AlarmInstance {
            val mId: Long
            val mYear: Int
            val mMonth: Int
            val mDay: Int
            val mHour: Int
            val mMinute: Int
            val mLabel: String
            val mVibrate: Boolean
            var mAlarmId: Long? = null
            if (joinedTable) {
                mId = c.getLong(Alarm.INSTANCE_ID_INDEX)
                mYear = c.getInt(Alarm.INSTANCE_YEAR_INDEX)
                mMonth = c.getInt(Alarm.INSTANCE_MONTH_INDEX)
                mDay = c.getInt(Alarm.INSTANCE_DAY_INDEX)
                mHour = c.getInt(Alarm.INSTANCE_HOUR_INDEX)
                mMinute = c.getInt(Alarm.INSTANCE_MINUTE_INDEX)
                mLabel = c.getString(Alarm.INSTANCE_LABEL_INDEX)
                mVibrate = c.getInt(Alarm.INSTANCE_VIBRATE_INDEX) == 1
            } else {
                mId = c.getLong(ID_INDEX)
                mYear = c.getInt(YEAR_INDEX)
                mMonth = c.getInt(MONTH_INDEX)
                mDay = c.getInt(DAY_INDEX)
                mHour = c.getInt(HOUR_INDEX)
                mMinute = c.getInt(MINUTES_INDEX)
                mLabel = c.getString(LABEL_INDEX)
                mVibrate = c.getInt(VIBRATE_INDEX) == 1
            }
            val mRingtone: Uri = if (c.isNull(RINGTONE_INDEX)) {
                // Should we be saving this with the current ringtone or leave it null
                // so it changes when user changes default ringtone?
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } else {
                Uri.parse(c.getString(RINGTONE_INDEX))
            }

            if (!c.isNull(ALARM_ID_INDEX)) {
                mAlarmId = c.getLong(ALARM_ID_INDEX)
            }
            val mAlarmState: Int = c.getInt(ALARM_STATE_INDEX)
            return AlarmInstance(
                mId,
                mYear,
                mMonth,
                mDay,
                mHour,
                mMinute,
                mLabel,
                mVibrate,
                mRingtone,
                mAlarmId,
                mAlarmState
            )
        }

        suspend fun getInstanceHelper(cr: ContentResolver, instanceId: Long): AlarmInstance? {
            return withContext(Dispatchers.IO) {
                var alarmInstance: AlarmInstance? = null
                val cursor: Cursor? =
                    cr.query(getContentUri(instanceId), QUERY_COLUMNS, null, null, null)
                cursor?.let {
                    if (it.moveToFirst()) {
                        alarmInstance = constructorHelper(it, false)
                    }
                }
                cursor?.close()
                return@withContext alarmInstance
            }
        }

        suspend fun deleteInstance(contentResolver: ContentResolver, id: Long): Boolean {
            if (id == INVALID_ID) return false
            return withContext(Dispatchers.IO) {
                val deletedRows: Int = contentResolver.delete(getContentUri(id), null, null)
                return@withContext deletedRows == 1
            }
        }

        suspend fun updateInstance(
            contentResolver: ContentResolver,
            instance: AlarmInstance
        ): Boolean {
            if (instance.id == INVALID_ID) return false
            return withContext(Dispatchers.IO) {
                val values: ContentValues = createContentValues(instance)
                val rowsUpdated: Long =
                    contentResolver.update(getContentUri(instance.id), values, null, null).toLong()
                return@withContext rowsUpdated == 1L
            }
        }

        private fun createContentValues(instance: AlarmInstance): ContentValues {
            val values = ContentValues(COLUMN_COUNT)
            if (instance.id != INVALID_ID) {
                values.put(_ID, instance.id)
            }

            values.put(InstancesColumns.YEAR, instance.year)
            values.put(InstancesColumns.MONTH, instance.month)
            values.put(InstancesColumns.DAY, instance.day)
            values.put(InstancesColumns.HOUR, instance.hour)
            values.put(InstancesColumns.MINUTES, instance.minute)
            values.put(AlarmSettingColumns.LABEL, instance.label)
            values.put(AlarmSettingColumns.VIBRATE, instance.vibrate)
            if (instance.ringtone == null) {
                // We want to put null in the database, so we'll be able
                // to pick up on changes to the default alarm
                values.putNull(AlarmSettingColumns.RINGTONE)
            } else {
                values.put(AlarmSettingColumns.RINGTONE, instance.ringtone.toString())
            }
            values.put(InstancesColumns.ALARM_ID, instance.alarmId)
            values.put(InstancesColumns.ALARM_STATE, instance.alarmState)
            return values
        }

        suspend fun deleteOtherInstances(
            context: Context,
            contentResolver: ContentResolver,
            alarmId: Long,
            instanceId: Long
        ) {
            val instances = getInstancesByAlarmId(contentResolver, alarmId)
            for (instance in instances) {
                if (instance.id != instanceId) {
                    AlarmStateManager.unregisterInstance(context, instance)
                    deleteInstance(contentResolver, instance.id)
                }
            }
        }

        suspend fun addInstance(
            contentResolver: ContentResolver,
            instance: AlarmInstance
        ): AlarmInstance {
            val instances = getInstances(
                contentResolver,
                ClockProvider.GET_ALL_INSTANCES_BY_ALARM_ID,
                instance.alarmId.toString()
            )
            // Make sure we are not adding a duplicate instances. This is not a
            // fix and should never happen. This is only a safe guard against bad code, and you
            // should fix the root issue if you see the error message.
            for (otherInstances in instances) {
                if (otherInstances.alarmTime == instance.alarmTime) {
                    Log.i(
                        TAG, "Detected duplicate instance in DB. Updating " +
                                otherInstances + " to " + instance
                    )
                    // Copy over the new instance values and update the db
                    instance.id = otherInstances.id
                    updateInstance(contentResolver, instance)
                    return instance
                }
            }

            val values: ContentValues = createContentValues(instance)
            val uri: Uri = contentResolver.insert(InstancesColumns.CONTENT_URI, values)!!
            instance.id = getId(uri)
            return instance
        }
    }
}