package com.thao_soft.alarmer.data

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings

class AlarmModel(context: Context, private val mSettingsModel: SettingsModel) {
    private var mDefaultAlarmRingtoneUri: Uri? = null
    init {
        // Clear caches affected by system settings when system settings change.
        val cr: ContentResolver = context.contentResolver
        val observer: ContentObserver = SystemAlarmAlertChangeObserver()
        cr.registerContentObserver(Settings.System.DEFAULT_ALARM_ALERT_URI, false, observer)
    }

    private inner class SystemAlarmAlertChangeObserver
        : ContentObserver(Handler(Looper.myLooper()!!)) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            mDefaultAlarmRingtoneUri = null
        }
    }

    var defaultAlarmRingtoneUri: Uri
        get() {
            if (mDefaultAlarmRingtoneUri == null) {
                mDefaultAlarmRingtoneUri = mSettingsModel.defaultAlarmRingtoneUri
            }

            return mDefaultAlarmRingtoneUri!!
        }
        set(uri) {
            // Never set the silent ringtone as default; new alarms should always make sound by default.
            if (AlarmSettingColumns.NO_RINGTONE_URI != uri) {
                mSettingsModel.defaultAlarmRingtoneUri = uri
                mDefaultAlarmRingtoneUri = uri
            }
        }

    val alarmCrescendoDuration: Long
        get() = mSettingsModel.alarmCrescendoDuration

    val alarmVolumeButtonBehavior: AlarmVolumeButtonBehavior
        get() = mSettingsModel.alarmVolumeButtonBehavior

    val alarmTimeout: Int
        get() = mSettingsModel.alarmTimeout

    val snoozeLength: Int
        get() = mSettingsModel.snoozeLength
}