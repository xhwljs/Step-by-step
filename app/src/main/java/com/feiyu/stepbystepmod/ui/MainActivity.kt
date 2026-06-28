package com.feiyu.stepbystepmod.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.feiyu.stepbystepmod.R
import com.feiyu.stepbystepmod.util.LogManager
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private var btnGrantPermission: MaterialButton? = null
    private var btnOpenSettings: MaterialButton? = null
    private var btnStartService: MaterialButton? = null

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkOverlayPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            initViews()
            setupListeners()
            checkOverlayPermission()
            LogManager.info("主界面已启动")
        } catch (e: Exception) {
            LogManager.error("主界面初始化失败: ${e.message}")
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews() {
        btnGrantPermission = findViewById(R.id.btn_grant_permission)
        btnOpenSettings = findViewById(R.id.btn_open_settings)
        btnStartService = findViewById(R.id.btn_start_service)

        if (btnGrantPermission == null || btnOpenSettings == null || btnStartService == null) {
            throw RuntimeException("布局视图初始化失败，请检查布局文件")
        }
    }

    private fun setupListeners() {
        btnGrantPermission?.setOnClickListener {
            requestOverlayPermission()
        }

        btnOpenSettings?.setOnClickListener {
            openAppSettings()
        }

        btnStartService?.setOnClickListener {
            startFloatService()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = Settings.canDrawOverlays(this)
            btnGrantPermission?.isEnabled = !hasPermission
            btnStartService?.isEnabled = hasPermission

            if (hasPermission) {
                LogManager.success("悬浮窗权限已授权")
            } else {
                LogManager.warning("悬浮窗权限未授权")
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    private fun startFloatService() {
        try {
            FloatingWindowManager.getInstance().init(this)
            FloatingWindowManager.getInstance().show()
            Toast.makeText(this, R.string.msg_module_activated, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
