package me.rhunk.snapenhance.features

import me.rhunk.snapenhance.ModContext

abstract class Feature(
    val nameKey: String,
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
}