package me.rhunk.snapenhance.manager.setup

import android.os.Bundle

data class Requirements(
    val firstRun: Boolean = false,
    val language: Boolean = false,
    val mappings: Boolean = false,
    val saveFolder: Boolean = false,
    val ffmpeg: Boolean = false
) {
    companion object {
        fun fromBundle(bundle: Bundle): Requirements {
            return Requirements(
                firstRun = bundle.getBoolean("firstRun"),
                language = bundle.getBoolean("language"),
                mappings = bundle.getBoolean("mappings"),
                saveFolder = bundle.getBoolean("saveFolder"),
                ffmpeg = bundle.getBoolean("ffmpeg")
            )
        }

        fun toBundle(requirements: Requirements): Bundle {
            return Bundle().apply {
                putBoolean("firstRun", requirements.firstRun)
                putBoolean("language", requirements.language)
                putBoolean("mappings", requirements.mappings)
                putBoolean("saveFolder", requirements.saveFolder)
                putBoolean("ffmpeg", requirements.ffmpeg)
            }
        }
    }
}
