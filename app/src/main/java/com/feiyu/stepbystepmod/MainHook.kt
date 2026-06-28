package com.feiyu.stepbystepmod

import android.app.Application
import com.feiyu.stepbystepmod.hook.MemoryModifier
import com.feiyu.stepbystepmod.util.LogManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object MainHook {
    const val TARGET_PACKAGE = "com.feiyu.stepbystepapp"

    @Volatile
    private var classLoader: ClassLoader? = null

    fun getClassLoader(): ClassLoader? = classLoader

    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        classLoader = lpparam.classLoader
        LogManager.info("[$TARGET_PACKAGE] 模块已加载")

        // Hook Application.onCreate 来初始化
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as Application
                    val pid = android.os.Process.myPid()
                    MemoryModifier.getInstance().init(pid)
                    LogManager.success("模块已在目标应用中初始化, PID=$pid")
                }
            }
        )

        // Hook Activity 启动以启动悬浮窗
        try {
            XposedHelpers.findAndHookMethod(
                "com.feiyu.stepbystepapp.MainActivity",
                lpparam.classLoader,
                "onCreate",
                android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as android.app.Activity
                        LogManager.info("MainActivity 已启动")

                        // 启动悬浮窗
                        val intent = android.content.Intent()
                        intent.setClassName(
                            "com.feiyu.stepbystepmod",
                            "com.feiyu.stepbystepmod.ui.FloatWindowService"
                        )
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                activity.startForegroundService(intent)
                            } else {
                                activity.startService(intent)
                            }
                            LogManager.success("悬浮窗服务已启动")
                        } catch (e: Exception) {
                            LogManager.error("启动悬浮窗服务失败: ${e.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            LogManager.error("Hook MainActivity 失败: ${e.message}")
        }
    }
}
