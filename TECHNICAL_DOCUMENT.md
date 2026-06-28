# 步步为营 LSPosed 游戏修改器 - 技术文档

## 一、项目概述

### 1.1 项目背景

本项目是一款基于 **LSPosed** 框架的 Android 游戏修改模块，目标是 **com.feiyu.stepbystepapp**（步步为营）游戏。模块通过内存扫描和特征码定位技术，实现游戏数据的动态修改。

### 1.2 核心功能

| 功能模块 | 描述 | 实现状态 |
|---------|------|---------|
| 特征码搜索 | 基于 GG 修改器原理的联合搜索算法 | ✅ 已完成 |
| 无限刷新 | 局内刷新次数锁定为 999 | ✅ 已完成 |
| 等级锁定 | 局内等级锁定为 1（持续监控） | ✅ 已完成 |
| 悬浮窗 UI | 可拖拽悬浮球 + 卡片面板 | ✅ 已完成 |
| 主题换色 | 6 种主题色可选 | ✅ 已完成 |

### 1.3 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                      LSPosed Framework                       │
├─────────────────────────────────────────────────────────────┤
│  MainHook (入口)                                            │
│  ├── hookAllApplications() → 劫持 Application.onCreate()     │
│  └── handleLoadPackage() → 识别目标应用                      │
├─────────────────────────────────────────────────────────────┤
│  FloatingWindowManager (悬浮窗管理)                          │
│  ├── createFloatingButton() → 创建悬浮球                    │
│  ├── showPanel() / hidePanel() → 面板显示/隐藏              │
│  └── 触摸拖拽 + 点击事件处理                                 │
├─────────────────────────────────────────────────────────────┤
│  MemoryScanner (内存扫描核心)                                │
│  ├── smartScan() → 特征码智能搜索                           │
│  ├── scanFeatureCodes() → 分层扫描算法                       │
│  ├── verifyFeatureGroup() → 匹配度验证                      │
│  ├── calculateOffsets() → 偏移量计算                        │
│  └── applyLevelLock() / applyInfiniteRefresh() → 内存写入   │
├─────────────────────────────────────────────────────────────┤
│  ModConfig (配置管理)                                        │
│  ├── SharedPreferences 持久化                               │
│  ├── 地址/原始值备份                                         │
│  └── 开关状态管理                                            │
├─────────────────────────────────────────────────────────────┤
│  LogManager (日志系统)                                       │
│  ├── 分类日志 (INFO/SEARCH/MODIFY/RESTORE)                 │
│  └── 实时日志监听器                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、核心技术详解

### 2.1 特征码搜索原理

#### 2.1.1 特征码定义

```kotlin
private val fullFeatureCodes = intArrayOf(
    1148846080,      // [0] 索引0的值
    1084227584,      // [1] 索引1的值
    -1055916032,     // [2] 索引2的值（负数）
    1065353216,      // [3] 索引3的值
    8,               // [4] 索引4的值
    108,             // [5] 索引5的值
    108,             // [6] 索引6的值（重复）
    2,               // [7] 索引7的值
    2,               // [8] 索引8的值（重复）
    1,               // [9] 索引9的值
    1072693248       // [10] 索引10的值
)
```

#### 2.1.2 搜索算法流程

```
┌──────────────────────────────────────────────────────────────┐
│                     smartScan() 流程                         │
├──────────────────────────────────────────────────────────────┤
│ 1. 获取内存区域列表 (通过 /proc/[pid]/maps)                   │
│    ↓                                                          │
│ 2. 过滤条件：                                                  │
│    - 必须有读权限 (permissions.contains("r"))                 │
│    - 排除系统路径 (/dev/, /system/, /vendor/, etc.)           │
│    - 排除 dalvik/dex/oat/art 等虚拟机相关区域                  │
│    - 大小限制: 64B ~ 100MB                                   │
│    ↓                                                          │
│ 3. scanFeatureCodes() - 第一阶段扫描                          │
│    - 逐区域扫描，首值命中时记录候选地址                         │
│    - 调用 verifyFeatureGroup() 验证完整特征组                  │
│    ↓                                                          │
│ 4. 按 matchCount 降序排序候选结果                             │
│    ↓                                                          │
│ 5. 选择最佳候选:                                               │
│    - matchCount >= 9 → 直接使用                               │
│    - matchCount >= 6 → 使用最优候选                           │
│    - 其他 → 返回候选列表供进一步筛选                           │
│    ↓                                                          │
│ 6. finalizeScan() → 计算偏移地址并备份原始值                   │
└──────────────────────────────────────────────────────────────┘
```

#### 2.1.3 匹配验证算法 (verifyFeatureGroup)

```kotlin
/**
 * 核心原理: 顺序匹配 + 跨度限制
 *
 * 1. 从候选地址读取 512 字节范围
 * 2. 按字节偏移顺序扫描特征值
 * 3. 条件1: 匹配值必须按顺序出现
 * 4. 条件2: 相邻匹配位置间隔 ≤ 32 个 int (128 字节)
 * 5. 返回匹配数量
 */
private fun verifyFeatureGroup(baseAddr: Long): Int {
    val bytes = readMemory(baseAddr, 512)
    val ints = ByteBuffer.wrap(bytes).order(LITTLE_ENDIAN).asIntBuffer()

    var currentIdx = 0
    var lastPos = -1

    for (i in 0 until intCount) {
        if (ints[i] == fullFeatureCodes[currentIdx]) {
            // 跨度检查: 相邻匹配位置间隔 ≤ 128 字节
            if (lastPos == -1 || (i - lastPos) <= 32) {
                positions[currentIdx] = i
                lastPos = i
                currentIdx++
            }
        }
    }

    // 匹配数 ≥ 6 且 跨度 ≤ 512 字节 → 有效
    if (matched >= 6 && span <= 512) return matched
    return matched
}
```

### 2.2 内存读写机制

#### 2.2.1 读取 /proc/[pid]/mem

```kotlin
/**
 * Linux 内存读取接口
 * - 通过 /proc/self/mem 文件描述符访问
 * - 使用 RandomAccessFile 进行随机读写
 * - 支持大文件分块读取
 */
private fun readMemory(address: Long, size: Int): ByteArray? {
    val pid = android.os.Process.myPid()
    return try {
        val buffer = ByteArray(size)
        val fis = FileInputStream("/proc/$pid/mem")
        fis.skip(address)  // 跳转至目标地址
        var totalRead = 0
        while (totalRead < size) {
            val chunk = ByteArray(minOf(size - totalRead, 4096))
            val read = fis.read(chunk)
            if (read <= 0) break
            System.arraycopy(chunk, 0, buffer, totalRead, read)
            totalRead += read
        }
        buffer
    } catch (e: Exception) { null }
}

/**
 * 写入内存
 * - 写入模式: "rw"
 * - 每次写入 4 字节 (Int)
 */
private fun writeMemory(address: Long, data: ByteArray) {
    val pid = android.os.Process.myPid()
    RandomAccessFile("/proc/$pid/mem", "rw").use { raf ->
        raf.seek(address)
        raf.write(data)
    }
}
```

#### 2.2.2 字节序转换

```kotlin
// Little Endian (小端序) - Intel/ARM 使用
private fun intToBytes(value: Int): ByteArray {
    return ByteBuffer.allocate(4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(value)
        .array()
}

fun readInt(address: Long): Int {
    val bytes = readMemory(address, 4)
    return ByteBuffer.wrap(bytes)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int
}
```

### 2.3 偏移量计算

#### 2.3.1 地址结构

```
基址 (Base Address) = 0x6C7CC00884
├── +380 (0x17C): 刷新次数 (Refresh Count)
├── +384 (0x180): 可刷新次数 (Available Refresh)
└── +386 (0x182): 等级 (Level)
```

#### 2.3.2 偏移计算代码

```kotlin
fun calculateOffsets(baseAddress: Long) {
    ModConfig.baseAddress = baseAddress
    ModConfig.refreshCountAddress = baseAddress + 380L      // 0x17C
    ModConfig.availableRefreshAddress = baseAddress + 384L  // 0x180
    ModConfig.levelAddress = baseAddress + 386L            // 0x182
}
```

### 2.4 等级锁定实现 (持续监控)

#### 2.4.1 后台守护线程

```kotlin
private var lockThread: Thread? = null
@Volatile private var lockRunning = false

private fun startLockThread() {
    if (lockRunning) return
    lockRunning = true

    lockThread = Thread {
        while (lockRunning) {
            try {
                if (ModConfig.isLevelLock && ModConfig.levelAddress != 0L) {
                    val curLevel = readInt(ModConfig.levelAddress)
                    if (curLevel != 1) {
                        writeInt(ModConfig.levelAddress, 1)  // 重新锁定
                        if (count % 10 == 0) {
                            logger.info("等级被改变，已重新锁定")
                        }
                    }
                }
                Thread.sleep(500)  // 500ms 检查间隔
            } catch (e: InterruptedException) { break }
        }
    }.apply {
        name = "LevelLockThread"
        isDaemon = true
        start()
    }
}
```

#### 2.4.2 锁定原理

```
游戏内存: 等级值 ←→ 被游戏逻辑修改
                        ↑
                    每 500ms 重新写入 1
```

**为什么需要持续写入？**
- 游戏运行中，等级值会被游戏逻辑反复修改
- 单次写入后会被游戏覆盖
- 必须轮询检测并重新写入才能保持锁定状态

### 2.5 无限刷新实现

```kotlin
fun applyInfiniteRefresh(): Boolean {
    // 1. 备份原始值
    if (!ModConfig.isRefreshModified) {
        ModConfig.originalRefreshCount = readInt(refreshCountAddress)
        ModConfig.originalAvailableRefresh = readInt(availableRefreshAddress)
    }

    // 2. 写入修改值
    val r1 = writeInt(refreshCountAddress, 999)      // 刷新次数 = 999
    val r2 = writeInt(availableRefreshAddress, 999)  // 可刷新次数 = 999

    if (r1 && r2) {
        ModConfig.isRefreshModified = true
        return true
    }
    return false
}
```

**与等级锁定的区别：**
- 无限刷新只需要**单次写入**（值不会自动恢复）
- 等级锁定需要**持续轮询写入**（值会被游戏覆盖）

---

## 三、配置管理系统

### 3.1 ModConfig 数据结构

```kotlin
object ModConfig {
    // === 功能开关 ===
    var isInfiniteRefresh: Boolean  // 无限刷新开关
    var isLevelLock: Boolean        // 等级锁定开关

    // === 地址存储 ===
    var baseAddress: Long           // 基址
    var refreshCountAddress: Long   // 刷新次数地址
    var availableRefreshAddress: Long // 可刷新次数地址
    var levelAddress: Long          // 等级地址

    // === 原始值备份 ===
    var originalRefreshCount: Int
    var originalAvailableRefresh: Int
    var originalLevel: Int

    // === 修改状态 ===
    var isRefreshModified: Boolean  // 是否已修改刷新
    var isLevelModified: Boolean    // 是否已修改等级

    // === 主题 ===
    var themeColor: String          // "purple"|"blue"|"green"|"red"|"orange"|"pink"
}
```

### 3.2 持久化方式

```kotlin
// 使用跨进程 SharedPreferences
fun init(context: Context) {
    val modContext = context.createPackageContext(
        "com.stepbystep.modifier",
        Context.CONTEXT_IGNORE_SECURITY  // 忽略安全检查
    )
    prefs = modContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_WORLD_READABLE  // 允许其他进程读取
    )
}
```

---

## 四、日志系统

### 4.1 日志类型

| 类型 | 用途 | 颜色/标识 |
|-----|------|----------|
| INFO | 一般信息 | 白色 |
| SUCCESS | 操作成功 | 绿色 ✓ |
| WARNING | 警告信息 | 黄色 ⚠ |
| ERROR | 错误信息 | 红色 ✗ |
| SEARCH | 搜索过程 | 蓝色 🔍 |
| MODIFY | 修改操作 | 橙色 📝 |
| RESTORE | 恢复操作 | 紫色 ↩ |

### 4.2 日志格式

```
[时间戳] [类型] 消息内容
  → 详情（可选）
```

**示例：**
```
[14:32:15.123] [SEARCH] 开始智能搜索特征码...
[14:32:15.124] [SEARCH] 特征码列表: 1148846080, 1084227584...
[14:32:16.456] [SUCCESS] 找到唯一基址！
  → 0x6C7CC00884
```

---

## 五、LSPosed 入口机制

### 5.1 入口点配置

**assets/xposed_init:**
```
com.stepbystep.modifier.hook.MainHook
```

### 5.2 加载流程

```kotlin
class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 1. 标记模块已激活
        isModuleActivated = true

        // 2. Hook 所有 Application.onCreate()
        hookAllApplications(lpparam)

        // 3. 判断是否为目标应用
        if (lpparam.packageName != TARGET_PACKAGE) return

        // 4. 保存 ClassLoader
        classLoader = lpparam.classLoader

        // 5. 初始化模块
        initMod(lpparam)
    }
}
```

### 5.3 悬浮窗启动

```kotlin
private fun initMod(lpparam: XC_LoadPackage.LoadPackageParam) {
    Thread {
        Thread.sleep(3000)  // 等待游戏完全加载

        Handler(Looper.getMainLooper()).post {
            // 显示悬浮窗
            FloatingWindowManager.getInstance().init(appContext)
            FloatingWindowManager.getInstance().show()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(app, "⚡ 步步升级 修改器已加载成功", Toast.LENGTH_SHORT).show()
        }, 2000)
    }.start()
}
```

---

## 六、悬浮窗架构

### 6.1 窗口类型

```kotlin
val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
} else {
    WindowManager.LayoutParams.TYPE_PHONE
}
```

**TYPE_APPLICATION_OVERLAY** (Android 8.0+):
- 需要申请 `SYSTEM_ALERT_WINDOW` 权限
- 显示在其他应用之上
- 不允许获取焦点

### 6.2 悬浮球组件

- **形状**: 圆形 (56dp)
- **图标**: ⚡ 符号
- **颜色**: 根据主题变化
- **位置**: 首次显示在 (x=100, y=300)
- **交互**: 拖拽移动 + 点击展开面板

### 6.3 面板组件

- **尺寸**: 屏幕 90% 宽 × 75% 高
- **模式**: `FLAG_NOT_TOUCH_MODAL` - 点击外部不关闭
- **内容**: 功能标签页 (Compose UI)

---

## 七、UI/UX 设计 (Jetpack Compose)

### 7.1 区域划分

```
┌─────────────────────────────────────────┐
│  Header (渐变背景 + 标题)               │
├─────────────────────────────────────────┤
│  TabBar (胶囊标签: 海岛/主线/Boss/设置) │
├─────────────────────────────────────────┤
│                                         │
│  Content Area (区域内容)                 │
│  ├── 海岛探险: 搜索 + 开关 + 地址       │
│  ├── 主线关卡: 占位页面 (待开发)         │
│  ├── 部落Boss: 占位页面 (待开发)         │
│  └── 通用设置: 主题 + 操作 + 关于        │
│                                         │
└─────────────────────────────────────────┘
```

### 7.2 区域配色方案

| 区域 | 主色 | 辅色 | 背景色 |
|-----|------|------|-------|
| 海岛探险 | #00A896 | #028090 | #E8F8F5 |
| 主线关卡 | #E63946 | #D62828 | #FDF2F4 |
| 部落Boss | #7B2CBF | #5A189A | #F5EEFF |
| 通用设置 | #5C6BC0 | #3F51B5 | #E8EAF6 |

### 7.3 主题色选项

| 名称 | 色值 | 预览 |
|-----|------|-----|
| 优雅紫 | #6750A4 | 🟣 |
| 科技蓝 | #0061A4 | 🔵 |
| 自然绿 | #006B5A | 🟢 |
| 热情红 | #BA1A1A | 🔴 |
| 活力橙 | #FF6B00 | 🟠 |
| 浪漫粉 | #AD1A6B | 🩷 |

---

## 八、数据流图

### 8.1 特征码搜索数据流

```
用户点击搜索
     ↓
smartScan()
     ↓
┌─────────────────────────────────────┐
│  getMemoryRegions()                 │
│  读取 /proc/[pid]/maps              │
└─────────────────────────────────────┘
     ↓
┌─────────────────────────────────────┐
│  scanFeatureCodes()                  │
│  for region in regions:             │
│    if readable && !system:          │
│      readMemory()                   │
│      if firstValue match:           │
│        verifyFeatureGroup()         │
└─────────────────────────────────────┘
     ↓
┌─────────────────────────────────────┐
│  sortedResults = results.sort()     │
│  选择 matchCount >= 6 的最优候选     │
└─────────────────────────────────────┘
     ↓
┌─────────────────────────────────────┐
│  finalizeScan(baseAddress)           │
│  calculateOffsets() → 计算子地址      │
│  backupCurrentValues() → 备份原始值  │
└─────────────────────────────────────┘
     ↓
更新 UI 地址显示
```

### 8.2 等级锁定数据流

```
用户开启等级锁定
     ↓
applyLevelLock()
     ↓
┌─────────────────────────────────────┐
│  backupOriginalValues()             │
│  备份当前等级值                      │
└─────────────────────────────────────┘
     ↓
writeInt(levelAddress, 1) → 单次写入
     ↓
┌─────────────────────────────────────┐
│  startLockThread()                  │
│  后台线程每 500ms 检查               │
│  while (lockRunning) {              │
│    if readInt() != 1:               │
│      writeInt(levelAddress, 1)       │
│  }                                  │
└─────────────────────────────────────┘
```

---

## 九、关键技术点总结

### 9.1 内存扫描核心

1. **特征码定义**: 11 个 int 值组成的唯一序列
2. **搜索策略**: 首值命中 → 扩展验证 → 匹配度排序
3. **验证条件**: 匹配数 ≥ 6 且 跨度 ≤ 512 字节
4. **偏移计算**: 基址 + 固定偏移量

### 9.2 内存写入核心

1. **读写接口**: `/proc/[pid]/mem` 文件
2. **字节序**: Little Endian
3. **写入单位**: 4 字节 (Int)
4. **权限**: 需要目标进程可读写的内存区域

### 9.3 持久化核心

1. **跨进程访问**: `Context.CONTEXT_IGNORE_SECURITY`
2. **模式**: `MODE_WORLD_READABLE`
3. **存储内容**: 地址、原始值、开关状态

### 9.4 悬浮窗核心

1. **窗口类型**: `TYPE_APPLICATION_OVERLAY`
2. **触摸处理**: `ACTION_DOWN/MOVE/UP` 区分拖拽和点击
3. **UI 框架**: Jetpack Compose (最新重构)

---

## 十、文件结构

```
app/
├── src/main/
│   ├── java/com/stepbystep/modifier/
│   │   ├── hook/
│   │   │   ├── MainHook.kt              # LSPosed 入口
│   │   │   └── FloatingWindowManager.kt  # 悬浮窗管理
│   │   ├── service/
│   │   │   └── FloatingWindowService.kt  # 悬浮窗服务
│   │   ├── ui/
│   │   │   ├── ModPanelView.kt          # 原生 UI 面板
│   │   │   ├── ComposePanel.kt          # Compose UI 面板
│   │   │   ├── SettingsActivity.kt       # 设置页
│   │   │   └── theme/                   # Compose 主题
│   │   │       ├── ColorPalette.kt
│   │   │       ├── Theme.kt
│   │   │       └── Typography.kt
│   │   ├── MemoryScanner.kt             # 内存扫描核心
│   │   ├── ModConfig.kt                 # 配置管理
│   │   └── LogManager.kt                # 日志系统
│   ├── assets/
│   │   └── xposed_init                  # LSPosed 入口文件
│   └── res/
│       ├── layout/                      # XML 布局
│       ├── values/                      # 资源值
│       └── drawable/                    # drawable 资源
└── build.gradle.kts                     # 模块构建配置
```

---

## 十一、后续开发计划

### 11.1 待开发功能区

| 功能区 | 描述 | 优先级 |
|-------|------|-------|
| 主线关卡 | 主线关卡相关修改功能 | 中 |
| 部落Boss | 部落Boss战斗相关功能 | 中 |

### 11.2 技术升级方向

1. **特征码自动更新**: 当搜索失败时提示用户录制新特征码
2. **多基址管理**: 支持同一游戏的多个基址版本
3. **数据统计**: 游戏数据分析面板
4. **脚本录制**: 录制并回放复杂操作序列

---

## 十二、参考链接

- [LSPosed 官方仓库](https://github.com/LSPosed/LSPosed)
- [GG修改器 原理介绍](https://www.123pan.com/)
- [Jetpack Compose 文档](https://developer.android.com/jetpack/compose)
- [Android 内存管理](/proc/[pid]/mem)

---

*文档版本: v1.0*
*最后更新: 2026-06-28*
