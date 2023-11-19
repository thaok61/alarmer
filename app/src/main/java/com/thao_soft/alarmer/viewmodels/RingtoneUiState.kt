package com.thao_soft.alarmer.viewmodels

import com.thao_soft.alarmer.data.Ringtone

data class RingtoneUiState(
    val items: List<Ringtone> = mutableListOf(),
    val selectedIdx: Int  = -1,
)