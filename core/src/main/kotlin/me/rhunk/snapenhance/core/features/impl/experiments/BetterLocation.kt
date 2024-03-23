package me.rhunk.snapenhance.core.features.impl.experiments

import android.location.Location
import android.location.LocationManager
import me.rhunk.snapenhance.common.util.protobuf.EditorContext
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.global.SuspendLocationUpdates
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.days

class BetterLocation : Feature("Better Location", loadParams = FeatureLoadParams.INIT_SYNC) {
    private fun editClientUpdate(editor: EditorContext) {
        val config = context.config.global.betterLocation

        editor.apply {
            // SCVSLocationUpdate
            edit(1) {
                context.log.verbose("SCVSLocationUpdate ${this@apply}")
                if (config.spoofLocation.get()) {
                    val coordinates by config.coordinates
                    remove(1)
                    remove(2)
                    addFixed32(1, coordinates.first.toFloat()) // lat
                    addFixed32(2, coordinates.second.toFloat()) // lng
                }

                if (config.alwaysUpdateLocation.get()) {
                    remove(7)
                    addVarInt(7, System.currentTimeMillis()) // timestamp
                }

                if (context.feature(SuspendLocationUpdates::class).isSuspended()) {
                    remove(7)
                    addVarInt(7, System.currentTimeMillis() - 15.days.inWholeMilliseconds)
                }
            }

            // SCVSDeviceData
            edit(3) {
                config.spoofBatteryLevel.getNullable()?.takeIf { it.isNotEmpty() }?.let {
                    val value = it.toIntOrNull()?.toFloat()?.div(100) ?: return@edit
                    remove(2)
                    addFixed32(2, value)
                    if (value == 100F) {
                        remove(3)
                        addVarInt(3, 1) // devicePluggedIn
                    }
                }

                if (config.spoofHeadphones.get()) {
                    remove(4)
                    addVarInt(4, 1) // headphoneOutput
                    remove(6)
                    addVarInt(6, 1) // isOtherAudioPlaying
                }

                edit(10) {
                    remove(1)
                    addVarInt(1, 4) // type = ALWAYS
                    remove(2)
                    addVarInt(2, 1) // precise = true
                }
            }
        }
    }

    override fun init() {
        if (context.config.global.betterLocation.globalState != true) return

        if (context.config.global.betterLocation.spoofLocation.get()) {
            LocationManager::class.java.apply {
                hook("isProviderEnabled", HookStage.BEFORE) { it.setResult(true) }
                hook("isProviderEnabledForUser", HookStage.BEFORE) { it.setResult(true) }
            }
            Location::class.java.apply {
                hook("getLatitude", HookStage.BEFORE) {
                    it.setResult(context.config.global.betterLocation.coordinates.get().first) }
                hook("getLongitude", HookStage.BEFORE) {
                    it.setResult(context.config.global.betterLocation.coordinates.get().second)
                }
            }
        }

        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (event.uri == "/snapchat.valis.Valis/SendClientUpdate") {
                event.buffer = ProtoEditor(event.buffer).apply {
                    edit {
                        editEach(1) {
                            editClientUpdate(this)
                        }
                    }
                }.toByteArray()
            }
        }

        findClass("com.snapchat.client.grpc.ClientStreamSendHandler\$CppProxy").hook("send", HookStage.BEFORE) { param ->
            val array = param.arg<ByteBuffer>(0).let {
                it.position(0)
                ByteArray(it.capacity()).also { buffer -> it.get(buffer); it.position(0) }
            }

            param.setArg(0, ProtoEditor(array).apply {
                edit {
                    editClientUpdate(this)
                }
            }.toByteArray().let {
                ByteBuffer.allocateDirect(it.size).put(it).rewind()
            })
        }
    }
}