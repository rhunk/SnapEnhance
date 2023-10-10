package me.rhunk.snapenhance.core.features

object FeatureLoadParams {
    const val NO_INIT = 0

    const val INIT_SYNC = 0b0001
    const val ACTIVITY_CREATE_SYNC = 0b0010

    const val INIT_ASYNC = 0b0100
    const val ACTIVITY_CREATE_ASYNC = 0b1000
}