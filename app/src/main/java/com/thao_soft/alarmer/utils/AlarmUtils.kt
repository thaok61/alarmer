package com.thao_soft.alarmer.utils

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.thao_soft.alarmer.R
import com.thao_soft.alarmer.provider.AlarmInstance
import java.util.Calendar
import java.util.Locale

object AlarmUtils {
    fun getFormattedTime(context: Context, time: Calendar): String {
        val skeleton = if (DateFormat.is24HourFormat(context)) "EHm" else "Ehma"
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        return DateFormat.format(pattern, time) as String
    }

    fun getAlarmText(context: Context, instance: AlarmInstance, includeLabel: Boolean): String {
        val alarmTimeStr: String = getFormattedTime(context, instance.alarmTime)
        return if (instance.label!!.isEmpty() || !includeLabel) {
            alarmTimeStr
        } else {
            alarmTimeStr + " - " + instance.label
        }
    }

    fun formatElapsedTimeUntilAlarm(context: Context, delta: Long): String {
        // If the alarm will ring within 60 seconds, just report "less than a minute."
        var variableDelta = delta
        val formats = context.resources.getStringArray(R.array.alarm_set)
        if (variableDelta < DateUtils.MINUTE_IN_MILLIS) {
            return formats[0]
        }

        // Otherwise, format the remaining time until the alarm rings.

        // Round delta upwards to the nearest whole minute. (e.g. 7m 58s -> 8m)
        val remainder = variableDelta % DateUtils.MINUTE_IN_MILLIS
        variableDelta += if (remainder == 0L) 0 else DateUtils.MINUTE_IN_MILLIS - remainder
        var hours = variableDelta.toInt() / (1000 * 60 * 60)
        val minutes = variableDelta.toInt() / (1000 * 60) % 60
        val days = hours / 24
        hours %= 24

        val daySeq = Utils.getNumberFormattedQuantityString(context, R.plurals.days, days)
        val minSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.minutes, minutes)
        val hourSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.hours, hours)

        val showDays = days > 0
        val showHours = hours > 0
        val showMinutes = minutes > 0

        // Compute the index of the most appropriate time format based on the time delta.
        val index = ((if (showDays) 1 else 0)
                or (if (showHours) 2 else 0)
                or (if (showMinutes) 4 else 0))

        return String.format(formats[index], daySeq, hourSeq, minSeq)
    }
}