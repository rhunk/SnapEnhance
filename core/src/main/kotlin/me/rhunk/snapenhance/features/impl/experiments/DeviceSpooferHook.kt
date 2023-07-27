package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class DeviceSpooferHook: Feature("device_spoofer", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC)  {
	override fun asyncOnActivityCreate() {
		//FINGERPRINT
		if(getFingerprint().isNotEmpty()) {
			val fingerprintClass = android.os.Build::class.java
			Hooker.hook(fingerprintClass, "FINGERPRINT", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(getFingerprint())
			}
			Hooker.hook(fingerprintClass, "deriveFingerprint", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(getFingerprint())
			}
		}
		else {
			Logger.xposedLog("Fingerprint is null, not spoofing")
		}
		
		//ANDROID ID
		if(getAndroidId().isNotEmpty()) {
			val settingsSecureClass = android.provider.Settings.Secure::class.java
			Hooker.hook(settingsSecureClass, "getString", HookStage.BEFORE) { hookAdapter ->
				if(hookAdapter.args()[1] == "android_id") {
					hookAdapter.setResult(getAndroidId())
				}
			}
		}
		else {
			Logger.xposedLog("Android ID is null, not spoofing")
		}
	}
	private fun getFingerprint():String {
		return context.config.string(ConfigProperty.FINGERPRINT)
	}
	
	private fun getAndroidId():String {
		return context.config.string(ConfigProperty.ANDROID_ID)
	}
	
}