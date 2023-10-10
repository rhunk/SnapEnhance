package me.rhunk.snapenhance.core

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.rhunk.snapenhance.common.Constants

class XposedLoader : IXposedHookLoadPackage {
    override fun handleLoadPackage(p0: XC_LoadPackage.LoadPackageParam) {
        if (p0.packageName != Constants.SNAPCHAT_PACKAGE_NAME) return
        SnapEnhance()
    }
}