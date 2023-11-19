package com.thao_soft.alarmer.data

import android.net.Uri
import com.thao_soft.alarmer.utils.Utils

data class Ringtone(
    val uri: Uri,
    val mName: String? = null,
    val mHasPermissions: Boolean = true,
    var isSelected: Boolean = false,
    var isPlaying: Boolean = false
    ) {
    val isSilent: Boolean
        get() = Utils.RINGTONE_SILENT == uri

    val name: String?
        get() = mName ?: DataModel.dataModel.getRingtoneTitle(uri)
}