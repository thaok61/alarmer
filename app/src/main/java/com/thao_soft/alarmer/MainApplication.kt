package com.thao_soft.alarmer

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.thao_soft.alarmer.data.DataModel
import com.thao_soft.alarmer.utils.Utils

class MainApplication : Application()  {
    override fun onCreate() {
        super.onCreate()
        val applicationContext = applicationContext
        val prefs = getDefaultSharedPreferences(applicationContext)
        DataModel.dataModel.init(applicationContext, prefs)
    }

    companion object {
        const val TAG = "MainApplication"
        /**
         * Returns the default [SharedPreferences] instance from the underlying storage context.
         */
        private fun getDefaultSharedPreferences(context: Context): SharedPreferences {
            val storageContext: Context
            if (Utils.isNOrLater) {
                // All N devices have split storage areas. Migrate the existing preferences
                // into the new device encrypted storage area if that has not yet occurred.
                val name = context.packageName + "_preferences"

                storageContext = context.createDeviceProtectedStorageContext()

                if (!storageContext.moveSharedPreferencesFrom(context, name)) {
                    Log.wtf(TAG, "getDefaultSharedPreferences: ")
                }
            } else {
                storageContext = context
            }
            return PreferenceManager.getDefaultSharedPreferences(storageContext)
        }
    }

}

