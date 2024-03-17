package me.rhunk.snapenhance.core.event.events.impl

import android.app.Activity
import android.content.Intent
import me.rhunk.snapenhance.core.event.events.AbstractHookEvent

class ActivityResultEvent(
    val activity: Activity,
    val requestCode: Int,
    val resultCode: Int,
    val intent: Intent
): AbstractHookEvent()