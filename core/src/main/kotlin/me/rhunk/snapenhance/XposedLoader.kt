package me.rhunk.snapenhance

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage {
    override fun handleLoadPackage(p0: XC_LoadPackage.LoadPackageParam) {
        if (p0.packageName != Constants.SNAPCHAT_PACKAGE_NAME) return
        SnapEnhance()
    }
}