package me.rhunk.snapenhance.action.impl

import android.content.Intent
import android.os.Bundle
import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.core.BuildConfig
import me.rhunk.snapenhance.ui.map.MapActivity

class OpenMap: AbstractAction("action.open_map") {
    override fun run() {
        context.runOnUiThread {
            val mapActivityIntent = Intent()
            mapActivityIntent.setClassName(BuildConfig.APPLICATION_ID, MapActivity::class.java.name)
            mapActivityIntent.putExtra("location", Bundle().apply {
                putDouble("latitude", context.config.spoof.location.latitude.get().toDouble())
                putDouble("longitude", context.config.spoof.location.longitude.get().toDouble())
            })

            context.mainActivity!!.startActivityForResult(mapActivityIntent, 0x1337)
        }
    }
}