package me.rhunk.snapenhance;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedLoader implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam packageParam) throws Throwable {
        if (!packageParam.packageName.equals(Constants.SNAPCHAT_PACKAGE_NAME)) return;
        new SnapEnhance();
    }
}
