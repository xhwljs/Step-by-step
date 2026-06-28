package com.feiyu.stepbystepmod

import android.app.Activity
import android.app.AndroidAppHelper
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.feiyu.stepbystepmod.hook.MemoryModifier
import com.feiyu.stepbystepmod.ui.FloatWindowService
import com.feiyu.stepbystepmod.util.LogManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object MainHook : IXposedHookLoadPackage {

    const val TARGET_PACKAGE = "com.feiyu.stepbystepapp"

    @Volatile
    private var classLoader: ClassLoader? = null
    @Volatile
    private var moduleHooked = false
    @Volatile
    private var floatWindowStarted = false

    fun getClassLoader(): ClassLoader? = classLoader

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        // 防止重复hook
        if (moduleHooked) return
        moduleHooked = true

        classLoader = lpparam.classLoader
        XposedBridge.log("[StepByStepMod] 模块已注入目标应用: ${lpparam.packageName}")

        LogManager.info("[$TARGET_PACKAGE] ✓ 模块Hook成功，已注入")

        // 在目标应用主线程显示Toast提示
        try {
            val appClass = lpparam.classLoader.loadClass("android.app.ActivityThread")
            val currentActivityThread = XposedHelpers.callStaticMethod(appClass, "currentActivityThread")
            if (currentActivityThread != null) {
                val handler = XposedHelpers.getObjectField(currentActivityThread, "mH") as android.os.Handler
                handler.post {
                    try {
                        val ctx = XposedHelpers.callMethod(currentActivityThread, "getSystemContext") as android.app.ContextImpl
                        Toast.makeText(ctx, "StepByStep Mod 已激活，点击悬浮球打开控制面板", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        // Hook Application.attach 来初始化内存修改器
        XposedHelpers.findAndHookMethod(
            android.app.Application::class.java,
            "attach",
            android.content.Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as android.app.Application
                    val context = param.args[0] as android.content.Context
                    val pid = android.os.Process.myPid()

                    // 只在目标应用进程初始化
                    if (context.packageName != TARGET_PACKAGE) return

                    // 初始化内存修改器
                    MemoryModifier.getInstance().init(pid, context)
                    LogManager.success("内存修改器已初始化, PID=$pid")

                    // 延迟启动悬浮窗，等待应用完全启动
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startFloatWindowService(app)
                    }, 1500)
                }
            }
        )

        // Hook MainActivity.onCreate 备用启动（如果attach没触发）
        try {
            XposedHelpers.findAndHookMethod(
                "$TARGET_PACKAGE.MainActivity",
                lpparam.classLoader,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (floatWindowStarted) return
                        val activity = param.thisObject as Activity
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            startFloatWindowService(activity)
                        }, 1000)
                    }
                }
            )
        } catch (_: Throwable) {}

        LogManager.info("[$TARGET_PACKAGE] 开始监控目标应用")
    }

    private fun startFloatWindowService(context: android.content.Context) {
        if (floatWindowStarted) return
        floatWindowStarted = true

        try {
            val intent = Intent(context, FloatWindowService::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_FROM_BACKGROUND

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            LogManager.success("悬浮窗服务已启动")
            XposedBridge.log("[StepByStepMod] 悬浮窗服务已启动")
        } catch (e: Throwable) {
            LogManager.error("启动悬浮窗失败: ${e.message}")
            XposedBridge.log("[StepByStepMod] 启动悬浮窗失败: ${e.message}")
        }
    }
}
