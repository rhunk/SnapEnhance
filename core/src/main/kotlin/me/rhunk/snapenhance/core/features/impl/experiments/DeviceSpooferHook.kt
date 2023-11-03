package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.Hooker

class DeviceSpooferHook: Feature("device_spoofer", loadParams = FeatureLoadParams.INIT_SYNC)  {
	override fun init() {
		if (context.config.experimental.spoof.globalState != true) return

		val fingerprint by context.config.experimental.spoof.device.fingerprint
		val androidId by context.config.experimental.spoof.device.androidId
		val getInstallerPackageName by context.config.experimental.spoof.device.getInstallerPackageName
		val debugFlag by context.config.experimental.spoof.device.debugFlag
		val mockLocationState by context.config.experimental.spoof.device.mockLocationState
		val splitClassLoader by context.config.experimental.spoof.device.splitClassLoader
		val isLowEndDevice by context.config.experimental.spoof.device.isLowEndDevice
		val getDataDirectory by context.config.experimental.spoof.device.getDataDirectory

		val settingsSecureClass = android.provider.Settings.Secure::class.java
		val fingerprintClass = android.os.Build::class.java
		val packageManagerClass = android.content.pm.PackageManager::class.java
		val applicationInfoClass = android.content.pm.ApplicationInfo::class.java

		//FINGERPRINT
		if (fingerprint.isNotEmpty()) {
			Hooker.hook(fingerprintClass, "FINGERPRINT", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(fingerprint)
				context.log.verbose("Fingerprint spoofed to $fingerprint")
			}
			Hooker.hook(fingerprintClass, "deriveFingerprint", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(fingerprint)
				context.log.verbose("Fingerprint spoofed to $fingerprint")
			}
		}

		//ANDROID ID
		if (androidId.isNotEmpty()) {
			Hooker.hook(settingsSecureClass, "getString", HookStage.BEFORE) { hookAdapter ->
				if(hookAdapter.args()[1] == "android_id") {
					hookAdapter.setResult(androidId)
					context.log.verbose("Android ID spoofed to $androidId")
				}
			}
		}

		//TODO: org.chromium.base.BuildInfo, org.chromium.base.PathUtils getDataDirectory, MushroomDeviceTokenManager(?), TRANSPORT_VPN FLAG, isFromMockProvider, nativeLibraryDir, sourceDir, network capabilities, query all jvm properties

		//INSTALLER PACKAGE NAME
		if(getInstallerPackageName.isNotEmpty()) {
			Hooker.hook(packageManagerClass, "getInstallerPackageName", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(getInstallerPackageName)
			}
		}

		//DEBUG FLAG
		Hooker.hook(applicationInfoClass, "FLAG_DEBUGGABLE", HookStage.BEFORE) { hookAdapter ->
			hookAdapter.setResult(debugFlag)
		}

		//MOCK LOCATION
		Hooker.hook(settingsSecureClass, "getString", HookStage.BEFORE) { hookAdapter ->
			if(hookAdapter.args()[1] == "ALLOW_MOCK_LOCATION") {
				hookAdapter.setResult(mockLocationState)
			}
		}

		//GET SPLIT CLASSLOADER
		if(splitClassLoader.isNotEmpty()) {
			Hooker.hook(context.classCache.chromiumJNIUtils, "getSplitClassLoader", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(splitClassLoader)
			}
		}

		//ISLOWENDDEVICE
		if(isLowEndDevice.isNotEmpty()) {
			Hooker.hook(context.classCache.chromiumBuildInfo, "getAll", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(isLowEndDevice)
			}
		}

		//GETDATADIRECTORY
		if(getDataDirectory.isNotEmpty()) {
			Hooker.hook(context.classCache.chromiumPathUtils, "getDataDirectory", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(getDataDirectory)
			}
		}

		//accessibility_enabled
		
	}
}