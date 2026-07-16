# NFC 编码采集

一个用于按扫描顺序采集 NFC 贴片 UID 编码的 Android 应用。

## 功能

- 前台自动读取 NFC 贴片编码。
- 按扫描顺序自动生成序号，从 1 开始。
- 当前清单本地保存，退出应用后再次打开仍保留。
- 导出 Excel 可打开的 `.xls` 表格。
- 清零当前清单，下一盒 NFC 贴片从序号 1 重新采集。
- 同一清单内重复编码会提示，不重复加入。

## 安装包

调试 APK：

`E:\NFCCollector\app\build\outputs\apk\debug\app-debug.apk`

## 使用方式

1. 在支持 NFC 的 Android 手机上安装 APK。
2. 打开手机 NFC 开关。
3. 打开应用后，按顺序将手机靠近 NFC 贴片。
4. 扫描完成后点击“导出 Excel”，选择保存位置。
5. 确认导出后点击“清零”，即可开始下一盒。

## 构建

当前工程使用：

- Android Gradle Plugin 8.13.1
- compileSdk 36
- Java 17 语法级别

本机可用构建命令示例：

```powershell
$env:JAVA_HOME='C:\Users\fodizi\.jdks\openjdk-24.0.2+12-54'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
& 'C:\Users\fodizi\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' assembleDebug
```
