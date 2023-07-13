package me.rhunk.snapenhance.bridge

import android.app.Activity
import android.os.Bundle

class ForceStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}