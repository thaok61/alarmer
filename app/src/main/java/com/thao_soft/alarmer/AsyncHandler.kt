package com.thao_soft.alarmer

import android.os.Handler
import android.os.HandlerThread

object AsyncHandler {
    private val sHandlerThread = HandlerThread("AsyncHandler")
    private val sHandler: Handler

    init {
        sHandlerThread.start()
        sHandler = Handler(sHandlerThread.looper)
    }

    fun post(r: () -> Unit) {
        sHandler.post(r)
    }
}