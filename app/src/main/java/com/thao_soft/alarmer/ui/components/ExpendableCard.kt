package com.thao_soft.alarmer.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thao_soft.alarmer.R
import com.thao_soft.alarmer.data.Weekdays
import com.thao_soft.alarmer.provider.AlarmAndAlarmInstance
import com.thao_soft.alarmer.viewmodels.AlarmUiState
import com.thao_soft.alarmer.viewmodels.AlarmViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableCard(
    alarmUiState: AlarmUiState,
    alarmIdx: Int,
    alarmViewModel: AlarmViewModel,
    onClickLabel: () -> Unit,
    onNavigateRingtone: (alarmId: Long) -> Unit
) {
    val alarmAndAlarmInstance = alarmUiState.alarmAndInstances[alarmIdx]
    val alarm = alarmAndAlarmInstance.alarm

    val expandedState = alarmUiState.selectedAlarmAndAlarmInstance == alarmAndAlarmInstance
    val rotateArrowState by animateFloatAsState(
        targetValue = if (expandedState) 180f else 0f, label = "rotateArrowState"
    )
    val label: String = if (alarm.label.isNullOrEmpty()) "Add label" else alarm.label!!
    Card(
        modifier = Modifier.animateContentSize(),
        onClick = {
            if (expandedState) {
                alarmViewModel.expandCard(null)
            } else {
                alarmViewModel.expandCard(alarmAndAlarmInstance)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {

            Row {
                TextAndIcon(
                    modifier = Modifier.weight(1f), label, R.drawable.ic_label, expandedState,
                    {
                        IconButton(modifier = Modifier
                            .size(32.dp)
                            .rotate(rotateArrowState),
                            onClick = {
                                if (expandedState) {
                                    alarmViewModel.expandCard(null)
                                } else {
                                    alarmViewModel.expandCard(alarmAndAlarmInstance)
                                }
                            }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "arrow Down"
                            )
                        }
                    },
                    onClick = {
                        onClickLabel()
                    }
                )

            }
            ClockItemView(modifier = Modifier, alarm) {
                alarmViewModel.changeTimePickerState(alarmIdx)
            }

            Row {
                if (alarm.daysOfWeek == Weekdays.NONE) {
                    val labelText: String = if (AlarmAndAlarmInstance.isTomorrow(
                            alarmAndAlarmInstance, Calendar.getInstance()
                        )
                    ) {
                        stringResource(R.string.alarm_tomorrow)
                    } else {
                        stringResource(R.string.alarm_today)
                    }
                    Text(
                        modifier = Modifier.weight(6f), text = labelText
                    )
                } else {
                    Text(
                        modifier = Modifier.weight(6f), text = alarm.daysOfWeek.toString(
                            LocalContext.current, Weekdays.Order.MON_TO_SUN
                        )
                    )
                }

                Switch(
                    modifier = Modifier.weight(1f),
                    checked = alarm.enabled,
                    onCheckedChange = {

                        alarmViewModel.enableAlarm(alarmIdx)
                    },
                    colors = SwitchDefaults.colors()
                )
            }
            AnimatedVisibility(expandedState) {
                ExpandableDetailCard(alarm, alarmIdx, alarmViewModel, onNavigateRingtone)
            }
        }
    }
}