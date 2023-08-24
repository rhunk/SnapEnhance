package me.rhunk.snapenhance

import android.content.Context
import java.lang.ref.WeakReference

object SharedContextHolder {
    private lateinit var _remoteSideContext: WeakReference<RemoteSideContext>

    fun remote(context: Context): RemoteSideContext {
        if (!::_remoteSideContext.isInitialized || _remoteSideContext.get() == null) {
            _remoteSideContext = WeakReference(RemoteSideContext(context))
            _remoteSideContext.get()?.reload()
        }

        return _remoteSideContext.get()!!
    }
}