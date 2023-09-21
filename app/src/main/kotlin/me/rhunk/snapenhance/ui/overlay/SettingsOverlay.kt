package me.rhunk.snapenhance.ui.overlay

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.arthenica.ffmpegkit.Packages.getPackageName
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.ui.AppMaterialTheme
import me.rhunk.snapenhance.ui.manager.EnumSection
import me.rhunk.snapenhance.ui.manager.Navigation
import me.rhunk.snapenhance.ui.manager.sections.features.FeaturesSection


class SettingsOverlay(
    private val context: RemoteSideContext
) {
    private lateinit var dialog: Dialog
    private fun checkForPermissions(): Boolean {
        if (!Settings.canDrawOverlays(context.androidContext)) {
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            myIntent.setData(Uri.parse("package:" + getPackageName()))
            context.androidContext.startActivity(myIntent)
            return false
        }
        return true
    }

    @Composable
    private fun OverlayContent() {
        val navHostController = rememberNavController()

        /*navHostController.addOnDestinationChangedListener { _, destination, _ ->
            dialog.setCancelable(destination.route == FeaturesSection.MAIN_ROUTE)
        }*/

        val navigation = remember {
            Navigation(
                context,
                mapOf(
                    EnumSection.FEATURES to FeaturesSection().apply {
                        enumSection = EnumSection.FEATURES
                        context = this@SettingsOverlay.context
                    }
                ),
                navHostController
            )
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { navigation.TopBar() }
        ) { innerPadding ->
            navigation.NavigationHost(
                startDestination = EnumSection.FEATURES,
                innerPadding = innerPadding
            )
        }
    }

    fun close() {
        if (!::dialog.isInitialized || !dialog.isShowing) return
        context.config.writeConfig()
        dialog.dismiss()
    }

    fun show() {
        if (!checkForPermissions()) {
            return
        }

        if (::dialog.isInitialized && dialog.isShowing) {
            return
        }

        context.androidContext.mainExecutor.execute {
            dialog = Dialog(context.androidContext, R.style.FullscreenOverlayDialog)
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }

            dialog.setContentView(
                overlayComposeView(context.androidContext).apply {
                    setContent {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                                .clip(shape = MaterialTheme.shapes.large),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AppMaterialTheme {
                                OverlayContent()
                            }
                        }
                    }
                }
            )

            dialog.setCancelable(true)
            dialog.setOnDismissListener {
                close()
            }

            dialog.show()
        }
    }
}