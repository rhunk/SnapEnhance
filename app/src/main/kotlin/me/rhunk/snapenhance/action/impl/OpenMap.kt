package me.rhunk.snapenhance.action.impl

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.ui.map.MapActivity

class OpenMap: AbstractAction("action.open_map", dependsOnProperty = ConfigProperty.LOCATION_SPOOF) {
    override fun run() {
        context.runOnUiThread {
            if(!context.config.bool(ConfigProperty.LOCATION_SPOOF)) {
                //TODO: i18n?
                Toast.makeText(context.mainActivity, "Location Spoofer is not enabled!", Toast.LENGTH_SHORT).show()
                return@runOnUiThread
            }

            val mapActivityIntent = Intent()
            mapActivityIntent.setClassName(BuildConfig.APPLICATION_ID, MapActivity::class.java.name)
            mapActivityIntent.putExtra("location", Bundle().apply {
                putDouble("latitude", context.config.string(ConfigProperty.LATITUDE).toDouble())
                putDouble("longitude", context.config.string(ConfigProperty.LONGITUDE).toDouble())
            })

            context.mainActivity!!.startActivityForResult(mapActivityIntent, 0x1337)
        }
    }
}