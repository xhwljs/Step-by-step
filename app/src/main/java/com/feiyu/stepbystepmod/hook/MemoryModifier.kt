package com.feiyu.stepbystepmod.hook

import com.feiyu.stepbystepmod.util.LogManager
import com.feiyu.stepbystepmod.util.SignatureScanner
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MemoryModifier private constructor() {

    companion object {
        @Volatile
        private var instance: MemoryModifier? = null

        fun getInstance(): MemoryModifier {
            return instance ?: synchronized(this) {
                instance ?: MemoryModifier().also { instance = it }
            }
        }

        const val REFRESH_VALUE = 999
        const val SKILL_SELECT_VALUE = 99999
        const val LOCK_LEVEL = 1
        const val MAIN_SKILL_OFFSET = -0x12L
    }

    private var pid: Int = -1
    private var isInitialized = false
    private var context: android.content.Context? = null

    // 海岛模式
    @Volatile var islandBaseAddress: Long = 0
        private set
    @Volatile var islandMatchCount: Int = 0
    private var originalIslandRefreshRemaining = 2
    private var originalIslandRefreshTotal = 2
    private var originalIslandLevel = 1

    // 主线关卡
    @Volatile var mainLevelBaseAddress: Long = 0
        private set
    @Volatile var mainLevelMatchCount: Int = 0
    private var originalMainRefreshRemaining = 2
    private var originalMainRefreshTotal = 2
    private var originalMainLevel = 1
    private var originalMainSkillSelect = 2

    // 功能开关
    @Volatile var islandInfiniteRefresh = false
    @Volatile var islandLevelLock = false
    @Volatile var mainLevelInfiniteRefresh = false
    @Volatile var mainLevelInfiniteSkill = false

    // 修改状态
    @Volatile var isIslandRefreshModified = false
    @Volatile var isIslandLevelModified = false
    @Volatile var isMainRefreshModified = false
    @Volatile var isMainSkillModified = false

    // 锁定线程
    private val lockThreadRunning = AtomicBoolean(false)
    private var lockThread: Thread? = null
    private var lockLoopCount = 0L

    fun init(pid: Int, ctx: android.content.Context) {
        this.pid = pid
        this.context = ctx
        this.isInitialized = true
        LogManager.success("MemoryModifier 初始化完成, PID=$pid")
    }

    fun isReady(): Boolean = isInitialized && pid > 0

    // ===== 扫描功能 =====

    fun scanAll(onComplete: (islandSuccess: Boolean, mainSuccess: Boolean) -> Unit) {
        var islandDone = false
        var mainDone = false
        var islandOk = false
        var mainOk = false

        val checkComplete = {
            if (islandDone && mainDone) {
                onComplete(islandOk, mainOk)
            }
        }

        scanIslandMode { success ->
            islandOk = success
            islandDone = true
            checkComplete()
        }

        scanMainLevel { success ->
            mainOk = success
            mainDone = true
            checkComplete()
        }
    }

    fun scanIslandMode(onComplete: (Boolean) -> Unit) {
        if (!isReady()) {
            LogManager.error("未初始化，无法扫描海岛模式")
            onComplete(false)
            return
        }

        SignatureScanner.scanIslandMode(pid, object : SignatureScanner.ScanCallback {
            override fun onProgress(progress: Int) {}

            override fun onSuccess(baseAddress: Long, matchCount: Int) {
                islandBaseAddress = baseAddress
                islandMatchCount = matchCount
                backupIslandValues()
                onComplete(true)
            }

            override fun onError(message: String) {
                onComplete(false)
            }
        })
    }

    fun scanMainLevel(onComplete: (Boolean) -> Unit) {
        if (!isReady()) {
            LogManager.error("未初始化，无法扫描主线关卡")
            onComplete(false)
            return
        }

        SignatureScanner.scanMainLevel(pid, object : SignatureScanner.ScanCallback {
            override fun onProgress(progress: Int) {}

            override fun onSuccess(baseAddress: Long, matchCount: Int) {
                mainLevelBaseAddress = baseAddress
                mainLevelMatchCount = matchCount
                backupMainLevelValues()
                onComplete(true)
            }

            override fun onError(message: String) {
                onComplete(false)
            }
        })
    }

    // ===== 地址计算 =====

    private fun getIslandRefreshRemainingAddr(): Long =
        islandBaseAddress + SignatureScanner.IslandOffsets.REFRESH_REMAINING

    private fun getIslandRefreshTotalAddr(): Long =
        islandBaseAddress + SignatureScanner.IslandOffsets.REFRESH_TOTAL

    private fun getIslandLevelAddr(): Long =
        islandBaseAddress + SignatureScanner.IslandOffsets.LEVEL

    private fun getMainRefreshRemainingAddr(): Long =
        mainLevelBaseAddress + SignatureScanner.MainLevelOffsets.REFRESH_REMAINING

    private fun getMainRefreshTotalAddr(): Long =
        mainLevelBaseAddress + SignatureScanner.MainLevelOffsets.REFRESH_TOTAL

    private fun getMainLevelAddr(): Long =
        mainLevelBaseAddress + SignatureScanner.MainLevelOffsets.LEVEL

    private fun getMainSkillSelectAddr(): Long =
        getMainRefreshRemainingAddr() + MAIN_SKILL_OFFSET

    // ===== 备份原始值 =====

    private fun backupIslandValues() {
        if (islandBaseAddress <= 0) return
        originalIslandRefreshRemaining = SignatureScanner.readInt(pid, getIslandRefreshRemainingAddr())
        originalIslandRefreshTotal = SignatureScanner.readInt(pid, getIslandRefreshTotalAddr())
        originalIslandLevel = SignatureScanner.readInt(pid, getIslandLevelAddr())
        LogManager.info("[海岛模式] 已备份原始值 - 刷新剩余: $originalIslandRefreshRemaining, 刷新总数: $originalIslandRefreshTotal, 等级: $originalIslandLevel")
    }

    private fun backupMainLevelValues() {
        if (mainLevelBaseAddress <= 0) return
        originalMainRefreshRemaining = SignatureScanner.readInt(pid, getMainRefreshRemainingAddr())
        originalMainRefreshTotal = SignatureScanner.readInt(pid, getMainRefreshTotalAddr())
        originalMainLevel = SignatureScanner.readInt(pid, getMainLevelAddr())
        originalMainSkillSelect = SignatureScanner.readInt(pid, getMainSkillSelectAddr())
        LogManager.info("[主线关卡] 已备份原始值 - 刷新剩余: $originalMainRefreshRemaining, 刷新总数: $originalMainRefreshTotal, 等级: $originalMainLevel, 技能选择: $originalMainSkillSelect")
    }

    // ===== 海岛模式修改 =====

    fun setIslandInfiniteRefresh(enable: Boolean): Boolean {
        if (islandBaseAddress <= 0) {
            LogManager.warning("海岛模式基址未找到")
            return false
        }
        islandInfiniteRefresh = enable
        return if (enable) {
            val ok1 = SignatureScanner.writeInt(pid, getIslandRefreshRemainingAddr(), REFRESH_VALUE)
            val ok2 = SignatureScanner.writeInt(pid, getIslandRefreshTotalAddr(), REFRESH_VALUE)
            if (ok1 && ok2) {
                isIslandRefreshModified = true
                LogManager.modify("[海岛模式] 无限刷新已开启 (刷新次数=$REFRESH_VALUE)")
            }
            ok1 && ok2
        } else {
            val ok1 = SignatureScanner.writeInt(pid, getIslandRefreshRemainingAddr(), originalIslandRefreshRemaining)
            val ok2 = SignatureScanner.writeInt(pid, getIslandRefreshTotalAddr(), originalIslandRefreshTotal)
            if (ok1 && ok2) {
                isIslandRefreshModified = false
                LogManager.restore("[海岛模式] 无限刷新已关闭 (已恢复原始值)")
            }
            ok1 && ok2
        }
    }

    fun setIslandLevelLock(enable: Boolean): Boolean {
        if (islandBaseAddress <= 0) {
            LogManager.warning("海岛模式基址未找到")
            return false
        }
        islandLevelLock = enable
        return if (enable) {
            val ok = SignatureScanner.writeInt(pid, getIslandLevelAddr(), LOCK_LEVEL)
            if (ok) {
                isIslandLevelModified = true
                LogManager.modify("[海岛模式] 等级锁定已开启 (等级=$LOCK_LEVEL)")
                startLockLoop()
            }
            ok
        } else {
            val ok = SignatureScanner.writeInt(pid, getIslandLevelAddr(), originalIslandLevel)
            if (ok) {
                isIslandLevelModified = false
                LogManager.restore("[海岛模式] 等级锁定已关闭 (已恢复原始值)")
                checkStopLockLoop()
            }
            ok
        }
    }

    // ===== 主线关卡修改 =====

    fun setMainLevelInfiniteRefresh(enable: Boolean): Boolean {
        if (mainLevelBaseAddress <= 0) {
            LogManager.warning("主线关卡基址未找到")
            return false
        }
        mainLevelInfiniteRefresh = enable
        return if (enable) {
            val ok1 = SignatureScanner.writeInt(pid, getMainRefreshRemainingAddr(), REFRESH_VALUE)
            val ok2 = SignatureScanner.writeInt(pid, getMainRefreshTotalAddr(), REFRESH_VALUE)
            if (ok1 && ok2) {
                isMainRefreshModified = true
                LogManager.modify("[主线关卡] 无限刷新已开启 (刷新次数=$REFRESH_VALUE)")
            }
            ok1 && ok2
        } else {
            val ok1 = SignatureScanner.writeInt(pid, getMainRefreshRemainingAddr(), originalMainRefreshRemaining)
            val ok2 = SignatureScanner.writeInt(pid, getMainRefreshTotalAddr(), originalMainRefreshTotal)
            if (ok1 && ok2) {
                isMainRefreshModified = false
                LogManager.restore("[主线关卡] 无限刷新已关闭 (已恢复原始值)")
            }
            ok1 && ok2
        }
    }

    fun setMainLevelInfiniteSkill(enable: Boolean): Boolean {
        if (mainLevelBaseAddress <= 0) {
            LogManager.warning("主线关卡基址未找到")
            return false
        }
        mainLevelInfiniteSkill = enable
        val addr = getMainSkillSelectAddr()
        return if (enable) {
            val ok = SignatureScanner.writeInt(pid, addr, SKILL_SELECT_VALUE)
            if (ok) {
                isMainSkillModified = true
                LogManager.modify("[主线关卡] 无限选择技能已开启 (地址: 0x${addr.toString(16)}, 值: $SKILL_SELECT_VALUE)")
            }
            ok
        } else {
            val ok = SignatureScanner.writeInt(pid, addr, originalMainSkillSelect)
            if (ok) {
                isMainSkillModified = false
                LogManager.restore("[主线关卡] 无限选择技能已关闭 (已恢复原始值)")
            }
            ok
        }
    }

    // ===== 锁定循环 =====

    private fun startLockLoop() {
        if (lockThreadRunning.get()) return
        lockThreadRunning.set(true)
        lockThread = thread(name = "MemoryLockThread", isDaemon = true) {
            LogManager.info("内存锁定循环已启动")
            lockLoopCount = 0
            while (lockThreadRunning.get()) {
                try {
                    applyLockedValues()
                    lockLoopCount++
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    LogManager.error("锁定循环出错: ${e.message}")
                }
            }
            LogManager.info("内存锁定循环已停止")
        }
    }

    private fun checkStopLockLoop() {
        val needsLock = islandLevelLock
        if (!needsLock) {
            stopLockLoop()
        }
    }

    fun stopLockLoop() {
        lockThreadRunning.set(false)
        lockThread?.interrupt()
        lockThread = null
    }

    private fun applyLockedValues() {
        // 海岛等级锁定 - 持续监控
        if (islandLevelLock && islandBaseAddress > 0) {
            val curLevel = SignatureScanner.readInt(pid, getIslandLevelAddr())
            if (curLevel != LOCK_LEVEL) {
                SignatureScanner.writeInt(pid, getIslandLevelAddr(), LOCK_LEVEL)
                if (lockLoopCount % 10 == 0L) {
                    LogManager.debug("[海岛模式] 等级被改变，已重新锁定")
                }
            }
        }

        // 海岛无限刷新 - 持续锁定
        if (islandInfiniteRefresh && islandBaseAddress > 0) {
            SignatureScanner.writeInt(pid, getIslandRefreshRemainingAddr(), REFRESH_VALUE)
            SignatureScanner.writeInt(pid, getIslandRefreshTotalAddr(), REFRESH_VALUE)
        }

        // 主线无限刷新
        if (mainLevelInfiniteRefresh && mainLevelBaseAddress > 0) {
            SignatureScanner.writeInt(pid, getMainRefreshRemainingAddr(), REFRESH_VALUE)
            SignatureScanner.writeInt(pid, getMainRefreshTotalAddr(), REFRESH_VALUE)
        }

        // 主线无限选择技能
        if (mainLevelInfiniteSkill && mainLevelBaseAddress > 0) {
            SignatureScanner.writeInt(pid, getMainSkillSelectAddr(), SKILL_SELECT_VALUE)
        }
    }

    // ===== 恢复功能 =====

    fun restoreIslandDefaults() {
        if (islandBaseAddress <= 0) return
        LogManager.restore("[海岛模式] 恢复默认值")
        SignatureScanner.writeInt(pid, getIslandRefreshRemainingAddr(), originalIslandRefreshRemaining)
        SignatureScanner.writeInt(pid, getIslandRefreshTotalAddr(), originalIslandRefreshTotal)
        SignatureScanner.writeInt(pid, getIslandLevelAddr(), originalIslandLevel)
        isIslandRefreshModified = false
        isIslandLevelModified = false
    }

    fun restoreMainLevelDefaults() {
        if (mainLevelBaseAddress <= 0) return
        LogManager.restore("[主线关卡] 恢复默认值")
        SignatureScanner.writeInt(pid, getMainRefreshRemainingAddr(), originalMainRefreshRemaining)
        SignatureScanner.writeInt(pid, getMainRefreshTotalAddr(), originalMainRefreshTotal)
        SignatureScanner.writeInt(pid, getMainLevelAddr(), originalMainLevel)
        SignatureScanner.writeInt(pid, getMainSkillSelectAddr(), originalMainSkillSelect)
        isMainRefreshModified = false
        isMainSkillModified = false
    }

    fun restoreAllDefaults() {
        stopLockLoop()
        restoreIslandDefaults()
        restoreMainLevelDefaults()
        islandInfiniteRefresh = false
        islandLevelLock = false
        mainLevelInfiniteRefresh = false
        mainLevelInfiniteSkill = false
    }

    fun isIslandBaseFound() = islandBaseAddress > 0
    fun isMainLevelBaseFound() = mainLevelBaseAddress > 0

    fun getPid() = pid
}
