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
import com.feiyu.stepbystepmod.R
import com.feiyu.stepbystepmod.hook.MemoryModifier
import com.feiyu.stepbystepmod.util.LogManager
import com.feiyu.stepbystepmod.util.ThemeManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import de.robv.android.xposed.XposedBridge

class FloatingWindowManager private() : LogManager.LogListener, ThemeManager.ThemeListener {

    companion object {
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
        isInitialized = true
        XposedBridge.log("[StepByStepMod] FloatingWindowManager.init 完成")
    }

    fun show() {
        if (!isInitialized || isShowing) {
            XposedBridge.log("[StepByStepMod] FloatingWindowManager.show 跳过: initialized=$isInitialized, showing=$isShowing")
            return
        }

        handler.post {
            try {
                ThemeManager.init(appContext!!)
                ThemeManager.addListener(this)
                LogManager.addListener(this)

                createFloatView()
                isShowing = true
                XposedBridge.log("[StepByStepMod] 悬浮窗已显示")

                Toast.makeText(appContext, "StepByStep Mod 已激活", Toast.LENGTH_LONG).show()
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

    private fun createFloatView() {
        val ctx = appContext ?: return
        floatView = LayoutInflater.from(ctx).inflate(R.layout.layout_float_window, null)
        floatIcon = floatView?.findViewById(R.id.float_icon)

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
        val ctx = appContext ?: return
        mainView = LayoutInflater.from(ctx).inflate(R.layout.layout_main_panel, null)

        mainContainer = mainView?.findViewById(R.id.main_container)
        titleBar = mainView?.findViewById(R.id.title_bar)
        closeBtn = mainView?.findViewById(R.id.btn_close)
        minimizeBtn = mainView?.findViewById(R.id.btn_minimize)
        themeBtn = mainView?.findViewById(R.id.btn_theme)

        islandCard = mainView?.findViewById(R.id.card_island_mode)
        mainLevelCard = mainView?.findViewById(R.id.card_main_level)
        tribeBossCard = mainView?.findViewById(R.id.card_tribe_boss)
        logCard = mainView?.findViewById(R.id.card_logs)

        switchIslandRefresh = mainView?.findViewById(R.id.switch_island_refresh)
        switchIslandSkill = mainView?.findViewById(R.id.switch_island_skill)
        switchMainLevelRefresh = mainView?.findViewById(R.id.switch_main_level_refresh)
        switchMainLevelSkill = mainView?.findViewById(R.id.switch_main_level_skill)

        btnStartModify = mainView?.findViewById(R.id.btn_start_modify)
        btnCopyLogs = mainView?.findViewById(R.id.btn_copy_logs)
        btnClearLogs = mainView?.findViewById(R.id.btn_clear_logs)

        logRecyclerView = mainView?.findViewById(R.id.log_recycler_view)
        logAdapter = LogAdapter()
        logRecyclerView?.layoutManager = LinearLayoutManager(ctx)
        logRecyclerView?.adapter = logAdapter

        themeColorContainer = mainView?.findViewById(R.id.theme_color_container)

        setupListeners()
        setupThemeColors()
        applyTheme()
    }

    private fun setupThemeColors() {
        val ctx = appContext ?: return
        val colors = ThemeManager.ThemeColor.values()
        themeColorContainer?.removeAllViews()

        for (color in colors) {
            val dot = View(ctx)
            val size = dpToPx(28)
            val params = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(8)
            }
            dot.layoutParams = params
            dot.setBackgroundResource(R.drawable.bg_theme_dot)

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
        val ctx = appContext ?: return dp
        return (dp * ctx.resources.displayMetrics.density).toInt()
    }

    private fun setupListeners() {
        val ctx = appContext ?: return

        closeBtn?.setOnClickListener { hideMainView() }
        minimizeBtn?.setOnClickListener { hideMainView() }

        themeBtn?.setOnClickListener {
            val container = mainView?.findViewById<LinearLayout>(R.id.theme_color_container)
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
        val ctx = appContext ?: return
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

        mainView?.findViewById<MaterialTextView>(R.id.tv_title)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(R.id.tv_island_title)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(R.id.tv_main_level_title)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(R.id.tv_tribe_boss_title)?.setTextColor(mutedColor)
        mainView?.findViewById<MaterialTextView>(R.id.tv_logs_title)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(R.id.tv_island_refresh_label)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(R.id.tv_island_skill_label)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(R.id.tv_main_level_refresh_label)?.setTextColor(textColor)
        mainView?.findViewById<MaterialTextView>(R.id.tv_main_level_skill_label)?.setTextColor(textColor)

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
        val ctx = appContext ?: return
        if (!MemoryModifier.getInstance().isReady()) {
            Toast.makeText(ctx, "模块未初始化", Toast.LENGTH_SHORT).show()
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
        val ctx = appContext ?: return

        if (mainView == null) {
            createMainView()
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
            val tvLog: MaterialTextView = itemView.findViewById(R.id.tv_log_item)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(appContext)
                .inflate(R.layout.item_log, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val logs = LogManager.getAllLogs()
            if (position >= logs.size) return
            val log = logs[position]
            holder.tvLog.text = log.toFormattedString()
            holder.tvLog.setTextColor(log.level.color.toInt())
        }

        override fun getItemCount(): Int = LogManager.getLogCount()
    }
}
