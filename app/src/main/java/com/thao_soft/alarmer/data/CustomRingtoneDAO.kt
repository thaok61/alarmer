package com.thao_soft.alarmer.data

import android.content.SharedPreferences
import android.net.Uri

object CustomRingtoneDAO {
    private const val RINGTONE_IDS = "ringtone_ids"

    private const val RINGTONE_URI = "ringtone_uri_"

    private const val RINGTONE_TITLE = "ringtone_title_"
    fun getCustomRingtones(prefs: SharedPreferences): MutableList<CustomRingtone> {
        val ids: Set<String> = prefs.getStringSet(RINGTONE_IDS, emptySet<String>())!!
        val ringtones: MutableList<CustomRingtone> = ArrayList(ids.size)

        for (id in ids) {
            val idLong = id.toLong()
            val uri: Uri = Uri.parse(prefs.getString(RINGTONE_URI + id, null))
            val title: String? = prefs.getString(RINGTONE_TITLE + id, null)
            ringtones.add(CustomRingtone(idLong, uri, title, true))
        }

        return ringtones
    }

}