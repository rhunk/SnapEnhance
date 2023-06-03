package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class FingerprintSpoof : Feature("Fingerprint Spoofer", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC)  {
	override fun asyncOnActivityCreate() {
		if(getFingerprint().isEmpty()) return
		
		val fingerprintClass = android.os.Build::class.java
		
		Hooker.hook(fingerprintClass, "FINGERPRINT", HookStage.BEFORE) {hookAdapter ->
			hookAdapter.setResult(getFingerprint())
		}
		Hooker.hook(fingerprintClass, "deriveFingerprint", HookStage.BEFORE) {hookAdapter ->
			hookAdapter.setResult(getFingerprint())
		}
	}
	
	private fun getFingerprint():String {
		return context.config.string(ConfigProperty.FINGERPRINT)
	}
	
}