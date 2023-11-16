package com.thao_soft.alarmer.data

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.util.ArrayMap
import android.util.Log

class RingtoneModel(private val mContext: Context, private val mPrefs: SharedPreferences) {

    /** Maps ringtone uri to ringtone title; looking up a title from scratch is expensive.  */
    private val mRingtoneTitles: MutableMap<Uri, String?> = ArrayMap(16)

    companion object {
        val TAG: String = RingtoneModel::class.java.simpleName
    }

    fun loadRingtoneTitles() {
        // Early return if the cache is already primed.
        if (mRingtoneTitles.isNotEmpty()) {
            return
        }

        val ringtoneManager = RingtoneManager(mContext)
        ringtoneManager.setType(AudioManager.STREAM_ALARM)

        // Cache a title for each system ringtone.
        try {
            val cursor: Cursor? = ringtoneManager.cursor
            cursor?.let {
                cursor.moveToFirst()
                while (!cursor.isAfterLast) {
                    val ringtoneTitle: String = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val ringtoneUri: Uri = ringtoneManager.getRingtoneUri(cursor.position)
                    mRingtoneTitles[ringtoneUri] = ringtoneTitle
                    cursor.moveToNext()
                }
            }
        } catch (ignored: Throwable) {
            // best attempt only
            Log.e(TAG, "Error loading ringtone title cache", ignored)
        }
    }
}