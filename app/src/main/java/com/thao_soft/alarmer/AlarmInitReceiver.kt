package com.thao_soft.alarmer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.thao_soft.alarmer.AlarmAlertWakeLock.createPartialWakeLock
import com.thao_soft.alarmer.alarms.AlarmStateManager
import com.thao_soft.alarmer.alarms.goAsync
import com.thao_soft.alarmer.data.DataModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmInitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = goAsync {
        val action = intent.action
        Log.d(TAG, "AlarmInitReceiver $action")

        val wl = createPartialWakeLock(context)
        wl.acquire(10 * 60 * 1000L /*10 minutes*/)

        // We need to increment the global id out of the async task to prevent race conditions
        withContext(Dispatchers.Main) {
            DataModel.dataModel.updateGlobalIntentId()
        }


        // Process restored data if any exists
        if (!DeskClockBackupAgent.processRestoredData(context)) {
            // Update all the alarm instances on time change event
            AlarmStateManager.fixAlarmInstances(context)
        }
        wl.release()
        Log.d(TAG, "AlarmInitReceiver finished")
    }

    companion object {


        const val TAG: String = "AlarmInitReceiver"
    }
}