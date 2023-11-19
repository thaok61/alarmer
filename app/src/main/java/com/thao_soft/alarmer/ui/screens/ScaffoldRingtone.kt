package com.thao_soft.alarmer.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thao_soft.alarmer.R
import com.thao_soft.alarmer.viewmodels.RingtoneUiState
import com.thao_soft.alarmer.viewmodels.RingtoneViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldRingtone(
    alarmId: Long,
    onNavigateUp: () -> Unit,
    ringtoneViewModel: RingtoneViewModel = viewModel(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {

    val ringtoneUiState by ringtoneViewModel.uiState.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("ScaffoldRingtone", "ON_RESUME: ")
                ringtoneViewModel.getListRingtone(alarmId)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                Log.d("ScaffoldRingtone", "ON_PAUSE: ")
                ringtoneViewModel.updateAlarm()
                ringtoneViewModel.stopPlayingRingtone(
                    ringtoneUiState.items[ringtoneUiState.selectedIdx],
                    false
                )
            }
        }

        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }

    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                navigationIcon =
                {
                    IconButton(onClick = { onNavigateUp() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },

                title = {
                    Text("Ringtone")
                }
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(ringtoneUiState.items.size) { idx ->
                RingtoneCard(idx, ringtoneViewModel, ringtoneUiState)
            }
        }
    }
}


@Composable
fun RingtoneCard(idx: Int, ringtoneViewModel: RingtoneViewModel, ringtoneUiState: RingtoneUiState) {
    val ringtone = ringtoneUiState.items[idx]
    Row(modifier = Modifier
        .clickable {
            ringtoneViewModel.onClickRingTone(idx)
        }
        .padding(16.dp)
    ) {
        Image(
            painterResource(if (ringtone.isPlaying) R.drawable.ic_ringtone_active_static else R.drawable.ic_ringtone),
            "Label",
            colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = ringtone.name ?: "")
        Box(modifier = Modifier.weight(1f))
        if (ringtone.isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Checked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}