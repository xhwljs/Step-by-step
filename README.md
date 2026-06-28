# StepByStep Mod

LSPosed 游戏修改模块 for com.feiyu.stepbystepapp

## 功能特性

### 海岛模式
- 无限刷新 - 设置刷新剩余次数和总次数为 999
- 无限选择技能 - 锁定海岛等级为 1

### 主线关卡
- 无限刷新 - 设置刷新剩余次数和总次数为 999
- 无限选择技能 - 修改主线刷新剩余次数基址 -0x12 偏移为 99999

### 通用功能
- 悬浮窗控制面板
- 主题换色 (深色/浅色)
- 详细日志系统 (支持复制/清空)
- 部落 Boss 模式 (开发中)

## 技术实现

- **签名扫描**: 基于特征码的内存扫描
- **内存修改**: 直接内存读写操作
- **UI**: Material Design 3 + 悬浮窗

## 构建

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## 自动构建

推送代码到 main 分支会自动触发 GitHub Actions 构建。

## 使用方法

1. 安装模块并激活
2. 授予悬浮窗权限
3. 启动模块
4. 点击"开始修改"扫描特征码
5. 通过开关控制各项功能

## 目标应用

- Package: `com.feiyu.stepbystepapp`
- Min SDK: 26
- Target SDK: 34
