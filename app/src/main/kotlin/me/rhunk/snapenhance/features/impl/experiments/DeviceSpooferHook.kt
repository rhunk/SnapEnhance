package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class DeviceSpooferHook :Feature("device_spoofer", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC)  {
	override fun asyncOnActivityCreate() {
		if(getFingerprint().isEmpty()) return
		
		//FINGERPRINT
		val fingerprintClass = android.os.Build::class.java
		Hooker.hook(fingerprintClass, "FINGERPRINT", HookStage.BEFORE) {hookAdapter ->
			hookAdapter.setResult(getFingerprint())
		}
		Hooker.hook(fingerprintClass, "deriveFingerprint", HookStage.BEFORE) {hookAdapter ->
			hookAdapter.setResult(getFingerprint())
		}
		
		if(getAndroidId().isEmpty()) return
		
		//ANDROID ID
		val settingsSecureClass = android.provider.Settings.Secure::class.java
		Hooker.hook(settingsSecureClass, "getString", HookStage.BEFORE) {hookAdapter ->
			if(hookAdapter.args()[1] == "android_id") {
				hookAdapter.setResult(getAndroidId())
			}
		}
		
		
	}
	private fun getFingerprint():String {
		return context.config.string(ConfigProperty.FINGERPRINT)
	}
	
	private fun getAndroidId():String {
		return context.config.string(ConfigProperty.ANDROID_ID)
	}
	
}