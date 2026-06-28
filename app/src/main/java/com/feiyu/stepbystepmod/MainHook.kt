package com.feiyu.stepbystepmod

import android.content.Intent
import android.os.Build
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
        const val TARGET_PACKAGE = "com.feiyu.stepbystepapp"
        @Volatile var instance: MainHook? = null
            private set
    }

    private var classLoader: ClassLoader? = null
    private var floatWindowStarted = false
    private var appContext: android.content.Context? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        instance = this
        classLoader = lpparam.classLoader

        XposedBridge.log("[StepByStepMod] handleLoadPackage: ${lpparam.packageName}")

        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            lpparam.classLoader,
            "attach",
            android.content.Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ctx = param.args[0] as android.content.Context
                    if (ctx.packageName != TARGET_PACKAGE) return

                    appContext = ctx
                    val pid = android.os.Process.myPid()

                    XposedBridge.log("[StepByStepMod] Application.attach, pid=$pid")

                    com.feiyu.stepbystepmod.hook.MemoryModifier.getInstance().init(pid, ctx)

                    showToast(ctx, "StepByStep Mod 已激活")

                    Handler(Looper.getMainLooper()).postDelayed({
                        startFloatWindow(ctx)
                    }, 2000)
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as android.app.Activity
                    if (activity.packageName != TARGET_PACKAGE) return
                    if (floatWindowStarted) return

                    Handler(Looper.getMainLooper()).postDelayed({
                        startFloatWindow(activity)
                    }, 1500)
                }
            }
        )
    }

    private fun showToast(ctx: android.content.Context, text: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
            }
        } catch (_: Throwable) {}
    }

    private fun startFloatWindow(ctx: android.content.Context) {
        if (floatWindowStarted) return
        floatWindowStarted = true

        try {
            val intent = Intent(ctx, com.feiyu.stepbystepmod.ui.FloatWindowService::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            XposedBridge.log("[StepByStepMod] 悬浮窗服务已启动")
        } catch (e: Throwable) {
            XposedBridge.log("[StepByStepMod] 启动悬浮窗失败: ${e.message}")
            floatWindowStarted = false
        }
    }
}
