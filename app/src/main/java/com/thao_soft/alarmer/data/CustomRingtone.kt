package com.thao_soft.alarmer.data

import android.net.Uri

class CustomRingtone internal constructor(
    /** The unique identifier of the custom ringtone.  */
    val id: Long,
    /** The uri that allows playback of the ringtone.  */
    private val mUri: Uri,
    /** The title describing the file at the given uri; typically the file name.  */
    val title: String?,
    /** `true` iff the application has permission to read the content of `mUri uri`.  */
    private val mHasPermissions: Boolean
) : Comparable<CustomRingtone> {

    val uri: Uri
        get() = mUri

    fun hasPermissions(): Boolean = mHasPermissions

    fun setHasPermissions(hasPermissions: Boolean): CustomRingtone =
        if (mHasPermissions == hasPermissions) {
            this
        } else {
            CustomRingtone(id, mUri, title, hasPermissions)
        }

    override fun compareTo(other: CustomRingtone): Int {
        return String.CASE_INSENSITIVE_ORDER.compare(title, other.title)
    }
}