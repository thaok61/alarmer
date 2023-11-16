package com.thao_soft.alarmer.alarms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.thao_soft.alarmer.MainActivity
import com.thao_soft.alarmer.R
import com.thao_soft.alarmer.data.InstancesColumns
import com.thao_soft.alarmer.provider.Alarm
import com.thao_soft.alarmer.provider.AlarmInstance
import com.thao_soft.alarmer.utils.AlarmUtils
import com.thao_soft.alarmer.utils.Utils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

internal object AlarmNotifications {
    fun showAlarmNotification(service: Service, instance: AlarmInstance) {
        Log.d(TAG, "Displaying alarm notification for alarm instance: " + instance.id)
        val resources: Resources = service.resources
        val notification: NotificationCompat.Builder = NotificationCompat.Builder(
            service, ALARM_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(instance.getLabelOrDefault(service))
            .setContentText(
                AlarmUtils.getFormattedTime(
                    service, instance.alarmTime
                )
            )
            .setColor(ContextCompat.getColor(service, R.color.default_background))
            .setSmallIcon(R.drawable.stat_notify_alarm)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setWhen(0)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)

        // Setup Snooze Action
        val snoozeIntent: Intent = AlarmStateManager.createStateChangeIntent(
            service,
            AlarmStateManager.ALARM_SNOOZE_TAG, instance, InstancesColumns.SNOOZE_STATE
        )
        snoozeIntent.putExtra(AlarmStateManager.FROM_NOTIFICATION_EXTRA, true)
        val snoozePendingIntent: PendingIntent = PendingIntent.getService(
            service,
            ALARM_FIRING_NOTIFICATION_ID,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notification.addAction(
            R.drawable.ic_snooze_24dp,
            resources.getString(R.string.alarm_alert_snooze_text), snoozePendingIntent
        )

        // Setup Dismiss Action
        val dismissIntent: Intent = AlarmStateManager.createStateChangeIntent(
            service,
            AlarmStateManager.ALARM_DISMISS_TAG, instance, InstancesColumns.DISMISSED_STATE
        )
        dismissIntent.putExtra(AlarmStateManager.FROM_NOTIFICATION_EXTRA, true)
        val dismissPendingIntent: PendingIntent = PendingIntent.getService(
            service,
            ALARM_FIRING_NOTIFICATION_ID,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notification.addAction(
            R.drawable.ic_alarm_off_24dp,
            resources.getString(R.string.alarm_alert_dismiss_text),
            dismissPendingIntent
        )

        // Setup Content Action
        val contentIntent: Intent = AlarmInstance.createIntent(
            service, MainActivity::class.java,
            instance.id
        )
        notification.setContentIntent(
            PendingIntent.getActivity(
                service,
                ALARM_FIRING_NOTIFICATION_ID,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Setup fullscreen intent
        val fullScreenIntent: Intent =
            AlarmInstance.createIntent(service, MainActivity::class.java, instance.id)
        // set action, so we can be different then content pending intent
        fullScreenIntent.action = "fullscreen_activity"
        fullScreenIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        notification.setFullScreenIntent(
            PendingIntent.getActivity(
                service,
                ALARM_FIRING_NOTIFICATION_ID,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ),
            true
        )
        notification.priority = NotificationCompat.PRIORITY_MAX

        clearNotification(service, instance)
        service.startForeground(ALARM_FIRING_NOTIFICATION_ID, notification.build())
    }

    fun createViewAlarmIntent(context: Context?, instance: AlarmInstance): Intent {
        val alarmId = instance.alarmId ?: Alarm.INVALID_ID
        return Alarm.createIntent(context, MainActivity::class.java, alarmId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun clearNotification(context: Context, instance: AlarmInstance) {
        Log.i(TAG, "Clearing notifications for alarm instance: " + instance.id)
        val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)
        val id = instance.hashCode()
        Log.d(TAG, "clearNotification: hashcode: $id")
        nm.cancel(id)
        updateUpcomingAlarmGroupNotification(context, id, null)
        updateMissedAlarmGroupNotification(context, id, null)
    }

    private fun updateMissedAlarmGroupNotification(
        context: Context,
        canceledNotificationId: Int,
        postedNotification: Notification?
    ) {
        if (!Utils.isNOrLater) {
            return
        }

        val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            ALARM_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.alarm_missed_title),
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val firstMissed: Notification? = getFirstActiveNotification(
            context, MISSED_GROUP_KEY,
            canceledNotificationId, postedNotification
        )
        if (firstMissed == null) {
            nm.cancel(ALARM_GROUP_MISSED_NOTIFICATION_ID)
            return
        }

        var summary: Notification? = getActiveGroupSummaryNotification(context, MISSED_GROUP_KEY)
        if (summary == null ||
            summary.contentIntent != firstMissed.contentIntent
        ) {
            nm.createNotificationChannel(channel)
            summary = NotificationCompat.Builder(context, ALARM_NOTIFICATION_CHANNEL_ID)
                .setShowWhen(false)
                .setContentIntent(firstMissed.contentIntent)
                .setColor(ContextCompat.getColor(context, R.color.default_background))
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setGroup(MISSED_GROUP_KEY)
                .setGroupSummary(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .build()
            nm.notify(ALARM_GROUP_MISSED_NOTIFICATION_ID, summary)
        }
    }

    private fun updateUpcomingAlarmGroupNotification(
        context: Context,
        canceledNotificationId: Int,
        postedNotification: Notification?
    ) {
        if (!Utils.isNOrLater) {
            return
        }

        val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            ALARM_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.alarm_alert_predismiss_title),
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val firstUpcoming: Notification? = getFirstActiveNotification(
            context, UPCOMING_GROUP_KEY,
            canceledNotificationId, postedNotification
        )
        if (firstUpcoming == null) {
            nm.cancel(ALARM_GROUP_NOTIFICATION_ID)
            return
        }

        var summary: Notification? = getActiveGroupSummaryNotification(context, UPCOMING_GROUP_KEY)
        if (summary == null ||
            summary.contentIntent != firstUpcoming.contentIntent
        ) {
            summary = NotificationCompat.Builder(context, ALARM_NOTIFICATION_CHANNEL_ID)
                .setShowWhen(false)
                .setContentIntent(firstUpcoming.contentIntent)
                .setColor(ContextCompat.getColor(context, R.color.default_background))
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setGroup(UPCOMING_GROUP_KEY)
                .setGroupSummary(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .build()
            nm.notify(ALARM_GROUP_NOTIFICATION_ID, summary)
        }
    }

    fun showHighPriorityNotification(context: Context, instance: AlarmInstance) {
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            context, ALARM_HIGH_PRIORITY_NOTIFICATION_CHANNEL_ID)
            .setShowWhen(false)
            .setContentTitle(context.getString(
                R.string.alarm_alert_predismiss_title))
            .setContentText(AlarmUtils.getAlarmText(
                context, instance, true /* includeLabel */))
            .setColor(ContextCompat.getColor(context, R.color.default_background))
            .setSmallIcon(R.drawable.stat_notify_alarm)
            .setAutoCancel(false)
            .setSortKey(createSortKey(instance))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)

        if (Utils.isNOrLater) {
            builder.setGroup(UPCOMING_GROUP_KEY)
        }

        // Setup up dismiss action
        val dismissIntent: Intent = AlarmStateManager.createStateChangeIntent(context,
            AlarmStateManager.ALARM_DISMISS_TAG, instance, InstancesColumns.PREDISMISSED_STATE)
        val id = instance.hashCode()
        Log.d(TAG, "showHighPriorityNotification: $id")
        builder.addAction(R.drawable.ic_alarm_off_24dp,
            context.getString(R.string.alarm_alert_dismiss_text),
            PendingIntent.getService(context, id,
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        // Setup content action if instance is owned by alarm
        val viewAlarmIntent: Intent = createViewAlarmIntent(context, instance)
        builder.setContentIntent(PendingIntent.getActivity(context, id,
            viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            ALARM_HIGH_PRIORITY_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.default_label),
            NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)
        val notification: Notification = builder.build()
        nm.notify(id, notification)
        updateUpcomingAlarmGroupNotification(context, -1, notification)
    }

    private fun getFirstActiveNotification(
        context: Context,
        group: String,
        canceledNotificationId: Int,
        postedNotification: Notification?
    ): Notification? {
        val nm: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications: Array<StatusBarNotification> = nm.getActiveNotifications()
        var firstActiveNotification: Notification? = postedNotification
        for (statusBarNotification in notifications) {
            val n: Notification = statusBarNotification.getNotification()
            if (!isGroupSummary(n) && group == n.getGroup() &&
                statusBarNotification.getId() != canceledNotificationId
            ) {
                if (firstActiveNotification == null ||
                    n.sortKey < firstActiveNotification.sortKey
                ) {
                    firstActiveNotification = n
                }
            }
        }
        return firstActiveNotification
    }

    private fun getActiveGroupSummaryNotification(context: Context, group: String): Notification? {
        val nm: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications: Array<StatusBarNotification> = nm.activeNotifications
        for (statusBarNotification in notifications) {
            val n: Notification = statusBarNotification.notification
            if (isGroupSummary(n) && group == n.group) {
                return n
            }
        }
        return null
    }

    private fun isGroupSummary(n: Notification): Boolean {
        return n.flags and Notification.FLAG_GROUP_SUMMARY == Notification.FLAG_GROUP_SUMMARY
    }

    private fun createSortKey(instance: AlarmInstance): String {
        val timeKey = SORT_KEY_FORMAT.format(instance.alarmTime.time)
        val missedAlarm = instance.alarmState == InstancesColumns.MISSED_STATE
        return if (missedAlarm) "MISSED $timeKey" else timeKey
    }

    fun showSnoozeNotification(context: Context, instance: AlarmInstance) {
        Log.d(TAG, "Displaying snoozed notification for alarm instance: " + instance.id)

        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            context, ALARM_SNOOZE_NOTIFICATION_CHANNEL_ID)
            .setShowWhen(false)
            .setContentTitle(instance.getLabelOrDefault(context))
            .setContentText(context.getString(R.string.alarm_alert_snooze_until,
                AlarmUtils.getFormattedTime(context, instance.alarmTime)))
            .setColor(ContextCompat.getColor(context, R.color.default_background))
            .setSmallIcon(R.drawable.stat_notify_alarm)
            .setAutoCancel(false)
            .setSortKey(createSortKey(instance))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)

        if (Utils.isNOrLater) {
            builder.setGroup(UPCOMING_GROUP_KEY)
        }

        // Setup up dismiss action
        val dismissIntent: Intent = AlarmStateManager.createStateChangeIntent(context,
            AlarmStateManager.ALARM_DISMISS_TAG, instance, InstancesColumns.DISMISSED_STATE)
        val id = instance.hashCode()
        Log.d(TAG, "showSnoozeNotification: $id")
        builder.addAction(R.drawable.ic_alarm_off_24dp,
            context.getString(R.string.alarm_alert_dismiss_text),
            PendingIntent.getService(context, id,
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        // Setup content action if instance is owned by alarm
        val viewAlarmIntent: Intent = createViewAlarmIntent(context, instance)
        builder.setContentIntent(PendingIntent.getActivity(context, id,
            viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            ALARM_SNOOZE_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.alarm_alert_snooze_text),
            NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)
        val notification: Notification = builder.build()
        nm.notify(id, notification)
        updateUpcomingAlarmGroupNotification(context, -1, notification)
    }

    @Synchronized
    fun showLowPriorityNotification(
        context: Context,
        instance: AlarmInstance
    ) {

        Log.d(TAG, "Displaying low priority notification for alarm instance: " + instance.id)

        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            context, ALARM_LOW_PRIORITY_NOTIFICATION_CHANNEL_ID)
            .setShowWhen(false)
            .setContentTitle(context.getString(
                R.string.alarm_alert_predismiss_title))
            .setContentText(AlarmUtils.getAlarmText(
                context, instance, true /* includeLabel */))
            .setColor(ContextCompat.getColor(context, R.color.default_background))
            .setSmallIcon(R.drawable.stat_notify_alarm)
            .setAutoCancel(false)
            .setSortKey(createSortKey(instance))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)

        if (Utils.isNOrLater) {
            builder.setGroup(UPCOMING_GROUP_KEY)
        }

        // Setup up hide notification
        val hideIntent: Intent = AlarmStateManager.createStateChangeIntent(context,
            AlarmStateManager.ALARM_DELETE_TAG, instance,
            InstancesColumns.HIDE_NOTIFICATION_STATE)
        val id = instance.hashCode()
        Log.d(TAG, "showLowPriorityNotification: $id")
        builder.setDeleteIntent(PendingIntent.getService(context, id,
            hideIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        // Setup up dismiss action
        val dismissIntent: Intent = AlarmStateManager.createStateChangeIntent(context,
            AlarmStateManager.ALARM_DISMISS_TAG, instance, InstancesColumns.PREDISMISSED_STATE)
        builder.addAction(R.drawable.ic_alarm_off_24dp,
            context.getString(R.string.alarm_alert_dismiss_text),
            PendingIntent.getService(context, id,
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        // Setup content action if instance is owned by alarm
        val viewAlarmIntent: Intent = createViewAlarmIntent(context, instance)
        builder.setContentIntent(PendingIntent.getActivity(context, id,
            viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            ALARM_LOW_PRIORITY_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.alarm_alert_predismiss_title),
            NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)
        val notification: Notification = builder.build()
        nm.notify(id, notification)
        updateUpcomingAlarmGroupNotification(context, -1, notification)
    }

    @Synchronized
    fun showMissedNotification(
        context: Context,
        instance: AlarmInstance
    ) {
        Log.d(TAG, "Displaying missed notification for alarm instance: " + instance.id)

        val label = instance.label
        val alarmTime: String = AlarmUtils.getFormattedTime(context, instance.alarmTime)
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            context, ALARM_MISSED_NOTIFICATION_CHANNEL_ID)
            .setShowWhen(false)
            .setContentTitle(context.getString(R.string.alarm_missed_title))
            .setContentText(if (instance.label!!.isEmpty()) {
                alarmTime
            } else {
                context.getString(R.string.alarm_missed_text, alarmTime, label)
            })
            .setColor(ContextCompat.getColor(context, R.color.default_background))
            .setSortKey(createSortKey(instance))
            .setSmallIcon(R.drawable.stat_notify_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)

        if (Utils.isNOrLater) {
            builder.setGroup(MISSED_GROUP_KEY)
        }

        val id = instance.hashCode()
        Log.d(TAG, "showMissedNotification: $id")

        // Setup dismiss intent
        val dismissIntent: Intent = AlarmStateManager.createStateChangeIntent(context,
            AlarmStateManager.ALARM_DISMISS_TAG, instance, InstancesColumns.DISMISSED_STATE)
        builder.setDeleteIntent(PendingIntent.getService(context, id,
            dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        // Setup content intent
        val showAndDismiss: Intent = AlarmInstance.createIntent(context,
            AlarmStateManager::class.java, instance.id)
        showAndDismiss.putExtra(EXTRA_NOTIFICATION_ID, id)
        showAndDismiss.action = AlarmStateManager.SHOW_AND_DISMISS_ALARM_ACTION
        builder.setContentIntent(PendingIntent.getBroadcast(context, id,
            showAndDismiss, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            ALARM_MISSED_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.alarm_missed_title),
            NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)
        val notification: Notification = builder.build()
        nm.notify(id, notification)
        updateMissedAlarmGroupNotification(context, -1, notification)
    }

    fun updateNotification(context: Context, instance: AlarmInstance) {
        when (instance.alarmState) {
            InstancesColumns.LOW_NOTIFICATION_STATE -> {
                showLowPriorityNotification(context, instance)
            }
            InstancesColumns.HIGH_NOTIFICATION_STATE -> {
                showHighPriorityNotification(context, instance)
            }
            InstancesColumns.SNOOZE_STATE -> showSnoozeNotification(context, instance)
            InstancesColumns.MISSED_STATE -> showMissedNotification(context, instance)
            else -> Log.d(TAG, "No notification to update")
        }
    }

    val TAG: String = AlarmNotifications::class.java.simpleName

    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    /**
     * Notification channel containing all low priority notifications.
     */
    private const val ALARM_LOW_PRIORITY_NOTIFICATION_CHANNEL_ID = "alarmLowPriorityNotification"

    /**
     * Notification channel containing all high priority notifications.
     */
    private const val ALARM_HIGH_PRIORITY_NOTIFICATION_CHANNEL_ID = "alarmHighPriorityNotification"

    /**
     * Notification channel containing all snooze notifications.
     */
    private const val ALARM_SNOOZE_NOTIFICATION_CHANNEL_ID = "alarmSnoozeNotification"

    /**
     * Notification channel containing all missed notifications.
     */
    private const val ALARM_MISSED_NOTIFICATION_CHANNEL_ID = "alarmMissedNotification"

    /**
     * Notification channel containing all alarm notifications.
     */
    private const val ALARM_NOTIFICATION_CHANNEL_ID = "alarmNotification"

    /**
     * Formats times such that chronological order and lexicographical order agree.
     */
    private val SORT_KEY_FORMAT: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * This value is coordinated with group ids from
     * [com.thao.deskclock.data.NotificationModel]
     */
    private const val UPCOMING_GROUP_KEY = "1"

    /**
     * This value is coordinated with group ids from
     * [com.thao.deskclock.data.NotificationModel]
     */
    private const val MISSED_GROUP_KEY = "4"

    /**
     * This value is coordinated with notification ids from
     * [com.thao.deskclock.data.NotificationModel]
     */
    private const val ALARM_GROUP_NOTIFICATION_ID = Int.MAX_VALUE - 4

    /**
     * This value is coordinated with notification ids from
     * [com.thao.deskclock.data.NotificationModel]
     */
    private const val ALARM_GROUP_MISSED_NOTIFICATION_ID = Int.MAX_VALUE - 5

    /**
     * This value is coordinated with notification ids from
     * [com.thao.deskclock.data.NotificationModel]
     */
    private const val ALARM_FIRING_NOTIFICATION_ID = Int.MAX_VALUE - 7

}