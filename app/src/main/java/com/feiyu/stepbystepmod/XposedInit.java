package com.feiyu.stepbystepmod;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed Module Entry Point
 *
 * This class is loaded by LSPosed when the target app is launched.
 * The module is configured in AndroidManifest.xml with:
 * - xposedmodule=true
 * - xposedscope=[com.feiyu.stepbystepapp]
 * - xposedminversion=82
 */
public class XposedInit implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        MainHook.handleLoadPackage(lpparam);
    }
}
