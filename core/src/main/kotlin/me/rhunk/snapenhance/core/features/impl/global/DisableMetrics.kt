package me.rhunk.snapenhance.core.features.impl.global

import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams

class DisableMetrics : Feature("DisableMetrics", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        if (!context.config.global.disableMetrics.get()) return

        context.event.subscribe(NetworkApiRequestEvent::class) { param ->
            val url = param.url
            if (url.contains("app-analytics") || url.endsWith("metrics")) {
                param.canceled = true
            }
        }

        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (event.uri.startsWith("/snap.security.IntegritySyncService/")) {
                event.canceled = true
            }
            if (event.uri.startsWith("/snapchat.cdp.cof.CircumstancesService/")) {
                if (ProtoReader(event.buffer).getVarInt(21) == 1L) return@subscribe
                event.canceled = true
            }
        }
    }
}
