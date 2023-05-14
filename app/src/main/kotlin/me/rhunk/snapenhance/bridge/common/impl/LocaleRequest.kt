package me.rhunk.snapenhance.bridge.common.impl

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage

class LocaleRequest(
    var locale: String? = null
) : BridgeMessage() {

    override fun write(bundle: Bundle) {
        bundle.putString("locale", locale)
    }

    override fun read(bundle: Bundle) {
        locale = bundle.getString("locale")
    }
}