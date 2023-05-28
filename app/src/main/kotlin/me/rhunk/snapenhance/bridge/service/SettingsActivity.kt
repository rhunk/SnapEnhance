@file:Suppress("DEPRECATION")

package me.rhunk.snapenhance.bridge.service

import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceActivity
import me.rhunk.snapenhance.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsActivity : PreferenceActivity(), Preference.OnPreferenceChangeListener {
    private val preferenceKeys = arrayOf(
        "SAVE_FOLDER",
        "PREVENT_READ_RECEIPTS",
        "HIDE_BITMOJI_PRESENCE",
        "SHOW_MESSAGE_CONTENT_IN_NOTIFICATIONS",
        "NOTIFICATION_FILTER",
        "MESSAGE_LOGGER",
        "UNLIMITED_SNAP_VIEW_TIME",
        "AUTO_DOWNLOAD_SNAPS",
        "AUTO_DOWNLOAD_STORIES",
        "AUTO_DOWNLOAD_PUBLIC_STORIES",
        "AUTO_DOWNLOAD_SPOTLIGHT",
        "OVERLAY_MERGE",
        "DOWNLOAD_INCHAT_SNAPS",
        "ANTI_DOWNLOAD_BUTTON",
        "ANTI_AUTO_SAVE",
        "hide_ui_elements",
        "BLOCK_ADS",
        "DISABLE_METRICS",
        "PREVENT_SCREENSHOT_NOTIFICATIONS",
        "PREVENT_STATUS_NOTIFICATIONS",
        "ANONYMOUS_STORY_VIEW",
        "HIDE_TYPING_NOTIFICATION",
        "MENU_SLOT_ID",
        "MESSAGE_PREVIEW_LENGTH",
        "NEW_MAP_UI",
        "STREAK_EXPIRATION_INFO",
        "EXTERNAL_MEDIA_AS_SNAP",
        "AUTO_SAVE",
        "SNAPCHAT_PLUS",
        "DISABLE_VIDEO_LENGTH_RESTRICTION",
        "DISABLE_SNAP_SPLITTING",
        "USE_DOWNLOAD_MANAGER",
        "OVERRIDE_MEDIA_QUALITY",
        "MEDIA_QUALITY_LEVEL",
        "MEO_PASSCODE_BYPASS"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preference_manager)

        preferenceKeys.forEach { key ->
            val preference = findPreference(key)
            preference?.onPreferenceChangeListener = this
        }
    }

    init {
        Runtime.getRuntime().exec("su")
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (preference.key == "hide_ui_elements") {
            val selectedElements = newValue as Set<String>
            updateHideUIElements(selectedElements)
        } else if (preference.key == "SAVE_FOLDER") {
            val value = newValue.toString()
            updateSaveFolder(value)
        } else {
            val value = newValue.toString()
            File(filesDir, "config.json").writeText(updateJsonValue(File(filesDir, "config.json").readText(), preference.key, value))
        }
        return true
    }

    private fun updateSaveFolder(value: String) {
        val updatedConfigJson = updateJsonValue(File(filesDir, "config.json").readText(), "SAVE_FOLDER", value)
        File(filesDir, "config.json").writeText(updatedConfigJson)
    }

    private fun updateHideUIElements(selectedElements: Set<String>) {
        val configJson = JSONObject(File(filesDir, "config.json").readText())
        val uiHideValues = resources.getStringArray(R.array.ui_hide_values)
        for (elementKey in uiHideValues) {
            configJson.put(elementKey, selectedElements.contains(elementKey))
        }
        File(filesDir, "config.json").writeText(configJson.toString())
    }

    private fun updateJsonValue(jsonString: String, key: String, value: String): String {
        val jsonObject = JSONObject(jsonString)
        jsonObject.put(key, value)
        return jsonObject.toString()
    }
}