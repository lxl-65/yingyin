package app.yingyin.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** PC 上的「放映厅」服务。App 优先读它，这样分类、详情、线路和网站完全同源。 */
const val SERVER_BASE = "http://192.168.5.4:8899"

data class ServerCategory(val key: String, val name: String, val hidden: Boolean)
data class ServerGroup(val cat: String, val name: String, val items: List<Item>)
data class ServerFacets(
    val genres: List<String> = emptyList(),
    val areas: List<String> = emptyList(),
    val years: List<String> = emptyList()
)

data class ServerPage(
    val items: List<Item>,
    val alive: Int,
    val sources: Int,
    val page: Int,
    val facets: ServerFacets = ServerFacets()
)

object ServerApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    /** 只用来判断“家里那台电脑此刻在不在”，失败时最多等 1.5 秒。 */
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .callTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var probeAt = 0L
    @Volatile private var probeOk = false

    /**
     * PC 服务是增强项，不是运行前提。
     * 朋友手机不在同一个 Wi-Fi 时，1.5 秒内失败并直接走本地源，不拖首页和详情。
     */
    suspend fun probe(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - probeAt < 5 * 60_000L) return@withContext probeOk
        probeOk = try {
            val req = Request.Builder()
                .url("$SERVER_BASE/api/meta")
                .header("User-Agent", "YingyinApp/0.3")
                .build()
            probeClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
        probeAt = now
        probeOk
    }

    fun isOnline(): Boolean = probeOk

    private suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$SERVER_BASE$path").header("User-Agent", "YingyinApp/0.2").build()
            client.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun item(o: JSONObject): Item {
        val entries = o.optJSONArray("entries") ?: JSONArray()
        val refs = buildList {
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val s = e.optInt("s", -1)
                val id = e.optString("id")
                if (s >= 0 && id.isNotEmpty()) add(EntryRef(s, id, e.optString("name")))
            }
        }
        val first = refs.firstOrNull()
        val type = o.optString("tn").ifBlank { o.optString("type") }
        return Item(
            id = first?.id ?: o.optString("id"),
            sid = first?.s?.toString() ?: "",
            name = o.optString("title", o.optString("name")),
            pic = o.optString("pic").ifBlank {
                o.optJSONArray("pics")?.optString(0) ?: ""
            },
            year = o.optString("year"),
            remarks = o.optString("remarks"),
            type = type,
            area = o.optString("area"),
            score = o.optString("score").toDoubleOrNull() ?: 0.0,
            srcs = refs.size.coerceAtLeast(1),
            entries = refs,
            lang = o.optString("lang")
        )
    }

    suspend fun meta(): List<ServerCategory> {
        val raw = get("/api/meta") ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("cats") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(ServerCategory(o.optString("key"), o.optString("name"), o.optBoolean("hidden", false)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun sourceCount(): Int {
        val raw = get("/api/meta") ?: return 0
        return try {
            JSONObject(raw).optJSONArray("sources")?.length() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    suspend fun curated(): List<ServerGroup> {
        val raw = get("/api/curated") ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("groups") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val g = arr.optJSONObject(i) ?: continue
                    val list = g.optJSONArray("items") ?: JSONArray()
                    val items = buildList {
                        for (j in 0 until list.length()) list.optJSONObject(j)?.let { add(item(it)) }
                    }
                    if (items.isNotEmpty()) add(ServerGroup(g.optString("cat"), g.optString("name"), items))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun list(
        cat: String,
        page: Int = 1,
        genre: String = "",
        area: String = "",
        year: String = "",
        sort: String = ""
    ): ServerPage? {
        if (cat == "private" && !Library.showPrivate) return null
        val q = StringBuilder("/api/list?cat=${URLEncoder.encode(cat, "UTF-8")}&page=$page")
        if (cat == "private") q.append("&nsfw=1")
        if (genre.isNotBlank()) q.append("&g=${URLEncoder.encode(genre, "UTF-8")}")
        if (area.isNotBlank()) q.append("&area=${URLEncoder.encode(area, "UTF-8")}")
        if (year.isNotBlank()) q.append("&year=${URLEncoder.encode(year, "UTF-8")}")
        if (sort.isNotBlank()) q.append("&sort=${URLEncoder.encode(sort, "UTF-8")}")
        val raw = get(q.toString()) ?: return null
        return try {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(item(it)) }
            }
            val f = o.optJSONObject("facets")
            fun facet(name: String): List<String> {
                val a = f?.optJSONArray(name) ?: return emptyList()
                return buildList {
                    for (i in 0 until a.length()) {
                        val pair = a.optJSONArray(i) ?: continue
                        val v = pair.optString(0)
                        if (v.isNotEmpty()) add(v)
                    }
                }
            }
            ServerPage(
                items = items,
                alive = o.optInt("alive"),
                sources = o.optInt("total"),
                page = page,
                facets = ServerFacets(facet("genres"), facet("areas"), facet("years"))
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun search(wd: String): List<Item>? {
        val raw = get("/api/search?wd=${URLEncoder.encode(wd, "UTF-8")}") ?: return null
        return try {
            val arr = JSONObject(raw).optJSONArray("items") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(item(it)) }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun detail(item: Item): Detail? {
        if (item.entries.isEmpty()) return null
        val pairs = item.entries.joinToString(",") { "${it.s}:${it.id}" }
        val raw = get("/api/detail?list=${URLEncoder.encode(pairs, "UTF-8")}") ?: return null
        return try {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("lines") ?: return null
            val lines = buildList {
                for (i in 0 until arr.length()) {
                    val l = arr.optJSONObject(i) ?: continue
                    val epsArr = l.optJSONArray("eps") ?: JSONArray()
                    val eps = buildList {
                        for (j in 0 until epsArr.length()) {
                            val e = epsArr.optJSONObject(j) ?: continue
                            val url = e.optString("url")
                            if (url.isNotEmpty()) add(Episode(e.optString("n", "第${j + 1}集"), url))
                        }
                    }
                    if (eps.isEmpty()) continue
                    add(
                        Line(
                            from = l.optString("name", l.optString("srcName")),
                            eps = eps,
                            quality = l.optString("q"),
                            latency = l.optInt("lat", 0),
                            bad = l.optBoolean("bad", false),
                            recommended = l.optBoolean("rec", false)
                        )
                    )
                }
            }.sortedWith(
                compareBy<Line> { it.bad }
                    .thenByDescending { it.recommended }
                    .thenBy { if (it.quality.isBlank()) 1 else 0 }
                    .thenBy { it.latency }
            )
            Detail(
                item = item.copy(
                    name = o.optString("title", item.name),
                    pic = o.optString("pic", item.pic),
                    year = o.optString("year", item.year),
                    area = o.optString("area", item.area),
                    type = o.optString("type", item.type),
                    remarks = o.optString("remarks", item.remarks),
                    score = o.optString("score").toDoubleOrNull() ?: item.score,
                    lang = o.optString("lang", item.lang),
                    srcs = o.optJSONArray("sources")?.length() ?: item.srcs
                ),
                lines = lines,
                desc = cleanText(o.optString("content")),
                actors = o.optString("actor"),
                director = o.optString("director"),
                lang = o.optString("lang", item.lang)
            )
        } catch (_: Exception) {
            null
        }
    }
}
