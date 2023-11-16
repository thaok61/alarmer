package com.thao_soft.alarmer.data

import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat
import java.util.Calendar

class TimeModel(private val mContext: Context) {
    /**
     * @return the current time in milliseconds
     */
    fun currentTimeMillis(): Long = System.currentTimeMillis()

    /**
     * @return milliseconds since boot, including time spent in sleep
     */
    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    /**
     * @return `true` if 24 hour time format is selected; `false` otherwise
     */
    fun is24HourFormat(): Boolean = DateFormat.is24HourFormat(mContext)

    /**
     * @return a new Calendar with the [.currentTimeMillis]
     */
    val calendar: Calendar
        get() {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = currentTimeMillis()
            return calendar
        }
}