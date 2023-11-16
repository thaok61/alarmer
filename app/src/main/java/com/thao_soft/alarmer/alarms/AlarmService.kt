package com.thao_soft.alarmer.alarms

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.thao_soft.alarmer.AlarmAlertWakeLock
import com.thao_soft.alarmer.R
import com.thao_soft.alarmer.data.InstancesColumns
import com.thao_soft.alarmer.events.Events
import com.thao_soft.alarmer.provider.AlarmInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmService : Service() {
    /** Binder given to AlarmActivity.  */
    private val mBinder: IBinder = Binder()

    /** Whether the service is currently bound to AlarmActivity  */
    private var mIsBound = false

    /** Listener for changes in phone state.  */
    private val mPhoneStateListener = PhoneStateChangeListener()

    /** Whether the receiver is currently registered  */
    private var mIsRegistered = false

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onBind(intent: Intent?): IBinder {
        mIsBound = true
        return mBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mIsBound = false
        return super.onUnbind(intent)
    }

    private lateinit var mTelephonyManager: TelephonyManager
    private var mCurrentAlarm: AlarmInstance? = null

    private suspend fun startAlarm(instance: AlarmInstance) {
        Log.d(TAG, "AlarmService.start with instance: " + instance.id)
        if (mCurrentAlarm != null) {
            AlarmStateManager.setMissedState(this, mCurrentAlarm!!)
            stopCurrentAlarm()
        }

        AlarmAlertWakeLock.acquireCpuWakeLock(this)

        mCurrentAlarm = instance
        AlarmNotifications.showAlarmNotification(this, mCurrentAlarm!!)
        mTelephonyManager.listen(mPhoneStateListener.init(), PhoneStateListener.LISTEN_CALL_STATE)
        AlarmKlaxon.start(this, mCurrentAlarm!!)
        sendBroadcast(Intent(ALARM_ALERT_ACTION))
    }

    private fun stopCurrentAlarm() {
        if (mCurrentAlarm == null) {
            Log.d(TAG, "There is no current alarm to stop")
            return
        }

        val instanceId = mCurrentAlarm!!.id
        Log.d(TAG, "AlarmService.stop with instance: $instanceId")

        AlarmKlaxon.stop(this)
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE)
        sendBroadcast(Intent(ALARM_DONE_ACTION))

        stopForeground(STOP_FOREGROUND_REMOVE)

        mCurrentAlarm = null
        AlarmAlertWakeLock.releaseCpuLock()
    }

    private val mActionsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            Log.i(TAG, "AlarmService received intent $action")
            if (mCurrentAlarm == null ||
                mCurrentAlarm!!.alarmState != InstancesColumns.FIRED_STATE) {
                Log.i(TAG, "No valid firing alarm")
                return
            }

            if (mIsBound) {
                Log.i(TAG, "AlarmActivity bound; AlarmService no-op")
                return
            }
            serviceScope.launch {
                when (action) {
                    ALARM_SNOOZE_ACTION -> {
                        // Set the alarm state to snoozed.
                        // If this broadcast receiver is handling the snooze intent then AlarmActivity
                        // must not be showing, so always show snooze toast.
                        AlarmStateManager.setSnoozeState(
                            context,
                            mCurrentAlarm!!,
                            true /* showToast */
                        )
                        Events.sendAlarmEvent(R.string.action_snooze, R.string.label_intent)
                    }

                    ALARM_DISMISS_ACTION -> {

                        // Set the alarm state to dismissed.
                        AlarmStateManager.deleteInstanceAndUpdateParent(context, mCurrentAlarm!!)
                        Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_intent)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mTelephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Register the broadcast receiver
        val filter = IntentFilter(ALARM_SNOOZE_ACTION)
        filter.addAction(ALARM_DISMISS_ACTION)
        registerReceiver(mActionsReceiver, filter, Context.RECEIVER_EXPORTED)
        mIsRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmService.onStartCommand() with $intent")
        if (intent == null) {
            return START_NOT_STICKY
        }

        val instanceId = AlarmInstance.getId(intent.data!!)
        when (intent.action) {
            AlarmStateManager.CHANGE_STATE_ACTION -> {
                serviceScope.launch(Dispatchers.Main) {
                    withContext(Dispatchers.IO) {
                        AlarmStateManager.handleIntent(this@AlarmService, intent)
                    }


                    // If state is changed to firing, actually fire the alarm!
                    val alarmState: Int =
                        intent.getIntExtra(AlarmStateManager.ALARM_STATE_EXTRA, -1)
                    if (alarmState == InstancesColumns.FIRED_STATE) {

                        val cr: ContentResolver = this@AlarmService.contentResolver
                        val instance: AlarmInstance? = AlarmInstance.getInstance(cr, instanceId)
                        if (instance == null) {
                            Log.e(TAG, "No instance found to start alarm: $instanceId")
                            if (mCurrentAlarm != null) {
                                // Only release lock if we are not firing alarm
                                AlarmAlertWakeLock.releaseCpuLock()
                            }
                        } else if (mCurrentAlarm != null && mCurrentAlarm!!.id == instanceId) {
                            Log.e(TAG, "Alarm already started for instance: $instanceId")
                        } else {
                            startAlarm(instance)
                        }
                    }
                }
            }
            STOP_ALARM_ACTION -> {
                if (mCurrentAlarm != null && mCurrentAlarm!!.id != instanceId) {
                    Log.e(TAG, "Can't stop alarm for instance: $instanceId because current alarm is: ${mCurrentAlarm!!.id}" )
                } else {
                    stopCurrentAlarm()
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "AlarmService.onDestroy() called")
        super.onDestroy()
        if (mCurrentAlarm != null) {
            stopCurrentAlarm()
        }

        if (mIsRegistered) {
            unregisterReceiver(mActionsReceiver)
            mIsRegistered = false
        }
        serviceJob.cancel()
    }

    private inner class PhoneStateChangeListener : PhoneStateListener() {
        private var mPhoneCallState = 0

        fun init(): PhoneStateChangeListener {
            mPhoneCallState = -1
            return this
        }

        override fun onCallStateChanged(state: Int, ignored: String?) {
            if (mPhoneCallState == -1) {
                mPhoneCallState = state
            }

            if (state != TelephonyManager.CALL_STATE_IDLE && state != mPhoneCallState) {
                startService(AlarmStateManager.createStateChangeIntent(this@AlarmService,
                    "AlarmService", mCurrentAlarm!!, InstancesColumns.MISSED_STATE))
            }
        }
    }

    companion object {
        val TAG: String = AlarmService::class.java.simpleName
        /**
         * AlarmActivity and AlarmService (when unbound) listen for this broadcast intent
         * so that other applications can snooze the alarm (after ALARM_ALERT_ACTION and before
         * ALARM_DONE_ACTION).
         */
        const val ALARM_SNOOZE_ACTION = "com.thao_soft.alarmer.ALARM_SNOOZE"

        /**
         * AlarmActivity and AlarmService listen for this broadcast intent so that other
         * applications can dismiss the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
         */
        const val ALARM_DISMISS_ACTION = "com.thao_soft.alarmer.ALARM_DISMISS"

        /** A public action sent by AlarmService when the alarm has started.  */
        const val ALARM_ALERT_ACTION = "com.thao_soft.alarmer.ALARM_ALERT"

        /** A public action sent by AlarmService when the alarm has stopped for any reason.  */
        const val ALARM_DONE_ACTION = "com.thao_soft.alarmer.ALARM_DONE"

        /** Private action used to stop an alarm with this service.  */
        const val STOP_ALARM_ACTION = "com.thao_soft.alarmer.STOP_ALARM"

        /**
         * Utility method to help stop an alarm properly. Nothing will happen, if alarm is not firing
         * or using a different instance.
         *
         * @param context application context
         * @param instance you are trying to stop
         */
        @JvmStatic
        fun stopAlarm(context: Context, instance: AlarmInstance) {
            val intent: Intent =
                AlarmInstance.createIntent(context, AlarmService::class.java, instance.id)
                    .setAction(STOP_ALARM_ACTION)

            // We don't need a wake lock here, since we are trying to kill an alarm
            context.startService(intent)
        }
    }
}