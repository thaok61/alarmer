package com.thao_soft.alarmer.data

import android.net.Uri
import android.provider.BaseColumns
import com.thao_soft.alarmer.BuildConfig

const val AUTHORITY: String = BuildConfig.APPLICATION_ID

interface AlarmsColumns : AlarmSettingColumns, BaseColumns {
    companion object {
        /**
         * The content:// style URL for this table.
         */
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/alarms")

        /**
         * The content:// style URL for the alarms with instance tables, which is used to get the
         * next firing instance and the current state of an alarm.
         */
        val ALARMS_WITH_INSTANCES_URI: Uri = Uri.parse("content://" + AUTHORITY +
                "/alarms_with_instances")

        /**
         * Hour in 24-hour localtime 0 - 23.
         *
         * Type: INTEGER
         */
        const val HOUR = "hour"

        /**
         * Minutes in localtime 0 - 59.
         *
         * Type: INTEGER
         */
        const val MINUTES = "minutes"

        /**
         * Days of the week encoded as a bit set.
         *
         * Type: INTEGER
         *
         * [com.thao.deskclock.data.Weekdays]
         */
        const val DAYS_OF_WEEK = "daysofweek"

        /**
         * True if alarm is active.
         *
         * Type: BOOLEAN
         */
        const val ENABLED = "enabled"

        /**
         * Determine if alarm is deleted after it has been used.
         *
         * Type: INTEGER
         */
        const val DELETE_AFTER_USE = "delete_after_use"
    }
}