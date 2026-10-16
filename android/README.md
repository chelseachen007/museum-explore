# Android 构建

这是博物馆探索的 Kotlin + Jetpack Compose Android 工程，目标 Android 10（API 29）及以上，首版锁定竖屏和浅色模式。

当前 APK 使用 Compose + Material 3 统一页面组件，通过 Navigation Compose 管理页面，离线加载内置的 195 件文物数据，并按城市展示收藏单位和文物数量。

## 构建

需要 Android SDK、JDK 17 和 Gradle 8.10+：

```sh
./gradlew assembleDebug
```

生成文件：`app/build/outputs/apk/debug/app-debug.apk`

当前开发版已接入城市/博物馆浏览、打卡、感想、照片选择、文物手动收集和 ZIP 记录备份入口。当前 ZIP 保存记录和照片 URI 引用；数据仍使用兼容现有版本的 SharedPreferences。

## 验证

使用项目内置 JDK 17 构建：

```sh
JAVA_HOME="$PWD/.toolchain/jdk-17.0.20.1+1/Contents/Home" \\
  ./.toolchain/gradle-8.10.2/bin/gradle -p android assembleDebug
```

运行 Compose UI 和截图冒烟测试（需要已启动 API 35 模拟器）：

```sh
JAVA_HOME="$PWD/.toolchain/jdk-17.0.20.1+1/Contents/Home" \\
  ./.toolchain/gradle-8.10.2/bin/gradle -p android connectedDebugAndroidTest
```
