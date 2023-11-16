package com.thao_soft.alarmer.provider

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.thao_soft.alarmer.data.AUTHORITY
import com.thao_soft.alarmer.data.AlarmSettingColumns
import com.thao_soft.alarmer.data.AlarmsColumns
import com.thao_soft.alarmer.data.InstancesColumns
import com.thao_soft.alarmer.data.Weekdays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class ClockProvider : ContentProvider() {

    companion object {
        private const val ALARMS = 1
        private const val ALARMS_ID = 2
        private const val INSTANCES = 3
        private const val INSTANCES_ID = 4
        private const val ALARMS_WITH_INSTANCES = 5

        const val GET_ALL_INSTANCES = "ALL_INSTANCES"
        const val GET_ALL_INSTANCES_BY_ALARM_ID = "GET_ALL_INSTANCES_BY_ALARM_ID"
        const val GET_ALL_INSTANCES_BY_ALARM_STATES = "GET_ALL_INSTANCES_BY_ALARM_STATES"
        val TAG: String = ClockProvider::class.java.simpleName
    }

    private lateinit var appDatabase: AppDatabase
    private var alarmDAO: AlarmDAO? = null
    private var alarmInstanceDAO: AlarmInstanceDAO? = null
    private var alarmAndAlarmInstanceDAO: AlarmAndAlarmInstanceDAO? = null

    private val applicationScope = CoroutineScope(SupervisorJob())

    private val sURIMatcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    init {
        sURIMatcher.addURI(AUTHORITY, "alarms", ALARMS)
        sURIMatcher.addURI(AUTHORITY, "alarms/#", ALARMS_ID)
        sURIMatcher.addURI(AUTHORITY, "instances", INSTANCES)
        sURIMatcher.addURI(AUTHORITY, "instances/#", INSTANCES_ID)
        sURIMatcher.addURI(
            AUTHORITY,
            "alarms_with_instances", ALARMS_WITH_INSTANCES
        )
    }

    override fun onCreate(): Boolean {
        appDatabase = AppDatabase.getDatabase(context!!, applicationScope)
        alarmDAO = appDatabase.alarmDao()
        alarmInstanceDAO = appDatabase.alarmInstanceDao()
        alarmAndAlarmInstanceDAO = appDatabase.alarmAndAlarmInstanceDao()
        return true
    }

    override fun query(
        uri: Uri,
        projectionIn: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sort: String?
    ): Cursor? {
        val cursor = when (sURIMatcher.match(uri)) {
            ALARMS -> {
                alarmDAO?.getAlarms()
            }

            ALARMS_ID -> {
                val id = uri.lastPathSegment!!.toLong()
                alarmDAO?.getAlarmById(id)
            }

            INSTANCES -> {
                when (selection) {
                    GET_ALL_INSTANCES -> alarmInstanceDAO?.getAlarmInstances()

                    GET_ALL_INSTANCES_BY_ALARM_ID -> {
                        Log.d(TAG, "query: $selectionArgs")
                        val alarmId = selectionArgs!![0]!!.toLong()
                        alarmInstanceDAO?.getAlarmInstancesByAlarmId(alarmId)
                    }

                    GET_ALL_INSTANCES_BY_ALARM_STATES -> {
                        alarmInstanceDAO?.getAlarmInstancesByFiringAlarm()
                    }

                    else -> throw IllegalArgumentException("Unknown SELECTION $selection")
                }
            }

            INSTANCES_ID -> {
                val id = uri.lastPathSegment!!.toLong()
                alarmInstanceDAO?.getAlarmInstanceById(id)
            }

            ALARMS_WITH_INSTANCES -> {
                alarmAndAlarmInstanceDAO?.getAlarmInstancesWithAlarm()
            }

            else -> throw IllegalArgumentException("Unknown URI $uri")
        }

        if (cursor == null) {
            Log.e(TAG, "Alarms.query: failed")
        } else {
            cursor.setNotificationUri(context!!.contentResolver, uri)
        }
        return cursor
    }

    override fun getType(uri: Uri): String {
        return when (sURIMatcher.match(uri)) {
            ALARMS -> "vnd.android.cursor.dir/alarms"
            ALARMS_ID -> "vnd.android.cursor.item/alarms"
            INSTANCES -> "vnd.android.cursor.dir/instances"
            INSTANCES_ID -> "vnd.android.cursor.item/instances"
            else -> throw IllegalArgumentException("Unknown URI")
        }
    }

    override fun insert(uri: Uri, initialValues: ContentValues?): Uri {
        val rowId: Long = when (sURIMatcher.match(uri)) {
            ALARMS -> {
                val enabled = initialValues?.getAsBoolean(AlarmsColumns.ENABLED) ?: false
                val hour = initialValues?.getAsInteger(AlarmsColumns.HOUR) ?: 0
                val minutes = initialValues?.getAsInteger(AlarmsColumns.MINUTES) ?: 0
                val daysOfWeekBits = initialValues?.getAsInteger(AlarmsColumns.DAYS_OF_WEEK) ?: 0
                val daysOfWeek = Weekdays.fromBits(daysOfWeekBits)
                val vibrate = initialValues?.getAsBoolean(AlarmSettingColumns.VIBRATE) ?: false
                val label = initialValues?.getAsString(AlarmSettingColumns.LABEL)
                val alert: Uri =
                    if (initialValues?.containsKey(AlarmSettingColumns.RINGTONE) == true) {
                        Uri.parse(initialValues.getAsString(AlarmSettingColumns.RINGTONE))
                    } else {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    }
                val deleteAfterUse = initialValues?.getAsBoolean(AlarmsColumns.DELETE_AFTER_USE) ?: false
                val alarm = Alarm(
                    0,
                    enabled,
                    hour,
                    minutes,
                    daysOfWeek,
                    vibrate,
                    label,
                    alert,
                    deleteAfterUse
                )

                alarmDAO!!.insert(alarm)
            }

            INSTANCES -> {
                val year = initialValues?.getAsInteger(InstancesColumns.YEAR) ?: 0
                val month = initialValues?.getAsInteger(InstancesColumns.MONTH) ?: 0
                val day = initialValues?.getAsInteger(InstancesColumns.DAY) ?: 0
                val hour = initialValues?.getAsInteger(InstancesColumns.HOUR) ?: 0
                val minutes = initialValues?.getAsInteger(InstancesColumns.MINUTES) ?: 0
                val label = initialValues?.getAsString(AlarmSettingColumns.LABEL) ?: ""
                val vibrate = initialValues?.getAsBoolean(AlarmSettingColumns.VIBRATE) ?: false
                val ringtone =
                    Uri.parse(initialValues?.getAsString(AlarmSettingColumns.RINGTONE) ?: "")
                val alarmId = initialValues?.getAsLong(InstancesColumns.ALARM_ID) ?: 0
                val alarmState = initialValues?.getAsInteger(InstancesColumns.ALARM_STATE) ?: 0

                val alarmInstance = AlarmInstance(
                    0,
                    year,
                    month,
                    day,
                    hour,
                    minutes,
                    label,
                    vibrate,
                    ringtone,
                    alarmId,
                    alarmState
                )
                alarmInstanceDAO!!.insert(alarmInstance)
            }

            else -> throw IllegalArgumentException("Cannot insert from URI: $uri")
        }

        val uriResult: Uri = ContentUris.withAppendedId(uri, rowId)
        notifyChange(context!!.contentResolver, uriResult)
        return uriResult
    }

    override fun delete(uri: Uri, where: String?, whereArgs: Array<String>?): Int {
        val primaryKey = uri.lastPathSegment!!.toLong()
        val count = when (sURIMatcher.match(uri)) {
            ALARMS_ID -> alarmDAO!!.delete(primaryKey)
            INSTANCES_ID -> alarmInstanceDAO!!.delete(primaryKey)
            else -> throw IllegalArgumentException("Cannot delete from URI: $uri")
        }
        notifyChange(context!!.contentResolver, uri)
        return count
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        where: String?,
        whereArgs: Array<String?>?
    ): Int {
        val primaryKey = uri.lastPathSegment!!.toLong()
        val count = when (sURIMatcher.match(uri)) {
            ALARMS_ID -> {
                val enabled = values?.getAsBoolean(AlarmsColumns.ENABLED) ?: false
                val hour = values?.getAsInteger(AlarmsColumns.HOUR) ?: 0
                val minutes = values?.getAsInteger(AlarmsColumns.MINUTES) ?: 0
                val daysOfWeekBits = values?.getAsInteger(AlarmsColumns.DAYS_OF_WEEK) ?: 0
                val daysOfWeek = Weekdays.fromBits(daysOfWeekBits)
                val vibrate = values?.getAsBoolean(AlarmSettingColumns.VIBRATE) ?: false
                val label = values?.getAsString(AlarmSettingColumns.LABEL)
                val alert: Uri =
                    if (values?.containsKey(AlarmSettingColumns.RINGTONE) == true) {
                        Uri.parse(values.getAsString(AlarmSettingColumns.RINGTONE))
                    } else {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    }
                val deleteAfterUse = values?.getAsBoolean(AlarmsColumns.DELETE_AFTER_USE) ?: false
                val alarm = Alarm(
                    primaryKey,
                    enabled,
                    hour,
                    minutes,
                    daysOfWeek,
                    vibrate,
                    label,
                    alert,
                    deleteAfterUse
                )
                alarmDAO!!.update(alarm)
            }

            INSTANCES_ID -> {
                val year = values?.getAsInteger(InstancesColumns.YEAR) ?: 0
                val month = values?.getAsInteger(InstancesColumns.MONTH) ?: 0
                val day = values?.getAsInteger(InstancesColumns.DAY) ?: 0
                val hour = values?.getAsInteger(InstancesColumns.HOUR) ?: 0
                val minutes = values?.getAsInteger(InstancesColumns.MINUTES) ?: 0
                val label = values?.getAsString(AlarmSettingColumns.LABEL) ?: ""
                val vibrate = values?.getAsBoolean(AlarmSettingColumns.VIBRATE) ?: false
                val ringtone = Uri.parse(values?.getAsString(AlarmSettingColumns.RINGTONE) ?: "")
                val alarmId = values?.getAsLong(InstancesColumns.ALARM_ID) ?: 0
                val alarmState = values?.getAsInteger(InstancesColumns.ALARM_STATE) ?: 0
                val alarmInstance = AlarmInstance(
                    primaryKey,
                    year,
                    month,
                    day,
                    hour,
                    minutes,
                    label,
                    vibrate,
                    ringtone,
                    alarmId,
                    alarmState
                )
                alarmInstanceDAO!!.update(alarmInstance)
            }
            else -> {
                throw UnsupportedOperationException("Cannot update URI: $uri")
            }
        }
        Log.d(TAG, "*** notifyChange() id: $primaryKey url $uri")

        notifyChange(context!!.contentResolver, uri)

        return count
    }

    /**
     * Notify affected URIs of changes.
     */
    private fun notifyChange(resolver: ContentResolver, uri: Uri) {
        resolver.notifyChange(uri, null)

        val match: Int = sURIMatcher.match(uri)
        // Also notify the joined table of changes to instances or alarms.
        if (match == ALARMS || match == INSTANCES || match == ALARMS_ID || match == INSTANCES_ID) {
            resolver.notifyChange(AlarmsColumns.ALARMS_WITH_INSTANCES_URI, null)
        }
    }
}