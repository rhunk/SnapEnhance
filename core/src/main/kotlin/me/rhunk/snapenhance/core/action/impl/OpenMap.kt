package me.rhunk.snapenhance.core.action.impl

import android.content.Intent
import android.os.Bundle
import me.rhunk.snapenhance.common.BuildConfig
import me.rhunk.snapenhance.core.action.AbstractAction

class OpenMap: AbstractAction() {
    override fun run() {
        context.runOnUiThread {
            val mapActivityIntent = Intent()
            mapActivityIntent.setClassName(BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID + ".ui.MapActivity")
            mapActivityIntent.putExtra("location", Bundle().apply {
                putDouble("latitude", context.config.experimental.spoof.location.latitude.get().toDouble())
                putDouble("longitude", context.config.experimental.spoof.location.longitude.get().toDouble())
            })

            context.mainActivity!!.startActivityForResult(mapActivityIntent, 0x1337)
        }
    }
}