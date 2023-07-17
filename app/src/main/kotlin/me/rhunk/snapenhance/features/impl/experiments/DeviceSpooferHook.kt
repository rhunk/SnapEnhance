package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.SnapClassCache
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookAdapter
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class DeviceSpooferHook: Feature("device_spoofer", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC)  {
	private val settingsSecureClass = android.provider.Settings.Secure::class.java
	private val fingerprintClass = android.os.Build::class.java
	private val packageManagerClass = android.content.pm.PackageManager::class.java
	private val applicationInfoClass = android.content.pm.ApplicationInfo::class.java
	override fun asyncOnActivityCreate() {
		if(!context.config.bool(ConfigProperty.DEVICE_SPOOF)) return
		
		//FINGERPRINT
		if(getFingerprint().isNotEmpty()) {
			Hooker.hook(fingerprintClass, "FINGERPRINT", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(getFingerprint())
			}
			Hooker.hook(fingerprintClass, "deriveFingerprint", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(getFingerprint())
			}
		}
		
		//ANDROID ID
		if(getAndroidId().isNotEmpty()) {
			Hooker.hook(settingsSecureClass, "getString", HookStage.BEFORE) { hookAdapter ->
				if(hookAdapter.args()[1] == "android_id") {
					hookAdapter.setResult(getAndroidId())
				}
			}
		}
		
		//TODO: org.chromium.base.BuildInfo, org.chromium.base.PathUtils getDataDirectory, MushroomDeviceTokenManager(?), TRANSPORT_VPN FLAG, isFromMockProvider, nativeLibraryDir, sourceDir, network capabilities, query all jvm properties
		
		//INSTALLER PACKAGE NAME
		if(getInstallerPackageName().isNotEmpty()) {
			Hooker.hook(packageManagerClass, "getInstallerPackageName", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(getInstallerPackageName())
			}
		}
		
		//DEBUG FLAG
		Hooker.hook(applicationInfoClass, "FLAG_DEBUGGABLE", HookStage.BEFORE) { hookAdapter ->
			hookAdapter.setResult(getDebugFlagState())
		}
		
		//MOCK LOCATION
		Hooker.hook(settingsSecureClass, "getString", HookStage.BEFORE) { hookAdapter ->
			if(hookAdapter.args()[1] == "ALLOW_MOCK_LOCATION") {
				hookAdapter.setResult(getMockLocationState())
			}
		}
		
		//GET SPLIT CLASSLOADER
		if(splitClassloader().isNotEmpty()) {
			Hooker.hook(context.classCache.chromiumJNIUtils, "getSplitClassLoader", HookStage.BEFORE) { hookAdapter ->
				hookAdapter.setResult(splitClassloader())
			}
		}
	}
	private fun getFingerprint():String {
		return context.config.string(ConfigProperty.FINGERPRINT)
	}
	
	private fun getAndroidId():String {
		return context.config.string(ConfigProperty.ANDROID_ID)
	}
	private fun getInstallerPackageName():String {
		return context.config.string(ConfigProperty.INSTALLER_PACKAGE_NAME)
	}
	
	private fun getDebugFlagState():Boolean {
		return context.config.bool(ConfigProperty.DEBUGGABLE_FLAG)
	}
	
	private fun getMockLocationState():Int {
		if(context.config.bool(ConfigProperty.MOCK_LOCATION_FLAG).toString() == "false") {
			return 0
		}
		return 1
	}
	
	private fun splitClassloader():String {
		return context.config.string(ConfigProperty.SPLIT_CLASSLOADER)
	}
 }