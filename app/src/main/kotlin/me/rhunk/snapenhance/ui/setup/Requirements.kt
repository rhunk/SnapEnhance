package me.rhunk.snapenhance.ui.setup

object Requirements {
    const val FIRST_RUN = 0b00001
    const val LANGUAGE = 0b00010
    const val MAPPINGS = 0b00100
    const val SAVE_FOLDER = 0b01000

    fun getName(requirement: Int): String {
        return when (requirement) {
            FIRST_RUN -> "FIRST_RUN"
            LANGUAGE -> "LANGUAGE"
            MAPPINGS -> "MAPPINGS"
            SAVE_FOLDER -> "SAVE_FOLDER"
            else -> "UNKNOWN"
        }
    }
}

