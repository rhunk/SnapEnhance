package me.rhunk.snapenhance.core.features.impl.experiments

import android.annotation.SuppressLint
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.LSPatchUpdater
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook

class DeviceSpooferHook: Feature("device_spoofer", loadParams = FeatureLoadParams.INIT_SYNC)  {
	private fun hookInstallerPackageName() {
		context.androidContext.packageManager::class.java.hook("getInstallerPackageName", HookStage.BEFORE) { param ->
			param.setResult("com.android.vending")
		}
	}

	@SuppressLint("MissingPermission")
	override fun init() {
		// force installer package name for lspatch users
		if (LSPatchUpdater.HAS_LSPATCH) {
			hookInstallerPackageName()
		}

		if (context.config.experimental.spoof.globalState != true) return

		val fingerprint by context.config.experimental.spoof.fingerprint
		val androidId by context.config.experimental.spoof.androidId
		val removeMockLocationFlag by context.config.experimental.spoof.removeMockLocationFlag
		val overridePlayStoreInstallerPackageName by context.config.experimental.spoof.overridePlayStoreInstallerPackageName
		val removeVpnTransportFlag by context.config.experimental.spoof.removeVpnTransportFlag
		val randomizePersistentDeviceToken by context.config.experimental.spoof.randomizePersistentDeviceToken

		//Installer package name
		if(overridePlayStoreInstallerPackageName) {
			hookInstallerPackageName()
		}

		findClass("android.provider.Settings\$NameValueCache").apply {
			hook("getStringForUser", HookStage.BEFORE) { hookAdapter ->
				val key = hookAdapter.argNullable<String>(1) ?: return@hook
				when (key) {
					"android_id" -> {
						if (androidId.isNotEmpty()) {
							hookAdapter.setResult(androidId)
						}
					}
					"ALLOW_MOCK_LOCATION" -> {
						if (removeMockLocationFlag) {
							hookAdapter.setResult("0")
						}
					}
				}
			}
		}

		if (removeMockLocationFlag) {
			Location::class.java.hook("isMock", HookStage.BEFORE) { param ->
				param.setResult(false)
			}
		}

		if (randomizePersistentDeviceToken) {
			context.androidContext.filesDir.resolve("Snapchat").listFiles()?.firstOrNull {
				it.name.startsWith("device_token")
			}?.delete()
		}

		if (removeVpnTransportFlag) {
			ConnectivityManager::class.java.hook("getAllNetworks", HookStage.AFTER) { param ->
				val instance = param.thisObject() as? ConnectivityManager ?: return@hook
				val networks = param.getResult() as? Array<*> ?: return@hook

				param.setResult(networks.filterIsInstance<Network>().filter { network ->
					val capabilities = instance.getNetworkCapabilities(network) ?: return@filter false
					!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
				}.toTypedArray())
			}
		}

		if (fingerprint.isNotEmpty()) {
			Build.FINGERPRINT // init fingerprint field
			Build::class.java.getField("FINGERPRINT").apply {
				isAccessible = true
				set(null, fingerprint)
				isAccessible = false
			}
		}
	}
}