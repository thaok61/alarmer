package com.thao_soft.alarmer.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thao_soft.alarmer.MainActivity
import com.thao_soft.alarmer.data.DataModel
import com.thao_soft.alarmer.provider.Alarm
import com.thao_soft.alarmer.utils.Utils
import java.util.Calendar

@Composable
fun ClockItemView(modifier: Modifier, alarm: Alarm, onClickClock: () -> Unit) {
    val time = updateTime(alarm)
    Row(modifier.clickable {
        onClickClock()
    }, verticalAlignment = Alignment.Bottom) {
        Text(text = time.toString(), style = MaterialTheme.typography.headlineLarge)
    }
}

private fun updateTime(alarm: Alarm): CharSequence {
    val mFormat12: CharSequence = Utils.get12ModeFormat(0.3f, false)
    val mFormat24: CharSequence = Utils.get24ModeFormat(false)
    val format24Requested: Boolean = DataModel.dataModel.is24HourFormat()
    val format = if (format24Requested) {
        mFormat24
    } else {
        mFormat12
    }
    // Format the time relative to UTC to ensure hour and minute are not adjusted for DST.
    val calendar: Calendar = DataModel.dataModel.calendar
    calendar.timeZone = MainActivity.UTC
    calendar[Calendar.HOUR_OF_DAY] = alarm.hour
    calendar[Calendar.MINUTE] = alarm.minutes
    return DateFormat.format(format, calendar)
}