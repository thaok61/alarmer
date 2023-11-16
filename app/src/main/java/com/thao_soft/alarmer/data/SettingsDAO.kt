package com.thao_soft.alarmer.data

import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import com.thao_soft.alarmer.ui.Constants
import com.thao_soft.alarmer.data.Weekdays.Order
import java.util.Calendar

object SettingsDAO {
    private const val KEY_DEFAULT_ALARM_RINGTONE_URI = "default_alarm_ringtone_uri"
    private const val KEY_ALARM_GLOBAL_ID = "intent.extra.alarm.global.id"

    fun getAlarmCrescendoDuration(prefs: SharedPreferences): Long {
        val crescendoSeconds: String = prefs.getString(Constants.KEY_ALARM_CRESCENDO, "0")!!
        return crescendoSeconds.toInt() * DateUtils.SECOND_IN_MILLIS
    }

    fun getAlarmVolumeButtonBehavior(prefs: SharedPreferences): AlarmVolumeButtonBehavior {
        val defaultValue = Constants.DEFAULT_VOLUME_BEHAVIOR
        val value: String = prefs.getString(Constants.KEY_VOLUME_BUTTONS, defaultValue)!!
        return when (value) {
            Constants.DEFAULT_VOLUME_BEHAVIOR -> AlarmVolumeButtonBehavior.NOTHING
            Constants.VOLUME_BEHAVIOR_SNOOZE -> AlarmVolumeButtonBehavior.SNOOZE
            Constants.VOLUME_BEHAVIOR_DISMISS -> AlarmVolumeButtonBehavior.DISMISS
            else -> throw IllegalArgumentException("Unknown volume button behavior: $value")
        }
    }

    fun getAlarmTimeout(prefs: SharedPreferences): Int {
        // Default value must match the one in res/xml/settings.xml
        val string: String = prefs.getString(Constants.KEY_AUTO_SILENCE, "10")!!
        return string.toInt()
    }

    fun getSnoozeLength(prefs: SharedPreferences): Int {
        // Default value must match the one in res/xml/settings.xml
        val string: String = prefs.getString(Constants.KEY_ALARM_SNOOZE, "10")!!
        return string.toInt()
    }

    fun getDefaultAlarmRingtoneUri(prefs: SharedPreferences): Uri {
        val uriString: String? = prefs.getString(KEY_DEFAULT_ALARM_RINGTONE_URI, null)
        return if (uriString == null) {
            Settings.System.DEFAULT_ALARM_ALERT_URI
        } else {
            Uri.parse(uriString)
        }
    }

    fun setDefaultAlarmRingtoneUri(prefs: SharedPreferences, uri: Uri) {
        prefs.edit().putString(KEY_DEFAULT_ALARM_RINGTONE_URI, uri.toString()).apply()
    }

    fun getGlobalIntentId(prefs: SharedPreferences): Int {
        return prefs.getInt(KEY_ALARM_GLOBAL_ID, -1)
    }

    fun getWeekdayOrder(prefs: SharedPreferences): Order {
        val defaultValue = Calendar.getInstance().firstDayOfWeek.toString()
        return when (val firstCalendarDay = defaultValue.toInt()) {
            Calendar.SATURDAY -> Order.SAT_TO_FRI
            Calendar.SUNDAY -> Order.SUN_TO_SAT
            Calendar.MONDAY -> Order.MON_TO_SUN
            else -> throw IllegalArgumentException("Unknown weekday: $firstCalendarDay")
        }
    }
}