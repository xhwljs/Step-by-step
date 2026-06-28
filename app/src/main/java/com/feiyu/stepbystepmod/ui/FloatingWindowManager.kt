package com.feiyu.stepbystepmod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.feiyu.stepbystepmod.hook.MemoryModifier
import com.feiyu.stepbystepmod.util.LogManager
import com.feiyu.stepbystepmod.util.ThemeManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import de.robv.android.xposed.XposedBridge

class FloatingWindowManager private constructor() : LogManager.LogListener, ThemeManager.ThemeListener {

    companion object {
        const val MODULE_PACKAGE = "com.feiyu.stepbystepmod"

        @Volatile
        private var instance: FloatingWindowManager? = null

        fun getInstance(): FloatingWindowManager {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = FloatingWindowManager()
                    }
                }
            }
            return instance!!
        }
    }

    private var appContext: Context? = null
    private var moduleContext: Context? = null
    private var layoutInflater: LayoutInflater? = null
    private var windowManager: WindowManager? = null
    private var isInitialized = false
    private var isShowing = false

    private var floatView: View? = null
    private var mainView: View? = null

    private var floatIcon: ImageView? = null
    private var mainContainer: FrameLayout? = null
    private var titleBar: LinearLayout? = null
    private var closeBtn: ImageView? = null
    private var minimizeBtn: ImageView? = null
    private var themeBtn: ImageView? = null

    private var islandCard: MaterialCardView? = null
    private var mainLevelCard: MaterialCardView? = null
    private var tribeBossCard: MaterialCardView? = null
    private var logCard: MaterialCardView? = null

    private var switchIslandRefresh: SwitchMaterial? = null
    private var switchIslandSkill: SwitchMaterial? = null
    private var switchMainLevelRefresh: SwitchMaterial? = null
    private var switchMainLevelSkill: SwitchMaterial? = null

    private var btnStartModify: MaterialButton? = null
    private var btnCopyLogs: MaterialButton? = null
    private var btnClearLogs: MaterialButton? = null

    private var logRecyclerView: RecyclerView? = null
    private var logAdapter: LogAdapter? = null

    private var themeColorContainer: LinearLayout? = null

    private var isMainViewVisible = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var floatParams: WindowManager.LayoutParams? = null

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        windowManager = appContext?.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 获取模块自己的Context，用于加载布局和资源
        try {
            val rawModuleContext = appContext?.createPackageContext(
                MODULE_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
            )
            // 给模块Context设置主题 - Material组件必须有正确的主题
            val themeResId = rawModuleContext?.resources?.getIdentifier(
                "Theme_StepByStepMod", "style", MODULE_PACKAGE
            ) ?: 0
            moduleContext = if (themeResId != 0 && rawModuleContext != null) {
                android.view.ContextThemeWrapper(rawModuleContext, themeResId)
            } else {
                rawModuleContext
            }
            layoutInflater = LayoutInflater.from(moduleContext)
            XposedBridge.log("[StepByStepMod] 模块Context获取成功, themeResId=0x${themeResId.toString(16)}")
        } catch (e: Throwable) {
            XposedBridge.log("[StepByStepMod] 获取模块Context失败: ${e.message}")
            layoutInflater = LayoutInflater.from(appContext)
        }

        isInitialized = true
        XposedBridge.log("[StepByStepMod] FloatingWindowManager.init 完成")
    }

    fun show() {
        if (!isInitialized || isShowing) return

        handler.post {
            try {
                val ctx = moduleContext ?: appContext ?: return@post
                ThemeManager.init(ctx)
                ThemeManager.addListener(this)
                LogManager.addListener(this)

                createFloatView()
                isShowing = true
                XposedBridge.log("[StepByStepMod] 悬浮窗已显示")

                Toast.makeText(ctx, "⚡ StepByStep Mod 已激活", Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {
                XposedBridge.log("[StepByStepMod] 显示悬浮窗失败: ${e.message}")
            }
        }
    }

    fun hide() {
        if (!isShowing) return
        handler.post {
            try {
                floatView?.let { windowManager?.removeView(it) }
                mainView?.let { windowManager?.removeView(it) }
                floatView = null
                mainView = null
                LogManager.removeListener(this)
                ThemeManager.removeListener(this)
                isShowing = false
                isMainViewVisible = false
            } catch (_: Exception) {}
        }
    }

    private fun getResId(name: String, type: String): Int {
        val ctx = moduleContext ?: return 0
        return ctx.resources.getIdentifier(name, type, MODULE_PACKAGE)
    }

    private fun createFloatView() {
        val inflater = layoutInflater ?: return
        val layoutId = getResId("layout_float_window", "layout")
        if (layoutId == 0) {
            XposedBridge.log("[StepByStepMod] 找不到布局资源: layout_float_window")
            return
        }

        floatView = inflater.inflate(layoutId, null)
        val iconId = getResId("float_icon", "id")
        floatIcon = floatView?.findViewById(iconId)

        floatParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        floatIcon?.setOnClickListener {
            toggleMainView()
        }

        floatIcon?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatParams!!.x
                    initialY = floatParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    floatParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatView, floatParams)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatView, floatParams)
        applyFloatTheme()
    }

    private fun getWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun createMainView() {
        val inflater = layoutInflater ?: return
        val layoutId = getResId("layout_main_panel", "layout")
        if (layoutId == 0) {
            XposedBridge.log("[StepByStepMod] 找不到布局资源: layout_main_panel")
            return
        }

        mainView = inflater.inflate(layoutId, null)

        mainContainer = mainView?.findViewById(getResId("main_container", "id"))
        titleBar = mainView?.findViewById(getResId("title_bar", "id"))
        closeBtn = mainView?.findViewById(getResId("btn_close", "id"))
        minimizeBtn = mainView?.findViewById(getResId("btn_minimize", "id"))
        themeBtn = mainView?.findViewById(getResId("btn_theme", "id"))

        islandCard = mainView?.findViewById(getResId("card_island_mode", "id"))
        mainLevelCard = mainView?.findViewById(getResId("card_main_level", "id"))
        tribeBossCard = mainView?.findViewById(getResId("card_tribe_boss", "id"))
        logCard = mainView?.findViewById(getResId("card_logs", "id"))

        switchIslandRefresh = mainView?.findViewById(getResId("switch_island_refresh", "id"))
        switchIslandSkill = mainView?.findViewById(getResId("switch_island_skill", "id"))
        switchMainLevelRefresh = mainView?.findViewById(getResId("switch_main_level_refresh", "id"))
        switchMainLevelSkill = mainView?.findViewById(getResId("switch_main_level_skill", "id"))

        btnStartModify = mainView?.findViewById(getResId("btn_start_modify", "id"))
        btnCopyLogs = mainView?.findViewById(getResId("btn_copy_logs", "id"))
        btnClearLogs = mainView?.findViewById(getResId("btn_clear_logs", "id"))

        logRecyclerView = mainView?.findViewById(getResId("log_recycler_view", "id"))
        logAdapter = LogAdapter()
        val ctx = moduleContext ?: appContext
        logRecyclerView?.layoutManager = ctx?.let { LinearLayoutManager(it) }
        logRecyclerView?.adapter = logAdapter

        themeColorContainer = mainView?.findViewById(getResId("theme_color_container", "id"))

        setupListeners()
        setupThemeColors()
        applyTheme()
    }

    private fun setupThemeColors() {
        val ctx = moduleContext ?: appContext ?: return
        val colors = ThemeManager.ThemeColor.values()
        themeColorContainer?.removeAllViews()

        val dotBgId = getResId("bg_theme_dot", "drawable")

        for (color in colors) {
            val dot = View(ctx)
            val size = dpToPx(28)
            val params = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(8)
            }
            dot.layoutParams = params
            if (dotBgId != 0) {
                dot.setBackgroundResource(dotBgId)
            }

            try {
                val drawable = dot.background
                if (drawable is GradientDrawable) {
                    drawable.setColor(color.getPrimaryInt())
                }
            } catch (_: Exception) {}

            dot.setOnClickListener {
                ThemeManager.setTheme(color)
                Toast.makeText(ctx, "主题已切换", Toast.LENGTH_SHORT).show()
            }

            themeColorContainer?.addView(dot)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val ctx = moduleContext ?: appContext ?: return dp
        return (dp * ctx.resources.displayMetrics.density).toInt()
    }

    private fun setupListeners() {
        val ctx = moduleContext ?: appContext ?: return

        closeBtn?.setOnClickListener { hideMainView() }
        minimizeBtn?.setOnClickListener { hideMainView() }

        themeBtn?.setOnClickListener {
            val container = themeColorContainer
            container?.visibility = if (container?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnStartModify?.setOnClickListener {
            startSignatureScan()
        }

        switchIslandRefresh?.setOnCheckedChangeListener { _, isChecked ->
            if (switchIslandRefresh?.isPressed != true) return@setOnCheckedChangeListener
            MemoryModifier.getInstance().setIslandInfiniteRefresh(isChecked)
        }

        switchIslandSkill?.setOnCheckedChangeListener { _, isChecked ->
            if (switchIslandSkill?.isPressed != true) return@setOnCheckedChangeListener
            MemoryModifier.getInstance().setIslandLevelLock(isChecked)
        }

        switchMainLevelRefresh?.setOnCheckedChangeListener { _, isChecked ->
            if (switchMainLevelRefresh?.isPressed != true) return@setOnCheckedChangeListener
            MemoryModifier.getInstance().setMainLevelInfiniteRefresh(isChecked)
        }

        switchMainLevelSkill?.setOnCheckedChangeListener { _, isChecked ->
            if (switchMainLevelSkill?.isPressed != true) return@setOnCheckedChangeListener
            MemoryModifier.getInstance().setMainLevelInfiniteSkill(isChecked)
        }

        btnCopyLogs?.setOnClickListener {
            val logs = LogManager.getFormattedLogs()
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Logs", logs)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, "日志已复制", Toast.LENGTH_SHORT).show()
        }

        btnClearLogs?.setOnClickListener {
            LogManager.clear()
            logAdapter?.notifyDataSetChanged()
            Toast.makeText(ctx, "日志已清空", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme() {
        val bgColor = ThemeManager.getBackgroundColor()
        val cardBg = ThemeManager.getCardBackgroundColor()
        val textColor = ThemeManager.getForegroundColor()
        val mutedColor = ThemeManager.getMutedColor()
        val primaryColor = ThemeManager.getPrimaryColor()

        mainContainer?.setBackgroundColor(bgColor)
        titleBar?.setBackgroundColor(cardBg)

        listOf(islandCard, mainLevelCard, tribeBossCard, logCard).forEach { card ->
            card?.setCardBackgroundColor(cardBg)
            try {
                card?.strokeColor = ThemeManager.getBorderColor()
            } catch (_: Exception) {}
        }

        val tvTitleId = getResId("tv_title", "id")
        val tvIslandTitleId = getResId("tv_island_title", "id")
        val tvMainLevelTitleId = getResId("tv_main_level_title", "id")
        val tvTribeBossTitleId = getResId("tv_tribe_boss_title", "id")
        val tvLogsTitleId = getResId("tv_logs_title", "id")
        val tvIslandRefreshLabelId = getResId("tv_island_refresh_label", "id")
        val tvIslandSkillLabelId = getResId("tv_island_skill_label", "id")
        val tvMainLevelRefreshLabelId = getResId("tv_main_level_refresh_label", "id")
        val tvMainLevelSkillLabelId = getResId("tv_main_level_skill_label", "id")

        mainView?.findViewById<MaterialTextView>(tvTitleId)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(tvIslandTitleId)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(tvMainLevelTitleId)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(tvTribeBossTitleId)?.setTextColor(mutedColor)
        mainView?.findViewById<MaterialTextView>(tvLogsTitleId)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(tvIslandRefreshLabelId)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(tvIslandSkillLabelId)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(tvMainLevelRefreshLabelId)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(tvMainLevelSkillLabelId)?.setTextColor(textColor)

        themeBtn?.setColorFilter(textColor)
        closeBtn?.setColorFilter(textColor)
        minimizeBtn?.setColorFilter(textColor)

        btnStartModify?.setBackgroundColor(primaryColor)
        applyFloatTheme()
    }

    private fun applyFloatTheme() {
        val primaryColor = ThemeManager.getPrimaryColor()
        val icon = floatIcon ?: return
        try {
            val bg = icon.background
            if (bg is GradientDrawable) {
                bg.setColor(primaryColor)
            }
        } catch (_: Exception) {}
    }

    private fun startSignatureScan() {
        val ctx = moduleContext ?: appContext
        if (!MemoryModifier.getInstance().isReady()) {
            if (ctx != null) Toast.makeText(ctx, "模块未初始化", Toast.LENGTH_SHORT).show()
            return
        }

        btnStartModify?.isEnabled = false
        btnStartModify?.text = "扫描中..."

        MemoryModifier.getInstance().scanAll { islandSuccess, mainSuccess ->
            handler.post {
                btnStartModify?.isEnabled = true
                if (islandSuccess || mainSuccess) {
                    btnStartModify?.text = "扫描成功"
                    LogManager.success("特征码扫描完成")
                } else {
                    btnStartModify?.text = "扫描失败"
                    LogManager.error("特征码扫描失败")
                }
            }
        }
    }

    private fun toggleMainView() {
        if (isMainViewVisible) {
            hideMainView()
        } else {
            showMainView()
        }
    }

    private fun showMainView() {
        try {
            if (mainView == null) {
                createMainView()
            }
            if (mainView == null) {
                XposedBridge.log("[StepByStepMod] showMainView: mainView为null，加载失败")
                return
            }

            if (mainView?.parent == null) {
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                windowManager?.addView(mainView, params)
            }
            mainView?.visibility = View.VISIBLE
            isMainViewVisible = true
            mainView?.alpha = 0f
            mainView?.animate()?.alpha(1f)?.setDuration(200)?.start()
        } catch (e: Throwable) {
            XposedBridge.log("[StepByStepMod] showMainView 异常: ${e.message}")
        }
    }

    private fun hideMainView() {
        mainView?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            mainView?.let {
                try { windowManager?.removeView(it) } catch (_: Exception) {}
            }
            mainView = null
        }
        isMainViewVisible = false
    }

    override fun onLogAdded(entry: LogManager.LogEntry) {
        handler.post {
            logAdapter?.notifyItemInserted(LogManager.getLogCount() - 1)
            if (isMainViewVisible && logRecyclerView?.isComputingLayout == false) {
                logRecyclerView?.smoothScrollToPosition((logAdapter?.itemCount ?: 1) - 1)
            }
        }
    }

    override fun onThemeChanged(theme: ThemeManager.ThemeColor) {
        applyTheme()
    }

    inner class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
        inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvLog: MaterialTextView? = itemView.findViewById(getResId("tv_log_item", "id"))
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): LogViewHolder {
            val inflater = layoutInflater
            val itemLayoutId = getResId("item_log", "layout")
            val view = if (inflater != null && itemLayoutId != 0) {
                inflater.inflate(itemLayoutId, parent, false)
            } else {
                View(parent.context)
            }
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val logs = LogManager.getAllLogs()
            if (position >= logs.size) return
            val log = logs[position]
            holder.tvLog?.text = log.toFormattedString()
            holder.tvLog?.setTextColor(log.level.color.toInt())
        }

        override fun getItemCount(): Int = LogManager.getLogCount()
    }
}
