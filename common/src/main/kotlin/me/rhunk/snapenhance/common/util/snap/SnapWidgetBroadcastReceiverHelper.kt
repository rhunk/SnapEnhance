package me.rhunk.snapenhance.common.util.snap

import android.content.Intent
import me.rhunk.snapenhance.common.Constants

object SnapWidgetBroadcastReceiverHelper {
    private const val ACTION_WIDGET_UPDATE = "com.snap.android.WIDGET_APP_START_UPDATE_ACTION"
    const val CLASS_NAME = "com.snap.widgets.core.BestFriendsWidgetProvider"

    fun create(targetAction: String, callback: Intent.() -> Unit): Intent {
        with(Intent()) {
            callback(this)
            action = ACTION_WIDGET_UPDATE
            putExtra(":)", true)
            putExtra("action", targetAction)
            setClassName(Constants.SNAPCHAT_PACKAGE_NAME, CLASS_NAME)
            return this
        }
    }

    fun isIncomingIntentValid(intent: Intent): Boolean {
        return intent.action == ACTION_WIDGET_UPDATE && intent.getBooleanExtra(":)", false)
    }
}