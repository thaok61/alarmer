package com.thao_soft.alarmer.viewmodels

import android.app.Application
import android.content.ContentResolver
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thao_soft.alarmer.alarms.AlarmNotifications
import com.thao_soft.alarmer.alarms.AlarmStateManager
import com.thao_soft.alarmer.data.DataModel
import com.thao_soft.alarmer.provider.Alarm
import com.thao_soft.alarmer.provider.AlarmAndAlarmInstance
import com.thao_soft.alarmer.provider.AlarmInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
         val TAG: String = AlarmViewModel::class.java.simpleName
    }
    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()
    private val context = getApplication<Application>()
    private val cr: ContentResolver = getApplication<Application>().contentResolver
    private var mDeletedAlarm: Alarm? = null

    init {
        getAllAlarms()
    }


    private fun getAllAlarms() {
        viewModelScope.launch(Dispatchers.IO) {
            val alarms = AlarmAndAlarmInstance.getAlarms(cr)
            _uiState.value = AlarmUiState(alarms)
        }
    }

    fun enableAlarm(index: Int) {
        enableWhenAlarmChange(index) { alarm ->
            val newAlarm = alarm.copy(
                enabled = !alarm.enabled
            )
            updateAlarm(newAlarm, alarm.enabled, false)
            newAlarm
        }
    }

    fun enableWhenAlarmChange(index: Int, createAlarm: (Alarm) -> Alarm) {
        _uiState.update { state ->
            val newAlarmAndInstances = state.alarmAndInstances.toMutableList()
            val itemAlarm = newAlarmAndInstances[index].alarm
            val newAlarm = createAlarm(itemAlarm)
            val shouldUpdateSelectedAlarm = state.selectedAlarmAndAlarmInstance == newAlarmAndInstances[index]
            newAlarmAndInstances[index] = newAlarmAndInstances[index].copy(
                alarm = newAlarm
            )
            if (shouldUpdateSelectedAlarm) {
                state.copy(
                    alarmAndInstances = newAlarmAndInstances,
                    selectedAlarmAndAlarmInstance = newAlarmAndInstances[index]
                )
            } else {
                state.copy(
                    alarmAndInstances = newAlarmAndInstances
                )
            }

        }
    }

    fun enableVibrate(index: Int) {
        enableWhenAlarmChange(index) { alarm ->
            val newAlarm = alarm.copy(
                vibrate = !alarm.vibrate
            )
            updateAlarm(newAlarm, popToast = false, minorUpdate = true)
            newAlarm
        }
    }

    fun expandCard(alarmAndAlarmInstance: AlarmAndAlarmInstance?) {
        _uiState.update { state ->

            state.copy(
                selectedAlarmAndAlarmInstance = alarmAndAlarmInstance
            )
        }
    }

    fun enableDayOfWeeks(alarmIdx: Int, dayIdx: Int, checked: Boolean) {
        enableWhenAlarmChange(alarmIdx) { alarm ->
            val now = Calendar.getInstance()
            val oldNextAlarmTime = alarm.getNextAlarmTime(now)
            val weekday = DataModel.dataModel.weekdayOrder.calendarDays[dayIdx]
            val newAlarm = alarm.copy(
                daysOfWeek = alarm.daysOfWeek.setBit(weekday, checked)
            )
            val newNextAlarmTime = newAlarm.getNextAlarmTime(now)
            val popupToast = oldNextAlarmTime != newNextAlarmTime
            updateAlarm(newAlarm, popupToast, minorUpdate = false)
            newAlarm
        }
    }

    private fun updateAlarm(alarm: Alarm, popToast: Boolean, minorUpdate: Boolean) {

        viewModelScope.launch(Dispatchers.IO) {
            Alarm.updateAlarm(cr, alarm)

            if (minorUpdate) {
                val instanceList = AlarmInstance.getInstancesByAlarmId(cr, alarm.id)
                for (instance in instanceList) {
                    // Make a copy of the existing instance
                    val newInstance = instance.copy()
                    // Copy over minor change data to the instance; we don't know
                    // exactly which minor field changed, so just copy them all.
                    newInstance.vibrate = alarm.vibrate
                    newInstance.ringtone = alarm.alert
                    newInstance.label = alarm.label
                    // Since we copied the mId of the old instance and the mId is used
                    // as the primary key in the AlarmInstance table, this will replace
                    // the existing instance.
                    AlarmInstance.updateInstance(cr, newInstance)
                    // Update the notification for this instance.
                    AlarmNotifications.updateNotification(context, newInstance)
                }
            } else {
                AlarmStateManager.deleteAllInstances(context, alarm.id)
                if (alarm.enabled) setupAlarmInstance(alarm)
            }
        }

    }

    private suspend fun addAlarm(alarm: Alarm): AlarmAndAlarmInstance {
        val newAlarm = Alarm.addAlarm(cr, alarm)
        var alarmInstance: AlarmInstance? = null
        if (newAlarm.enabled) {
            alarmInstance = setupAlarmInstance(newAlarm)
        }
        return AlarmAndAlarmInstance(newAlarm, alarmInstance)
    }

    private suspend fun setupAlarmInstance(alarm: Alarm): AlarmInstance {
        val context = getApplication<Application>()
        val cr: ContentResolver = context.contentResolver
        var newInstance = alarm.createInstanceAfter(Calendar.getInstance())
        Log.d(TAG, "setupAlarmInstance: newInstance: ${newInstance.alarmTime.time}")
        Log.d(TAG, "setupAlarmInstance: alarm: ${alarm}")
        newInstance = AlarmInstance.addInstance(cr, newInstance)
        // Register instance to state manager
        AlarmStateManager.registerInstance(context, newInstance, true)
        return newInstance
    }

    fun onTimeSet(
        hourOfDay: Int,
        minute: Int,
        selectedAlarmAndAlarmInstance: AlarmAndAlarmInstance? = null
    ) {
        Log.d(TAG, "onTimeSet: hour: $hourOfDay, minute: $minute")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { state ->
                if (selectedAlarmAndAlarmInstance == null) {
                    // If mSelectedAlarm is null then we're creating a new alarm.
                    val a = withContext(Dispatchers.Main) {
                        Alarm.constructorHelper(hourOfDay, minute)
                    }
                    a.enabled = true

                    val newAlarmAndInstances = state.alarmAndInstances.toMutableList()

                    val newAlarmAndInstance = withContext(Dispatchers.IO) {
                        addAlarm(a)
                    }

                    newAlarmAndInstances.add(newAlarmAndInstance)
                    state.copy(
                        alarmAndInstances = newAlarmAndInstances
                    )
                } else {
                    selectedAlarmAndAlarmInstance.alarm.hour = hourOfDay
                    selectedAlarmAndAlarmInstance.alarm.minutes = minute
                    selectedAlarmAndAlarmInstance.alarm.enabled = true
                    updateAlarm(
                        selectedAlarmAndAlarmInstance.alarm,
                        popToast = true,
                        minorUpdate = false
                    )
                    state.copy(
                        selectedAlarmAndAlarmInstance = null
                    )
                }
            }
        }


    }

    fun onClickDelete(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { state ->
                val newAlarmAndInstances = state.alarmAndInstances.toMutableList()
                val deleteAlarmAndAlarmInstance = newAlarmAndInstances[index]

                val isDeleted = deleteAlarm(deleteAlarmAndAlarmInstance.alarm)
                if (isDeleted) {
                    newAlarmAndInstances.remove(deleteAlarmAndAlarmInstance)
                    state.copy(
                        alarmAndInstances = newAlarmAndInstances
                    )
                } else {
                    state
                }

            }
        }
    }


    private suspend fun deleteAlarm(deleteAlarm: Alarm?): Boolean {
        if (deleteAlarm == null) {
            return false
        }

        AlarmStateManager.deleteAllInstances(context, deleteAlarm.id)
        val isDeleted = Alarm.deleteAlarm(cr, deleteAlarm.id)

        if (isDeleted) {
            mDeletedAlarm = deleteAlarm
        }
        return isDeleted
    }

}