package me.rhunk.snapenhance.features.impl.privacy

import me.rhunk.snapenhance.core.eventbus.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class DisableMetrics : Feature("DisableMetrics", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val disableMetrics by context.config.global.disableMetrics

        Hooker.hook(context.classCache.unifiedGrpcService, "unaryCall", HookStage.BEFORE,
            {  disableMetrics }) { param ->
            val url: String = param.arg(0)
            if (url.endsWith("snapchat.valis.Valis/SendClientUpdate") ||
                url.endsWith("targetingQuery")
            ) {
                param.setResult(null)
            }
        }

        context.event.subscribe(NetworkApiRequestEvent::class, { disableMetrics }) { param ->
            val url = param.url
            if (url.contains("app-analytics") || url.endsWith("v1/metrics")) {
                param.canceled = true
            }
        }
    }
}