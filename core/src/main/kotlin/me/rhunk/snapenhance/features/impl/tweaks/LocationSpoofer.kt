package me.rhunk.snapenhance.features.impl.tweaks

import android.content.Intent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.hook.hook

class LocationSpoofer: Feature("LocationSpoof", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        Hooker.hook(context.mainActivity!!.javaClass, "onActivityResult", HookStage.BEFORE) { param ->
            val intent = param.argNullable<Intent>(2) ?: return@hook
            val bundle = intent.getBundleExtra("location") ?: return@hook
            param.setResult(null)

            with(context.config.experimental.spoof.location) {
                latitude.set(bundle.getFloat("latitude"))
                longitude.set(bundle.getFloat("longitude"))

                context.longToast("Location set to $latitude, $longitude")
            }
        }

        if (context.config.experimental.spoof.location.globalState != true) return

        val latitude by context.config.experimental.spoof.location.latitude
        val longitude by context.config.experimental.spoof.location.longitude

        val locationClass = android.location.Location::class.java
        val locationManagerClass = android.location.LocationManager::class.java

        locationClass.hook("getLatitude", HookStage.BEFORE) { it.setResult(latitude.toDouble()) }
        locationClass.hook("getLongitude", HookStage.BEFORE) { it.setResult(longitude.toDouble()) }
        locationClass.hook("getAccuracy", HookStage.BEFORE) { it.setResult(0.0F) }

        //Might be redundant because it calls isProviderEnabledForUser which we also hook, meaning if isProviderEnabledForUser returns true this will also return true
        locationManagerClass.hook("isProviderEnabled", HookStage.BEFORE) { it.setResult(true) }
        locationManagerClass.hook("isProviderEnabledForUser", HookStage.BEFORE) { it.setResult(true) }
    }
}