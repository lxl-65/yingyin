# 影视仓（App）

安卓影视聚合播放器：一个 APK，自己维护一份源清单，朋友装上就能用。

> 命名：产品名 **影视仓**，App 名 **影视仓**，Web 端叫 **放映厅**，代码仓 `yingyin-app`。
> 安卓桌面图标显示的就是「影视仓」（`android:label`）。规范全文见文档仓 `NAMING.md`。
> 旧名「放映厅」曾用于本 App，现只指 Web 端。

- **手机端**：Kotlin + Jetpack Compose + Media3(ExoPlayer)
- **订阅**：`docs/sub.json`（GitHub Pages 托管，App 启动自动拉取，失败回退内置源）
- **分发**：GitHub Releases 放 APK，`docs/index.html` 是下载页

## 构建

```bash
export JAVA_HOME='C:\Tools\jdk-21'
export ANDROID_HOME='C:\Tools\android-sdk'
C:/Tools/gradle-8.7/bin/gradle.bat assembleRelease
```

签名配置在 `android/keystore.properties`（不入库），见 `android/README.md`。

## 订阅格式

```json
{
  "version": 1,
  "updated": "2026-11-22 21:00",
  "sources": [
    {"key": "hongniuzy2", "name": "hongniuzy2.com", "api": "https://www.hongniuzy2.com/api.php/provide/vod"}
  ]
}
```

接口按苹果 CMS V10 规范：`?ac=videolist&pg=1`、`?ac=videolist&wd=关键词`、`?ac=detail&ids=ID`。

## 说明

- 线路排序：多数 CMS 每个源有两条线路，第一条是网页播放页，第二条才是 m3u8；客户端按直链占比排序并自动换源。
- 仅聚合公开接口，不存储、不传播影视内容。
