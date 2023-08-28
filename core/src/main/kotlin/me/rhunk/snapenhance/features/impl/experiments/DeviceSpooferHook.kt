package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class DeviceSpooferHook: Feature("device_spoofer", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC)  {
	override fun asyncOnActivityCreate() {
		if (context.config.experimental.spoof.globalState != true) return

		val fingerprint by context.config.experimental.spoof.device.fingerprint
		val androidId by context.config.experimental.spoof.device.androidId

		if (fingerprint.isNotEmpty()) {
			val fingerprintClass = android.os.Build::class.java
			Hooker.hook(fingerprintClass, "FINGERPRINT", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(fingerprint)
				Logger.debug("Fingerprint spoofed to $fingerprint")
			}
			Hooker.hook(fingerprintClass, "deriveFingerprint", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(fingerprint)
				Logger.debug("Fingerprint spoofed to $fingerprint")
			}
		}

		if (androidId.isNotEmpty()) {
			val settingsSecureClass = android.provider.Settings.Secure::class.java
			Hooker.hook(settingsSecureClass, "getString", HookStage.BEFORE) { hookAdapter ->
				if(hookAdapter.args()[1] == "android_id") {
					hookAdapter.setResult(androidId)
					Logger.debug("Android ID spoofed to $androidId")
				}
			}
		}
	}
}