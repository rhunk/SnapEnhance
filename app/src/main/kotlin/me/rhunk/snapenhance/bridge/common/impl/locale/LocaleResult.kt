package me.rhunk.snapenhance.bridge.common.impl.locale

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage

class LocaleResult(
    var locale: String? = null,
    var content: ByteArray? = null
) : BridgeMessage(){
    override fun write(bundle: Bundle) {
        bundle.putString("locale", locale)
        bundle.putByteArray("content", content)
    }

    override fun read(bundle: Bundle) {
        locale = bundle.getString("locale")
        content = bundle.getByteArray("content")
    }
}