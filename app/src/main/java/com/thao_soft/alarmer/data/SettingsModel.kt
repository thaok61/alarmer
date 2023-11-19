package com.thao_soft.alarmer.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

class SettingsModel(
    private val mContext: Context,
    private val mPrefs: SharedPreferences,
    private val mTimeModel: TimeModel
) {
    fun updateGlobalIntentId() {
        SettingsDAO.updateGlobalIntentId(mPrefs)
    }

    var isRestoreBackupFinished: Boolean
        get() = SettingsDAO.isRestoreBackupFinished(mPrefs)
        set(finished) {
            SettingsDAO.setRestoreBackupFinished(mPrefs, finished)
        }
    val weekdayOrder: Weekdays.Order
        get() = SettingsDAO.getWeekdayOrder(mPrefs)

    val globalIntentId: Int
        get() = SettingsDAO.getGlobalIntentId(mPrefs)
    val alarmCrescendoDuration: Long
        get() = SettingsDAO.getAlarmCrescendoDuration(mPrefs)

    val alarmVolumeButtonBehavior: AlarmVolumeButtonBehavior
        get() = SettingsDAO.getAlarmVolumeButtonBehavior(mPrefs)

    val alarmTimeout: Int
        get() = SettingsDAO.getAlarmTimeout(mPrefs)

    val snoozeLength: Int
        get() = SettingsDAO.getSnoozeLength(mPrefs)

    var defaultAlarmRingtoneUri: Uri
        get() = SettingsDAO.getDefaultAlarmRingtoneUri(mPrefs)
        set(uri) {
            SettingsDAO.setDefaultAlarmRingtoneUri(mPrefs, uri)
        }
}