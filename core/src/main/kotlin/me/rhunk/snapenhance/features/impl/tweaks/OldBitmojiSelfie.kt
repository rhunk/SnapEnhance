package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.core.eventbus.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.core.util.snap.BitmojiSelfie
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams

class OldBitmojiSelfie : Feature("OldBitmojiSelfie", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val urlPrefixes = arrayOf("https://images.bitmoji.com/3d/render/", "https://cf-st.sc-cdn.net/3d/render/")
        val state by context.config.userInterface.ddBitmojiSelfie

        context.event.subscribe(NetworkApiRequestEvent::class, { state }) { event ->
            if (urlPrefixes.firstOrNull { event.url.startsWith(it) } == null) return@subscribe
            val bitmojiURI = event.url.substringAfterLast("/")
            event.url =
                BitmojiSelfie.BitmojiSelfieType.STANDARD.prefixUrl +
                bitmojiURI +
                (bitmojiURI.takeIf { !it.contains("?") }?.let { "?" } ?: "&") + "transparent=1"
        }
    }
}