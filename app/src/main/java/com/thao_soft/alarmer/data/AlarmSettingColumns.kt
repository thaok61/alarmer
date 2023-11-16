package com.thao_soft.alarmer.data

import android.net.Uri
import android.provider.BaseColumns

interface AlarmSettingColumns : BaseColumns {
    companion object {
        /**
         * This string is used to indicate no ringtone.
         */
        val NO_RINGTONE_URI: Uri = Uri.EMPTY

        /**
         * This string is used to indicate no ringtone.
         */
        val NO_RINGTONE: String = NO_RINGTONE_URI.toString()

        /**
         * True if alarm should vibrate
         *
         * Type: BOOLEAN
         */
        const val VIBRATE = "vibrate"

        /**
         * Alarm label.
         *
         * Type: STRING
         */
        const val LABEL = "label"

        /**
         * Audio alert to play when alarm triggers. Null entry
         * means use system default and entry that equal
         * Uri.EMPTY.toString() means no ringtone.
         *
         * Type: STRING
         */
        const val RINGTONE = "ringtone"
    }
}