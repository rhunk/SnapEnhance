package me.rhunk.snapenhance.ui.download

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.bridge.common.impl.file.BridgeFileType

class SettingAdapter(
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

class SettingLayoutInflater(
    private val activity: DownloadManagerActivity
) {
    fun inflate(parent: ViewGroup) {
        val settingsView = activity.layoutInflater.inflate(R.layout.settings_page, parent, false)

        settingsView.findViewById<ImageButton>(R.id.settings_button).setOnClickListener {
            parent.removeView(settingsView)
        }

        settingsView.findViewById<ListView>(R.id.setting_page_list).apply {
            adapter = SettingAdapter(activity, R.layout.setting_item, mutableListOf<Pair<String, () -> Unit>>().apply {
                add("Clear Cache" to {
                    context.cacheDir.deleteRecursively()
                    Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                })

                BridgeFileType.values().forEach { fileType ->
                    add("Clear ${fileType.displayName} File" to {
                        fileType.resolve(context).deleteRecursively()
                        Toast.makeText(context, "${fileType.displayName} file cleared", Toast.LENGTH_SHORT).show()
                    })
                }

                add("Reset All" to {
                    context.dataDir.deleteRecursively()
                    Toast.makeText(context, "Success!", Toast.LENGTH_SHORT).show()
                })
            }.toTypedArray())
        }

        activity.registerBackCallback {
            parent.removeView(settingsView)
        }

        parent.addView(settingsView)
    }
}