package me.rhunk.snapenhance.bridge.common.impl.locale

import android.os.Bundle
import me.rhunk.snapenhance.bridge.common.BridgeMessage
import me.rhunk.snapenhance.data.LocalePair

class LocaleResult(
    private var locales: Array<String>? = null,
    private var localContentArray: Array<String>? = null
) : BridgeMessage(){

    fun getLocales(): List<LocalePair> {
        val locales = locales ?: return emptyList()
        val localContentArray = localContentArray ?: return emptyList()
        return locales.mapIndexed { index, locale ->
            LocalePair(locale, localContentArray[index])
        }.asReversed()
    }


    override fun write(bundle: Bundle) {
        bundle.putStringArray("locales", locales)
        bundle.putSerializable("localContentArray", localContentArray)
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    override fun read(bundle: Bundle) {
        locales = bundle.getStringArray("locales")
        localContentArray = bundle.getSerializable("localContentArray") as? Array<String>
    }
}