package me.rhunk.snapenhance.action

import me.rhunk.snapenhance.ModContext
import java.io.File

abstract class AbstractAction(
    val nameKey: String
) {
    lateinit var context: ModContext

    /**
     * called on the main thread when the mod initialize
     */
    open fun init() {}

    /**
     * called when the action is triggered
     */
    open fun run() {}

    protected open fun deleteRecursively(parent: File?) {
        if (parent == null) return
        if (parent.isDirectory) for (child in parent.listFiles()!!) deleteRecursively(
            child
        )
        if (parent.exists() && (parent.isFile || parent.isDirectory)) {
            parent.delete()
        }
    }
}