package com.thao_soft.alarmer.viewmodels

import android.app.Application
import android.content.ContentResolver
import android.database.MatrixCursor
import android.media.AudioManager
import android.media.RingtoneManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thao_soft.alarmer.R
import com.thao_soft.alarmer.data.Ringtone
import com.thao_soft.alarmer.provider.Alarm
import com.thao_soft.alarmer.ringtone.RingtonePreviewKlaxon
import com.thao_soft.alarmer.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RingtoneViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(RingtoneUiState())
    val uiState: StateFlow<RingtoneUiState> = _uiState.asStateFlow()
    private val context = getApplication<Application>()
    private val cr: ContentResolver = getApplication<Application>().contentResolver

    companion object {
        const val TAG: String = "RingtoneViewModel"
    }

    var alarm: Alarm? = null

    fun getListRingtone(alarmId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            alarm = Alarm.getAlarm(cr, alarmId)
            if (alarm == null) {
                return@launch
            }
            val ringtoneManager = RingtoneManager(context)
            ringtoneManager.setType(AudioManager.STREAM_ALARM)

            val systemRingtoneCursor = try {
                ringtoneManager.cursor
            } catch (e: Exception) {
                Log.e(TAG, "Could not get system ringtone cursor")
                MatrixCursor(arrayOf())
            }

            val systemRingtoneCount = systemRingtoneCursor.count
            val listRingtone = mutableListOf<Ringtone>()

            listRingtone.add(
                Ringtone(
                    Utils.RINGTONE_SILENT,
                    null,
                    isSelected = Utils.RINGTONE_SILENT == alarm!!.alert
                )
            )
            listRingtone.add(
                Ringtone(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), context.getString(
                        R.string.default_alarm_ringtone_title
                    ),
                    isSelected = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) == alarm!!.alert
                )
            )

            for (i in 0 until systemRingtoneCount) {
                val ringtoneUri = ringtoneManager.getRingtoneUri(i)
                Log.d(TAG, "getListRingtone: system: $ringtoneUri")
                listRingtone.add(
                    Ringtone(
                        ringtoneUri,
                        null,
                        isSelected = ringtoneUri == alarm!!.alert
                    )
                )
            }
            var selectedIdx = -1

            for (i in 0 until listRingtone.size) {
                if (listRingtone[i].isSelected) {
                    selectedIdx = i
                }
            }

            _uiState.update { state ->
                if (state.selectedIdx == -1) {
                    state.copy(items = listRingtone, selectedIdx = selectedIdx)
                } else {
                    state
                }
            }

        }
    }

    fun onClickRingTone(idx: Int) {
        viewModelScope.launch {
            _uiState.update { state ->
                var oldSelection: Ringtone? = null
                val listRingtone = state.items.toMutableList()
                if (state.selectedIdx != -1) {
                    oldSelection = listRingtone[state.selectedIdx]
                }
                var newSelection = listRingtone[idx]
                Log.d(TAG, "onClickRingTone: ${state.selectedIdx} --- curIdx: $idx")
                Log.d(TAG, "onClickRingTone: ${newSelection.isPlaying} --- isPlaying")
                if (state.selectedIdx == idx) {

                    newSelection = if (newSelection.isPlaying) {
                        stopPlayingRingtone(newSelection, false)
                    } else {
                        startPlayingRingtone(newSelection)
                    }
                    listRingtone[idx] = newSelection.copy()
                } else {
                    if (oldSelection != null) {
                        oldSelection = stopPlayingRingtone(oldSelection, true)
                    }
                    if (oldSelection != null) {
                        listRingtone[state.selectedIdx] = oldSelection.copy()
                    }
                    newSelection = startPlayingRingtone(newSelection)
                    listRingtone[idx] = newSelection.copy()
                }
                state.copy(items = listRingtone, selectedIdx = idx)
            }
        }
    }

    fun updateAlarm() {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {

            if (alarm != null && state.selectedIdx != -1) {
                alarm!!.alert = state.items[state.selectedIdx].uri
                Alarm.updateAlarm(cr, alarm!!)
            }
        }
    }

    private fun startPlayingRingtone(ringtone: Ringtone): Ringtone {
        if (!ringtone.isPlaying && !ringtone.isSilent) {
            RingtonePreviewKlaxon.start(context, ringtone.uri)
            ringtone.isPlaying = true
        }

        if (!ringtone.isSelected) {
            ringtone.isSelected = true
        }
        return ringtone
    }

    fun stopPlayingRingtone(ringtone: Ringtone, deselect: Boolean): Ringtone {
        if (ringtone.isPlaying) {
            RingtonePreviewKlaxon.stop(context)
            ringtone.isPlaying = false
        }

        if (deselect && ringtone.isSelected) {
            ringtone.isSelected = false
        }
        return ringtone
    }
}