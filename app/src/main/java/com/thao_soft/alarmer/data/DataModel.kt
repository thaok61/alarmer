package com.thao_soft.alarmer.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.Keep
import com.thao_soft.alarmer.utils.Utils
import java.util.Calendar

@SuppressLint("StaticFieldLeak")
class DataModel {
    companion object {
        const val ACTION_WORLD_CITIES_CHANGED = "com.thao.deskclock.WORLD_CITIES_CHANGED"

        private val sDataModel = DataModel()

        @get:JvmStatic
        @get:Keep
        val dataModel
            get() = sDataModel
    }

    var isRestoreBackupFinished: Boolean
        /**
         * @return `true` if the restore process (of backup and restore) has completed
         */
        get() = mSettingsModel!!.isRestoreBackupFinished
        /**
         * @param finished `true` means the restore process (of backup and restore) has completed
         */
        set(finished) {
            mSettingsModel!!.isRestoreBackupFinished = finished
        }
    val weekdayOrder: Weekdays.Order
        get() {
            Utils.enforceMainLooper()
            return mSettingsModel!!.weekdayOrder
        }

    val snoozeLength: Int
        get() = mAlarmModel!!.snoozeLength

    val calendar: Calendar
        get() = mTimeModel!!.calendar

    val alarmTimeout: Int
        get() = mAlarmModel!!.alarmTimeout

    /** The model from which alarm data are fetched.  */
    private var mAlarmModel: AlarmModel? = null

    /** The model from which settings are fetched.  */
    private var mSettingsModel: SettingsModel? = null

    /** The model from which time data are fetched.  */
    private var mTimeModel: TimeModel? = null

    private var mRingtoneModel: RingtoneModel? = null

    private var mContext: Context? = null

    val alarmCrescendoDuration: Long
        get() {
            Utils.enforceMainLooper()
            return mAlarmModel!!.alarmCrescendoDuration
        }

    val globalIntentId: Int
        get() = mSettingsModel!!.globalIntentId

    var defaultAlarmRingtoneUri: Uri
        /**
         * @return the uri of the ringtone to which all new alarms default
         */
        get() {
            Utils.enforceMainLooper()
            return mAlarmModel!!.defaultAlarmRingtoneUri
        }
        /**
         * @param uri the uri of the ringtone to which future new alarms will default
         */
        set(uri) {
            Utils.enforceMainLooper()
            mAlarmModel!!.defaultAlarmRingtoneUri = uri
        }

    fun init(context: Context, prefs: SharedPreferences) {
        if (mContext !== context) {
            mContext = context.applicationContext
            mTimeModel = TimeModel(mContext!!)
            mRingtoneModel = RingtoneModel(mContext!!, prefs)
            mSettingsModel = SettingsModel(mContext!!, prefs, mTimeModel!!)
            mAlarmModel = AlarmModel(mContext!!, mSettingsModel!!)
        }
    }

    fun loadRingtoneTitles() {
        Utils.enforceNotMainLooper()
        mRingtoneModel?.loadRingtoneTitles()
    }

    fun elapsedRealtime(): Long {
        return mTimeModel!!.elapsedRealtime()
    }

    fun is24HourFormat(): Boolean {
        return mTimeModel!!.is24HourFormat()
    }

    fun getRingtoneTitle(uri: Uri): String? {
        Utils.enforceMainLooper()
        return mRingtoneModel?.getRingtoneTitle(uri)
    }

    fun loadRingtonePermissions() {
        Utils.enforceNotMainLooper()
        mRingtoneModel?.loadRingtonePermissions()
    }

    fun updateGlobalIntentId() {
        Utils.enforceMainLooper()
        mSettingsModel!!.updateGlobalIntentId()
    }
}