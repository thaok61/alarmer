package com.thao_soft.alarmer

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thao_soft.alarmer.provider.Alarm
import com.thao_soft.alarmer.provider.AlarmInstance
import com.thao_soft.alarmer.ui.components.ClockItemView
import com.thao_soft.alarmer.ui.theme.AlarmerTheme
import com.thao_soft.alarmer.viewmodels.AlarmFullScreenViewModel
import kotlinx.coroutines.delay
import java.util.Calendar

class AlarmFullScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.data == null) {
            finish()
        }
        setContent {
            val instanceId = AlarmInstance.getId(intent.data!!)
            AlarmerTheme {
                // A surface container using the 'background' color from the theme
                ScaffoldFullScreenAlarmer(instanceId)
            }
        }
    }
}

@Composable
fun ScaffoldFullScreenAlarmer(
    instanceId: Long,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    alarmFullScreenViewModel: AlarmFullScreenViewModel = viewModel()
) {
    val alarmUiState by alarmFullScreenViewModel.uiState.collectAsState()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                alarmFullScreenViewModel.getInstanceById(instanceId)
            }


        }

        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }

    }

    Scaffold { innerPadding ->
        val calendarState = remember {
            mutableStateOf(Calendar.getInstance())
        }

        LaunchedEffect(key1 = calendarState) {
            while (true) {
                delay(500)
                calendarState.value = Calendar.getInstance()
            }
        }
        val calendar = Calendar.getInstance()
        val alarm = Alarm.constructorHelper(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.weight(1f))
            ClockItemView(modifier = Modifier.padding(innerPadding), alarm = alarm) {}
            Box(modifier = Modifier.weight(1f))
            Text(text = alarmUiState.instance?.label ?: "")
            Box(modifier = Modifier.weight(1f))
            RowButton(alarmUiState.instance, alarmFullScreenViewModel)
            Box(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowButton(
    instance: AlarmInstance?,
    alarmFullScreenViewModel: AlarmFullScreenViewModel
) {
    val activity = LocalContext.current as Activity
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        Button(onClick = {
            if (instance != null) {
                alarmFullScreenViewModel.setSnoozeState(instance)
                activity.finish()
            }
        }, modifier = Modifier.size(100.dp)) {
            Text(text = "Snooze")
        }
        Box(modifier = Modifier.weight(1f))
        Button(onClick = {
            if (instance != null) {
                alarmFullScreenViewModel.deleteInstanceAndUpdateParent(instance)
                activity.finish()
            }
        }, Modifier.size(100.dp)) {
            Text(text = "Stop")
        }
    }
}