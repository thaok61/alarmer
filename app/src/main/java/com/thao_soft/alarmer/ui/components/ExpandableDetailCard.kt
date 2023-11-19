package com.thao_soft.alarmer.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thao_soft.alarmer.R
import com.thao_soft.alarmer.data.DataModel
import com.thao_soft.alarmer.data.Weekdays
import com.thao_soft.alarmer.provider.Alarm
import com.thao_soft.alarmer.viewmodels.AlarmViewModel


@Composable
fun ExpandableDetailCard(
    alarm: Alarm,
    alarmIdx: Int,
    alarmViewModel: AlarmViewModel,
    onNavigateRingtone: (alarmId: Long) -> Unit
) {

    val weekdays = DataModel.dataModel.weekdayOrder.calendarDays
    val ringtoneTitle = DataModel.dataModel.getRingtoneTitle(alarm.alert)
    Column {
        LazyRow {
            items(weekdays.size) { idx ->
                val weekday = Weekdays.getShortWeekday(weekdays[idx])
                val selected = alarm.daysOfWeek.isBitOn(weekdays[idx])
                ChipDay(
                    modifier = Modifier.weight(1f), "$weekday", selected
                ) {
                    alarmViewModel.enableDayOfWeeks(alarmIdx, idx, !selected)
                }
            }
        }
        Box(modifier = Modifier.height(8.dp))
        TextAndIcon(
            modifier = Modifier,
            ringtoneTitle ?: "",
            R.drawable.ic_ringtone,
            true
        ) {
            onNavigateRingtone(alarm.id)
        }
        TextAndIcon(
            modifier = Modifier, "Vibrate", R.drawable.ic_vibrator, true,
            {
                Checkbox(checked = alarm.vibrate,
                    onCheckedChange = { alarmViewModel.enableVibrate(alarmIdx) })
            },
        )

        TextAndIcon(modifier = Modifier,
            "Delete",
            R.drawable.ic_delete_small,
            true,
            onClick = { alarmViewModel.onClickDelete(alarmIdx) })
    }
}