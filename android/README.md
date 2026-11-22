# 放映厅 Android

Kotlin + Jetpack Compose + Media3(ExoPlayer)。一个 APK 同时能装在手机和电视盒子（TV 端 UI 还在做）。

## 构建

需要 JDK 17+、Android SDK（platform 34 / build-tools 34.0.0）、Gradle 8.7。

```bash
export JAVA_HOME='C:\Tools\jdk-21'
export ANDROID_HOME='C:\Tools\android-sdk'
C:/Tools/gradle-8.7/bin/gradle.bat assembleDebug     # 调试包
C:/Tools/gradle-8.7/bin/gradle.bat assembleRelease   # 正式签名的包
```

产物：`app/build/outputs/apk/{debug,release}/`

## 签名

`keystore.properties`（不入库）指向 `yingyin-release.jks`。这两个文件要另外备份，
丢了就没法给已装用户发升级包（只能让他们卸载重装）。

## 代码结构

| 文件 | 作用 |
|---|---|
| `Data.kt` | 数据层：订阅拉取、CMS(苹果V10) 接口、多源聚合并按片名去重、线路排序、本地缓存 |
| `Screens.kt` | 首页网格 / 搜索 / 详情（线路+选集）/ 我的 |
| `Player.kt` | ExoPlayer 播放页，失败自动换下一条线路 |
| `MainActivity.kt` | 单 Activity + Compose 路由 |

## 关键设计

- **一个 APK 两种形态**：`uiMode == TELEVISION` 时切 5 列大卡片布局；Manifest 里已加 `LEANBACK_LAUNCHER`。
- **线路优先级**：多数 CMS 每个源有两条线路，第一条是网页播放页（打不开），第二条才是 m3u8。
  `parseDetail` 按「直链占比」给线路排序，播放器再按同样规则排候选地址。
- **自动换源**：播放出错时依次尝试其它线路的同集地址，全部失败才报错。
- **订阅**：`BuildConfig.SUB_URLS`（逗号分隔多个地址，做回退）→ 拉 `{"sources":[{"key","name","api"}]}`；
  拉不到就用内置的 8 个源，所以离线也不会白屏。
