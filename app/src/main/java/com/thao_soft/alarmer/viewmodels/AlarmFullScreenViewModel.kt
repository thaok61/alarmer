package com.thao_soft.alarmer.viewmodels

import android.app.Application
import android.content.ContentResolver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thao_soft.alarmer.alarms.AlarmStateManager
import com.thao_soft.alarmer.provider.AlarmInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmFullScreenViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        val TAG: String = AlarmViewModel::class.java.simpleName
    }

    private val _uiState = MutableStateFlow(AlarmFullScreenState())
    val uiState: StateFlow<AlarmFullScreenState> = _uiState.asStateFlow()
    private val context = getApplication<Application>()
    private val cr: ContentResolver = getApplication<Application>().contentResolver
    fun getInstanceById(instanceId: Long) {
        viewModelScope.launch {

            val alarmInstance = withContext(Dispatchers.IO) {
                AlarmInstance.getInstanceHelper(cr, instanceId)
            }

            _uiState.update {
                it.copy(instance = alarmInstance)
            }
        }
    }

    fun setSnoozeState(instance: AlarmInstance) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmStateManager.setSnoozeState(context, instance, false /* showToast */)
        }
    }

    fun deleteInstanceAndUpdateParent(instance: AlarmInstance) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmStateManager.deleteInstanceAndUpdateParent(context, instance)
        }
    }
}