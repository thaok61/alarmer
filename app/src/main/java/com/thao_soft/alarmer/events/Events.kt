package com.thao_soft.alarmer.events

import androidx.annotation.StringRes
import com.thao_soft.alarmer.R

object Events {
    const val EXTRA_EVENT_LABEL = "com.thao.deskclock.extra.EVENT_LABEL"

    @JvmStatic
    fun sendAlarmEvent(@StringRes action: Int, @StringRes label: Int) {
        sendEvent(R.string.category_alarm, action, label)
    }

    @JvmStatic
    fun sendEvent(@StringRes category: Int, @StringRes action: Int, @StringRes label: Int) {
//        Controller.getController().sendEvent(category, action, label)
    }
}