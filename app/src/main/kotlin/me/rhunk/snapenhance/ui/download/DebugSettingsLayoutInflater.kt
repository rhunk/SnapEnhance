package me.rhunk.snapenhance.ui.download

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.bridge.types.BridgeFileType
import me.rhunk.snapenhance.ui.config.ConfigActivity
import me.rhunk.snapenhance.ui.spoof.DeviceSpooferActivity
import java.io.File

class ActionListAdapter(
    private val activity: DownloadManagerActivity,
    private val layoutId: Int,
    private val actions: Array<Pair<String, () -> Unit>>
) : ArrayAdapter<Pair<String, () -> Unit>>(activity, layoutId, actions) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: activity.layoutInflater.inflate(layoutId, parent, false)
        val action = actions[position]
        view.isClickable = true

        view.findViewById<TextView>(R.id.feature_text).text = action.first
        view.setOnClickListener {
            action.second()
        }

        return view
    }
}

class DebugSettingsLayoutInflater(
    private val activity: DownloadManagerActivity
) {
    private fun confirmAction(title: String, message: String, action: () -> Unit) {
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(SharedContext.translation["button.positive"]) { _, _ ->
                    action()
                }
                .setNegativeButton(SharedContext.translation["button.negative"]) { _, _ -> }
                .show()
        }
    }

    private fun showSuccessToast() {
        Toast.makeText(activity, "Success", Toast.LENGTH_SHORT).show()
    }

    fun inflate(parent: ViewGroup) {
        val debugSettingsLayout = activity.layoutInflater.inflate(R.layout.debug_settings_page, parent, false)

        val debugSettingsTranslation = activity.translation.getCategory("debug_settings_page")

        debugSettingsLayout.findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            parent.removeView(debugSettingsLayout)
        }

        debugSettingsLayout.findViewById<TextView>(R.id.title).text = activity.translation["debug_settings"]

        debugSettingsLayout.findViewById<ListView>(R.id.setting_page_list).apply {
            adapter = ActionListAdapter(activity, R.layout.debug_setting_item, mutableListOf<Pair<String, () -> Unit>>().apply {
                add(SharedContext.translation["config_activity.title"] to {
                    activity.startActivity(Intent(activity, ConfigActivity::class.java))
                })
                add(SharedContext.translation["spoof_activity.title"] to {
                    activity.startActivity(Intent(activity, DeviceSpooferActivity::class.java))
                })
                add(debugSettingsTranslation["clear_cache_title"] to {
                    context.cacheDir.listFiles()?.forEach {
                        it.deleteRecursively()
                    }
                    showSuccessToast()
                })

                BridgeFileType.values().forEach { fileType ->
                    val actionName = debugSettingsTranslation.format("clear_file_title", "file_name" to fileType.displayName)
                    add(actionName to {
                        confirmAction(actionName, debugSettingsTranslation.format("clear_file_confirmation", "file_name" to fileType.displayName)) {
                            fileType.resolve(context).deleteRecursively()
                            showSuccessToast()
                        }
                    })
                }

                add(debugSettingsTranslation["reset_all_title"] to {
                    confirmAction(debugSettingsTranslation["reset_all_title"], debugSettingsTranslation["reset_all_confirmation"]) {
                        arrayOf(context.cacheDir, context.filesDir, File(context.dataDir, "databases"), File(context.dataDir, "shared_prefs")).forEach {
                            it.listFiles()?.forEach { file ->
                                file.deleteRecursively()
                            }
                        }
                        showSuccessToast()
                    }
                })
            }.toTypedArray())
        }

        activity.registerBackCallback {
            parent.removeView(debugSettingsLayout)
        }

        parent.addView(debugSettingsLayout)
    }
}