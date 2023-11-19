package com.thao_soft.alarmer.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.annotation.AnyRes
import androidx.core.os.BuildCompat
import com.thao_soft.alarmer.data.DataModel
import java.text.NumberFormat
import java.util.Locale

object Utils {
    fun enforceMainLooper() {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw IllegalAccessError("May only call from main thread.")
        }
    }

    fun enforceNotMainLooper() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            throw IllegalAccessError("May not call from main thread.")
        }
    }

    fun getResourceUri(context: Context, @AnyRes resourceId: Int): Uri {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .path(resourceId.toString())
            .build()
    }

    fun updateNextAlarm(am: AlarmManager, info: AlarmManager.AlarmClockInfo, op: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setAlarmClock(info, op)
            }
        } else {
            am.setAlarmClock(info, op)
        }

    }

    fun now(): Long = DataModel.dataModel.elapsedRealtime()

    fun get12ModeFormat(amPmRatio: Float, includeSeconds: Boolean): CharSequence {
        var pattern = DateFormat.getBestDateTimePattern(
            Locale.getDefault(),
            if (includeSeconds) "hmsa" else "hma")
        if (amPmRatio <= 0) {
            pattern = pattern.replace("a".toRegex(), "").trim { it <= ' ' }
        }

        // Replace spaces with "Hair Space"
        pattern = pattern.replace(" ".toRegex(), "\u200A")
        // Build a spannable so that the am/pm will be formatted
        val amPmPos = pattern.indexOf('a')
        if (amPmPos == -1) {
            return pattern
        }

        val sp: Spannable = SpannableString(pattern)
        sp.setSpan(
            RelativeSizeSpan(amPmRatio), amPmPos, amPmPos + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(
            StyleSpan(Typeface.NORMAL), amPmPos, amPmPos + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(
            TypefaceSpan("sans-serif"), amPmPos, amPmPos + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        return sp
    }

    fun get24ModeFormat(includeSeconds: Boolean): CharSequence {
        return DateFormat.getBestDateTimePattern(
            Locale.getDefault(),
            if (includeSeconds) "Hms" else "Hm")
    }

    fun getNumberFormattedQuantityString(context: Context, id: Int, quantity: Int): String {
        val localizedQuantity = NumberFormat.getInstance().format(quantity.toLong())
        return context.resources.getQuantityString(id, quantity, localizedQuantity)
    }

    val RINGTONE_SILENT: Uri = Uri.EMPTY
    val isLOrLater: Boolean
        get() = true

    val isMOrLater: Boolean
        get() = true

    val isNOrLater: Boolean
        get() = true

}
