package com.feiyu.stepbystepmod.util

import android.content.Context
import android.content.SharedPreferences

object ThemeManager {

    enum class ThemeColor(val key: String, val primary: Long, val primaryDark: Long, val accent: Long) {
        PURPLE("purple", 0xFF6750A4, 0xFF381E72, 0xFF7C3AED),
        BLUE("blue", 0xFF0061A4, 0xFF003366, 0xFF0095FF),
        GREEN("green", 0xFF006B5A, 0xFF003D33, 0xFF00A896),
        RED("red", 0xFFBA1A1A, 0xFF601410, 0xFFE63946),
        ORANGE("orange", 0xFFFF6B00, 0xFFB34700, 0xFFFF8C42),
        PINK("pink", 0xFFAD1A6B, 0xFF620038, 0xFFEC4899);

        fun getPrimaryInt(): Int = primary.toInt()
        fun getPrimaryDarkInt(): Int = primaryDark.toInt()
        fun getAccentInt(): Int = accent.toInt()
    }

    private const val PREFS_NAME = "mod_theme"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_DARK_MODE = "dark_mode"

    private var prefs: SharedPreferences? = null
    private var currentTheme: ThemeColor = ThemeColor.PURPLE
    private var isDarkMode: Boolean = true

    private val listeners = mutableListOf<ThemeListener>()

    interface ThemeListener {
        fun onThemeChanged(theme: ThemeColor)
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedKey = prefs?.getString(KEY_THEME, ThemeColor.PURPLE.key)
        currentTheme = ThemeColor.values().find { it.key == savedKey } ?: ThemeColor.PURPLE
        isDarkMode = prefs?.getBoolean(KEY_DARK_MODE, true) ?: true
    }

    fun setTheme(theme: ThemeColor) {
        currentTheme = theme
        prefs?.edit()?.putString(KEY_THEME, theme.key)?.apply()
        listeners.forEach { it.onThemeChanged(theme) }
    }

    fun getCurrentTheme(): ThemeColor = currentTheme

    fun getPrimaryColor(): Int = currentTheme.getPrimaryInt()
    fun getPrimaryDarkColor(): Int = currentTheme.getPrimaryDarkInt()
    fun getAccentColor(): Int = currentTheme.getAccentInt()

    fun setDarkMode(enabled: Boolean) {
        isDarkMode = enabled
        prefs?.edit()?.putBoolean(KEY_DARK_MODE, enabled)?.apply()
    }

    fun isDark(): Boolean = isDarkMode

    // 背景色
    fun getBackgroundColor(): Int {
        return if (isDarkMode) 0xFF0F0F23.toInt() else 0xFFF8FAFC.toInt()
    }

    fun getCardBackgroundColor(): Int {
        return if (isDarkMode) 0xFF1A1A2E.toInt() else 0xFFFFFFFF.toInt()
    }

    fun getForegroundColor(): Int {
        return if (isDarkMode) 0xFFE2E8F0.toInt() else 0xFF1E293B.toInt()
    }

    fun getMutedColor(): Int {
        return if (isDarkMode) 0xFF27273B.toInt() else 0xFFE2E8F0.toInt()
    }

    fun getBorderColor(): Int {
        return if (isDarkMode) 0xFF4C1D95.toInt() else 0xFFC4B5FD.toInt()
    }

    fun addListener(listener: ThemeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ThemeListener) {
        listeners.remove(listener)
    }
}
