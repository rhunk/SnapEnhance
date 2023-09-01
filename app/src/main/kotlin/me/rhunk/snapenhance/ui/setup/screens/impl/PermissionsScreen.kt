package me.rhunk.snapenhance.ui.setup.screens.impl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.ui.setup.screens.SetupScreen
import me.rhunk.snapenhance.ui.util.ActivityLauncherHelper

class PermissionsScreen : SetupScreen() {
    private lateinit var activityLauncherHelper: ActivityLauncherHelper

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    @SuppressLint("BatteryLife")
    @Composable
    override fun Content() {
        var notificationPermissionGranted by remember { mutableStateOf(true) }
        var isBatteryOptimisationIgnored by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionGranted = context.androidContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
            val powerManager = context.androidContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            isBatteryOptimisationIgnored = powerManager.isIgnoringBatteryOptimizations(context.androidContext.packageName)
        }

        if (isBatteryOptimisationIgnored && notificationPermissionGranted) {
            allowNext(true)
        } else {
            allowNext(false)
        }

        DialogText(text = "To continue you need to fit the following requirements:")

        OutlinedCard(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(5.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Absolute.SpaceAround
                ) {
                    DialogText(text = "Notification access", modifier = Modifier.weight(1f))
                    if (notificationPermissionGranted) {
                        DialogText(text = "Granted")
                    } else {
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                activityLauncherHelper.requestPermission(Manifest.permission.POST_NOTIFICATIONS) { resultCode, _ ->
                                    coroutineScope.launch {
                                        notificationPermissionGranted = resultCode == ComponentActivity.RESULT_OK
                                    }
                                }
                            }
                        }) {
                            Text(text = "Request")
                        }
                    }
                }
                Row {
                    DialogText(text = "Battery optimisation", modifier = Modifier.weight(1f))
                    if (isBatteryOptimisationIgnored) {
                        DialogText(text = "Ignored")
                    } else {
                        Button(onClick = {
                            activityLauncherHelper.launch(Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = Uri.parse("package:${context.androidContext.packageName}")
                            }) { resultCode, _ ->
                                coroutineScope.launch {
                                    isBatteryOptimisationIgnored = resultCode == 0
                                }
                            }
                        }) {
                            Text(text = "Request")
                        }
                    }
                }
            }
        }
    }
}