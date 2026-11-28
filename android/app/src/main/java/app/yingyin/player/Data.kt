package app.yingyin.player

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class Source(
    val key: String,
    val name: String,
    val api: String,
    /** 采集站的分类 id，例如 movie -> "1"、series -> "2"。没有它也能搜索，只是分类页只能走本地兜底。 */
    val cats: Map<String, String> = emptyMap(),
    /** 订阅里记录的最近一次响应耗时，数字越小越先参与首屏。 */
    val ms: Int = 0,
    /** 采集站的真实子分类表，格式 "1:movie,2:series,..."；分类页按它一次抓 3 个子类。 */
    val typeMap: String = "",
    /** true = 用户在本机添加的源，可在 App 里编辑/删除；内置源只读。 */
    val custom: Boolean = false
) {
    fun subtypes(cat: String): List<String> {
        if (typeMap.isBlank()) return emptyList()
        return typeMap.split(',').mapNotNull { row ->
            val p = row.split(':', limit = 2)
            if (p.size == 2 && p[1] == cat) p[0] else null
        }.distinct()
    }
}

data class EntryRef(val s: Int, val id: String, val name: String = "")

/** 测试自定义源的结果：不抛异常，直接把可读结论给界面。 */
data class SourceTest(val ok: Boolean, val message: String, val ms: Long = 0L)

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
    val alts: List<Pair<String, String>> = emptyList(),
    /** 网站 /api/list、/api/search 返回的多源条目；有它就走服务器详情。 */
    val entries: List<EntryRef> = emptyList(),
    val lang: String = "",
    /** 合并语种版本时记录 sid:id -> 语种，详情页给线路加“粤语/国语”前缀。 */
    val altLangs: Map<String, String> = emptyMap()
)

data class Episode(val name: String, val url: String)
data class Line(
    val from: String,
    val eps: List<Episode>,
    val quality: String = "",
    val latency: Int = 0,
    val bad: Boolean = false,
    val recommended: Boolean = false
)
data class Detail(val item: Item, val lines: List<Line>, val desc: String, val actors: String = "")

data class UpdateInfo(val versionCode: Int, val versionName: String, val apk: String, val notes: String)

fun looksMedia(url: String): Boolean {
    val u = url.lowercase()
    return u.contains(".m3u8") || u.contains(".mp4") || u.contains(".flv") || u.contains(".mkv") || u.contains("/xjgo?")
}

/**
 * 内置种子源：和 docs/sub.json 同一套 21 个源（2026-11-23 从 PC 端 22 源清单里去掉只能本机跑的小鸡）。
 * 订阅拉取成功会用远程清单覆盖；拉取失败也至少能用这一份离线清单。
 */
val SEED: List<Source> = listOf(
    Source("s0", "光速资源", "http://api.guangsuapi.com/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "4", "variety" to "3", "short" to "31"), 3569, "1:movie,2:series,3:variety,4:anime,6:movie,7:series,8:movie,9:movie,10:series,11:movie,12:movie,13:series,14:series,15:series,16:series,20:anime,21:series,22:series,23:series,24:doc,25:movie,31:short,32:movie,37:variety,38:variety,39:variety,40:variety,41:anime,42:anime,43:anime,49:short,51:short,52:series"),
    Source("s1", "非凡5", "http://ffzy5.tv/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "4", "variety" to "3", "short" to "36"), 2727, "1:movie,2:series,3:variety,4:anime,6:movie,7:series,8:movie,9:movie,10:movie,11:series,12:movie,13:series,14:series,15:series,16:series,20:doc,21:series,22:series,23:series,24:series,25:variety,26:variety,27:variety,28:variety,29:anime,30:anime,31:anime,32:anime,33:anime,34:movie,36:short"),
    Source("s2", "360资源", "https://360zy.com/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "4", "variety" to "3", "doc" to "27"), 1282, "1:movie,2:series,3:variety,4:anime,5:movie,6:movie,7:series,8:movie,9:movie,10:movie,11:series,12:movie,13:series,14:series,15:series,16:series,20:movie,22:movie,23:movie,24:movie,25:movie,26:movie,27:doc,28:movie,29:movie,30:series,31:series,32:series,33:series,34:variety,35:variety,36:variety,37:variety,38:anime,39:anime,40:anime,45:movie,46:short,51:short"),
    Source("s3", "百度资源", "https://api.apibdzy.com/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "4", "variety" to "3", "doc" to "20"), 0, "1:movie,2:series,3:variety,4:anime,6:movie,7:series,8:movie,9:movie,10:movie,11:series,12:movie,13:series,14:series,15:series,16:series,17:series,18:series,19:series,20:doc,23:series,25:variety,26:variety,27:variety,28:variety,29:anime,30:anime,31:anime,39:movie,44:anime,45:anime,51:movie,54:short,63:anime,65:short"),
    Source("s4", "非凡资源", "https://api.ffzyapi.com/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "4", "variety" to "3", "short" to "36"), 2508, "1:movie,2:series,3:variety,4:anime,6:movie,7:series,8:movie,9:movie,10:movie,11:series,12:movie,13:series,14:series,15:series,16:series,20:doc,21:series,22:series,23:series,24:series,25:variety,26:variety,27:variety,28:variety,29:anime,30:anime,31:anime,32:anime,33:anime,34:movie,36:short"),
    Source("s5", "猫眼资源", "https://api.maoyanapi.top/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "4", "variety" to "3", "doc" to "27"), 738, "1:movie,2:series,3:variety,4:anime,5:movie,6:movie,7:series,8:movie,9:movie,10:movie,11:series,12:movie,13:series,14:series,15:series,16:series,20:movie,22:movie,23:movie,24:movie,25:movie,26:movie,27:doc,28:movie,29:movie,30:series,31:series,32:series,33:series,34:variety,35:variety,36:variety,37:variety,38:anime,39:anime,40:anime,45:movie,46:short,47:short,48:short,49:short,50:short,51:short,52:short,53:short,54:movie,55:anime"),
    Source("s6", "暴风资源", "https://bfzy2.tv/api.php/provide/vod", mapOf("movie" to "20", "series" to "30", "anime" to "39", "variety" to "45", "doc" to "28"), 0, "20:movie,21:movie,22:series,23:movie,24:movie,25:movie,26:series,27:movie,28:doc,29:movie,30:series,31:series,32:series,33:series,34:series,35:series,36:series,37:series,38:series,39:anime,40:anime,41:anime,42:anime,43:anime,44:anime,45:variety,46:variety,47:variety,48:variety,49:variety,50:movie,52:movie,58:short,68:series"),
    Source("s7", "魔都资源", "https://caiji.moduapi.cc/api.php/provide/vod", mapOf("movie" to "7", "series" to "8", "variety" to "9", "short" to "38"), 0, ""),
    Source("s8", "豪华资源", "https://hhzyapi.com/api.php/provide/vod", mapOf("movie" to "2", "series" to "1", "anime" to "17", "variety" to "27", "short" to "38"), 10247, "1:series,2:movie,3:series,4:series,5:series,6:series,7:series,8:movie,9:movie,10:movie,11:series,12:movie,13:movie,14:series,15:movie,16:doc,17:anime,20:series,23:movie,24:anime,25:anime,26:anime,27:variety,28:series,30:variety,31:variety,32:variety,33:variety,34:movie,35:movie,36:movie,37:movie,38:short,39:movie,50:short,52:short,53:series"),
    Source("s9", "爱奇艺资源", "https://iqiyizyapi.com/api.php/provide/vod", mapOf("movie" to "7", "series" to "8", "anime" to "40", "variety" to "9", "short" to "38"), 1509, "1:anime,2:anime,3:anime,4:anime,5:anime,6:anime,7:movie,8:series,9:variety,10:movie,11:series,12:movie,13:movie,14:movie,15:series,16:movie,17:movie,18:movie,19:movie,20:movie,21:movie,22:movie,23:movie,24:doc,25:movie,26:series,27:series,28:series,29:series,30:series,31:series,32:series,33:series,34:variety,35:variety,36:variety,37:variety,38:short,39:movie,40:anime,43:series"),
    Source("s10", "极速资源", "https://jszyapi.com/api.php/provide/vod", mapOf("movie" to "2", "series" to "1", "anime" to "17", "variety" to "27", "short" to "38"), 0, "1:series,2:movie,3:series,4:series,5:series,6:series,7:series,8:movie,9:movie,10:movie,11:series,12:movie,13:movie,14:series,15:movie,16:doc,17:anime,20:series,23:movie,24:anime,25:anime,26:anime,27:variety,28:series,30:variety,31:variety,32:variety,33:variety,34:movie,35:movie,36:movie,37:movie,38:short,39:movie,50:short,53:short,54:series"),
    Source("s11", "红牛资源", "https://www.hongniuzy2.com/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "4", "variety" to "3", "doc" to "19", "short" to "30"), 1706, "1:movie,2:series,3:variety,4:anime,5:movie,6:series,7:movie,8:movie,9:movie,10:series,11:movie,12:series,13:series,14:series,15:series,16:series,17:series,18:series,19:doc,20:anime,21:movie,30:short,31:movie,36:anime,37:anime,38:anime,39:variety,40:variety,41:variety,42:variety,48:short,50:short,51:series"),
    Source("s12", "红牛3", "https://www.hongniuzy3.com/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "4", "variety" to "3", "doc" to "19", "short" to "30"), 1645, "1:movie,2:series,3:variety,4:anime,5:movie,6:series,7:movie,8:movie,9:movie,10:series,11:movie,12:series,13:series,14:series,15:series,16:series,17:series,18:series,19:doc,20:anime,21:movie,30:short,31:movie,36:anime,37:anime,38:anime,39:variety,40:variety,41:variety,42:variety,48:short,50:short,51:series"),
    Source("s13", "魔都2", "https://www.mdzyapi.com/api.php/provide/vod", mapOf("movie" to "7", "series" to "8", "variety" to "9", "short" to "38"), 0, ""),
    Source("s14", "千里资源", "https://www.qlys.cc/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "4", "variety" to "5", "short" to "3"), 1734, "1:movie,2:series,3:short,4:anime,5:variety,6:series,7:series,8:series,9:series,10:movie,11:movie,12:movie,13:movie,14:movie,15:series,16:series,17:doc,22:series,23:series,24:series"),
    Source("s15", "最大资源", "http://zuidazy.me/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "variety" to "3", "anime" to "4", "doc" to "20", "short" to "54"), 4655, "1:movie,2:series,3:variety,4:anime,6:movie,7:movie,8:movie,9:movie,10:movie,11:movie,12:movie,13:series,14:series,15:series,16:series,17:series,18:series,19:series,20:doc,23:series,25:variety,26:variety,27:variety,28:variety,29:anime,30:anime,31:anime,39:anime,44:anime,45:anime,54:short,62:movie,63:anime,65:short,66:short,68:short,69:short,70:movie,71:movie,75:short"),
    Source("s16", "金鹰资源", "http://jyzyapi.com/provide/vod/from/jinyingm3u8", mapOf("series" to "1", "movie" to "2", "doc" to "16", "anime" to "17", "variety" to "27", "short" to "30"), 0, "1:series,2:movie,3:series,4:series,5:series,6:series,7:series,9:movie,10:movie,11:movie,12:movie,13:movie,14:movie,15:movie,16:doc,17:anime,20:series,23:anime,24:anime,25:anime,26:anime,27:variety,28:series,30:short,36:variety,37:variety,38:variety,39:variety,40:short,41:short,42:short,43:short,44:short,45:short,46:short,48:short"),
    Source("s17", "虎牙资源", "https://huyazy.net/api.php/provide/vod", mapOf("series" to "1", "movie" to "2", "doc" to "16", "anime" to "17", "variety" to "27", "short" to "30"), 0, "1:series,2:movie,3:series,4:series,5:series,6:series,7:series,9:movie,10:movie,11:movie,12:movie,13:movie,14:movie,15:movie,16:doc,17:anime,20:series,23:anime,24:anime,25:anime,26:anime,27:variety,28:series,30:short,38:variety,39:variety,40:variety,41:variety,42:short,43:short,44:short,45:short,46:short,47:short,48:short,50:short"),
    Source("s18", "速播资源", "https://subocj.com/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "anime" to "3", "variety" to "4", "doc" to "5", "short" to "27"), 19915, "1:movie,2:series,3:anime,4:variety,5:doc,6:movie,7:movie,8:movie,9:movie,10:movie,11:movie,12:movie,13:anime,14:series,15:series,16:series,17:series,18:series,20:series,21:series,24:anime,25:anime,26:anime,27:short,33:variety,34:variety,35:variety,36:variety,37:short,38:short,39:short,40:short,41:short,42:short,43:short,45:short"),
    Source("s19", "优酷资源", "https://api.ukuapi88.com/api.php/provide/vod", mapOf("movie" to "1", "series" to "2", "variety" to "3", "anime" to "4", "doc" to "24", "short" to "32"), 0, "1:movie,2:series,3:variety,4:anime,6:movie,7:movie,8:movie,9:movie,10:movie,11:movie,12:movie,13:series,14:series,15:series,16:series,20:anime,21:series,22:series,23:series,24:doc,32:short,33:movie,34:movie"),
    Source("s20", "豪虎资源", "https://haohuazy.com/api.php/provide/vod", mapOf("series" to "1", "movie" to "2", "doc" to "16", "anime" to "17", "variety" to "27", "short" to "38"), 0, "1:series,2:movie,3:series,4:series,5:series,6:series,7:series,9:movie,10:movie,11:movie,12:movie,13:movie,14:movie,15:movie,16:doc,17:anime,20:series,23:anime,24:anime,25:anime,26:anime,27:variety,28:series,30:variety,31:variety,32:variety,33:variety,34:movie,35:movie,36:movie,37:movie,38:short,45:short,46:short,47:short,48:short,49:short,50:short,51:short,53:short")
)
private const val UA =
    "Mozilla/5.0 (Linux; Android 11; M2007J3SC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

/** 家里那台跑 8899 的 PC。只在家用同 Wi-Fi 时尝试；不可达就静默跳过，不拖慢 12 条 API 源。 */
private const val XJ_BASE = "http://192.168.5.2:8899"

fun norm(s: String): String =
    s.replace(Regex("[\\s·:：!！?？,，.。'’\"“”()（）\\[\\]【】《》\\-—_/\\\\|]"), "").lowercase()

/** 去掉片名末尾的语种版本，用来把「同一部片的粤语/国语/英语版」并成一张卡。 */
private val LANG_SUFFIX = Regex(
    "(?i)(?:[\\s·:：\\-—_]*(?:粤语|国语|普通话|英语|英文|日语|韩语|中字|中文字幕|双语|原声|国配|粤配)(?:版|配音|音轨|字幕)?)+\\s*$"
)

fun baseTitle(name: String): String = name.replace(LANG_SUFFIX, "").trim()

fun langOf(name: String): String = when {
    Regex("粤语|canto", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "粤语"
    Regex("国语|国配|mandarin", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "国语"
    Regex("普通话", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "普通话"
    Regex("英语|英文|english", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "英语"
    Regex("日语|japanese", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "日语"
    Regex("韩语|korean", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "韩语"
    else -> ""
}

fun itemKey(item: Item): String = norm(baseTitle(item.name)) + "|" + item.year.take(4)

/**
 * 列表层把同片不同语种并成一张卡：
 * 主条目保留原片名，其它语种条目的 sid/id 收进 alts，详情页会一起展开线路。
 */
fun groupLanguageVariants(items: List<Item>): List<Item> {
    val groups = LinkedHashMap<String, MutableList<Item>>()
    items.forEach { item ->
        if (item.name.isBlank()) return@forEach
        groups.getOrPut(itemKey(item)) { mutableListOf() }.add(item)
    }
    val out = groups.values.map { group ->
        val main = group.firstOrNull { baseTitle(it.name) == it.name.trim() } ?: group.first()
        val extra = group.filter { it !== main }
            .flatMap { listOf(it.sid to it.id) + it.alts }
        val extraLangs = group.filter { it !== main }
            .mapNotNull { v ->
                val lg = langOf(v.name)
                if (lg.isBlank()) null else "${v.sid}:${v.id}" to lg
            }.toMap()
        main.copy(
            alts = (main.alts + extra)
                .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                .distinct(),
            altLangs = main.altLangs + extraLangs
        )
    }
    return out
}

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
    private const val HOME_PAGES = 2
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 搜索只等 3.6 秒：慢源不拖住整页，先回来的先显示。 */
    private val searchHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(2800, TimeUnit.MILLISECONDS)
        .callTimeout(3600, TimeUnit.MILLISECONDS)
        .build()

    /** 分类：源站多、量大，允许 5.5 秒把子分类拉全；详情用更短的 detailHttp。 */
    private val pageHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(4500, TimeUnit.MILLISECONDS)
        .callTimeout(5500, TimeUnit.MILLISECONDS)
        .build()

    /** 详情：单源最多 4 秒，点开即播优先。 */
    private val detailHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .callTimeout(4000, TimeUnit.MILLISECONDS)
        .build()

    /** 线路体检：只拉第一集的前 1KB，2 秒没回应就判坏。 */
    private val probeHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1000, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .callTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    /** 4K 家庭专线：短超时，PC 不在线时不拖慢详情页 */
    private val xjHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .callTimeout(3000, TimeUnit.MILLISECONDS)
        .build()

    var sources by androidx.compose.runtime.mutableStateOf(SEED)
        private set

    var subState by androidx.compose.runtime.mutableStateOf("内置源 · ${SEED.size} 个")
        private set
    var homeItems by androidx.compose.runtime.mutableStateOf<List<Item>>(emptyList())
        private set
    var serverGroups by androidx.compose.runtime.mutableStateOf<List<ServerGroup>>(emptyList())
        private set
    var serverCategories by androidx.compose.runtime.mutableStateOf<List<ServerCategory>>(emptyList())
        private set
    var serverSourceCount by androidx.compose.runtime.mutableStateOf(0)
        private set
    var serverFeed by androidx.compose.runtime.mutableStateOf<List<Item>>(emptyList())
        private set
    var serverHomeReady by androidx.compose.runtime.mutableStateOf(false)
        private set
    var homeLoading by androidx.compose.runtime.mutableStateOf(false)
    var loadingMore by androidx.compose.runtime.mutableStateOf(false)
    private var nextHomePage = HOME_PAGES + 1
        private set
    var update by androidx.compose.runtime.mutableStateOf<UpdateInfo?>(null)
        private set
    private var homeCache: List<Item> = emptyList()
    private var homeCacheAt: Long = 0L
    private var cacheCtx: Context? = null
    private var sourcePrefs: SharedPreferences? = null
    private var syncedSources: List<Source>? = null

    /** 本机自定义源；内置 SEED 只读，不会写回订阅。 */
    var customSources by androidx.compose.runtime.mutableStateOf<List<Source>>(emptyList())
        private set

    /** 启动时读本地缓存，第二次开 App 秒开 */
    fun init(ctx: Context) {
        cacheCtx = ctx.applicationContext
        Library.init(ctx.applicationContext)
        sourcePrefs = ctx.applicationContext.getSharedPreferences("yingyin_sources", Context.MODE_PRIVATE)
        loadCustomSources()
        sources = mergeSources(SEED)
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
        // 旧版本只缓存了每个源第一页（约 100 部），升级后强制刷新一次
        if (homeCache.size < 260) homeCacheAt = 0L
    }

    fun cachedHome(): List<Item> = groupLanguageVariants(homeCache)

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

    private fun text(url: String): String? = fetch(http, url)

    private fun fetch(client: OkHttpClient, url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (t: Throwable) {
        null
    }

    /** 搜索专用：协程取消时能真正 cancel OkHttp call，连续换关键词不会占满线程池。 */
    private suspend fun fetchAsync(client: OkHttpClient, url: String): String? =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(Request.Builder().url(url).header("User-Agent", UA).build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = try {
                        if (response.isSuccessful) response.body?.string() else null
                    } catch (_: Throwable) {
                        null
                    } finally {
                        response.close()
                    }
                    if (cont.isActive) cont.resume(body)
                }
            })
        }

    private fun parseSub(raw: String): List<Source>? {
        return try {
            val arr = JSONObject(raw).optJSONArray("sources") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val j = arr.optJSONObject(i) ?: return@mapNotNull null
                val api = j.optString("api")
                if (api.isBlank()) null else {
                    val catsObj = j.optJSONObject("cats")
                    val cats = if (catsObj == null) emptyMap() else buildMap {
                        for (k in catsObj.keys()) {
                            val v = catsObj.optString(k)
                            if (v.isNotBlank()) put(k, v)
                        }
                    }
                    val mapObj = j.optJSONObject("map")
                    val typeMap = if (mapObj == null) "" else buildList {
                        for (k in mapObj.keys()) add("$k:${mapObj.optString(k)}")
                    }.joinToString(",")
                    Source(
                        j.optString("key", "s$i"),
                        j.optString("name", "源$i"),
                        api,
                        cats,
                        j.optInt("ms", 0),
                        typeMap
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sourceKey(api: String) = api.trim().trimEnd('/').lowercase()

    /** 自定义源排最前；地址去重，避免首页重复请求。 */
    private fun mergeSources(base: List<Source>): List<Source> {
        val out = LinkedHashMap<String, Source>()
        // 用户自己加的源排在最前，方便找到、编辑和删除；同名接口以内置源为准。
        customSources.forEach { out[sourceKey(it.api)] = it }
        base.forEach { if (!out.containsKey(sourceKey(it.api))) out[sourceKey(it.api)] = it }
        return out.values.toList()
    }

    private fun loadCustomSources() {
        val raw = sourcePrefs?.getString("custom", "[]") ?: return
        customSources = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val j = arr.optJSONObject(i) ?: return@mapNotNull null
                val api = j.optString("api")
                if (api.isBlank()) return@mapNotNull null
                Source(
                    key = j.optString("key", "c$i"),
                    name = j.optString("name", "自定义源"),
                    api = api,
                    custom = true
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistCustomSources() {
        val arr = JSONArray()
        customSources.forEach { s ->
            arr.put(JSONObject().apply {
                put("key", s.key)
                put("name", s.name)
                put("api", s.api)
            })
        }
        sourcePrefs?.edit()?.putString("custom", arr.toString())?.apply()
    }

    private fun refreshSources() {
        sources = mergeSources(syncedSources ?: SEED)
        homeCacheAt = 0L
        synchronized(searchCacheLock) { searchCache.clear() }
    }

    fun normalizeSourceApi(raw: String): String {
        var s = raw.trim()
        if (s.isBlank()) return s
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }

    /**
     * 自定义源只做本机保存：内置 SEED 和远程订阅都不会被改写。
     * key == null 表示新增；返回 null 表示保存成功，否则返回给界面看的错误。
     */
    fun saveCustomSource(key: String?, name: String, api: String): String? {
        val n = name.trim()
        val a = normalizeSourceApi(api)
        if (n.isBlank()) return "名字不能为空"
        if (a.isBlank()) return "接口地址不能为空"
        val dup = sources.any {
            it.key != key && sourceKey(it.api) == sourceKey(a)
        }
        if (dup) return "这个源已经在列表里了"
        val newKey = key ?: "c${System.currentTimeMillis()}"
        val source = Source(newKey, n, a, custom = true)
        customSources = if (key == null) {
            customSources + source
        } else {
            customSources.map { if (it.key == key) source.copy(key = it.key) else it }
        }
        refreshSources()
        persistCustomSources()
        return null
    }

    fun removeCustomSource(key: String) {
        customSources = customSources.filterNot { it.key == key }
        refreshSources()
        persistCustomSources()
    }

    /** 测试源时只拉第一页；能解析出片名就算通过，不把整站抓下来。 */
    suspend fun testSource(api: String): SourceTest = withContext(Dispatchers.IO) {
        val a = normalizeSourceApi(api)
        if (a.isBlank()) return@withContext SourceTest(false, "接口地址不能为空")
        val started = System.currentTimeMillis()
        val raw = text(Cms.url(a, pg = 1))
        val ms = System.currentTimeMillis() - started
        if (raw == null) {
            return@withContext SourceTest(false, "连不上，检查地址、网络或 HTTPS", ms)
        }
        val list = Cms.parseList(raw, "test")
        if (list.isEmpty()) {
            SourceTest(false, "接口能打开，但没解析到片库；需要 Apple CMS 格式（ac=videolist）", ms)
        } else {
            SourceTest(true, "连接成功，第一页 ${list.size} 部", ms)
        }
    }

    suspend fun sync(urls: List<String>): Boolean = withContext(Dispatchers.IO) {
        for (u in urls) {
            val raw = text(u) ?: continue
            val list = parseSub(raw) ?: continue
            if (list.isNotEmpty()) {
                // 远程订阅可能还是旧版 12 源；内置 21 源不能被它缩回去。
                // 以 API 去重，远程的同名源优先（带 cats / ms），其余保留内置源。
                val merged = LinkedHashMap<String, Source>()
                SEED.forEach { merged[it.api.trim().trimEnd('/').lowercase()] = it }
                list.forEach { remote ->
                    val k = remote.api.trim().trimEnd('/').lowercase()
                    val base = merged[k]
                    merged[k] = remote.copy(
                        cats = remote.cats.ifEmpty { base?.cats ?: emptyMap() },
                        typeMap = remote.typeMap.ifBlank { base?.typeMap ?: "" },
                        ms = if (remote.ms == 0) base?.ms ?: 0 else remote.ms
                    )
                }
                syncedSources = merged.values.toList()
                sources = mergeSources(syncedSources!!)
                synchronized(searchCacheLock) { searchCache.clear() }
                subState = "远程订阅 · ${sources.size} 个源"
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

    /** 优先用 PC 放映厅：分类、推荐、搜索、详情全部同源。PC 不在线就完全走本地源。 */
    suspend fun loadServerHome(): Boolean = withContext(Dispatchers.IO) {
        if (!ServerApi.probe()) {
            serverSourceCount = 0
            serverHomeReady = false
            return@withContext false
        }
        val groups = ServerApi.curated()
        if (groups.isEmpty()) return@withContext false
        val cats = ServerApi.meta()
        val n = ServerApi.sourceCount()
        serverGroups = groups
        serverCategories = cats
        serverSourceCount = n
        serverFeed = ServerApi.list("home", 1)?.items.orEmpty()
        serverHomeReady = true
        val all = groupLanguageVariants(groups.flatMap { it.items } + serverFeed)
            .filter { Library.showPrivate || !isPrivateItem(it) }
        if (all.isNotEmpty()) homeItems = all
        true
    }

    suspend fun home(t: String = "", force: Boolean = false): List<Item> = coroutineScope {
        if (t.isBlank() && !force && homeCache.isNotEmpty() && System.currentTimeMillis() - homeCacheAt < 10 * 60_000L) {
            val safe = groupLanguageVariants(homeCache).filter { Library.showPrivate || !isPrivateItem(it) }
            homeItems = safe
            return@coroutineScope safe
        }
        val src = sources
        val acc = java.util.Collections.synchronizedList(mutableListOf<Item>())
        homeLoading = true
        val jobs = src.map { s ->
            async(Dispatchers.IO) {
                // 首页一次多拉几页，避免只显示每个源第一页的 100 多部
                val part = ArrayList<Item>()
                for (pg in 1..HOME_PAGES) {
                    val page = Cms.parseList(text(Cms.url(s.api, t = t, pg = pg)) ?: "", s.key)
                    if (page.isEmpty()) break
                    part.addAll(page)
                }
                if (part.isNotEmpty()) {
                    acc.addAll(part)
                    homeItems = merge(acc.toList()).filter { Library.showPrivate || !isPrivateItem(it) }   // 有多少先显示多少
                }
            }
        }
        jobs.awaitAll()
        homeLoading = false
        val all = merge(acc.toList())
        val merged = all.filter { Library.showPrivate || !isPrivateItem(it) }
        homeItems = merged
        nextHomePage = HOME_PAGES + 1
        if (t.isBlank() && all.isNotEmpty()) {
            // 缓存保留完整集合，展示层再按开关隐藏；否则开了私密后缓存里永远没有私密条目。
            homeCache = all
            homeCacheAt = System.currentTimeMillis()
            cacheCtx?.let { saveCache(it, all) }
        }
        merged
    }

    /** 分类“全部”的后续页：按页码直接向各源要，不复用首页第一页。 */
    suspend fun homePage(page: Int): List<Item> = supervisorScope {
        val acc = java.util.Collections.synchronizedList(mutableListOf<Item>())
        sources.map { s ->
            async(Dispatchers.IO) {
                val part = Cms.parseList(fetch(pageHttp, Cms.url(s.api, pg = page)) ?: "", s.key)
                if (part.isNotEmpty()) acc.addAll(part)
            }
        }.awaitAll()
        merge(acc.toList()).filter { Library.showPrivate || !isPrivateItem(it) }
    }

    /** 分类/首页的“加载更多”：每次再向 12 个源要 4 页，去重后追加。 */
    suspend fun loadMore(): Int = coroutineScope {
        if (loadingMore || homeLoading) return@coroutineScope 0
        loadingMore = true
        val start = nextHomePage
        val end = start + 3
        nextHomePage = end + 1
        val src = sources
        val acc = java.util.Collections.synchronizedList(mutableListOf<Item>())
        val jobs = src.map { s ->
            async(Dispatchers.IO) {
                for (pg in start..end) {
                    val page = Cms.parseList(text(Cms.url(s.api, pg = pg)) ?: "", s.key)
                    if (page.isEmpty()) break
                    acc.addAll(page)
                }
            }
        }
        jobs.awaitAll()
        val before = homeItems.size
        val merged = merge(homeItems + acc.toList())
        homeItems = merged
        homeCache = merged
        homeCacheAt = System.currentTimeMillis()
        cacheCtx?.let { saveCache(it, merged) }
        loadingMore = false
        (merged.size - before).coerceAtLeast(0)
    }

    /* ---------------- 搜索：先回来的源先显示，慢源不拖整页 ---------------- */

    private val searchCache = LinkedHashMap<String, Pair<Long, List<Item>>>()
    private val searchCacheLock = Any()

    private fun cachedSearch(key: String): List<Item>? {
        synchronized(searchCacheLock) {
            val hit = searchCache[key] ?: return null
            val ttl = if (hit.second.isEmpty()) 2 * 60_000L else 10 * 60_000L
            if (System.currentTimeMillis() - hit.first > ttl) {
                searchCache.remove(key)
                return null
            }
            return hit.second
        }
    }

    private fun putSearchCache(key: String, items: List<Item>) {
        synchronized(searchCacheLock) {
            searchCache[key] = System.currentTimeMillis() to items
            while (searchCache.size > 24) {
                val first = searchCache.keys.firstOrNull() ?: break
                searchCache.remove(first)
            }
        }
    }

    /**
     * 本地全源搜索。
     *
     * 不依赖家里的电脑。每个源独立超时，谁先回来谁先通过 onPartial 交给界面；
     * 同一个关键词 10 分钟内直接复用，不再打 21 个源。
     */
    suspend fun search(wd: String, onPartial: (List<Item>) -> Unit = {}): List<Item> {
        val q = wd.trim()
        if (q.isBlank()) return emptyList()
        val key = norm(q)
        cachedSearch(key)?.let { return it }

        val snapshot = sources.sortedWith(
            compareBy { if (it.ms <= 0) Int.MAX_VALUE else it.ms }
        )
        if (snapshot.isEmpty()) return emptyList()

        val found = supervisorScope {
            val acc = java.util.Collections.synchronizedList(mutableListOf<Item>())
            val jobs = snapshot.map { s ->
                async(Dispatchers.IO) {
                    val raw = withTimeoutOrNull(3_800) {
                        fetchAsync(searchHttp, Cms.url(s.api, wd = q))
                    }
                    val page = Cms.parseList(raw, s.key)
                    if (page.isNotEmpty()) {
                        acc.addAll(page)
                        val partial = merge(acc.toList())
                        try {
                            withContext(Dispatchers.Main) { onPartial(partial) }
                        } catch (_: Throwable) {
                            // 页面已经退出时不必让整轮搜索失败
                        }
                    }
                }
            }
            jobs.awaitAll()
            merge(acc.toList())
        }
        putSearchCache(key, found)
        return found
    }

    /**
     * 不经过 PC 的分类页：按订阅里的 cats 映射向采集站要对应分类，再在手机上去重合并。
     * 返回结构和 /api/list 一样，界面不用分成两套。
     */
    suspend fun category(
        key: String,
        page: Int = 1,
        genre: String = "",
        area: String = "",
        year: String = ""
    ): ServerPage = supervisorScope {
        val includePrivate = Library.showPrivate
        if (key == "home" || key == "all") {
            // 首页本身已经拉 1..HOME_PAGES；后续页从 HOME_PAGES+1 开始，避免“加载更多”永远重复第一页。
            val all = if (page <= 1) home() else homePage(page + HOME_PAGES - 1)
            val filtered = filterFacets(all, genre, area, year)
            return@supervisorScope ServerPage(filtered, if (all.isEmpty()) 0 else sources.size, sources.size, page, facetsFor(filtered))
        }
        if (key == "private") {
            // 私密分类绕过首页缓存：缓存可能是 showPrivate=false 时生成的，不能拿它当私密列表。
            val all = if (includePrivate) home(force = true).filter { categoryKeyOf(it) == "private" } else emptyList()
            val filtered = filterFacets(all, genre, area, year)
            return@supervisorScope ServerPage(filtered, if (all.isEmpty()) 0 else sources.size, sources.size, page, facetsFor(filtered))
        }

        val active = sources.filter { it.subtypes(key).isNotEmpty() || it.cats.containsKey(key) }.ifEmpty { sources }
        val acc = java.util.Collections.synchronizedList(mutableListOf<Item>())
        val aliveSet = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val jobs = active.flatMap { s ->
            val ids = s.subtypes(key).ifEmpty { listOfNotNull(s.cats[key]).filter { it.isNotBlank() } }
            val rotated = if (ids.isEmpty()) listOf("") else {
                val off = ((page - 1) * 3) % ids.size
                (ids.drop(off) + ids.take(off)).take(3)
            }
            rotated.map { t ->
                async(Dispatchers.IO) {
                    val raw = withTimeoutOrNull(6_000) {
                        fetch(pageHttp, Cms.url(s.api, t = t, pg = page))
                    }
                    val list = Cms.parseList(raw, s.key)
                    if (list.isNotEmpty()) {
                        aliveSet.add(s.key)
                        acc.addAll(list)
                    }
                }
            }
        }
        jobs.awaitAll()
        val safe = acc.toList().filter { includePrivate || !isPrivateItem(it) }
        val merged = filterFacets(merge(safe), genre, area, year)
        ServerPage(merged, aliveSet.size, sources.size, page, facetsFor(merged))
    }

    private fun filterFacets(items: List<Item>, genre: String, area: String, year: String): List<Item> {
        return items.filter { item ->
            (genre.isBlank() || item.type.contains(genre, ignoreCase = true)) &&
                (area.isBlank() || item.area.contains(area, ignoreCase = true)) &&
                (year.isBlank() || item.year.startsWith(year))
        }
    }

    fun facetsFor(items: List<Item>): ServerFacets {
        fun labels(select: (Item) -> String, split: Boolean = false): List<String> =
            items.asSequence()
                .map(select)
                .filter { it.isNotBlank() }
                .flatMap { raw ->
                    if (!split) sequenceOf(raw)
                    else raw.split(',', '/', '、').asSequence().map { it.trim() }
                }
                .filter { it.isNotBlank() }
                .groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key }
                .toList()

        fun genre(raw: String): String {
            val t = raw.trim()
            if (t in setOf("电影", "电影片", "电视剧", "连续剧", "剧集", "动漫", "动画", "综艺")) return ""
            return t.removeSuffix("片").ifBlank { t }
        }

        val genres = labels(select = { genre(it.type) }).filter { it.isNotBlank() }.distinct().take(16)
        val areas = labels({ it.area }, split = true).distinct().take(24)
        val years = items.map { it.year.take(4) }.filter { it.length == 4 }.distinct().sortedDescending().take(24)
        return ServerFacets(genres = genres, areas = areas, years = years)
    }

    fun merge(all: List<Item>): List<Item> {
        val groups = LinkedHashMap<String, MutableList<Item>>()
        for (one in all) {
            val k = itemKey(one)
            groups.getOrPut(k) { mutableListOf() }.add(one)
        }
        return groups.values.map { list ->
            val head = list.firstOrNull { baseTitle(it.name) == it.name.trim() }
                ?: list.maxByOrNull { it.score } ?: list.first()
            val alts = list.flatMap { listOf(it.sid to it.id) + it.alts }
                .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                .distinct()
            val langs = list.mapNotNull { one ->
                val lg = langOf(one.name)
                if (lg.isBlank()) null else "${one.sid}:${one.id}" to lg
            }.toMap()
            head.copy(
                srcs = list.map { it.sid }.distinct().size,
                alts = alts,
                altLangs = head.altLangs + langs
            )
        }.sortedWith(compareByDescending<Item> { it.srcs }.thenByDescending { it.score })
    }

    private fun xjText(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        xjHttp.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (t: Throwable) {
        null
    }

    /** 从家里 PC 的 8899 取小鸡 4K 线路；PC 不在线/不是同 Wi-Fi 就返回空，不影响 12 条 API 源。 */
    private suspend fun xjLines(item: Item): List<Line> = withContext(Dispatchers.IO) {
        if (XJ_BASE.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(item.name, "UTF-8")
        val y = item.year.take(4)
        val raw = xjText("$XJ_BASE/api/xj?wd=$q&y=$y&proxy=1") ?: return@withContext emptyList()
        try {
            val j = JSONObject(raw)
            if (!j.optBoolean("ok")) return@withContext emptyList()
            val arr = j.optJSONArray("lines") ?: return@withContext emptyList()
            val out = ArrayList<Line>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val eps = o.optJSONArray("eps") ?: continue
                val list = ArrayList<Episode>()
                for (k in 0 until eps.length()) {
                    val e = eps.optJSONObject(k) ?: continue
                    val u = e.optString("url")
                    if (!u.startsWith("http")) continue
                    list.add(Episode(e.optString("n").ifBlank { "第${k + 1}集" }, u))
                }
                if (list.isNotEmpty()) out.add(Line(o.optString("name").ifBlank { "小鸡看看" }, list))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 详情页只探测每条线路的第一集：能连上就排前面，连不上标坏，避免第一源就是死链。 */
    private suspend fun probeLines(lines: List<Line>): List<Line> = supervisorScope {
        lines.map { line ->
            async(Dispatchers.IO) {
                val ep = line.eps.firstOrNull { looksMedia(it.url) } ?: line.eps.firstOrNull()
                if (ep == null) return@async line.copy(bad = true)
                val t0 = System.currentTimeMillis()
                val ok = try {
                    val req = Request.Builder()
                        .url(ep.url)
                        .header("User-Agent", UA)
                        .apply { if (!ep.url.contains(".m3u8", true)) header("Range", "bytes=0-1023") }
                        .build()
                    probeHttp.newCall(req).execute().use { r -> r.code in 200..399 }
                } catch (_: Exception) {
                    false
                }
                line.copy(
                    latency = (System.currentTimeMillis() - t0).toInt(),
                    bad = !ok,
                    recommended = ok
                )
            }
        }.awaitAll()
    }

    private fun rankLines(lines: List<Line>): List<Line> =
        lines.sortedWith(
            compareBy<Line> { it.bad }
                .thenByDescending { it.recommended }
                .thenBy { if (it.latency <= 0) Int.MAX_VALUE else it.latency }
                .thenByDescending { if (it.quality.contains("4K", true)) 1 else 0 }
        )

    private fun combineDetails(item: Item, details: List<Detail>): Detail? {
        if (details.isEmpty()) return null
        val lines = details.flatMap { it.lines }
            .distinctBy { "${it.from}|${it.eps.size}|${it.eps.firstOrNull()?.url.orEmpty()}" }
        if (lines.isEmpty()) return null
        val desc = details.map { it.desc }.maxByOrNull { it.length }.orEmpty()
        val actors = details.map { it.actors }.maxByOrNull { it.length }.orEmpty()
        return Detail(item, rankLines(lines), desc, actors)
    }

    suspend fun detail(item: Item): Detail? = supervisorScope {
        // 家里 PC 在线时才尝试 4K 专线；朋友手机没有 PC 也不会在这里空等。
        val online = ServerApi.probe()
        val xjJob = async(Dispatchers.IO) { if (online) xjLines(item) else emptyList() }
        val byKey = sources.associateBy { it.key }

        val tries = buildList<Triple<String, String, String>> {
            if (item.sid.isNotBlank() && item.id.isNotBlank()) {
                add(Triple(item.sid, item.id, langOf(item.name)))
            }
            item.alts.forEach { (sid, id) ->
                add(Triple(sid, id, item.altLangs["$sid:$id"].orEmpty()))
            }
            item.entries.forEach { e ->
                if (e.id.isNotBlank()) {
                    sources.getOrNull(e.s)?.let { add(Triple(it.key, e.id, "")) }
                }
            }
        }.filter { it.second.isNotBlank() }.distinctBy { it.first to it.second }

        // 同一部片的多个源同时展开，再合并所有线路——不是只挑一个源的两条线。
        val direct = tries.mapNotNull { (sid, id, lang) ->
            val api = byKey[sid]?.api ?: return@mapNotNull null
            async(Dispatchers.IO) {
                fetch(detailHttp, Cms.url(api, ids = id))?.let { Cms.parseDetail(it, sid) }
                    ?.let { d ->
                        if (lang.isBlank()) d else d.copy(
                            lines = d.lines.map { l ->
                                if (l.from.startsWith(lang)) l else l.copy(from = "$lang · ${l.from}")
                            }
                        )
                    }
            }
        }.awaitAll().filterNotNull().filter { it.lines.isNotEmpty() }

        var details = direct
        // 源站 id 失效时，再用片名在其它源里找一次；仍是并行，最多 5.5 秒。
        if (details.isEmpty()) {
            details = sources.filter { it.key != item.sid }.map { s ->
                async(Dispatchers.IO) {
                    val raw = fetch(detailHttp, Cms.url(s.api, wd = item.name)) ?: return@async null
                    val hit = Cms.parseList(raw, s.key)
                        .firstOrNull {
                            val a = norm(it.name)
                            val b = norm(item.name)
                            a == b || a.contains(b) || b.contains(a)
                        } ?: return@async null
                    fetch(detailHttp, Cms.url(s.api, ids = hit.id))?.let { Cms.parseDetail(it, s.key) }
                }
            }.awaitAll().filterNotNull().filter { it.lines.isNotEmpty() }
        }

        val xj = xjJob.await()
        val merged = combineDetails(item, details + if (xj.isEmpty()) emptyList() else listOf(Detail(item, xj, "")))
        if (merged == null) return@supervisorScope if (xj.isEmpty()) null else Detail(item, xj, "")
        merged.copy(lines = rankLines(probeLines(merged.lines)))
    }
}
