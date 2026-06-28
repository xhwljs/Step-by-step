package com.feiyu.stepbystepmod.util

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

object SignatureScanner {

    private const val TAG = "SignatureScanner"

    // 海岛模式完整特征码（11个int）
    // 格式: 1148846080;1084227584;-1055916032;1065353216;8;2(刷新剩余);2(刷新总次数);1(海岛等级);1072693248
    val ISLAND_FULL_SIGNATURE = intArrayOf(
        1148846080,      // [0] 基址锚点1
        1084227584,      // [1] 基址锚点2
        -1055916032,     // [2] 基址锚点3
        1065353216,      // [3] 基址锚点4
        8,               // [4] 类型标识
        2,               // [5] 刷新剩余次数
        2,               // [6] 刷新总次数
        1,               // [7] 海岛等级
        0,               // [8] 占位
        0,               // [9] 占位
        1072693248       // [10] 基址锚点5
    )

    // 主线关卡完整特征码（8个int）
    // 格式: 1148846080;1073741824;-1055916032;1065353216;2(刷新剩余);2(刷新总次数);1(主线等级);1072693248
    val MAIN_LEVEL_FULL_SIGNATURE = intArrayOf(
        1148846080,      // [0] 基址锚点1
        1073741824,      // [1] 基址锚点2
        -1055916032,     // [2] 基址锚点3
        1065353216,      // [3] 基址锚点4
        2,               // [4] 刷新剩余次数
        2,               // [5] 刷新总次数
        1,               // [6] 主线等级
        1072693248       // [7] 基址锚点5
    )

    // 偏移量定义（字节偏移）- 基于用户提供的11元素完整签名
    // 签名: [0]=1148846080,[1]=1084227584,[2]=-1055916032,[3]=1065353216,[4]=8,[5]=108,[6]=108,[7]=2(刷新剩余),[8]=2(刷新总),[9]=1(等级),[10]=1072693248
    object IslandOffsets {
        const val REFRESH_REMAINING = 7 * 4    // 0x1C - 刷新剩余次数
        const val REFRESH_TOTAL = 8 * 4        // 0x20 - 刷新总次数
        const val LEVEL = 9 * 4                // 0x24 - 海岛等级
    }

    object MainLevelOffsets {
        const val REFRESH_REMAINING = 4 * 4    // 0x10 - 刷新剩余次数
        const val REFRESH_TOTAL = 5 * 4        // 0x14 - 刷新总次数
        const val LEVEL = 6 * 4                // 0x18 - 主线等级
        const val SKILL_SELECT = -0x12         // 无限选择技能（刷新剩余次数基址 - 0x12）
    }

    data class ScanCandidate(
        val address: Long,
        val matchCount: Int,
        val positions: IntArray
    ) : Comparable<ScanCandidate> {
        override fun compareTo(other: ScanCandidate): Int = other.matchCount - matchCount
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScanCandidate) return false
            return address == other.address
        }
        override fun hashCode(): Int = address.hashCode()
    }

    data class ScanResult(
        val success: Boolean,
        val baseAddress: Long = 0,
        val matchCount: Int = 0,
        val message: String = ""
    )

    interface ScanCallback {
        fun onProgress(progress: Int)
        fun onSuccess(baseAddress: Long, matchCount: Int)
        fun onError(message: String)
    }

    // ===== 公开扫描方法 =====

    fun scanIslandMode(pid: Int, callback: ScanCallback) {
        thread(name = "Scan-Island") {
            try {
                LogManager.search("[海岛模式] 开始智能搜索特征码...")
                LogManager.search("[海岛模式] 特征码: ${ISLAND_FULL_SIGNATURE.joinToString()}")
                callback.onProgress(0)

                val result = smartScan(pid, ISLAND_FULL_SIGNATURE, "海岛模式", callback)

                if (result.success) {
                    LogManager.success("[海岛模式] 找到基址: 0x${result.baseAddress.toString(16)} (匹配度: ${result.matchCount}/${ISLAND_FULL_SIGNATURE.size})")
                    LogManager.info("[海岛模式] 刷新剩余次数偏移: 0x${Integer.toHexString(IslandOffsets.REFRESH_REMAINING)}")
                    LogManager.info("[海岛模式] 刷新总次数偏移: 0x${Integer.toHexString(IslandOffsets.REFRESH_TOTAL)}")
                    LogManager.info("[海岛模式] 海岛等级偏移: 0x${Integer.toHexString(IslandOffsets.LEVEL)}")
                    callback.onSuccess(result.baseAddress, result.matchCount)
                } else {
                    LogManager.error("[海岛模式] 扫描失败: ${result.message}")
                    callback.onError(result.message)
                }
            } catch (e: Exception) {
                LogManager.error("[海岛模式] 扫描异常: ${e.message}")
                callback.onError(e.message ?: "未知错误")
            }
        }
    }

    fun scanMainLevel(pid: Int, callback: ScanCallback) {
        thread(name = "Scan-MainLevel") {
            try {
                LogManager.search("[主线关卡] 开始智能搜索特征码...")
                LogManager.search("[主线关卡] 特征码: ${MAIN_LEVEL_FULL_SIGNATURE.joinToString()}")
                callback.onProgress(0)

                val result = smartScan(pid, MAIN_LEVEL_FULL_SIGNATURE, "主线关卡", callback)

                if (result.success) {
                    LogManager.success("[主线关卡] 找到基址: 0x${result.baseAddress.toString(16)} (匹配度: ${result.matchCount}/${MAIN_LEVEL_FULL_SIGNATURE.size})")
                    LogManager.info("[主线关卡] 刷新剩余次数偏移: 0x${Integer.toHexString(MainLevelOffsets.REFRESH_REMAINING)}")
                    LogManager.info("[主线关卡] 刷新总次数偏移: 0x${Integer.toHexString(MainLevelOffsets.REFRESH_TOTAL)}")
                    LogManager.info("[主线关卡] 主线等级偏移: 0x${Integer.toHexString(MainLevelOffsets.LEVEL)}")
                    LogManager.info("[主线关卡] 无限选择技能偏移: -0x12 (刷新剩余次数地址-0x12)")
                    callback.onSuccess(result.baseAddress, result.matchCount)
                } else {
                    LogManager.error("[主线关卡] 扫描失败: ${result.message}")
                    callback.onError(result.message)
                }
            } catch (e: Exception) {
                LogManager.error("[主线关卡] 扫描异常: ${e.message}")
                callback.onError(e.message ?: "未知错误")
            }
        }
    }

    // ===== 核心智能扫描算法 =====

    private fun smartScan(
        pid: Int,
        signature: IntArray,
        name: String,
        callback: ScanCallback
    ): ScanResult {
        val regions = getMemoryRegions(pid)
        if (regions.isEmpty()) {
            return ScanResult(false, message = "未找到可扫描的内存区域")
        }

        LogManager.search("[$name] 共 ${regions.size} 个内存区域待扫描")

        val candidates = mutableListOf<ScanCandidate>()
        var totalScanned = 0L
        val totalSize = regions.sumOf { it.size }
        val firstValue = signature[0]

        for ((index, region) in regions.withIndex()) {
            if (region.size <= 0 || region.size > 100 * 1024 * 1024) continue

            val buffer = readMemory(pid, region.startAddr, region.size.toInt())
            if (buffer == null || buffer.isEmpty()) continue

            val intBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
            val intCount = intBuffer.remaining()

            // 第一阶段：首值快速匹配
            for (i in 0 until intCount) {
                if (intBuffer.get(i) == firstValue) {
                    val candidateAddr = region.startAddr + i * 4L
                    // 第二阶段：完整特征组验证
                    val (matchCount, positions) = verifyFeatureGroup(
                        pid, candidateAddr, signature
                    )
                    if (matchCount >= 6) {
                        candidates.add(ScanCandidate(candidateAddr, matchCount, positions))
                        LogManager.search("[$name] 候选地址: 0x${candidateAddr.toString(16)} (匹配: $matchCount)")
                    }
                }
            }

            totalScanned += region.size
            val progress = ((totalScanned * 100) / totalSize.coerceAtLeast(1)).toInt()
            callback.onProgress(progress.coerceAtMost(100))
        }

        // 按匹配度排序
        candidates.sort()

        LogManager.search("[$name] 扫描完成，找到 ${candidates.size} 个候选地址")

        // 选择最佳候选
        return when {
            candidates.isEmpty() -> ScanResult(false, message = "未找到匹配的特征码")
            candidates[0].matchCount >= 9 -> {
                ScanResult(true, candidates[0].address, candidates[0].matchCount)
            }
            candidates[0].matchCount >= 6 -> {
                LogManager.warning("[$name] 匹配度较低，使用最佳候选")
                ScanResult(true, candidates[0].address, candidates[0].matchCount)
            }
            else -> ScanResult(false, message = "匹配度过低，无法确定基址")
        }
    }

    /**
     * 核心匹配验证算法
     * 原理: 顺序匹配 + 跨度限制
     */
    private fun verifyFeatureGroup(
        pid: Int,
        baseAddr: Long,
        signature: IntArray
    ): Pair<Int, IntArray> {
        val scanSize = 512 // 扫描512字节范围
        val bytes = readMemory(pid, baseAddr, scanSize) ?: return Pair(0, intArrayOf())

        val intBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        val intCount = intBuffer.remaining()

        val positions = IntArray(signature.size)
        var currentIdx = 0
        var lastPos = -1

        for (i in 0 until intCount) {
            if (currentIdx >= signature.size) break

            if (intBuffer.get(i) == signature[currentIdx]) {
                // 跨度检查: 相邻匹配位置间隔 ≤ 32个int (128字节)
                if (lastPos == -1 || (i - lastPos) <= 32) {
                    positions[currentIdx] = i
                    lastPos = i
                    currentIdx++
                }
            }
        }

        // 检查整体跨度是否 ≤ 512字节
        val matched = currentIdx
        val span = if (matched > 0) (positions[matched - 1] - positions[0]) * 4 else 0

        return if (matched >= 6 && span <= 512) {
            Pair(matched, positions)
        } else {
            Pair(matched, positions)
        }
    }

    // ===== 内存区域解析 =====

    private data class MemoryRegion(
        val startAddr: Long,
        val endAddr: Long,
        val size: Long,
        val perms: String,
        val path: String
    )

    private fun getMemoryRegions(pid: Int): List<MemoryRegion> {
        val regions = mutableListOf<MemoryRegion>()
        val mapsFile = File("/proc/$pid/maps")
        if (!mapsFile.exists()) return regions

        val excludedPaths = listOf(
            "/dev/", "/system/", "/vendor/", "/product/",
            "/apex/", "/data/dalvik-cache/", ".dex", ".oat", ".art",
            "/sys/", "/proc/", "anon_inode"
        )

        try {
            mapsFile.forEachLine { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size < 2) return@forEachLine

                val addrParts = parts[0].split("-")
                if (addrParts.size != 2) return@forEachLine

                try {
                    val start = addrParts[0].toLong(16)
                    val end = addrParts[1].toLong(16)
                    val perms = if (parts.size > 1) parts[1] else ""
                    val path = if (parts.size > 5) parts[5] else ""

                    // 过滤条件
                    if (!perms.contains("r")) return@forEachLine
                    if (path.isNotEmpty() && excludedPaths.any { path.contains(it) }) return@forEachLine

                    val size = end - start
                    if (size < 64 || size > 100 * 1024 * 1024) return@forEachLine

                    regions.add(MemoryRegion(start, end, size, perms, path))
                } catch (_: NumberFormatException) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析内存映射失败: ${e.message}")
        }

        return regions
    }

    // ===== 内存读写 =====

    fun readMemory(pid: Int, address: Long, size: Int): ByteArray? {
        if (size <= 0) return null
        return try {
            val buffer = ByteArray(size)
            val memFile = RandomAccessFile("/proc/$pid/mem", "r")
            memFile.use {
                it.seek(address)
                it.readFully(buffer)
            }
            buffer
        } catch (e: Exception) {
            null
        }
    }

    fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean {
        return try {
            val memFile = RandomAccessFile("/proc/$pid/mem", "rw")
            memFile.use {
                it.seek(address)
                it.write(data)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入内存失败: ${e.message}")
            false
        }
    }

    fun readInt(pid: Int, address: Long): Int {
        val bytes = readMemory(pid, address, 4) ?: return 0
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun writeInt(pid: Int, address: Long, value: Int): Boolean {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        return writeMemory(pid, address, bytes)
    }
}
