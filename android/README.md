# Android 构建

这是博物馆探索的原生 Android 工程，目标 Android 10（API 29）及以上。

当前 APK 是第一阶段可安装骨架：离线加载内置的 195 件文物数据，并按城市展示收藏单位和文物数量。

## 构建

需要 Android SDK、JDK 17 和 Gradle 8.10+：

```sh
./gradlew assembleDebug
```

生成文件：`app/build/outputs/apk/debug/app-debug.apk`

当前开发版已接入城市/博物馆浏览、打卡、感想、照片选择、文物手动收集和 ZIP 记录备份入口。当前 ZIP 保存记录和照片 URI 引用；下一阶段再迁移到 Room，并把照片原图打包进备份，同时接入高德地图。
