package com.thao_soft.alarmer

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thao_soft.alarmer.data.DataModel
import com.thao_soft.alarmer.data.Weekdays
import com.thao_soft.alarmer.provider.Alarm
import com.thao_soft.alarmer.provider.AlarmAndAlarmInstance
import com.thao_soft.alarmer.ui.theme.AlarmerTheme
import com.thao_soft.alarmer.utils.Utils
import com.thao_soft.alarmer.viewmodels.AlarmUiState
import com.thao_soft.alarmer.viewmodels.AlarmViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlarmerTheme {
                // A surface container using the 'background' color from the theme
                ScaffoldAlarmer()
            }
        }
    }

    companion object {
        /** UTC does not have DST rules and will not alter the [.mHour] and [.mMinute].  */
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldAlarmer(alarmViewModel: AlarmViewModel = viewModel()) {

    val alarmUiState by alarmViewModel.uiState.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }


    val snackState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    val openAlertDialog = remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            LargeFloatingActionButton(onClick = {
                showTimePicker = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }, floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            items(alarmUiState.alarmAndInstances.size) { idx ->
                Row(modifier = Modifier.padding(8.dp)) {
                    ExpandableCard(alarmUiState, idx, alarmViewModel, onClickLabel = {
                        openAlertDialog.value = true
                    })
                }
            }
        }

        if (showTimePicker) {
            val cal = Calendar.getInstance()
            val state = rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE)
            )

            TimePickerDialog(
                onCancel = { showTimePicker = false },
                onConfirm = {
                    alarmViewModel.onTimeSet(state.hour, state.minute)
                    showTimePicker = false
                },
            ) {
                TimePicker(state = state)
            }
        }

        if (openAlertDialog.value) {
            AlertDialogExample(
                onDismissRequest = { openAlertDialog.value = false },
                onConfirmation = {
                    openAlertDialog.value = false
                    Log.d("ScaffoldAlarmer", "ScaffoldAlarmer: $it")
                },
            )
        }
    }
    SnackbarHost(hostState = snackState)
}

@Composable
fun TimePickerDialog(
    title: String = "Select Time",
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    toggle: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min)
                .background(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ),
        ) {
            toggle()
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = title,
                    style = MaterialTheme.typography.labelMedium
                )
                content()
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onCancel
                    ) { Text("Cancel") }
                    TextButton(
                        onClick = onConfirm
                    ) { Text("OK") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableCard(
    alarmUiState: AlarmUiState,
    alarmIdx: Int,
    alarmViewModel: AlarmViewModel,
    onClickLabel: () -> Unit
) {
    val alarmAndAlarmInstance = alarmUiState.alarmAndInstances[alarmIdx]
    val alarm = alarmAndAlarmInstance.alarm
    val alarmInstance = alarmAndAlarmInstance.alarmInstance


    val expandedState = alarmUiState.selectedAlarmAndAlarmInstance == alarmAndAlarmInstance
    val rotateArrowState by animateFloatAsState(
        targetValue = if (expandedState) 180f else 0f, label = "rotateArrowState"
    )
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
                    modifier = Modifier.weight(1f), "Add label", R.drawable.ic_label, expandedState,
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
            ClockItemView(modifier = Modifier, alarm)

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
                ExpandableDetailCard(alarm, alarmIdx, alarmViewModel)
            }
        }
    }
}

@Composable
fun AlertDialogExample(
    onDismissRequest: () -> Unit,
    onConfirmation: (textInput: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        text = {
            OutlinedTextField(
                text,
                onValueChange = { text = it },
                label = { Text("Label") },
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation(text)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Cancel")
            }
        }
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun TextAndIcon(
    modifier: Modifier,
    text: String,
    @DrawableRes icon: Int,
    iconVisible: Boolean,
    content: @Composable (() -> Unit) = { },
    onClick: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier
                .weight(5f)
                .clickable {
                    if (onClick != null) {
                        onClick()
                    }
                }) {
            AnimatedVisibility(iconVisible) {
                Image(
                    painterResource(icon),
                    "Label",
                    colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(text = text)
        }
        content()

    }
}

@Composable
fun ClockItemView(modifier: Modifier, alarm: Alarm) {
    val time = updateTime(alarm)
    Row(modifier, verticalAlignment = Alignment.Bottom) {
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

@Composable
fun ExpandableDetailCard(alarm: Alarm, alarmIdx: Int, alarmViewModel: AlarmViewModel) {

    val weekdays = DataModel.dataModel.weekdayOrder.calendarDays
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
        TextAndIcon(
            modifier = Modifier, "Default (Cesium)", R.drawable.ic_ringtone, true
        )
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

@Composable
fun ChipDay(modifier: Modifier, text: String, selected: Boolean, onClick: () -> Unit) {
    Box(modifier = modifier.fillMaxWidth()) {
        AssistChip(
            modifier = Modifier
                .size(50.dp)
                .padding(4.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ),
            onClick = { onClick() },
            label = {
                Text(
                    text = text,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = CircleShape,
            border = AssistChipDefaults.assistChipBorder(
                borderColor =
                if (selected) Color.Transparent
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AlarmerTheme {
        ScaffoldAlarmer()
    }
}