package com.feiyu.stepbystepmod

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    companion object {
        const val TAG = "StepByStepMod"
        const val TARGET_PACKAGE = "com.feiyu.stepbystepapp"
        var classLoader: ClassLoader? = null
            private set
        var isModuleActivated = false
            private set
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookAllApplications(lpparam)

        if (lpparam.packageName != TARGET_PACKAGE) return

        classLoader = lpparam.classLoader
        isModuleActivated = true
    }

    private fun hookAllApplications(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as android.app.Application

                        if (lpparam.packageName == TARGET_PACKAGE) {
                            val pid = android.os.Process.myPid()

                            Thread {
                                try {
                                    com.feiyu.stepbystepmod.hook.MemoryModifier.getInstance().init(pid, app)
                                } catch (_: Throwable) {}

                                try {
                                    Thread.sleep(3000)
                                    Handler(Looper.getMainLooper()).post {
                                        try {
                                            com.feiyu.stepbystepmod.ui.FloatingWindowManager.getInstance().init(app)
                                            com.feiyu.stepbystepmod.ui.FloatingWindowManager.getInstance().show()
                                        } catch (_: Throwable) {}
                                    }
                                } catch (_: Throwable) {}
                            }.start()

                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    Toast.makeText(
                                        app,
                                        "⚡ StepByStep Mod 已激活",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (_: Throwable) {}
                            }, 2000)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("[$TAG] Hook Application.onCreate 失败: ${e.message}")
        }
    }
}
