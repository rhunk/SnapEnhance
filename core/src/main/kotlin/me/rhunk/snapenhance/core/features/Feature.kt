package me.rhunk.snapenhance.core.features

import me.rhunk.snapenhance.core.ModContext

abstract class Feature(
    val featureKey: String,
    val loadParams: Int = FeatureLoadParams.INIT_SYNC
) {
    lateinit var context: ModContext

    /**
     * called on the main thread when the mod initialize
     */
    open fun init() {}

    /**
     * called on a dedicated thread when the mod initialize
     */
    open fun asyncInit() {}

    /**
     * called when the Snapchat Activity is created
     */
    open fun onActivityCreate() {}


    /**
     * called on a dedicated thread when the Snapchat Activity is created
     */
    open fun asyncOnActivityCreate() {}

    protected fun findClass(name: String): Class<*> {
        return context.androidContext.classLoader.loadClass(name)
    }

    protected fun runOnUiThread(block: () -> Unit) {
        context.runOnUiThread(block)
    }
}