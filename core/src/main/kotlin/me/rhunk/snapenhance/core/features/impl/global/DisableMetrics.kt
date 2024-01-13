package me.rhunk.snapenhance.core.features.impl.global

import me.rhunk.snapenhance.core.event.events.impl.NetworkApiRequestEvent
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
    }
}
