package com.thao_soft.alarmer.ringtone

import android.content.Context
import android.net.Uri
import android.util.Log
import com.thao_soft.alarmer.AsyncRingtonePlayer

object RingtonePreviewKlaxon {
    private var sAsyncRingtonePlayer: AsyncRingtonePlayer? = null

    @JvmStatic
    fun stop(context: Context) {
        Log.d("RingtonePreviewKlaxon", "RingtonePreviewKlaxon.stop()")
        getAsyncRingtonePlayer(context).stop()
    }

    @JvmStatic
    fun start(context: Context, uri: Uri) {
        stop(context)
        Log.d("RingtonePreviewKlaxon", "RingtonePreviewKlaxon.start()")
        getAsyncRingtonePlayer(context).play(uri, 0)
    }

    @Synchronized
    private fun getAsyncRingtonePlayer(context: Context): AsyncRingtonePlayer {
        if (sAsyncRingtonePlayer == null) {
            sAsyncRingtonePlayer = AsyncRingtonePlayer(context.applicationContext)
        }
        return sAsyncRingtonePlayer!!
    }
}