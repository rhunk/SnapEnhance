package me.rhunk.snapenhance.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import me.rhunk.snapenhance.Logger

class ForceStartBroadcast : BroadcastReceiver() {
    companion object {
        const val ACTION = "me.rhunk.snapenhance.bridge.ForceStartBroadcast.FORCE_START_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        Logger.debug("ForceStartBroadcast received")
        Handler(Looper.getMainLooper()).postDelayed({}, 2000)
    }
}