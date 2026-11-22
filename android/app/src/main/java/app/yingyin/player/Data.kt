package app.yingyin.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Source(val key: String, val name: String, val api: String)

data class Item(
    val id: String,
    val sid: String,
    val name: String,
    val pic: String = "",
    val year: String = "",
    val remarks: String = "",
    val type: String = "",
    val area: String = "",
    val score: Double = 0.0,
    val srcs: Int = 1,
    val alts: List<Pair<String, String>> = emptyList()
)

data class Episode(val name: String, val url: String)
data class Line(val from: String, val eps: List<Episode>)
data class Detail(val item: Item, val lines: List<Line>, val desc: String, val actors: String = "")

data class UpdateInfo(val versionCode: Int, val versionName: String, val apk: String, val notes: String)

fun looksMedia(url: String): Boolean {
    val u = url.lowercase()
    return u.contains(".m3u8") || u.contains(".mp4") || u.contains(".flv") || u.contains(".mkv")
}

/** 内置种子源：与 docs/sub.json 同步（2026-11-22 复测 30s 超时，12 个可用站） */
val SEED: List<Source> = listOf(
    Source("maoyan", "猫眼资源", "https://api.maoyanapi.top/api.php/provide/vod"),
    Source("360", "360资源", "https://360zy.com/api.php/provide/vod"),
    Source("iqy", "爱奇艺资源", "https://iqiyizyapi.com/api.php/provide/vod"),
    Source("hn3", "红牛3", "https://www.hongniuzy3.com/api.php/provide/vod"),
    Source("hn2", "红牛资源", "https://www.hongniuzy2.com/api.php/provide/vod"),
    Source("qlys", "千里资源", "https://www.qlys.cc/api.php/provide/vod"),
    Source("ffzy", "非凡资源", "https://api.ffzyapi.com/api.php/provide/vod"),
    Source("ffzy5", "非凡5", "http://ffzy5.tv/api.php/provide/vod"),
    Source("gs", "光速资源", "http://api.guangsuapi.com/api.php/provide/vod"),
    Source("zdy", "最大资源", "http://zuidazy.me/api.php/provide/vod"),
    Source("hhzy", "豪华资源", "https://hhzyapi.com/api.php/provide/vod"),
    Source("sub", "速播资源", "https://subocj.com/api.php/provide/vod")
)

private const val UA =
    "Mozilla/5.0 (Linux; Android 11; M2007J3SC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

fun norm(s: String): String =
    s.replace(Regex("[\\s·:：!！?？,，.。'’\"“”()（）\\[\\]【】《》\\-—_/\\\\|]"), "").lowercase()

object Cms {
    fun url(api: String, t: String = "", pg: Int = 1, wd: String = "", ids: String = ""): String {
        val sb = StringBuilder(api)
        sb.append(if (api.contains("?")) "&" else "?")
        sb.append("ac=").append(if (ids.isNotEmpty()) "detail" else "videolist")
        if (ids.isNotEmpty()) sb.append("&ids=").append(ids) else sb.append("&pg=").append(pg)
        if (t.isNotEmpty()) sb.append("&t=").append(t)
        if (wd.isNotEmpty()) sb.append("&wd=").append(URLEncoder.encode(wd, "UTF-8"))
        return sb.toString()
    }

    fun parseList(json: String?, sid: String): List<Item> {
        if (json == null) return emptyList()
        return try {
            val arr = JSONObject(json).optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val j = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = j.optString("vod_id")
                val name = j.optString("vod_name")
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                Item(
                    id = id,
                    sid = sid,
                    name = name,
                    pic = j.optString("vod_pic"),
                    year = j.optString("vod_year"),
                    remarks = j.optString("vod_remarks"),
                    type = j.optString("type_name"),
                    area = j.optString("vod_area"),
                    score = j.optString("vod_score").toDoubleOrNull() ?: 0.0
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseDetail(json: String?, sid: String): Detail? {
        if (json == null) return null
        return try {
            val arr = JSONObject(json).optJSONArray("list") ?: return null
            if (arr.length() == 0) return null
            val j = arr.getJSONObject(0)
            val item = Item(
                id = j.optString("vod_id"),
                sid = sid,
                name = j.optString("vod_name"),
                pic = j.optString("vod_pic"),
                year = j.optString("vod_year"),
                remarks = j.optString("vod_remarks"),
                type = j.optString("type_name"),
                area = j.optString("vod_area"),
                score = j.optString("vod_score").toDoubleOrNull() ?: 0.0
            )
            val froms = j.optString("vod_play_from").split("\$\$\$")
            val urls = j.optString("vod_play_url").split("\$\$\$")
            val lines = ArrayList<Line>()
            for (k in froms.indices) {
                val raw = urls.getOrNull(k) ?: continue
                if (raw.isBlank()) continue
                val eps = raw.split("#").mapNotNull { seg ->
                    if (seg.isBlank()) return@mapNotNull null
                    val p = seg.split("\$", limit = 2)
                    val nm = p.getOrNull(0)?.trim().orEmpty()
                    val u = p.getOrNull(1)?.trim().orEmpty()
                    if (!u.startsWith("http")) null else Episode(nm.ifBlank { "播放" }, u)
                }
                if (eps.isNotEmpty()) {
                    lines.add(Line(froms[k].ifBlank { "线路${k + 1}" }, eps))
                }
            }
            val desc = j.optString("vod_content").replace(Regex("<[^>]+>"), "").trim()
            // 优先把直链 m3u8/mp4 的线路排在前面：很多源的前一条线路是网页播放页，播放器打不开
            val ordered = lines.sortedByDescending { l ->
                l.eps.count { looksMedia(it.url) }.toFloat() / l.eps.size.coerceAtLeast(1)
            }
            Detail(item, ordered, desc, j.optString("vod_actor"))
        } catch (e: Exception) {
            null
        }
    }
}

object Repo {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(22, TimeUnit.SECONDS)
        .callTimeout(26, TimeUnit.SECONDS)
        .build()

    var sources by androidx.compose.runtime.mutableStateOf(SEED)
        private set

    var subState by androidx.compose.runtime.mutableStateOf("内置源 · 12 个")
        private set
    var homeItems by androidx.compose.runtime.mutableStateOf<List<Item>>(emptyList())
        private set
    var homeLoading by androidx.compose.runtime.mutableStateOf(false)
        private set
    var update by androidx.compose.runtime.mutableStateOf<UpdateInfo?>(null)
        private set
    private var homeCache: List<Item> = emptyList()
    private var homeCacheAt: Long = 0L
    private var cacheCtx: Context? = null

    /** 启动时读本地缓存，第二次开 App 秒开 */
    fun init(ctx: Context) {
        cacheCtx = ctx.applicationContext
        val sp = ctx.getSharedPreferences("yingyin", Context.MODE_PRIVATE)
        val raw = sp.getString("home", null) ?: return
        homeCacheAt = sp.getLong("homeAt", 0L)
        homeCache = try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val j = arr.optJSONObject(i) ?: return@mapNotNull null
                Item(
                    id = j.optString("id"), sid = j.optString("sid"), name = j.optString("name"),
                    pic = j.optString("pic"), year = j.optString("year"), remarks = j.optString("remarks"),
                    type = j.optString("type"), area = j.optString("area"),
                    score = j.optDouble("score", 0.0), srcs = j.optInt("srcs", 1)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun cachedHome(): List<Item> = homeCache

    private fun saveCache(ctx: Context, items: List<Item>) {
        try {
            val arr = org.json.JSONArray()
            items.forEach { it ->
                arr.put(JSONObject().apply {
                    put("id", it.id); put("sid", it.sid); put("name", it.name)
                    put("pic", it.pic); put("year", it.year); put("remarks", it.remarks)
                    put("type", it.type); put("area", it.area)
                    put("score", it.score); put("srcs", it.srcs)
                })
            }
            ctx.getSharedPreferences("yingyin", Context.MODE_PRIVATE).edit()
                .putString("home", arr.toString()).putLong("homeAt", homeCacheAt).apply()
        } catch (e: Exception) { }
    }

    private fun text(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (t: Throwable) {
        null
    }

    private fun parseSub(raw: String): List<Source>? {
        return try {
            val arr = JSONObject(raw).optJSONArray("sources") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val j = arr.optJSONObject(i) ?: return@mapNotNull null
                val api = j.optString("api")
                if (api.isBlank()) null else Source(
                    j.optString("key", "s$i"),
                    j.optString("name", "源$i"),
                    api
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sync(urls: List<String>): Boolean = withContext(Dispatchers.IO) {
        for (u in urls) {
            val raw = text(u) ?: continue
            val list = parseSub(raw) ?: continue
            if (list.isNotEmpty()) {
                sources = list
                subState = "远程订阅 · ${list.size} 个源"
                homeCacheAt = 0L   // 源变了，首页缓存作废
                return@withContext true
            }
        }
        subState = "内置源 · ${sources.size} 个（订阅不可达）"
        false
    }

    suspend fun checkUpdate(urls: List<String>): UpdateInfo? = withContext(Dispatchers.IO) {
        for (u in urls) {
            val raw = text(u) ?: continue
            try {
                val j = JSONObject(raw)
                val vc = j.optInt("versionCode", 0)
                if (vc > BuildConfig.VERSION_CODE) {
                    val info = UpdateInfo(
                        vc, j.optString("versionName"), j.optString("apk"), j.optString("notes")
                    )
                    update = info
                    return@withContext info
                }
                return@withContext null
            } catch (e: Exception) {
            }
        }
        null
    }

    suspend fun home(t: String = ""): List<Item> = coroutineScope {
        if (t.isBlank() && homeCache.isNotEmpty() && System.currentTimeMillis() - homeCacheAt < 10 * 60_000L) {
            homeItems = homeCache
            return@coroutineScope homeCache
        }
        val src = sources
        val acc = java.util.Collections.synchronizedList(mutableListOf<Item>())
        homeLoading = true
        val jobs = src.map { s ->
            async(Dispatchers.IO) {
                val part = Cms.parseList(text(Cms.url(s.api, t = t)) ?: "", s.key)
                if (part.isNotEmpty()) {
                    acc.addAll(part)
                    homeItems = merge(acc.toList())   // 有多少先显示多少
                }
            }
        }
        jobs.awaitAll()
        homeLoading = false
        val merged = merge(acc.toList())
        homeItems = merged
        if (t.isBlank() && merged.isNotEmpty()) {
            homeCache = merged
            homeCacheAt = System.currentTimeMillis()
            cacheCtx?.let { saveCache(it, merged) }
        }
        merged
    }

    suspend fun search(wd: String): List<Item> = coroutineScope {
        val snapshot = sources
        val parts = snapshot.map { s ->
            async(Dispatchers.IO) { Cms.parseList(text(Cms.url(s.api, wd = wd)), s.key) }
        }
        merge(parts.awaitAll().flatten())
    }

    fun merge(all: List<Item>): List<Item> {
        val groups = LinkedHashMap<String, MutableList<Item>>()
        for (one in all) {
            val k = norm(one.name) + "|" + one.year.take(4)
            groups.getOrPut(k) { mutableListOf() }.add(one)
        }
        return groups.values.map { list ->
            val head = list.maxByOrNull { it.score } ?: list.first()
            head.copy(
                srcs = list.map { it.sid }.distinct().size,
                alts = list.map { it.sid to it.id }
            )
        }.sortedWith(compareByDescending<Item> { it.srcs }.thenByDescending { it.score })
    }

    suspend fun detail(item: Item): Detail? = withContext(Dispatchers.IO) {
        val byKey = sources.associateBy { it.key }
        val tries = (listOf(item.sid to item.id) + item.alts).distinct()
        for ((sid, id) in tries) {
            val api = byKey[sid]?.api ?: continue
            val d = Cms.parseDetail(text(Cms.url(api, ids = id)), sid)
            if (d != null && d.lines.isNotEmpty()) return@withContext d
        }
        for (s in sources) {
            if (s.key == item.sid) continue
            val hit = Cms.parseList(text(Cms.url(s.api, wd = item.name)), s.key)
                .firstOrNull { norm(it.name) == norm(item.name) } ?: continue
            val d = Cms.parseDetail(text(Cms.url(s.api, ids = hit.id)), s.key)
            if (d != null && d.lines.isNotEmpty()) return@withContext d
        }
        null
    }
}
