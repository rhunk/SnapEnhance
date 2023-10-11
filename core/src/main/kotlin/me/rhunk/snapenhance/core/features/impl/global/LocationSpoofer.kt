package me.rhunk.snapenhance.core.features.impl.global

import android.location.Location
import android.location.LocationManager
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook

class LocationSpoofer: Feature("LocationSpoof", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        if (context.config.global.spoofLocation.globalState != true) return

        val coordinates by context.config.global.spoofLocation.coordinates

        Location::class.java.apply {
            hook("getLatitude", HookStage.BEFORE) { it.setResult(coordinates.first) }
            hook("getLongitude", HookStage.BEFORE) { it.setResult(coordinates.second) }
            hook("getAccuracy", HookStage.BEFORE) { it.setResult(0.0F) }
        }

        LocationManager::class.java.apply {
            //Might be redundant because it calls isProviderEnabledForUser which we also hook, meaning if isProviderEnabledForUser returns true this will also return true
            hook("isProviderEnabled", HookStage.BEFORE) { it.setResult(true) }
            hook("isProviderEnabledForUser", HookStage.BEFORE) { it.setResult(true) }
        }
    }
}