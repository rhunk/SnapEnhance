package me.rhunk.snapenhance.features.impl.privacy

import de.robv.android.xposed.XposedHelpers
import me.rhunk.snapenhance.Logger.debug
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookAdapter
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import java.nio.charset.StandardCharsets
import java.util.*

class DisableMetrics : Feature("DisableMetrics", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val disableMetricsFilter: (HookAdapter) -> Boolean = {
            context.config.bool(ConfigProperty.DISABLE_METRICS)
        }

        Hooker.hook(context.classCache.unifiedGrpcService, "unaryCall", HookStage.BEFORE, disableMetricsFilter) { param ->
            val url: String = param.arg(0)
            if (url.endsWith("snapchat.valis.Valis/SendClientUpdate") ||
                url.endsWith("targetingQuery")
            ) {
                param.setResult(null)
            }
        }

        Hooker.hook(context.classCache.networkApi, "submit", HookStage.BEFORE, disableMetricsFilter) { param ->
            val httpRequest: Any = param.arg(0)
            val url = XposedHelpers.getObjectField(httpRequest, "mUrl").toString()
            if (url.contains("resolve?co=")) {
                val index = url.indexOf("co=")
                val end = url.lastIndexOf("&")
                val co = url.substring(index + 3, end)
                val decoded = Base64.getDecoder().decode(co.toByteArray(StandardCharsets.UTF_8))
                debug("decoded : " + decoded.toString(Charsets.UTF_8))
                debug("content: $co")
            }
            if (url.contains("app-analytics") || url.endsWith("v1/metrics")) {
                param.setResult(null)
            }
        }
    }
}