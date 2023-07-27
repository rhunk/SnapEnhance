package me.rhunk.snapenhance.util

import android.content.Intent

typealias ActivityResultCallback = (requestCode: Int, resultCode: Int, data: Intent?) -> Unit