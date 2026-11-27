# 影视仓 App

Android 端影视聚合播放器，包名 `app.yingyin.player`。

## 运行方式

- **独立模式（默认）**：手机直接访问 21 个采集源，首页、分类、搜索、详情、播放都不需要家里电脑。
- **家庭增强模式（可选）**：如果手机和 PC 在同一个 Wi-Fi，App 启动时会用 1.5 秒探测 PC 的 `http://192.168.5.2:8899`。探测成功才使用 PC 的精选片单、分类和小鸡 4K 线路；失败自动完全走本地，不拖慢。
- 朋友手机没有 PC、IP 变化、PC 关机，都走独立模式。

## 关键行为

- 搜索：21 个源并发，单源最多 3.6 秒，先回来的先显示；同一关键词缓存 10 分钟；新搜索会取消旧搜索。
- 分类：按 `docs/sub.json` 的 `map` 抓每个源真实子分类，最多同时抓 3 个子类；筛选支持类型/地区/年份。
- 详情：同一部片的多源同时展开，合并线路；每条线路探测第一集，健康的按延迟排前面。
- 播放：跳过体检坏线；缓冲 8 秒仍为 0 秒自动换下一条。
- 私密：默认关闭，首页/分类/搜索/详情都不显示；设置里手动打开才出现。

## 构建

```bash
cd android
JAVA_HOME='C:/Tools/jdk-21' ANDROID_HOME='C:/Tools/android-sdk' \
  C:/Tools/gradle-8.7/bin/gradle.bat assembleRelease --console=plain
```

APK：`android/app/build/outputs/apk/release/app-release.apk`

## 发版检查

```bash
node tools/qa/standalone_sources.mjs
python tools/qa/phone_smoke.py
```

详见 `docs/SOURCES.md`。
