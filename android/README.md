# Android 构建

这是博物馆探索的 Kotlin + Jetpack Compose Android 工程，目标 Android 10（API 29）及以上，首版锁定竖屏和浅色模式。

当前 APK 使用 Compose + Material 3 统一页面组件，通过 Navigation Compose 管理页面，离线加载内置的 195 件文物数据，并按城市展示收藏单位和文物数量。

## 构建

需要 Android SDK、JDK 17 和 Gradle 8.10+：

```sh
./gradlew assembleDebug
```

生成文件：`app/build/outputs/apk/debug/app-debug.apk`

当前 0.5.2 开发版已接入城市/博物馆浏览、打卡、博物馆级感想、拍照/相册多图、照片关联国宝、国宝多图轮播、全图缩放查看和 ZIP 记录备份。照片会复制到应用内部目录，备份 ZIP 同时包含照片文件；记录与设置仍使用兼容现有版本的 SharedPreferences。

文物图片已全部下载到根目录 `relic_images/`，并同步到 `app/src/main/assets/relic_images/`；数据里的 `image` 字段只保存本地相对路径，详情页和列表离线也能显示实物照片。详情页展示字段在读取数据时仍会再清理一次 HTML 标签、实体和引用标记。

如果更新了文物数据或图片，先回到项目根目录执行：

```sh
python3 tools/text_clean.py
python3 tools/localize_assets.py
python3 tools/validate_data.py
```

再执行 Android 构建，确保外链图片已落到本地、文本没有 HTML 残留。

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
