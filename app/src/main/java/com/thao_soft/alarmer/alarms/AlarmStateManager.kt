package com.thao_soft.alarmer.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.thao_soft.alarmer.AlarmAlertWakeLock
import com.thao_soft.alarmer.MainActivity
import com.thao_soft.alarmer.R
import com.thao_soft.alarmer.data.DataModel
import com.thao_soft.alarmer.data.InstancesColumns
import com.thao_soft.alarmer.events.Events
import com.thao_soft.alarmer.provider.Alarm
import com.thao_soft.alarmer.provider.AlarmInstance
import com.thao_soft.alarmer.provider.ClockProvider
import com.thao_soft.alarmer.utils.AlarmUtils
import com.thao_soft.alarmer.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class AlarmStateManager : BroadcastReceiver() {

    interface StateChangeScheduler {
        fun scheduleInstanceStateChange(
            context: Context,
            time: Calendar,
            instance: AlarmInstance,
            newState: Int
        )

        fun cancelScheduledInstanceStateChange(context: Context, instance: AlarmInstance)
    }

    override fun onReceive(context: Context, intent: Intent) = goAsync {
        if (INDICATOR_ACTION == intent.action) {
            return@goAsync
        }
        val wl: PowerManager.WakeLock = AlarmAlertWakeLock.createPartialWakeLock(context)
        wl.acquire(10 * 60 * 1000L /*10 minutes*/)
        handleIntent(context, intent)
        wl.release()
    }




    companion object {

        private var sStateChangeScheduler: StateChangeScheduler = AlarmManagerStateChangeScheduler()

        suspend fun handleIntent(context: Context, intent: Intent) {
            val action: String? = intent.action
            Log.d(TAG, "AlarmStateManager received intent $intent")

            if (CHANGE_STATE_ACTION == action) {
                val uri = intent.data!!
                val instance: AlarmInstance? =
                    AlarmInstance.getInstance(context.contentResolver, AlarmInstance.getId(uri))

                if (instance == null) {
                    Log.e(TAG, "Can not change state for unknown instance: $uri")
                    return
                }

                val globalId = DataModel.dataModel.globalIntentId
                val intentId: Int = intent.getIntExtra(ALARM_GLOBAL_ID_EXTRA, -1)
                val alarmState: Int = intent.getIntExtra(ALARM_STATE_EXTRA, -1)

                if (intentId != globalId) {
                    Log.i(
                        TAG, "IntentId: " + intentId + " GlobalId: " + globalId +
                                " AlarmState: " + alarmState
                    )

                    if (!intent.hasCategory(ALARM_DISMISS_TAG) &&
                        !intent.hasCategory(ALARM_SNOOZE_TAG)
                    ) {
                        Log.i(TAG, "Ignoring old Intent")
                        return
                    }
                }

                if (intent.getBooleanExtra(FROM_NOTIFICATION_EXTRA, false)) {
                    if (intent.hasCategory(ALARM_DISMISS_TAG)) {
                        Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_notification)
                    } else if (intent.hasCategory(ALARM_SNOOZE_TAG)) {
                        Events.sendAlarmEvent(R.string.action_snooze, R.string.label_notification)
                    }
                }

                if (alarmState >= 0) {
                    setAlarmState(context, instance, alarmState)
                } else {
                    registerInstance(context, instance, true)
                }
            } else if (SHOW_AND_DISMISS_ALARM_ACTION == action) {
                val uri: Uri = intent.data!!
                val instance: AlarmInstance? =
                    AlarmInstance.getInstance(
                        context.contentResolver,
                        AlarmInstance.getId(uri)
                    )

                if (instance == null) {
                    Log.e(TAG, "Null alarminstance for SHOW_AND_DISMISS")
                    val id: Int = intent.getIntExtra(AlarmNotifications.EXTRA_NOTIFICATION_ID, -1)
                    if (id != -1) {
                        NotificationManagerCompat.from(context).cancel(id)
                    }
                    return
                }

                val alarmId = instance.alarmId ?: Alarm.INVALID_ID
                val viewAlarmIntent: Intent =
                    Alarm.createIntent(context, MainActivity::class.java, alarmId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                context.startActivity(viewAlarmIntent)
                deleteInstanceAndUpdateParent(context, instance)
            }
        }

        suspend fun registerInstance(
            context: Context,
            instance: AlarmInstance,
            updateNextAlarm: Boolean
        ) {
            Log.i(
                TAG,
                "Registering instance: " + instance.id + ", alarmState: " + instance.alarmState
            )

            val cr: ContentResolver = context.contentResolver
            val alarm = Alarm.getAlarm(cr, instance.alarmId!!)
            val currentTime = currentTime
            val alarmTime: Calendar = instance.alarmTime
            val timeoutTime: Calendar? = instance.timeout
            val lowNotificationTime: Calendar = instance.lowNotificationTime
            val highNotificationTime: Calendar = instance.highNotificationTime
            val missedTTL: Calendar = instance.missedTimeToLive

            // Handle special use cases here
            if (instance.alarmState == InstancesColumns.DISMISSED_STATE) {
                // This should never happen, but add a quick check here
                Log.e(TAG, "Alarm Instance is dismissed, but never deleted")
                deleteInstanceAndUpdateParent(context, instance)
                return
            } else if (instance.alarmState == InstancesColumns.FIRED_STATE) {
                Log.d(TAG, "Alarm Instance FIRED_STATE")
                // Keep alarm firing, unless it should be timed out
                val hasTimeout = timeoutTime != null && currentTime.after(timeoutTime)
                if (!hasTimeout) {
                    setFiredState(context, instance)
                    return
                }
            } else if (instance.alarmState == InstancesColumns.MISSED_STATE) {
                Log.d(TAG, "Alarm Instance MISSED_STATE")
                if (currentTime.before(alarmTime)) {
                    if (instance.alarmId == null) {
                        Log.i(TAG, "Cannot restore missed instance for one-time alarm")
                        // This instance parent got deleted (ie. deleteAfterUse), so
                        // we should not re-activate it.-
                        deleteInstanceAndUpdateParent(context, instance)
                        return
                    }

                    // TODO: This will re-activate missed snoozed alarms, but will
                    // use our normal notifications. This is not ideal, but very rare use-case.
                    // We should look into fixing this in the future.

                    // Make sure we re-enable the parent alarm of the instance
                    // because it will get activated by by the below code
                    alarm!!.enabled = true
                    Alarm.updateAlarm(cr, alarm)
                }
            } else if (instance.alarmState == InstancesColumns.PREDISMISSED_STATE) {
                Log.d(TAG, "Alarm Instance PREDISMISSED_STATE")
                if (currentTime.before(alarmTime)) {
                    setPreDismissState(context, instance)
                } else {
                    deleteInstanceAndUpdateParent(context, instance)
                }
                return
            }

            // Fix states that are time sensitive
            if (currentTime.after(missedTTL)) {
                Log.d(TAG, "Alarm is so old, just dismiss it")
                // Alarm is so old, just dismiss it
                deleteInstanceAndUpdateParent(context, instance)
            } else if (currentTime.after(alarmTime)) {
                // There is a chance that the TIME_SET occurred right when the alarm should go off,
                // so we need to add a check to see if we should fire the alarm instead of marking
                // it missed.
                val alarmBuffer = Calendar.getInstance()
                alarmBuffer.time = alarmTime.time
                alarmBuffer.add(Calendar.SECOND, ALARM_FIRE_BUFFER)
                Log.d(
                    TAG,
                    "registerInstance: current: ${currentTime.time}, alarmBuffer: ${alarmBuffer.time}"
                )
                if (currentTime.before(alarmBuffer)) {
                    Log.d(TAG, "registerInstance: setFiredState")
                    setFiredState(context, instance)
                } else {
                    Log.d(TAG, "registerInstance: setMissedState")
                    setMissedState(context, instance)
                }
            } else if (instance.alarmState == InstancesColumns.SNOOZE_STATE) {
                // We only want to display snooze notification and not update the time,
                // so handle showing the notification directly
                AlarmNotifications.showSnoozeNotification(context, instance)
                scheduleInstanceStateChange(
                    context, instance.alarmTime,
                    instance, InstancesColumns.FIRED_STATE
                )
            } else if (currentTime.after(highNotificationTime)) {
                setHighNotificationState(context, instance)
            } else if (currentTime.after(lowNotificationTime)) {
                // Only show low notification if it wasn't hidden in the past
                if (instance.alarmState == InstancesColumns.HIDE_NOTIFICATION_STATE) {
                    setHideNotificationState(context, instance)
                } else {
                    setLowNotificationState(context, instance)
                }
            } else {
                // Alarm is still active, so initialize as a silent alarm
                setSilentState(context, instance)
            }

            // The caller prefers to handle updateNextAlarm for optimization
            if (updateNextAlarm) {
                updateNextAlarm(context)
            }
        }

        private suspend fun setAlarmState(context: Context, instance: AlarmInstance?, state: Int) {
            if (instance == null) {
                Log.e(TAG, "Null alarm instance while setting state to %d$state")
                return
            }

            when (state) {
                InstancesColumns.SILENT_STATE -> setSilentState(context, instance)
                InstancesColumns.LOW_NOTIFICATION_STATE -> {
                    setLowNotificationState(context, instance)
                }

                InstancesColumns.HIDE_NOTIFICATION_STATE -> {
                    setHideNotificationState(context, instance)
                }

                InstancesColumns.HIGH_NOTIFICATION_STATE -> {
                    setHighNotificationState(context, instance)
                }

                InstancesColumns.FIRED_STATE -> setFiredState(context, instance)
                InstancesColumns.SNOOZE_STATE -> {
                    setSnoozeState(context, instance, true /* showToast */)
                }

                InstancesColumns.MISSED_STATE -> setMissedState(context, instance)
                InstancesColumns.PREDISMISSED_STATE -> setPreDismissState(context, instance)
                InstancesColumns.DISMISSED_STATE -> deleteInstanceAndUpdateParent(context, instance)
                else -> Log.e(TAG, "Trying to change to unknown alarm state: $state")
            }


        }

        private suspend fun setPreDismissState(context: Context, instance: AlarmInstance) {
            Log.i(TAG, "Setting predismissed state to instance " + instance.id)

            // Update alarm in db
            val contentResolver: ContentResolver = context.contentResolver
            instance.alarmState = InstancesColumns.PREDISMISSED_STATE
            AlarmInstance.updateInstance(contentResolver, instance)

            // Setup instance notification and scheduling timers
            AlarmNotifications.clearNotification(context, instance)
            scheduleInstanceStateChange(
                context, instance.alarmTime, instance,
                InstancesColumns.DISMISSED_STATE
            )

            // Check parent if it needs to reschedule, disable or delete itself
            if (instance.alarmId != null) {
                updateParentAlarm(context, instance)
            }

            updateNextAlarm(context)
        }

        private fun scheduleInstanceStateChange(
            ctx: Context,
            time: Calendar,
            instance: AlarmInstance,
            newState: Int
        ) {
            sStateChangeScheduler.scheduleInstanceStateChange(ctx, time, instance, newState)
        }

        private suspend fun setFiredState(context: Context, instance: AlarmInstance) {
            Log.i(TAG, "setFiredState: ${instance.id}")

            // Update alarm state in db
            val contentResolver: ContentResolver = context.contentResolver
            instance.alarmState = InstancesColumns.FIRED_STATE
            AlarmInstance.updateInstance(contentResolver, instance)

            instance.alarmId?.let {
                // if the time changed *backward* and pushed an instance from missed back to fired,
                // remove any other scheduled instances that may exist
                AlarmInstance.deleteOtherInstances(context, contentResolver, it, instance.id)
            }

            Events.sendAlarmEvent(R.string.action_fire, 0)

            val timeout: Calendar? = instance.timeout
            timeout?.let {
                scheduleInstanceStateChange(context, it, instance, InstancesColumns.MISSED_STATE)
            }

            // Instance not valid anymore, so find next alarm that will fire and notify system
            updateNextAlarm(context)
        }

        private suspend fun setHighNotificationState(context: Context, instance: AlarmInstance) {
            Log.i(TAG, "Setting high notification state to instance " + instance.id)

            // Update alarm state in db
            val contentResolver: ContentResolver = context.contentResolver
            instance.alarmState = InstancesColumns.HIGH_NOTIFICATION_STATE
            AlarmInstance.updateInstance(contentResolver, instance)

            // Setup instance notification and scheduling timers
            AlarmNotifications.showHighPriorityNotification(context, instance)
            scheduleInstanceStateChange(
                context, instance.alarmTime,
                instance, InstancesColumns.FIRED_STATE
            )
        }

        private suspend fun setHideNotificationState(context: Context, instance: AlarmInstance) {
            Log.d(TAG, "Setting hide notification state to instance " + instance.id)

            // Update alarm state in db
            val contentResolver: ContentResolver = context.contentResolver
            instance.alarmState = InstancesColumns.HIDE_NOTIFICATION_STATE
            AlarmInstance.updateInstance(contentResolver, instance)

            // Setup instance notification and scheduling timers
            AlarmNotifications.clearNotification(context, instance)
            scheduleInstanceStateChange(
                context, instance.highNotificationTime,
                instance, InstancesColumns.HIGH_NOTIFICATION_STATE
            )
        }

        private suspend fun setLowNotificationState(context: Context, instance: AlarmInstance) {
            Log.i(TAG, "Setting low notification state to instance " + instance.id)
            // Update alarm state in db
            val contentResolver: ContentResolver = context.contentResolver
            instance.alarmState = InstancesColumns.LOW_NOTIFICATION_STATE
            AlarmInstance.updateInstance(contentResolver, instance)

            // Setup instance notification and scheduling timers
            AlarmNotifications.showLowPriorityNotification(context, instance)
            scheduleInstanceStateChange(
                context, instance.highNotificationTime,
                instance, InstancesColumns.HIGH_NOTIFICATION_STATE
            )
        }

        private suspend fun setSilentState(context: Context, instance: AlarmInstance) {
            Log.i(TAG, "Setting silent state to instance " + instance.id)

            // Update alarm in db
            val contentResolver: ContentResolver = context.contentResolver
            instance.alarmState = InstancesColumns.SILENT_STATE
            AlarmInstance.updateInstance(contentResolver, instance)

            // Setup instance notification and scheduling timers
            AlarmNotifications.clearNotification(context, instance)
            scheduleInstanceStateChange(
                context, instance.lowNotificationTime,
                instance, InstancesColumns.LOW_NOTIFICATION_STATE
            )
        }

        fun createStateChangeIntent(
            context: Context?,
            tag: String?,
            instance: AlarmInstance,
            state: Int?
        ): Intent {
            val intent: Intent =
                AlarmInstance.createIntent(context, AlarmService::class.java, instance.id)
            intent.action = CHANGE_STATE_ACTION
            intent.addCategory(tag)
            intent.putExtra(ALARM_GLOBAL_ID_EXTRA, DataModel.dataModel.globalIntentId)
            if (state != null) {
                intent.putExtra(ALARM_STATE_EXTRA, state.toInt())
            }
            return intent
        }

        suspend fun setMissedState(context: Context, instance: AlarmInstance) {
            // Stop alarm if this instance is firing it
            AlarmService.stopAlarm(context, instance)

            // Check parent if it needs to reschedule, disable or delete itself
            if (instance.alarmId != null) {
                updateParentAlarm(context, instance)
            }

            // Update alarm state
            val contentResolver: ContentResolver = context.contentResolver
            instance.alarmState = InstancesColumns.MISSED_STATE
            AlarmInstance.updateInstance(contentResolver, instance)

            // Setup instance notification and scheduling timers
            AlarmNotifications.showMissedNotification(context, instance)
            scheduleInstanceStateChange(
                context, instance.missedTimeToLive,
                instance, InstancesColumns.DISMISSED_STATE
            )

            // Instance is not valid anymore, so find next alarm that will fire and notify system
            updateNextAlarm(context)
        }

        suspend fun setSnoozeState(
            context: Context,
            instance: AlarmInstance,
            showToast: Boolean
        ) {
            // Stop alarm if this instance is firing it
            AlarmService.stopAlarm(context, instance)

            // Calculate the new snooze alarm time
            val snoozeMinutes = DataModel.dataModel.snoozeLength
            val newAlarmTime = Calendar.getInstance()
            newAlarmTime.add(Calendar.MINUTE, snoozeMinutes)

            // Update alarm state and new alarm time in db.

            Log.d(
                TAG, "Setting snoozed state to instance " + instance.id + " for " +
                        AlarmUtils.getFormattedTime(context, newAlarmTime)
            )
            instance.alarmTime = newAlarmTime
            instance.alarmState = InstancesColumns.SNOOZE_STATE
            AlarmInstance.updateInstance(context.contentResolver, instance)

            // Setup instance notification and scheduling timers
            AlarmNotifications.showSnoozeNotification(context, instance)
            scheduleInstanceStateChange(
                context, instance.alarmTime,
                instance, InstancesColumns.FIRED_STATE
            )

            // Display the snooze minutes in a toast.
            if (showToast) {
                val mainHandler = Handler(context.mainLooper)
                val myRunnable = Runnable {
                    val displayTime =
                        String.format(
                            context
                                .resources
                                .getQuantityText(
                                    R.plurals.alarm_alert_snooze_set,
                                    snoozeMinutes
                                )
                                .toString(),
                            snoozeMinutes
                        )
                    Toast.makeText(context, displayTime, Toast.LENGTH_LONG).show()
                }
                mainHandler.post(myRunnable)
            }

            // Instance time changed, so find next alarm that will fire and notify system
            updateNextAlarm(context)
        }

        suspend fun deleteInstanceAndUpdateParent(context: Context, instance: AlarmInstance) {
            Log.i(TAG, "Deleting instance " + instance.id + " and updating parent alarm.")
            // Remove all other timers and notifications associated to it
            unregisterInstance(context, instance)

            // Check parent if it needs to reschedule, disable or delete itself
            if (instance.alarmId != null) {
                updateParentAlarm(context, instance)
            }

            // Delete instance as it is not needed anymore
            AlarmInstance.deleteInstance(context.contentResolver, instance.id)

            // Instance is not valid anymore, so find next alarm that will fire and notify system
            updateNextAlarm(context)
        }

        private suspend fun updateNextAlarm(context: Context) {
            val nextAlarm = getNextFiringAlarm(context)

            updateNextAlarmInAlarmManager(context, nextAlarm)
        }

        private fun updateNextAlarmInAlarmManager(context: Context, nextAlarm: AlarmInstance?) {
            // Sets a surrogate alarm with alarm manager that provides the AlarmClockInfo for the
            // alarm that is going to fire next. The operation is constructed such that it is
            // ignored by AlarmStateManager.

            val alarmManager: AlarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val flags = if (nextAlarm == null) PendingIntent.FLAG_NO_CREATE else 0
            val operation: PendingIntent? = PendingIntent.getBroadcast(
                context, 0 /* requestCode */,
                createIndicatorIntent(context), flags or PendingIntent.FLAG_IMMUTABLE
            )

            if (nextAlarm != null) {
                Log.i(TAG, "Setting upcoming AlarmClockInfo for alarm: " + nextAlarm.id)
                val alarmTime: Long = nextAlarm.alarmTime.timeInMillis

                // Create an intent that can be used to show or edit details of the next alarm.
                val viewIntent: PendingIntent =
                    PendingIntent.getActivity(
                        context, nextAlarm.id.hashCode(),
                        AlarmNotifications.createViewAlarmIntent(context, nextAlarm),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                val info = AlarmManager.AlarmClockInfo(alarmTime, viewIntent)
                Utils.updateNextAlarm(alarmManager, info, operation!!)
            } else if (operation != null) {
                Log.i(TAG, "Canceling upcoming AlarmClockInfo")
                alarmManager.cancel(operation)
            }
        }

        private fun createIndicatorIntent(context: Context?): Intent {
            return Intent(context, AlarmStateManager::class.java).setAction(INDICATOR_ACTION)
        }

        private suspend fun getNextFiringAlarm(context: Context): AlarmInstance? {
            val cr: ContentResolver = context.contentResolver
            val alarmInstances =
                AlarmInstance.getInstances(cr, ClockProvider.GET_ALL_INSTANCES_BY_ALARM_STATES)

            var nextAlarm: AlarmInstance? = null
            Log.d(TAG, "getNextFiringAlarm: ${alarmInstances.size}")
            for (instance in alarmInstances) {
                if (nextAlarm == null || instance.alarmTime.before(nextAlarm.alarmTime)) {
                    nextAlarm = instance
                }
            }
            return null
        }

        private suspend fun updateParentAlarm(context: Context, instance: AlarmInstance) {
            val cr: ContentResolver = context.contentResolver
            val alarm = Alarm.getAlarm(cr, instance.alarmId!!)
            if (alarm == null) {
                Log.e(TAG, "Parent has been deleted with instance: $instance")
                return
            }

            if (!alarm.daysOfWeek.isRepeating) {
                if (alarm.deleteAfterUse) {
                    Log.i(TAG, "Deleting parent alarm: " + alarm.id)
                    Alarm.deleteAlarm(cr, alarm.id)
                } else {
                    Log.i(TAG, "Disabling parent alarm: " + alarm.id)
                    alarm.enabled = false
                    Alarm.updateAlarm(cr, alarm)
                }
            } else {
                // Schedule the next repeating instance which may be before the current instance if
                // a time jump has occurred. Otherwise, if the current instance is the next instance
                // and has already been fired, schedule the subsequent instance.
                var nextRepeatedInstance = alarm.createInstanceAfter(currentTime)
                if (instance.alarmState > InstancesColumns.FIRED_STATE &&
                    nextRepeatedInstance.alarmTime == instance.alarmTime
                ) {
                    nextRepeatedInstance = alarm.createInstanceAfter(instance.alarmTime)
                }
                Log.i(
                    TAG, "Creating new instance for repeating alarm " + alarm.id +
                            " at " +
                            AlarmUtils.getFormattedTime(context, nextRepeatedInstance.alarmTime)
                )

                AlarmInstance.addInstance(cr, nextRepeatedInstance)
                registerInstance(context, nextRepeatedInstance, true)
            }
        }

        suspend fun unregisterInstance(context: Context, instance: AlarmInstance) {
            Log.i(TAG, "Unregistering instance " + instance.id)
            // Stop alarm if this instance is firing it
            AlarmService.stopAlarm(context, instance)
            AlarmNotifications.clearNotification(context, instance)
            cancelScheduledInstanceStateChange(context, instance)
            setDismissState(context, instance)
        }


        private fun cancelScheduledInstanceStateChange(ctx: Context, instance: AlarmInstance) {
            sStateChangeScheduler.cancelScheduledInstanceStateChange(ctx, instance)
        }

        private suspend fun setDismissState(context: Context, instance: AlarmInstance) {
            Log.i(TAG, "Setting dismissed state to instance " + instance.id)
            instance.alarmState = InstancesColumns.DISMISSED_STATE
            val contentResolver: ContentResolver = context.contentResolver
            AlarmInstance.updateInstance(contentResolver, instance)
        }

        suspend fun deleteAllInstances(context: Context, alarmId: Long) {
            Log.d(TAG, "Deleting all instances of alarm: $alarmId")
            val cr: ContentResolver = context.contentResolver
            val instances = AlarmInstance.getInstancesByAlarmId(cr, alarmId)
            for (instance in instances) {
                unregisterInstance(context, instance)
                AlarmInstance.deleteInstance(context.contentResolver, instance.id)
            }
            updateNextAlarm(context)
        }

        suspend fun fixAlarmInstances(context: Context) {
            Log.i(TAG, "fixAlarmInstances: ")
            // Register all instances after major time changes or when phone restarts
            val contentResolver: ContentResolver = context.contentResolver
            val currentTime = currentTime

            // Sort the instances in reverse chronological order so that later instances are fixed
            // or deleted before re-scheduling prior instances (which may re-create or update the
            // later instances).
            val instances = AlarmInstance.getInstances(
                contentResolver, null /* selection */
            )
            instances.sortWith { lhs, rhs -> rhs.alarmTime.compareTo(lhs.alarmTime) }

            for (instance in instances) {
                val alarm = Alarm.getAlarm(contentResolver, instance.id)
                if (alarm == null) {
                    unregisterInstance(context, instance)
                    AlarmInstance.deleteInstance(contentResolver, instance.id)
                    Log.e(TAG, "Found instance without matching alarm; deleting instance $instance")
                    continue
                }
                val priorAlarmTime = alarm.getPreviousAlarmTime(instance.alarmTime)
                val missedTTLTime: Calendar = instance.missedTimeToLive
                if (currentTime.before(priorAlarmTime) || currentTime.after(missedTTLTime)) {
                    val oldAlarmTime: Calendar = instance.alarmTime
                    val newAlarmTime = alarm.getNextAlarmTime(currentTime)
                    val oldTime: CharSequence =
                        DateFormat.format("MM/dd/yyyy hh:mm a", oldAlarmTime)
                    val newTime: CharSequence =
                        DateFormat.format("MM/dd/yyyy hh:mm a", newAlarmTime)
                    Log.i(
                        TAG, "A time change has caused an existing alarm scheduled" +
                                " to fire at $oldTime to be replaced by a new alarm scheduled to fire at $newTime"
                    )

                    // The time change is so dramatic the AlarmInstance doesn't make any sense;
                    // remove it and schedule the new appropriate instance.
                    deleteInstanceAndUpdateParent(context, instance)
                } else {
                    registerInstance(context, instance, false /* updateNextAlarm */)
                }
            }

            updateNextAlarm(context)
        }

        private val TAG = AlarmStateManager::class.java.simpleName
        private const val INDICATOR_ACTION = "indicator"
        const val CHANGE_STATE_ACTION = "change_state"
        const val ALARM_STATE_EXTRA = "intent.extra.alarm.state"
        private const val ALARM_GLOBAL_ID_EXTRA = "intent.extra.alarm.global.id"
        const val FROM_NOTIFICATION_EXTRA = "intent.extra.from.notification"
        const val SHOW_AND_DISMISS_ALARM_ACTION = "show_and_dismiss_alarm"

        const val ALARM_DISMISS_TAG = "DISMISS_TAG"
        const val ALARM_SNOOZE_TAG = "SNOOZE_TAG"
        const val ALARM_DELETE_TAG = "DELETE_TAG"
        private const val ALARM_MANAGER_TAG = "ALARM_MANAGER"

        private const val ALARM_FIRE_BUFFER = 15

        private val currentTime: Calendar
            get() = DataModel.dataModel.calendar
    }

    class AlarmManagerStateChangeScheduler : StateChangeScheduler {
        override fun scheduleInstanceStateChange(
            context: Context,
            time: Calendar,
            instance: AlarmInstance,
            newState: Int
        ) {
            val timeInMillis = time.timeInMillis
            Log.i(
                TAG, "Scheduling state change $newState to instance ${instance.id} " +
                        "at ${AlarmUtils.getFormattedTime(context, time)} ($timeInMillis)"
            )
            val stateChangeIntent: Intent =
                createStateChangeIntent(context, ALARM_MANAGER_TAG, instance, newState)
            // Treat alarm state change as high priority, use foreground broadcasts
            stateChangeIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            val pendingIntent: PendingIntent =
                PendingIntent.getForegroundService(
                    context,
                    instance.hashCode(),
                    stateChangeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

            val am: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        }

        override fun cancelScheduledInstanceStateChange(context: Context, instance: AlarmInstance) {
            Log.d(TAG, "Canceling instance " + instance.id + " timers")

            // Create a PendingIntent that will match any one set for this instance
            val pendingIntent: PendingIntent? =
                PendingIntent.getForegroundService(
                    context, instance.hashCode(),
                    createStateChangeIntent(context, ALARM_MANAGER_TAG, instance, null),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )

            pendingIntent?.let {
                val am: AlarmManager =
                    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.cancel(it)
                it.cancel()
            }
        }
    }
}

fun BroadcastReceiver.goAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
) {
    val pendingResult = goAsync()
    // Must run globally; there's no teardown callback.
    CoroutineScope(Dispatchers.IO).launch(context) {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}
