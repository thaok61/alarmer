package com.thao_soft.alarmer

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.material.snackbar.Snackbar
import com.thao_soft.alarmer.data.AlarmSettingColumns.Companion.NO_RINGTONE_URI
import com.thao_soft.alarmer.ui.screens.ScaffoldAlarmer
import com.thao_soft.alarmer.ui.screens.ScaffoldRingtone
import com.thao_soft.alarmer.ui.theme.AlarmerTheme
import java.util.TimeZone

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(
            arrayOf(
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.POST_NOTIFICATIONS
            ), 200
        )
        val nm: NotificationManagerCompat = NotificationManagerCompat.from(this)
        if (!nm.canUseFullScreenIntent()) {
            val fullScreenIntent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            startActivity(fullScreenIntent)
        }


        setContent {
            AlarmerTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        ScaffoldAlarmer(onNavigateRingtone = { alarmId ->
                            navController.navigate("ringtone?alarmId=$alarmId")
                        })
                    }
                    composable(
                        "ringtone?alarmId={alarmId}",
                        arguments = listOf(
                            navArgument("alarmId") { defaultValue = "-1" },
                        )
                    ) { backStackEntry ->
                        val alarmStringId =
                            backStackEntry.arguments?.getString("alarmId") ?: "-1"
                        val alarmId = alarmStringId.toLong()
                        ScaffoldRingtone(alarmId, onNavigateUp = {
                            navController.navigateUp()
                        })
                    }
                }
            }
        }

    }

    companion object {
        /** UTC does not have DST rules and will not alter the [.mHour] and [.mMinute].  */
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    }
}