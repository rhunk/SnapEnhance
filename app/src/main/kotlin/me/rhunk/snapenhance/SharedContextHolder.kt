package me.rhunk.snapenhance

import android.content.Context

object SharedContextHolder {
    private lateinit var _remoteSideContext: RemoteSideContext

    fun remote(context: Context): RemoteSideContext {
        if (!::_remoteSideContext.isInitialized) {
            _remoteSideContext = RemoteSideContext(context)
        }

        if (_remoteSideContext.androidContext != context) {
            _remoteSideContext.androidContext = context
        }

        return _remoteSideContext
    }
}