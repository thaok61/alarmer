package com.thao_soft.alarmer.data

import android.content.Context
import android.content.SharedPreferences
import android.content.UriPermission
import android.database.Cursor
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.util.ArrayMap
import android.util.ArraySet
import android.util.Log
import com.thao_soft.alarmer.R

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

    fun getRingtoneTitle(uri: Uri): String? {
        // Special case: no ringtone has a title of "Silent".
        if (AlarmSettingColumns.NO_RINGTONE_URI == uri) {
            return mContext.getString(R.string.silent_ringtone_title)
        }

        // If the ringtone is custom, it has its own title.
        val customRingtone = getCustomRingtone(uri)
        if (customRingtone != null) {
            return customRingtone.title
        }

        // Check the cache.
        var title = mRingtoneTitles[uri]

        if (title == null) {
            // This is slow because a media player is created during Ringtone object creation.
            val ringtone: Ringtone? = RingtoneManager.getRingtone(mContext, uri)
            if (ringtone == null) {
                Log.e(TAG, "No ringtone for uri: $uri", )
                return mContext.getString(R.string.unknown_ringtone_title)
            }

            // Cache the title for later use.
            title = ringtone.getTitle(mContext)
            mRingtoneTitles[uri] = title
        }
        return title
    }

    private fun getCustomRingtone(uri: Uri): CustomRingtone? {
        for (ringtone in mutableCustomRingtones) {
            if (ringtone.uri == uri) {
                return ringtone
            }
        }

        return null
    }

    fun loadRingtonePermissions() {
        val ringtones = mutableCustomRingtones
        if (ringtones.isEmpty()) {
            return
        }

        val uriPermissions: List<UriPermission> =
            mContext.contentResolver.persistedUriPermissions
        val permissions: MutableSet<Uri?> = ArraySet(uriPermissions.size)
        for (uriPermission in uriPermissions) {
            permissions.add(uriPermission.uri)
        }

        val i = ringtones.listIterator()
        while (i.hasNext()) {
            val ringtone = i.next()
            i.set(ringtone.setHasPermissions(permissions.contains(ringtone.uri)))
        }
    }

    private var mCustomRingtones: MutableList<CustomRingtone>? = null
    private val mutableCustomRingtones: MutableList<CustomRingtone>
        get() {
            if (mCustomRingtones == null) {
                mCustomRingtones = CustomRingtoneDAO.getCustomRingtones(mPrefs)
                mCustomRingtones!!.sort()
            }

            return mCustomRingtones!!
        }
}