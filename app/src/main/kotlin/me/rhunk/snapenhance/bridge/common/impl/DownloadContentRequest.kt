package me.rhunk.snapenhance.bridge.common.impl

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage

class DownloadContentRequest(
    var url: String? = null,
    var path: String? = null
) : BridgeMessage() {

    override fun write(bundle: Bundle) {
        bundle.putString("url", url)
        bundle.putString("path", path)
    }

    override fun read(bundle: Bundle) {
        url = bundle.getString("url")
        path = bundle.getString("path")
    }
}
