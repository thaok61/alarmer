package com.thao_soft.alarmer.viewmodels

import com.thao_soft.alarmer.provider.AlarmAndAlarmInstance

data class AlarmUiState(
    val alarmAndInstances: List<AlarmAndAlarmInstance> = mutableListOf(),
    val showSnackBar: Boolean = false,
    val selectedAlarmAndAlarmInstance: AlarmAndAlarmInstance? = null
)