package com.thao_soft.alarmer.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thao_soft.alarmer.ui.components.ExpandableCard
import com.thao_soft.alarmer.ui.components.LabelDialog
import com.thao_soft.alarmer.ui.components.TimePickerDialog
import com.thao_soft.alarmer.viewmodels.AlarmViewModel
import java.util.Calendar


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldAlarmer(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    alarmViewModel: AlarmViewModel = viewModel(),
    onNavigateRingtone: (alarmId: Long) -> Unit
) {

    val alarmUiState by alarmViewModel.uiState.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("ScaffoldAlarmer", "ON_RESUME: ")
                alarmViewModel.getAllAlarms()
            }
        }

        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }

    }

    val openAlertDialog = remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            LargeFloatingActionButton(onClick = {
                alarmViewModel.changeTimePickerState(-1)
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
                        alarmViewModel.setSelectedIdx(idx)
                    }, onNavigateRingtone = onNavigateRingtone)
                }
            }
        }

        if (alarmUiState.showTimePicker) {
            val cal = Calendar.getInstance()
            val state = rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE)
            )

            TimePickerDialog(
                onCancel = { alarmViewModel.changeTimePickerState(-1) },
                onConfirm = {
                    alarmViewModel.onTimeSet(state.hour, state.minute, alarmUiState.selectedIdx)
                    alarmViewModel.changeTimePickerState(-1)
                },
            ) {
                TimePicker(state = state)
            }
        }

        if (openAlertDialog.value) {
            val label: String? =
                if (alarmUiState.selectedIdx == -1) "" else
                    alarmUiState.alarmAndInstances[alarmUiState.selectedIdx].alarm.label
            LabelDialog(
                label ?: "",
                onDismissRequest = { openAlertDialog.value = false },
                onConfirmation = {
                    openAlertDialog.value = false
                    alarmViewModel.updateAlarmLabel(alarmIdx = alarmUiState.selectedIdx, it)
                },
            )
        }
    }
}