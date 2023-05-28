package me.rhunk.snapenhance.bridge.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import me.rhunk.snapenhance.Constants

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageManager.getLaunchIntentForPackage(Constants.SNAPCHAT_PACKAGE_NAME)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(this)
        }
        finish()
    }
}
