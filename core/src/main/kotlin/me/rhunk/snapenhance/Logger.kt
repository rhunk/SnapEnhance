package me.rhunk.snapenhance

import android.util.Log
import de.robv.android.xposed.XposedBridge
import me.rhunk.snapenhance.core.BuildConfig

object Logger {
    private const val TAG = "SnapEnhance"

    fun log(message: Any?) {
        Log.i(TAG, message.toString())
    }

    fun debug(message: Any?) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, message.toString())
    }

    fun debug(tag: String, message: Any?) {
        if (!BuildConfig.DEBUG) return
        Log.d(tag, message.toString())
    }

    fun error(throwable: Throwable) {
        Log.e(TAG, "", throwable)
    }

    fun error(message: Any?) {
        Log.e(TAG, message.toString())
    }

    fun error(message: Any?, throwable: Throwable) {
        Log.e(TAG, message.toString(), throwable)
    }

    fun xposedLog(message: Any?) {
        XposedBridge.log(message.toString())
    }

    fun xposedLog(message: Any?, throwable: Throwable?) {
        XposedBridge.log(message.toString())
        XposedBridge.log(throwable)
    }

    fun xposedLog(throwable: Throwable) {
        XposedBridge.log(throwable)
    }
}