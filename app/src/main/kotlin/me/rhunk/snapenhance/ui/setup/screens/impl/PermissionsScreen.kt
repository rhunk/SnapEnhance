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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

    @Composable
    private fun RequestButton(onClick: () -> Unit) {
        Button(onClick = onClick) {
            Text(text = context.translation["setup.permissions.request_button"])
        }
    }

    @Composable
    private fun GrantedIcon() {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(5.dp)
        )
    }

    @SuppressLint("BatteryLife")
    @Composable
    override fun Content() {
        var notificationPermissionGranted by remember { mutableStateOf(true) }
        var isBatteryOptimisationIgnored by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        if (isBatteryOptimisationIgnored && notificationPermissionGranted) {
            allowNext(true)
        } else {
            allowNext(false)
        }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionGranted =
                    context.androidContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
            val powerManager =
                context.androidContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            isBatteryOptimisationIgnored =
                powerManager.isIgnoringBatteryOptimizations(context.androidContext.packageName)
            if (isBatteryOptimisationIgnored && notificationPermissionGranted) {
                goNext()
            }
        }

        DialogText(text = context.translation["setup.permissions.dialog"])

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(all = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogText(
                        text = context.translation["setup.permissions.notification_access"],
                        modifier = Modifier.weight(1f)
                    )
                    if (notificationPermissionGranted) {
                        GrantedIcon()
                    } else {
                        RequestButton {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                activityLauncherHelper.requestPermission(Manifest.permission.POST_NOTIFICATIONS) { resultCode, _ ->
                                    coroutineScope.launch {
                                        notificationPermissionGranted =
                                            resultCode == ComponentActivity.RESULT_OK
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogText(
                        text = context.translation["setup.permissions.battery_optimization"],
                        modifier = Modifier.weight(1f)
                    )
                    if (isBatteryOptimisationIgnored) {
                        GrantedIcon()
                    } else {
                        RequestButton {
                            activityLauncherHelper.launch(Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = Uri.parse("package:${context.androidContext.packageName}")
                            }) { resultCode, _ ->
                                coroutineScope.launch {
                                    isBatteryOptimisationIgnored = resultCode == 0
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}