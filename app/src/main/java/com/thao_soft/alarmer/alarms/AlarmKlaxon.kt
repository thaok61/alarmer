package com.thao_soft.alarmer.alarms

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.os.Vibrator
import android.util.Log
import com.thao_soft.alarmer.AsyncRingtonePlayer
import com.thao_soft.alarmer.data.AlarmSettingColumns
import com.thao_soft.alarmer.data.DataModel
import com.thao_soft.alarmer.provider.AlarmInstance
import com.thao_soft.alarmer.utils.Utils

internal object AlarmKlaxon {
    private val TAG = AlarmKlaxon::class.java.simpleName

    private val VIBRATE_PATTERN = longArrayOf(500, 500)

    private var sStarted = false
    @SuppressLint("StaticFieldLeak")
    private var sAsyncRingtonePlayer: AsyncRingtonePlayer? = null

    @JvmStatic
    fun stop(context: Context) {
        if (sStarted) {
            Log.d(TAG, "AlarmKlaxon.stop()")
            sStarted = false
            getAsyncRingtonePlayer(context)!!.stop()
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        }
    }

    @JvmStatic
    fun start(context: Context, instance: AlarmInstance) {
        // Make sure we are stopped before starting
        stop(context)
        Log.d(TAG, "AlarmKlaxon.start()")

        if (AlarmSettingColumns.NO_RINGTONE_URI != instance.ringtone) {
            val crescendoDuration = DataModel.dataModel.alarmCrescendoDuration
            getAsyncRingtonePlayer(context)!!.play(instance.ringtone, crescendoDuration)
        }

        if (instance.vibrate) {
            val vibrator: Vibrator = getVibrator(context)
            if (Utils.isLOrLater) {
                vibrateLOrLater(vibrator)
            }
        }

        sStarted = true
    }

    private fun vibrateLOrLater(vibrator: Vibrator) {
        vibrator.vibrate(VIBRATE_PATTERN, 0, AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
    }

    private fun getVibrator(context: Context): Vibrator {
        return context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    @Synchronized
    private fun getAsyncRingtonePlayer(context: Context): AsyncRingtonePlayer? {
        if (sAsyncRingtonePlayer == null) {
            sAsyncRingtonePlayer = AsyncRingtonePlayer(context.applicationContext)
        }

        return sAsyncRingtonePlayer
    }
}