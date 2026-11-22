# 源清单对照：网页端 21+1 个 → App 端为什么只有 8 个（现已 12 个）

> 数据来源：`C:\AI\demo\yingyin\data\sources.json`（22 条）、`data\health_v2.json`（10 秒体检，2026-11-22 19:46）、`_shots\health30.json`（30 秒复测，2026-11-22 21:0x）

## 一、先把数量对上

| 你在网页项目里给过的东西 | 实际是什么 | 数量 |
|---|---|---|
| `data/sources.json` | 采集站（CMS 接口），21 个公网站 + 1 个本机服务 | 22 |
| `data/curated_resolved.json` | **精选片单**，不是源：电影/电视剧/动漫/纪录片 各 12 部，带已解析好的播放地址 | 48 部 |
| 「小鸡看看」 | 你电脑上跑的 `xjcms.js` 服务，把网站包成 CMS 接口 + `xj://` 播放地址由 `server.js` 实时换链 | 1 个本地服务 |

所以「源」是 22 条，不是几百个；48 部是片单，和源是两回事。

## 二、8 个是怎么来的：体检口径太严

App 第一版的内置源，取的是「**列表 + 详情 + 播放三项全过**」的站。
当时体检用的超时是 **10 秒**：10 / 22 能出列表，只有 8 个三项全过。

今天用 **30 秒**重测同一批源，结果是：**14 个能出列表，12 个三项全过**。

差距不是「源死了」，是「源慢」——比如 `api.guangsuapi.com` 3.5 秒、`hhzyapi.com` 10 秒、`subocj.com` 20 秒才吐完第一页，10 秒口径直接把后两个判死。

## 三、逐源对照（30 秒口径，2026-11-22 复测）

| 源 | 接口 | 列表 | 详情 | 播放 | 列表耗时 | 结论 |
|---|---|---|---|---|---|---|
| 猫眼资源 | `api.maoyanapi.top/api.php/provide/vod` | ✓ | ✓ | ✓ | 738 ms | ✅ 已接入 |
| 360资源 | `360zy.com/api.php/provide/vod` | ✓ | ✓ | ✓ | 1282 ms | ✅ 已接入 |
| 爱奇艺资源 | `iqiyizyapi.com/api.php/provide/vod` | ✓ | ✓ | ✓ | 1509 ms | ✅ 已接入 |
| 红牛3 | `www.hongniuzy3.com/api.php/provide/vod` | ✓ | ✓ | ✓ | 1645 ms | ✅ 已接入 |
| 红牛资源 | `www.hongniuzy2.com/api.php/provide/vod` | ✓ | ✓ | ✓ | 1706 ms | ✅ 已接入 |
| 千里资源 | `www.qlys.cc/api.php/provide/vod` | ✓ | ✓ | ✓ | 1734 ms | ✅ 已接入 |
| 非凡资源 | `api.ffzyapi.com/api.php/provide/vod` | ✓ | ✓ | ✓ | 2508 ms | ✅ 已接入 |
| 非凡5 | `ffzy5.tv/api.php/provide/vod` | ✓ | ✓ | ✓ | 2727 ms | ✅ 已接入 |
| 光速资源 | `api.guangsuapi.com/api.php/provide/vod` | ✓ | ✓ | ✓ | 3569 ms | ✅ 已接入 |
| 最大资源 | `zuidazy.me/api.php/provide/vod` | ✓ | ✓ | ✓ | 4655 ms | ✅ 已接入 |
| 豪华资源 | `hhzyapi.com/api.php/provide/vod` | ✓ | ✓ | ✓ | 10247 ms | ✅ 已接入 |
| 速播资源 | `subocj.com/api.php/provide/vod` | ✓ | ✓ | ✓ | 19915 ms | ✅ 已接入 |
| 小鸡看看（本地） | `127.0.0.1:8899/api/xjcms` | ✓ | ✓ | ✗ | 8 ms | 🟡 只出列表，播放不通 |
| UU资源 | `api.ukuapi88.com/api.php/provide/vod` | ✓ | ✓ | ✗ | 1541 ms | 🟡 只出列表，播放不通 |
| 金鹰资源 | `jyzyapi.com/provide/vod/from/jinyingm3u8` | ✗ | ✗ | ✗ | 30314 ms | ❌ 30 秒内连不上 |
| 魔都2 | `www.mdzyapi.com/api.php/provide/vod` | ✗ | ✗ | ✗ | 33162 ms | ❌ 30 秒内连不上 |
| 金山资源 | `jszyapi.com/api.php/provide/vod` | ✗ | ✗ | ✗ | 34490 ms | ❌ 30 秒内连不上 |
| 豪华资源(旧) | `haohuazy.com/api.php/provide/vod` | ✗ | ✗ | ✗ | 37469 ms | ❌ 30 秒内连不上 |
| BF资源 | `bfzy2.tv/api.php/provide/vod` | ✗ | ✗ | ✗ | 37710 ms | ❌ 30 秒内连不上 |
| 魔都资源 | `caiji.moduapi.cc/api.php/provide/vod` | ✗ | ✗ | ✗ | 39576 ms | ❌ 30 秒内连不上 |
| 百度资源 | `api.apibdzy.com/api.php/provide/vod` | ✗ | ✗ | ✗ | 48903 ms | ❌ 30 秒内连不上 |
| 虎牙资源 | `huyazy.net/api.php/provide/vod` | ✗ | ✗ | ✗ | 63627 ms | ❌ 30 秒内连不上 |

## 四、小鸡看看为什么进不了 App

1. 它的地址是 `http://127.0.0.1:8899/api/xjcms` —— 这是**你电脑本机**的回环地址。手机装上 App 后，`127.0.0.1` 指的是手机自己，永远连不上。
2. 它的播放地址是 `xj://...`，要靠电脑上的 `server.js` 实时换成真实 m3u8，且链接 **10–60 秒就失效**。App 拿到 `xj://` 没法直接播。
3. 要用它，等于把 `xjcms.js` + `server.js` 的解链逻辑搬进 App，还得跟着网站改。这不是「接一个源」，是「再做一个后端」，所以第一版没做。

## 五、现在的状态

- App 内置源与 `docs/sub.json` 已同步为 **12 个**（`sub.json` 是唯一入口，改它就等于改所有已装 App 的源）。
- 首页改成**边拉边显示**：快源 1–2 秒先出结果，慢源陆续补进来，不再等最慢的那个。
- 30 秒都连不上的 8 个站先不进清单；等它们恢复或换域名，改 `sub.json` 加回来即可，不用发新版本。

## 六、以后加源的规矩

1. 先在电脑上跑一遍体检（列表 / 详情 / 播放三项），30 秒超时；
2. 三项全过的写进 `docs/sub.json`；
3. `git push`，已装 App 下次启动自动同步，**不用重新打包发 APK**。
