package me.rhunk.snapenhance.core.features.impl.ui

import android.net.Uri
import me.rhunk.snapenhance.common.util.snap.BitmojiSelfie
import me.rhunk.snapenhance.core.event.events.impl.NetworkApiRequestEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams

class OldBitmojiSelfie : Feature("OldBitmojiSelfie", loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        val urlPrefixes = arrayOf("https://images.bitmoji.com/3d/render/", "https://cf-st.sc-cdn.net/3d/render/")
        val oldBitmojiSelfie = context.config.userInterface.oldBitmojiSelfie.getNullable() ?: return

        context.event.subscribe(NetworkApiRequestEvent::class) { event ->
            if (urlPrefixes.firstOrNull { event.url.startsWith(it) } == null) return@subscribe
            event.url = event.url.replace("ua=1", "") // replace ua=1 with nothing for old 3d selfies/background

            // replace with old 2d selfies
            if (oldBitmojiSelfie == "2d" && event.url.contains("trim=circle")) {
                val bitmojiPath = event.url.substringAfterLast("/").substringBeforeLast("?")
                event.url = Uri.parse(BitmojiSelfie.BitmojiSelfieType.STANDARD.prefixUrl)
                    .buildUpon()
                    .appendPath(bitmojiPath)
                    .appendQueryParameter("transparent", "1")
                    .appendQueryParameter("trim", "circle")
                    .build()
                    .toString()
            }

            if (arrayOf("?", "&").any { event.url.endsWith(it) }) event.url = event.url.dropLast(1)
        }
    }
}