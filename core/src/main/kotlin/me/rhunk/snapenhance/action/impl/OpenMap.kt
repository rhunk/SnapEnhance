package me.rhunk.snapenhance.action.impl

import android.content.Intent
import android.os.Bundle
import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.core.BuildConfig
import me.rhunk.snapenhance.ui.map.MapActivity

class OpenMap: AbstractAction("action.open_map", dependsOnProperty = ConfigProperty.LOCATION_SPOOF) {
    override fun run() {
        context.runOnUiThread {
            val mapActivityIntent = Intent()
            mapActivityIntent.setClassName(BuildConfig.LIBRARY_PACKAGE_NAME, MapActivity::class.java.name)
            mapActivityIntent.putExtra("location", Bundle().apply {
                putDouble("latitude", context.config.string(ConfigProperty.LATITUDE).toDouble())
                putDouble("longitude", context.config.string(ConfigProperty.LONGITUDE).toDouble())
            })

            context.mainActivity!!.startActivityForResult(mapActivityIntent, 0x1337)
        }
    }
}